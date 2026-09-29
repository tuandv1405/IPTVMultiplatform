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
