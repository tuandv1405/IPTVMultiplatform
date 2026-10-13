# TS IPTV desktop: build, cài đặt, quảng bá "ủng hộ" thay AdMob

Cập nhật 2026-10-11 (lần 2: sửa player desktop, gói phát hành Windows). Tài liệu cho bản desktop (Compose
Multiplatform, JVM). Bản Windows và macOS (Apple Silicon) đã được build và kiểm thử trên máy dev; Linux chưa build,
chưa kiểm thử.

## 1. Tóm tắt

- **Desktop phát được video.** Bộ cài Windows và file `.dmg` macOS đóng gói sẵn libVLC 3.0.24 (bản chính
  thức của VideoLAN), nên máy người dùng không cần cài VLC.
- **Không có quảng cáo bên thứ ba trên desktop.** Mọi chỗ quảng cáo (banner Home, banner Player, các ô
  native trong danh sách) hiện thẻ **ủng hộ nhà phát triển** hoặc thẻ **bản Android trên Google Play**.
- **"App open" của desktop là màn hình ủng hộ** (VietQR + link ủng hộ online), hiện khi mở app. Nó tuân
  theo đúng luật của quảng cáo: không hiện trong 24 giờ đầu, không hiện ở giao diện TV, không hiện cho
  người đã mua gói bỏ quảng cáo.
- **Desktop chạy không cần đăng nhập**, giống giao diện TV: playlist và phát video đều là dữ liệu cục bộ.

## 2. Build và cài

### Phát hành Windows (một lệnh)

Hướng dẫn từng bước (chuẩn bị máy, tăng phiên bản, kiểm tra, phát hành, xử lý lỗi):
[desktop-windows-release.md](desktop-windows-release.md).

```bash
./gradlew :composeApp:packageDesktopRelease
```

Kết quả nằm trong `composeApp/build/release/desktop/` (mỗi file khoảng 200 MB, trong đó VLC chiếm khoảng 110 MB):

| File | Dành cho | Cách dùng |
|---|---|---|
| `TS-IPTV-<v>-Setup.exe` | **Cài 1 chạm** | Nhấp đúp: app tự cài (chỉ hiện thanh tiến trình, không hỏi gì), tạo lối tắt ở Start Menu và Desktop, rồi tự mở. |
| `TS-IPTV-<v>-Windows-x64.msi` | Cài bằng Windows Installer | Nhấp đúp rồi làm theo hướng dẫn. Cài im lặng: `msiexec /i <file>.msi /passive`. |
| `TS-IPTV-<v>-Windows-x64-portable.zip` | **Cài thủ công, chạy ngay** | Giải nén vào đâu cũng được, mở thư mục `TS IPTV`, chạy `TS IPTV.exe`. Muốn gỡ thì xoá thư mục. |
| `README.txt` | Người dùng | Hướng dẫn 3 cách cài, cảnh báo SmartScreen, phím tắt (tiếng Anh và tiếng Việt). |
| `SHA256SUMS.txt` | Kiểm tra file tải về | Định dạng `sha256sum`. |

- **Cài cho người dùng hiện tại** (`perUserInstall`): cài vào `%LOCALAPPDATA%\TS IPTV`, **không cần quyền
  admin**, không hiện hộp thoại UAC. Nhờ vậy `Setup.exe` cài được chỉ với một chạm. Gỡ ở Settings > Apps.
- `Setup.exe` là gói tự giải nén tạo bằng **IExpress** (có sẵn trong Windows, không cần cài thêm). Nó giải
  nén MSI và `composeApp/packaging/windows/install.cmd` vào thư mục tạm, chạy `msiexec /i … /passive`, rồi
  mở app. Nếu phiên bản này đã được cài rồi (mã 1638) thì nó chỉ mở app.
- Dữ liệu người dùng (playlist, lịch sử) nằm ở `%USERPROFILE%\.tsiptv`, còn cài đặt nằm trong Java Preferences
  (registry `HKCU\Software\JavaSoft\Prefs`). Cài bản mới hay gỡ app đều không xoá dữ liệu này. Bản zip dùng
  chung dữ liệu với bản cài.
