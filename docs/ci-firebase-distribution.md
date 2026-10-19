# CI: merge vào main → AAB → Firebase App Distribution

Workflow: `.github/workflows/pr-firebase-distribution.yml`. Bản iOS: `docs/ci-ios-firebase-distribution.md`.

CI chỉ build khi có code vào `main` (merge PR hoặc push); pull request không được build. Mỗi lần như vậy,
`.github/workflows/version-bump.yml` tăng version và commit lên `main` (mục *Version*), rồi gọi workflow này để
build đúng commit đó:

1. Build `bundleRelease` (có R8) và ký bằng upload key.
2. Đẩy file AAB lên Firebase App Distribution, kèm release notes gồm version, commit merge, tiêu đề PR và mã commit.
3. Lưu AAB làm artifact của workflow trong 14 ngày.
4. Import test case trong `qa/app-testing/` và cho App Testing agent chạy bộ smoke trên bản vừa upload
   (`docs/ci-app-testing.md`).

Có thể build tay một nhánh bất kỳ bằng **Actions › Build → Firebase App Distribution (Android) › Run workflow**.
Build tay không tăng version.

## Cài đặt một lần

### 1. GitHub secrets

Vào GitHub › repo › **Settings › Secrets and variables › Actions › New repository secret**:

| Secret | Giá trị |
|---|---|
| `TSIPTV_KEYSTORE_BASE64` | File upload keystore `.jks`, mã hoá base64 (lệnh ở dưới) |
| `TSIPTV_STORE_PASSWORD` | `storePassword` trong `composeApp/keystore.properties` |
| `TSIPTV_KEY_ALIAS` | `keyAlias` |
| `TSIPTV_KEY_PASSWORD` | `keyPassword` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Toàn bộ nội dung file key JSON của service account (bước 2) |

Mã hoá keystore sang base64 trên Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\duong-dan\ts_iptv_android_key.jks")) | Set-Clipboard
```

Dán nội dung clipboard vào secret. Không commit file `.jks` hay chuỗi base64 này.

### 2. Service account và key JSON cho App Distribution

1. Google Cloud Console › project **tsiptv-8bdd6** › **IAM & Admin › Service Accounts** › **Create service account**,
   ví dụ đặt tên `github-app-distribution`.
2. Cấp role **Firebase App Distribution Admin** (`roles/firebaseappdistro.admin`).
3. Vào tab **Keys › Add key › Create new key › JSON**. Dán toàn bộ nội dung file vào secret
   `FIREBASE_SERVICE_ACCOUNT_JSON`, rồi xoá file khỏi máy. Không commit file này.
4. Bật API: `gcloud services enable firebaseappdistribution.googleapis.com --project tsiptv-8bdd6`.

Khi đã có secret này, workflow dùng key và bỏ qua cách không cần key ở dưới.

### 2b. Cách khác: không cần key (Workload Identity Federation)

Chỉ dùng khi không tạo được key, ví dụ khi tổ chức bật policy `iam.disableServiceAccountKeyCreation`.
Nếu đã có `FIREBASE_SERVICE_ACCOUNT_JSON` thì bỏ qua mục này.

Tổ chức Google Cloud của bạn bật policy `iam.disableServiceAccountKeyCreation`, nên không tạo được key JSON.
Vì vậy GitHub Actions đổi token OIDC của GitHub lấy quyền của một service account. Cách này không cần key, và chỉ
repo `tuandv1405/IPTVMultiplatform` được dùng.

Chạy các lệnh sau một lần trong Cloud Shell (hoặc máy có `gcloud`, đăng nhập bằng tài khoản owner của project):

```bash
PROJECT_ID=tsiptv-8bdd6
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
REPO=tuandv1405/IPTVMultiplatform
SA=github-app-distribution@$PROJECT_ID.iam.gserviceaccount.com

gcloud services enable iamcredentials.googleapis.com sts.googleapis.com \
  firebaseappdistribution.googleapis.com --project $PROJECT_ID

gcloud iam service-accounts create github-app-distribution --project $PROJECT_ID \
  --display-name "GitHub Actions: App Distribution"
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member "serviceAccount:$SA" --role roles/firebaseappdistro.admin

gcloud iam workload-identity-pools create github --project $PROJECT_ID \
  --location global --display-name "GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc github-oidc --project $PROJECT_ID \
  --location global --workload-identity-pool github --display-name "GitHub OIDC" \
  --issuer-uri https://token.actions.githubusercontent.com \
  --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition "assertion.repository=='$REPO'"

gcloud iam service-accounts add-iam-policy-binding $SA --project $PROJECT_ID \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"

