# Đưa TS IPTV lên store của Smart TV

Cập nhật 2026-10-12. App TV nằm ở [`tv-web/`](../tv-web/README.md) (một mã nguồn cho LG webOS và Samsung Tizen).
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

## 2. Chuẩn bị chung (dùng cho mọi store)

1. **Chính sách quyền riêng tư** bằng tiếng Anh: dùng `https://tsiptv-8bdd6.web.app/privacy/` (bổ sung một đoạn
   về app TV: dữ liệu chỉ lưu trên TV, không gửi đi).
2. **Playlist demo cho người duyệt**, chỉ chứa stream hợp pháp (stream thử của Apple/Mux/Akamai). Có thể dùng
   `https://tsiptv-8bdd6.web.app/examples/playlist.m3u` nhưng nên tạo một file riêng, ví dụ
   `web/public/examples/tv-review.m3u`, gồm 3–5 kênh **đều phát được**. Samsung đánh trượt app khi không thử
   được tính năng **[chính thức]**.
3. **Ảnh chụp màn hình** 1920x1080 hoặc 1280x720, chỉ dùng kênh demo: **không** logo kênh có bản quyền, không
   bóng đá/thể thao, không chữ "miễn phí".
4. **Mô tả store** trung lập: "Trình phát IPTV cho danh sách M3U của bạn. App không cung cấp nội dung."
   Không nhắc tên nhà cung cấp IPTV nào.
5. **Icon**: đã có trong `tv-web/platforms/` (khi đổi logo, chạy lại `python tv-web/scripts/make_icons.py`).
   Store còn cần icon lớn (LG ≥ 400x400): dùng `brand/app-icon-1024.png`.

## 3. LG webOS — LG Content Store

Cổng: Seller Lounge https://seller.lgappstv.com · Tài liệu https://webostv.developer.lge.com

### 3.1 Tạo tài khoản
1. Tạo **LG Developer account** tại webostv.developer.lge.com (một số hướng dẫn khuyên tắt xác thực 2 lớp khi
   đăng ký **[tham khảo]**).
2. Vào Seller Lounge, đăng ký **Individual Seller** (tên cá nhân; store hiển thị "Seller ID"). Công ty thì chọn
   Corporate Seller (cần giấy phép kinh doanh).
3. Hỏi 1:1 Q&A về việc đăng ký từ Việt Nam và danh sách quốc gia phát hành **[chưa xác minh]**.

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
3. Phiên Developer Mode có hạn (thường nói khoảng 50 giờ **[chưa xác minh]**); bấm **Extend** trong app để gia
   hạn, hết hạn thì app đã cài bị xoá.

### 3.3 Nộp app
1. Seller Lounge > **Apps > Register App**. App ID: `tss.t.tsiptv` (đúng với `appinfo.json`, không đổi được sau
   khi phát hành).
2. Điền thông tin: tên, mô tả (EN + VI), danh mục (Entertainment / Video), quốc gia, phiên bản webOS hỗ trợ
   (chọn webOS 4.0 trở lên), giá (Free), age rating.
3. Tải lên:
   - file `.ipk` (`npm run package:webos`; mỗi lần nộp phải tăng `version` trong `package.json`);
   - icon ≥ 400x400, màu nền tile (`#03041d`), ảnh chụp 1 chính + 2–5 phụ (960x540 hoặc 1280x720), ảnh nền
     launcher/splash 1920x1080 **[tham khảo]**;
   - **UX scenario** (file PowerPoint mẫu tải từ Seller Lounge): mô tả từng màn hình và cách dùng. Ghi rõ:
     "Bấm Add playlist → nhập `<URL playlist demo>` → Save → chọn kênh → OK để xem". Viết chi tiết: người duyệt
     làm theo đúng tài liệu này **[chính thức]**;
   - **Self-checklist** (file Excel mẫu): điền kết quả tự kiểm tra thật; bỏ trống hoặc sơ sài sẽ bị trả về
     **[chính thức]**.
