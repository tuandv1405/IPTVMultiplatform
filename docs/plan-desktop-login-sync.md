# Kế hoạch: đăng nhập trên máy tính và đồng bộ với điện thoại

Ngày: 2026-10-13. Trạng thái: **kế hoạch, chưa triển khai.**

**Phạm vi:**
- App desktop (Windows, macOS): tích hợp Firebase thật; đăng nhập bằng email, hoặc quét QR bằng điện thoại.
- App mobile (Android, iOS): màn **Settings › Thiết bị đăng nhập** để xem, thêm (quét QR), đổi tên và đăng xuất
  các thiết bị.
- Backend nhỏ để cấp token đăng nhập.
- Mở rộng dữ liệu đồng bộ.

## 1. Mục tiêu

1. Desktop đăng nhập bằng **cùng tài khoản** với điện thoại, kể cả tài khoản tạo bằng Google hoặc Apple.
   Cách chính là **quét QR hiện trên máy tính bằng app trên điện thoại**.
2. Phiên đăng nhập được giữ sau khi tắt và mở lại app, giống điện thoại.
3. Desktop dùng được các tính năng gắn với tài khoản: đồng bộ playlist, giới hạn thiết bị, quyền lợi gói đăng ký.
4. Người dùng quản lý mọi thiết bị đăng nhập từ điện thoại: xem danh sách, thêm thiết bị mới, đổi tên,
   đăng xuất từ xa (có hiệu lực ngay), đăng xuất tất cả thiết bị khác.
5. Mở rộng dữ liệu đồng bộ: add-on Stremio, kênh yêu thích, lịch sử xem.
6. Desktop vẫn dùng được khi không đăng nhập, như hiện nay (`AppFlags.worksSignedOut`).

## 2. Hiện trạng

| Phần | Hiện trạng | Vị trí |
|---|---|---|
| Firebase Auth desktop | **Giả lập trong bộ nhớ** (`InMemoryFirebaseAuth`), không gọi Firebase | `DesktopFirebaseInitializer.kt:64`, `DesktopModule.kt:33` |
| Firestore desktop | **Giả lập trong bộ nhớ** (`InMemoryFirebaseFirestore`) | `DesktopFirebaseInitializer.kt:66` |
| Khởi tạo Firebase JVM | Đã có `Firebase.initialize`, nhưng dùng **app ID và API key của bản Android**. `FilePersistedPlatform` ghi `~/.tsiptv/firebase.properties`, chưa được dùng | `DesktopFirebaseInitializer.kt:20-120` |
| App Check | Android cài Play Integrity App Check. Desktop không có provider nào | `TSAndroidApplication.kt:40-46` |
| Màn đăng nhập desktop | `LoginScreenDesktop2` đã nối `AuthViewModel`. Nút Google/Apple trả lỗi "not implemented". Nút "Sync Data" là TODO | `LoginScreenDesktop.kt:229-245`, `AuthRepositoryImpl.kt:174-192` |
| Token | `AuthToken` lưu trong KeyValueStorage là chuỗi giả `dummy_access_token_…` | `AuthRepositoryImpl.kt:98-106, 228-239` |
| Danh sách thiết bị | Đã có `users/{uid}/devices`, `meta/devices` (tối đa 4) và `AccountCloud.listDevices/removeDevice`. Hiển thị trong màn **Connect**, mục "Thiết bị" | `AccountCloud.kt`, `DeviceLimit.kt`, `ConnectScreen.kt:314-334` |
| Đăng xuất từ xa | `removeDevice` chỉ xoá document. Máy bị gỡ **chỉ tự đăng xuất ở lần mở app sau** (`DeviceCheck.RemovedElsewhere`). Chuỗi hiện tại: "It will be signed out next time it opens the app" | `DeviceSessionManager.kt:95-101`, `strings_cast_sync.xml:82` |
| Đồng bộ | Một slot `users/{uid}/sync/current`, chỉ playlist dạng URL (tối đa 100, 256 KiB), push/pull một lần. Mỗi lần push tốn 1 quota (Free: 1 lần/ngày) | `SyncPlanner.kt`, `SyncService.kt:54-170` |
| Entitlement | Đọc realtime `users/{uid}/entitlements/current`, áp dụng mọi nền tảng | `ServerEntitlementSource.kt:47-49` |
| Chỉ lưu trên máy (Room) | Kênh yêu thích, lịch sử xem, add-on Stremio, nguồn tsiptv, EPG, cài đặt | `RoomEntities.kt`, `StremioEntities.kt` |
| Backend | Không có Cloud Functions. `firebase.json` chỉ có hosting, rules và emulator. Server duy nhất là `telegram-bot/` (Node + firebase-admin), chưa deploy | `firebase.json`, `telegram-bot/src/auth.js` |

