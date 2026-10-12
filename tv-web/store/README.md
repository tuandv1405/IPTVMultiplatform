# Hồ sơ nộp store (LG Content Store, Samsung Apps TV)

Mọi thứ cần tải lên hoặc dán vào trang nộp app. Các bước nộp: [docs/tv-stores-guide.md](../../docs/tv-stores-guide.md).

## Tạo lại

```bash
cd tv-web
node scripts/store-screenshots.js           # store/screenshots/{en,vi}/*.png (Edge/Chrome headless)
python scripts/make_store_assets.py         # icon + JPG đúng cỡ cho từng store (chạy được từ mọi thư mục)
```

Ảnh chụp dùng playlist demo https://tsiptv-download.web.app/tv/review.m3u (chỉ stream thử hợp pháp; file nằm ở
`tsiptv-releases/public/tv/review.m3u`). Đổi giao diện thì
chạy lại cả hai lệnh.

## Tệp

| Dùng cho | Tệp | Yêu cầu của store |
|---|---|---|
| LG: icon store | `lg/icon-400x400.png` | 400x400, không trong suốt, nền không dải màu |
| LG: ảnh nền / splash | `lg/background-1920x1080.png` | 1920x1080 |
| LG: ảnh chụp | `lg/screenshots/{en,vi}/*.jpg` | 1280x720; 1 ảnh chính (`02-player`) + 2–5 ảnh phụ |
| Samsung: icon 512x423 | `samsung/icon-512x423.png` | PNG 24-bit, < 300 KB |
| Samsung: icon 1920x1080 | `samsung/icon-1920x1080-logo.png` + `samsung/icon-1920x1080-background.png` | logo PNG 32-bit, nền PNG 24-bit, mỗi file < 300 KB |
| Samsung: ảnh chụp | `samsung/screenshots/{en,vi}/*.jpg` | 1920x1080 JPG < 500 KB, tối thiểu 4 ảnh |
| Gói LG | `../dist/tss.t.tsiptv_<version>_all.ipk` | `npm run package:webos` |
| Gói Samsung | `../dist/TSIPTV.wgt` | `npm run package:tizen` (profile chứng chỉ Samsung) |

Các yêu cầu kích thước lấy từ tài liệu Samsung và hướng dẫn của đối tác LG; kiểm tra lại trên trang nộp, store
có thể đổi.

## Thông tin app

| Trường | Giá trị |
|---|---|
| Tên | `TS IPTV` (Samsung: phải trùng `<name>` trong `config.xml`) |
| App ID | LG `tss.t.tsiptv` · Samsung `TSIPTVtv01.TSIPTV` (không đổi được sau khi phát hành) |
| Danh mục | Entertainment / Video |
| Giá | Miễn phí, không mua trong app, không quảng cáo |
| Privacy policy | https://tsiptv-8bdd6.web.app/privacy/?lang=en (mục "Smart TV app") |
| Hỗ trợ | https://tsiptv-8bdd6.web.app/support/ |
| Playlist cho người duyệt | https://tsiptv-download.web.app/tv/review.m3u |
| Độ tuổi | App không có nội dung dựng sẵn. Trả lời bảng câu hỏi của store đúng như vậy. Chọn 18+ sẽ không phát hành được ở một số nước (Samsung: Hàn Quốc, Ả Rập Saudi, UAE). |

### Mô tả (English)

> TS IPTV plays your own IPTV playlists on your TV.
>
> • Add M3U / M3U8 playlists by link, keep several and switch between them
> • Channels grouped by category, plus Favorites and Recently watched
> • Smooth channel list, even with thousands of channels
> • Full-screen player: change channel with ▲▼ or CH+/CH−, add favorites with the red button
> • Plays HLS streams, and MPEG-TS or MP4 links where the TV supports them
> • English and Vietnamese
>
> TS IPTV is a player only. It does not include, sell or recommend any channels, playlists or
> subscriptions. You need a playlist from a provider you are entitled to watch.

### Mô tả (Tiếng Việt)

> TS IPTV phát danh sách IPTV của chính bạn trên TV.
>
> • Thêm danh sách M3U / M3U8 bằng đường dẫn, lưu nhiều danh sách và chuyển qua lại
> • Kênh chia theo nhóm, có Yêu thích và Xem gần đây
> • Danh sách kênh mượt, kể cả khi có hàng nghìn kênh
> • Xem toàn màn hình: đổi kênh bằng ▲▼ hoặc CH+/CH−, thêm yêu thích bằng nút đỏ
> • Phát luồng HLS, và đường dẫn MPEG-TS hoặc MP4 nếu TV hỗ trợ
> • Tiếng Việt và tiếng Anh
>
> TS IPTV chỉ là trình phát. Ứng dụng không kèm, không bán và không giới thiệu kênh, danh sách phát hay
> gói thuê bao nào. Bạn cần danh sách phát từ nhà cung cấp mà bạn có quyền xem.

Từ khoá: `IPTV, M3U, playlist, player, live TV, HLS`. Không dùng: tên nhà cung cấp IPTV, tên kênh, "free",
"sports", "movies".

### Ghi chú cho người duyệt (dán vào ô test info / UX scenario, tiếng Anh)

> The app has no built-in content. A playlist of legal test streams is provided for review.
>
> 1. First start: a notice explains the app is a player only. Press OK on "I understand".
> 2. "Add playlist" is focused. Press OK.
> 3. Name: `Demo`. M3U link: `https://tsiptv-download.web.app/tv/review.m3u`
>    (select a field and press OK to open the on-screen keyboard).
> 4. Select "Save" and press OK. The channel list opens with 4 channels: Big Buck Bunny and Tears of
>    Steel (Blender Foundation, CC BY), Apple HLS sample, Unified Streaming live test.
> 5. Select a channel and press OK to play.
>    - ▲▼ or CH+/CH−: previous/next channel
>    - OK: on-screen controls (★ favorite, ❚❚ pause, ☰ back to list); ◀▶ moves between them
>    - ◀▶ without controls: seek 10 s (video on demand only)
>    - Red button: add/remove favorite
>    - Back: hides the controls, then returns to the channel list
> 6. Channel list: ◀ moves to the groups (Favorites, Recently watched, All, playlist groups).
>    "Playlists" (top) lets you switch, reload or delete playlists.
> 7. Back on the channel list asks "Exit TS IPTV?".
>
> Data (playlists, favorites, settings) stays in the app's storage on the TV. The app has no account,
> analytics or ads.

### LG UX scenario (PowerPoint mẫu từ Seller Lounge)

Mỗi trang một màn hình, dùng ảnh trong `lg/screenshots/en/`:

1. Thông báo lần đầu → OK.
2. `04-add-playlist`: nhập tên và đường dẫn playlist demo, Save.
3. `01-channels`: nhóm bên trái, lưới kênh bên phải; ▲▼◀▶ di chuyển, OK xem.
4. `02-player`: phát toàn màn hình; phím như mục 5 ở trên.
5. `03-favorites`: nút đỏ hoặc ★ thêm yêu thích; nhóm Yêu thích.
6. `05-playlists`: chọn, tải lại, xoá danh sách.
7. Back ở màn hình chính → hỏi thoát.
