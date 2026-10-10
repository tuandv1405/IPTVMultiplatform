# Billing server (Google Play subscriptions)

Verifies TS IPTV subscription purchases with Google Play and keeps
`users/{uid}/entitlements/current` up to date, so the entitlement follows the signed-in account on
every device and the Firestore rules can lift the quota caps for verified Unlimited users
(`docs/prd-subscriptions.md` §5.3, §6, §7).

**Status: ready to deploy, NOT deployed.** Until it runs, the app trusts the local Play purchase
state on each device (PRD §5.3).

## What it does

| Route | Caller | Does |
|---|---|---|
| `POST /billing/verify` | the app, after a purchase / on restore | Checks the Firebase ID token, reads the purchase with `purchases.subscriptionsv2.get`, checks product and account hash, links the purchase to the uid (first claim wins), acknowledges it if needed, recomputes the entitlement |
| `POST /billing/rtdn` | Pub/Sub push (Real-time developer notifications) | Checks the push OIDC token, re-reads the subscription, updates the linked user's entitlement (renew, cancel, grace, hold, pause, recover, restart, revoke, expire, voided) |
| `GET /healthz` | Cloud Run | `{"ok":true}` (standalone server only) |

### `POST /billing/verify` contract

Request:
```
POST /billing/verify
Authorization: Bearer <Firebase ID token>
Content-Type: application/json

{"productId": "tsiptv_unlimited", "purchaseToken": "<Play purchase token>"}
```
Success `200`:
```json
{"entitlement": {"plan": "unlimited", "active": true, "state": "ACTIVE",
  "productId": "tsiptv_unlimited", "basePlanId": "monthly",
  "expiresAtMs": 1762942800000, "autoRenewing": true, "source": "google_play"}}
```
`plan` is `free` / `no_ads` / `unlimited`; `state` is `ACTIVE`, `IN_GRACE_PERIOD`, `CANCELED`,
`ON_HOLD`, `PAUSED`, `EXPIRED`, `PENDING`, `PENDING_PURCHASE_CANCELED`, `REVOKED` or `NONE`.
With nothing active, `plan` is `free`, `active` false, and the other fields describe the latest
purchase (so the app can say "payment on hold").

Errors (`{"error": "<code>"}`):
| Status | Code | Meaning |
|---|---|---|
| 400 | `bad_json`, `bad_token`, `unknown_product`, `product_mismatch` | bad input; the token is not for that product |
| 401 | `unauthenticated` | missing / invalid / revoked ID token |
| 403 | `account_mismatch` | the purchase was made for another account (`obfuscatedAccountId`) |
| 404 | `purchase_not_found` | Play does not know the token (or it belongs to another package) |
| 409 | `linked_to_other_account` | already claimed by another uid |
| 502 | `play_rejected` | Play refused our credentials (deployment problem) |
| 503 | `play_unavailable` | Play outage; retry later |

`obfuscatedAccountId` must be the lowercase hex SHA-256 of the Firebase uid (UTF-8).

## Data (Firestore, Admin SDK)

- `billing_purchases/{sha256(token)}` — `uid` (null until claimed), `purchaseToken`, `productId`,
  `basePlanId`, `plan`, `state`, `expiresAt` (ms), `autoRenewing`, `acknowledged`, `accountHash`,
  `linkedPurchaseTokenHash`, `replaced`, `replacedBy`, `revoked`, `testPurchase`, `updatedAt`.
  Clients have no access (catch-all rule).
- `billing_accounts/{sha256(uid)}` — `{uid}`: lets RTDN link a purchase the app never verified.
- `users/{uid}/entitlements/current` — `v, plan, active, state, productId, basePlanId,
  expiresAt (timestamp), autoRenewing, source, updatedAt`. Owner read-only (`firestore.rules`).
- Needs one single-field index (automatic): `billing_purchases.uid`.

## Consistency, abuse limits

- **Revoked is sticky.** Once a purchase is revoked or voided it stays `revoked: true`, even if a
  later Play re-read still reports the line item active.
- **Upgrades / downgrades** (`linkedPurchaseToken`) without an account id inherit the replaced
  purchase's uid; if the replaced purchase belonged to another uid, that user's entitlement is
  recomputed too.
- **Concurrent verify and RTDN (race).** Purchase records are upserts of fresh Play state (last
  write wins; both writers just read Play, and `revoked` / the uid link cannot be undone by a later
  write: linking is a first-claim-wins transaction). The entitlement document is rebuilt by
  `recomputeEntitlement`, which reads the user's purchases and writes `entitlements/current` in **one
  Firestore transaction**: if another request changes that user's purchases meanwhile, Firestore
  retries with fresh reads, so the document always matches the purchases it was computed from. A
  stale Play read can still land last for a single purchase record; the next RTDN (Play sends one
  for every state change) corrects it.
- **Rate limits** on `/billing/verify` (in memory, per instance): 10 a minute per uid and 30 a minute
  per client address (`BILLING_VERIFY_PER_UID_PER_MIN`, `BILLING_VERIFY_PER_IP_PER_MIN`; set
  `BILLING_TRUST_PROXY=true` on Cloud Run so the address comes from `X-Forwarded-For`). Over the
  limit: `429 {"error":"rate_limited"}`. With `BILLING_TRUST_PROXY=true` the address is the
  `X-Forwarded-For` entry `BILLING_TRUSTED_PROXY_HOPS` places from the **right** (default `1`: the
  address the last trusted proxy saw; Cloud Run alone = 1, a load balancer in front of Cloud Run = 2).
  Entries further left are written by the client and are ignored; a header with fewer entries than
  the hop count falls back to the socket address.