**Kết luận:** code đồng bộ, giới hạn thiết bị và entitlement đều là code chung và sẽ chạy trên desktop. Thứ còn
thiếu là **Firebase thật trên JVM**, một **`uid` thật**, và **backend** để đăng nhập bằng QR.

## 3. Tích hợp Firebase cho desktop

### 3.1 Thư viện và khả năng trên JVM

Desktop dùng gitlive 2.3.0. Trên JVM, gitlive chạy bằng `dev.gitlive:firebase-java-sdk` 0.6.1, hiện được kéo
vào gián tiếp. Mình đã kiểm tra bytecode của thư viện:

| Sản phẩm | Trên JVM | Ghi chú |
|---|---|---|
| Auth: email/mật khẩu, tạo tài khoản, đăng xuất | Có | |
| Auth: `signInWithCustomToken` | Có | Dùng cho đăng nhập bằng QR |
| Auth: `signInWithCredential` (Google, Apple, OAuth) | **Không**: ném `NotImplementedError` | Nên Google/Apple phải đi qua custom token |
| Auth: `sendPasswordResetEmail` | Cần kiểm chứng | Nếu chưa có thì gọi REST `accounts:sendOobCode` |
| Auth: giữ phiên, tự làm mới token | Có, qua `FirebasePlatform.store/retrieve` | Đã có `FilePersistedPlatform` |
| Firestore: get, set, transaction, `snapshots` | Có (`firebase-firestore-jvm`, gRPC OkHttp) | Cần thử thật ở bước 0 |
| Analytics | **Không** (`TODO()`) | Giữ tắt như hiện nay |
| Storage | Cần kiểm chứng | Desktop hiện không cần |
| Messaging (FCM), Crashlytics, App Check | **Không** | Xem 3.4 |

### 3.2 Cấu hình project Firebase

1. **Đăng ký app riêng cho desktop.** Vào Firebase Console › Project settings › Add app › **Web**, đặt tên
   "TS IPTV Desktop". Thay `APPLICATION_ID` và `API_KEY` trong `DesktopFirebaseInitializer` bằng giá trị của app
   này, không dùng app Android nữa. Lý do:
   - API key của app Android có thể bị giới hạn chỉ cho app Android (package và SHA-1). Request từ desktop khi đó
     bị từ chối.
   - Tách được số liệu và quota của desktop.
2. **Giới hạn API key desktop:** trong Google Cloud Console › Credentials, chỉ cho phép các API cần dùng:
   - Identity Toolkit;
   - Token Service;
   - Cloud Firestore;
   - Firebase Installations, nếu SDK cần.

   Không thể giới hạn theo ứng dụng, vì desktop không gửi referrer hay package. Đây là giới hạn chung của app
   desktop, và an toàn vì rules mới là lớp bảo vệ dữ liệu.
3. **Authorized domains:** không cần cho email hay custom token.
4. **Tách cấu hình khỏi code:** đọc cấu hình từ `desktopSupportConfig`, giống `DesktopSupportConfig` đã sinh lúc
   build, để đổi project (ví dụ dev/prod) mà không sửa code. Thêm cờ `-Dtsiptv.firebase.emulator=true` để trỏ
   tới emulator (auth 9099, firestore 8085).

