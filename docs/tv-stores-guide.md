# Đưa TS IPTV lên store của Smart TV

Cập nhật 2026-10-13. App TV nằm ở [`tv-web/`](../tv-web/README.md) (một mã nguồn cho LG webOS và Samsung Tizen).
Thông tin store lấy từ tài liệu chính thức của LG, Samsung, Amazon, Titan OS (đánh dấu **[chính thức]**) hoặc từ
blog/diễn đàn (**[tham khảo]**). Điểm **[chưa xác minh]** cần hỏi lại store trước khi đầu tư thời gian.

## 1. Nên lên store nào (theo thứ tự)

| # | Store | Gói | Vì sao |
|---|---|---|---|
| 1 | **Google Play cho Android TV / Google TV** | APK/AAB hiện có | Đã có bản Android TV (`feature/android-tv`). Cá nhân ở Việt Nam đăng được, nhiều TV nhất (Sony, TCL, Xiaomi, Philips Android, TV box). |
| 2 | **Amazon Appstore (Fire TV, Fire OS)** | Cùng APK | Tài khoản miễn phí. Lưu ý: Fire TV đời mới (Vega OS, từ cuối 2025) không chạy APK nữa. Amazon duyệt app IPTV rất chặt. |
| 3 | **LG Content Store (webOS)** | `.ipk` từ `tv-web` | Store web TV dễ vào nhất cho cá nhân: LG cho phép **Individual Seller** **[chính thức]**, chưa thấy phí. |
| 4 | **Samsung Apps TV (Tizen)** | `.wgt` từ `tv-web` | Tài khoản thường (**Public Seller**) chỉ phát hành được ở **Mỹ** **[chính thức]**. Muốn ra Việt Nam / toàn cầu phải là **Partner Seller** (ký hợp đồng với Samsung), thực tế cần công ty. |
| 5 | Hisense VIDAA, Titan OS (Philips châu Âu) | Web app host trên HTTPS | Chỉ nhận đối tác doanh nghiệp; app host qua HTTPS sẽ chặn stream `http://` (phần lớn IPTV). Để sau khi có người dùng. |
| — | Bỏ qua: Vizio, Xumo/Comcast, Panasonic, Zeasn/Foxxum, Opera/Vewd | | Chỉ đối tác, chỉ Mỹ, hoặc đã đóng. |

**Việc cần làm ngay trước khi nộp LG/Samsung:** thử `tv-web` trên TV thật (xem
[tv-web/README.md](../tv-web/README.md), mục "Chưa kiểm tra"), và hỏi LG qua **Seller Lounge > 1:1 Q&A** rằng
người bán cá nhân ở Việt Nam có đăng ký và phát hành (kể cả ở Việt Nam) được không **[chưa xác minh]**.

## 2. Chuẩn bị chung (đã có sẵn)

| Việc | Trạng thái |
|---|---|
| Privacy policy có mục app TV | `web/public/privacy/`, mục "Smart TV app". **Cần deploy** trang web (bước 2.1) |
| Playlist cho người duyệt | https://tsiptv-download.web.app/tv/review.m3u (file `tsiptv-releases/public/tv/review.m3u`): 4 stream thử hợp pháp, đã phát thử cả 4, **đã deploy**. Không đặt trong `web/public/examples/` vì trang đó chỉ cho phép stream Mux và phim archive.org (`check_guides.py`). Test case App Testing (`qa/app-testing/`) cũng dùng file này: đổi tên kênh hay nhóm thì sửa test case theo |
| Icon, ảnh chụp (EN + VI) đúng cỡ từng store | [`tv-web/store/`](../tv-web/store/README.md) |
| Mô tả EN/VI, từ khoá, ghi chú cho người duyệt, nội dung UX scenario | [`tv-web/store/README.md`](../tv-web/store/README.md) |
| Gói `.ipk` / `.wgt` | `npm run package:webos` / `npm run package:tizen` trong `tv-web/` |

### 2.1 Đưa privacy policy lên mạng

```bash
python web/scripts/check_guides.py      # kiểm tra trang web trước khi deploy (xem web/README.md)
firebase deploy --only hosting:tsiptv-8bdd6
```

Kiểm tra lại: https://tsiptv-8bdd6.web.app/privacy/?lang=en có mục "Smart TV app".

Playlist demo: sửa `tsiptv-releases/public/tv/review.m3u` rồi `firebase deploy --only hosting` trong
`tsiptv-releases`. Trước mỗi lần nộp, phát thử lại từng kênh: người duyệt bấm vào kênh hỏng là app bị trả về.