- **JWKS:** an unknown `kid` in a push token forces at most one JWKS refetch per minute; the normal
  refresh is hourly.

## Files

`googleAuth.js` (service-account JWT or metadata-server tokens), `playApi.js` (subscriptionsv2.get,
acknowledge), `entitlement.js` (pure rules), `service.js`, `oidc.js` (Pub/Sub push token),
`http.js` (routes), `firestoreStore.js` / `memoryStore.js`, `config.js`, `index.js` (wiring),
`standalone.js` (server). Tests: `test/billing.test.js` (`npm run test:billing`, no network, no
`node_modules` needed).

## Deploy (do these by hand; nothing here was deployed)

Assumes project `tsiptv-8bdd6` and region `asia-southeast1`; adjust as needed.

1. **Link Play Console to the Cloud project.** Play Console › Setup › API access › link
   `tsiptv-8bdd6` (or the project you choose).
2. **Enable the API:** Cloud Console › APIs & Services › enable **Google Play Android Developer API**
   (`androidpublisher.googleapis.com`) and **Cloud Pub/Sub API**.
3. **Service account for the server:**
   ```
   gcloud iam service-accounts create tsiptv-billing --display-name "TS IPTV billing"
   ```
   Give it Firestore access: `roles/datastore.user` on the project.
4. **Grant it in Play Console:** Users and permissions › Invite new users › the service account's
   email › App permissions for TS IPTV: **View financial data, orders and cancellation survey
   responses** and **Manage orders and subscriptions**. (It can take up to 24 h to take effect.)
5. **Credentials:** on Cloud Run use the service account as the service identity (no key file; the
   metadata server issues the androidpublisher token). Elsewhere create a JSON key, keep it out of
   git, and set `BILLING_SERVICE_ACCOUNT_KEY_FILE=/path/key.json`. Firebase Admin uses
   Application Default Credentials (same account).
6. **Pub/Sub topic and publisher grant:**
   ```
   gcloud pubsub topics create play-rtdn
   gcloud pubsub topics add-iam-policy-binding play-rtdn \
     --member=serviceAccount:google-play-developer-notifications@system.gserviceaccount.com \
     --role=roles/pubsub.publisher
   ```
7. **Deploy the server** (Cloud Run, from `telegram-bot/`):
   ```
   gcloud run deploy tsiptv-billing --source . --region asia-southeast1 \
     --service-account tsiptv-billing@tsiptv-8bdd6.iam.gserviceaccount.com \
     --command node --args src/billing/standalone.js \
     --allow-unauthenticated \
     --set-env-vars BILLING_PACKAGE_NAME=tss.t.tsiptv,BILLING_PRODUCT_NOADS=tsiptv_noads,BILLING_PRODUCT_UNLIMITED=tsiptv_unlimited,FIREBASE_PROJECT_ID=tsiptv-8bdd6,BILLING_PUSH_AUDIENCE=https://<run-host>/billing/rtdn,BILLING_PUSH_SERVICE_ACCOUNT=rtdn-push@tsiptv-8bdd6.iam.gserviceaccount.com
   ```
   (`--allow-unauthenticated` because the app authenticates with Firebase ID tokens and Pub/Sub
   with its OIDC token, both checked in code.) Deploy once, read the URL, then set
   `BILLING_PUSH_AUDIENCE` to `https://<run-host>/billing/rtdn` and redeploy.
8. **Push subscription with OIDC:**
   ```
   gcloud iam service-accounts create rtdn-push
   gcloud pubsub subscriptions create play-rtdn-push --topic play-rtdn \
     --push-endpoint=https://<run-host>/billing/rtdn \
     --push-auth-service-account=rtdn-push@tsiptv-8bdd6.iam.gserviceaccount.com \
     --push-auth-token-audience=https://<run-host>/billing/rtdn \
     --ack-deadline=30 --dead-letter-topic=play-rtdn-dead --max-delivery-attempts=10
   ```
   (create `play-rtdn-dead` first, or drop the two dead-letter flags). The Pub/Sub service agent
   needs `roles/iam.serviceAccountTokenCreator` on `rtdn-push` in older projects.
9. **Play Console › Monetize › Monetization setup › Real-time developer notifications:** topic
   `projects/tsiptv-8bdd6/topics/play-rtdn`, notifications for subscriptions (and voided purchases),
   then **Send test notification**; the server logs `billing: RTDN test notification received`.
10. **Firestore rules:** deploy the rules from this branch (`firebase deploy --only
    firestore:rules`, after `cd firestore-tests && npm test`).
11. **App:** build with `TSIPTV_BILLING_VERIFY_URL=https://<run-host>/billing/verify` (Gradle
    property, `local.properties` or environment). Empty = the app skips server verification.

### Mounting in the existing contributor server instead

The routes work in any node:http server. In `src/http/server.js`'s `handle(req, res)` you would add,
before the route lookup:
```js
if (billingRoutes && (await billingRoutes.handle(req, res))) return;
```
with `billingRoutes = createBilling({ config: loadBillingConfig(), store: createFirestoreBillingStore(db),
verifyIdToken: (t) => getAuth().verifyIdToken(t, true) }).routes` built in `src/index.js`. That is
not wired in this branch (the contributor server is left untouched); the standalone service is the
documented path.
