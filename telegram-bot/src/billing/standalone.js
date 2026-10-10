// Standalone billing server: only /healthz, POST /billing/verify and POST /billing/rtdn.
// Run: node src/billing/standalone.js   (needs `npm ci` for firebase-admin; see README.md)
import { createServer } from "node:http";
import { BillingConfigError, loadBillingConfig, readServiceAccountKey } from "./config.js";
import { createFirestoreBillingStore } from "./firestoreStore.js";
import { createBilling } from "./index.js";

let config;
try {
  config = loadBillingConfig();
} catch (error) {
  if (error instanceof BillingConfigError) {
    console.error(error.message);
    process.exit(1);
  }
  throw error;
}

const { initializeApp, applicationDefault } = await import("firebase-admin/app");
const { getAuth } = await import("firebase-admin/auth");
const { getFirestore } = await import("firebase-admin/firestore");

const app = initializeApp({ credential: applicationDefault(), projectId: config.firebaseProjectId });
const auth = getAuth(app);
const store = createFirestoreBillingStore(getFirestore(app));
const { routes } = createBilling({
  config,
  store,
  // checkRevoked: a signed-out or disabled account cannot claim purchases.
  verifyIdToken: (token) => auth.verifyIdToken(token, true),
  serviceAccountKey: readServiceAccountKey(config.serviceAccountKeyFile),
});

const server = createServer(async (req, res) => {
  try {
    if (req.method === "GET" && new URL(req.url, "http://localhost").pathname === "/healthz") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end('{"ok":true}');
      return;
    }
    if (await routes.handle(req, res)) return;
    res.writeHead(404, { "content-type": "application/json" });
    res.end('{"error":"not_found"}');
  } catch (error) {
    console.error("billing: unhandled", error?.message);
    if (!res.headersSent) res.writeHead(500);
    res.end();
  }
});

server.listen(config.port, config.host, () => {
  console.log(`billing server on ${config.host}:${config.port} for ${config.packageName}`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
