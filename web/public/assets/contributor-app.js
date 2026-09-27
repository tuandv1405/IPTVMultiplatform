/* Contributor dashboard: sign in → verify email → verify phone → accept policy
 * → submit playlists and follow their review status.
 *
 * Firebase Auth does the identity work in the browser; every decision that
 * matters (verified? contributor? allowed to submit?) is taken again by the
 * contributor API from the ID token, so nothing here is a security boundary.
 */
import { auth } from "/assets/firebase.js";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  signOut,
  RecaptchaVerifier,
  linkWithPhoneNumber,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import { API_BASE, API_CONFIGURED } from "/assets/contributor-config.js";

// ---------------------------------------------------------------- i18n ----

const T = {
  vi: {
    nav_policy: "Chính sách", nav_directory: "Danh sách công khai", sign_out: "Đăng xuất", retry: "Thử lại",
    title: "Người đóng góp",
    step_signin: "Đăng nhập", step_email: "Email", step_phone: "Số điện thoại", step_policy: "Chính sách",
    closed: "Chương trình người đóng góp sắp mở. Hãy quay lại sau.",
    signin_intro: "Đăng nhập bằng tài khoản TS IPTV của bạn, hoặc tạo tài khoản mới.",
    tab_login: "Đăng nhập", tab_register: "Tạo tài khoản", email: "Email", password: "Mật khẩu",
    forgot: "Quên mật khẩu?", forgot_sent: "Nếu tài khoản tồn tại, email đặt lại mật khẩu đã được gửi.",
    forgot_need_email: "Nhập email trước, rồi bấm “Quên mật khẩu?”.",
    email_title: "Xác thực email", email_body: "Chúng tôi đã gửi liên kết xác thực tới",
    email_hint: "Mở liên kết trong email (kiểm tra cả thư rác), rồi quay lại đây và bấm “Tôi đã xác thực”.",
    email_done: "Tôi đã xác thực", email_resend: "Gửi lại email", email_resent: "Đã gửi lại email xác thực.",
    email_still: "Email chưa được xác thực. Hãy mở liên kết trong email trước.",
    phone_title: "Xác thực số điện thoại",
    phone_body: "Số điện thoại là bắt buộc, được mã hoá và không bao giờ hiển thị công khai.",
    phone_label: "Số điện thoại (kèm mã quốc gia)", phone_send: "Gửi mã", code_label: "Mã gồm 6 chữ số",
    code_confirm: "Xác nhận", phone_change: "Đổi số", phone_sent: "Đã gửi mã tới",
    phone_invalid: "Nhập số điện thoại dạng quốc tế, ví dụ +84912345678.",
    code_invalid: "Mã gồm đúng 6 chữ số.",
    policy_title: "Chấp thuận chính sách người đóng góp", policy_read: "Hãy đọc kỹ",
    policy_link: "chính sách người đóng góp", public_name: "Tên hiển thị công khai",
    public_name_hint: "Tên duy nhất công chúng nhìn thấy. Không dùng email hay số điện thoại.",
    decl_contact_accurate: "Email và số điện thoại đã xác thực là của tôi và liên hệ được.",
    decl_accept_policy: "Tôi đã đọc và chấp thuận chính sách người đóng góp.",
    decl_accept_takedown: "Tôi đồng ý nhà phát hành có thể gỡ danh sách và đình chỉ quyền đóng góp của tôi bất cứ lúc nào.",
    policy_accept: "Chấp thuận và tiếp tục", policy_outdated_note: "Chính sách đã được cập nhật. Hãy chấp thuận lại để tiếp tục gửi danh sách.",
    banned: "Tài khoản người đóng góp này đã bị đình chỉ. Nếu bạn cho rằng đây là nhầm lẫn, hãy liên hệ chintk111999@gmail.com.",
    hello: "Xin chào", new_title: "Gửi danh sách phát mới", f_name: "Tên danh sách",
    f_url: "Đường dẫn danh sách (M3U, M3U8, XSPF, JSON)", check_link: "Kiểm tra", checking: "Đang kiểm tra…",
    f_image: "Hình ảnh (PNG, JPEG hoặc WebP)", f_desc: "Mô tả (không bắt buộc)", f_category: "Thể loại", f_language: "Ngôn ngữ",
    decl_title: "Cam kết về danh sách này",
    decl_own_work: "Tôi tự tạo ra và duy trì danh sách này.",
    decl_not_copied: "Danh sách không được sao chép, thu thập hay lấy cắp từ người khác hoặc dịch vụ khác.",
    decl_rights_ok: "Tôi có quyền chia sẻ mọi đường dẫn trong đó, và hiểu rằng danh sách vi phạm bản quyền sẽ bị gỡ bỏ bất cứ lúc nào.",
    submit: "Gửi để kiểm duyệt", submitting: "Đang gửi…", submit_hint: "Bấm “Kiểm tra” đường dẫn trước khi gửi.",
    submitted: "Đã gửi! Danh sách đang ở trạng thái IN REVIEW.",
    verify_ok: "Đường dẫn hợp lệ", verify_channels: "kênh", verify_groups: "nhóm", verify_sample: "Ví dụ:",
    mine_title: "Danh sách của tôi", mine_empty: "Bạn chưa gửi danh sách nào.",
    withdraw: "Rút lại", withdraw_confirm: "Rút lại danh sách này? Nếu đã công khai, nó sẽ bị gỡ khỏi thư mục.",
    view_public: "Xem công khai", sent_on: "Gửi ngày", channels: "kênh",
    choose: "— Chọn —",
    status: { IN_REVIEW: "ĐANG DUYỆT", APPROVED: "ĐÃ DUYỆT", REJECTED: "BỊ TỪ CHỐI", WITHDRAWN: "ĐÃ RÚT", REMOVED: "ĐÃ GỠ" },
    reason: {
      copyright: "Vi phạm bản quyền hoặc sao chép từ người khác.",
      broken: "Đường dẫn hỏng hoặc không an toàn.",
      inappropriate: "Nội dung không phù hợp.",
      incomplete: "Thông tin chưa đầy đủ hoặc gây hiểu nhầm.",
      duplicate: "Trùng với danh sách đã có.",
      other: "Không đáp ứng chính sách người đóng góp.",
      banned: "Tài khoản người đóng góp đã bị đình chỉ.",
      removed: "Nhà phát hành đã gỡ danh sách này.",
      withdrawn: "Bạn đã rút lại danh sách này.",
      account_deleted: "Đã gỡ vì tài khoản bị xoá.",
    },
    image_too_large: "Không nén được ảnh xuống dưới 300 KB. Hãy chọn ảnh khác.",
    image_invalid: "Ảnh phải là PNG, JPEG hoặc WebP.",
    image_required: "Hãy chọn hình ảnh cho danh sách.",
    categories: {
      general: "Tổng hợp", news: "Tin tức", sports: "Thể thao", movies: "Phim", kids: "Thiếu nhi", music: "Âm nhạc",
      documentary: "Tài liệu", education: "Giáo dục", religious: "Tôn giáo", regional: "Địa phương", other: "Khác",
    },
  },
  en: {
    nav_policy: "Policy", nav_directory: "Public directory", sign_out: "Sign out", retry: "Try again",
    title: "Contributors",
    step_signin: "Sign in", step_email: "Email", step_phone: "Phone", step_policy: "Policy",
    closed: "The contributor programme opens soon. Please check back later.",
    signin_intro: "Sign in with your TS IPTV account, or create a new one.",
    tab_login: "Sign in", tab_register: "Create account", email: "Email", password: "Password",
    forgot: "Forgot password?", forgot_sent: "If the account exists, a password reset email is on its way.",
    forgot_need_email: "Enter your email first, then press “Forgot password?”.",
    email_title: "Verify your email", email_body: "We sent a verification link to",
    email_hint: "Open the link in that email (check spam too), then come back and press “I've verified”.",
    email_done: "I've verified", email_resend: "Resend email", email_resent: "Verification email sent again.",
    email_still: "The email is not verified yet. Open the link in the email first.",
    phone_title: "Verify your phone number",
    phone_body: "A phone number is mandatory. It is encrypted and never shown publicly.",
    phone_label: "Phone number (with country code)", phone_send: "Send code", code_label: "6-digit code",
    code_confirm: "Confirm", phone_change: "Change number", phone_sent: "Code sent to",
    phone_invalid: "Enter the number in international format, e.g. +84912345678.",
    code_invalid: "The code is exactly 6 digits.",
    policy_title: "Accept the contributor policy", policy_read: "Please read the",
    policy_link: "contributor policy", public_name: "Public display name",
    public_name_hint: "The only name the public sees. Do not use your email or phone number.",
    decl_contact_accurate: "The verified email and phone number are mine and reachable.",
    decl_accept_policy: "I have read and accept the contributor policy.",
    decl_accept_takedown: "I agree the publisher may remove my playlists and suspend my contributor access at any time.",
    policy_accept: "Accept and continue", policy_outdated_note: "The policy has been updated. Accept it again to keep submitting.",
    banned: "This contributor account has been suspended. If you think this is a mistake, contact chintk111999@gmail.com.",
    hello: "Hello", new_title: "Submit a new playlist", f_name: "Playlist name",
    f_url: "Playlist link (M3U, M3U8, XSPF, JSON)", check_link: "Check", checking: "Checking…",
    f_image: "Image (PNG, JPEG or WebP)", f_desc: "Description (optional)", f_category: "Category", f_language: "Language",
    decl_title: "Declarations for this playlist",
    decl_own_work: "I created and maintain this playlist myself.",
    decl_not_copied: "It is not copied, scraped or taken from another person or service.",
    decl_rights_ok: "I have the right to share every link in it, and I understand infringing playlists are removed at any time.",
    submit: "Submit for review", submitting: "Submitting…", submit_hint: "Press “Check” on the link before submitting.",
    submitted: "Submitted! The playlist is now IN REVIEW.",
    verify_ok: "Link works", verify_channels: "channels", verify_groups: "groups", verify_sample: "For example:",
    mine_title: "My playlists", mine_empty: "You have not submitted any playlist yet.",
    withdraw: "Withdraw", withdraw_confirm: "Withdraw this playlist? If it is public it will be removed from the directory.",
    view_public: "View public page", sent_on: "Sent", channels: "channels",
    choose: "— Choose —",
    status: { IN_REVIEW: "IN REVIEW", APPROVED: "APPROVED", REJECTED: "REJECTED", WITHDRAWN: "WITHDRAWN", REMOVED: "REMOVED" },
    reason: {
      copyright: "Copyright infringement, or copied from someone else.",
      broken: "Broken or unsafe link.",
      inappropriate: "Inappropriate content.",
      incomplete: "Incomplete or misleading information.",
      duplicate: "Duplicate of an existing playlist.",
      other: "Does not meet the contributor policy.",
      banned: "The contributor account has been suspended.",
      removed: "The publisher removed this playlist.",
      withdrawn: "You withdrew this playlist.",
      account_deleted: "Removed because the account was deleted.",
    },
    image_too_large: "Could not compress the image below 300 KB. Choose another one.",
    image_invalid: "The image must be PNG, JPEG or WebP.",
    image_required: "Choose an image for the playlist.",
    categories: {
      general: "General", news: "News", sports: "Sports", movies: "Movies", kids: "Kids", music: "Music",
      documentary: "Documentary", education: "Education", religious: "Religious", regional: "Regional", other: "Other",
    },
  },
};

