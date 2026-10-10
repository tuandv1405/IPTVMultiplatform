# PRD: Subscriptions ("Không quảng cáo" and "Unlimited")

Status: **draft for the user's review** · Date: 2026-10-10 · Owner: PO + Dev (subscriptions)
Related: `docs/prd-admob.md` (ads layer, `AdsGate`, 24 h gate, Shopee fallback),
`docs/prd-tv-cast-and-sync.md` (`QuotaPolicy`, rewarded tasks, quota rules, device limit §4, §8
"Entitlements (paid phase)"), `play-store/data-safety.md`.

---

## 1. Goals and non-goals

**Goals**
- Two auto-renewing plans so users can pay to remove ads, or to remove ads **and** the daily caps.
- One entitlement model in common code that every gate reads (ads, quotas, rewarded tasks,
  device limit), so a plan change takes effect everywhere at once.
- Google Play Billing on Android (phone, tablet and Android TV), following Play's subscription
  and payments policies.
- A server path that makes the entitlement trustworthy (Play Developer API + Real-time developer
  notifications) and lets the Firestore rules lift the quota caps only for verified Unlimited users.

**Non-goals (this release)**
- iOS (StoreKit 2) and desktop purchases: later (§9). They compile with "not available" stubs and
  already **honour** a server entitlement bought on Android.
- One-time ("lifetime") products, promo codes UI, family sharing (open question Q5).
- Paid features beyond ads and quotas.

---

## 2. Plans and entitlements

| | Free | **Không quảng cáo** (No ads) | **Unlimited** |
|---|---|---|---|
| Play product id (configurable, §5.1) | – | `tsiptv_noads` | `tsiptv_unlimited` |
| AdMob app open, banners, native ads | after the 24 h ad-free start | **none** | **none** |
| Shopee affiliate fallback | after 24 h | **none** | **none** |
| "Remove ads" link near ad slots | shown | – (no ads) | – |
| Rewarded-ad tasks (extra sends / syncs) | offered | still offered (opt-in; they are the only ads such a user can see, and only when they tap the task) | **hidden** (not needed) |
| Send playlist to TV | 3 / day (+1 per rewarded ad, ≤ 5) | same as Free | **unlimited** in the UI; fair-use cap 200 / day in the rules (§6.2) |
| Device sync push | 1 / day (+1 per 2 rewarded ads, ≤ 2) | same as Free | **unlimited** in the UI; fair-use cap 50 / day in the rules |
| Signed-in devices per account | 4 | 4 | 4 (**open question Q3**: 6) |
| Needs an account to buy | no | no | **yes** (its features need an account) |

**Plan order:** `FREE < NO_ADS < UNLIMITED`. Every gate asks "at least NO_ADS?" (`noAds`) or
"UNLIMITED?" (`unlimited`), never the product id.

### 2.1 The entitlement object (common code)

```
Entitlement(
  plan: FREE | NO_ADS | UNLIMITED,
  source: NONE | CACHE | PLAY | SERVER,   // where the plan comes from
  verified: Boolean,                     // source == SERVER (the rules know it)
  status: ACTIVE | IN_GRACE_PERIOD | CANCELED | ON_HOLD | PAUSED | EXPIRED | PENDING | NONE,
  productId, basePlanId?, expiresAtMs?, autoRenewing?
)
```

Resolution (`EntitlementResolver`, pure, unit-tested):
1. **Server document** `users/{uid}/entitlements/current` (signed-in only): counts when
   `active == true` and `expiresAt > now`.