- **Chưa ký số**: SmartScreen sẽ báo "Windows protected your PC", người dùng phải bấm "More info" rồi
  "Run anyway". Muốn hết cảnh báo thì cần chứng chỉ code signing (OV/EV), hoặc phát hành qua Microsoft Store
  (mục 5).
- Phiên bản lấy từ `versionMajor.versionMinor.versionPatch` trong `composeApp/build.gradle.kts` (dùng chung
  với Android). **MSI chỉ thay được bản đã cài khi phiên bản mới cao hơn**, nên phải tăng `versionPatch`
  trước mỗi lần phát hành desktop. `upgradeUuid` cố định, không được đổi.
- Các task con: `packageDesktopSetupExe`, `packageDesktopMsi`, `packageDesktopPortableZip`,
  `desktopReleaseSums`. Chỉ chạy được trên Windows (MSI cần WiX do plugin Compose tự tải, Setup.exe cần
  IExpress).

### Build lẻ, chạy thử

- `./gradlew :composeApp:packageReleaseMsi`: chỉ build MSI, ra file trong `composeApp/build/compose/binaries/main-release/msi/`.
- Lần build đầu, task `downloadVlcWin64` tải `vlc-3.0.24-win64.zip` từ download.videolan.org và kiểm
  tra SHA-256. Nếu checksum sai, build dừng. Các lần sau dùng lại file trong `composeApp/build/vlc/`.
- `prepareDesktopVlc` chỉ lấy `libvlc.dll`, `libvlccore.dll`, `plugins/` và `COPYING.txt`. Các plugin
  GUI, streaming output, visualization và hai file `.jar` của Blu-ray bị bỏ (trước đây trình đóng gói đưa hai
  file jar này vào classpath của app). Chỉ máy build Windows mới đóng gói VLC. Nếu file zip VLC không đổi,
  Gradle không nhận ra khi bộ lọc thay đổi: sửa danh sách lọc xong thì xoá `composeApp/build/desktop-resources`.
- Chạy thử không cần cài: `./gradlew :composeApp:run`, hoặc
  `./gradlew :composeApp:createReleaseDistributable` rồi mở
  `composeApp/build/compose/binaries/main-release/app/TS IPTV/TS IPTV.exe`.

### macOS

Chỉ build được trên máy Mac (`jpackage` chỉ tạo `.dmg` trên macOS).

| Việc | Lệnh | Cấu hình IDE (`.run/`) |
|---|---|---|
| Chạy thử (dev) | `./gradlew :composeApp:run` | `desktopApp` |
| Build file cài | `./gradlew :composeApp:packageReleaseDmg` | `desktopApp (package DMG)` |
| Chỉ build `.app`, không đóng `.dmg` | `./gradlew :composeApp:createReleaseDistributable` | |

- Kết quả: `composeApp/build/compose/binaries/main-release/dmg/TS IPTV-<v>.dmg` (khoảng 200 MB). Mở file,
  kéo `TS IPTV` vào `Applications`. Bản `.app` chưa đóng gói nằm ở `…/main-release/app/TS IPTV.app`.
- **JDK:** Gradle daemon chạy bằng JDK 21 (`gradle/gradle-daemon-jvm.properties`): Gradle tự tìm JDK 21 đã
  cài, nếu không có thì tự tải về. JBR 25 đi kèm Android Studio làm Kotlin 2.2 lỗi ngay khi cấu hình
  (`What went wrong: 25.0.3`). Trong Android Studio: Settings > Build Tools > Gradle > Gradle JDK nên để
  mặc định (theo daemon JVM criteria).
- **VLC:** task `downloadVlcMac` tải `vlc-3.0.24-<arm64|intel64>.dmg` (đúng kiến trúc của JDK đang build)
  và kiểm tra SHA-256. `prepareDesktopVlcMac` mount file dmg bằng `hdiutil`, chép `lib/libvlc.dylib`,
  `lib/libvlccore.dylib` và `plugins/` (bỏ plugin GUI, Growl, streaming output, visualization) vào
  `build/desktop-resources/macos/vlc`, kèm `COPYING.txt` (LGPL, lấy từ tag 3.0.24 trên code.videolan.org vì
  file dmg không có). App thấy nó ở `<resources>/vlc/lib`, vlcj tự đặt plugin path là `lib/../plugins`
  (`DesktopVlc.kt`). `:composeApp:run` cũng dùng bản VLC này, nên máy dev không cần cài VLC.