/** Server error codes and Firebase Auth codes → text. Unknown codes fall back to the server's message. */
const ERRORS = {
  vi: {
    unauthorized: "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.",
    email_not_verified: "Email chưa được xác thực.", phone_not_verified: "Số điện thoại chưa được xác thực.",
    not_contributor: "Hãy chấp thuận chính sách người đóng góp trước.", banned: "Tài khoản người đóng góp đã bị đình chỉ.",
    policy_outdated: "Chính sách đã thay đổi. Hãy tải lại trang.", declarations_required: "Hãy đánh dấu tất cả các cam kết.",
    invalid_public_name: "Tên hiển thị dài 2–40 ký tự, chỉ gồm chữ, số, khoảng trắng, dấu chấm, gạch ngang và gạch dưới, không chứa số điện thoại.",
    invalid_name: "Tên danh sách dài 3–80 ký tự.", invalid_url: "Đường dẫn không hợp lệ.",
    invalid_description: "Mô tả tối đa 500 ký tự.", invalid_category: "Hãy chọn thể loại.", invalid_language: "Hãy chọn ngôn ngữ.",
    invalid_image: "Ảnh phải là PNG, JPEG hoặc WebP.", image_too_large: "Ảnh phải nhỏ hơn 300 KB.",
    unsupported_scheme: "Chỉ chấp nhận đường dẫn http:// hoặc https://.",
    credentials_in_url: "Đường dẫn không được chứa tên đăng nhập hay mật khẩu.",
    port_not_allowed: "Chỉ chấp nhận các cổng web tiêu chuẩn.",
    private_address: "Không chấp nhận đường dẫn tới địa chỉ nội bộ hoặc cục bộ.",
    dns_failed: "Tên máy chủ của đường dẫn không tồn tại.", unreachable: "Không kết nối được tới đường dẫn.",
    timeout: "Đường dẫn phản hồi quá chậm.", http_error: "Máy chủ trả về lỗi.", too_large: "Danh sách lớn hơn 15 MB.",
    too_many_redirects: "Đường dẫn chuyển hướng quá nhiều lần.",
    not_a_playlist: "Đường dẫn không trả về danh sách M3U, XSPF hoặc JSON.",
    no_channels: "Danh sách không có kênh nào kèm đường dẫn phát.",
    link_already_submitted: "Đường dẫn này đã được gửi trước đó.",
    too_many_in_review: "Bạn đang có quá nhiều danh sách chờ duyệt.",
    too_many_live: "Bạn đã đạt số danh sách tối đa.", rate_limited: "Thao tác quá nhanh. Hãy thử lại sau một phút.",
    cannot_withdraw: "Danh sách này không thể rút lại nữa.",
    link_check_failed: "Đường dẫn không vượt qua bước kiểm tra.", read_failed: "Không đọc được nội dung danh sách.",
    verify_failed: "Không kiểm tra được đường dẫn.", invalid_json: "Yêu cầu không hợp lệ.",
    invalid_content_type: "Yêu cầu không hợp lệ.", payload_too_large: "Dữ liệu gửi lên quá lớn.",
    not_found: "Không tìm thấy.", invalid_reason: "Lý do không hợp lệ.", internal: "Máy chủ gặp lỗi. Hãy thử lại sau.",
    "auth/invalid-credential": "Email hoặc mật khẩu không đúng.", "auth/wrong-password": "Email hoặc mật khẩu không đúng.",
    "auth/user-not-found": "Email hoặc mật khẩu không đúng.", "auth/invalid-email": "Email không hợp lệ.",
    "auth/email-already-in-use": "Email này đã có tài khoản. Hãy đăng nhập.", "auth/weak-password": "Mật khẩu cần ít nhất 6 ký tự.",
    "auth/missing-password": "Hãy nhập mật khẩu.", "auth/too-many-requests": "Quá nhiều lần thử. Hãy đợi vài phút.",
    "auth/network-request-failed": "Lỗi mạng. Kiểm tra kết nối rồi thử lại.",
    "auth/invalid-phone-number": "Số điện thoại không hợp lệ.",
    "auth/invalid-verification-code": "Mã xác thực không đúng.", "auth/code-expired": "Mã đã hết hạn. Hãy gửi mã mới.",
    "auth/credential-already-in-use": "Số điện thoại này đã gắn với tài khoản khác.",
    "auth/account-exists-with-different-credential": "Số điện thoại này đã gắn với tài khoản khác.",
    "auth/provider-already-linked": "Tài khoản đã có số điện thoại.", "auth/quota-exceeded": "Hệ thống tạm hết lượt gửi SMS. Hãy thử lại sau.",
    "auth/operation-not-allowed": "Xác thực số điện thoại chưa được bật. Hãy liên hệ hỗ trợ.",
    "auth/billing-not-enabled": "Xác thực số điện thoại chưa được bật. Hãy liên hệ hỗ trợ.",
    "auth/captcha-check-failed": "Kiểm tra reCAPTCHA thất bại. Tải lại trang rồi thử lại.",
    network: "Không kết nối được tới máy chủ người đóng góp. Hãy thử lại sau.",
  },
  en: {
    unauthorized: "Your session expired. Sign in again.",
    email_not_verified: "Your email is not verified.", phone_not_verified: "Your phone number is not verified.",
    not_contributor: "Accept the contributor policy first.", banned: "This contributor account has been suspended.",
    policy_outdated: "The policy changed. Reload the page.", declarations_required: "Tick every declaration.",
    invalid_public_name: "The display name must be 2–40 letters, digits, spaces, dots, dashes or underscores, with no phone number.",
    invalid_name: "The playlist name must be 3–80 characters.", invalid_url: "That link is not valid.",
    invalid_description: "The description can be at most 500 characters.", invalid_category: "Choose a category.", invalid_language: "Choose a language.",
    invalid_image: "The image must be PNG, JPEG or WebP.", image_too_large: "The image must be under 300 KB.",
    unsupported_scheme: "Only http:// and https:// links are accepted.",
    credentials_in_url: "Links must not contain a username or password.",
    port_not_allowed: "Only the standard web ports are accepted.",
    private_address: "Links to private or local addresses are not accepted.",
    dns_failed: "The link's server name does not exist.", unreachable: "The link could not be reached.",
    timeout: "The link took too long to answer.", http_error: "The server answered with an error.", too_large: "The playlist is larger than 15 MB.",
    too_many_redirects: "The link redirects too many times.",
    not_a_playlist: "The link does not return an M3U, XSPF or JSON playlist.",
    no_channels: "The playlist has no channels with a stream link.",
    link_already_submitted: "This link has already been submitted.",
    too_many_in_review: "You have too many playlists waiting for review.",
    too_many_live: "You have reached the maximum number of playlists.", rate_limited: "Too fast. Try again in a minute.",
    cannot_withdraw: "This playlist can no longer be withdrawn.",
    link_check_failed: "The link did not pass the check.", read_failed: "The playlist could not be read.",
    verify_failed: "The link could not be checked.", invalid_json: "The request was not valid.",
    invalid_content_type: "The request was not valid.", payload_too_large: "The upload is too large.",
    not_found: "Not found.", invalid_reason: "Unknown reason.", internal: "Server error. Try again later.",
    "auth/invalid-credential": "Wrong email or password.", "auth/wrong-password": "Wrong email or password.",
    "auth/user-not-found": "Wrong email or password.", "auth/invalid-email": "That email address is not valid.",
    "auth/email-already-in-use": "This email already has an account. Sign in instead.", "auth/weak-password": "The password needs at least 6 characters.",
    "auth/missing-password": "Enter a password.", "auth/too-many-requests": "Too many attempts. Wait a few minutes.",
    "auth/network-request-failed": "Network problem. Check your connection and try again.",
    "auth/invalid-phone-number": "That phone number is not valid.",
    "auth/invalid-verification-code": "Wrong verification code.", "auth/code-expired": "The code expired. Send a new one.",
    "auth/credential-already-in-use": "This phone number is linked to another account.",
    "auth/account-exists-with-different-credential": "This phone number is linked to another account.",
    "auth/provider-already-linked": "This account already has a phone number.", "auth/quota-exceeded": "SMS quota reached for now. Try again later.",
    "auth/operation-not-allowed": "Phone verification is not enabled yet. Contact support.",
    "auth/billing-not-enabled": "Phone verification is not enabled yet. Contact support.",
    "auth/captcha-check-failed": "The reCAPTCHA check failed. Reload the page and try again.",
    network: "Could not reach the contributor server. Try again later.",
  },
};

