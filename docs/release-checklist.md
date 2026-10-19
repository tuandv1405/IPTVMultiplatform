# Việc cần làm để phát hành đủ các nền tảng

Cập nhật 2026-10-19. File này gom mọi việc còn lại để có TS IPTV trên iOS, Android TV, LG và Samsung, và để trang
tải https://tsiptv-download.web.app hiện đủ các nền tảng. Làm xong việc nào thì đánh dấu `[x]` và ghi ngày. Cột
**Ai làm**: **Bạn** (cần tài khoản Apple, Google, LG, Samsung hoặc TV thật), **Claude** (code, CI, đóng gói, thử),
**chintk19** (chủ repo `chintk19/tsiptv-releases`).

Không ghi mật khẩu, file `.p12`, `.p8`, `.mobileprovision` hay chuỗi base64 vào file này hay vào repo.

## Đã xong

| Việc | Ngày |
|---|---|
| Desktop 1.1.6 (Windows + macOS) trên https://github.com/chintk19/tsiptv-releases/releases/tag/desktop-1.1.6, trang tải trỏ về 1.1.6 | 2026-10-19 |
| Mỗi lần merge vào `main`: tự tăng version, build Android và đẩy lên Firebase App Distribution (`docs/ci-firebase-distribution.md` › *Version*) | 2026-10-19 |
| Cast / gửi lên TV từ iPhone (PR #21), cài thử lên iPhone "Tun" | 2026-10-19 |
| App Android hỗ trợ Android TV trên `main` (`LEANBACK_LAUNCHER`, banner TV, không bắt buộc màn hình cảm ứng) | trước 2026-10-19 |
| `tv-web` 1.0.0: test 6/6 đạt, `npm run package:webos` ra `tss.t.tsiptv_1.0.0_all.ipk` | 2026-10-19 |

## Thứ tự nên làm

1. **Android TV** (mục 1): gần như xong, chỉ cần bật trong Play Console.
2. **iOS qua TestFlight** (mục 2): mất khoảng 1–2 ngày chờ Apple duyệt bản đầu.
3. **LG** (mục 3): cần TV LG thật để thử, mỗi vòng duyệt khoảng 3 tuần.
4. **Samsung** (mục 4): chỉ làm nếu chấp nhận phát hành ở Mỹ, hoặc có công ty để làm Partner Seller.
5. **Trang tải** (mục 5): cập nhật sau mỗi store được duyệt.
6. Tuỳ chọn: **cài ngoài store cho người rành kỹ thuật** (mục 7), có thể làm song song khi chờ store duyệt.

---

## 1. Android TV / Google TV (Google Play)

Cùng app Android đang có trên Google Play, chỉ cần mở thêm cho TV. Chi tiết: `docs/tv-stores-guide.md` mục 5.

| | Việc | Ai làm |
|---|---|---|
| [ ] | 1.1 Play Console › app TS IPTV › **Test and release › Advanced settings › Form factors** › **+ Add form factor** › **Android TV** | Bạn |
| [ ] | 1.2 Tải ảnh chụp màn hình TV (1920x1080) và banner TV (`brand/tv-banner-1280x720.png`) lên mục store listing của Android TV | Bạn |
| [ ] | 1.3 Gửi bản phát hành hiện tại cho Android TV, chờ Google duyệt TV (Google kiểm tra điều khiển bằng D-pad) | Bạn |
| [ ] | 1.4 Nếu Google trả về lỗi về TV: gửi nội dung email cho Claude để sửa | Bạn → Claude |
| [ ] | 1.5 Được duyệt: báo Claude để thêm link Google Play vào mục Smart TV của trang tải (mục 5) | Bạn → Claude |

---

## 2. iOS (TestFlight, link công khai)

GitHub không phát hành iOS cho người dùng được: file IPA chỉ cài trên máy có UDID nằm trong profile, tối đa 100
máy. TestFlight với **Public Link** cho tối đa 10.000 người cài, không cần UDID. Sau này có thể lên App Store từ
cùng bản build.

### 2a. Phần Bạn làm (tài khoản Apple Developer, team `57N5M3BU77`)

| | Việc | Ai làm |
|---|---|---|
| [ ] | 2.1 App Store Connect › **Business › Agreements**: chấp nhận thoả thuận Apple Developer Program mới nhất, nếu đang có thoả thuận chờ ký | Bạn |
| [ ] | 2.2 App Store Connect › **Apps › + › New App**: iOS, tên (ví dụ "TS IPTV", phải chưa ai dùng trên App Store), ngôn ngữ chính, Bundle ID `tss.t.tsiptv`, SKU ví dụ `tsiptv-ios` | Bạn |
| [ ] | 2.3 App Store Connect › **Users and Access › Integrations › App Store Connect API › Team Keys › Generate**, quyền **App Manager**. Tải file `AuthKey_<KeyID>.p8` (chỉ tải được một lần), ghi lại **Key ID** và **Issuer ID** | Bạn |
| [ ] | 2.4 Xcode › **Settings › Accounts** › team › **Manage Certificates…** › **+** › **Apple Distribution**. Keychain Access › **login › My Certificates** › chuột phải `Apple Distribution: Do Van Tuan…` › **Export…** ra `.p12`, đặt mật khẩu. Export từ **My Certificates** để file có private key | Bạn |
| [ ] | 2.5 developer.apple.com › **Profiles › +** › **App Store Connect** › App ID `tss.t.tsiptv` › chọn certificate ở 2.4 › tải file `.mobileprovision` | Bạn |
| [ ] | 2.6 Đặt GitHub secrets (lệnh bên dưới), rồi xoá file `.p12` và `.p8` khỏi máy hoặc cất vào nơi an toàn | Bạn |
| [ ] | 2.7 Tuỳ chọn, để bản iOS lên cả Firebase cho tester nội bộ: tạo thêm profile **Ad Hoc** (đã đăng ký UDID các máy thử, gồm iPhone "Tun" `00008110-000878203AC1401E`) và đặt secret `IOS_PROVISIONING_PROFILE_BASE64` (`docs/ci-ios-firebase-distribution.md`) | Bạn |

Lệnh cho 2.6, chạy trong thư mục repo:

```bash
base64 -i ~/Desktop/apple-distribution.p12 | gh secret set IOS_CERT_P12_BASE64
gh secret set IOS_CERT_PASSWORD                     # nhập mật khẩu file .p12
base64 -i ~/Downloads/TSIPTV_App_Store.mobileprovision | gh secret set IOS_APPSTORE_PROFILE_BASE64
gh secret set APP_STORE_CONNECT_API_KEY < ~/Downloads/AuthKey_XXXXXXXXXX.p8
gh secret set APP_STORE_CONNECT_KEY_ID              # Key ID
gh secret set APP_STORE_CONNECT_ISSUER_ID           # Issuer ID
```

Kiểm tra bằng `gh secret list`: phải thấy đủ 6 tên trên (thêm `IOS_PROVISIONING_PROFILE_BASE64` nếu làm 2.7).

### 2b. Phần Claude làm (sau khi xong 2.1–2.6)

| | Việc | Ai làm |
|---|---|---|
| [ ] | 2.8 `iosApp/ci/build-ipa.sh` nhận profile App Store (method `app-store-connect`) và upload lên App Store Connect bằng API key | Claude |
| [ ] | 2.9 Version hiển thị của app iOS theo version của app (`1.1.x`), thay cho `MARKETING_VERSION=1.0` cố định trong `iosApp/Configuration/Config.xcconfig`. Build number đã tự tăng sau mỗi merge | Claude |
| [ ] | 2.10 Thêm job TestFlight vào `.github/workflows/version-bump.yml`: sau mỗi merge vào `main`, build bản iOS và upload lên TestFlight | Claude |
| [ ] | 2.11 Chạy build đầu tiên ngay, không chờ lần merge sau; báo khi bản build hiện trong TestFlight | Claude |

### 2c. Sau khi bản đầu tiên vào TestFlight (Bạn, khoảng 10 phút)

| | Việc | Ai làm |
|---|---|---|
| [ ] | 2.12 TestFlight › **Test Information**: mô tả, email nhận góp ý, **link chính sách quyền riêng tư** (https://tsiptv-8bdd6.web.app/privacy/?lang=en, bắt buộc với tester bên ngoài) | Bạn |
| [ ] | 2.13 **Beta App Review Information**: app bắt buộc đăng nhập, nên nhập tài khoản demo (ví dụ tài khoản test `tester@tsiptv.com`) và ghi chú dùng playlist hợp pháp https://tsiptv-download.web.app/tv/review.m3u | Bạn |
| [ ] | 2.14 Tạo nhóm **External**, thêm bản build, **Submit for Review**. Lần đầu khoảng 1–2 ngày | Bạn |
| [ ] | 2.15 Được duyệt: bật **Public Link** của nhóm, gửi link cho Claude | Bạn → Claude |
| [ ] | 2.16 Thử cast từ iPhone lên Android TV cùng Wi-Fi: cho phép "Mạng cục bộ", TV hiện trong danh sách, ghép đôi bằng mã 6 số, phát một kênh, gửi một playlist | Bạn |

Lưu ý: Apple duyệt app IPTV khá kỹ. App và ghi chú cho người duyệt chỉ được nhắc tới playlist hợp pháp, không
nhắc tới thể thao, phim hay "kênh miễn phí".

---

## 3. LG webOS (LG Content Store)

Gói: `tv-web/dist/tss.t.tsiptv_<version>_all.ipk`. Chi tiết: `docs/tv-stores-guide.md` mục 3, `tv-web/README.md`.

| | Việc | Ai làm |
|---|---|---|
| [ ] | 3.1 Tạo **LG Developer account** ở webostv.developer.lge.com | Bạn |
| [ ] | 3.2 Seller Lounge (seller.lgappstv.com): đăng ký **Individual Seller** | Bạn |
| [ ] | 3.3 Seller Lounge › **1:1 Q&A**: hỏi người bán cá nhân ở Việt Nam có phát hành được không, và ở những nước nào. Chờ trả lời rồi mới làm 3.7 | Bạn |
| [ ] | 3.4 Trên TV LG: mở LG Content Store, cài app **Developer Mode**, đăng nhập LG Developer account, bật **Dev Mode Status**, khởi động lại TV. Gửi Claude **IP của TV**; khi Claude hỏi, bấm **Key Server** và đọc passphrase 6 ký tự | Bạn |
| [ ] | 3.5 Cài bản `.ipk` lên TV và chạy thử: playlist demo, phát HLS, stream `.ts`, phím Back/CH/màu, bàn phím ảo khi nhập link. Sửa lỗi nếu có | Claude (cùng Bạn bấm remote) |
| [ ] | 3.6 Điền 2 file mẫu tải từ Seller Lounge: **UX scenario** (PowerPoint) và **Self-checklist** (Excel), theo kết quả thử thật ở 3.5. Claude soạn nội dung từ `tv-web/store/README.md`, Bạn chép vào file mẫu | Claude + Bạn |
| [ ] | 3.7 Seller Lounge › **Apps › Register App**, App ID `tss.t.tsiptv`: tải gói `.ipk`, icon, ảnh nền, ảnh chụp (`tv-web/store/lg/`), mô tả EN + VI, privacy policy URL, 2 file ở 3.6. Gửi QA | Bạn |
| [ ] | 3.8 LG trả kết quả (Pretest → Function test → Content test, khoảng 3 tuần mỗi vòng). Bị trả về: gửi danh sách lỗi cho Claude, Claude sửa và tăng `version` trong `tv-web/package.json`, Bạn nộp lại | Bạn ↔ Claude |
| [ ] | 3.9 Được duyệt: chọn ngày phát hành, gửi Claude link app trên LG Content Store (mục 5) | Bạn → Claude |

---

## 4. Samsung Tizen (Samsung Apps TV)

Gói: `tv-web/dist/TSIPTV.wgt`, ký bằng chứng chỉ Samsung. Chi tiết: `docs/tv-stores-guide.md` mục 4.

Giới hạn: tài khoản cá nhân (**Public Seller**) chỉ phát hành được ở **Mỹ**. Phát hành ở Việt Nam cần **Partner
Seller** (hợp đồng với Samsung, thường cần công ty).

| | Việc | Ai làm |
|---|---|---|
| [ ] | 4.1 Quyết định: phát hành ở Mỹ (Public Seller) hay hỏi Samsung Vietnam về partnership. Ghi quyết định vào đây: … | Bạn |
| [ ] | 4.2 Đăng ký TV Seller Office (seller.samsungapps.com/tv) bằng Samsung Account | Bạn |
| [ ] | 4.3 Trên máy Windows có Tizen Studio (`C:\Users\Admin\tv-tools\tizen-studio`): Certificate Manager › **+** › **Samsung** › **TV**, profile tên `TSIPTV`, đăng nhập Samsung Account. Tạo author certificate và distributor certificate (privilege Public, thêm DUID của TV thử và của emulator) | Bạn |
| [ ] | 4.4 **Sao lưu author certificate (`.p12`) và mật khẩu** ở nơi an toàn. Mất là không cập nhật được app nữa | Bạn |
| [ ] | 4.5 Trên TV Samsung: Apps › bấm `1 2 3 4 5` › bật **Developer mode**, nhập IP máy tính, khởi động lại TV. Gửi Claude IP của TV, hoặc dùng emulator `tsiptv-tv` | Bạn |
| [ ] | 4.6 `npm run package:tizen`, cài lên TV hoặc emulator, thử AVPlay phát cả 4 kênh demo, phím Back/CH/đỏ, mất mạng. Sửa lỗi nếu có | Claude (trên máy Windows có chứng chỉ) |
| [ ] | 4.7 Seller Office › **Applications › Create**: tên trùng `<name>` trong `config.xml` ("TS IPTV"), gói `.wgt`, ảnh trong `tv-web/store/samsung/`, mô tả, privacy policy URL, ghi chú cho người duyệt. Gửi chứng nhận | Bạn |
| [ ] | 4.8 Bị trả về: gửi email kết quả cho Claude để sửa, nộp lại. Được duyệt: gửi Claude link app (mục 5) | Bạn ↔ Claude |

---

## 5. Trang tải (https://tsiptv-download.web.app)

Trang lấy dữ liệu từ `public/releases.json` trong `chintk19/tsiptv-releases`. Hiện iOS và Smart TV đang ở "Sắp có"
(`"status": "soon"`). Bạn chỉ có quyền đọc repo đó, nên cần chọn một trong hai cách:

| | Việc | Ai làm |
|---|---|---|
| [ ] | 5.1 Chọn cách cập nhật: (a) chintk19 sửa tay `public/releases.json` rồi chạy `firebase deploy --only hosting` trong repo đó, hoặc (b) Claude thêm một workflow chạy tay trong repo app, dùng secret `RELEASES_REPO_TOKEN` sẵn có, để sửa mục iOS / Smart TV và deploy lại trang. Khuyên dùng (b) | Bạn |
| [ ] | 5.2 iOS: đổi `ios` sang `"status": "live"` với link TestFlight (sau 2.15) | Claude hoặc chintk19 |
| [ ] | 5.3 Smart TV: đổi `tv` sang `"status": "live"` với link từng store đã được duyệt: Google Play cho Android TV (1.5), LG (3.9), Samsung (4.8) | Claude hoặc chintk19 |

---

## 6. Việc khác còn treo

| | Việc | Ai làm |
|---|---|---|
| [ ] | 6.1 Merge PR có file này vào `main`. PR đó cũng đưa ghi chú `docs/releases/desktop-1.1.6.md` vào `main` (hiện ghi chú chỉ nằm trên nhánh `release/desktop-1.1.6`). Lần merge sẽ tự tăng version lên 1.1.7 | Bạn |
| [ ] | 6.2 Tuỳ chọn: thêm quyền cho Claude chạy `gh pr merge`, để Claude tự merge khi CI đạt (hiện bị chặn ở chế độ auto) | Bạn |

---

## 7. Tuỳ chọn: cài ngoài store (LG, Samsung)

Không có cách chính thức nào để người dùng mở một trang web rồi bấm tải và cài app trên TV LG hay Samsung: TV chỉ
cài app từ store của hãng. Các cách dưới đây đều cần máy tính, tài khoản developer hoặc công cụ của cộng đồng, nên
chỉ dành cho người rành kỹ thuật. Người dùng thường vẫn đi qua store (mục 3, 4) và Google Play cho Android TV
(mục 1).

**Không dùng được: mở bản web trong trình duyệt của TV.** `tv-web` mở được bằng trình duyệt có sẵn, nhưng gần như
không phát được IPTV:
- phần lớn máy chủ playlist và stream không gửi header CORS, nên trình duyệt chặn (bản đóng gói thì không bị chặn);
- trang HTTPS chặn stream `http://`, mà phần lớn IPTV là `http://`;
- trình duyệt Samsung không có AVPlay;
- nút Back thường thoát luôn trình duyệt.

### 7a. LG: file `.ipk` cho Developer Mode hoặc Homebrew Channel

- **Developer Mode**: người dùng tạo LG Developer account, cài app Developer Mode, rồi cài `.ipk` từ máy tính
  (`ares-install`, hoặc công cụ của cộng đồng như webOS Dev Manager). Phiên tối đa 1000 giờ, phải bấm Extend; hết
  hạn thì app bị xoá.
- **Homebrew Channel** (TV đã root): cài được `.ipk` ngay trên TV, gần nhất với "cài từ web". Chỉ làm được trên TV
  có firmware còn lỗ hổng (faultmanager-autoroot nhắm tới webOS 4–10; phần lớn firmware mới và TV 2024 đã được vá).
  Root TV có rủi ro bảo mật: chỉ nhắc tới, không hướng dẫn người dùng thường làm.

| | Việc | Ai làm |
|---|---|---|
| [ ] | 7.1 Quyết định có phát hành `.ipk` công khai không. Lưu ý: bản cài ngoài store và bản trên LG Content Store cùng App ID `tss.t.tsiptv` | Bạn |
| [ ] | 7.2 CI đóng gói `.ipk` (`npm run package:webos`) và đính kèm vào release trên `chintk19/tsiptv-releases`, kèm hướng dẫn cài bằng Developer Mode | Claude |
| [ ] | 7.3 Thử cài `.ipk` từ release lên TV LG thật (cần 3.4) | Claude + Bạn |

### 7b. Samsung: module TizenBrew

TizenBrew là app cộng đồng: cài một lần bằng công cụ trên máy tính (TizenBrew Updater) hoặc gói qua USB, sau đó
người dùng thêm "module" bằng tên package npm ngay trên TV. Hạn chế:
- TV Tizen 8 trở lên (2024+) khó cài TizenBrew, có thể phải tự build lại từ mã nguồn;
- cách cài qua trình duyệt chỉ chạy trên Tizen 3–6 và chỉ giữ được 30 ngày;
- chưa kiểm chứng việc phát IPTV bên trong một module (AVPlay, CORS): phải thử trên TV thật trước khi công bố.

| | Việc | Ai làm |
|---|---|---|
| [ ] | 7.4 Quyết định có làm module TizenBrew không | Bạn |
| [ ] | 7.5 Đóng gói `tv-web` thành module TizenBrew, thử trên TV Samsung thật (cần 4.5): thêm playlist, phát HLS và MPEG-TS, phím Back/CH | Claude + Bạn |
| [ ] | 7.6 Đạt thì đăng module lên npm (cần tài khoản npm của Bạn) và viết hướng dẫn cài cho người dùng | Bạn + Claude |

Nguồn: [TizenBrew](https://github.com/reisxd/TizenBrew/blob/main/docs/README.md),
[LG Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app),
[webOS Homebrew: Developer Mode](https://www.webosbrew.org/devmode/),
[faultmanager-autoroot](https://github.com/throwaway96/faultmanager-autoroot).