### 3.3 Code

1. Thêm `implementation(libs.firebase.java.sdk)` vào `desktopMain`, ghim phiên bản khớp với gitlive 2.3.0, thay vì
   phụ thuộc gián tiếp.
2. `DesktopFirebaseAuth : IFirebaseAuth` trên gitlive `Firebase.auth`, theo mẫu `IosFirebaseAuthImplementation`
   (`authStateChanged`). Thêm `signInWithCustomToken(token)` vào `IFirebaseAuth`. Android và iOS cũng có hàm này
   nên không tốn công.
3. `DesktopFirebaseFirestore` trên gitlive `Firebase.firestore`.
4. Trong `DesktopModule`, thay ba bản `InMemory*` bằng bản thật. Bản `InMemory*` chỉ còn dùng cho test.
5. `AuthRepositoryImpl`:
   - Bỏ token giả. Lấy ID token thật bằng `getIdToken`, hoặc chỉ dựa vào trạng thái của SDK.
   - Đăng xuất phải xoá phiên trong `firebase.properties`.
6. File phiên `~/.tsiptv/firebase.properties`:
   - Đặt quyền `600` trên macOS và Linux.
   - Giai đoạn sau: mã hoá refresh token bằng macOS Keychain, hoặc Windows DPAPI qua JNA.
   - Nếu file hỏng hoặc không đọc được thì coi như chưa đăng nhập, không làm sập app.
7. Firestore offline cache trên JVM: tắt (`persistenceEnabled = false`) nếu gây lỗi khoá file khi mở 2 cửa sổ.
   Desktop không cần offline.

### 3.4 App Check

- Android đang dùng Play Integrity. Desktop **không có** provider App Check nào chạy trên JVM.
- **Cần kiểm tra:** Firebase Console › App Check › Firestore và Authentication đang ở chế độ **Enforce** hay
  **Monitor**. Nếu là Enforce thì desktop bị chặn hoàn toàn.
- Hướng xử lý nếu cần Enforce:
  - Desktop xin App Check token từ backend bằng custom provider. Backend dùng Admin SDK
    `appCheck().createToken(desktopAppId)`.
  - Chỉ cấp token khi request kèm ID token hợp lệ, hoặc sau bước duyệt QR.
  - Hướng này làm phức tạp đăng nhập lần đầu. **Đề xuất: để Monitor cho tới khi có backend.**

### 3.5 Kiểm thử

- Test JVM chạy với Firebase emulator: đăng nhập, giữ phiên, transaction thiết bị, rules.
- Test tay trên project thật, cả Windows và macOS: đăng nhập, tắt và mở lại app, chờ token hết hạn sau 1 giờ,
  ngắt mạng rồi bật lại.

## 4. Đăng nhập trên desktop

### 4.1 Các cách đăng nhập

| Cách | Tài khoản dùng được | Cần backend | Vai trò |
|---|---|---|---|
| **Quét QR bằng điện thoại** | Mọi tài khoản (email, Google, Apple) | Có | **Cách chính, hiện đầu tiên** |
| Nhập mã 8 ký tự trên điện thoại | Mọi tài khoản | Có (cùng luồng QR) | Dự phòng khi camera không quét được |
| Email và mật khẩu | Chỉ tài khoản có mật khẩu | Không | Phụ. Làm trước vì không cần backend |
| Google qua trình duyệt (loopback OAuth và PKCE) | Google | Có | Backlog |

### 4.2 Màn đăng nhập desktop