- Build trên Mac Apple Silicon ra bản **arm64**; muốn có bản Intel thì build trên Mac Intel (hoặc bằng JDK x64).
- **Chưa ký số (mặc định):** Compose ký ad-hoc. App chạy bình thường trên máy build. Trên máy khác, file tải
  từ Internet bị Gatekeeper chặn: lần đầu mở, macOS báo không kiểm tra được app; vào System Settings >
  Privacy & Security, bấm "Open Anyway" (macOS 15+), hoặc chạy
  `xattr -dr com.apple.quarantine "/Applications/TS IPTV.app"`.
- **Ký và notarize** (cần Apple Developer Program): đặt trong `local.properties` (hoặc `-P`, biến môi trường)
  rồi chạy `./gradlew :composeApp:packageReleaseDmg` và `:composeApp:notarizeReleaseDmg`.

  ```properties
  TSIPTV_MAC_SIGN_IDENTITY=Developer ID Application: Ten Ban (TEAMID)
  TSIPTV_MAC_NOTARY_APPLE_ID=ban@example.com
  TSIPTV_MAC_NOTARY_PASSWORD=xxxx-xxxx-xxxx-xxxx
  TSIPTV_MAC_NOTARY_TEAM_ID=TEAMID
  ```

  `TSIPTV_MAC_NOTARY_PASSWORD` là app-specific password tạo ở appleid.apple.com. Chưa thử ký thật.
- Bundle ID `tss.t.tsiptv.desktop`, icon `brand/app-icon.icns` (ảnh `app-icon-1024.png` thu còn 80 % trên nền
  trong suốt, đúng lề icon macOS). Dữ liệu người dùng nằm ở `~/.tsiptv`, cài đặt trong
  `~/Library/Preferences/com.apple.java.util.prefs.plist`.

### Cập nhật VLC

Trong `composeApp/build.gradle.kts`, đổi `vlcVersion`, `vlcWin64Sha256`, hai giá trị trong `vlcMacSha256`
và `vlcLicenseSha256`. Lấy SHA-256 từ file `.sha256` cạnh file zip/dmg trên
https://download.videolan.org/pub/videolan/vlc/ (`win64/`, `macosx/`); `vlcLicenseSha256` là SHA-256 của
`COPYING.LIB` ở tag mới trên code.videolan.org.

**Phải giữ libVLC 3.x.** App dùng vlcj **4.12.1**, là dòng vlcj dành cho libVLC 3. vlcj 5 (bản cũ
5.0.0-M4) cần libVLC 4, mà VLC 4 chưa có bản phát hành. Với vlcj 5, player lỗi ngay khi tạo
(`libvlc_media_get_meta_extra` not found), nên trước đây desktop chưa từng phát được video.

### Linux

Linux **chưa đóng gói VLC**: người dùng phải tự cài VLC 3.x. Nếu thiếu VLC, app vẫn mở được nhưng kênh nào
cũng báo lỗi phát. Muốn đóng gói thì thêm phụ thuộc gói `vlc` vào `.deb`.

## 3. Cấu hình kênh ủng hộ

Có 4 giá trị, tất cả đều tuỳ chọn. Thứ tự đọc: Gradle `-P`, rồi `local.properties` (gốc repo hoặc
`composeApp/`), rồi biến môi trường. Không commit giá trị thật vào git.

| Khoá | Ví dụ | Ý nghĩa |
|---|---|---|
| `TSIPTV_SUPPORT_URL` | `https://ko-fi.com/tenban` | Link ủng hộ quốc tế (Ko-fi, PayPal.me, GitHub Sponsors…). Bắt buộc `https://`. |
| `TSIPTV_SUPPORT_BANK_ID` | `MB`, `VCB`, `TCB`, `970422` | Mã ngân hàng theo VietQR (tên viết tắt hoặc BIN). |
| `TSIPTV_SUPPORT_ACCOUNT_NO` | `0123456789` | Số tài khoản. |
| `TSIPTV_SUPPORT_ACCOUNT_NAME` | `NGUYEN VAN A` | Tên chủ tài khoản, viết không dấu. |

