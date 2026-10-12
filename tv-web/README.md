# TS IPTV cho Smart TV (LG webOS, Samsung Tizen)

Ứng dụng web (HTML/CSS/JS, không cần build) chạy trên TV LG (webOS) và Samsung (Tizen). Độc lập với app
Kotlin trong `composeApp/`: TV có trình duyệt Chromium cũ (webOS 4 = Chromium 53, Tizen 4 = Chromium 56),
không chạy được Compose/Wasm, nên bản TV là một app web nhẹ.

Hướng dẫn chọn store và đưa app lên store: [docs/tv-stores-guide.md](../docs/tv-stores-guide.md).

## Tính năng (1.0.0)

- Thêm nhiều danh sách phát M3U/M3U8 bằng đường dẫn; chọn, tải lại, xoá. **Không có kênh dựng sẵn.**
- Nhóm kênh theo `group-title`, cùng các nhóm Yêu thích, Xem gần đây, Tất cả.
- Lưới kênh có logo (`tvg-logo`), số kênh (`tvg-chno`). Chỉ vẽ các hàng đang thấy, nên danh sách hàng nghìn
  kênh vẫn mượt.
- Player toàn màn hình: ▲▼ hoặc CH+/CH− đổi kênh, OK mở thanh điều khiển (★ yêu thích, tạm dừng, về danh
  sách), ◀▶ tua 10 giây với video có thời lượng, nút Đỏ = yêu thích, Back = về danh sách.
- Phát: Samsung dùng **AVPlay** (trình phát phần cứng: HLS, DASH, MPEG-TS); LG và trình duyệt dùng
  `<video>` (HLS gốc), nếu không được thì chuyển sang **hls.js** 1.5.20 (`src/vendor/hls.js`, Apache-2.0).
  Kênh có DRM (`#KODIPROP` Widevine…) báo "không phát được".
- Lần đầu mở: thông báo app chỉ là trình phát, không cung cấp nội dung (yêu cầu của store).
- Back ở màn hình chính hỏi "Thoát?" (bắt buộc với Samsung). Báo khi mất mạng. Tắt screensaver khi đang
  xem (Samsung).
- Tiếng Việt / English (theo ngôn ngữ TV, đổi bằng nút trên thanh tiêu đề).
- Điều khiển bằng remote (D-pad) và chuột Magic Remote của LG (rê để chọn, bấm để mở).

## Cấu trúc

```
tv-web/
  src/                 app (mở src/index.html là chạy)
    js/m3u.js          parser M3U (có test)
    js/store.js        localStorage: danh sách phát, yêu thích, xem gần đây, cache kênh
    js/platform.js     phím remote, thoát app, screensaver theo từng TV
    js/player.js       AVPlay / <video> / hls.js
    js/app.js          màn hình, focus, hộp thoại
  platforms/webos/     appinfo.json, icon 80/130, splash 1920x1080
  platforms/tizen/     config.xml, icon 512x423
  scripts/build.js     ghép src + platforms vào dist/webos, dist/tizen; ghi version từ package.json
  scripts/serve.js     server thử trên máy tính
  scripts/make_icons.py  tạo icon từ brand/ (Pillow)
  test/                node --test
```

## Chạy thử trên máy tính

```bash
cd tv-web
npm install          # chỉ cần cho ares-package (LG)
npm test             # test parser M3U
npm run serve        # http://localhost:8080
```

Trong trình duyệt: phím mũi tên, Enter = OK, Esc hoặc Backspace = Back, PageUp/PageDown = CH+/CH−.
Mở DevTools ở chế độ 1920x1080 để thấy đúng bố cục TV.

Playlist thử với stream hợp pháp: `https://tsiptv-8bdd6.web.app/examples/playlist.m3u` (kênh "HLS Test
Channel" phát được; các kênh còn lại là ví dụ, sẽ báo lỗi).

## Đóng gói

Tăng `version` trong `package.json` (dạng `x.y.z`) trước mỗi lần nộp store. Script ghi số này vào
`appinfo.json` và `config.xml`.

### LG webOS (.ipk)

```bash
npm run package:webos     # -> dist/tss.t.tsiptv_<version>_all.ipk
```

Cài lên TV (TV đã bật Developer Mode, xem docs/tv-stores-guide.md §3):

```bash
npx ares-setup-device                       # thêm TV: IP, port 9922, user prisoner
npx ares-novacom --device tv --getkey       # nhập passphrase hiện trên app Developer Mode
npx ares-install --device tv dist/tss.t.tsiptv_1.0.0_all.ipk
npx ares-launch --device tv tss.t.tsiptv
npx ares-inspect --device tv --app tss.t.tsiptv   # DevTools từ xa
```

App ID `tss.t.tsiptv` **không đổi được** sau khi lên store.

### Samsung Tizen (.wgt)

Cần Tizen Studio (có TV Extensions) hoặc extension VS Code "Tizen TV", và một certificate profile tạo bằng
Samsung Certificate Manager (author + distributor, có DUID của TV thử). Đặt tên profile là `TSIPTV` (hoặc sửa
`package:tizen` trong `package.json`).

```bash
npm run package:tizen     # -> dist/tizen/TS IPTV.wgt
tizen install -n "TS IPTV.wgt" -t <tên TV trong sdb devices> -- dist/tizen
tizen run -p TSIPTVtv01.TSIPTV -t <tên TV>
```

Package ID `TSIPTVtv01` (10 ký tự) và application ID `TSIPTVtv01.TSIPTV` cũng cố định sau khi lên store.
**Sao lưu author certificate**: mất nó thì không cập nhật được app.

## Đã kiểm tra / chưa kiểm tra

- Đã chạy tự động trên Edge (Chromium) 1920x1080 không giao diện: thông báo lần đầu, thêm danh sách (bàn
  phím), nhóm, lưới cuộn, chọn nhóm, phát HLS thật (Mux test stream), yêu thích, đổi kênh sang kênh hỏng
  (báo lỗi sau ~1 giây), Back về đúng kênh, hộp thoại thoát. Đã tạo `.ipk` bằng `ares-package`.
- **Chưa chạy trên TV thật.** Cần thử trên TV LG và Samsung trước khi nộp store, đặc biệt:
  - tải playlist từ máy chủ khác (CORS khi app chạy từ `file://`);
  - AVPlay trên Samsung (chưa chạy được ngoài TV);
  - stream `.ts` thô (không phải HLS) trên LG;
  - phím Back/CH/màu trên remote thật, bàn phím ảo khi nhập đường dẫn.
