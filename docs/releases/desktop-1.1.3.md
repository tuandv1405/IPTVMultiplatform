**Download:** `TS-IPTV-1.1.3-macOS-arm64.dmg`, open it and drag TS IPTV into Applications. / **Tải về:** `TS-IPTV-1.1.3-macOS-arm64.dmg`, mở file rồi kéo TS IPTV vào Applications.

First macOS release. Apple Silicon Macs (M1 and later) only. Java and libVLC 3.0.24 are bundled, so
nothing else has to be installed. Windows stays on 1.1.2.

| File | Use |
|---|---|
| `TS-IPTV-1.1.3-macOS-arm64.dmg` | Open, drag `TS IPTV` into `Applications`. |
| `SHA256SUMS.txt` | Checksum of the dmg (`shasum -a 256 -c SHA256SUMS.txt`). |

## Tiếng Việt

### Tính năng mới
- **Bản cho macOS (Apple Silicon):** file `.dmg` đã có sẵn Java và VLC, không cần cài thêm gì.
- **Cột kênh bên phải của player (cửa sổ rộng):** hiện các kênh cùng nhóm với kênh đang phát. Kênh không có
  nhóm thì hiện mọi kênh. Nhóm có dưới 10 kênh thì hiện tiếp các kênh xem gần đây, rồi các kênh còn lại.
  Mỗi kênh chỉ xuất hiện một lần.

### Sửa lỗi
- Danh sách kênh trong player không còn lúc hiện lại nhóm của kênh xem trước đó.
- Kênh không có nhóm không còn giữ danh sách kênh của kênh trước.

### Lưu ý
- **Lần đầu mở app**, macOS báo không kiểm tra được app (app chưa được ký số với Apple): vào System Settings >
  Privacy & Security, bấm "Open Anyway". Hoặc chạy trong Terminal:
  `xattr -dr com.apple.quarantine "/Applications/TS IPTV.app"`
- **Logo kênh** lấy từ playlist (`tvg-logo`). Playlist không có `tvg-logo` thì kênh không có ảnh. Ví dụ với
  iptv-org: dùng `https://iptv-org.github.io/iptv/countries/vn.m3u` (có logo và nhóm), không dùng file
  `streams/vn.m3u` trong repo GitHub (không có logo, không có nhóm).
- Chưa có bản cho Mac Intel và Linux.

## English

### New
- **macOS build (Apple Silicon):** a `.dmg` with Java and VLC bundled; nothing else to install.
- **Channel column on the right of the player (wide window):** the playing channel's group. A channel
  without a group shows every channel. A group under 10 channels is followed by recently watched
  channels, then all the others. Each channel is listed once.

### Fixed
- The player's channel list no longer switches back to the group of a channel watched earlier.
- A channel without a group no longer keeps the previous channel's list.

### Notes
- **On first launch** macOS says it cannot check the app (it is not signed with Apple): open System
  Settings > Privacy & Security and click "Open Anyway", or run
  `xattr -dr com.apple.quarantine "/Applications/TS IPTV.app"` in Terminal.
- **Channel logos** come from the playlist (`tvg-logo`); a playlist without them shows no pictures. For
  iptv-org, use `https://iptv-org.github.io/iptv/countries/vn.m3u` (logos and groups), not
  `streams/vn.m3u` from the GitHub repository (neither).
- No Intel Mac or Linux build yet.