Ví dụ `local.properties`:

```properties
TSIPTV_SUPPORT_URL=https://ko-fi.com/tenban
TSIPTV_SUPPORT_BANK_ID=MB
TSIPTV_SUPPORT_ACCOUNT_NO=0123456789
TSIPTV_SUPPORT_ACCOUNT_NAME=NGUYEN VAN A
```

Sau khi đổi giá trị thì build lại MSI. Task `generateDesktopSupportConfig` sinh ra
`DesktopSupportConfig.kt` trong `build/`.

- **Ảnh QR** lấy từ dịch vụ công khai VietQR quick link:
  `https://img.vietqr.io/image/<BANK_ID>-<ACCOUNT_NO>-compact2.png?addInfo=Ung%20ho%20TS%20IPTV&accountName=…`.
  App ngân hàng nào quét cũng tự điền tài khoản. QR không có số tiền, để người ủng hộ tự chọn.
- **Thiếu cả link lẫn tài khoản:** thẻ ủng hộ và màn hình khởi động không hiện. Mọi ô chỉ hiện thẻ
  "TS IPTV cho Android" (mở Google Play). Bản MSI build hôm nay đang ở trạng thái này, vì chưa có giá
  trị thật.

## 4. Hành vi trên desktop

| Vị trí | Mobile (Android) | Desktop |
|---|---|---|
| App open | AdMob app open | Màn hình "Ủng hộ nhà phát triển": QR VietQR, nút "Ủng hộ online", "Google Play", "Để sau" |
| Banner Home / Player | AdMob banner → fallback Shopee | Thẻ "Ủng hộ TS IPTV" (bấm để mở màn hình ủng hộ) |
| Native trong danh sách (Home, Player, Programs) | AdMob native → fallback Shopee | Xen kẽ: ô chẵn là thẻ ủng hộ, ô lẻ là thẻ "TS IPTV cho Android" |
| Link "Xoá quảng cáo" | Có | Không (desktop không có quảng cáo để xoá) |
| Tab History (thẻ Shopee riêng) | Như cũ | Như cũ: thẻ Shopee vẫn hiện, không đổi |

Luật hiển thị màn hình khởi động (`SupportHost` trong `desktopMain/.../support/SupportPromos.kt`):

- Chỉ khi đã cấu hình ít nhất một kênh ủng hộ.
- Chỉ khi `AdsGate` cho phép fallback: đã qua 24 giờ đầu, không phải giao diện TV, không có gói "No ads".
- Phải sẵn sàng trong 4 giây sau khi mở app. Cách nhau tối thiểu **6 giờ**
  (`START_SCREEN_MIN_INTERVAL_MS`). Bấm ra ngoài hoặc "Để sau" để đóng.
- Đổi tần suất: sửa `START_SCREEN_MIN_INTERVAL_MS`. Đặt `0` thì hiện mỗi lần mở app.

**Kiểm thử nhanh:** đặt biến môi trường `TSIPTV_DEBUG_ADS_NO_FIRST_DAY=true` trước khi chạy app để bỏ
qua 24 giờ đầu. Biến này chỉ khiến thẻ ủng hộ hiện sớm hơn, không ảnh hưởng gì khác.

Mã nguồn liên quan:

- `commonMain/.../ui/ads/HouseAds.kt`: hook `expect` `rememberHouseAd(slot)` và `houseAdsReplaceAds`.
  Bản Android/iOS trả `null`/`false`, nên mobile **không đổi**.
- `commonMain/.../ui/screens/ads/ShopeeFallback.kt`: house promo được ưu tiên trước Shopee.
- `desktopMain/.../ui/ads/HouseAds.desktop.kt`: chọn thẻ cho từng ô.
- `desktopMain/.../support/`: `SupportLinks`, các thẻ, dialog, `SupportHost`.
- Chuỗi giao diện: `composeResources/values*/strings_support.xml` (7 ngôn ngữ).