const LANGUAGES = ["vi", "en", "multi", "zh", "ja", "ko", "th", "id", "fr", "de", "es", "pt", "ru", "ar", "hi"];
const LANGUAGE_NAMES = {
  vi: { multi: "Nhiều ngôn ngữ" },
  en: { multi: "Multiple languages" },
};

const LANG_KEY = "tsiptv-lang";
let lang = pickLanguage();

function pickLanguage() {
  const requested = new URLSearchParams(location.search).get("lang");
  let stored = null;
  try { stored = localStorage.getItem(LANG_KEY); } catch { /* private window */ }
  return [requested, stored, "vi"].find((l) => l === "vi" || l === "en");
}

const t = (key) => T[lang][key] ?? T.en[key] ?? key;

function applyLanguage() {
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll("[data-ui-lang]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.uiLang === lang)));
  auth.languageCode = lang; // language of the verification email and SMS
  fillSelects();
  if (lastSubmissions) renderSubmissions(lastSubmissions);
  if (authMode) setAuthMode(authMode);
}

document.querySelectorAll("[data-ui-lang]").forEach((button) => {
  button.addEventListener("click", () => {
    lang = button.dataset.uiLang;
    try { localStorage.setItem(LANG_KEY, lang); } catch { /* not remembered */ }
    applyLanguage();
  });
});

// ------------------------------------------------------------ helpers ----

const $ = (id) => document.getElementById(id);

function show(state) {
  document.querySelectorAll(".state").forEach((el) => el.classList.toggle("on", el.dataset.state === state));
  const steps = ["signin", "email", "phone", "policy"];
  const index = state === "dashboard" || state === "banned" ? steps.length : steps.indexOf(state);
  $("progress").hidden = index < 0 && state !== "dashboard" && state !== "banned";
  document.querySelectorAll("#progress li").forEach((li, i) => {
    li.classList.toggle("done", i < index);
    li.classList.toggle("current", i === index);
  });
  $("signOut").hidden = !auth.currentUser;
}

function bind(name, value) {
  document.querySelectorAll(`[data-bind="${name}"]`).forEach((el) => { el.textContent = value ?? ""; });
}

function flash(message, bad = false) {
  const el = $("flash");
  el.textContent = message;
  el.classList.toggle("bad", bad);
  el.hidden = !message;
  if (message) el.scrollIntoView({ block: "nearest", behavior: "smooth" });
}

function describe(error) {
  const code = error?.code;
  return (code && (ERRORS[lang][code] ?? ERRORS.en[code])) || error?.message || ERRORS[lang].network;
}

function setError(id, error) { $(id).textContent = error ? describe(error) : ""; }

async function busy(button, labelKey, work) {
  const original = button.textContent;
  button.disabled = true;
  if (labelKey) button.textContent = t(labelKey);
  try { return await work(); } finally { button.disabled = false; button.textContent = original; }
}

let forceFreshToken = false;

async function api(method, path, body) {
  const user = auth.currentUser;
  if (!user) throw { code: "unauthorized" };
  // After verifying email/phone the cached ID token still carries the old
  // claims; ask for a new one so the server sees the change.
  const token = await user.getIdToken(forceFreshToken);
  forceFreshToken = false;
  let response;
  try {
    response = await fetch(API_BASE + path, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw { code: "network" };
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw { code: data.error, message: data.message, details: data.details, status: response.status };
  return data;
}

// -------------------------------------------------------------- routing ----

let policy = null;
let me = null;

async function route() {
  flash("");
  if (!API_CONFIGURED) return show("closed");
  const user = auth.currentUser;
  if (!user) return show("signin");

  bind("email", user.email);
  if (!user.emailVerified) return show("email");
  if (!user.phoneNumber) return show("phone");

  show("loading");
  try {
    policy ??= await loadPolicy();
    me = await api("GET", "/api/me");
    if (!me.emailVerified || !me.phoneVerified) {
      // The token predates the verification; one refresh settles it.
      forceFreshToken = true;
      me = await api("GET", "/api/me");
    }
  } catch (error) {
    if (error?.code === "unauthorized") {
      await signOut(auth); // onAuthStateChanged routes back to the sign-in form
      return;
    }
    // The user is signed in; the service is just unreachable or busy.
    $("retryMsg").textContent = describe(error);
    return show("retry");
  }

  if (!me.emailVerified) return show("email");
  if (!me.phoneVerified) return show("phone");
  if (me.contributor?.status === "BANNED") return show("banned");
  if (!me.contributor || !me.contributor.policyCurrent) {
    if (me.contributor) {
      flash(t("policy_outdated_note"));
      $("publicName").value = me.contributor.publicName;
    }
    return show("policy");
  }
  bind("publicName", me.contributor.publicName);
  show("dashboard");
  fillSelects();
  await loadSubmissions();
}

/** Only a successful answer is kept; an error body must not stand in for the policy. */
async function loadPolicy() {
  let response;
  try {
    response = await fetch(API_BASE + "/api/policy");
  } catch {
    throw { code: "network" };
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.policyVersion) throw { code: data.error || "network", message: data.message };
  return data;
}

onAuthStateChanged(auth, () => route());
$("retryBtn").addEventListener("click", () => route());
$("signOut").addEventListener("click", () => signOut(auth));

// ------------------------------------------------------------- sign in ----

let authMode = "login";

function setAuthMode(mode) {
  authMode = mode;
  document.querySelectorAll("[data-tab]").forEach((tab) => tab.setAttribute("aria-selected", String(tab.dataset.tab === mode)));
  $("authSubmit").textContent = t(mode === "login" ? "tab_login" : "tab_register");
  $("authPassword").autocomplete = mode === "login" ? "current-password" : "new-password";
  $("forgot").hidden = mode !== "login";
  setError("authErr", null);
}

document.querySelectorAll("[data-tab]").forEach((tab) => tab.addEventListener("click", () => setAuthMode(tab.dataset.tab)));

$("authForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  setError("authErr", null);
  const email = $("authEmail").value.trim();
  const password = $("authPassword").value;
  await busy($("authSubmit"), null, async () => {
    try {
      if (authMode === "login") {
        await signInWithEmailAndPassword(auth, email, password);
      } else {
        const { user } = await createUserWithEmailAndPassword(auth, email, password);
        await sendEmailVerification(user, { url: location.href.split("#")[0] });
      }
    } catch (error) {
      setError("authErr", error);
    }
  });
});

$("forgot").addEventListener("click", async () => {
  const email = $("authEmail").value.trim();
  if (!email) return ($("authErr").textContent = t("forgot_need_email"));
  try {
    await sendPasswordResetEmail(auth, email);
  } catch (error) {
    if (error.code !== "auth/user-not-found") return setError("authErr", error);
  }
  // Same message whether or not the account exists.
  $("authErr").textContent = "";
  flash(t("forgot_sent"));
});

// -------------------------------------------------------- verify email ----

$("emailDone").addEventListener("click", () =>
  busy($("emailDone"), null, async () => {
    setError("emailErr", null);
    await auth.currentUser.reload();
    if (!auth.currentUser.emailVerified) return ($("emailErr").textContent = t("email_still"));
    forceFreshToken = true;
    await route();
  }),
);

let resendAvailableAt = 0;
$("emailResend").addEventListener("click", async () => {
  if (Date.now() < resendAvailableAt) return;
  setError("emailErr", null);
  try {
    await sendEmailVerification(auth.currentUser, { url: location.href.split("#")[0] });
    resendAvailableAt = Date.now() + 60_000;
    flash(t("email_resent"));
  } catch (error) {
    setError("emailErr", error);
  }
});

// Coming back from the verification link in another tab.
document.addEventListener("visibilitychange", async () => {
  const user = auth.currentUser;
  if (document.visibilityState !== "visible" || !user || user.emailVerified) return;
  if (!document.querySelector('[data-state="email"]').classList.contains("on")) return;
  await user.reload();
  if (auth.currentUser.emailVerified) {
    forceFreshToken = true;
    route();
  }
});

// -------------------------------------------------------- verify phone ----

let recaptcha = null;
let confirmation = null;

function resetRecaptcha() {
  try { recaptcha?.clear(); } catch { /* already gone */ }
  $("recaptcha").innerHTML = "";
  recaptcha = null;
}

function normalisePhone(raw) {
  let phone = raw.replace(/[\s().-]/g, "");
  // A Vietnamese local number typed as 09… means +849…
  if (/^0\d{9}$/.test(phone)) phone = `+84${phone.slice(1)}`;
  return /^\+\d{8,15}$/.test(phone) ? phone : null;
}

$("phoneForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  setError("phoneErr", null);
  const phone = normalisePhone($("phoneNumber").value);
  if (!phone) return ($("phoneErr").textContent = t("phone_invalid"));
  await busy($("phoneSend"), null, async () => {
    try {
      recaptcha ??= new RecaptchaVerifier(auth, "recaptcha", { size: "invisible" });
      confirmation = await linkWithPhoneNumber(auth.currentUser, phone, recaptcha);
      $("phoneForm").hidden = true;
      $("codeForm").hidden = false;
      flash(`${t("phone_sent")} ${phone}`);
      $("phoneCode").focus();
    } catch (error) {
      resetRecaptcha(); // a reCAPTCHA widget cannot be reused after a failure
      setError("phoneErr", error);
    }
  });
});

$("codeForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  setError("phoneErr", null);
  const code = $("phoneCode").value.trim();
  if (!/^\d{6}$/.test(code)) return ($("phoneErr").textContent = t("code_invalid"));
  try {
    await confirmation.confirm(code);
    await auth.currentUser.reload();
    forceFreshToken = true;
    flash("");
    await route();
  } catch (error) {
    setError("phoneErr", error);
  }
});