echo "GCP_WORKLOAD_IDENTITY_PROVIDER = projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github-oidc"
echo "GCP_SERVICE_ACCOUNT            = $SA"
```

Sau đó vào GitHub › **Settings › Secrets and variables › Actions › Variables** và tạo hai **variable**
(không phải secret) với đúng hai giá trị mà hai lệnh `echo` cuối in ra: `GCP_WORKLOAD_IDENTITY_PROVIDER` và
`GCP_SERVICE_ACCOUNT`. Hai giá trị này không phải bí mật.

### 3. Firebase App Distribution

1. Firebase Console › **App Distribution** › chọn app Android `tss.t.tsiptv`, bấm **Get started**.
2. Tab **Testers & Groups**: tạo group có alias `testers` và thêm email tester.
3. **Bắt buộc khi phát hành AAB:** App Distribution chỉ nhận AAB khi project Firebase đã được liên kết với Google Play.
   Vào Firebase Console › Project settings › **Integrations › Google Play** › Link, và app phải có trên Play Console
   (một bản ở bất kỳ track nào, kể cả internal). Nếu chưa liên kết, workflow tự upload bản APK (build từ cùng commit)
   và hiện cảnh báo trên run. Muốn luôn dùng APK thì đặt variable `FIREBASE_DISTRIBUTE_FORMAT=apk`.
4. Nếu bước upload vẫn lỗi, mở run trên GitHub › job `distribute` › bước **Upload to Firebase App Distribution**.
   Dòng `::error::` cuối cùng tóm tắt nguyên nhân. Các lỗi hay gặp:
   - `failed to distribute to testers/groups … HTTP Error: 404`: bản build **đã upload**, nhưng không có group nào có alias `testers`.
     Tạo group với alias đó, hoặc chép alias thật của group vào variable `FIREBASE_TESTER_GROUPS`.
   - Sai group: chưa có group alias `testers`.
   - Thiếu quyền: service account chưa có role Firebase App Distribution Admin.
   - Chưa bật API `firebaseappdistribution.googleapis.com`.
   - Chưa bấm **Get started** trong App Distribution.

### 4. Tuỳ chọn: GitHub variables

Vào **Settings › Secrets and variables › Actions › Variables**:

| Variable | Mặc định |
|---|---|
| `FIREBASE_ANDROID_APP_ID` | `1:234600934735:android:c8d65b7ef4741dacd4a93a` (lấy từ `composeApp/google-services.json`) |
| `FIREBASE_TESTER_GROUPS` | `testers`. Đây là **alias** của group (Testers & Groups › mở group › alias), không phải tên hiển thị. Nhiều group thì phân tách bằng dấu phẩy. Để trống thì chỉ upload, không gửi cho ai |
| `FIREBASE_TESTERS` | (trống). Email tester, phân tách bằng dấu phẩy, dùng thêm hoặc thay cho group |
| `FIREBASE_DISTRIBUTE_FORMAT` | `aab`. Đặt `apk` để luôn upload APK, ví dụ khi chưa liên kết Google Play |

## Lưu ý

- `versionCode` lấy từ `composeApp/build.gradle.kts` và tăng tự động trước mỗi lần build trên `main` (mục *Version*
  bên dưới), nên mỗi bản trên App Distribution có version riêng.
- Để CI không bị hết RAM, Kotlin compiler chạy bên trong Gradle daemon (`kotlin.compiler.execution.strategy=in-process`)
  và heap của Gradle được giới hạn ở 4 GB.
- Rule R8 nằm trong `composeApp/proguard-rules.pro`. Nếu R8 báo "Missing classes", hãy chép các dòng trong
  `composeApp/build/outputs/mapping/release/missing_rules.txt` vào file đó, rồi build release và chạy thử trên máy trước khi merge.

## Version

Workflow `.github/workflows/version-bump.yml` chạy mỗi khi có code vào `main` (merge PR hoặc push). Đây là workflow
build duy nhất chạy tự động. Nó:

1. Tăng `versionPatch` trong `composeApp/build.gradle.kts` thêm 1. Từ đó `versionCode` Android (ví dụ `1010004` →
   `1010005`), `versionName` (`tsptv.1.1.0005`) và version bản desktop (`1.1.5`) cùng tăng.
2. Tăng `CURRENT_PROJECT_VERSION` (build number iOS) trong `iosApp/iosApp.xcodeproj/project.pbxproj` thêm 1.
3. Commit thẳng vào `main` bằng `github-actions[bot]`, với message dạng `Version 1.1.5 (Android 1010005, iOS build 7)`.
4. Build đúng commit đó và đẩy lên Firebase App Distribution: Android (`pr-firebase-distribution.yml`) và iOS
   (`ios-firebase-distribution.yml`) chạy song song. Nếu bước tăng version lỗi thì không build.

Commit này push bằng `GITHUB_TOKEN` nên không kích hoạt workflow nào khác, kể cả chính nó. Nếu `main` vừa có merge
mới trong lúc chạy, workflow lấy `main` mới nhất rồi thử lại, tối đa 3 lần. Mỗi lúc chỉ có một bước tăng version
chạy. Khi nhiều merge đến dồn dập, GitHub có thể bỏ một lượt đang chờ; lượt sau vẫn tăng version và build `main`
mới nhất, đã gồm cả code của lượt bị bỏ.

Lưu ý:

- Không sửa tay `versionPatch` hay `CURRENT_PROJECT_VERSION` trong PR. Nếu sửa, PR đó sẽ conflict với commit version
  trên `main`. Muốn lên version minor hoặc major thì đổi `versionMinor`/`versionMajor` và đặt `versionPatch = 0`
  trong một PR; lần merge đó sẽ ra `x.y.1`.
- `versionPatch` tối đa 9999. Khi vượt ngưỡng, workflow báo lỗi và không commit.
- Sau khi merge, chạy `git pull` trên `main` ở máy local để lấy commit version.
- Release desktop: tag phải khớp version hiện tại của `main` sau khi đã tăng, ví dụ `desktop-1.1.5`.