```
┌─────────────────────────────── Đăng nhập TS IPTV ───────────────────────────────┐
│  ┌──────────────┐   Quét mã bằng app TS IPTV trên điện thoại                    │
│  │  ▓▓ QR ▓▓▓  │   1. Mở TS IPTV trên điện thoại                               │
│  │  ▓▓▓▓▓▓▓▓▓  │   2. Settings › Thiết bị đăng nhập › Thêm thiết bị             │
│  │  ▓▓▓▓▓▓▓▓▓  │   3. Quét mã này, rồi bấm Xác nhận                             │
│  └──────────────┘   Hoặc nhập mã:  K7QM-4XTD     Mã mới sau 2:41  ↻            │
│ ───────────────────────────────────────────────────────────────────────────────  │
│  Đăng nhập bằng email ▸   (mở form email và mật khẩu)                            │
│  Dùng không cần đăng nhập ▸                                                      │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- QR chứa `https://tsiptv-8bdd6.web.app/l/{requestId}#{userCode}`, một **link https**, không dùng scheme
  `tsiptv://`. Lý do:
  - Camera hệ thống của iOS/Android mở thẳng app nhờ App Link / Universal Link.
  - Máy chưa cài app thì vào trang web hướng dẫn tải app.
  - Phần `#userCode` không gửi lên server khi mở link trong trình duyệt.
- Mã tự làm mới khi hết hạn (3 phút). Hiện đồng hồ đếm ngược và nút làm mới.
- Desktop hỏi trạng thái mỗi 2 giây (hoặc long-poll 25 giây). Khi được duyệt: hiện "Đang đăng nhập…", gọi
  `signInWithCustomToken`, rồi đóng màn hình và đăng ký thiết bị.
- Khi bị từ chối hoặc hết hạn: báo lỗi và tạo mã mới.
- Tài khoản đã đủ 4 thiết bị: điện thoại phải gỡ bớt một thiết bị trước khi duyệt (xem mục 5.3). Desktop không
  cần xử lý trường hợp này.

### 4.3 Luồng QR và backend

```
Desktop                              Backend                              Điện thoại (đã đăng nhập)
  │ POST /login/start ─────────────▶ tạo desktopLogins/{requestId}:
  │   {deviceName, os, appVersion}     userCode, pollSecretHash,
  │ ◀── requestId, userCode,           status=pending, expiresAt=+3'
  │     pollSecret, expiresAt
  │ hiện QR + userCode
  │                                                                    quét QR (hoặc gõ userCode)
  │                                  ◀── POST /login/lookup ─────────── {requestId|userCode} + ID token
  │                                  ──▶ {deviceName, os, ip-city}  ──▶ màn xác nhận
  │                                                                    "Đăng nhập trên MacBook-Tun?"
  │                                  ◀── POST /login/approve ────────── {requestId, deviceName mới?} + ID token
  │                                  kiểm ID token, kiểm còn slot,
  │                                  status=approved, uid, đặt trước slot,
  │                                  customToken = createCustomToken(uid)
  │ POST /login/poll {pollSecret} ─▶ trả customToken MỘT lần, xoá phiên
  │ signInWithCustomToken ──────────────────────────────────────────────▶ (danh sách thiết bị cập nhật realtime)
```

- **Bảo mật:**
  - Phiên chỉ dùng một lần, hết hạn sau 3 phút.
  - Chỉ desktop có `pollSecret`. Server chỉ lưu hash của nó, nên người chụp được QR cũng không lấy được token.
  - `userCode` gồm 8 ký tự trong bảng không gây nhầm (bỏ 0/O, 1/I), giới hạn số lần thử sai.
  - Điện thoại **luôn phải bấm Xác nhận**. Màn xác nhận hiện tên máy, hệ điều hành và thành phố ước lượng từ IP
    của desktop, để người dùng nhận ra yêu cầu lạ (chống lừa quét QR hộ người khác).
  - Giới hạn tần suất `start` theo IP, `approve` theo uid.
  - Custom token sống 1 giờ, và desktop đổi lấy phiên ngay. Chỉ trả token qua HTTPS.
- **Lưu trữ:** `desktopLogins/{requestId}` nằm ngoài `users/`. Rules đặt `allow read, write: if false` (chỉ backend
  truy cập), và có TTL policy theo `expiresAt`.
- **Dùng được cho TV:** cùng luồng thay cho bàn phím ảo trên Android TV. Để sau.

### 4.4 Backend

