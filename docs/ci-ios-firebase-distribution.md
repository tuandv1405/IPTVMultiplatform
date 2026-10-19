# CI: merge vào main → iOS IPA → Firebase App Distribution

Workflow: `.github/workflows/ios-firebase-distribution.yml`, chạy cùng lúc với workflow Android
(`docs/ci-firebase-distribution.md`). Script build: `iosApp/ci/build-ipa.sh`, chạy được cả trên Mac.

CI chỉ build khi có code vào `main`; pull request không được build. Sau khi `.github/workflows/version-bump.yml`
tăng version và commit lên `main` (`docs/ci-firebase-distribution.md` › *Version*), nó gọi workflow này để build đúng
commit đó trên runner `macos-26` (Xcode 26). Các bước:

1. Chạy `pod install` để tải MobileVLCKit.
2. Build Kotlin framework (Release, iosArm64) và archive app `iosApp` bản Release.
3. Ký bằng certificate và provisioning profile lấy từ secrets.
4. Đẩy file `.ipa` lên Firebase App Distribution, kèm release notes gồm version, commit merge, tiêu đề PR và mã commit.
5. Lưu `.ipa` và dSYM làm artifact của workflow trong 14 ngày.

Mỗi bản build có `CFBundleVersion` dạng `<CURRENT_PROJECT_VERSION>.<số run>`, ví dụ `6.12`, nên App Distribution
hiện mỗi bản là một release riêng. Lần chạy đầu mất khoảng 45–60 phút vì phải tải Kotlin/Native, MobileVLCKit và
Firebase SDK. Riêng bước link Kotlin framework bản Release đã mất khoảng 11 phút trên Mac M-series và cần heap
Gradle 6 GB (`gradle.properties`). Các lần sau dùng cache nên nhanh hơn. Repo public nên runner macOS không tính phí.

Khi chưa có secret ký iOS (mục 2), job build bị bỏ qua và workflow chỉ hiện một thông báo, không báo lỗi.

Có thể build tay một nhánh bằng **Actions › Build → Firebase App Distribution (iOS) › Run workflow**. Build tay
không tăng version.

## Lưu ý quan trọng về thiết bị

App ký Ad Hoc hoặc Development chỉ cài được trên những máy có **UDID nằm trong provisioning profile**. Tester mới
phải được đăng ký trước:

1. Firebase Console › App Distribution › chọn app iOS › **Testers & Groups**. Tester mở link mời trên iPhone và
   đăng ký thiết bị. Sau đó bấm **Export Apple UDIDs** để tải file danh sách UDID.
2. developer.apple.com › **Certificates, IDs & Profiles › Devices** › dấu **+** (hoặc **Register Multiple Devices**
   với file vừa tải).
3. Mở lại profile của app (mục 1b ở dưới) › **Edit** › tick các máy mới › **Save** › **Download**.
4. Cập nhật secret `IOS_PROVISIONING_PROFILE_BASE64` (mục 2), rồi chạy lại workflow.

## Cài đặt một lần

### 1. Certificate và provisioning profile

Workflow tự nhận loại profile:

| Profile | Export method | Certificate |
|---|---|---|
| **Ad Hoc** (khuyên dùng) | `release-testing` | Apple Distribution |
| Development | `debugging` | Apple Development |

#### 1a. Certificate Apple Distribution

1. Xcode › **Settings › Accounts** › chọn Apple ID › team **Do Van Tuan (57N5M3BU77)** › **Manage Certificates…**
   › **+** › **Apple Distribution**.
2. Mở **Keychain Access** › **login › My Certificates**. Chuột phải vào `Apple Distribution: Do Van Tuan (57N5M3BU77)`
   › **Export…** › định dạng `.p12`, đặt mật khẩu.

#### 1b. Profile Ad Hoc

developer.apple.com › **Profiles › +** › **Ad Hoc** › App ID `tss.t.tsiptv` › chọn certificate Apple Distribution
ở trên › chọn các thiết bị › đặt tên, ví dụ `TSIPTV Ad Hoc` › **Generate › Download**.

### 2. GitHub secrets

Chạy trên Mac trong thư mục repo (cần đăng nhập `gh`):

