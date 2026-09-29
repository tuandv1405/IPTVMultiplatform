# CI: pull request → AAB → Firebase App Distribution

Workflow: `.github/workflows/pr-firebase-distribution.yml`.

Mỗi khi một pull request được mở, có commit mới hoặc chuyển từ draft sang ready, workflow sẽ:

1. Build `bundleRelease` (có R8) và ký bằng upload key.
2. Đẩy file AAB lên Firebase App Distribution, kèm release notes gồm số PR, tiêu đề, branch và commit.
3. Lưu AAB làm artifact của workflow trong 14 ngày.

PR mở từ fork hoặc đang ở trạng thái draft sẽ không chạy, vì GitHub không cấp secret cho fork. Có thể chạy tay
bằng **Actions › PR build → Firebase App Distribution › Run workflow**.

## Cài đặt một lần

### 1. GitHub secrets

Vào GitHub › repo › **Settings › Secrets and variables › Actions › New repository secret**:

| Secret | Giá trị |
|---|---|
| `TSIPTV_KEYSTORE_BASE64` | File upload keystore `.jks`, mã hoá base64 (lệnh ở dưới) |
| `TSIPTV_STORE_PASSWORD` | `storePassword` trong `composeApp/keystore.properties` |
| `TSIPTV_KEY_ALIAS` | `keyAlias` |
| `TSIPTV_KEY_PASSWORD` | `keyPassword` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Toàn bộ nội dung file JSON của service account (bước 2) |

Mã hoá keystore sang base64 trên Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\duong-dan\ts_iptv_android_key.jks")) | Set-Clipboard
```

Dán nội dung clipboard vào secret. Không commit file `.jks` hay chuỗi base64 này.

### 2. Service account cho App Distribution

1. Mở Google Cloud Console › project **tsiptv-8bdd6** › **IAM & Admin › Service Accounts** › **Create service account**,
   đặt tên ví dụ `github-app-distribution`.
2. Cấp role **Firebase App Distribution Admin**.
3. Vào tab **Keys › Add key › JSON** để tải file JSON, dán toàn bộ nội dung vào secret `FIREBASE_SERVICE_ACCOUNT_JSON`,
   rồi xoá file JSON khỏi máy.

### 3. Firebase App Distribution

1. Firebase Console › **App Distribution** › chọn app Android `tss.t.tsiptv`, bấm **Get started**.
2. Tab **Testers & Groups**: tạo group có alias `testers` và thêm email tester.
3. **Bắt buộc khi phát hành AAB:** App Distribution chỉ nhận AAB khi project Firebase đã được liên kết với Google Play.
   Vào Firebase Console › Project settings › **Integrations › Google Play** › Link, và app phải có trên Play Console
   (một bản ở bất kỳ track nào, kể cả internal). Nếu chưa liên kết, bước upload sẽ báo lỗi. Tạm thời có thể đổi
   `bundleRelease` thành `assembleRelease` và đường dẫn `bundle/release/*.aab` thành `apk/release/*.apk`
   trong workflow.

### 4. Tuỳ chọn: GitHub variables

Vào **Settings › Secrets and variables › Actions › Variables**:

| Variable | Mặc định |
|---|---|
| `FIREBASE_ANDROID_APP_ID` | `1:234600934735:android:c8d65b7ef4741dacd4a93a` (lấy từ `composeApp/google-services.json`) |
| `FIREBASE_TESTER_GROUPS` | `testers` (nhiều group thì phân tách bằng dấu phẩy) |

## Lưu ý

- `versionCode` lấy từ `composeApp/build.gradle.kts`. Các bản build từ PR có cùng version, và App Distribution
  phân biệt chúng bằng thời điểm upload và release notes.
- Để CI không bị hết RAM, Kotlin compiler chạy bên trong Gradle daemon (`kotlin.compiler.execution.strategy=in-process`)
  và heap của Gradle được giới hạn ở 4 GB.
- Rule R8 nằm trong `composeApp/proguard-rules.pro`. Nếu R8 báo "Missing classes", hãy chép các dòng trong
  `composeApp/build/outputs/mapping/release/missing_rules.txt` vào file đó, rồi build release và chạy thử trên máy trước khi merge.