2. **Play** (this device's Play account): purchases from `queryPurchasesAsync(SUBS)` with
   `purchaseState == PURCHASED` **and** `isAcknowledged` (or acknowledged by us just now). Pending
   purchases never entitle.
3. **Cache:** the last confirmed plan in local storage, used only until Play answers at start-up
   (so the app open ad and banners never flash for a subscriber) and while Play is unreachable,
   for at most **72 h** after the last confirmation.
4. Result = the **highest** plan among (1) and (2); (3) only when neither has answered yet or Play
   is unavailable. On a tie the server wins (`verified = true`).

**Multi-device / cross-platform:** the entitlement follows the **signed-in account** through the
server document, on every platform (Android, TV, iOS, desktop). Without the server, or when not
signed in, it stays with **this device's Play account** only.

**Linking a purchase to an account:** the purchase flow sets `obfuscatedAccountId =
SHA-256(uid)` (hex, 64 chars) when signed in. The server maps that hash to the uid (`billing_accounts`
collection, written when a signed-in client verifies). A purchase made signed out has no account id;
it is linked to the first account that verifies it from the app afterwards (first claim wins; §7.2).

### 2.2 Effect on every gate

| Gate | Rule |
|---|---|
| `AdsGate` | `noAds` ⇒ `AdsState.NONE` (no AdMob, no Shopee fallback). The gate waits for the first entitlement value (cache read, a few ms) before it decides, so `MobileAds.initialize` and the app open request **never run** for a subscriber from a cold start. Becoming entitled mid-session removes every slot at once (banners destroyed with their composables; the app open ad is not shown because `adMobAllowedNow` is false). Losing the entitlement brings ads back immediately (the 24 h rule still applies). |
| Rewarded tasks (`RewardAvailability`) | Unlimited ⇒ the task rows are not shown at all. NO_ADS ⇒ still available. Because a NO_ADS user's SDK was never initialised, the rewarded gateway starts the SDK on demand (without the app open ad). |
| `QuotaPolicy` | Unlimited + verified ⇒ fair-use caps (200 sends, 50 syncs). Unlimited **not** verified (server not deployed, or not yet processed) ⇒ the most the current rules allow: 8 sends and 3 syncs a day, without watching ads (the client raises `sendRewards` / `syncRewards` together with the use, which the rules allow; see §6.3). Free / NO_ADS ⇒ unchanged. |
| Quota UI | Unlimited ⇒ "Không giới hạn" instead of "Còn N lượt", no tasks. Free / NO_ADS ⇒ when the quota is used up, a **"Nâng cấp Unlimited"** button next to the rewarded task. |
| Device limit | Unchanged (4) in this release; Q3. |

---

## 3. User experience

### 3.1 Plans screen ("Gói đăng ký")
Route `NavRoutes.Plans(entry)`; phone and TV layouts.
- **Header:** current plan and status ("Bạn đang dùng gói Unlimited · Gia hạn ngày 12/11/2026",
  "Đã huỷ gia hạn · Hết hạn ngày …", "Thanh toán đang gặp sự cố, hãy cập nhật trong Google Play"
  for grace period / account hold when the server reports it).
- **Plan cards:** two cards (No ads, Unlimited) with the feature list of §2 and, per card, the base
  plans from Play: `formattedPrice` / period ("49.000 ₫ / tháng", "399.000 ₫ / năm"). Prices are
  **only** from `ProductDetails`; nothing is hard-coded. A free trial offer, when Play says the
  user is eligible (the offer is returned), is shown as "Dùng thử miễn phí 7 ngày, sau đó
  49.000 ₫ / tháng".
- **Buttons:** "Đăng ký" / "Nâng cấp" / "Chuyển gói" / "Gói hiện tại" (disabled).
- **Disclosure (above the buttons, always visible, not in small grey text):**
  "Gói tự động gia hạn cho đến khi bạn huỷ. Bạn có thể huỷ bất cứ lúc nào trong Google Play
  (Thanh toán và gói thuê bao). Nếu có dùng thử, bạn sẽ không bị tính phí nếu huỷ trước khi hết
  thời gian dùng thử."
- **Links:** Điều khoản (`/terms/`), Quyền riêng tư (`/privacy/`), **Quản lý gói trong Google Play**
  (`https://play.google.com/store/account/subscriptions?sku=<productId>&package=tss.t.tsiptv`, or
  without `sku` when no plan), **Khôi phục giao dịch** (Restore: re-queries Play and re-verifies).
- Not available (iOS, desktop, no Play Store, billing unavailable): the cards show the features,
  with "Gói đăng ký hiện chỉ mua được trong ứng dụng Android (Google Play)." No price, no button,
  no link to buy elsewhere (App Store rule). A server entitlement still shows as the current plan.
- **Unlimited when signed out:** the button reads "Đăng nhập để đăng ký" and opens Login.
- **TV:** single column, every control focusable with the D-pad, the first enabled plan button
  focused, Back closes. Play Billing's purchase sheet works on Android TV.

### 3.2 Entry points
1. **Profile › Thông tin đăng ký** (replaces the "coming soon" dialog). TV: Settings › "Gói đăng ký"
   (the TV layout's Settings dialog; the phone layout reaches it through Profile).
2. **"Xoá quảng cáo" link** under the Home banner and the Player banner: a small text button,
   right-aligned **below** the slot (never over the ad, never styled like the ad, never inside the
   ad view), shown only while that slot shows an ad or the Shopee fallback. Not on native rows (the
   lists stay clean), not on TV (no ads).
3. **Quota screens:** the send-to-TV sheet and *TV & thiết bị › Đồng bộ*: a "Nâng cấp Unlimited" row
   ("Gửi và đồng bộ không giới hạn, không quảng cáo") when today's quota is used up, after the
   rewarded task. Nothing is shown while uses remain (no nagging).

### 3.3 Purchase flow
1. User taps a base plan → if signed in, `obfuscatedAccountId = SHA-256(uid)`.
2. Upgrade / downgrade: `SubscriptionUpdateParams(oldPurchaseToken, replacementMode)`:
   | From → to | Replacement mode | Why |
   |---|---|---|
   | No ads → Unlimited, new price per day higher | `CHARGE_PRORATED_PRICE` | immediate access, the price difference for the rest of the period (Play allows this mode only when the new plan costs more per unit of time) |
   | No ads → Unlimited, new price per day not higher (e.g. monthly → a cheap yearly) | `WITH_TIME_PRORATION` | immediate access, the unused time is credited |
   | Unlimited → No ads | `DEFERRED` | the user keeps what they paid for until renewal |
   | Same product, monthly → yearly | `WITH_TIME_PRORATION` | starts the year now, the unused month is credited |
   | Same product, yearly → monthly | `DEFERRED` | |
   | none → any | – (new purchase) | |

   The price per day is computed from `ProductDetails` (`priceAmountMicros` / `billingPeriod`) by the
   pure `ReplacementPolicy`.
3. `PurchasesUpdatedListener`: `OK` → for each purchase: `PURCHASED` → acknowledge (if not yet),
   send the token to the server verifier (when configured), refresh; `PENDING` → "Đang chờ thanh
   toán" notice, no entitlement; `USER_CANCELED` → nothing; `ITEM_ALREADY_OWNED` → restore;
   others → "Không mua được. Hãy thử lại sau."
4. `queryPurchasesAsync` on app start and every resume (catches renewals, cancellations, purchases
   made on another device with the same Play account, pending → purchased).

### 3.4 Lifecycle states (Google Play)
| State | Play client (`queryPurchasesAsync`) | Server (`subscriptionsv2`) | App |
|---|---|---|---|
| Active | returned | `SUBSCRIPTION_STATE_ACTIVE` | entitled |
| Canceled (auto-renew off, before expiry) | returned, `isAutoRenewing=false` | `CANCELED`, `expiryTime` future | entitled; "Hết hạn ngày …" |
| Grace period (payment failed, Play retries) | returned | `IN_GRACE_PERIOD` | entitled; "Hãy cập nhật thanh toán" + manage link |
| Account hold | not returned | `ON_HOLD` | **not** entitled; same notice |
| Paused | not returned | `PAUSED` | not entitled; "Gói đang tạm dừng đến …" |
| Expired / revoked / refunded | not returned | `EXPIRED` / voided | not entitled |
| Pending (slow payment method) | returned, `PENDING` | `PENDING` | not entitled; "Đang chờ thanh toán" |

Enable in Play Console per base plan: grace period (recommended 7 days), account hold (30 days),
pause (optional), resubscribe.

### 3.5 Restore purchases
Same Play account: automatic (`queryPurchasesAsync`). New device with another Play account: the
server entitlement of the signed-in account applies. The button re-runs both and re-verifies.

---

## 4. Platforms
- **Android phone / tablet / TV (Play builds):** Play Billing Library **8.0.0** (`billing-ktx`).
  Billing 8 needs `enablePendingPurchases(PendingPurchasesParams…enableOneTimeProducts())`
  and offers `enableAutoServiceReconnection()`. 8.3.0 and 9.x exist, but they pull
  `kotlin-stdlib` 2.2.10, newer than the project's Kotlin 2.2.0 compiler, which broke the Android
  compile (Room's generated `actual` constructor). Move to 9.x together with a Kotlin upgrade, before
  Play's deadline for 8.x (about two years after its release).
- **iOS:** later (StoreKit 2, same product ids, App Store Server Notifications v2 to the same
  server, which writes the same entitlement document). Now: `UnavailableBillingGateway`.
- **Desktop:** no store; `UnavailableBillingGateway`; a server entitlement still applies.

---

## 5. Billing details

### 5.1 Products (create in Play Console › Monetize › Subscriptions)
| Product id | Base plans | Offers (optional) |
|---|---|---|
| `tsiptv_noads` | `monthly` (P1M, auto-renewing), `yearly` (P1Y, auto-renewing) | `trial` – free trial, new customers only (Q2) |
| `tsiptv_unlimited` | `monthly`, `yearly` | `trial` |

- Ids are **configurable** at build time: `TSIPTV_BILLING_NOADS_ID`, `TSIPTV_BILLING_UNLIMITED_ID`
  (Gradle property, `local.properties` or environment; defaults above), so a test project can use
  other ids.
- Prices: set in Play Console per country (suggested in Q1). The app shows `formattedPrice` only.
- Tags: base plans `monthly` / `yearly` are recognised by id or by `billingPeriod` (P1M / P1Y).

### 5.2 Test purchases
`android.test.purchased` and the other static ids are for **one-time** products only; they do not
exist for subscriptions. Testing uses **license testers** (Play Console › Settings › License
testing) on a build installed from a **testing track**; test renewals are accelerated (monthly =
5 minutes, yearly = 30 minutes, up to 6 renewals; trials 3 minutes; grace and hold also shortened).
Steps in the hand-off.

### 5.3 Server verification and RTDN (`telegram-bot/src/billing/`)
Client-side entitlement can be faked (a patched APK can skip every check), so the trusted path is:
1. **`POST /billing/verify`** (Firebase ID token in `Authorization`): body `{productId, purchaseToken}`.
   The server calls `purchases.subscriptionsv2.get` (Android Publisher API), checks the package,
   the product, the account hash (if present it must equal SHA-256(uid)), links the purchase to the
   uid (first claim wins), acknowledges it if Play still says pending acknowledgement, and
   recomputes the user's entitlement.
2. **`POST /billing/rtdn`** (Pub/Sub push, OIDC-authenticated): every subscription notification
   (renewed, canceled, in grace, on hold, paused, revoked, expired, recovered, restarted, price
   change, pending purchase canceled) re-reads the subscription and recomputes the entitlement of
   its linked user; voided purchases revoke. Unknown tokens are linked through the account hash, or
   ignored (acknowledged) when unlinked.
3. Firestore (Admin SDK):
   - `billing_purchases/{sha256(token)}`: `uid`, `productId`, `basePlanId`, `state`,
     `expiresAt`, `autoRenewing`, `linkedPurchaseTokenHash`, `replaced`, `updatedAt`; clients
     have no access (the catch-all rule).
   - `billing_accounts/{sha256(uid)}`: `uid`; no client access.
   - **`users/{uid}/entitlements/current`**: `v: 1`, `plan` (`free` / `no_ads` / `unlimited`),
     `active`, `state`, `productId`, `basePlanId`, `expiresAt` (timestamp), `autoRenewing`,
     `source: "google_play"`, `updatedAt`. **Read-only** for the owner.
4. Not deployed in this release; the deploy steps are in the hand-off.

**Unlimited is sold only once the server is deployed (QC round 1).** The app offers
`tsiptv_unlimited` only when `TSIPTV_BILLING_VERIFY_URL` is configured; otherwise the Unlimited
card says "Coming soon". All copy (cards, quota screens, Terms, listing) states the fair-use caps:
up to 200 sends and 50 syncs a day.

**Until the server is deployed** the app trusts the local Play purchase state on that device
(acknowledged, `PURCHASED`). Risk: a modified APK can unlock "no ads" and the client-side Unlimited
view. Impact is bounded: ads are our revenue, but the Firestore caps still hold (§6.3), so a
fake Unlimited gets at most 8 sends / 3 syncs a day, which a modified client can already get today.

---

## 6. Firestore rules

### 6.1 Entitlement documents
```
match /users/{uid}/entitlements/{doc} { allow read: if isOwner(uid); allow write: if false; }
```
and `entitlements` is excluded from the owner wildcard (`users/{uid}/{coll}/**`).

### 6.2 Quotas
`isUnlimited(uid)` = the entitlement document exists, `plan == 'unlimited'`, `active == true`,
`expiresAt is timestamp && expiresAt > request.time`. In `validQuota`:
- Unlimited: `sends ≤ 200`, `syncs ≤ 50` (fair use; still +≤1 per write, still no delete).
- Otherwise unchanged: `sends ≤ 3 + sendRewards`, `syncs ≤ 1 + syncRewards`, rewards ≤ 5 / 2.
The sync slot rule (one more counted sync per push) is unchanged.

### 6.3 Unverified Unlimited
Without the server the rules see a Free user. The client therefore pairs each use beyond the free
amount with one reward step (`sendRewards+1` with `sends+1`), which the rules accept up to the
existing caps. This is honest about what the rules can check and gives paying users 8 / 3 a day
until the server runs.

---

## 7. Security and privacy
1. The purchase token is sent only to our server (TLS) and stored there only as a SHA-256 hash for
   lookup, plus the raw token inside `billing_purchases` (needed to re-query Play on RTDN). It never
   goes to analytics or logs.
2. First-claim linking: a token already linked to another uid is refused (`409`), so one purchase
   cannot unlock many accounts. A purchase with an account hash can only be linked to that account.
3. The account hash is SHA-256 of the uid: no email or personal data goes to Google Play.
4. RTDN endpoint: Pub/Sub push with an OIDC token whose audience and service-account email are
   checked; anything else gets `401`.
5. Entitlement documents are written only by the Admin SDK.

---

## 8. Policy
- **Subscriptions policy:** price, period, auto-renewal, trial length and what happens after the
  trial are shown before the purchase button; cancellation is explained and linked (Play's
  subscription centre deep link); the plan cards list exactly what the plan unlocks; no dark
  patterns (no pre-selected yearly plan disguised as monthly, no countdowns).
- **Payments policy:** removing ads and lifting quotas are digital features ⇒ Google Play Billing
  on Play builds; no links to other payment methods.
- **Ads policy:** the "Remove ads" link sits outside the ad, below it, in the app's own style; it
  cannot be mistaken for the ad or cause accidental clicks.
- **Data safety:** declare **Financial info › Purchase history** (collected, not shared, app
  functionality / account management); see `play-store/data-safety.md`.
- **Store listing:** "Contains ads · In-app purchases" (Play Console › App content › In-app
  purchases is shown automatically once products exist).
- **Privacy policy (suggested text, add to `/privacy/`):** "Gói đăng ký: khi bạn mua gói trên
  Android, Google Play xử lý thanh toán; chúng tôi không nhận thông tin thẻ. Chúng tôi lưu mã giao
  dịch, gói, trạng thái và ngày hết hạn, gắn với tài khoản của bạn (nếu đã đăng nhập) để mở khoá
  tính năng trên các thiết bị. Dữ liệu này được xoá khi bạn xoá tài khoản." (EN in the hand-off.)
- **Terms (suggested):** auto-renewal, cancellation via Google Play, refunds per Google Play's
  policy, fair-use limits of Unlimited (200 sends / 50 syncs a day), features may change with notice.

---

## 9. Later: iOS (StoreKit 2)
Same product ids as auto-renewable subscriptions in one subscription group (levels: Unlimited 1,
No ads 2, so upgrades are immediate and downgrades deferred). `Transaction.currentEntitlements`
locally; App Store Server Notifications v2 to `/billing/appstore` writing the same document;
`appAccountToken` = a UUID derived from the uid. Restore = `AppStore.sync()`.

---

## 10. Edge cases
| Case | Behaviour |
|---|---|
| Subscriber, cold start offline | Cached plan (≤ 72 h) ⇒ no ads; Play's own cache usually answers too |
| Cache older than 72 h and Play unreachable | Free until Play answers (ads may show) |
| Purchase while the app is killed | Next start's `queryPurchasesAsync` finds and acknowledges it |
| Acknowledge fails | Retried on next refresh; Play refunds unacknowledged purchases after 3 days |
| Two Play accounts on the device | The Play Store's current account is queried; restore explains it |
| Signed in as B with a purchase linked to A | Local Play entitlement still applies on this device; the server refuses to link it to B |
| Server says Unlimited, Play says nothing (other Play account) | Unlimited (server) |
| Server expired but Play still returns it (RTDN delay) | Play wins until the server catches up |
| Plan changes while the send sheet is open | Next action re-reads the policy |
| Trial already used | Play doesn't return the offer; the card shows the plain price |
| Billing unavailable (no Play Store, e.g. some TV boxes) | Cards without prices and the "not available" note |
| Refund / revoke | RTDN `REVOKED` / voided ⇒ entitlement off on next read; Play stops returning it |

---

## 11. Acceptance criteria
- **AC-SUB1** Profile › Thông tin đăng ký (phone) and Settings › Gói đăng ký (TV) open the plans
  screen; no "coming soon" dialog remains.
- **AC-SUB2** Prices, periods and trials come only from `ProductDetails`; no price is in the code
  or strings.
- **AC-SUB3** The plans screen shows the auto-renewal / cancel-anytime text, Terms, Privacy, the
  manage-subscription deep link and Restore.
- **AC-SUB4** With NO_ADS or UNLIMITED: no AdMob request of any kind, no Shopee fallback, no
  "Remove ads" link; from a cold start `MobileAds.initialize` is never called (unit test on the gate).
- **AC-SUB5** Losing the entitlement brings ads back (24 h rule and consent unchanged).
- **AC-SUB6** UNLIMITED: send and sync show "unlimited", the rewarded tasks are hidden; NO_ADS
  keeps the tasks.
- **AC-SUB7** Verified Unlimited may write up to 200 sends / 50 syncs a day; Free / NO_ADS keep
  3+5 / 1+2; the entitlement document is read-only for the client (emulator tests).
- **AC-SUB8** Unverified Unlimited gets 8 sends / 3 syncs a day without ads, within the existing rules.
- **AC-SUB9** Purchase, upgrade (prorated) and downgrade (deferred) use the replacement modes of
  §3.3; pending purchases never entitle; purchases are acknowledged.
- **AC-SUB10** `queryPurchasesAsync` runs at start and on every resume; a purchase made elsewhere
  with the same Play account appears without restarting.
- **AC-SUB11** When signed in, `obfuscatedAccountId` is SHA-256(uid); never the email or the uid.
- **AC-SUB12** The server module verifies with `subscriptionsv2.get`, handles RTDN, links first
  claim only, and writes `users/{uid}/entitlements/current` (unit tests with mocked Google APIs).
- **AC-SUB13** "Remove ads" link appears only below a banner slot that shows something, opens the
  plans screen, and never overlaps the ad.
- **AC-SUB14** iOS / desktop compile and show "not available"; a server entitlement still applies.
- **AC-SUB15** TV: the plans screen is fully usable with the D-pad.
- **AC-SUB16** All new strings exist in the 7 locales (`StringResourcesLocaleTest`).
- **AC-SUB17** `data-safety.md` declares purchase history; listing notes in-app purchases.

## 12. Test plan
- **Unit (common):** `EntitlementResolverTest` (server/Play/cache precedence, expiry, pending,
  72 h cache), `ReplacementPolicyTest`, `QuotaPolicyTest` (verified / unverified Unlimited, reward
  pairing), `AdsPolicyTest` gate cases (entitled from start ⇒ no SDK start; entitled mid-session ⇒
  NONE; expiry ⇒ ads back), `RewardTasksTest` (Unlimited hides), `PlanOfferTest` (period parsing,
  trial detection, manage URL).
- **Rules (emulator):** client cannot write `entitlements/*`; owner reads; another user cannot;
  verified Unlimited exceeds 3 sends / 1 sync; expired or inactive Unlimited does not; NO_ADS
  does not.
- **Server (node --test, mocked Google + store):** verify happy path, wrong package, wrong account
  hash, token linked to another uid, acknowledge call, RTDN renew / cancel / hold / revoke / voided,
  unlinked token, OIDC rejection, entitlement = max over linked purchases.
- **Manual (testing track, license tester):** buy each plan monthly; upgrade No ads → Unlimited;
  downgrade; cancel and watch expiry; grace period with the "declined" test card; account hold;
  pause; restore on a second device with the same account; TV purchase with the D-pad.

## 13. Open questions (for the user)
- **Q1 Prices.** Suggested: No ads 25.000 ₫ / month, 199.000 ₫ / year; Unlimited 49.000 ₫ / month,
  399.000 ₫ / year (USD 0.99 / 7.99 and 1.99 / 15.99; Play converts the rest).
- **Q2 Free trial.** Suggested: 7 days on Unlimited only, new customers once; none on No ads.
- **Q3 Device limit.** Decision for this release: **unchanged at 4 for every plan** (the limit also
  protects against account sharing, and raising it needs a server-verified entitlement in the
  device rule). Proposal for later: 6 devices for verified Unlimited (`ids.size() <= (isUnlimited ? 6 : 4)`).
- **Q4 Fair-use caps** for Unlimited: 200 sends / 50 syncs a day in the rules. OK?
- **Q5 Family sharing** (Play family library for subscriptions): suggested off for now.
- **Q6 Server host:** Cloud Run service from `telegram-bot` (same image, billing routes enabled by
  env) or a separate Cloud Function. The module supports both; Cloud Run is assumed in the hand-off.
- **Q7** Should a NO_ADS subscriber also get, say, double the free quota? (Not built; easy to add
  in `QuotaPolicy`.)
