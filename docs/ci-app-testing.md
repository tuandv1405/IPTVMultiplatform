# Test case cho Firebase App Distribution (App Testing agent, Android)

Test case nằm trong `qa/app-testing/`. Chúng được viết bằng tiếng Anh tự nhiên (mục tiêu và màn hình mong đợi).
**App Testing agent** của Firebase (Gemini) đọc test case, tự bấm trên một máy Android ảo và chấm đạt/không đạt.
Không cần code test hay `testTag`. Tính năng này đang ở giai đoạn preview, chỉ có cho Android.

| File | Test case | Khi nào chạy |
|---|---|---|
| `smoke.yaml` | `sign-in`, `add-playlist-url`, `play-channel` | Mỗi bản build của pull request |
| `regression.yaml` | Tìm kênh, lọc nhóm, link hỏng, Like/Dislike, Share, Clip, đổi kênh trong player, History, đổi ngôn ngữ, đăng xuất | Khi `FIREBASE_APP_TESTING=all`, hoặc chạy tay trên Console |

Playlist dùng trong test: `https://tsiptv-download.web.app/tv/review.m3u` (4 luồng thử hợp pháp, cũng là
playlist cho người duyệt store, xem `docs/tv-stores-guide.md`). Đổi file đó thì phải sửa test case theo.

## CI làm gì

Trong `.github/workflows/pr-firebase-distribution.yml`, sau bước upload lên App Distribution:

1. `firebase appdistribution:testcases:import` đẩy cả hai file lên **Firebase Console › App Distribution ›
   Test cases**. Test case có `id` nên import lại sẽ cập nhật, không tạo bản trùng.
2. `firebase apptesting:execute` chạy các test case trong `smoke.yaml` trên đúng bản AAB/APK vừa upload. Mặc
   định CI không chờ kết quả: xem kết quả ở Console › App Distribution › bản phát hành › tab **Tests**.

Bước này mặc định không làm hỏng build (`continue-on-error`): test lỗi hoặc hết quota chỉ hiện cảnh báo.

## Cài đặt một lần

1. **Tài khoản test riêng.** Bản release bắt buộc đăng nhập, nên agent cần một tài khoản. Tạo một tài khoản chỉ
   dùng để test (trong app: **Sign Up**), không dùng tài khoản thật. Mỗi lần chạy là một máy mới. Khi tài
   khoản đạt giới hạn 4 thiết bị, test case `sign-in` tự đăng xuất từ xa một thiết bị cũ.
2. Repo app › **Settings › Secrets and variables › Actions**:
   - Variable `FIREBASE_TEST_USERNAME`: email của tài khoản test.
   - Secret `FIREBASE_TEST_PASSWORD`: mật khẩu. CI ghi mật khẩu ra file tạm, không đưa vào dòng lệnh.

   Chưa đặt hai giá trị này thì CI vẫn import test case nhưng không chạy.
3. Service account của CI (`docs/ci-firebase-distribution.md`) đã có role **Firebase App Distribution Admin**.
   Nếu bước test báo thiếu quyền (`PERMISSION_DENIED`), cấp thêm **Firebase Test Lab Admin**
   (`roles/cloudtestservice.testAdmin`) cho service account đó.

## Tuỳ chọn: GitHub variables

| Variable | Mặc định | Ý nghĩa |
|---|---|---|
| `FIREBASE_APP_TESTING` | `smoke` | `smoke`: chạy `smoke.yaml`; `all`: chạy cả hai file; `off`: không import, không chạy |
| `FIREBASE_APP_TESTING_BLOCKING` | (trống) | `true`: chờ kết quả, test lỗi thì job lỗi (thêm tối đa khoảng 5 phút) |
| `FIREBASE_TEST_DEVICES` | Mặc định của CLI: `MediumPhone.arm`, Android 16, `en_US`, dọc | Ví dụ `model=MediumPhone.arm,version=34,locale=en_US,orientation=portrait`; nhiều máy cách nhau bằng `;`. Danh sách máy: `gcloud firebase test android models list` |

## Quota

Trong giai đoạn preview, mỗi project Firebase có **200 lượt test mỗi tháng**. Mỗi test case trên mỗi máy tính
là một lượt; prerequisite chạy gộp vào test case dùng nó nên không tính thêm. Vì vậy:

- `smoke`: 3 lượt cho mỗi lần push vào PR, tức khoảng 65 lần push mỗi tháng.
- `all`: 13 lượt cho mỗi lần push. Chỉ nên bật tạm, hoặc chạy tay.
- Thêm một máy trong `FIREBASE_TEST_DEVICES` thì nhân đôi số lượt.

## Chạy tay

- Trên Console: App Distribution › chọn bản phát hành › **Tests › Run tests**, chọn test case và máy.
- Từ máy dev (cần `firebase login` bằng tài khoản có quyền trên project):

  ```bash
  npx firebase-tools@15 apptesting:execute path/to/app-release.apk \
    --app 1:234600934735:android:c8d65b7ef4741dacd4a93a --test-dir qa/app-testing \
    --test-username <email-test> --test-password-file <file-mat-khau> --test-name-pattern "Like"
  ```

  Bỏ đường dẫn file thì CLI dùng bản mới nhất trên App Distribution.

## Viết test case

```yaml
tests:
  - displayName: Tên hiển thị
    id: ten-khong-dau            # duy nhất, dùng cho prerequisiteTestCaseId
    prerequisiteTestCaseId: sign-in   # tuỳ chọn: chạy các bước của test case này trước
    steps:
      - goal: Việc cần làm, một hành động rõ ràng.
        hint: Chỗ bấm, chữ trên nút, cách xử lý hộp thoại có thể hiện ra.   # tuỳ chọn
        finalScreenAssertion: Màn hình lúc thành công, nêu đúng chữ hiện trên đó.   # tuỳ chọn
```

- Chỉ dùng các khoá ở trên. Khoá lạ làm `testcases:import` báo lỗi `unexpected property`.
- Ghi đúng chữ tiếng Anh trên giao diện (`composeResources/values/strings.xml`). Nhiều icon không có
  `contentDescription` (ví dụ tab ở thanh dưới), nên hãy tả vị trí và chữ bên cạnh.
- Mỗi test case phải xong trong 5 phút. Agent cuộn chưa ổn định và đếm thao tác lặp không chính xác.
- Video trong player có thể hiện màu đen trong ảnh chụp (SurfaceView). Đừng kiểm tra nội dung khung hình.
- App không có màn hình giới thiệu (onboarding) và không có mục Yêu thích, nên không viết test cho các phần đó.
- Quảng cáo không hiện trong 24 giờ đầu sau khi cài, nên không ảnh hưởng tới test.
- Thử một test case mới bằng lệnh chạy tay ở trên trước khi đưa vào `smoke.yaml`.
