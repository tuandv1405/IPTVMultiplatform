/* Where the contributor API (telegram-bot/) is served. Set this after deploying it:
 *
 *   Cloud Run:              "https://tsiptv-contributor-bot-xxxxxxxx.a.run.app"
 *   VPS behind Caddy/nginx: "https://api.your-domain.example"
 *   Same origin via a Firebase Hosting rewrite of /api/**:  ""
 *
 * While the placeholder is left in, the contributor pages say the programme is
 * not open yet instead of failing on every request.
 */
export const API_BASE = "{{CONTRIBUTOR_API_BASE}}";

export const API_CONFIGURED = !API_BASE.includes("{{");
