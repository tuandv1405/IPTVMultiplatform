# Xem event "người dùng thêm nguồn" trên Firebase

Hướng dẫn ngắn cho project `tsiptv-8bdd6`. App gửi các event này qua Firebase Analytics (GA4).

## 1. Event và tham số app đang gửi

| Event | Khi nào | Tham số |
|---|---|---|
| `add_iptv_playlist` | Thêm playlist hoặc TS IPTV Source (bằng link hoặc file) thành công, không tính lần làm mới | `iptv_format` (vd. `M3U`, `XSPF`, `JSON`, `TSIPTV_SOURCE`), `channel_count`, `source_kind` (`link`/`file`), `has_epg` (`true`/`false`), `include_count` (chỉ TS IPTV Source); nếu thêm bằng link: `url_scheme`, `link_host`, `link` |
| `add_addon` | Thêm một addon Stremio | `link_host`, `url_scheme` |
| `play_iptv_channel` | Bắt đầu phát kênh | `channel_play_hour` |
| `play_addon_stream` | Bắt đầu phát luồng từ addon | `content_type` (`movie`, `series`, `tv`, `channel`, `other`) |

Nguồn: `composeApp/src/commonMain/kotlin/tss/t/tsiptv/core/firebase/analystics/AnalyticsConstants.kt`.

**Tham số `link` là đường dẫn đã được làm sạch, không phải link gốc.** Hàm `AnalyticsLinks.sanitized`
chỉ giữ scheme, host, port và path. Nó bỏ `user:pass@`, toàn bộ query (ví dụ
`?username=&password=` của Xtream), fragment và hậu tố `|User-Agent=…`/`|Authorization=…`.
Các đoạn path trông giống token được thay bằng `*`, và giá trị bị cắt ở 100 ký tự (giới hạn của GA4).

Ví dụ: `http://john:pw@iptv.example.com:8080/get.php?username=john&password=pw` → `http://iptv.example.com:8080/get.php`.

Addon chỉ gửi host, vì path của addon chứa cấu hình riêng của người dùng (thường là khoá dịch vụ).
Tên playlist và link của từng kênh không bao giờ được gửi.

## 2. Đăng ký tham số để xem được trong báo cáo

GA4 chỉ hiện giá trị tham số sau khi tham số đó được đăng ký làm *custom definition*.

1. Mở Firebase Console › project **tsiptv-8bdd6** › **Analytics** › **Custom definitions**
   (hoặc Google Analytics › Admin › Data display › Custom definitions).
2. Bấm **Create custom dimension** cho mỗi tham số dạng chữ:
   - Dimension name: `IPTV format`, Scope: **Event**, Event parameter: `iptv_format`
   - Dimension name: `Addon content type`, Scope: **Event**, Event parameter: `content_type`
   - `Source kind` → `source_kind`
   - `URL scheme` → `url_scheme`
   - `Has EPG` → `has_epg`
   - `Link host` → `link_host`
   - `Link` → `link`
3. Bấm **Create custom metric** cho tham số dạng số:
   - Metric name: `Channel count`, Scope: **Event**, Event parameter: `channel_count`, Unit: Standard
   - Metric name: `Include count`, Scope: **Event**, Event parameter: `include_count`, Unit: Standard
4. Dữ liệu chỉ có **từ lúc đăng ký trở đi** (không có dữ liệu cũ) và mất tới 24–48 giờ mới hiện trong báo cáo.

## 3. Xem ngay khi test (DebugView)

```sh
adb shell setprop debug.firebase.analytics.app tss.t.tsiptv
```

Mở app, thêm một playlist, rồi vào Firebase Console › Analytics › **DebugView**. Event
`add_iptv_playlist` và các tham số sẽ hiện sau vài giây. Tắt chế độ debug bằng lệnh:

```sh
adb shell setprop debug.firebase.analytics.app .none.
```

## 4. Xem số liệu

- **Analytics › Events:** chọn `add_iptv_playlist` để xem số lần thêm và số người dùng theo ngày.
- **Google Analytics › Explore › Free form:**
  - Dimensions: `Event name`, `IPTV format`
  - Metrics: `Event count`, `Channel count`
  - Filter: `Event name` = `add_iptv_playlist`

  Kết quả là số lượt thêm nguồn theo từng định dạng. Muốn xem các đường dẫn người dùng đã thêm thì
  đổi Dimensions thành `Link host` và `Link`, rồi thêm filter `Source kind` = `link`.
  Với addon, dùng `Event name` = `add_addon` và dimension `Link host`.
- **Dữ liệu thô:** bật Firebase › Project settings › Integrations › **BigQuery**. Sau đó có thể truy vấn:

  ```sql
  SELECT event_date,
         (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'iptv_format') AS format,
         (SELECT value.int_value    FROM UNNEST(event_params) WHERE key = 'channel_count') AS channels
  FROM `tsiptv-8bdd6.analytics_*.events_*`
  WHERE event_name = 'add_iptv_playlist'
  ```

## 5. Thêm hoặc sửa tham số

1. Khai báo hằng trong `core/firebase/analystics/AnalyticsConstants.kt`. Các tham số của `add_iptv_playlist` được gom trong `addSourceParams(...)`.
2. Mọi đường dẫn phải đi qua `AnalyticsLinks` (`sanitized`, `host`, `scheme`). Không truyền link gốc vào `logEvent`.
3. Thêm test vào `commonTest/.../core/firebase/AnalyticsLinksTest.kt`.
4. Đăng ký tham số mới làm custom dimension hoặc metric như ở mục 2.
5. Cập nhật `play-store/data-safety.md` và mục Data safety trên Play Console. Host hoặc link đã làm sạch vẫn có thể trỏ tới máy chủ riêng của người dùng, nên phải khai báo là "Other user-generated content" dùng cho Analytics, và ghi vào chính sách quyền riêng tư.