### 2.2 Trước mỗi lần nộp

1. Tăng `version` trong `tv-web/package.json`. Lần đầu giữ `1.0.0`; mỗi lần nộp lại phải lớn hơn bản trước.
2. Chạy `npm test` và kiểm tra trên trình giả lập (`tv-web/README.md`, mục "Thử trên trình giả lập").
3. Thử trên TV thật (mục 3.2 và 4.3). Store kiểm tra trên TV thật: lỗi chỉ xảy ra trên TV sẽ làm app bị trả về.

## 3. LG webOS — LG Content Store

Cổng: Seller Lounge https://seller.lgappstv.com · Tài liệu https://webostv.developer.lge.com

### 3.1 Tạo tài khoản
1. Tạo **LG Developer account** tại webostv.developer.lge.com (một số hướng dẫn khuyên tắt xác thực 2 lớp khi
   đăng ký **[tham khảo]**).
2. Vào Seller Lounge, đăng ký **Individual Seller** (tên cá nhân; store hiển thị "Seller ID"). Công ty thì chọn
   Corporate Seller (cần giấy phép kinh doanh).
3. Hỏi **1:1 Q&A**: người bán cá nhân ở Việt Nam có đăng ký được không, và phát hành được ở những nước nào
   **[chưa xác minh]**. Chờ trả lời rồi mới làm hồ sơ.

### 3.2 Thử trên TV LG thật (Developer Mode)
1. Trên TV: mở **LG Content Store**, cài app **Developer Mode**, đăng nhập bằng LG Developer account, bật
   **Dev Mode Status** → TV khởi động lại.
2. Trên máy tính, trong `tv-web/`:
   ```bash
   npm install
   npm run package:webos
   npx ares-setup-device            # Add: tên "tv", IP của TV, port 9922, user prisoner
   npx ares-novacom --device tv --getkey   # bấm "Key Server" trên app Developer Mode, nhập passphrase 6 ký tự
   npx ares-install --device tv dist/tss.t.tsiptv_1.0.0_all.ipk
   npx ares-launch --device tv tss.t.tsiptv
   npx ares-inspect --device tv --app tss.t.tsiptv   # DevTools để xem lỗi
   ```
3. Thêm playlist demo, phát cả 4 kênh, thử phím Back/CH/đỏ trên remote thật và chuột Magic Remote.
4. Phiên Developer Mode có hạn (thường nói khoảng 50 giờ **[chưa xác minh]**). Bấm **Extend** trong app để gia
   hạn; hết hạn thì app đã cài bị xoá.

### 3.3 Nộp app
1. Seller Lounge > **Apps > Register App**. App ID: `tss.t.tsiptv` (đúng với `appinfo.json`, không đổi được sau
   khi phát hành).
2. Điền thông tin từ `tv-web/store/README.md`: tên, mô tả EN + VI, danh mục, quốc gia, phiên bản webOS hỗ trợ
   (webOS 4.0 trở lên), giá Free, độ tuổi, privacy policy URL, URL hỗ trợ.
3. Tải lên:
   - gói `tv-web/dist/tss.t.tsiptv_<version>_all.ipk`;
   - icon `tv-web/store/lg/icon-400x400.png`, màu nền tile `#03041d`;
   - ảnh nền `tv-web/store/lg/background-1920x1080.png`;
   - ảnh chụp trong `tv-web/store/lg/screenshots/en/`: ảnh chính `02-player.jpg`; ảnh phụ `01-channels`,
     `03-favorites`, `04-add-playlist`, `05-playlists`;
   - **UX scenario**: tải file PowerPoint mẫu trong Seller Lounge, điền theo mục "LG UX scenario" và "Ghi chú
     cho người duyệt" trong `tv-web/store/README.md`. Người duyệt làm đúng theo tài liệu này **[chính thức]**;
   - **Self-checklist**: tải file Excel mẫu, điền kết quả tự kiểm tra **thật** (sau bước 3.2). Bỏ trống, sơ sài
     hoặc ghi N/A sai đều bị trả về mà không qua QA **[chính thức]**.
4. Gửi QA. LG kiểm tra 3 bước: **Pretest** (thông tin) → **Function test** (chức năng) → **Content test**
   (nội dung) **[chính thức]**. Bị từ chối thì nhận danh sách lỗi, sửa, tăng version rồi nộp lại. Dự trù khoảng
   **3 tuần** mỗi vòng **[tham khảo]**.
5. Duyệt xong thì chọn ngày phát hành. App xuất hiện trong LG Content Store ở các nước đã chọn; người dùng tìm
   "TS IPTV" và bấm Install.
