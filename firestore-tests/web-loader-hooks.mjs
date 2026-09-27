const GSTATIC = /^https:\/\/www\.gstatic\.com\/firebasejs\/[\d.]+\/firebase-(app|auth|firestore)\.js$/;
const ASSETS = new URL("../web/public/assets/", import.meta.url);
const HTTP_CONFIG = new URL("./http-config.mjs", import.meta.url).href;
let http = false;

export function initialize(data) {
  http = Boolean(data?.http);
}

export async function resolve(specifier, context, nextResolve) {
  const firebase = GSTATIC.exec(specifier);
  if (firebase) return nextResolve(`firebase/${firebase[1]}`, { ...context, parentURL: import.meta.url });
  // http-config.mjs re-exports the real config; only the site's own imports are redirected.
  if (http && specifier === "/assets/contributor-config.js" && context.parentURL !== HTTP_CONFIG) {
    return { url: HTTP_CONFIG, shortCircuit: true };
  }
  if (specifier.startsWith("/assets/")) return { url: new URL(specifier.slice("/assets/".length), ASSETS).href, shortCircuit: true };
  return nextResolve(specifier, context);
}
