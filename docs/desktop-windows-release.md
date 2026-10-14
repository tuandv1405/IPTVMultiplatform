# Hướng dẫn build bộ cài Windows (Setup.exe, MSI, zip)

Cập nhật 2026-10-12, theo bản desktop 1.1.2. Tài liệu này đi từng bước để tạo bộ cài Windows. Phần giải thích
chi tiết về cấu hình và hành vi của app nằm trong [desktop-build.md](desktop-build.md).

## 1. Kết quả

Chạy **một lệnh** để có đủ 5 file trong `composeApp/build/release/desktop/` (mỗi bộ cài khoảng 190–200 MB):

| File | Dùng để |
|---|---|
| `TS-IPTV-<v>-Setup.exe` | Cài 1 chạm: không hỏi gì, không cần quyền admin, cài xong tự mở app |
| `TS-IPTV-<v>-Windows-x64.msi` | Cài bằng Windows Installer (có thể cài im lặng: `msiexec /i <file> /passive`) |
| `TS-IPTV-<v>-Windows-x64-portable.zip` | Giải nén rồi chạy `TS IPTV\TS IPTV.exe`, không cần cài |
| `README.txt` | Hướng dẫn cài cho người dùng (tiếng Anh và tiếng Việt) |
| `SHA256SUMS.txt` | Mã SHA-256 của 3 bộ cài, đúng định dạng của `sha256sum` |

`<v>` là `versionMajor.versionMinor.versionPatch` trong `composeApp/build.gradle.kts`, ví dụ `1.1.2`.

## 2. Chuẩn bị máy build

- **Windows 10/11 x64.** Chỉ build được trên Windows: MSI cần WiX, còn Setup.exe cần `iexpress.exe` (có sẵn
  trong Windows). Trên macOS/Linux các task này không chạy được.
- **JDK 17 trở lên, có `jpackage`.** Máy dev hiện dùng Eclipse Temurin 21. `JAVA_HOME` phải trỏ tới JDK này,
  vì Gradle lấy runtime đóng gói vào app từ chính JDK đang chạy. Không dùng JRE (JRE không có `jpackage`).
- **Có Internet ở lần build đầu.** Lần đầu, build sẽ tải hai thứ:
  - **VLC 3.0.24 (win64):** tải từ download.videolan.org về `composeApp/build/vlc/`, kiểm tra SHA-256.
  - **WiX 3.11:** plugin Compose tự tải về `build/wix311/`.

  Các lần sau dùng lại file đã tải.
- Không cần cài VLC, WiX hay Visual Studio.

Kiểm tra nhanh:

```powershell
java -version            # 17 trở lên
jpackage --version       # phải chạy được
```

## 3. Các bước phát hành

### Bước 1: Lấy code mới nhất

```bash
git checkout main
git pull
```

### Bước 2: Tăng phiên bản

Mở `composeApp/build.gradle.kts` và tăng `versionPatch` (dùng chung với Android):

```kotlin
val versionMajor = 1
val versionMinor = 1
val versionPatch = 3   // trước đó là 2
```

**Bắt buộc với mỗi lần phát hành.** MSI chỉ thay được bản đã cài khi phiên bản mới **cao hơn**. Nếu cùng
phiên bản, Setup.exe chỉ mở app cũ, còn MSI báo "another version of this product is already installed".
Không bao giờ đổi `upgradeUuid`.

### Bước 3 (tuỳ chọn): Cấu hình thẻ "ủng hộ"

Thẻ ủng hộ hiện ở các chỗ đặt quảng cáo, dùng link ủng hộ và tài khoản VietQR. Thêm vào `local.properties`
ở gốc repo (không commit):

```properties
TSIPTV_SUPPORT_URL=https://...
TSIPTV_SUPPORT_BANK_ID=...
TSIPTV_SUPPORT_ACCOUNT_NO=...
TSIPTV_SUPPORT_ACCOUNT_NAME=...
```

