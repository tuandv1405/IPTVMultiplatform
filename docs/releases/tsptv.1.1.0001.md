# TS IPTV 1.1 — `tsptv.1.1.0001` (versionCode 1010001)

Tag: `tsptv.1.1.0001`. Play Console "What's new" text (en-US / vi-VN, under 500 characters each): `play-store/release-notes/1.1.0.md`.

## Tiếng Việt

### Tính năng mới
- **Playlist M3U kiểu Kodi**
  - Header riêng cho từng kênh (User-Agent, Referer, Cookie…).
  - DRM Widevine, ClearKey và PlayReady trên Android.
  - Đọc thông tin catch-up và lịch phát sóng từ `x-tvg-url`.
  - Mở được file `.strm`.
- **Addon tương thích Stremio**
  - Người dùng tự thêm addon của mình. App không cài sẵn addon nào.
  - Duyệt danh mục, tìm kiếm, xem chi tiết phim và phim bộ, chọn luồng phát, và xem tiếp từ vị trí cũ.
  - Có giao diện cho cả điện thoại và Android TV.
  - Link addon được mã hoá trên máy.
- **Định dạng TS IPTV Source**
  - Một file JSON để tự tạo nguồn: kênh, phim lẻ, phim bộ, trang chủ tuỳ biến (hero, các hàng, lưới) và màu sắc riêng.
  - Có thể ghép thêm M3U, XMLTV, addon và nguồn khác.
  - Trước khi import, app hiện màn hình xem trước: danh sách máy chủ sẽ kết nối, số mục bị bỏ qua và xác nhận 18+.
- **Trong khi xem**
  - Đổi luồng phát hoặc bật phụ đề (VTT, SRT) ngay trong player.
  - Kênh có nhiều luồng thì tự chọn luồng đầu tiên mà thiết bị phát được.

### Cải tiến
- **Android TV:** điều hướng bằng remote tốt hơn. Con trỏ quay về đúng chỗ sau khi bấm Back, và tìm kiếm dùng được bằng D-pad.
- **Lịch phát sóng:** kênh không có `epgId` được ghép theo tên. Mỗi lịch giữ bản tốt gần nhất khi tải lỗi.
- **Trang web:**
  - Thêm hướng dẫn cho các định dạng IPTV, M3U, XMLTV, XSPF, JSON, Xtream Codes, Kodi, MonPlayer, addon Stremio và TS IPTV Source.
  - Có trang kiểm tra file TS IPTV Source ngay trên trình duyệt.
  - Thanh điều hướng mới dạng icon, chuyển trang và cuộn mượt hơn.

### Sửa lỗi
- Bản release (R8) không còn báo thiếu rule khi build, và đã được thử trên máy ảo mà không bị crash.
- Kênh trực tiếp phát sau một phim không còn hiện thanh tua của phim.
- Sửa nhiều trường hợp crash khi dữ liệu có id trùng, và lỗi tiếp tục xem bị ghi đè vị trí.

### Dữ liệu và quyền riêng tư
- Event Analytics khi thêm nguồn giờ kèm định dạng, số kênh, có lịch phát sóng hay không, và tên miền.
- Link gửi lên là **link đã làm sạch**: đã bỏ tài khoản, mật khẩu, query, token và header. Link của từng kênh và tên playlist không bao giờ được gửi.
- Khai báo Data safety đã được cập nhật trong `play-store/data-safety.md`.

### Ghi chú kỹ thuật
- **Database:** Room v3 → v6 bằng AutoMigration và giữ nguyên dữ liệu cũ (đã kiểm tra trên dữ liệu thật).
- **Nền tảng:** iOS chưa được build và kiểm tra cho bản này. Bản desktop chưa được chạy thử.

## English

### New
- **Kodi-style M3U playlists**
  - Per-channel headers.
  - Widevine, ClearKey and PlayReady DRM on Android.
  - Catch-up information and `x-tvg-url` guides.
  - `.strm` files.
- **Stremio-compatible addons**
  - Users add their own addons; none are bundled.
  - Catalogues, search, movie and series details, stream picker, and Continue watching with resume.
  - Phone and Android TV layouts.
  - Addon links are encrypted on the device.
- **TS IPTV Source format**
  - One JSON file for your own source: channels, movies, series, a custom home layout (hero, rows, grids) and colours.
  - It can pull in M3U, XMLTV, addons and other sources.
  - Before import, a preview shows every server the source contacts, the skipped items and an 18+ confirmation.
- **While playing**
  - Switch stream or turn on VTT/SRT subtitles from the player.
  - Channels with several streams play the first one the device supports.

### Improved
- **Android TV:** better remote navigation. Focus returns to the right place after Back, and search works with the D-pad.
- **Programme guide:** channels without `epgId` are matched by name, and each guide keeps its last good copy when a fetch fails.
- **Website:**
  - Format guides for IPTV, M3U, XMLTV, XSPF, JSON, Xtream Codes, Kodi, MonPlayer, Stremio addons and TS IPTV Source.
  - An in-browser validator for TS IPTV Source files.
  - New icon navigation and smoother page changes and scrolling.

### Fixed
- The R8 release build no longer reports a missing rule, and it was smoke-tested on an emulator without crashes.
- A live channel played after a movie no longer shows the movie's seek bar.
- Several crashes on duplicate ids, and Continue watching positions being overwritten.

### Data and privacy
- The add-source Analytics event now carries the format, the channel count, whether a guide is included, and the host.
- The link it carries is a **sanitized** link, without credentials, query, tokens or headers. Channel links and playlist names are never sent.
- The Data safety notes are updated in `play-store/data-safety.md`.

### Technical notes
- **Database:** Room v3 → v6 by AutoMigration, keeping existing data (verified on real data).
- **Platforms:** iOS has not been built or tested for this release. The desktop build was not run.
