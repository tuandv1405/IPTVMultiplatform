# Checklist: phát hành bản có AdMob và cập nhật Play Console

Áp dụng cho bản đầu tiên có quảng cáo (AdMob trên Android điện thoại/tablet; Android TV không có
quảng cáo AdMob). Chi tiết kỹ thuật: `docs/prd-admob.md`, `docs/handoff-admob.md`.

Đánh dấu từng mục khi xong. Thứ tự quan trọng: các mục có ★ phải xong **trước** khi gửi bản
lên track production.

---

## 1. AdMob console (https://admob.google.com)

- [ ] ★ **Tạo app** trong AdMob: Apps › Add app › Android › "Is the app listed on a supported
      app store?". Nếu app đã có trên Play: chọn **Yes** và tìm `tss.t.tsiptv`. Nếu chưa thì
      chọn No, sau khi app lên Play thì vào App settings › **Link to store**.
- [ ] ★ Ghi lại **App ID** dạng `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`.
- [ ] ★ **Tạo 5 ad unit** (Apps › TS IPTV › Ad units › Add ad unit) và ghi lại ID dạng
      `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ`:

  | Ad unit | Loại trong AdMob | Biến cấu hình |
  | --- | --- | --- |
  | App open | **App open** | `TSIPTV_ADMOB_APP_OPEN_UNIT` |
  | Banner (Home + player) | **Banner** (app tự dùng adaptive size) | `TSIPTV_ADMOB_BANNER_UNIT` |
  | Native (danh sách) | **Native advanced** | `TSIPTV_ADMOB_NATIVE_UNIT` |
  | Rewarded (nhận lượt gửi/đồng bộ) | **Rewarded** | `TSIPTV_ADMOB_REWARDED_UNIT` |
  | App ID | (không phải ad unit) | `TSIPTV_ADMOB_APP_ID` |

  Rewarded chỉ dùng khi bản có tính năng gửi lên TV/đồng bộ (nhánh `feature/tv-cast-and-sync`).
  Có thể tạo trước. Phần thưởng trong AdMob để `1` / `reward`: app tự tính lượt, không đọc giá
  trị này.
- [ ] ★ **Privacy & messaging › GDPR**: tạo message "European regulations", chọn ngôn ngữ
      (ít nhất English, có thể thêm Vietnamese), gắn với app TS IPTV, **Publish**. Thiếu bước
      này thì hộp thoại đồng ý (UMP) không hiện và người dùng EEA/UK sẽ **không có quảng cáo**.
- [ ] **Privacy & messaging › US state regulations**: tạo và publish message cho các bang Mỹ
      (khuyến nghị nếu có người dùng ở Mỹ).
- [ ] **Payments**: điền thông tin thanh toán, thuế, địa chỉ nhận PIN.
- [ ] **Blocking controls**: chặn các nhóm quảng cáo không muốn hiện (cờ bạc, hẹn hò, chính
      trị…), nhất là khi app có người dùng ở Việt Nam.
- [ ] **Test devices** (Settings › Test devices): thêm máy thật bạn sẽ dùng để thử. Lấy mã thiết
      bị trong logcat của bản release, dòng chứa `addTestDeviceIdentifiers` hoặc
      `Use new ConsentDebugSettings.Builder().addTestDeviceHashedId(...)`. Xem hướng dẫn ở
      `docs/handoff-admob.md`. **Không tự bấm vào quảng cáo thật trên máy chưa đăng ký test.**

## 2. Cấu hình build (máy bạn và CI)

- [ ] ★ Thêm các ID vào `local.properties` ở thư mục gốc (file này **không** commit):

  ```properties
  TSIPTV_ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
  TSIPTV_ADMOB_APP_OPEN_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/1111111111
  TSIPTV_ADMOB_BANNER_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/2222222222
  TSIPTV_ADMOB_NATIVE_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/3333333333
  TSIPTV_ADMOB_REWARDED_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/4444444444
  ```

  Hoặc đặt cùng tên làm biến môi trường hay `-P` Gradle property.
- [ ] ★ CI (GitHub Actions): thêm 5 giá trị trên vào **Settings › Secrets and variables ›
      Actions** (secret hoặc variable), rồi truyền vào bước build bằng `env:`. Workflow hiện
      tại (`.github/workflows/pr-firebase-distribution.yml`) chưa truyền các biến này, nên bản
      CI sẽ dùng test ID. Với bản thử trên App Distribution thì như vậy là đúng, nhưng **đừng**
      đưa bản CI lên Play.
- [ ] ★ **Sửa keystore**: bản release hiện không đóng gói được vì `ts_iptv_android_key.jks`
      không có alias `ts_iptv`. Kiểm tra bằng
      `keytool -list -keystore ts_iptv_android_key.jks`, rồi sửa `keyAlias` trong
      `composeApp/keystore.properties` (hoặc secret `TSIPTV_KEY_ALIAS`) cho đúng alias thật.
- [ ] ★ Build: `./gradlew :composeApp:bundleRelease`. Log build **không** được có dòng
      `... is not set - the release build uses the AdMob TEST id`. Có dòng đó tức là còn thiếu ID.
- [ ] Tăng version (`versionMinor`/`versionPatch` trong `composeApp/build.gradle.kts`) và viết
      release note mới trong `play-store/release-notes/`.

## 3. app-ads.txt (bắt buộc để không mất doanh thu)

- [ ] ★ Lấy **publisher ID** `pub-XXXXXXXXXXXXXXXX` (Account › Settings, hoặc phần giữa của App
      ID).
- [ ] ★ Tạo `web/public/app-ads.txt` với đúng một dòng:

  ```text
  google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
  ```