```bash
base64 -i ~/Downloads/TSIPTV_Ad_Hoc.mobileprovision | gh secret set IOS_PROVISIONING_PROFILE_BASE64
base64 -i ~/Desktop/apple-distribution.p12 | gh secret set IOS_CERT_P12_BASE64
gh secret set IOS_CERT_PASSWORD        # nhập mật khẩu của file .p12
```

| Secret | Giá trị |
|---|---|
| `IOS_CERT_P12_BASE64` | File `.p12` (certificate và private key), mã hoá base64 |
| `IOS_CERT_PASSWORD` | Mật khẩu của file `.p12` |
| `IOS_PROVISIONING_PROFILE_BASE64` | File `.mobileprovision`, mã hoá base64 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Dùng chung với workflow Android, đã có sẵn |

Sau khi đặt secret xong, xoá file `.p12` khỏi Desktop. Không commit file `.p12`, `.mobileprovision` hay chuỗi base64.

Secret chỉ được dùng trong runner. Script import certificate vào một keychain tạm, xoá keychain đó khi chạy xong,
và workflow xoá các file đã giải mã.

### 3. Firebase App Distribution

1. App iOS `tss.t.tsiptv` (`1:234600934735:ios:503f6df8ba12a94cd4a93a`, project **tsiptv-8bdd6**) đã có trong
   Firebase. Service account của workflow Android có role Firebase App Distribution Admin trên cả project nên dùng
   được cho iOS.
2. Firebase Console › App Distribution › chọn app iOS › **Get started**, nếu chưa bấm.
3. Tab **Testers & Groups**: tạo group cho tester iOS, ví dụ alias `ios-testers`. Chép alias đó vào variable
   `FIREBASE_IOS_TESTER_GROUPS` (mục 4). Nếu để trống, workflow chỉ upload bản build mà không gửi cho ai. Khi đó có
   thể gửi tay trong Console.

### 4. Tuỳ chọn: GitHub variables

Vào **Settings › Secrets and variables › Actions › Variables**:

| Variable | Mặc định |
|---|---|
| `FIREBASE_IOS_TESTER_GROUPS` | (trống). Alias group, phân tách bằng dấu phẩy. Để trống thì chỉ upload |
| `FIREBASE_IOS_TESTERS` | (trống). Email tester, phân tách bằng dấu phẩy |
| `FIREBASE_IOS_APP_ID` | `1:234600934735:ios:503f6df8ba12a94cd4a93a` (lấy từ `iosApp/iosApp/GoogleService-Info.plist`) |
| `IOS_RUNNER` | `macos-26` |
| `IOS_XCODE_VERSION` | (trống, dùng Xcode mặc định của runner). Ví dụ `26.3` để chọn `/Applications/Xcode_26.3.app` |

## Build thử trên Mac

```bash
IOS_PROFILE=~/Downloads/TSIPTV_Ad_Hoc.mobileprovision \
IOS_CERT_P12=~/Desktop/apple-distribution.p12 IOS_CERT_PASSWORD='…' \
iosApp/ci/build-ipa.sh
```

Nếu certificate đã nằm trong login keychain thì bỏ `IOS_CERT_P12`. File IPA được ghi vào `build/ios-ipa/export/`.

## Lỗi hay gặp

- `None of the profile's N certificates has its private key in the keychain`: file `.p12` không chứa certificate
  có trong profile, hoặc file chỉ có certificate mà thiếu private key. Hãy export lại từ **My Certificates**, không
  export từ **Certificates**.
- `… is Xcode managed, but signing settings require a manually managed profile`: đang dùng profile do Xcode tự tạo
  ("iOS Team Provisioning Profile"). Hãy tạo profile Ad Hoc trên developer.apple.com như mục 1b.
- `Java heap space` ở `linkReleaseFrameworkIosArm64`: heap Gradle quá nhỏ. Bước này cần `-Xmx6144M`.
- `The provisioning profile expired`: profile hết hạn. Tạo lại hoặc gia hạn profile, rồi cập nhật secret.
- Tester không cài được app ("Unable to install"): UDID của máy chưa có trong profile. Xem mục
  *Lưu ý quan trọng về thiết bị*.
- `failed to distribute to testers/groups`: alias group sai. Bản build vẫn đã được upload.
- `Sandbox is not in sync with the Podfile.lock`: chạy `cd iosApp && pod install` rồi commit `Podfile.lock`.