6. Mỗi bản cập nhật: tăng version, chạy `npm run package:webos`, nộp gói mới. Bản cập nhật lại qua QA.

## 4. Samsung Tizen — Samsung Apps TV

Cổng: TV Seller Office https://seller.samsungapps.com/tv · Tài liệu https://developer.samsung.com/smarttv

### 4.1 Tài khoản và phạm vi phát hành
- Ai có Samsung Account cũng đăng ký được → **Public Seller**: chỉ phát hành ở **Mỹ** **[chính thức]**.
- Muốn phát hành ở nước khác (gồm Việt Nam): cần **Partner Seller**, tức là hợp đồng với Samsung (trụ sở hoặc
  chi nhánh, ví dụ Samsung Vietnam), rồi gửi yêu cầu partnership trong Seller Office để Content Manager duyệt.
  Một nguồn cũ nói mất 2 tuần–2 tháng **[tham khảo]**. Cá nhân có được làm partner không: **[chưa xác minh]**,
  khả năng cao cần công ty.
- Gợi ý: đăng ký Public Seller, phát hành ở **Mỹ trước** để làm quen quy trình chứng nhận; song song hỏi Samsung
  Vietnam (qua 1:1 Q&A) về partnership.
- Privacy policy phải có bản tiếng Anh. App 18+ không được phát hành ở Hàn Quốc, Ả Rập Saudi, UAE **[chính thức]**.

### 4.2 Công cụ và chứng chỉ (bắt buộc, kể cả để thử trên emulator)
1. Tizen Studio + TV Extension: máy này đã cài ở `C:\Users\Admin\tv-tools\tizen-studio` (cách cài không cần
   quyền admin: `tv-web/README.md`).
2. Mở `tools\certificate-manager\certificate-manager.exe` → **+** → chọn **Samsung** → **TV** → đặt tên profile
   `TSIPTV` → đăng nhập Samsung Account:
   - **Author certificate**: tạo một lần, **sao lưu file .p12 và mật khẩu** ở nơi an toàn. Mất là không cập nhật
     được app nữa, vì mọi bản cập nhật phải ký bằng cùng author certificate.
   - **Distributor certificate**: privilege **Public**, thêm **DUID** của TV thử và của emulator (xem trong
     Device Manager khi thiết bị đã kết nối).
   - Khi phát hành, Samsung thay chữ ký distributor bằng chữ ký chính thức của họ.
3. Chứng chỉ Tizen thường (không phải của Samsung) bị cả TV và Samsung TV Emulator từ chối:
   `install failed[118, -12] ... Invalid certificate chain`.

### 4.3 Thử trên TV Samsung thật hoặc emulator
1. TV: mở **Apps**, bấm `1 2 3 4 5` trên remote → bật **Developer mode**, nhập IP máy tính → khởi động lại TV,
   rồi chạy `sdb connect <IP TV>`. Emulator: `em-cli launch -n tsiptv-tv` (máy ảo đã tạo sẵn trên máy này).
2. Trong `tv-web/`:
   ```bash
   npm run package:tizen        # ký bằng profile TSIPTV -> dist/TSIPTV.wgt
   tizen install -n TSIPTV.wgt -s <serial trong "sdb devices"> -- dist
   tizen run -p TSIPTVtv01.TSIPTV -s <serial>
   ```
   Lỗi cài đặt **1010** gần như luôn do distributor certificate thiếu DUID của thiết bị.
3. Thêm playlist demo, phát cả 4 kênh bằng AVPlay, thử phím Back/CH/đỏ, thử mất mạng (rút dây mạng).

### 4.4 Nộp app
1. Seller Office > **Applications > Create**. Tên ở ngôn ngữ mặc định phải **trùng** `<name>` trong `config.xml`
   ("TS IPTV") **[chính thức]**.
2. Tải gói `tv-web/dist/TSIPTV.wgt` (ký bằng profile Samsung `TSIPTV`).
3. **App Images**: icon `tv-web/store/samsung/icon-512x423.png`; icon 1920x1080 gồm
   `icon-1920x1080-logo.png` và `icon-1920x1080-background.png`; ảnh chụp `samsung/screenshots/en/*.jpg`
   (5 ảnh, tối thiểu 4). Trong lúc chứng nhận không đổi được icon.
4. Mô tả, từ khoá, danh mục, độ tuổi, privacy policy URL, quốc gia (Public Seller: chỉ Mỹ): lấy từ
   `tv-web/store/README.md`.