- [ ] ★ `firebase deploy --only hosting`. Kiểm tra `https://tsiptv-8bdd6.web.app/app-ads.txt`
      trả về 200 và đúng nội dung.
- [ ] ★ Play Console › Store presence › Store settings › **Website** phải là
      `https://tsiptv-8bdd6.web.app` (cùng domain với app-ads.txt). AdMob đọc domain này từ Play.
- [ ] Sau 24–72 giờ: AdMob › Apps › app-ads.txt hiện **Verified**.

## 4. Play Console

- [ ] ★ **App content › Ads**: chọn **Yes, my app contains ads**.
- [ ] ★ **App content › Advertising ID**: chọn **Yes**, mục đích **Advertising or marketing**
      (và Analytics nếu Firebase Analytics dùng ad ID). Manifest đã có quyền
      `com.google.android.gms.permission.AD_ID`.
- [ ] ★ **App content › Data safety**: cập nhật theo `play-store/data-safety.md`. Phần thay đổi
      chính:
  - **Device or other IDs**: collected **và shared**, mục đích Advertising/marketing, Analytics.
  - **Approximate location** (AdMob suy ra từ IP): shared, Advertising.
  - **App interactions** và **Diagnostics**: collected và shared, Advertising, Analytics.
  - **Other user-generated content / App activity**: tên miền và đường dẫn playlist **đã làm
    sạch** gửi lên Analytics (không có user/pass/query).
  - "Data is encrypted in transit": Yes. "Users can request deletion": Yes (trang
    `/delete-account/`).
- [ ] ★ **App content › Target audience**: giữ nhóm tuổi **18+** (hoặc 13+). **Không** chọn nhóm
      dưới 13 tuổi. Nếu chọn, app phải tuân thủ Families Policy và quảng cáo phải dùng SDK được
      chứng nhận cho trẻ em.
- [ ] **App content › Content rating**: không đổi. Làm lại bảng câu hỏi nếu Play yêu cầu.
- [ ] ★ **Chính sách quyền riêng tư** (`https://tsiptv-8bdd6.web.app/privacy/`): bổ sung các ý
      sau rồi deploy hosting:
  - AdMob (Google) hiển thị quảng cáo, dùng advertising ID, IP và thông tin thiết bị; có thể cá
    nhân hoá theo lựa chọn đồng ý.
  - Hộp thoại đồng ý (Google UMP) cho EEA/UK/US, và mục "Tuỳ chọn quyền riêng tư" trong app để
    đổi lựa chọn.
  - Firebase Analytics nhận định dạng playlist, số kênh, tên miền và đường dẫn đã làm sạch.
  - Link chính sách của Google: `https://policies.google.com/technologies/partner-sites`.
- [ ] ★ **Store listing**: cập nhật mô tả theo mục 5 bên dưới (đã cập nhật sẵn trong
      `listing-vi.md` và `listing-en.md`).
- [ ] Ảnh chụp màn hình: không cần chụp lại vì quảng cáo. Nếu chụp mới thì **đừng** chụp quảng
      cáo thật vào ảnh store.

## 5. Mô tả store đã cập nhật

`listing-vi.md` và `listing-en.md` đã được viết lại phần **Mô tả đầy đủ** (dưới 4000 ký tự):

- thêm các tính năng của bản 1.1: playlist kiểu Kodi, addon tương thích Stremio, định dạng TS
  IPTV Source, đổi luồng phát và phụ đề;
- sửa đoạn "Dữ liệu của bạn": trước đây ghi "chúng tôi không thể nhìn thấy" playlist, điều này
  không còn đúng vì Analytics nhận tên miền và đường dẫn đã làm sạch;
- ghi rõ app **miễn phí, có quảng cáo**, không có quảng cáo trong 24 giờ đầu, không quảng cáo
  trên Android TV.

Chỉ thêm mục "Phát lên TV, gửi playlist, đồng bộ thiết bị" vào mô tả khi bản có các tính năng đó
được phát hành.

## 6. Thử sau khi lên Play (track Internal testing trước)

- [ ] Gửi bản lên **Internal testing**, cài từ Play trên máy thật đã đăng ký **test device**
      trong AdMob.
- [ ] Bật công tắc thời gian để vượt 24 giờ, hoặc đợi đủ 24 giờ (bản release không có công tắc
      debug).
- [ ] Kiểm tra từng vị trí:
  - **App open**: hiện khi khởi động lại app từ đầu (kill app rồi mở), trong khoảng 4 giây.
  - **Banner Home**: dính dưới thanh chip thể loại.
  - **Banner player**: nằm dưới tiêu đề, ẩn khi toàn màn hình.
  - **Native**: hiện trong 3 danh sách, có nhãn "Quảng cáo" **và biểu tượng AdChoices** (đây là
    mục chưa kiểm được bằng quảng cáo test).
- [ ] Thử từ VPN EEA (hoặc bản debug với `-Ptsiptv.debugUmpGeography=EEA`): hộp thoại đồng ý
      hiện, mục "Tuỳ chọn quyền riêng tư" mở lại được.
- [ ] AdMob › Policy center: không có cảnh báo.
- [ ] Rồi mới lên **Production**, phát hành theo tỉ lệ (staged rollout, ví dụ 10% → 50% → 100%).

## 7. Theo dõi sau phát hành

- [ ] AdMob dashboard: match rate, eCPM theo định dạng, **Invalid traffic**.
- [ ] Play Console › Android vitals: crash/ANR sau khi thêm SDK quảng cáo.
- [ ] Không tự bấm quảng cáo trên máy chưa đăng ký test, và không nhờ người khác bấm.
- [ ] Cân nhắc sau: mediation (Meta, AppLovin…) khi lượng người dùng tăng.
