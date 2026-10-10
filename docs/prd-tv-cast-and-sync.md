# PRD: TV cast, send to TV, device limit and device sync

Status: **draft for review** · Branch: `feature/tv-cast-and-sync` · Date: 2026-10-10
Platforms in this round: Android phone/tablet (sender) and Android TV (receiver). iOS and desktop
compile with "not supported yet" implementations.

Four features that share two foundations: a **LAN link** between a phone and a TS IPTV TV app
(A, B) and an **account layer** in Firestore (B quota, C, D).

| | Feature | Needs an account | Transport |
|---|---|---|---|
| A | Cast what is playing to a TV on the same Wi-Fi | No (pairing only) | LAN |
| B | Send a playlist to the TV | Yes (daily quota is per account) | LAN, quota in Firestore |
| C | At most 4 signed-in devices per account | Yes | Firestore |
| D | Sync playlist definitions between the account's devices | Yes | Firestore |

---

## 1. Goals and non-goals

Goals
- A viewer watching on the phone moves the stream to the living-room TV in two taps.
- A playlist typed on the phone (long URLs) reaches the TV without typing on a remote.
- The free tier stays sustainable: daily quotas, extra uses earned with **rewarded ads**.
- An account cannot be shared by an unbounded number of people (4 devices).
- A user with several devices can copy their playlist set from one device to the others.

Non-goals (this round)
- iOS / desktop **senders** (follow-up), iOS / desktop **receivers** (out of scope).
- Remote control after the cast (play/pause from the phone), casting to Chromecast / DLNA.
- Paid plans. The PRD only fixes where entitlements plug in (§8).
- End-to-end encryption of the sync payload (decision in §7.4).

---

## 2. A. Cast now-playing to a TV

### 2.1 User flow (phone)
1. While a video plays (channel, addon stream, TS IPTV Source VOD) a **TV icon** shows in the
   player's top bar. Hidden on TV layouts and on platforms without LAN support.