5. Ô **thông tin cho người kiểm tra**: dán mục "Ghi chú cho người duyệt".
6. Gửi chứng nhận: **Pre-test** (thông tin, gói, privilege) → **chứng nhận QA**; kết quả gửi qua email. Bị trả
   về thì sửa, tăng version rồi nộp lại.
7. Duyệt xong thì chọn ngày phát hành; app có trong Samsung Apps TV ở các nước đã chọn.
8. Danh sách bắt buộc https://developer.samsung.com/smarttv/develop/development-checklist/mandatory-features.html.
   App đã có sẵn:
   - [x] Back lùi một màn; ở màn chính hỏi thoát;
   - [x] báo khi mất mạng;
   - [x] tắt screensaver khi đang phát;
   - [x] dữ liệu chỉ nằm trong localStorage của app (gỡ app là xoá);
   - [ ] không treo/crash: cần tự kiểm tra trên TV thật với playlist lớn.

### 4.5 Sau khi phát hành (cả hai store)
- Sửa mục Smart TV trong `tsiptv-releases/public/releases.json` từ "soon" sang link store, rồi chạy
  `firebase deploy --only hosting` trong project đó để https://tsiptv-download.web.app hiện link.
- Mỗi bản cập nhật đều qua duyệt lại. Giữ `version` của bản LG và Samsung giống nhau cho dễ theo dõi.

## 5. Android TV, Google TV, Fire TV (dùng app Android)

- **Google Play**: bản Android TV (nhánh `feature/android-tv`) cần đủ yêu cầu TV: `LEANBACK_LAUNCHER`, banner
  320x180 (`brand/tv-banner-1280x720.png`), điều khiển hoàn toàn bằng D-pad, ảnh chụp màn hình TV. Chọn
  "Android TV" trong Play Console > Advanced settings > Form factors. Cần có production access trước
  (`play-store/production-access-application.md`).
- **Amazon Appstore** (developer.amazon.com, miễn phí): nộp cùng APK, chọn thiết bị Fire TV. Amazon đang cùng
  liên minh ACE chặn các app "IPTV" vi phạm bản quyền **[tham khảo]**: mô tả phải thật trung lập.
- Fire TV đời mới chạy **Vega OS** (không cài APK) **[chính thức]**: cần app riêng (React Native + WebView),
  có thể dựa trên `tv-web`. Lưu ý WebView của Vega tắt HTTP mặc định.

## 6. Rủi ro chính sách với app IPTV

- Không store nào có quy định cấm "IPTV player" thành văn. Nhưng nhiều app đã bị gỡ do **khiếu nại bản quyền**:
  Smart IPTV, SS-IPTV (Samsung 2018–2019); 36 app IPTV bị gỡ khỏi Google Play và App Store vào 03/2026
  **[tham khảo]**. Rủi ro đến từ khiếu nại, không phải từ khâu duyệt.
- Cách giảm rủi ro (app đã làm phần lớn):
  1. Không có kênh/playlist dựng sẵn, không gợi ý nguồn: **đã làm**.
  2. Thông báo lần đầu mở "chỉ là trình phát, bạn chịu trách nhiệm về nội dung": **đã làm**; nhắc lại trong
     mô tả store và Terms.
  3. Ảnh store, mô tả, từ khoá không nhắc thể thao, phim, "free channels", tên nhà cung cấp.
  4. Không quảng bá nội dung người lớn; chọn age rating trung thực.

## 7. Giới hạn kỹ thuật cần biết

| Năm TV | webOS (Chromium) | Tizen (Chromium) |
|---|---|---|
| 2018 | 4.0 (53) | 4.0 (56) |
| 2020 | 5.x (68) | 5.5 (69) |
| 2022 | 22 (87) | 6.5 (85) |
| 2024 | 24 (108) | 8.0 (108) |
| 2025 | 25 (120) | 9.0 (120) |

- Trình duyệt TV không tự cập nhật: TV 2018 mãi là Chromium 53/56. `tv-web` viết cho mức này (không
  async/await, không cú pháp mới).
- Stream `http://` phát được trong app đóng gói (`file://`) vì không bị chặn mixed content. App **host trên
  HTTPS** (VIDAA, Titan OS) sẽ chặn chúng.
- Tải playlist từ máy chủ khác có thể bị **CORS** chặn trên một số firmware **[chưa xác minh]**. Nếu gặp trên
  TV thật: cần một máy chủ trung gian hoặc API riêng của từng nền tảng.
- Remote: Back = 461 (LG), 10009 (Samsung); Samsung phải đăng ký phím media/màu: app đã làm.
