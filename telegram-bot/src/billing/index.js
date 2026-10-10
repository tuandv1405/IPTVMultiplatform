// Billing module entry: wires Google auth, the Play API, a store, the service and the routes.
// Mounting into the existing server (src/http/server.js) is described in README.md.
import { createGoogleAuth } from "./googleAuth.js";
import { createBillingRoutes } from "./http.js";
import { createPushTokenVerifier } from "./oidc.js";
import { createPlayApi } from "./playApi.js";
import { createBillingService } from "./service.js";

export { createBillingService, BillingError } from "./service.js";
export { createBillingRoutes, entitlementToJson } from "./http.js";
export { createMemoryBillingStore } from "./memoryStore.js";
export { computeEntitlement, accountHash } from "./entitlement.js";
export { loadBillingConfig } from "./config.js";

/**
 * Everything needed to serve /billing/verify and /billing/rtdn.
 * @param {object} options
 * @param {ReturnType<typeof import("./config.js").loadBillingConfig>} options.config
 * @param {object} options.store billing store (firestoreStore.js in production)
 * @param {(idToken: string) => Promise<{ uid: string }>} options.verifyIdToken e.g. firebase-admin `getAuth().verifyIdToken`
 * @param {object} [options.serviceAccountKey] parsed JSON key; null = metadata server
 */
export function createBilling({ config, store, verifyIdToken, serviceAccountKey = null, fetch = globalThis.fetch, log = console }) {
  const auth = createGoogleAuth({ serviceAccountKey, fetch });
  const playApi = createPlayApi({ auth, fetch });
  const service = createBillingService({ playApi, store, packageName: config.packageName, productPlans: config.productPlans, log });
  const verifyPushToken = createPushTokenVerifier({
    audience: config.pushAudience,
    serviceAccountEmail: config.pushServiceAccount,
    fetch,
  });
  const routes = createBillingRoutes({ service, verifyIdToken, verifyPushToken, log });
  return { service, routes };
}
