// Billing configuration from the environment (see README.md in this folder).
import { readFileSync } from "node:fs";

export class BillingConfigError extends Error {}

export function loadBillingConfig(env = process.env) {
  const problems = [];
  const noAds = (env.BILLING_PRODUCT_NOADS || "tsiptv_noads").trim();
  const unlimited = (env.BILLING_PRODUCT_UNLIMITED || "tsiptv_unlimited").trim();
  const config = {
    port: Number(env.PORT || 8080),
    host: env.HOST || "0.0.0.0",
    packageName: (env.BILLING_PACKAGE_NAME || "tss.t.tsiptv").trim(),
    productPlans: { [noAds]: "no_ads", [unlimited]: "unlimited" },
    serviceAccountKeyFile: (env.BILLING_SERVICE_ACCOUNT_KEY_FILE || "").trim() || null,
    pushAudience: (env.BILLING_PUSH_AUDIENCE || "").trim(),
    pushServiceAccount: (env.BILLING_PUSH_SERVICE_ACCOUNT || "").trim(),
    firebaseProjectId: env.FIREBASE_PROJECT_ID || "tsiptv-8bdd6",
    // /billing/verify limits per minute; behind Cloud Run / a proxy set BILLING_TRUST_PROXY=true so
    // the client address comes from X-Forwarded-For, counted BILLING_TRUSTED_PROXY_HOPS entries from
    // the right (default 1: the address the last proxy saw; left entries can be spoofed).
    rateLimit: {
      perUid: Number(env.BILLING_VERIFY_PER_UID_PER_MIN || 10),
      perIp: Number(env.BILLING_VERIFY_PER_IP_PER_MIN || 30),
      trustProxy: env.BILLING_TRUST_PROXY === "true",
      trustedHops: Number(env.BILLING_TRUSTED_PROXY_HOPS || 1),
    },
  };
  if (!Number.isInteger(config.rateLimit.trustedHops) || config.rateLimit.trustedHops < 1) {
    problems.push("BILLING_TRUSTED_PROXY_HOPS must be a positive integer");
  }
  if (noAds === unlimited) problems.push("BILLING_PRODUCT_NOADS and BILLING_PRODUCT_UNLIMITED must differ");
  if (!/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/i.test(config.packageName)) problems.push("BILLING_PACKAGE_NAME is not a package name");
  if (!config.pushAudience) problems.push("BILLING_PUSH_AUDIENCE is required (the audience set on the Pub/Sub push subscription)");
  if (!/^[^@\s]+@[^@\s]+$/.test(config.pushServiceAccount)) problems.push("BILLING_PUSH_SERVICE_ACCOUNT must be the push subscription's service account email");
  if (!Number.isInteger(config.port) || config.port <= 0) problems.push("PORT must be a positive integer");
  if (problems.length) throw new BillingConfigError(`Billing configuration problems:\n- ${problems.join("\n- ")}`);
  return config;
}

/** The parsed service-account key, or null to use the metadata server (Cloud Run identity). */
export function readServiceAccountKey(file) {
  if (!file) return null;
  return JSON.parse(readFileSync(file, "utf8"));
}