| Lựa chọn | Ưu | Nhược |
|---|---|---|
| **Cloud Functions** (đề xuất) | Cùng project, sẵn Admin SDK (`createCustomToken`, `revokeRefreshTokens`, App Check) | Phải chuyển project sang gói **Blaze**. Miễn phí 2 triệu lượt gọi mỗi tháng, nhưng phải gắn thẻ |
| Cloudflare Worker | Miễn phí, không cần Blaze | Tự ký JWT custom token, tự gọi REST cho revoke. Lưu private key của service account trong secret |
| `telegram-bot/` (Node) | Đã có `firebase-admin`, `verifyIdToken` | Chưa deploy, cần máy chạy 24/7 |

Các endpoint cần có:
- `login/start`, `login/lookup`, `login/approve`, `login/deny`, `login/poll`;
- `devices/signOut` (mục 5.4);
- `devices/signOutOthers`.

Mỗi endpoint đều kiểm ID token, trừ `start` và `poll`.

## 5. Mobile: Settings › Thiết bị đăng nhập

### 5.1 Vị trí và tái sử dụng

- **Vị trí:** Profile › Settings › **Thiết bị đăng nhập** (`ProfileScreenActions.Settings`). Thêm route mới
  `NavRoutes.LoginDevices`.
- **Tái sử dụng:**
  - `AccountCloud.listDevices`, `removeDevice`, `DeviceLimitPolicy`, `RegisteredDevice`;
  - logic gỡ thiết bị trong `ConnectViewModel`.

  Mục "Thiết bị" trong màn Connect đổi thành một dòng tóm tắt "2/4 thiết bị ›" dẫn sang màn mới, để chỉ có một
  nơi quản lý.
- **Chỉ hiện khi đã đăng nhập.** Khi chưa đăng nhập thì hiện lời mời đăng nhập.

### 5.2 Giao diện

```
←  Thiết bị đăng nhập                                   2/4
┌───────────────────────────────────────────────────────────┐
│ 📱  iPhone 13 Pro Max · iOS 26.0          (Thiết bị này)  │
│     TS IPTV 1.0 (6) · Đang hoạt động                      │
├───────────────────────────────────────────────────────────┤
│ 💻  MacBook-Tun · macOS 15            ⋮  Đổi tên          │
│     Desktop 1.2.0 · Hoạt động 5 phút trước   Đăng xuất    │
├───────────────────────────────────────────────────────────┤
│ 📺  Android TV phòng khách                ⋮               │
│     TV 1.4.2 · Hoạt động 3 ngày trước                     │
└───────────────────────────────────────────────────────────┘
[ ⊕  Thêm thiết bị (quét mã QR) ]
[    Đăng xuất tất cả thiết bị khác ]
```

- Mỗi dòng gồm:
  - biểu tượng theo nền tảng (điện thoại, máy tính bảng, TV, máy tính);
  - tên thiết bị (đổi được);
  - hệ điều hành và phiên bản app;
  - lần hoạt động cuối;
  - nhãn "Thiết bị này".
- Thiết bị "ma" (`ghost`: còn chiếm slot nhưng mất document) hiện là "Thiết bị không xác định" và gỡ được.
- Danh sách cập nhật **realtime**: dùng `snapshots` trên `users/{uid}/devices` thay cho `get` một lần.
- Thêm vào `RegisteredDevice`: `os` (ví dụ "macOS 15.5"), `model`, `signedInAt`, `signInMethod` (`password`,
  `google`, `apple`, `qr`). Rules phải cho các trường mới.

### 5.3 Thêm thiết bị (quét QR)

1. Bấm **Thêm thiết bị**. Mở camera quét QR, kèm nút "Nhập mã thay vì quét".
   - Android: CameraX và ML Kit Barcode (bundled, không cần Google Play Services). Có thể dùng
     `GmsBarcodeScanning` nếu chấp nhận phụ thuộc Play Services.
   - iOS: `AVCaptureMetadataOutput` (QR), gói trong `UIKitView`. Thêm `NSCameraUsageDescription`; Info.plist đã có
     sẵn khoá này.
   - Xin quyền camera đúng lúc. Nếu bị từ chối thì chuyển sang nhập mã.
