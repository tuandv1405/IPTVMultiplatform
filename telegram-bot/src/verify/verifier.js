import { createHash } from "node:crypto";
import { FetchRefused, checkUrl, createFetcher } from "./fetcher.js";
import { NotAPlaylist, analysePlaylist } from "./playlistParser.js";

/**
 * Fetches a contributor's link and confirms it is a working playlist.
 * Always resolves: { ok: true, ... } or { ok: false, code, message }.
 */
export function createPlaylistVerifier({ fetcher = createFetcher(), clock = Date.now } = {}) {
  return async function verifyPlaylist(url) {
    try {
      const response = await fetcher(url);
      const summary = analysePlaylist(response.body);
      return {
        ok: true,
        ...summary,
        finalUrl: response.finalUrl,
        bytes: response.body.length,
        checkedAt: clock(),
      };
    } catch (error) {
      if (error instanceof FetchRefused || error instanceof NotAPlaylist) {
        return { ok: false, code: error.code, message: error.message, checkedAt: clock() };
      }
      return { ok: false, code: "verify_failed", message: "The link could not be checked.", checkedAt: clock() };
    }
  };
}

/**
 * The form a link is compared in for "already submitted": scheme and host are
 * case-insensitive, default ports and fragments are noise. Query strings stay —
 * they often select a different list.
 */
export function normaliseUrl(raw) {
  const url = checkUrl(raw);
  url.hash = "";
  if ((url.protocol === "http:" && url.port === "80") || (url.protocol === "https:" && url.port === "443")) {
    url.port = "";
  }
  url.hostname = url.hostname.toLowerCase();
  return url.href;
}

export function urlHash(raw) {
  return createHash("sha256").update(normaliseUrl(raw)).digest("hex");
}
