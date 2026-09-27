// Stand-in for web/public/assets/contributor-config.js in the HTTP-backend test:
// same exports, but BACKEND = "http" and the API on the test server's port.
export * from "../web/public/assets/contributor-config.js";
export const BACKEND = "http";
export const API_BASE = globalThis.TSIPTV_TEST_API_BASE;
export const API_CONFIGURED = true;
