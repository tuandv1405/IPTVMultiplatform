// Lets Node import the site's browser modules unchanged:
//   https://www.gstatic.com/firebasejs/<v>/firebase-<x>.js  →  the "firebase/<x>" npm package
//   /assets/<file>                                        →  ../web/public/assets/<file>
import { register } from "node:module";
import { pathToFileURL } from "node:url";

register(new URL("./web-loader-hooks.mjs", import.meta.url), pathToFileURL("./"));
