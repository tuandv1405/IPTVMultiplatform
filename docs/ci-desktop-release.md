# CI: bản desktop → repo phát hành công khai → trang tải về

Workflow: `.github/workflows/desktop-release.yml`.

Repo app này sẽ chuyển sang **riêng tư**. Khi đó mọi link `github.com/tuandv1405/IPTVMultiplatform/...` trả về
404 với người dùng, kể cả link tải file trong GitHub Releases. Vì vậy các bộ cài desktop được đăng ở một repo
**công khai** khác, `chintk19/tsiptv-releases`. Repo đó chỉ chứa Releases và mã nguồn trang tải về
`https://tsiptv-download.web.app/` (thư mục `../tsiptv-releases` cạnh repo này trên máy dev), không có mã app.

Khi đẩy tag `desktop-<v>`, workflow sẽ:

1. Kiểm tra tag khớp `versionMajor.versionMinor.versionPatch` trong `composeApp/build.gradle.kts`.
2. Build song song:
   - **Windows** (`windows-latest`): `packageDesktopRelease` ra `Setup.exe`, `.msi`, `-portable.zip`, `README.txt`.
   - **macOS** (`macos-15`, Apple Silicon): `packageReleaseDmg`, đổi tên thành `TS-IPTV-<v>-macOS-arm64.dmg`.
3. Ghi một `SHA256SUMS.txt` cho cả bản phát hành, rồi tạo Release `desktop-<v>` ở repo `tsiptv-releases`, nội
   dung lấy từ `docs/releases/desktop-<v>.md`. Nếu Release đã có thì thay file và giữ checksum của nền tảng
   không build lại.
4. Checkout repo `tsiptv-releases`, chạy `scripts/sync_github.py` để `public/releases.json` trỏ tới file mới,
   commit, rồi `firebase deploy --only hosting` trang tải về.

Chạy tay: **Actions › Desktop release → releases repo › Run workflow**, chọn branch và nền tảng (`all`,
`windows`, `macos`). Ví dụ build lại riêng macOS: Release cùng tag được cập nhật, file Windows giữ nguyên.

## Cài đặt một lần

Làm **theo đúng thứ tự** và chỉ chuyển repo app sang riêng tư ở bước cuối.

### 1. Tạo repo công khai `chintk19/tsiptv-releases` từ thư mục trang tải về

Thư mục `tsiptv-releases` trên máy đã là một git repo (chưa có remote):

```bash
cd ../tsiptv-releases
git remote add origin https://github.com/chintk19/tsiptv-releases.git
git push -u origin main
```

Trên GitHub tạo repo **Public**, trống (không thêm README) trước khi push. Repo phải có ít nhất một commit thì
mới tạo được Release.

### 2. Token để repo app ghi vào repo phát hành

GitHub › **Settings › Developer settings › Fine-grained tokens › Generate new token**:

- Repository access: **Only select repositories** › `chintk19/tsiptv-releases`.
- Permissions › Repository › **Contents: Read and write** (Metadata: Read tự bật).
- Đặt hạn dùng; khi hết hạn thì tạo token mới và thay secret.

Lưu vào repo app: **Settings › Secrets and variables › Actions › New repository secret** `RELEASES_REPO_TOKEN`.

### 3. Quyền deploy trang tải về

Bước 4 dùng cùng service account với App Distribution (`docs/ci-firebase-distribution.md`, mục 2 hoặc 2b).
Cấp thêm role **Firebase Hosting Admin**:

```bash
gcloud projects add-iam-policy-binding tsiptv-8bdd6 \
  --member "serviceAccount:github-app-distribution@tsiptv-8bdd6.iam.gserviceaccount.com" \
  --role roles/firebasehosting.admin
```

Không muốn CI deploy trang: đặt variable `DEPLOY_DOWNLOAD_PAGE=false`, rồi deploy tay như README của
`tsiptv-releases`.

### 4. Đăng bản đầu tiên ở repo mới

Trang tải hiện vẫn trỏ tới Windows 1.1.2 và macOS 1.1.3 trong repo app. Trước khi đổi repo app sang riêng tư:

- Cách nhanh: chạy workflow cho phiên bản hiện tại (đẩy tag `desktop-<v>` hoặc **Run workflow** với `all`). Cả
  Windows và macOS lên cùng một phiên bản và trang được cập nhật.
- Hoặc chép nguyên các bản cũ sang bằng `gh` (README của `tsiptv-releases`, mục "Chép một bản cũ").

Kiểm tra `https://tsiptv-download.web.app/`: mọi nút tải phải trỏ tới `github.com/chintk19/tsiptv-releases/...`.
Log của job `download-page` có dòng `::warning::still links to the private app repo` nếu còn sót link cũ.

### 5. Chuyển repo app sang riêng tư

GitHub › repo app › **Settings › General › Danger Zone › Change visibility**. Sau đó mở thử một link tải trong
cửa sổ ẩn danh.

## Tuỳ chọn: GitHub variables

| Variable | Mặc định | Ý nghĩa |
|---|---|---|
| `RELEASES_REPO` | `chintk19/tsiptv-releases` | Repo công khai nhận Release |
| `MACOS_RUNNER` | `macos-15` | Runner build `.dmg`. `macos-15` là Apple Silicon, ra bản arm64 |
| `DEPLOY_DOWNLOAD_PAGE` | (trống) | `false` để bỏ job cập nhật và deploy trang tải về |
| `TSIPTV_SUPPORT_URL`, `TSIPTV_SUPPORT_BANK_ID`, `TSIPTV_SUPPORT_ACCOUNT_NO`, `TSIPTV_SUPPORT_ACCOUNT_NAME` | (trống) | Thẻ "ủng hộ" trong app desktop (`desktop-build.md` §3). Để trống thì không hiện |

## Lưu ý

- **Release note là công khai.** `docs/releases/desktop-<v>.md` không được có link tới repo app; workflow dừng
  nếu thấy chữ `IPTVMultiplatform` trong file.
- **Phút chạy GitHub Actions.** Repo riêng tư chỉ có số phút miễn phí giới hạn mỗi tháng, và runner Windows tính
  gấp 2, macOS gấp 10 lần Linux. Một lần phát hành tốn khoảng 15–20 phút Windows và 15–20 phút macOS. Workflow
  build Android và iOS cho mỗi pull request cũng bắt đầu tốn phút sau khi repo thành riêng tư.
- Bộ cài vẫn **chưa ký số** như trước: Windows báo SmartScreen, macOS cần **Open Anyway** lần đầu.
- Ngoài máy dev, mới chỉ build bằng runner GitHub qua workflow này; lần chạy đầu nên cài thử Setup.exe và
  `.dmg` tải từ trang (mục "Cài thử" trong `desktop-windows-release.md`).
