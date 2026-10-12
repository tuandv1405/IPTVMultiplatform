# TS IPTV Desktop 1.1.2 — Windows (laptop/PC)

First desktop release (1.1.1 was an internal test build; 1.1.2 replaces it when installed over it).
Windows 10/11, 64-bit only. Java and libVLC 3.0.24 are bundled, so nothing else
has to be installed. No macOS build: `jpackage` can only make a `.dmg` on a Mac (see
`docs/desktop-build.md`, section 2).

Build: `./gradlew :composeApp:packageDesktopRelease` (on Windows) → `composeApp/build/release/desktop/`.

| File | Use |
|---|---|
| `TS-IPTV-1.1.2-Setup.exe` | One-click install: no questions, no administrator prompt, Start Menu and desktop shortcuts, opens the app when done. |
| `TS-IPTV-1.1.2-Windows-x64.msi` | Windows Installer (silent: `msiexec /i TS-IPTV-1.1.2-Windows-x64.msi /passive`). |
| `TS-IPTV-1.1.2-Windows-x64-portable.zip` | Manual install: unzip anywhere, run `TS IPTV\TS IPTV.exe`. |
| `README.txt` | Install guide for users (English and Vietnamese). |
| `SHA256SUMS.txt` | Checksums of the three packages. |

## Tiếng Việt

### Tính năng mới
- **Bản cho Windows:** cài 1 chạm bằng `Setup.exe`, cài bằng MSI, hoặc giải nén file zip là chạy. App được cài
  cho tài khoản hiện tại vào `%LOCALAPPDATA%\TS IPTV`, không cần quyền quản trị.
- **Phím tắt trong player:** Space phát/tạm dừng, F bật/tắt toàn màn hình, Esc thoát toàn màn hình, M tắt tiếng,
  Trái/Phải tua 10 giây (video có thời lượng).
- **Rê chuột lên video** là hiện nút điều khiển, sau 7 giây tự ẩn.
- **Player cho màn hình lớn:** khi cửa sổ đủ rộng, video nằm bên trái cùng tên kênh và lịch phát sóng, danh
  sách kênh nằm ở cột bên phải. Kênh đang phát được tô viền; đổi kênh không che video.

### Sửa lỗi
- Nhập playlist từ file không còn báo lỗi: hộp thoại chọn file mở bình thường và file được nhập.
- Các danh sách ngang (nhóm kênh ở Home, các hàng phim, addon…) cuộn được bằng con lăn chuột và bằng cách kéo.
- Video không còn đè lên nút điều khiển: trước đây không bấm được tạm dừng, không thoát được player.
- Nút phát/tạm dừng hiện đúng trạng thái, và lịch sử xem tính đúng thời gian xem.
- Chỉnh âm lượng hoặc tắt tiếng không còn làm app crash.
- Bấm tạm dừng hai lần không còn làm video phát tiếp.
- Mở kênh mới không còn hiện khung hình cuối của kênh cũ.
- Toàn màn hình phủ kín màn hình. Ở mức hiển thị 125 % trở lên, giao diện không còn bị thu nhỏ, và thoát ra
  thì cửa sổ về đúng vị trí và kích thước cũ.
- Player ở cửa sổ ngang cao tối đa 60 % cửa sổ, vẫn thấy danh sách kênh bên dưới.
- Thanh tiêu đề, taskbar và Start Menu hiện đúng icon và tên app.

### Lưu ý
- App chưa được ký số: Windows có thể báo "Windows protected your PC", bấm "More info" rồi "Run anyway".
- Chưa có bản macOS và Linux. Chưa kiểm thử kênh DRM và radio trên desktop.

## English

### New
- **Windows build:** one-click `Setup.exe`, an MSI, or a portable zip. It installs for the current user in
  `%LOCALAPPDATA%\TS IPTV`, without an administrator prompt.
- **Player shortcuts:** Space play/pause, F full screen, Esc leave full screen, M mute, Left/Right seek 10 s
  (videos with a duration).
- **Moving the mouse over the video** shows the controls; they hide again after 7 s.
- **Player for large screens:** in a wide window the video sits on the left with the title and the guide,
  and the channels are in a column on the right. The playing channel is outlined; switching channel never
  covers the video.

### Fixed
- Importing a playlist from a file no longer fails with an error: the file dialog opens and the file is imported.
- Horizontal lists (channel groups on Home, movie rows, addons…) scroll with the mouse wheel and by dragging.
- The video no longer covers the player controls. Before, playback could not be paused or left.
- The play/pause button shows the right state, and watch history counts the time watched.
- Changing the volume or muting no longer crashes the app.
- Pressing pause twice no longer resumes playback.
- A new channel no longer opens on the last frame of the previous one.
- Full screen covers the whole screen. At 125 % display scaling or more the UI no longer shrinks, and leaving
  full screen puts the window back where it was, at its old size.
- In a landscape window the player is at most 60 % of the window height, so the channel list stays visible.
- The title bar, taskbar and Start Menu show the app's icon and name.

### Notes
- The app is not code-signed yet: Windows may show "Windows protected your PC"; click "More info", then
  "Run anyway".
- No macOS or Linux build yet. DRM and radio channels were not tested on desktop.

## Technical notes
- **Rendering:** libVLC decodes into memory (`CallbackVideoSurface`, RV32) and Compose draws the frames
  (`VideoFrameSink`, `MediaPlayerContent`), replacing the heavyweight AWT canvas.
- **Full screen on Windows:** borderless window over the monitor through JNA (`DesktopFullScreen`), because
  Compose's `WindowPlacement.Fullscreen` resets the DPI scale on Windows. macOS/Linux keep the Compose one.
- **Mouse:** `TSLazyRow` (`ui/widgets/TSLazyRow.kt`) maps the vertical wheel and mouse drags to horizontal
  scrolling on desktop; every horizontal list uses it.
- **File picker:** the modal `FileDialog` is opened from its own Swing event, not from a Compose coroutine;
  its nested event loop re-entered Compose's dispatcher and broke the coroutine (`ClassCastException`).
- **Wide player:** `WidePlayerLayout` in `PlayerScreen.kt`, desktop windows from 960 dp.
- **Packaging:** `perUserInstall`, Start Menu group "TS IPTV", `Setup.exe` made with IExpress
  (`composeApp/packaging/windows/install.cmd`). The two Blu-ray `.jar` files of VLC are no longer bundled.
- **Version:** shares `versionMajor.versionMinor.versionPatch` with Android. An MSI only replaces an installed
  version when the new one is higher: bump `versionPatch` before the next desktop release.
- **Tests:** `desktopTest` 609 passing (`TSLazyRowMouseTest` added); Android compiles. Played through the real UI on Windows 11 (125 %
  scaling, two monitors) and a headless libVLC smoke test (play, pause, seek, channel switch, 404 error and
  recovery, stop). Each package was installed, run and uninstalled. The file picker, wheel scrolling and
  the wide player were checked in the running app.