Bỏ trống thì app không hiện thẻ ủng hộ. Chi tiết xem [desktop-build.md §3](desktop-build.md#3-cấu-hình-kênh-ủng-hộ).

### Bước 4: Build

```bash
./gradlew :composeApp:packageDesktopRelease
```

Trong PowerShell hoặc cmd, dùng `.\gradlew.bat :composeApp:packageDesktopRelease`.

Máy dev mất khoảng 3 phút. Lần đầu lâu hơn vì phải tải VLC và WiX. Task này lần lượt:

1. Tải và lọc libVLC vào app (`downloadVlcWin64`, `prepareDesktopVlc`).
2. Build MSI bằng jpackage và WiX (`packageDesktopMsi`).
3. Gói MSI cùng `composeApp/packaging/windows/install.cmd` thành Setup.exe tự giải nén bằng IExpress
   (`packageDesktopSetupExe`).
4. Nén thư mục app thành zip (`packageDesktopPortableZip`).
5. Ghi `SHA256SUMS.txt` (`desktopReleaseSums`), rồi gom tất cả vào `build/release/desktop/`.

Thư mục `build/release/desktop/` được ghi lại từ đầu mỗi lần build: file cũ (kể cả của phiên bản trước) bị
xoá. Muốn giữ bản cũ thì chép ra chỗ khác trước khi build.

### Bước 5: Kiểm tra file

```bash
cd composeApp/build/release/desktop
sha256sum -c SHA256SUMS.txt     # Git Bash: cả 3 dòng phải là OK
```

Trong PowerShell: `Get-FileHash .\TS-IPTV-*-Setup.exe` rồi so với `SHA256SUMS.txt`.

### Bước 6: Cài thử

Đóng TS IPTV nếu đang mở, rồi thử ít nhất:

- **Setup.exe:** nhấp đúp. App phải được cài vào `%LOCALAPPDATA%\TS IPTV` và tự mở. Nếu máy đang có bản cũ
  hơn, bản cũ phải được thay (Settings > Apps chỉ còn một dòng "TS IPTV").
- **Zip:** giải nén ra một thư mục bất kỳ và chạy `TS IPTV\TS IPTV.exe`.

Trong app, thêm một playlist và phát một kênh để chắc chắn VLC đã được đóng gói. Thử thêm **Import từ file**,
vì hộp thoại chọn file là phần mà test tự động không chạm tới.

Dữ liệu người dùng (`%USERPROFILE%\.tsiptv`) và cài đặt được giữ nguyên khi cài đè hoặc gỡ app.

### Bước 7: Phát hành

1. Viết release note `docs/releases/desktop-<v>.md` (xem [desktop-1.1.2.md](releases/desktop-1.1.2.md) làm mẫu).
   Release note được đăng công khai, nên không đặt link tới repo app (repo riêng tư).
2. Commit thay đổi phiên bản và release note, tạo tag `desktop-<v>`, rồi push cả hai.
3. CI (`.github/workflows/desktop-release.yml`) build lại Windows và macOS từ tag, đăng Release `desktop-<v>`
   ở repo công khai `tuandv1405/tsiptv-releases` và cập nhật trang tải về. Chi tiết và cài đặt một lần:
   [ci-desktop-release.md](ci-desktop-release.md).

Không đăng bộ cài vào Releases của repo app: repo này riêng tư, người dùng không tải được.

## 4. Chỉ build một loại file

| Muốn có | Lệnh | Kết quả nằm ở |
|---|---|---|
| Chỉ MSI | `./gradlew :composeApp:packageDesktopMsi` | `composeApp/build/release/stage/msi/` |
| MSI và Setup.exe | `./gradlew :composeApp:packageDesktopSetupExe` | `composeApp/build/release/stage/msi/` và `stage/setup/` |
| Chỉ zip | `./gradlew :composeApp:packageDesktopPortableZip` | `composeApp/build/release/stage/zip/` |
| MSI gốc của Compose (tên `TS IPTV-<v>.msi`) | `./gradlew :composeApp:packageReleaseMsi` | `composeApp/build/compose/binaries/main-release/msi/` |
| Thư mục app, không đóng gói | `./gradlew :composeApp:createReleaseDistributable` | `composeApp/build/compose/binaries/main-release/app/TS IPTV/` |
| Chạy thử từ mã nguồn | `./gradlew :composeApp:run` | |

## 5. Gặp lỗi

| Hiện tượng | Nguyên nhân và cách xử lý |
|---|---|
| Setup.exe chạy xong vẫn mở bản cũ, hoặc MSI báo đã có phiên bản khác | Chưa tăng `versionPatch` (bước 2). |
| Build dừng ở `downloadVlcWin64` vì sai checksum | File tải về hỏng hoặc `vlcWin64Sha256` không khớp `vlcVersion`. Xoá `composeApp/build/vlc/` rồi build lại. Đổi phiên bản VLC: xem [desktop-build.md](desktop-build.md#cập-nhật-vlc). Phải giữ VLC 3.x. |
| Sửa danh sách plugin VLC nhưng bộ cài không đổi | Gradle không nhận ra thay đổi bộ lọc. Xoá `composeApp/build/desktop-resources/` rồi build lại. |
| `jpackage` không tìm thấy, hoặc lỗi WiX | `JAVA_HOME` đang trỏ tới JRE hoặc JDK dưới 17. Nếu lỗi WiX, xoá `build/wix311/` để plugin tải lại. |
| `IExpress did not write …` | Không tìm thấy hoặc không chạy được `iexpress.exe` (nằm trong `C:\Windows\System32`). Chỉ build trên Windows bản đầy đủ. |
| App cài xong không mở được, chỉ hiện "Failed to launch JVM" | Runtime đóng gói thiếu module Java. Chạy `./gradlew :composeApp:suggestRuntimeModules`, rồi thêm module được gợi ý vào `modules(...)` trong `composeApp/build.gradle.kts`. |
| Mở app được nhưng kênh nào cũng báo lỗi phát | Bộ cài thiếu VLC: thư mục `%LOCALAPPDATA%\TS IPTV\app\resources\vlc` không có `libvlc.dll`. Chỉ build trên máy Windows mới đóng gói VLC. |
| Không xoá hoặc ghi được file trong `build/` | Một bản TS IPTV đang chạy từ thư mục build (zip đã giải nén, hoặc `createReleaseDistributable`). Đóng app đó rồi build lại. |
| Người tải về thấy "Windows protected your PC" | Bộ cài chưa ký số, cảnh báo này là bình thường. Người dùng bấm **More info**, rồi **Run anyway**. Muốn hết cảnh báo thì cần chứng chỉ code signing. |

## 6. Không có trong hướng dẫn này

- **macOS (`.dmg`) và Linux (`.deb`):** cần build trên máy macOS hoặc Linux tương ứng, và chưa đóng gói VLC
  (xem [desktop-build.md §2](desktop-build.md#macos)).
- **Ký số bộ cài:** chưa làm.
- **Build trên CI:** xem [ci-desktop-release.md](ci-desktop-release.md) (chạy khi đẩy tag `desktop-<v>`).
