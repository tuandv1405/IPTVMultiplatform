# Hand-off: Subscriptions ("Không quảng cáo" and "Unlimited")

Branch: `feature/subscriptions` (not pushed, not merged, nothing deployed) · Date: 2026-10-10
PRD: `docs/prd-subscriptions.md` (AC-SUB1 … AC-SUB17, open questions Q1–Q7).

## Results

| Check | Result |
|---|---|
| `:composeApp:desktopTest` | **586 tests, 0 failures** (545 before; new: `EntitlementResolverTest`, `BillingPolicyTest`, `EntitlementRepositoryTest`, 4 `AdsPolicyTest` gate cases, 4 `QuotaPolicyTest` cases, `subscriptionKeysArePresent`) |
| `:composeApp:compileCommonMainKotlinMetadata` | green |
| `:composeApp:assembleDebug` | green |
| `:composeApp:assembleRelease` | green, **including R8**; unsigned here (no keystore configured in this worktree). No keep rules needed: Billing ships its consumer rules. |
| Firestore rules (emulator) | rules + devices/sync + **subscriptions** + web-backend: **51/51** (12 new). `web-http-backend.test.js` not run (needs `telegram-bot/node_modules`, as before). |
| Server module | `cd telegram-bot; node --test test/billing.test.js`: **26/26**, no network, no `node_modules` |
| Device | My own AVD **`TSIPTV_SUBS_QA`** (Pixel 6, API 37.1 Play Store image, headless, AVD name checked before each install). App starts, no crash; Profile › Subscription Info opens the plans screen (status, both cards, disclosure, Manage / Restore / Terms / Privacy). Without a Google account Play answers `SERVICE_DISCONNECTED`, so the screen says "Couldn't load prices from Google Play" (prices need a testing track, below). The AVD is shut down; delete it whenever you like. No sign-in, no Firestore writes. |

## What was built

**Common (`core/billing/`)**
- `Entitlement` (`Plan` FREE / NO_ADS / UNLIMITED, `EntitlementSource` NONE / CACHE / PLAY / SERVER, status, expiry). `noAds`, `unlimited`, `verified`.
- `EntitlementResolver` (pure): highest of server document and Play purchases (PURCHASED + acknowledged only); cache (≤ 72 h) only until both answered; tie → server.
- `EntitlementRepository`: `StateFlow<Entitlement?>` (null only until the cache is read), server document listener per signed-in uid, cache writes, expiry timer, and sends confirmed purchases to the billing server once per account (again on Restore).
- `BillingGateway` (+ `UnavailableBillingGateway` for iOS / desktop), `OfferSelector` (one offer per base plan, eligible trial preferred), `ReplacementPolicy` (modes of PRD §3.3, manage URL, `accountHash` = SHA-256(uid) hex), `ServerEntitlementSource` (Firestore), `PurchaseVerifier` (HTTP, disabled while no URL).
- Gates:
  - `AdsGate` takes the entitlement: subscribers get `AdsState.NONE` (no AdMob, no Shopee, so no History item either), and the gate waits for the entitlement before calling `startAdMob`, so `MobileAds.initialize` and the app open request never run for a subscriber. Pure `AdsDecision`.
  - Rewarded: `AdsGate.rewardedAllowedNow` (not for Unlimited; kept for No ads). For No ads the rewarded gateway starts the SDK on demand **without** the app open ad (`AdsPlatform.startAdMobForRewarded`).
  - `QuotaPolicy(QuotaPlan)`: FREE (also No ads) unchanged; UNLIMITED_VERIFIED 200 sends / 50 syncs, no reward counters; UNLIMITED_UNVERIFIED 8 / 3 a day by raising `sendRewards` / `syncRewards` together with each extra use (valid under the existing rules). `QuotaService.policy` follows the current plan on every call. The old `feature.account.Entitlement` hook is replaced.

**Android**
- `PlayBillingClient` (Billing **8.0.0**, `billing-ktx`): auto reconnection, `queryProductDetailsAsync`, `queryPurchasesAsync(SUBS)` at start and on every activity resume (≥ 10 s apart), acknowledge, pending purchases, `launchBillingFlow` with `obfuscatedAccountId` and `SubscriptionUpdateParams`, `ITEM_ALREADY_OWNED` → restore. Logs (tag `TSBilling`) only response codes, debug only.
- Build settings (Gradle property, `local.properties` or env; defaults in brackets): `TSIPTV_BILLING_NOADS_ID` (`tsiptv_noads`), `TSIPTV_BILLING_UNLIMITED_ID` (`tsiptv_unlimited`), `TSIPTV_BILLING_VERIFY_URL` (empty = no server; must be `https://…/billing/verify`).
- `TSAndroidApplication` creates the repository before `AdsGate`.
- **Why 8.0.0 and not 8.3 / 9.1:** those pull `kotlin-stdlib` 2.2.10, and with the project's Kotlin 2.2.0 compiler the Android compile then fails on Room's generated `AppDatabaseConstructor` ("Declaration must be marked with 'actual'"). Upgrade Billing together with Kotlin.

**UI**
- `NavRoutes.Plans` / `PlansScreen` / `PlansViewModel`. Entry points: Profile › Subscription Info (the "coming soon" dialog is gone), TV Settings › Subscription plans, `LocalOpenPlans`.
- "Remove ads" link (`RemoveAdsLink`) right-aligned **below** the Home and Player banners, only while the slot shows a loaded banner or the Shopee fallback.
- Send-to-TV sheet and TV & devices › Sync: "Unlimited …" instead of the counter for Unlimited; "Get Unlimited" row after the rewarded task when the quota is used up.
- Unlimited purchase needs sign-in (button "Sign in to subscribe"); No ads can be bought signed out.
- Strings: `strings_subscriptions.xml` in all 7 locales.