## 4b. Player trên desktop

- **Hình được vẽ bằng Compose**, không còn dùng cửa sổ AWT riêng. libVLC giải mã vào bộ nhớ (callback
  `RV32`), `VideoFrameSink` chép từng khung hình sang Skia bitmap, rồi `MediaPlayerContent` vẽ khung hình
  vừa khít khung (letterbox, đúng tỉ lệ hiển thị). Trước đây `EmbeddedMediaPlayerComponent` (một `Canvas`
  AWT loại heavyweight) đè lên mọi thứ: che hết nút điều khiển và nuốt mọi cú click, nên không tạm dừng
  hay thoát player được.
- **Rê chuột lên video thì nút điều khiển hiện ra** (như mọi player desktop), sau 7 giây tự ẩn. Màn hình cảm
  ứng không đổi gì: ngón tay chưa chạm thì không tạo sự kiện di chuyển.
- **Toàn màn hình thật**: nút toàn màn hình giờ phủ kín cả màn hình. Trên Windows, `DesktopFullScreen` bỏ viền
  và thanh tiêu đề, rồi đặt cửa sổ phủ kín màn hình. Không dùng `WindowPlacement.Fullscreen` của Compose: trên
  Windows nó đưa tỉ lệ hiển thị về 100 % (ở mức 125 % trở lên, nút bấm bị nhỏ đi, còn thoát ra thì nội dung
  bị cắt). Thoát toàn màn hình thì cửa sổ về đúng vị trí và kích thước cũ.
- **Phím tắt** khi player đang mở: Space phát/tạm dừng, F bật/tắt toàn màn hình, Esc thoát toàn màn hình,
  M tắt tiếng, Trái/Phải tua 10 giây (chỉ với video có thời lượng).
- **Cửa sổ rộng (từ 960 dp, chỉ desktop):** player chia hai cột. Bên trái là video to nhất có thể (16:9),
  tên kênh và lịch phát sóng; bên phải là cột kênh rộng 380 dp, kênh đang phát có viền xanh và được cuộn tới
  khi đổi kênh. Đổi kênh không che video. Cửa sổ hẹp hơn vẫn dùng bố cục điện thoại (`WidePlayerLayout`
  trong `PlayerScreen.kt`).
- **Cột kênh bên phải** (`WidePlayerChannels.kt`): các kênh cùng nhóm với kênh đang phát; kênh không có
  nhóm (hoặc nhóm rỗng) thì hiện mọi kênh của playlist. Nhóm có dưới 10 kênh thì nối thêm các kênh xem gần
  đây (mới nhất trước), rồi mọi kênh còn lại của playlist. Mỗi kênh chỉ xuất hiện một lần.
- Ở bố cục điện thoại trên cửa sổ ngang, player thu nhỏ cao tối đa 60 % cửa sổ, để vẫn thấy danh sách kênh.
- Các lỗi đã sửa:
  - `isPlaying` không bao giờ đổi trên desktop: nút phát/tạm dừng hiện sai, bấm tạm dừng không có tác dụng,
    lịch sử không tính thời gian xem.
  - Bấm âm lượng hoặc tắt tiếng thì app crash (`TODO()`).
  - Bấm tạm dừng hai lần thì video phát tiếp.
  - Mở kênh mới vẫn thấy khung hình cuối của kênh cũ.
  - Thanh tiêu đề và taskbar hiện icon Java thay vì icon của app.

## 4c. Chuột và hộp thoại trên desktop

- **Danh sách ngang cuộn được bằng chuột** (hàng nhóm kênh ở Home, các hàng của TS IPTV Source, addon,
  mùa phim…): lăn chuột khi con trỏ nằm trên hàng thì hàng chạy ngang; tới cuối hàng thì lăn tiếp sẽ cuộn
  trang như cũ. Kéo bằng chuột trái cũng cuộn được, và kéo không bị tính là bấm. Compose chỉ cuộn ngang bằng
  Shift + lăn chuột hoặc touchpad, nên trước đây chuột thường không cuộn được. Mọi danh sách ngang mới phải
  dùng `TSLazyRow` (`ui/widgets/TSLazyRow.kt`) thay cho `LazyRow`. Test: `TSLazyRowMouseTest`.