4. Gửi QA. LG kiểm tra 3 bước: **Pretest** (thông tin) → **Function test** (lỗi chức năng) → **Content test**
   (nội dung) **[chính thức]**. Bị từ chối thì nhận danh sách lỗi, sửa, nộp lại. Thời gian: dự trù khoảng
   **3 tuần** mỗi vòng **[tham khảo]**.
5. Mỗi bản cập nhật đều qua QA lại.

## 4. Samsung Tizen — Samsung Apps TV

Cổng: TV Seller Office https://seller.samsungapps.com/tv · Tài liệu https://developer.samsung.com/smarttv

### 4.1 Tài khoản và phạm vi phát hành
- Ai có Samsung Account cũng đăng ký được → **Public Seller**: chỉ phát hành ở **Mỹ** **[chính thức]**.
- Muốn phát hành ở nước khác (gồm Việt Nam): cần **Partner Seller** = hợp đồng với Samsung (trụ sở hoặc chi nhánh,
  ví dụ Samsung Vietnam) rồi gửi yêu cầu partnership trong Seller Office, Content Manager duyệt. Một nguồn cũ nói
  mất 2 tuần–2 tháng **[tham khảo]**. Cá nhân có được làm partner không: **[chưa xác minh]**, khả năng cao cần
  công ty.
- Gợi ý: đăng ký Public Seller, phát hành **Mỹ trước** để làm quen quy trình chứng nhận; song song liên hệ Samsung
  Vietnam (qua 1:1 Q&A) về partnership.
- Privacy policy phải có bản tiếng Anh. App 18+ không được phát hành ở Hàn Quốc, Ả Rập Saudi, UAE **[chính thức]**.

### 4.2 Công cụ và chứng chỉ
1. Cài **Tizen Studio** (kèm **TV Extensions**) hoặc VS Code extension **Tizen TV** (`tizensdk.tizentv`).
2. Mở **Certificate Manager** → tạo profile **Samsung** tên `TSIPTV`:
   - **Author certificate**: tạo một lần, **sao lưu file .p12 và mật khẩu**. Mọi bản cập nhật phải ký cùng author.
   - **Distributor certificate**: thêm **DUID** của TV thử (Device Manager đọc được khi TV đã kết nối).
   - Khi phát hành, Samsung thay chữ ký distributor bằng chữ ký chính thức.

### 4.3 Thử trên TV Samsung thật
1. Trên TV: mở **Apps**, bấm `1 2 3 4 5` trên remote → bật **Developer mode**, nhập IP máy tính → khởi động lại TV.
2. Trên máy tính: `sdb connect <IP TV>` (hoặc Device Manager), rồi trong `tv-web/`:
   ```bash
   npm run package:tizen
   tizen install -n "TS IPTV.wgt" -t <tên TV trong "sdb devices"> -- dist/tizen
   tizen run -p TSIPTVtv01.TSIPTV -t <tên TV>
   ```
   Lỗi cài đặt **1010** gần như luôn là do certificate không có DUID của TV.

### 4.4 Nộp app
1. Seller Office > **Applications > Create**. Tên ở ngôn ngữ mặc định phải **trùng** `<name>` trong `config.xml`
   ("TS IPTV") **[chính thức]**.
2. Tải `.wgt`, điền mô tả, ảnh, quốc gia (Public Seller: chỉ Mỹ), age rating, privacy policy URL.
3. Ô **thông tin cho người kiểm tra**: ghi URL playlist demo và các bước thêm playlist.
4. Samsung kiểm tra: **Pre-test** (thông tin, gói, privilege) → **chứng nhận QA**; kết quả gửi qua email.
5. Danh sách bắt buộc https://developer.samsung.com/smarttv/develop/development-checklist/mandatory-features.html
   — app đã có sẵn:
   - [x] Back lùi một màn; ở màn chính hỏi thoát;
   - [x] báo khi mất mạng;
   - [x] tắt screensaver khi đang phát;
   - [x] dữ liệu chỉ nằm trong localStorage của app (gỡ app là xoá);
   - [ ] không treo/crash: cần tự kiểm tra trên TV thật với playlist lớn.

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
