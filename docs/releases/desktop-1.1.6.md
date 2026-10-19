**Download:** Windows `TS-IPTV-1.1.6-Setup.exe` (one-click install), macOS `TS-IPTV-1.1.6-macOS-arm64.dmg`. / **Tải về:** Windows `TS-IPTV-1.1.6-Setup.exe` (cài 1 chạm), macOS `TS-IPTV-1.1.6-macOS-arm64.dmg`.

Windows and macOS together again, on the same version. Windows 10/11 64-bit, installs over 1.1.4. Apple
Silicon Macs (M1 and later), replaces 1.1.3. Java and libVLC 3.0.24 are bundled.

| File | Use |
|---|---|
| `TS-IPTV-1.1.6-Setup.exe` | Windows: one-click install, no questions, no administrator prompt, opens the app when done. |
| `TS-IPTV-1.1.6-Windows-x64.msi` | Windows Installer (silent: `msiexec /i TS-IPTV-1.1.6-Windows-x64.msi /passive`). |
| `TS-IPTV-1.1.6-Windows-x64-portable.zip` | Windows: unzip anywhere, run `TS IPTV\TS IPTV.exe`. |
| `TS-IPTV-1.1.6-macOS-arm64.dmg` | macOS: open, drag `TS IPTV` into `Applications`. First launch: System Settings > Privacy & Security > Open Anyway. |
| `README.txt` | Windows install guide (English and Vietnamese). |
| `SHA256SUMS.txt` | Checksums of every package (`sha256sum -c SHA256SUMS.txt`, on macOS `shasum -a 256 -c SHA256SUMS.txt`). |

## Tiếng Việt

### Tính năng mới
- **Trang chủ dạng lưới:** cửa sổ rộng hiện 2 thẻ kênh mỗi hàng (từ 640 dp) hoặc 3 thẻ (từ 1100 dp), cho cả
  "Xem tiếp" và "Tất cả kênh". Trước đây mỗi kênh chiếm cả một hàng.
- **Thích / Không thích** ngay cạnh tên kênh trong player cửa sổ rộng. Lựa chọn được lưu trên máy này.
- **Chia sẻ ảnh** khung hình đang phát, kèm tên kênh: Facebook và Telegram mở trang chia sẻ trên web, ảnh nằm sẵn
  trong clipboard để dán vào. Có thể sao chép hoặc lưu ảnh.
- **Clip:** nút Clip thay cho nút Báo lỗi. Lưu 15, 30 hoặc 60 giây tiếp theo của kênh đang phát (hoặc của phim,
  từ vị trí đang xem) thành file MP4 trong `Videos/TS IPTV` (macOS: `Movies/TS IPTV`), trong khi video vẫn phát. Kênh có DRM,
  radio và DASH chưa làm clip được.
- **Dưới tên kênh trong player cửa sổ rộng:** ưu đãi Shopee khi có quảng cáo, nếu không thì thẻ "Ủng hộ TS IPTV".

### Sửa lỗi
- **macOS:** có bản sửa của Windows 1.1.4. Các stream (addon Stremio, kênh IPTV) mà máy chủ dùng chứng chỉ HTTPS
  cấp cho tên miền khác (CDN) nay phát được, giống app Android.

## English

### New
- **Home as a grid:** a wide window shows two channel cards per line (from 640 dp) or three (from 1100 dp), for
  both Continue watching and All channels. Each channel used to take a whole line.
- **Like / Dislike** next to the title in the wide player, saved on this computer.
- **Share a picture** of what is playing, with the channel name: Facebook and Telegram open their web share page
  with the picture on the clipboard, ready to paste; or copy or save the picture.
- **Clip** replaces Report: saves the next 15, 30 or 60 seconds of the live channel (or of a movie, from the
  shown position) as an MP4 in `Videos/TS IPTV` (macOS: `Movies/TS IPTV`) while playback goes on. DRM channels, radio and DASH cannot be
  clipped yet.
- **Under the title in the wide player:** the Shopee offer while ads may show, otherwise the "Support TS IPTV"
  card.

### Fixed
- **macOS** gets the Windows 1.1.4 fix: streams (Stremio addons, IPTV channels) whose server presents an HTTPS
  certificate issued for another name (CDNs) now play, like they do in the Android app.