- **Nhập playlist từ file:** trước đây bấm "Import from file" là hiện cửa sổ "Error" (`ClassCastException`).
  Hộp thoại chọn file của Windows chạy vòng lặp sự kiện riêng; mở nó từ một coroutine của Compose làm Compose
  chạy lồng vào dispatcher của chính coroutine đó. Giờ hộp thoại được mở bằng một sự kiện Swing riêng
  (`PlaylistFilePicker.desktop.kt`), kết quả mới đưa lại cho Compose. Đừng mở hộp thoại modal (FileDialog,
  JOptionPane…) bên trong `scope.launch` của Compose.

## 5. Phương án thay AdMob trên desktop (khảo sát 2026-10-11)

AdMob chỉ có SDK cho Android/iOS. Bảng dưới xếp theo mức phù hợp với TS IPTV (dev cá nhân ở Việt Nam,
app phát playlist của người dùng).

| # | Phương án | Đánh giá |
|---|---|---|
| 1 | **VietQR / chuyển khoản + link ủng hộ (Ko-fi qua PayPal, PayPal.me, GitHub Sponsors)** | **Đã làm.** Không phí, không cần ai duyệt. GitHub Sponsors hỗ trợ Việt Nam từ 10/2023. Ko-fi dùng PayPal khi không có Stripe; khả năng nhận tiền ở VN chưa xác minh. |
| 2 | **Affiliate Shopee/Lazada, chỉ khi người dùng bấm** | Đã có sẵn (tab History). Điều khoản Shopee Affiliate (01/12/2025) cấm pop-up, auto-redirect, iframe: **không được** dùng làm màn hình khởi động. Phải đăng ký app desktop là một kênh. |
| 3 | **Microsoft Store + gói trả phí "Supporter"** | Store Policy 10.8.1 cho phép app PC dùng IAP của Store (phí 15%) hoặc cổng thanh toán riêng (giữ 100%). Đăng ký dev cá nhân miễn phí từ 9/2025. Gọi `Windows.Services.Store` từ JVM cần cầu nối native và đóng gói MSIX: làm được nhưng tốn công. Store cũng giúp bỏ cảnh báo SmartScreen. |
| 4 | **Liftoff/Vungle Windows SDK** | SDK quảng cáo desktop duy nhất còn được bảo trì (Win32 7.x, 2026). Chỉ có trên Windows, là SDK native nên cần cầu nối JNA, và chưa rõ có nhận app IPTV không. Chỉ nên thử sau 1–3. |
| ✗ | AdSense/AdMob trong WebView desktop | **Cấm.** Chính sách AdSense cấm quảng cáo Google gắn vào phần mềm (trừ qua AdMob SDK). Vi phạm có thể khoá cả tài khoản Google, tức mất luôn AdMob trên Android. |
| ✗ | Paddle, Lemon Squeezy | Cả hai **cấm IPTV** trong danh mục sản phẩm. |
| ✗ | Buy Me a Coffee | Chỉ trả tiền qua Stripe, mà Stripe không hỗ trợ VN. |
| ✗ | Amazon Associates | Cấm link trong ứng dụng client. |
| ✗ | EthicalAds, Carbon Ads | Dành cho website cho lập trình viên, app IPTV không đủ điều kiện. |
| ✗ | Microsoft Advertising SDK, AdsJumbo | Đã khai tử (2020) hoặc ngừng phát triển. |

Lưu ý chính sách: hãy mô tả app là **trình phát playlist của chính người dùng**, không kèm hay gợi ý
nguồn kênh. Nhiều cổng thanh toán và chợ ứng dụng lọc từ khoá "IPTV".

Nguồn chính:

- AdSense program policies: https://support.google.com/adsense/answer/48182
- Shopee Affiliate terms (01/12/2025): https://shopee.vn/affiliate/kol-dieu-khoan-tham-gia-update-01-12-2025/
- Microsoft Store policies: https://learn.microsoft.com/en-us/windows/apps/publish/store-policies
- Paddle (cấm IPTV): https://paddle.com/help/start/intro-to-paddle/what-am-i-not-allowed-to-sell-on-paddle
- Lemon Squeezy (cấm IPTV): https://docs.lemonsqueezy.com/help/getting-started/prohibited-products
- GitHub Sponsors (thêm VN): https://github.blog/changelog/2023-10-03-github-sponsors-is-now-available-in-35-new-regions
- Stripe (không có VN): https://stripe.com/global
- Vungle Windows SDK: https://www.nuget.org/packages/VungleSDKForWindowsDesktopNative

## 6. Gợi ý bước tiếp theo

1. Điền 4 giá trị ở mục 3, build lại MSI, rồi kiểm tra màn hình ủng hộ bằng
   `TSIPTV_DEBUG_ADS_NO_FIRST_DAY=true`.
2. Nếu muốn có thu nhập định kỳ: đăng ký GitHub Sponsors (phù hợp nhất nếu có phần mã nguồn mở) hoặc
   Ko-fi + PayPal, rồi đặt link vào `TSIPTV_SUPPORT_URL`.
3. Cân nhắc phát hành trên Microsoft Store (miễn phí đăng ký). Bản Store không bị SmartScreen chặn, và
   sau này có thể thêm gói "Supporter".

## 7. Giới hạn đã biết

- **Không có đăng nhập trên desktop.** Firebase Auth và Firestore trên desktop chỉ chạy trong bộ nhớ
  (`InMemoryFirebaseAuth`, `InMemoryFirebaseFirestore`). Nút Google/Apple báo lỗi "not implemented". App mặc
  định bỏ qua Login (`AppFlags.worksSignedOut`), nhưng màn hình vẫn truy cập được từ Profile. Kế hoạch tích hợp
  Firebase thật, đăng nhập bằng QR quét từ điện thoại và màn Thiết bị đăng nhập:
  [plan-desktop-login-sync.md](plan-desktop-login-sync.md).
- **Không có analytics trên desktop.** `Firebase.analytics` trên JVM là `TODO()` và đã từng làm app
  crash khi thêm playlist. Mọi lệnh gọi giờ đi qua `AppAnalytics` và bị bỏ qua trên desktop.
- **macOS:** chưa ký số, chưa notarize (xem mục 2, macOS). Mới kiểm thử bản arm64.
- **Linux:** chưa build, chưa kiểm thử. Thiếu VLC thì kênh nào cũng báo lỗi phát chung chung, chưa có thông
  báo riêng kiểu "cần cài VLC".
- **Hình đi qua bộ nhớ:** mỗi khung hình được chép từ libVLC sang Compose (khoảng 8 MB/khung với 1080p). Máy yếu
  phát 4K có thể bị rớt khung. Khi đó khung hình bị bỏ qua chứ không dồn lại.
- **VLC chỉ gửi được 2 header:** `User-Agent` và `Referer`; header khác bị bỏ. Không có DRM
  (Widevine/PlayReady); kênh DRM bị từ chối trước khi phát.
- **Giấy phép:** libVLC theo LGPL-2.1+, kèm `COPYING.txt` trong thư mục `vlc/`. Phát hành rộng nên ghi
  công VideoLAN trong trang About/giấy phép.

## 8. Đã kiểm thử

### macOS (2026-10-12, MacBook Apple Silicon, macOS 26)

- `./gradlew :composeApp:run` mở app; `packageReleaseDmg` ra `TS IPTV-1.1.3.dmg` (200 MB, app 313 MB), phát hành trên GitHub là `desktop-1.1.3`;
  `codesign --verify --deep --strict` pass; mở `TS IPTV.app` thì app chạy.
- Smoke test tạm (không commit) chạy bằng chính launcher của app đã đóng gói (cùng chữ ký ad-hoc, hardened
  runtime, `disable-library-validation`): `DesktopVlc` thấy `resources/vlc/lib`, vlcj nạp libVLC 3.0.24, phát
  HLS bipbop của Apple tới trạng thái PLAYING. Tạo player lần đầu mất khoảng 5 giây.
- Lưu ý: lần đầu nạp các file VLC gốc (chữ ký VideoLAN, chưa ký lại) mất khoảng 3 phút, vì macOS kiểm tra chữ
  ký của từng plugin. Bản trong app đã được Compose ký lại nên không gặp.
