import { createHash } from "node:crypto";

export class NotAPlaylist extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

const STREAM_URL = /^(https?|rtmps?|rtsp|rtp|udp|mms|srt):\/\/\S+$/i;

/**
 * Recognises the same families of playlist the app imports (M3U/M3U8, XSPF,
 * JSON channel lists incl. iptv-org) and summarises one. This is a check that
 * the link is a working playlist, not a second copy of the app's parsers:
 * it only needs channel names, groups and stream URLs.
 */
export function analysePlaylist(input) {
  const text = (Buffer.isBuffer(input) ? input.toString("utf8") : String(input ?? "")).replace(/^﻿/, "");
  const head = text.trimStart().slice(0, 512).toLowerCase();

  let result;
  if (head.startsWith("#extm3u") || /#extinf/i.test(text.slice(0, 64 * 1024))) {
    result = parseM3u(text);
  } else if (head.startsWith("<?xml") || head.startsWith("<playlist")) {
    if (!/<playlist[\s>]/i.test(text) || !/<tracklist[\s>]/i.test(text)) {
      throw new NotAPlaylist("not_a_playlist", "This XML document is not an XSPF playlist.");
    }
    result = parseXspf(text);
  } else if (head.startsWith("{") || head.startsWith("[")) {
    result = parseJson(text);
  } else if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
    throw new NotAPlaylist("not_a_playlist", "The link returns a web page, not a playlist.");
  } else {
    throw new NotAPlaylist("not_a_playlist", "The link does not return an M3U, XSPF or JSON playlist.");
  }

  const channels = result.channels.filter((c) => STREAM_URL.test(c.url));
  if (channels.length === 0) {
    throw new NotAPlaylist("no_channels", "The playlist has no channels with a stream link.");
  }

  const groups = new Set(channels.map((c) => c.group).filter(Boolean));
  const uniqueUrls = [...new Set(channels.map((c) => c.url.trim()))].sort();
  return {
    format: result.format,
    channelCount: channels.length,
    groupCount: groups.size,
    sampleNames: channels.slice(0, 5).map((c) => c.name || "(unnamed)"),
    // Same channel list => same hash, regardless of order, names or groups.
    contentHash: createHash("sha256").update(uniqueUrls.join("\n")).digest("hex"),
  };
}

function parseM3u(text) {
  const channels = [];
  let pending = null;
  let sawExtinf = false;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (/^#EXTINF/i.test(line)) {
      sawExtinf = true;
      const comma = findTitleComma(line);
      pending = {
        name: comma >= 0 ? line.slice(comma + 1).trim() : "",
        group: attribute(line, "group-title"),
      };
      if (!pending.name) pending.name = attribute(line, "tvg-name");
      continue;
    }
    if (line.startsWith("#")) {
      if (/^#EXTGRP:/i.test(line) && pending) pending.group = line.slice(8).trim();
      continue;
    }
    if (pending) {
      channels.push({ ...pending, url: line });
      pending = null;
    } else if (!sawExtinf) {
      // Bare M3U: one stream URL per line, no metadata.
      channels.push({ name: "", group: "", url: line });
    }
  }
  return { format: "m3u", channels };
}

/** The title follows the first comma that is outside a quoted attribute value. */
function findTitleComma(line) {
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    if (line[i] === '"') quoted = !quoted;
    else if (line[i] === "," && !quoted) return i;
  }
  return -1;
}

function attribute(line, name) {
  const match = line.match(new RegExp(`${name}="([^"]*)"`, "i"));
  return match ? match[1].trim() : "";
}

function parseXspf(text) {
  const channels = [];
  const tracks = text.match(/<track[\s>][\s\S]*?<\/track>/gi) ?? [];
  for (const track of tracks) {
    channels.push({
      name: xmlText(track, "title"),
      group: xmlText(track, "album"),
      url: decodeXml(xmlText(track, "location")),
    });
  }
  return { format: "xspf", channels };
}

function xmlText(fragment, tag) {
  const match = fragment.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, "i"));
  if (!match) return "";
  return match[1].replace(/^<!\[CDATA\[|\]\]>$/g, "").trim();
}

function decodeXml(value) {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

const URL_KEYS = ["url", "stream_url", "streamUrl", "stream", "link", "src", "source", "file"];
const NAME_KEYS = ["name", "title", "channel", "channel_name", "tvg_name"];
const GROUP_KEYS = ["group", "group_title", "groupTitle", "category", "categories"];
const LIST_KEYS = ["channels", "streams", "items", "data", "playlist", "list", "results"];

function parseJson(text) {
  let doc;
  try {
    doc = JSON.parse(text);
  } catch {
    throw new NotAPlaylist("not_a_playlist", "The link returns malformed JSON.");
  }
  let list = Array.isArray(doc) ? doc : null;
  if (!list && doc && typeof doc === "object") {
    for (const key of LIST_KEYS) {
      if (Array.isArray(doc[key])) {
        list = doc[key];
        break;
      }
    }
  }
  if (!list) throw new NotAPlaylist("not_a_playlist", "This JSON document has no channel list.");

  const channels = [];
  for (const item of list) {
    if (!item || typeof item !== "object") continue;
    const url = pick(item, URL_KEYS);
    if (typeof url !== "string") continue;
    const group = pick(item, GROUP_KEYS);
    channels.push({
      name: String(pick(item, NAME_KEYS) ?? ""),
      group: Array.isArray(group) ? String(group[0] ?? "") : String(group ?? ""),
      url,
    });
  }
  return { format: "json", channels };
}

function pick(object, keys) {
  for (const key of keys) if (object[key] != null) return object[key];
  return undefined;
}