$("phoneRetry").addEventListener("click", () => {
  confirmation = null;
  resetRecaptcha();
  $("codeForm").hidden = true;
  $("phoneForm").hidden = false;
  flash("");
});

// ------------------------------------------------------- accept policy ----

function declarations(form) {
  return Object.fromEntries([...form.querySelectorAll('input[type="checkbox"]')].map((box) => [box.name, box.checked]));
}

$("policyForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  setError("policyErr", null);
  const button = event.submitter ?? $("policyForm").querySelector("button");
  await busy(button, null, async () => {
    try {
      me = await api("POST", "/api/contributor/register", {
        publicName: $("publicName").value,
        policyVersion: policy.policyVersion,
        declarations: declarations($("policyForm")),
      });
      await route();
    } catch (error) {
      setError("policyErr", error);
    }
  });
});

// ---------------------------------------------------------- dashboard ----

function fillSelects() {
  const category = $("plCategory");
  const language = $("plLanguage");
  const keepCategory = category.value;
  const keepLanguage = language.value;
  const categories = policy?.categories ?? Object.keys(T.en.categories);
  category.replaceChildren(new Option(t("choose"), ""), ...categories.map((c) => new Option(T[lang].categories[c] ?? c, c)));
  const names = new Intl.DisplayNames([lang], { type: "language" });
  language.replaceChildren(
    new Option(t("choose"), ""),
    ...LANGUAGES.map((code) => new Option(LANGUAGE_NAMES[lang][code] ?? safeName(names, code), code)),
  );
  category.value = keepCategory;
  language.value = keepLanguage;
}

