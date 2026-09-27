import http from "node:http";
import https from "node:https";
import { isIP } from "node:net";
import zlib from "node:zlib";
import { createSafeLookup, isPublicAddress } from "./netguard.js";

export class FetchRefused extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

const ALLOWED_PORTS = new Set(["", "80", "443", "8000", "8080", "8443"]);

/**
 * Checks a contributor-supplied URL before anything is fetched. Returns the
 * parsed URL or throws FetchRefused with a code the web app can translate.
 */
export function checkUrl(raw) {
  let url;
  try {
    url = new URL(String(raw ?? "").trim());
  } catch {
    throw new FetchRefused("invalid_url", "That is not a valid link.");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new FetchRefused("unsupported_scheme", "Only http:// and https:// links are accepted.");
  }
  if (url.username || url.password) {
    throw new FetchRefused("credentials_in_url", "Links must not contain a username or password.");
  }
  if (!ALLOWED_PORTS.has(url.port)) {
    throw new FetchRefused("port_not_allowed", "Only the standard web ports are accepted.");
  }
  const host = url.hostname.replace(/^\[|\]$/g, "");
  if (isIP(host) && !isPublicAddress(host)) {
    throw new FetchRefused("private_address", "Links to private or local addresses are not accepted.");
  }
  if (/^localhost$|\.localhost$|\.local$|\.internal$/i.test(host)) {
    throw new FetchRefused("private_address", "Links to private or local addresses are not accepted.");
  }
  return url;
}

/**
 * GET with SSRF protection, a redirect limit, a byte cap and a deadline.
 * Resolves to { finalUrl, status, contentType, body: Buffer }.
 */
export function createFetcher({
  timeoutMs = 15_000,
  maxBytes = 15 * 1024 * 1024,
  maxRedirects = 3,
  lookup = createSafeLookup(),
  userAgent = "TSIPTV-PlaylistVerifier/1.0 (+https://tsiptv-8bdd6.web.app/contributor/)",
} = {}) {
  return async function fetchPlaylist(rawUrl) {
    const deadline = Date.now() + timeoutMs;
    let url = checkUrl(rawUrl);
    for (let hop = 0; ; hop++) {
      const response = await requestOnce(url, { deadline, maxBytes, lookup, userAgent });
      if (response.redirect) {
        if (hop >= maxRedirects) throw new FetchRefused("too_many_redirects", "The link redirects too many times.");
        // Every hop goes through checkUrl and the safe lookup again.
        url = checkUrl(new URL(response.redirect, url).href);
        continue;
      }
      return { ...response, finalUrl: url.href };
    }
  };
}

function requestOnce(url, { deadline, maxBytes, lookup, userAgent }) {
  return new Promise((resolve, reject) => {
    const remaining = deadline - Date.now();
    if (remaining <= 0) return reject(new FetchRefused("timeout", "The link took too long to answer."));

    const client = url.protocol === "https:" ? https : http;
    const req = client.request(
      url,
      {
        method: "GET",
        lookup,
        headers: {
          "User-Agent": userAgent,
          Accept: "*/*",
          "Accept-Encoding": "gzip, deflate, br",
        },
      },
      (res) => {
        const status = res.statusCode ?? 0;
        if (status >= 300 && status < 400 && res.headers.location) {
          res.resume();
          return resolve({ redirect: res.headers.location });
        }
        if (status < 200 || status >= 300) {
          res.resume();
          return reject(new FetchRefused("http_error", `The link answered HTTP ${status}.`));
        }
        const declared = Number(res.headers["content-length"] ?? 0);
        if (declared > maxBytes) {
          res.destroy();
          return reject(new FetchRefused("too_large", "The playlist is larger than 15 MB."));
        }

        let stream = res;
        const encoding = String(res.headers["content-encoding"] ?? "").toLowerCase();
        if (encoding === "gzip") stream = res.pipe(zlib.createGunzip());
        else if (encoding === "deflate") stream = res.pipe(zlib.createInflate());
        else if (encoding === "br") stream = res.pipe(zlib.createBrotliDecompress());

        const chunks = [];
        let total = 0;
        stream.on("data", (chunk) => {
          total += chunk.length;
          // Counted after decompression, so a small gzip bomb cannot slip past.
          if (total > maxBytes) {
            req.destroy();
            stream.destroy();
            reject(new FetchRefused("too_large", "The playlist is larger than 15 MB."));
            return;
          }
          chunks.push(chunk);
        });
        stream.on("end", () =>
          resolve({ status, contentType: String(res.headers["content-type"] ?? ""), body: Buffer.concat(chunks) }),
        );
        stream.on("error", () => reject(new FetchRefused("read_failed", "The playlist could not be read.")));
      },
    );

    // An absolute deadline, not just an idle timeout: a server dripping one
    // byte at a time would otherwise hold the request open indefinitely.
    const deadlineTimer = setTimeout(() => {
      const error = new FetchRefused("timeout", "The link took too long to answer.");
      req.destroy(error);
      reject(error);
    }, remaining);
    req.on("close", () => clearTimeout(deadlineTimer));
    req.on("error", (error) => {
      if (error instanceof FetchRefused) return reject(error);
      if (error.code === "EPRIVATEADDRESS") {
        return reject(new FetchRefused("private_address", "Links to private or local addresses are not accepted."));
      }
      if (error.code === "ENOTFOUND" || error.code === "EAI_AGAIN") {
        return reject(new FetchRefused("dns_failed", "The link's server name does not exist."));
      }
      reject(new FetchRefused("unreachable", "The link could not be reached."));
    });
    req.end();
  });
}