**Server** (`telegram-bot/src/billing/`, not deployed): see its `README.md`. `POST /billing/verify` (Firebase ID token) and `POST /billing/rtdn` (Pub/Sub push, OIDC). Writes `billing_purchases/{sha256(token)}`, `billing_accounts/{sha256(uid)}` and `users/{uid}/entitlements/current`. First claim wins; account-hash mismatch → 403; already linked elsewhere → 409.

**Rules** (`firestore.rules`): `users/{uid}/entitlements/*` owner read, no client writes; `validQuota(uid)` allows 200 sends / 50 syncs when `entitlements/current` says `plan == 'unlimited'`, `active == true`, `expiresAt > request.time`. Free and No ads unchanged. The extra `get()` only runs when the free caps are exceeded.

**Docs:** PRD; `play-store/data-safety.md` (Financial info › Purchase history: collected, not shared, optional); `listing-en.md` / `listing-vi.md` (In-app purchases: Yes, new description paragraph); `RELEASE-CHECKLIST.md`.

## What you need to do

### Play Console
1. **Monetize › Products › Subscriptions › Create subscription** `tsiptv_noads` ("Không quảng cáo") and `tsiptv_unlimited` ("Unlimited"). Set **Monetize › Monetization setup** payments profile first if not done.
2. For each: base plans **`monthly`** (auto-renewing, 1 month) and **`yearly`** (auto-renewing, 1 year); set prices (Q1); per base plan enable **grace period** (7 days), **account hold** (30 days), resubscribe; pause optional. Activate the base plans.
3. Optional free trial (Q2): add an offer (e.g. id `trial`) on the base plan, phase "Free trial 7 days", eligibility "New customer acquisition: never had this subscription". The app shows it only when Play returns it (eligible users).
4. **Settings › License testing:** add the tester Gmail accounts, response "RESPOND_NORMALLY". `android.test.purchased` and similar static ids are for one-time products only; subscriptions are tested with license testers.
5. **Testing › Internal testing:** upload a signed release (same `applicationId` `tss.t.tsiptv`, versionCode higher than production), add the testers to the track, open the opt-in link on the test device, install from Play. Prices load only for a build installed from a Play track (or the same package/version signed by the same key) with a Play account that is a license tester.
6. Test cards in the purchase sheet for license testers: "Test card, always approves", "always declines" (→ grace period / account hold), "slow test card" (→ pending). Renewal speed for testers: 1 month = **5 minutes**, 1 year = **30 minutes**, trials 3 minutes, at most 6 renewals; grace and hold are shortened likewise.
7. Test plan in PRD §12 (buy, upgrade No ads → Unlimited prorated, downgrade deferred, cancel and expiry, decline → grace / hold, pending, restore on a 2nd device, TV purchase with D-pad).
8. App content: Data safety per `play-store/data-safety.md`; listing paragraph from `listing-*.md`; privacy policy and terms: add the texts from PRD §8 to `web/public/privacy/` and `web/public/terms/`.

### Google Cloud / server (only when you want server verification)
Steps are in `telegram-bot/src/billing/README.md` › Deploy. In short:
1. In the Cloud project linked to Play Console, enable **Google Play Android Developer API**.
2. Create a service account; in Play Console › Users and permissions invite its email with *View financial data* and *Manage orders and subscriptions*.
3. Deploy the billing service (Cloud Run with that service account, or a key file), env `BILLING_PACKAGE_NAME`, product ids, `BILLING_PUSH_AUDIENCE`, `BILLING_PUSH_SERVICE_ACCOUNT`.
4. Pub/Sub topic (e.g. `play-rtdn`); grant `google-play-developer-notifications@system.gserviceaccount.com` the Publisher role; push subscription to `https://<host>/billing/rtdn` with OIDC auth (service account + audience); a dead-letter topic is recommended.
5. Play Console › Monetization setup › **Real-time developer notifications**: topic name, *Send test notification*.
6. Build the app with `TSIPTV_BILLING_VERIFY_URL=https://<host>/billing/verify`.
7. Deploy the rules: `cd firestore-tests && npm test`, then `firebase deploy --only firestore:rules`.

**Until the server runs** the app trusts the local Play state on that device (PRD §5.3): a patched APK can unlock "no ads", and Unlimited stays within 8 sends / 3 syncs a day because the rules don't know the plan. A subscription bought on Android does not follow the account to other devices yet.

## Open decisions (PRD §13)
- **Q1 prices** (suggested No ads 25.000 ₫ / 199.000 ₫, Unlimited 49.000 ₫ / 399.000 ₫ month / year).
- **Q2 trial** (suggested 7 days on Unlimited only).
- **Q3 device limit:** kept at 4 for every plan in this release; proposal: 6 for verified Unlimited (one rules change + `DeviceLimitPolicy`).
- **Q4 fair-use caps** 200 sends / 50 syncs a day for Unlimited.
- **Q5 family sharing** (suggested off), **Q6 server host** (Cloud Run assumed), **Q7** extra quota for No ads (not built).

## Known limits
- iOS (StoreKit 2) and desktop purchases not built; they show "not available" and honour a server entitlement. iOS not compiled here.
- The base plan of a local Play purchase is unknown to the client (Play does not say); without the server the upgrade uses `WITH_TIME_PRORATION` (always valid) instead of `CHARGE_PRORATED_PRICE`, and "Current plan" marks both base plans of the owned product.
- A purchase made on another Play account while signed in as a different user still unlocks this device locally (PRD §10); the server refuses to link it to a second account.
- "Remove ads" link is not shown on native rows (by design) and was checked in code only (the AVD had no playlist / ad fill).
