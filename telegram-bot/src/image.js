import { badRequest } from "./errors.js";

export const MAX_IMAGE_BYTES = 300 * 1024;

/**
 * Validates the playlist image the web app sends as a data URL. The browser
 * already resized it; the server only trusts the bytes, not the declared type.
 */
export function parseImageDataUrl(dataUrl) {
  const match = /^data:(image\/(?:png|jpeg|webp));base64,([A-Za-z0-9+/=]+)$/.exec(String(dataUrl ?? ""));
  if (!match) throw badRequest("invalid_image", "The image must be a PNG, JPEG or WebP file.");
  const [, declaredMime, base64] = match;
  const bytes = Buffer.from(base64, "base64");
  if (bytes.length === 0) throw badRequest("invalid_image", "The image is empty.");
  if (bytes.length > MAX_IMAGE_BYTES) throw badRequest("image_too_large", "The image must be 300 KB or smaller.");

  const actualMime = sniff(bytes);
  if (!actualMime || actualMime !== declaredMime) {
    throw badRequest("invalid_image", "The image must be a PNG, JPEG or WebP file.");
  }
  return { mime: actualMime, data: bytes.toString("base64"), bytes };
}

function sniff(bytes) {
  if (bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return "image/png";
  }
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes.length >= 12 && bytes.toString("ascii", 0, 4) === "RIFF" && bytes.toString("ascii", 8, 12) === "WEBP") {
    return "image/webp";
  }
  return null;
}
