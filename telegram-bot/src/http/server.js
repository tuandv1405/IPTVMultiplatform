import http from "node:http";
import { timingSafeEqual } from "node:crypto";
import { ApiError, badRequest, notFound, tooMany, unauthorized } from "../errors.js";
import { CATEGORIES, CONTRIBUTOR_DECLARATIONS, POLICY_VERSION, SUBMISSION_DECLARATIONS } from "../policy.js";
import { createRateLimiter } from "../rateLimit.js";

const MAX_BODY_BYTES = 1024 * 1024;

/**
 * The contributor API the web app calls, plus the Telegram webhook when that
 * mode is on. Plain node:http — a dozen routes do not need a framework.
 */
export function createHttpServer({ service, verifyToken, config, bot = null, log = console }) {
  const allowedOrigins = new Set(config.corsOrigins);
  const ipLimiter = createRateLimiter({ limit: config.rateLimitPerMinute ?? 30, windowMs: 60_000 });

  const routes = [
    ["GET", /^\/healthz$/, async () => ({ ok: true })],
    [
      "GET",
      /^\/api\/policy$/,
      async () => ({
        policyVersion: POLICY_VERSION,
        contributorDeclarations: CONTRIBUTOR_DECLARATIONS,
        submissionDeclarations: SUBMISSION_DECLARATIONS,
        categories: CATEGORIES,
      }),
    ],
    ["GET", /^\/api\/me$/, async (ctx) => service.me(await ctx.identity())],
    // Called by /delete-account/ right before the Firebase account is deleted.
    ["POST", /^\/api\/me\/delete$/, async (ctx) => service.deleteMe(await ctx.identity())],
    ["POST", /^\/api\/contributor\/register$/, async (ctx) => service.register(await ctx.identity(), await ctx.json())],
    [
      "POST",
      /^\/api\/playlists\/verify$/,
      async (ctx) => service.verifyLink(await ctx.identity(), (await ctx.json()).url),
    ],
    ["GET", /^\/api\/submissions$/, async (ctx) => ({ submissions: await service.listOwn(await ctx.identity()) })],
    ["POST", /^\/api\/submissions$/, async (ctx) => service.submit(await ctx.identity(), await ctx.json())],
    [
      "POST",
      /^\/api\/submissions\/([A-Za-z0-9_-]{1,40})\/withdraw$/,
      async (ctx, [id]) => service.withdraw(await ctx.identity(), id),
    ],
  ];

  if (bot && config.telegram.mode === "webhook") {
    routes.push([
      "POST",
      /^\/telegram\/webhook$/,
      async (ctx) => {
        // Telegram echoes the secret given to setWebhook in this header.
        const given = Buffer.from(String(ctx.req.headers["x-telegram-bot-api-secret-token"] ?? ""));
        const expected = Buffer.from(config.telegram.webhookSecret);
        if (given.length !== expected.length || !timingSafeEqual(given, expected)) throw unauthorized();
        // Handled before answering: on Cloud Run the CPU is only guaranteed
        // while a request is open.
        await bot.handleUpdate(await ctx.json());
        return { ok: true };
      },
    ]);
  }

  async function handle(req, res) {
    const url = new URL(req.url, "http://localhost");
    const origin = req.headers.origin;
    const corsAllowed = origin && allowedOrigins.has(origin);

    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Referrer-Policy", "no-referrer");
    if (corsAllowed) {
      res.setHeader("Access-Control-Allow-Origin", origin);
      res.setHeader("Vary", "Origin");
      res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
      res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      res.setHeader("Access-Control-Max-Age", "600");
    }
    if (req.method === "OPTIONS") {
      res.writeHead(corsAllowed ? 204 : 403).end();
      return;
    }

    try {
      const route = routes.find(([method, pattern]) => method === req.method && pattern.test(url.pathname));
      if (!route) throw notFound();

      // The webhook is Telegram's own traffic, not a browser; do not count it.
      if (!url.pathname.startsWith("/telegram/") && url.pathname !== "/healthz") {
        if (!ipLimiter(clientIp(req, config.trustProxy, config.trustedProxyHops ?? 1))) throw tooMany();
      }

      const params = route[1].exec(url.pathname).slice(1);
      const ctx = {
        req,
        json: () => readJson(req),
        identity: async () => {
          const header = String(req.headers.authorization ?? "");
          const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
          if (!token) throw unauthorized();
          return verifyToken(token);
        },
      };
      const body = await route[2](ctx, params);
      send(res, 200, body);
    } catch (error) {
      if (error instanceof ApiError) {
        send(res, error.status, { error: error.code, message: error.message, ...(error.details ? { details: error.details } : {}) });
        return;
      }
      // Messages of unexpected errors can carry internals; log them, do not return them.
      log.error?.("unhandled request error", { path: url.pathname, error: error.message });
      send(res, 500, { error: "internal", message: "Something went wrong. Try again later." });
    }
  }

  return http.createServer((req, res) => {
    handle(req, res).catch((error) => {
      log.error?.("request crashed", { error: error.message });
      if (!res.headersSent) res.writeHead(500).end();
    });
  });
}

function send(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Content-Length": Buffer.byteLength(payload) });
  res.end(payload);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    const type = String(req.headers["content-type"] ?? "").split(";")[0].trim().toLowerCase();
    if (type !== "application/json") {
      req.resume();
      return reject(badRequest("invalid_content_type", "Send JSON."));
    }
    if (Number(req.headers["content-length"] ?? 0) > MAX_BODY_BYTES) {
      req.resume();
      return reject(new ApiError(413, "payload_too_large", "The request is too large."));
    }
    const chunks = [];
    let total = 0;
    let tooLarge = false;
    req.on("data", (chunk) => {
      if (tooLarge) return; // drain the rest so the 413 can still be delivered
      total += chunk.length;
      if (total > MAX_BODY_BYTES) {
        tooLarge = true;
        chunks.length = 0;
        reject(new ApiError(413, "payload_too_large", "The request is too large."));
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (tooLarge) return;
      try {
        const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("shape");
        resolve(parsed);
      } catch {
        reject(badRequest("invalid_json", "The request body is not valid JSON."));
      }
    });
    req.on("error", () => reject(badRequest("invalid_json", "The request body could not be read.")));
  });
}

/**
 * Proxies append the address they saw to X-Forwarded-For, so only entries from
 * the right are trustworthy; whatever the client sent sits on the left. With
 * one trusted proxy (Caddy, nginx, Cloud Run's front end) the client is the
 * last entry.
 */
export function clientIp(req, trustProxy, trustedHops = 1) {
  if (trustProxy) {
    const entries = String(req.headers["x-forwarded-for"] ?? "")
      .split(",")
      .map((entry) => entry.trim())
      .filter(Boolean);
    const candidate = entries[entries.length - trustedHops];
    if (candidate) return candidate;
  }
  return req.socket.remoteAddress ?? "unknown";
}