2. Tap → "Phát trên TV" sheet. The phone browses the LAN for TS IPTV TVs and lists them by name
   ("Phòng khách", from the TV's device name). Paired TVs show "Đã ghép nối".
3. Pick a TV:
   - **Not paired yet** → pairing (§2.4): the TV shows a 6-digit code; the phone asks for it.
   - **Paired** → the stream is sent; the sheet says "Đang phát trên <TV>"; the TV starts playing.
4. Nothing found → the sheet shows the **conditions list** (§2.3) and **"Kết nối bằng địa chỉ IP"**
   (`192.168.1.20:47123`, the address the TV shows in its *Nhận từ điện thoại* screen).

### 2.2 What is sent
`CastStream`: URL, title, logo URL, MIME type, HTTP headers, DRM spec (system, licence URL,
licence headers, ClearKey keys), side-loaded subtitles, `isLive`, and for VOD the current position
(ms). Headers and DRM are sent because the stream does not play without them; they never leave the
LAN (direct TCP between the two devices, no relay, no cloud).

### 2.3 Conditions for devices to find each other (user-facing, also in the sheet)
1. Both on the **same Wi-Fi network and subnet** (not one on Wi-Fi and one on mobile data, not a
   "Guest" network, not two different routers / mesh SSIDs that are separate subnets).
2. The router must not use **client isolation / AP isolation** (common on guest and hotel networks).
3. The router must let **multicast / mDNS** through (some mesh systems and enterprise APs block it).
   If not, use **Kết nối bằng địa chỉ IP**.
4. **TS IPTV is open on the TV** (in the foreground), or the TV is on the *Nhận từ điện thoại* screen.
5. Both apps are on a version with this feature (protocol v1).
6. **VPN off** on both devices (a VPN moves traffic off the LAN).
7. iOS (later): the "Local Network" permission. Android: no runtime permission today; if a future
   Android version gates LAN access (local-network protection, `NEARBY_WIFI_DEVICES` /
   `ACCESS_LOCAL_NETWORK`), the app asks for it before browsing.

### 2.4 LAN protocol v1
Discovery
- mDNS / DNS-SD service type **`_tsiptv._tcp`**. Instance name: the TV's display name.
  TXT: `id` (receiver installation id), `v` (protocol version, `1`).
- Android: `NsdManager` (register on TV, discover + resolve on the phone).

Transport
- The TV runs a plain TCP server (`ServerSocket`) on a **random port**, only while the app is in the
  foreground (or on the *Nhận từ điện thoại* screen); stopped in `onPause`.
- One request per connection: a single UTF-8 JSON object terminated by `\n`, one JSON line back,
  then close. Read timeout 10 s. **Size cap**: 64 KiB per request, 1.5 MiB for a playlist offer with
  file content; above the cap the server answers `TOO_LARGE` and closes without reading the rest.
- At most 4 concurrent connections; others are closed at once.

Messages (field `type`)
| type | direction | content | answer |
|---|---|---|---|
| `hello` | phone → TV | `v` | `info`: `id`, `name`, `v` (used by connect-by-IP) |
| `pair_start` | phone → TV | `senderId`, `senderName`, `pub` (P-256 public key, base64) | `pair_challenge`: `sessionId`, `id`, `name`, `pub` |
| `pair_confirm` | phone → TV | `sessionId`, `proof` | `ok` / `WRONG_CODE` / `EXPIRED` |
| `signed` | phone → TV | `senderId`, `ts`, `nonce`, `body` (JSON string), `mac` | `ok` / error code |

Signed bodies: `{"cmd":"cast","stream":{…}}`, `{"cmd":"playlist","playlist":{…}}`, `{"cmd":"ping"}`.

Error codes: `BAD_REQUEST`, `UNSUPPORTED_VERSION`, `TOO_LARGE`, `UNPAIRED`, `BAD_SIGNATURE`,
`REPLAY`, `EXPIRED`, `WRONG_CODE`, `BUSY`, `LOCKED`, `BAD_URL`, `NOT_ACCEPTING`.

### 2.5 Security
Requirement: nobody on the LAN can push content to a TV without the TV owner's consent.

Pairing (first use per phone ↔ TV)
1. Phone sends `pair_start` with an ephemeral **ECDH P-256** public key. The TV makes its own
   ephemeral key pair and a random **6-digit code**, shows the code full-screen ("Ghép nối điện
   thoại"), and answers with its public key. Both compute
   `K = HMAC-SHA256(ECDH secret, "tsiptv-pair-v1" ‖ phonePub ‖ tvPub)`.
2. The user types the code on the phone. Phone sends `proof = HMAC-SHA256(K, "confirm" ‖ code)`.
3. TV checks it in constant time. On success both store `K` as the pair key (at rest encrypted with
   the app's `SecretCipher`: Android Keystore AES-GCM) and the TV says "Đã ghép nối với <phone>".

Limits: code valid 2 minutes; 3 wrong codes end the session; one pairing session at a time
(`BUSY`); 5 failed sessions in 10 minutes lock pairing for 10 minutes (`LOCKED`).

**Pairing mode (QC round 2).** The TV accepts `pair_start` only while its *TV & devices*
screen (*Nhận từ điện thoại* section) is shown and resumed; anywhere else the answer is
`PAIRING_CLOSED` and the phone says "To pair, open TV & devices on the TV, then try again." So no
LAN client can put the full-screen code over playback or Home. Leaving the screen drops an open
code. Already paired phones cast and send playlists from anywhere (signed requests). After the TV
user cancels a code there is a 30 s cooldown (`BUSY`); codes that run out unused escalate it
(30 s, 60 s, 2 min … up to 10 min; reset by a successful pairing).

After pairing every command is `signed`:
`mac = HMAC-SHA256(K, "tsiptv-v1\n" + senderId + "\n" + ts + "\n" + nonce + "\n" + body)`.
The TV rejects: unknown sender (`UNPAIRED`), `|now − ts| > 120 s` (`EXPIRED`), a nonce seen within
the window (`REPLAY`, cache of the last 512 nonces), a bad MAC (`BAD_SIGNATURE`).

What this protects against
- An unpaired device on the LAN: must guess the code online (3 tries per session, rate-limited).
- A passive sniffer, also during pairing: the code is never sent; the proof needs `K` (ECDH).
- Replays and late delivery of captured commands: timestamp window + nonce cache.

Residual risk (documented, accepted for v1): an **active man-in-the-middle present during the
2-minute pairing** (ARP spoofing on the user's Wi-Fi) can brute-force the 6 digits offline from the
proof. Fix for a later version: a PAKE (SPAKE2) or a confirmation fingerprint on both screens. The
commands are not encrypted on the LAN (signed only): stream URLs and headers are visible to a
sniffer on the same Wi-Fi — the same exposure as the player's own HTTP requests to the stream.

Optional, not built: auto-trust devices signed into the same account (would need the account's
device list on the TV and a signature by an account key; follow-up).

Other rules: incoming URLs (stream, logo, licence, subtitles, playlist, EPG) must be `http(s)`,
≤ 4096 chars, no whitespace/control characters; at most 32 headers, names are HTTP tokens, values
have no CR/LF and are ≤ 2048 chars. **Nothing logs URLs, headers, keys, codes or tokens**;
`toString()` of every message redacts them.

### 2.6 TV side
- Cast from a paired phone plays at once (paired = consent given), opens the TV player and shows
  "Đang phát từ <phone>". VOD starts at the sent position.
- *Settings → Nhận từ điện thoại* (D-pad): receiver status, TV name, `IP:port` for
  connect-by-IP, the conditions list, paired phones with **Bỏ ghép nối**.

---

## 3. B. Send a playlist to the TV

### 3.1 Flow
- Phone: **"Gửi tới TV"** (icon on each row of *Chọn danh sách phát*, and a button in
  *Thêm danh sách phát* that sends the typed name + link, or a picked file).
- Requires sign-in (quota per account). The sheet shows **"Còn N lượt gửi hôm nay"** and, below the
  limit, **"Làm nhiệm vụ để nhận thêm lượt gửi"** → "Xem 1 quảng cáo có thưởng để nhận thêm 1 lượt gửi".
- TV discovery and pairing exactly as in A. The phone sends `{"cmd":"playlist"}` with
  `SharedPlaylist`: name, URL, EPG URLs, headers (reserved: playlists carry no headers today), and for
  a file playlist its **content** (≤ 1 MiB text) and file name.
- TV shows a D-pad dialog: **"Nhận danh sách phát "<name>" từ <phone>?"** [Nhận] [Từ chối]. On
  Nhận it imports through the normal importer (`HomeViewModel.parseIptvSource` / `importFile`), so
  TS IPTV Sources get their usual preview / 18+ dialogs.
- A file playlist **already imported** cannot be sent from the list: the app never keeps file
  content (F1). The row says so and points to *Thêm danh sách phát → chọn tệp → Gửi tới TV*.
- QC round 2: the accepted playlist is imported **in the background** (the TV stays where it is,
  a notice says "Adding "<name>"…"); the result shows as a dialog over any screen. Only an import
  that needs the user (a single stream, a TS IPTV Source preview, an error) opens *Thêm danh sách
  phát*. While an import from a phone runs, new offers get `NOT_ACCEPTING` ("TV is busy").

### 3.2 Quota
- **3 sends per day per account**, reset at **local midnight** of the sending phone.
- The user's rule was "per Wi-Fi network"; the count is per account (a network is not an identity
  we can verify), and each send **records the network** (a hash of the TV's /24 subnet) in the
  quota document for later analysis.
- Counted when the TV accepted the delivery (`ok` answer), not when the user confirms on the TV.

### 3.3 Reward tasks — POLICY DECISION (lead)
The user asked for tasks like "click 1 banner ad" and "watch 1 interstitial for 30 s". We do **not**
build these:
- Rewarding or encouraging **ad clicks** is invalid traffic under AdMob program policies and risks
  suspension of the AdMob account.
- Interstitials cannot be forced to a duration, and rewarding them is not allowed either.

AdMob's format for "watch an ad to earn something" is the **Rewarded ad**. Tasks are therefore:
- Send: **"Xem 1 quảng cáo có thưởng để nhận thêm 1 lượt gửi"** (max 5 rewards / day).
- Sync: **"Xem 2 quảng cáo có thưởng để nhận thêm 1 lượt đồng bộ"** (max 2 extra syncs / day).

A reward is granted only on the SDK's `onUserEarnedReward`. All of this sits behind the common
interface `RewardedAdGateway` (`isAvailable`, `show(placement): RewardedAdResult`). This branch ships
`FakeRewardedAdGateway` (debug: grants after a short delay; release: reports "unavailable"); the lead
wires AdMob `RewardedAd` to it after the ads branch merges. No AdMob SDK in this branch.

---

## 4. C. At most 4 signed-in devices per account

### 4.1 Data
`users/{uid}/devices/{deviceId}`: `platform` (`android_phone`, `android_tv`, `ios`, `desktop`),
`name`, `appVersion`, `createdAt`, `lastSeen` (ms).
`users/{uid}/meta/devices`: `ids` — the list of registered device ids (≤ 4).
Device id: a random installation id (UUID v4) in local storage, regenerated on reinstall. **No
hardware identifiers.**

### 4.2 Flow
- On sign-in (and at each start while signed in) the app checks the registry:
  - this device registered → update `lastSeen` (at most every 6 h);
  - not registered and < 4 → register;
  - not registered and 4 → **"Đã đạt giới hạn 4 thiết bị"** dialog listing the 4 devices (name,
    platform, last active) with **"Đăng xuất từ xa"** per device, and **"Huỷ đăng nhập"**
    (signs this device out). D-pad navigable on TV.
- A device registered earlier whose entry is gone (removed from another device) **signs itself out**
  on its next start or check, with "Thiết bị này đã bị đăng xuất từ một thiết bị khác."
- Settings → *TV & thiết bị → Thiết bị đã đăng nhập* lists devices and allows remote sign-out.
- Sign-out on this device removes its own entry (frees the slot).

### 4.3 Rules and the counting trade-off
Rules cannot count a collection, so the count lives in `meta/devices.ids`:
- adding a device = one batch that creates `devices/{id}` **and** appends `id` to `ids`
  (`ids` grows by exactly that id, `size() ≤ 4`, the device doc must exist after and not before);
- removing = one batch that deletes `devices/{id}` **and** removes `id` from `ids`;
- a device doc can be created only if its id is in `ids` after the batch, deleted only if it is not.
Trade-off: concurrent sign-ins on two devices race on `meta/devices`; Firestore makes the second
batch fail (the rule sees the other's write), and the app retries once with fresh data.
The 4-device cap is enforced by the rules; **signing out** a removed device is client-side: a
modified client could ignore it (its Firebase token stays valid). Real revocation needs the Admin SDK
(`revokeRefreshTokens`) in a Cloud Function or the telegram-bot server (paid phase, §8).

---

## 5. D. Device sync of playlists

### 5.1 Push ("Đồng bộ thiết bị")
- Signed-in user taps **"Đồng bộ danh sách phát lên tài khoản"**. The payload holds playlist
  **definitions only**: id, name, URL, EPG URLs, headers, source type, format, last update. Never
  channels or streams.
- File playlists are **skipped with a note** ("N danh sách nhập từ tệp không được đồng bộ"): their
  content is not kept after import, so there is nothing small to sync.
- Stored at `users/{uid}/sync/current`: `v`, `fromDeviceId`, `fromDeviceName`, `createdAt`,
  `playlistCount`, `payload` (JSON string, ≤ 256 KiB, ≤ 100 playlists). One slot per account; a
  push replaces it.

### 5.2 Receive
- On start (and when the *TV & thiết bị* screen opens) a device compares `sync/current.createdAt`
  with the last sync it applied or pushed. Newer and from another device → banner
  **"Có bản đồng bộ mới từ <device>"** with **Gộp**, **Thay thế**, **Để sau**.
- **Gộp (merge)**: union by playlist id (= hash of the URL for links, `tsiptv:<id>` for sources).
  Same id on both sides: the newer `lastUpdated` wins (name and EPG list); nothing is deleted.
- **Thay thế (replace)**: the local set becomes the synced set. A confirmation lists **exactly what
  will be removed** ("Sẽ xoá 2 danh sách: A, B. Sẽ thêm 3 danh sách.") — including local file
  playlists, which cannot come back.
- New playlists are downloaded through the normal importer one by one; failures are listed
  ("N danh sách không tải được") and do not stop the others. A source needing 18+ confirmation is
  reported as failed (the user imports it by hand).

### 5.3 Quota
- MVP: **1 sync push per day per account**, counted on push, reset at local midnight.
- 2nd push the same day: **2 rewarded ads = 1 extra push** (max 2 extra / day). The user asked for
  "click 2 banners + a 30 s interstitial"; replaced by rewarded ads for the policy reasons in §3.3.
- Applying a sync is free and unlimited.

### 5.4 Privacy and encryption decision
Playlist URLs often embed credentials (`user:pass@`, `?token=`). Decision for the MVP:
- `users/{uid}/sync/current` is **owner-only** (rules), transported over TLS, encrypted at rest by
  Google — but **not end-to-end encrypted**.
- A key "derived per account" without a user secret would be derivable by anyone who can read the
  document (admins, a leaked backup), so it would be obfuscation, not protection. Real E2E needs a
  user passphrase (or a key exchanged between devices over the LAN pairing) and is a follow-up.
- The payload is never logged and never sent to analytics.

---

## 6. Quotas in Firestore

`users/{uid}/quota/daily` (one document, rolled over by day):
`day` (int `yyyymmdd`, the client's local date), `sends`, `sendRewards`, `syncs`, `syncRewards`,
`syncAds` (ads watched toward the next sync reward, 0–1), `networks` (≤ 10 hashed network ids),
`updatedAt` (ms).

Rules (client-side enforcement without a backend):
- Owner-only; **no delete** (reinstalling or deleting does not reset quotas).
- `day` must be the UTC date of server time −14 h, now, or +14 h (any real time zone).
- Same day: every counter is **monotonic** and grows by at most 1 per write;
  `sends ≤ 3 + sendRewards`, `sendRewards ≤ 5`, `syncs ≤ 1 + syncRewards`, `syncRewards ≤ 2`.
- New day (`day` greater than the stored one): counters restart (each ≤ 1).
- A write to `sync/current` must, in the same batch, raise `syncs` by exactly 1 and come from a
  registered device (`fromDeviceId ∈ meta/devices.ids`).

What this **cannot** prevent without a backend (honest list):
- A modified client can claim rewards it did not earn (up to the daily caps: 8 sends, 3 syncs a day)
  — the server never sees the AdMob reward. Fix: AdMob **server-side verification (SSV)** callback to
  a Cloud Function / the telegram-bot server, which alone may raise `*Rewards`.
- Sends happen on the LAN; a modified client can send without counting them. Fix: none without a
  server in the path (the TV could check a server-signed send token: paid-phase option).
- Choosing tomorrow's date (+14 h window) gives at most one extra day's quota in a real day.
- Remote sign-out (C) is advisory, see §4.3.

---

## 7. Edge cases

| Case | Behaviour |
|---|---|
| TV app goes to background during a cast/offer | Server stops; the phone gets a connection error → "Không gửi được tới TV…" |
| TV forgot the phone (unpaired / reinstalled) | `UNPAIRED` → phone drops its key and starts pairing again |
| Phone reinstalled | New installation id → pair again; old entry stays on TV until "Bỏ ghép nối" |
| Two phones pair at once | Second gets `BUSY` ("TV đang ghép nối với thiết bị khác") |
| Clock skew > 2 min between phone and TV | `EXPIRED` → message asks to check date/time |
| Same playlist sent twice | Normal importer: same URL → same id → replaced (F1 behaviour) |
| Offer arrives while another dialog is open on TV | Queued; one dialog at a time; offers older than 2 min are dropped |
| Stream with DRM cast to a TV without DRM support | TV player shows the usual DRM error |
| Signed out on phone | Cast works (no account needed); Send/Sync ask to sign in |
| Device limit reached while offline | Check retried at next start; no lock-out while offline |
| 5 devices already registered by an older bug | Dialog lists all; user removes until < 4 |
| Sync payload > 256 KiB / > 100 playlists | Push refused with a message (no quota spent) |
| Replace would remove the playing playlist | Another synced playlist becomes current |
| Local midnight passes while a sheet is open | Next action re-reads the quota (rollover) |

---

## 8. Entitlements (paid phase)

`QuotaPolicy` takes an `Entitlement` (`extraSendsPerDay`, `extraSyncsPerDay`, `unlimited`). The MVP
passes `Entitlement.FREE`. Paid phase:
1. A Cloud Function (or the telegram-bot server) receives store purchase notifications, writes
   `users/{uid}/entitlement` (Admin SDK; clients read-only), and verifies AdMob SSV rewards.
2. Rules read the entitlement document for the caps instead of the constants.
3. Device removal calls a function that also revokes the removed device's refresh tokens.

---

## 9. Strings (vi is the reference wording; all 7 locales ship)

| Key | Tiếng Việt | English |
|---|---|---|
| `connect_title` | TV & thiết bị | TV & devices |
| `connect_desc` | Phát trên TV, gửi danh sách phát, đồng bộ | Play on TV, send playlists, sync |
| `lan_cast_title` | Phát trên TV | Play on TV |
| `lan_cast_searching` | Đang tìm TV trong cùng mạng Wi-Fi… | Looking for TVs on this Wi-Fi… |
| `lan_cast_none_found` | Không tìm thấy TV nào | No TV found |
| `lan_conditions_title` | Điều kiện để tìm thấy TV | For your TV to show up |
| `lan_conditions_body` | • Điện thoại và TV dùng cùng một mạng Wi-Fi (không dùng mạng khách)… | • Phone and TV on the same Wi-Fi (not a guest network)… |
| `lan_connect_by_ip` | Kết nối bằng địa chỉ IP | Connect by IP address |
| `lan_ip_hint` | Ví dụ: 192.168.1.20:47123 | e.g. 192.168.1.20:47123 |
| `lan_ip_invalid` | Địa chỉ không hợp lệ | Invalid address |
| `lan_connect` | Kết nối | Connect |
| `lan_cancel` | Huỷ | Cancel |
| `lan_paired_badge` | Đã ghép nối | Paired |
| `lan_pair_title` | Ghép nối với %1$s | Pair with %1$s |
| `lan_pair_enter_code` | Nhập mã 6 số đang hiển thị trên TV | Enter the 6-digit code shown on the TV |
| `lan_pair_wrong_code` | Mã không đúng. Kiểm tra lại mã trên TV. | Wrong code. Check the code on the TV. |
| `lan_pair_failed` | Không ghép nối được. Hãy thử lại. | Pairing failed. Try again. |
| `lan_pair_busy` | TV đang ghép nối với thiết bị khác | The TV is pairing with another device |
| `lan_pair_locked` | Thử ghép nối sai quá nhiều lần. Hãy đợi 10 phút. | Too many failed attempts. Wait 10 minutes. |
| `lan_cast_sent` | Đang phát trên %1$s | Playing on %1$s |
| `lan_send_failed` | Không gửi được tới TV. Kiểm tra TV còn mở TS IPTV và cùng mạng Wi-Fi. | Couldn't reach the TV. Check TS IPTV is open on it and on the same Wi-Fi. |
| `lan_error_clock` | Giờ trên điện thoại và TV lệch nhau. Hãy kiểm tra ngày giờ. | Phone and TV clocks differ. Check date and time. |
| `lan_error_rejected` | TV từ chối nội dung này | The TV refused this content |
| `lan_not_supported` | Thiết bị này chưa hỗ trợ tính năng này | Not supported on this device yet |
| `lan_tv_pair_title` | Ghép nối điện thoại | Pair a phone |
| `lan_tv_pair_message` | Nhập mã này trên %1$s để cho phép gửi nội dung tới TV này. | Enter this code on %1$s to let it send to this TV. |
| `lan_tv_paired` | Đã ghép nối với %1$s | Paired with %1$s |
| `lan_tv_cast_from` | Đang phát từ %1$s | Playing from %1$s |
| `lan_tv_offer_title` | Nhận danh sách phát | Receive a playlist |
| `lan_tv_offer_message` | Nhận danh sách phát "%1$s" từ %2$s? | Receive the playlist "%1$s" from %2$s? |
| `lan_tv_accept` | Nhận | Receive |
| `lan_tv_decline` | Từ chối | Decline |
| `lan_tv_receive_title` | Nhận từ điện thoại | Receive from phone |
| `lan_tv_receive_ready` | TV đang sẵn sàng nhận với tên "%1$s" | Ready to receive as "%1$s" |
| `lan_tv_receive_address` | Kết nối bằng IP: %1$s | Connect by IP: %1$s |
| `lan_tv_receive_off` | Chế độ nhận chưa bật được trên TV này | Receiving couldn't start on this TV |
| `lan_tv_receive_hint` | Giữ TS IPTV mở trên TV. Trên điện thoại, bấm biểu tượng TV trong trình phát, hoặc Gửi tới TV. | Keep TS IPTV open on the TV. On the phone, tap the TV icon in the player, or Send to TV. |
| `lan_tv_paired_phones` | Điện thoại đã ghép nối | Paired phones |
| `lan_tv_unpair` | Bỏ ghép nối | Unpair |
| `lan_tv_no_paired` | Chưa có điện thoại nào | No phones yet |
| `send_tv_action` | Gửi tới TV | Send to TV |
| `send_tv_title` | Gửi danh sách phát tới TV | Send playlist to TV |
| `send_tv_remaining` | Còn %1$d lượt gửi hôm nay | %1$d sends left today |
| `send_tv_quota_reached` | Bạn đã dùng hết lượt gửi hôm nay | No sends left today |
| `send_tv_tasks` | Làm nhiệm vụ để nhận thêm lượt gửi | Do a task to get more sends |
| `send_tv_task_rewarded` | Xem 1 quảng cáo có thưởng để nhận thêm 1 lượt gửi | Watch 1 rewarded ad for 1 more send |
| `send_tv_sent` | Đã gửi. Hãy xác nhận trên TV. | Sent. Confirm on the TV. |
| `send_tv_sign_in` | Đăng nhập để gửi danh sách phát tới TV | Sign in to send playlists to a TV |
| `send_tv_file_too_large` | Tệp lớn hơn 1 MB, không gửi được. Hãy gửi đường dẫn. | File larger than 1 MB. Send a link instead. |
| `send_tv_file_unavailable` | Danh sách nhập từ tệp không lưu nội dung. Hãy chọn lại tệp ở màn hình Thêm danh sách phát. | File playlists aren't kept. Pick the file again in Add playlist. |
| `send_tv_need_input` | Nhập đường dẫn hoặc chọn tệp trước khi gửi | Enter a link or pick a file first |
| `reward_ad_unavailable` | Chưa có quảng cáo. Hãy thử lại sau. | No ad available. Try again later. |
| `reward_granted` | Bạn nhận thêm 1 lượt | You got 1 more |
| `reward_limit_reached` | Hôm nay bạn đã nhận đủ lượt thưởng | No more rewards today |
| `devices_title` | Thiết bị đã đăng nhập | Signed-in devices |
| `devices_count` | %1$d/%2$d thiết bị | %1$d/%2$d devices |
| `devices_limit_title` | Đã đạt giới hạn %1$d thiết bị | Limit of %1$d devices reached |
| `devices_limit_message` | Tài khoản đang đăng nhập trên %1$d thiết bị. Đăng xuất một thiết bị để tiếp tục trên thiết bị này. | This account is signed in on %1$d devices. Sign one out to continue here. |
| `devices_sign_out_remote` | Đăng xuất từ xa | Sign out remotely |
| `devices_cancel_sign_in` | Huỷ đăng nhập | Cancel sign-in |
| `devices_this_device` | Thiết bị này | This device |
| `devices_last_seen` | Hoạt động: %1$s | Active: %1$s |
| `devices_removed_notice` | Thiết bị này đã bị đăng xuất từ một thiết bị khác. | This device was signed out from another device. |
| `devices_remove_confirm` | Đăng xuất %1$s? Thiết bị đó sẽ bị đăng xuất khi mở ứng dụng lần sau. | Sign out %1$s? It will be signed out next time it opens the app. |
| `devices_error` | Không tải được danh sách thiết bị | Couldn't load devices |
| `sync_title` | Đồng bộ thiết bị | Device sync |
| `sync_push` | Đồng bộ danh sách phát lên tài khoản | Sync playlists to my account |
| `sync_push_desc` | Lưu %1$d danh sách phát của thiết bị này để thiết bị khác áp dụng | Save this device's %1$d playlists for your other devices |
| `sync_pushed` | Đã đồng bộ %1$d danh sách phát | Synced %1$d playlists |
| `sync_skipped_files` | %1$d danh sách nhập từ tệp không được đồng bộ | %1$d file playlists were not synced |
| `sync_remaining` | Còn %1$d lượt đồng bộ hôm nay | %1$d syncs left today |
| `sync_quota_reached` | Bạn đã dùng hết lượt đồng bộ hôm nay | No syncs left today |
| `sync_task_rewarded` | Xem 2 quảng cáo có thưởng để nhận thêm 1 lượt đồng bộ (%1$d/2) | Watch 2 rewarded ads for 1 more sync (%1$d/2) |
| `sync_new_available` | Có bản đồng bộ mới từ %1$s | New sync from %1$s |
| `sync_merge` | Gộp | Merge |
| `sync_merge_desc` | Thêm danh sách còn thiếu, giữ danh sách hiện có | Add missing playlists, keep yours |
| `sync_replace` | Thay thế | Replace |
| `sync_replace_desc` | Dùng đúng bộ danh sách đã đồng bộ | Use exactly the synced set |
| `sync_later` | Để sau | Later |
| `sync_replace_confirm_title` | Thay thế danh sách phát? | Replace playlists? |
| `sync_replace_confirm_message` | Sẽ xoá %1$d danh sách: %2$s. Sẽ thêm %3$d danh sách. | %1$d playlists will be removed: %2$s. %3$d will be added. |
| `sync_applied` | Đã áp dụng: thêm %1$d, cập nhật %2$d, xoá %3$d | Applied: %1$d added, %2$d updated, %3$d removed |
| `sync_apply_failed_some` | %1$d danh sách không tải được | %1$d playlists couldn't be loaded |
| `sync_none` | Chưa có bản đồng bộ nào | No sync yet |
| `sync_current_from` | Bản đồng bộ hiện tại: %1$s, %2$s | Current sync: %1$s, %2$s |
| `sync_too_large` | Quá nhiều danh sách để đồng bộ (tối đa 100) | Too many playlists to sync (max 100) |
| `sync_sign_in` | Đăng nhập để dùng đồng bộ và giới hạn thiết bị | Sign in to use sync and devices |
| `sync_error` | Không đồng bộ được. Hãy thử lại. | Sync failed. Try again. |

---

## 10. Acceptance criteria

A — Cast
- **AC-A1** A playing item shows a TV icon on phone layouts; tapping opens the TV list.
- **AC-A2** A TS IPTV TV in the foreground on the same Wi-Fi appears by its name within 5 s.
- **AC-A3** First cast to a TV pairs: the TV shows a 6-digit code; a correct code pairs, a wrong one
  shows "Mã không đúng"; the 3rd wrong code ends the session.
- **AC-A4** After pairing, the TV plays the stream with the same headers/DRM; VOD starts at the
  phone's position (±5 s).
- **AC-A5** An unpaired client, a bad MAC, a replayed request and a request with a timestamp 3 min
  old are refused (`UNPAIRED`, `BAD_SIGNATURE`, `REPLAY`, `EXPIRED`) — unit tests.
- **AC-A6** A request larger than the cap is refused with `TOO_LARGE`; a non-http(s) URL with `BAD_URL`.
- **AC-A7** The receiver stops when the TV app leaves the foreground (port closed).
- **AC-A8** "Kết nối bằng địa chỉ IP" with the address from the TV's receive screen works when mDNS
  is blocked.
- **AC-A9** Nothing in logcat contains a stream URL, header value, key or code.

B — Send playlist
- **AC-B1** "Gửi tới TV" exists on playlist rows and in Add playlist; signed out it asks to sign in.
- **AC-B2** TV shows "Nhận danh sách phát "<name>" từ <phone>?"; Nhận imports via the normal
  importer; Từ chối imports nothing.
- **AC-B3** The 4th send of the day is refused with "Bạn đã dùng hết lượt gửi hôm nay" and the
  rewarded task; a granted reward allows exactly 1 more send.
- **AC-B4** Quota survives reinstall (stored in Firestore) and resets after local midnight.
- **AC-B5** A file > 1 MiB is refused on the phone; an imported file playlist explains why it can't be sent.

C — Devices
- **AC-C1** Signing in on a 5th device shows the limit dialog listing 4 devices.
- **AC-C2** "Đăng xuất từ xa" frees the slot and the new device registers.
- **AC-C3** The removed device signs out at its next start with the notice.
- **AC-C4** Rules: a 5th id in `meta/devices` and a device doc not listed in `meta/devices` are
  refused; another user's devices are unreadable (emulator tests).

D — Sync
- **AC-D1** Push stores definitions only (no channels), skips file playlists with a note.
- **AC-D2** Another device shows "Có bản đồng bộ mới từ <device>".
- **AC-D3** Merge adds missing playlists and keeps local ones; same id → newer wins (unit tests).
- **AC-D4** Replace lists exactly what will be removed before doing it (unit tests + UI).
- **AC-D5** The 2nd push the same day needs 2 rewarded ads; the rules refuse a push without the
  matching `syncs + 1` in the same batch (emulator tests).
- **AC-D6** `sync/current` is unreadable for another user (emulator test).

Common
- **AC-X1** All new strings in 7 locales (`StringResourcesLocaleTest`).
- **AC-X2** `desktopTest`, `assembleDebug`, `compileCommonMainKotlinMetadata` pass.
- **AC-X3** No Room schema change.

---

## 11. Test plan

Unit (commonTest, run by `desktopTest`)
- `LanSecurityTest`: MAC round-trip, wrong key, tampered body, replay, expired, future timestamps,
  constant-time compare.
- `LanPairingTest`: full pairing between `LanSender` and `LanReceiverEngine` over an in-memory
  transport (with JVM ECDH in desktopTest), wrong code ×3, busy, lockout, then signed cast and
  playlist offers, unpair → `UNPAIRED`.
- `LanValidationTest`: URLs, headers, size caps.
- `QuotaPolicyTest`: free sends, rewards, caps, rollover at midnight, sync ad pairs, entitlements.
- `SyncPlannerTest`: merge (union, newer wins, nothing removed), replace (exact removal list), file
  playlists skipped on push, payload caps.
- `DeviceLimitPolicyTest`: registered / slot free / limit reached / removed-elsewhere.
- `StringResourcesLocaleTest.castSyncKeysArePresent`.

Rules (firestore-tests, emulator)
- devices: create with meta, 5th refused, unlisted device refused, remove frees slot, other user denied.
- quota: monotonic, caps, no delete, implausible day refused, new day restarts.
- sync: owner-only, needs `syncs + 1` in the same batch, needs a registered device.
- regression: `users/{uid}/deactiveRequest` still writable by the owner.

Manual / E2E
- Phone AVD `Pixel_10a` → TV AVD `TSLauncherReferenceTV` on one host. Emulator NAT (10.0.2.x per
  AVD) blocks mDNS between AVDs: use **connect by IP** with an adb port forward
  (`adb -s <tv> forward tcp:<p> tcp:<p>`, then from the phone `10.0.2.2:<p>`).
- Real devices on a home router (mDNS path), a guest network (expect "not found" + conditions).