2. Đọc được QR hợp lệ (`https://tsiptv-8bdd6.web.app/l/...`) thì gọi `login/lookup` và hiện màn xác nhận: tên máy,
   hệ điều hành, vị trí ước lượng, ô đổi tên. Chọn **Xác nhận** hoặc **Từ chối**.
3. Khi đã đủ 4 thiết bị: màn xác nhận yêu cầu chọn một thiết bị để đăng xuất trước, theo kiểu UI giới hạn thiết
   bị hiện có.
4. Duyệt xong thì hiện "Đã đăng nhập trên MacBook-Tun", và thiết bị mới xuất hiện trong danh sách (realtime).
5. **Mở từ camera hệ thống:** App Link (Android, `assetlinks.json`) và Universal Link (iOS, `apple-app-site-association`)
   đặt trên Firebase Hosting `web/public/.well-known/`. Mở link sẽ vào thẳng màn xác nhận.

### 5.4 Đăng xuất thiết bị (có hiệu lực ngay)

Hiện nay gỡ thiết bị chỉ có hiệu lực khi máy đó mở lại app. Cần làm hai lớp:

1. **Realtime trên máy bị gỡ:** `DeviceSessionManager` lắng nghe document thiết bị của chính mình
   (`users/{uid}/devices/{myId}`) bằng `snapshots`. Document bị xoá thì máy tự đăng xuất ngay và hiện "Thiết bị
   này đã bị đăng xuất từ điện thoại". Cách này không cần backend.
2. **Thu hồi phiên ở server:** endpoint `devices/signOut` xoá document thiết bị, rồi gọi
   `revokeRefreshTokens(uid)`. Nhờ vậy máy đang offline hoặc đã sửa app cũng không làm mới token được nữa.
   - Lưu ý: `revokeRefreshTokens` thu hồi **mọi** phiên của uid, kể cả điện thoại đang thao tác. Sau khi revoke,
     backend trả custom token mới để điện thoại tự đăng nhập lại ngay, người dùng không thấy gì.
   - Các máy còn lại (không bị gỡ) sẽ bị đăng xuất ở lần làm mới token tiếp theo. Để tránh điều này, các máy hợp lệ
     tự đăng nhập lại bằng custom token xin qua `devices/resume`, kèm `deviceId` và ID token cũ còn hạn. Nếu thấy
     phức tạp thì giai đoạn đầu chỉ dùng lớp 1, và để revoke cho "Đăng xuất tất cả thiết bị khác".
3. **Đăng xuất tất cả thiết bị khác:** xoá mọi document trừ máy hiện tại, rồi revoke.
4. Đổi chuỗi `devices_remove_confirm` (7 ngôn ngữ) cho đúng hành vi mới: "sẽ bị đăng xuất ngay".

### 5.5 Đổi tên thiết bị

Chủ tài khoản sửa được `name` của mọi thiết bị. Rules đã cho chủ sở hữu ghi. Máy được đổi tên giữ tên mới,
không ghi đè bằng hostname ở lần `touchDevice` sau. Để làm được, thêm trường `nameEditedAt`.

## 6. Mở rộng dữ liệu đồng bộ

| Bước | Dữ liệu | Cách làm | Ghi chú |
|---|---|---|---|
| 6a | **Add-on Stremio** (URL manifest) | Thêm vào payload `sync/current` bản `v2`. Nhỏ, cách gộp giống playlist | Bản cũ đọc `v1` phải bỏ qua trường lạ |
| 6b | **Kênh yêu thích** | `users/{uid}/favorites/{key}`. Key gồm playlist `sourceUrl` và `guideId` (hoặc stream URL), vì id trong Room khác nhau giữa các máy | Ghi ngay khi bấm, nghe realtime |
| 6c | **Tiếp tục xem** | `users/{uid}/history/{key}`, giữ 50 mục gần nhất, kèm vị trí phát cho phim | Ghi khi dừng xem hoặc mỗi 60 giây. Người ghi sau thắng (last-writer-wins) theo `updatedAt` |
| 6d | Cài đặt | Để sau | Ít giá trị |