function safeName(names, code) {
  try { return names.of(code) ?? code; } catch { return code; }
}

let verifiedUrl = null;
let imageDataUrl = null;

function updateSubmitState() {
  const linkOk = verifiedUrl !== null && verifiedUrl === $("plUrl").value.trim();
  $("submitBtn").disabled = !linkOk;
  $("submitHint").hidden = linkOk;
}

$("plUrl").addEventListener("input", () => {
  if ($("plUrl").value.trim() !== verifiedUrl) {
    verifiedUrl = null;
    $("verifyResult").hidden = true;
  }
  updateSubmitState();
});

$("checkLink").addEventListener("click", () =>
  busy($("checkLink"), "checking", async () => {
    const url = $("plUrl").value.trim();
    const box = $("verifyResult");
    verifiedUrl = null;
    box.hidden = false;
    box.replaceChildren();
    try {
      const result = await api("POST", "/api/playlists/verify", { url });
      box.className = `verify ${result.ok ? "ok" : "bad"}`;
      if (result.ok) {
        verifiedUrl = url;
        const head = document.createElement("strong");
        head.textContent = `✓ ${t("verify_ok")} · ${result.format.toUpperCase()} · ${result.channelCount} ${t("verify_channels")} · ${result.groupCount} ${t("verify_groups")}`;
        const sample = document.createElement("div");
        sample.textContent = `${t("verify_sample")} ${result.sampleNames.join(", ")}`;
        box.append(head, sample);
      } else {
        box.textContent = `✗ ${describe(result)}`;
      }
    } catch (error) {
      box.className = "verify bad";
      box.textContent = `✗ ${describe(error)}`;
    }
    updateSubmitState();
  }),
);

