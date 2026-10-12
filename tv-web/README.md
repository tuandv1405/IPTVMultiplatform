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
  scripts/package-tizen.js  ký dist/tizen thành dist/TSIPTV.wgt
  scripts/sim-check.js kiểm tra tự động trong trình giả lập TV (xem dưới)
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
npm run package:tizen     # -> dist/TSIPTV.wgt  (TIZEN_PROFILE=<profile> nếu không dùng TSIPTV)
tizen install -n TSIPTV.wgt -s <serial trong sdb devices> -- dist
tizen run -p TSIPTVtv01.TSIPTV -s <serial>
```

Nếu `tizen` không có trong PATH, đặt `TIZEN_STUDIO=<thư mục Tizen Studio>`. Tizen CLI tự đặt tên gói theo
`<name>` ("TS IPTV.wgt"); dấu cách làm `tizen install -n` lỗi trên Windows nên script đổi thành `TSIPTV.wgt`.

**TV Samsung và cả Samsung TV Emulator (Tizen 10) chỉ nhận gói ký bằng chứng chỉ Samsung.** Gói ký bằng
chứng chỉ Tizen thường bị từ chối: `install failed[118, -12] ... Invalid certificate chain`. Tạo chứng chỉ
Samsung bằng Certificate Manager (`tools/certificate-manager/certificate-manager.exe`), đăng nhập tài khoản
Samsung, chọn TV, thêm DUID của TV/emulator vào distributor certificate.

Package ID `TSIPTVtv01` (10 ký tự) và application ID `TSIPTVtv01.TSIPTV` cũng cố định sau khi lên store.
**Sao lưu author certificate**: mất nó thì không cập nhật được app.

## Thử trên trình giả lập (máy tính, không cần TV)

`scripts/sim-check.js` điều khiển app trong trình giả lập qua DevTools (gửi phím remote vào trang, không
đụng chuột/bàn phím thật) và kiểm tra: thông báo lần đầu, thêm playlist từ máy chủ không có CORS, nhóm,
điều hướng lưới, phát HLS, kênh hỏng báo lỗi, Back, hỏi thoát. Ảnh chụp ở `dist/sim-check/`.

**LG webOS TV Simulator** (tải ở webostv.developer.lge.com → Tools → Simulator, bản zip, giải nén là chạy;
bản 6.0 là Chromium 79, gần TV cũ nhất):

```bash
npm run build
"<Simulator>/webOS_TV_6.0_Simulator_1.4.1.exe" "<đường dẫn tuyệt đối>/dist/webos" "{}" \
  --remote-debugging-port=9334 --disable-backgrounding-occluded-windows
node scripts/sim-check.js --port 9334 --page dist/webos/index.html --back 461
```

**Samsung TV Web Simulator** (có sẵn trong Tizen Studio sau khi cài TV Extension: `tools/sec-tv-simulator`;
Chromium 55; app chạy trong iframe; nó chép app lúc khởi động nên build lại thì phải mở lại):

```bash
simulator.exe --file=file:///<đường dẫn tuyệt đối>/dist/tizen/index.html --platform=tizentv \
  --tizentvversion=10.0 --resolution=1920x1080 --remote-debugging-port=9335 --disableInspector
node scripts/sim-check.js --port 9335 --page ripple.html --frame appLauncher --back 10009
```

AVPlay của Web Simulator chỉ là bản giả: không phát được H.264 (thiếu codec), nên bước phát HLS báo SKIP.
Phát thật bằng AVPlay cần Samsung TV Emulator hoặc TV, cả hai cần chứng chỉ Samsung (xem trên).

Cài Tizen Studio trên Windows không có quyền admin: chạy bộ cài với `__COMPAT_LAYER=RunAsInvoker`, cài vào
thư mục không có dấu cách. Package Manager cần Java 8 (JDK đi kèm trong `tizen-studio/jdk`, chép ra chỗ
khác trước khi update vì nó tự thay JDK đó). Nếu biến môi trường `NoDefaultCurrentDirectoryInExePath` đang
bật, script `Install.bat` của các gói sẽ lỗi "is not recognized": bỏ biến đó khi chạy Package Manager.
Emulator TV cần WHPX (Windows Hypervisor Platform) hoặc HAXM.

## Đã kiểm tra / chưa kiểm tra

- Edge (Chromium mới) 1920x1080 không giao diện: toàn bộ luồng, phát HLS thật.
- LG webOS TV 6.0 Simulator (Chromium 79): `sim-check` đạt 13/13, gồm tải playlist từ `file://` không cần
  CORS và phát HLS bằng hls.js (Simulator không có HLS gốc; TV LG thật có).
- Samsung TV Web Simulator (Chromium 55, `webapis`/`tizen` giả lập): đạt hết trừ bước phát (SKIP như trên);
  `webapis.js`, `tvinputdevice.registerKey`, phím Back 10009, AVPlay `open/stop/close` chạy không lỗi.
- Samsung TV Emulator (Tizen 10): đã chạy được (WHPX), nhưng chưa cài được app vì cần chứng chỉ Samsung.
- **Chưa chạy trên TV thật.** Cần thử trước khi nộp store:
  - AVPlay phát HLS / MPEG-TS trên Samsung (emulator hoặc TV, cần chứng chỉ Samsung);
  - stream `.ts` thô (không phải HLS) và HLS gốc trên LG;
  - phím Back/CH/màu trên remote thật, bàn phím ảo khi nhập đường dẫn.