- **Khoá ghép kênh** là phần rủi ro nhất: cùng một kênh phải có cùng khoá trên mọi máy. Cần test với playlist thật.
- Cần quyết định đồng bộ realtime (6b, 6c) miễn phí hay chỉ cho gói trả phí. Cập nhật
  `docs/prd-subscriptions.md` và rules theo quyết định đó.

## 7. Các giai đoạn triển khai

| # | Giai đoạn | Nội dung chính | Thời gian | Phụ thuộc |
|---|---|---|---|---|
| 0 | Thử nghiệm Firebase JVM | Mục 3.3 bước 2–3 trên emulator và project thật. Kiểm tra App Check (3.4), API key (3.2) | 1–2 ngày | Không |
| 1 | Firebase thật và đăng nhập email trên desktop | Mục 3.2, 3.3. Form email. Đăng ký thiết bị với platform `desktop`. Bật nút đồng bộ playlist | 3–4 ngày | 0 |
| 2 | Màn **Thiết bị đăng nhập** (mobile) | Mục 5.1, 5.2, 5.4 lớp 1, 5.5. Danh sách realtime, đổi tên, đăng xuất ngay | 3–4 ngày | 1 (hoặc làm song song, vì chỉ dùng Firestore) |
| 3 | Backend | Mục 4.4: chọn nền tảng, các endpoint `login/*`, rules và TTL `desktopLogins`, test với emulator | 3–4 ngày | Quyết định backend |
| 4 | Đăng nhập bằng QR | Desktop: mục 4.2. Mobile: quét QR và nhập mã (5.3), màn xác nhận, App Link / Universal Link | 6–8 ngày | 2, 3 |
| 5 | Thu hồi phiên ở server | Mục 5.4 lớp 2, "Đăng xuất tất cả thiết bị khác" | 2–3 ngày | 3 |
| 6 | Mở rộng đồng bộ | 6a (2 ngày), rồi 6b và 6c (5–8 ngày) | 1–2 tuần | 1 |
| 7 | QC và phát hành | Ma trận Windows/macOS × Android/iOS × email/Google/Apple; tắt và mở lại app; vượt 4 thiết bị; đăng xuất từ xa khi đang online/offline; cập nhật chính sách quyền riêng tư, `desktop-build.md`, 7 ngôn ngữ | 4–5 ngày | Các mục trên |

Tổng cộng khoảng **6–7 tuần** cho một người. Có thể phát hành theo từng đợt:

| Đợt | Nội dung |
|---|---|
| Desktop 1.2.0 + mobile | Giai đoạn 0–2: đăng nhập email trên desktop, màn Thiết bị đăng nhập, đăng xuất ngay |
| Desktop 1.3.0 + mobile | Giai đoạn 3–5: đăng nhập bằng QR, thu hồi phiên |
| Sau đó | Giai đoạn 6: đồng bộ yêu thích và lịch sử |

## 8. Cần quyết định trước khi làm

1. **Backend:** Cloud Functions (chuyển sang gói Blaze), Cloudflare Worker, hay deploy `telegram-bot/`?
2. **App Check:** Firestore và Auth đang Enforce hay Monitor? Nếu Enforce thì cần custom provider cho desktop
   (mục 3.4).
3. **Đăng xuất từ xa:** chỉ cần realtime (lớp 1), hay cần cả thu hồi phiên ở server (lớp 2)?
4. **Đồng bộ dữ liệu:** đồng ý thứ tự 6a → 6b → 6c? Miễn phí hay chỉ cho gói trả phí?
5. **Quét QR trên Android:** ML Kit bundled (+ khoảng 2–3 MB), hay `GmsBarcodeScanning` (nhẹ nhưng cần Google
   Play Services, không chạy trên máy không có Play)?
6. **Phát hành từng phần** theo bảng trên?