$("plImage").addEventListener("change", async () => {
  imageDataUrl = null;
  $("plPreview").hidden = true;
  setError("submitErr", null);
  const file = $("plImage").files[0];
  if (!file) return;
  try {
    imageDataUrl = await prepareImage(file);
    $("plPreview").src = imageDataUrl;
    $("plPreview").hidden = false;
  } catch (key) {
    $("submitErr").textContent = t(key);
    $("plImage").value = "";
  }
});

/** Downscale to ≤512 px and re-encode as JPEG ≤300 KB, so uploads stay small and metadata (EXIF, GPS) is dropped. */
async function prepareImage(file) {
  if (!/^image\/(png|jpeg|webp)$/.test(file.type)) throw "image_invalid";
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject("image_invalid");
      image.src = url;
    });
    const scale = Math.min(1, 512 / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#0c0d2c"; // JPEG has no alpha; transparent logos sit on the site's navy
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.86, 0.75, 0.62, 0.5, 0.38]) {
      const dataUrl = canvas.toDataURL("image/jpeg", quality);
      const bytes = Math.floor(((dataUrl.length - dataUrl.indexOf(",") - 1) * 3) / 4);
      if (bytes <= 300 * 1024) return dataUrl;
    }
    throw "image_too_large";
  } finally {
    URL.revokeObjectURL(url);
  }
}

