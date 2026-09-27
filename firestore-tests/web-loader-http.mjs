// Same as web-loader.mjs, but the site's config resolves to http-config.mjs,
// which switches the pages to the own-server (HTTP) backend.
import { register } from "node:module";
import { pathToFileURL } from "node:url";

register(new URL("./web-loader-hooks.mjs", import.meta.url), pathToFileURL("./"), { data: { http: true } });
