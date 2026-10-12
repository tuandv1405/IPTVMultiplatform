**Download:** `TS-IPTV-1.1.4-Setup.exe` (one-click install) or `TS-IPTV-1.1.4-Windows-x64-portable.zip`. / **Tải về:** `TS-IPTV-1.1.4-Setup.exe` (cài 1 chạm) hoặc `TS-IPTV-1.1.4-Windows-x64-portable.zip`.

Windows 10/11, 64-bit. Installs over 1.1.2. Java and libVLC 3.0.24 are bundled. macOS stays on 1.1.3
(a `.dmg` can only be built on a Mac).

| File | Use |
|---|---|
| `TS-IPTV-1.1.4-Setup.exe` | One-click install: no questions, no administrator prompt, opens the app when done. |
| `TS-IPTV-1.1.4-Windows-x64.msi` | Windows Installer (silent: `msiexec /i TS-IPTV-1.1.4-Windows-x64.msi /passive`). |
| `TS-IPTV-1.1.4-Windows-x64-portable.zip` | Unzip anywhere, run `TS IPTV\TS IPTV.exe`. |
| `README.txt` | Install guide (English and Vietnamese). |
| `SHA256SUMS.txt` | Checksums of the three packages (`sha256sum -c SHA256SUMS.txt`). |

## Tiếng Việt

### Tính năng mới
- **Cột kênh bên phải của player (cửa sổ rộng)** — đã có trên macOS 1.1.3, nay có trên Windows: hiện các kênh
  cùng nhóm với kênh đang phát. Kênh không có nhóm thì hiện mọi kênh. Nhóm có dưới 10 kênh thì hiện tiếp các
  kênh xem gần đây, rồi các kênh còn lại.

### Sửa lỗi
- **Addon Stremio (và kênh IPTV) phát được trên Windows như trên điện thoại.** Nhiều máy chủ stream dùng chứng
  chỉ HTTPS cấp cho tên miền khác (CDN). Bản trước từ chối các stream này; nay chúng được phát giống app Android.
- Danh sách kênh trong player không còn lúc hiện lại nhóm của kênh xem trước đó.

## English

### New
- **Channel column on the right of the player (wide window)** — shipped on macOS 1.1.3, now on Windows: the
  playing channel's group; a channel without a group shows every channel; a group under 10 channels is
  followed by recently watched channels, then the others.

### Fixed
- **Stremio addon streams (and IPTV channels) now play on Windows as they do on Android.** Many stream servers
  present an HTTPS certificate issued for another name (CDNs); 1.1.2 refused them, 1.1.4 plays them like the
  Android app does.
- The player's channel list no longer switches back to the group of a channel watched earlier.