$("submitForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  setError("submitErr", null);
  if (!imageDataUrl) return ($("submitErr").textContent = t("image_required"));
  await busy($("submitBtn"), "submitting", async () => {
    try {
      await api("POST", "/api/submissions", {
        name: $("plName").value,
        url: $("plUrl").value.trim(),
        description: $("plDesc").value,
        category: $("plCategory").value,
        language: $("plLanguage").value,
        image: imageDataUrl,
        declarations: declarations($("submitForm")),
      });
      $("submitForm").reset();
      imageDataUrl = null;
      verifiedUrl = null;
      $("plPreview").hidden = true;
      $("verifyResult").hidden = true;
      flash(t("submitted"));
      await loadSubmissions();
    } catch (error) {
      // A failed link check carries the specific reason; show that, translated.
      const reason = error.details?.reason;
      $("submitErr").textContent = reason ? describe({ code: reason, message: error.message }) : describe(error);
    }
  });
  updateSubmitState();
});

let lastSubmissions = null;

async function loadSubmissions() {
  try {
    const { submissions } = await api("GET", "/api/submissions");
    renderSubmissions(submissions);
  } catch (error) {
    flash(describe(error), true);
  }
}

function renderSubmissions(submissions) {
  lastSubmissions = submissions;
  $("mineEmpty").hidden = submissions.length > 0;
  const date = new Intl.DateTimeFormat(lang, { dateStyle: "medium" });
  $("mine").replaceChildren(
    ...submissions.map((s) => {
      const li = document.createElement("li");
      const top = document.createElement("div");
      top.className = "top";
      const name = document.createElement("div");
      name.className = "name";
      name.textContent = s.name;
      const badge = document.createElement("span");
      badge.className = `badge ${s.status}`;
      badge.textContent = T[lang].status[s.status] ?? s.status;
      top.append(name, badge);

      const meta = document.createElement("div");
      meta.className = "meta";
      const count = s.channelCount != null ? ` · ${s.channelCount} ${t("channels")}` : "";
      meta.textContent = `${s.url}${count} · ${t("sent_on")} ${date.format(new Date(s.createdAt))} · ${s.id}`;
      li.append(top, meta);

      const code = s.statusReason?.code;
      if (code && s.status !== "APPROVED" && s.status !== "IN_REVIEW") {
        const reason = document.createElement("p");
        reason.className = "reason";
        reason.textContent = T[lang].reason[code] ?? code;
        if (s.statusReason.note) reason.textContent += ` (${s.statusReason.note})`;
        li.append(reason);
      }

      const actions = document.createElement("div");
      actions.className = "row";
      if (s.status === "APPROVED") {
        const view = document.createElement("a");
        view.className = "ghost";
        view.href = `/playlists/#${encodeURIComponent(s.id)}`;
        view.textContent = t("view_public");
        actions.append(view);
      }
      if (s.status === "IN_REVIEW" || s.status === "APPROVED") {
        const withdraw = document.createElement("button");
        withdraw.type = "button";
        withdraw.className = "ghost";
        withdraw.textContent = t("withdraw");
        withdraw.addEventListener("click", async () => {
          if (!confirm(t("withdraw_confirm"))) return;
          await busy(withdraw, null, async () => {
            try {
              await api("POST", `/api/submissions/${encodeURIComponent(s.id)}/withdraw`);
              await loadSubmissions();
            } catch (error) {
              flash(describe(error), true);
            }
          });
        });
        actions.append(withdraw);
      }
      if (actions.childElementCount) li.append(actions);
      return li;
    }),
  );
}

// --------------------------------------------------------------- start ----

applyLanguage();
setAuthMode("login");
show(API_CONFIGURED ? "loading" : "closed");