- Chưa kiểm thử: bấm phát kênh qua giao diện của bản `.dmg`, máy Mac khác (Gatekeeper), bản Intel.

### Lần 3 (2026-10-12, cùng máy)

- Chạy app thật, chỉ điều khiển cửa sổ của bản dev (tìm theo process id):
  - Bấm "Import from file" trước khi sửa: hiện cửa sổ "Error", log có `ClassCastException`. Sau khi sửa:
    hộp thoại mở, chọn `qa/kodi/qc-addon.strm` thì app báo đúng lỗi addon Kodi; bấm Esc thì đóng, log không
    có exception.
  - Lăn chuột trên hàng nhóm kênh ở Home: hàng chạy ngang, danh sách kênh không cuộn.
  - Player ở cửa sổ 1280 dp: bố cục hai cột, đổi kênh từ cột phải, viền xanh đi theo kênh đang phát.
- `./gradlew :composeApp:desktopTest`: 609 test đều pass (thêm `TSLazyRowMouseTest`, test này fail với
  `LazyRow` thường). `compileDebugKotlinAndroid` pass.

### Lần 2 (2026-10-11, Windows 11, màn hình 2560×1440 ở mức 125 %, có màn hình phụ)

- Điều khiển giao diện thật của app (chụp màn hình, click chuột, gõ phím):
  - Thêm playlist `qa/kodi/qc-playback.m3u` qua URL: 9 kênh, 3 mục bị bỏ đúng như mong đợi.
  - Mở kênh có User-Agent và kênh không có header.
  - Tạm dừng/phát bằng nút và bằng phím Space, tắt tiếng, tua bằng thanh tua và phím Phải.
  - Vào/ra toàn màn hình bằng nút, phím F và Esc. Đổi kênh từ danh sách.
  - Mở lại app thì playlist và lịch sử vẫn còn nguyên.
- Smoke test tạm (không commit) với libVLC đóng gói, chạy 3 lần đều pass: phát HLS, khung hình tới được
  Compose, tạm dừng 2 lần vẫn dừng, phát tiếp được, tua tới 2:00, đổi kênh thì khung hình cũ bị xoá, link 404
  trả về `STREAM_FAILED`, sau lỗi vẫn phát lại được, dừng thì về `IDLE`.
- `./gradlew :composeApp:desktopTest`: 607 test đều pass. `compileDebugKotlinAndroid` pass.
- Gói phát hành: mở `Setup.exe` bằng Explorer (giống nhấp đúp) thì cài xong trong khoảng 5 giây, không hỏi gì,
  có lối tắt ở Start Menu (thư mục "TS IPTV") và Desktop, rồi tự mở app. Gỡ bằng MSI sạch hoàn toàn. Bản zip
  giải nén ra là chạy được ngay.
- Chưa kiểm thử: kênh DRM (logic chặn trước không thay đổi), kênh radio, macOS, Linux, máy chưa từng cài
  Java/VLC (bản đóng gói đã kèm cả hai).

### Lần 1

- `./gradlew :composeApp:desktopTest`: 607 test đều pass. `compileDebugKotlinAndroid` pass.
- Smoke test tạm (không commit): `DesktopMediaPlayer` + libVLC đóng gói + vlcj 4.12.1 phát luồng HLS
  công khai của Apple (bipbop). Trạng thái PLAYING, thời gian chạy tới 5.5 giây, snapshot khung hình
  1920×1080 giải mã đúng.
- `run` với cấu hình ủng hộ thử: màn hình khởi động hiện đúng, QR VietQR hiển thị. Render offscreen thẻ
  banner, thẻ Android và dialog đều đúng bố cục.
- Bản đóng gói (`createReleaseDistributable`): mở thẳng vào Home không cần đăng nhập, thư mục
  `app/resources/vlc` có đủ libVLC.
- Chưa kiểm thử: luồng thêm playlist rồi bấm phát qua giao diện của bản MSI đã cài (đã kiểm từng phần
  riêng: import playlist trả HTTP 200, phát video qua smoke test), macOS, Linux.
