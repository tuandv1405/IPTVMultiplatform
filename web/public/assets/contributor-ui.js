/* Shared bits for the contributor pages: language, text, small DOM helpers,
 * and the Google sign-in header. Everything user-supplied goes through
 * textContent — never innerHTML. */

const TEXT = {
  vi: {
    sign_in_google: "Đăng nhập bằng Google", sign_out: "Đăng xuất", signed_in_as: "Đang đăng nhập:",
    loading: "Đang tải…", retry: "Thử lại", choose: "— Chọn —", cancel: "Huỷ", save: "Lưu",
    nav_policy: "Chính sách", nav_request: "Đăng ký", nav_submit: "Đăng playlist", nav_directory: "Danh sách công khai",
    signin_needed: "Hãy đăng nhập bằng tài khoản Google để tiếp tục.",
    status: {
      PENDING: "ĐANG CHỜ DUYỆT", APPROVED: "ĐÃ DUYỆT", REJECTED: "BỊ TỪ CHỐI", IN_REVIEW: "ĐANG DUYỆT",
      WITHDRAWN: "ĐÃ RÚT", REMOVED: "ĐÃ GỠ", ACTIVE: "ĐANG HOẠT ĐỘNG", SUSPENDED: "BỊ ĐÌNH CHỈ",
    },
    reason: {
      copyright: "Vi phạm bản quyền hoặc sao chép từ người khác.", broken: "Đường dẫn hỏng hoặc không an toàn.",
      inappropriate: "Nội dung không phù hợp.", incomplete: "Thông tin chưa đầy đủ hoặc gây hiểu nhầm.",
      duplicate: "Trùng với danh sách đã có.", other: "Không đáp ứng chính sách người đóng góp.",
      removed: "Nhà phát hành đã gỡ danh sách này.", withdrawn: "Bạn đã rút lại danh sách này.",
      banned: "Tài khoản người đóng góp đã bị đình chỉ.", account_deleted: "Đã gỡ vì tài khoản bị xoá.",
    },
    categories: {
      general: "Tổng hợp", news: "Tin tức", sports: "Thể thao", movies: "Phim", kids: "Thiếu nhi", music: "Âm nhạc",
      documentary: "Tài liệu", education: "Giáo dục", religious: "Tôn giáo", regional: "Địa phương", other: "Khác",
    },
    errors: {
      unauthorized: "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.",
      forbidden: "Bạn không có quyền thực hiện thao tác này.",
      network: "Không kết nối được tới máy chủ. Hãy thử lại.",
      request_pending: "Bạn đã có một yêu cầu đang chờ duyệt.",
      already_contributor: "Bạn đã là người đóng góp.",
      not_contributor: "Chỉ người đóng góp đã được duyệt mới đăng được playlist.",
      suspended: "Tài khoản người đóng góp của bạn đang bị đình chỉ.",
      invalid_url: "Đường dẫn không hợp lệ.", unsupported_scheme: "Chỉ chấp nhận đường dẫn http:// hoặc https://.",
      credentials_in_url: "Đường dẫn không được chứa tên đăng nhập hay mật khẩu.",
      link_already_submitted: "Đường dẫn này đã được gửi trước đó.",
      not_a_playlist: "Đường dẫn không trả về danh sách M3U, XSPF hoặc JSON.",
      no_channels: "Danh sách không có kênh nào kèm đường dẫn phát.",
      http_error: "Máy chủ của đường dẫn trả về lỗi.",
      link_check_failed: "Đường dẫn không vượt qua bước kiểm tra.",
      private_address: "Không chấp nhận đường dẫn tới địa chỉ nội bộ.",
      rate_limited: "Thao tác quá nhanh. Hãy thử lại sau một phút.",
      too_many_in_review: "Bạn đang có quá nhiều danh sách chờ duyệt.",
      invalid_image: "Ảnh phải là PNG, JPEG hoặc WebP.", image_too_large: "Không nén được ảnh xuống dưới 300 KB.",
      encryption_unavailable: "Chưa cấu hình khoá mã hoá — chương trình chưa mở.",
      "auth/unauthorized-domain": "Tên miền này chưa được phép đăng nhập Google.",
      "auth/network-request-failed": "Lỗi mạng. Kiểm tra kết nối rồi thử lại.",
      google_required: "Hãy đăng nhập bằng tài khoản Google.", email_not_verified: "Email của tài khoản chưa được xác thực.",
      already_decided: "Mục này đã được xử lý trước đó.", cannot_withdraw: "Danh sách này không thể rút lại nữa.",
      invalid_action: "Thao tác không hợp lệ.", invalid_decision: "Quyết định không hợp lệ.", invalid_status: "Trạng thái không hợp lệ.",
      invalid_reason: "Lý do không hợp lệ.", too_many_live: "Bạn đã đạt số danh sách tối đa.",
      invalid_public_name: "Tên hiển thị dài 2–40 ký tự, chỉ gồm chữ, số, khoảng trắng, dấu chấm, gạch ngang, gạch dưới và không chứa số điện thoại.",
      invalid_about: "Phần giới thiệu cần 20–1000 ký tự.", invalid_links: "Đường dẫn mẫu phải bắt đầu bằng http:// hoặc https:// (tối đa 3).",
      invalid_contact: "Thông tin liên hệ phải được mã hoá. Hãy tải lại trang.", consents_required: "Hãy chấp thuận cả ba chính sách.",
      declarations_required: "Hãy đánh dấu cả ba cam kết.", invalid_name: "Tên danh sách dài 3–80 ký tự.",
      invalid_description: "Mô tả tối đa 500 ký tự.", invalid_category: "Hãy chọn thể loại.", invalid_language: "Hãy chọn ngôn ngữ.",
      invalid_json: "Yêu cầu không hợp lệ.", invalid_content_type: "Yêu cầu không hợp lệ.", payload_too_large: "Dữ liệu gửi lên quá lớn.",
      timeout: "Đường dẫn phản hồi quá chậm.", unreachable: "Không kết nối được tới đường dẫn.", dns_failed: "Tên máy chủ của đường dẫn không tồn tại.",
      too_large: "Danh sách lớn hơn 15 MB.", too_many_redirects: "Đường dẫn chuyển hướng quá nhiều lần.",
      port_not_allowed: "Chỉ chấp nhận các cổng web tiêu chuẩn.", read_failed: "Không đọc được nội dung danh sách.",
      verify_failed: "Không kiểm tra được đường dẫn.", not_found: "Không tìm thấy.", internal: "Máy chủ gặp lỗi. Hãy thử lại sau.",
      "failed-precondition": "Thao tác không thực hiện được lúc này. Hãy tải lại trang.", aborted: "Có xung đột, hãy thử lại.",
      "deadline-exceeded": "Máy chủ phản hồi quá chậm. Hãy thử lại.", "resource-exhausted": "Hệ thống đang quá tải. Hãy thử lại sau.",
      unknown: "Đã xảy ra lỗi. Hãy thử lại.",    },
  },
  en: {
    sign_in_google: "Sign in with Google", sign_out: "Sign out", signed_in_as: "Signed in as",
    loading: "Loading…", retry: "Try again", choose: "— Choose —", cancel: "Cancel", save: "Save",
    nav_policy: "Policy", nav_request: "Apply", nav_submit: "Upload playlist", nav_directory: "Public directory",
    signin_needed: "Sign in with your Google account to continue.",
    status: {
      PENDING: "PENDING", APPROVED: "APPROVED", REJECTED: "REJECTED", IN_REVIEW: "IN REVIEW",
      WITHDRAWN: "WITHDRAWN", REMOVED: "REMOVED", ACTIVE: "ACTIVE", SUSPENDED: "SUSPENDED",
    },
    reason: {
      copyright: "Copyright infringement, or copied from someone else.", broken: "Broken or unsafe link.",
      inappropriate: "Inappropriate content.", incomplete: "Incomplete or misleading information.",
      duplicate: "Duplicate of an existing playlist.", other: "Does not meet the contributor policy.",
      removed: "The publisher removed this playlist.", withdrawn: "You withdrew this playlist.",
      banned: "The contributor account has been suspended.", account_deleted: "Removed because the account was deleted.",
    },
    categories: {
      general: "General", news: "News", sports: "Sports", movies: "Movies", kids: "Kids", music: "Music",
      documentary: "Documentary", education: "Education", religious: "Religious", regional: "Regional", other: "Other",
    },
    errors: {
      unauthorized: "Your session expired. Sign in again.",
      forbidden: "You are not allowed to do that.",
      network: "Could not reach the server. Try again.",
      request_pending: "You already have a request waiting for review.",
      already_contributor: "You are already a contributor.",
      not_contributor: "Only approved contributors can upload playlists.",
      suspended: "Your contributor account is suspended.",
      invalid_url: "That link is not valid.", unsupported_scheme: "Only http:// and https:// links are accepted.",
      credentials_in_url: "Links must not contain a username or password.",
      link_already_submitted: "This link has already been submitted.",
      not_a_playlist: "The link does not return an M3U, XSPF or JSON playlist.",
      no_channels: "The playlist has no channels with a stream link.",
      http_error: "The link's server answered with an error.",
      link_check_failed: "The link did not pass the check.",
      private_address: "Links to private or local addresses are not accepted.",
      rate_limited: "Too fast. Try again in a minute.",
      too_many_in_review: "You have too many playlists waiting for review.",
      invalid_image: "The image must be PNG, JPEG or WebP.", image_too_large: "Could not compress the image below 300 KB.",
      encryption_unavailable: "The encryption key is not configured — the programme is not open yet.",
      "auth/unauthorized-domain": "This domain is not authorised for Google sign-in.",
      "auth/network-request-failed": "Network problem. Check your connection and try again.",
      google_required: "Sign in with a Google account.", email_not_verified: "The account's email is not verified.",
      already_decided: "This was already decided.", cannot_withdraw: "This playlist can no longer be withdrawn.",
      invalid_action: "Invalid action.", invalid_decision: "Invalid decision.", invalid_status: "Invalid status.",
      invalid_reason: "Invalid reason.", too_many_live: "You have reached the maximum number of playlists.",
      invalid_public_name: "The display name must be 2–40 letters, digits, spaces, dots, dashes or underscores, with no phone number.",
      invalid_about: "The introduction needs 20–1000 characters.", invalid_links: "Sample links must start with http:// or https:// (up to 3).",
      invalid_contact: "Contact details must be encrypted. Reload the page.", consents_required: "Accept all three policies.",
      declarations_required: "Tick all three declarations.", invalid_name: "The playlist name must be 3–80 characters.",
      invalid_description: "The description can be at most 500 characters.", invalid_category: "Choose a category.", invalid_language: "Choose a language.",
      invalid_json: "The request was not valid.", invalid_content_type: "The request was not valid.", payload_too_large: "The upload is too large.",
      timeout: "The link took too long to answer.", unreachable: "The link could not be reached.", dns_failed: "The link's server name does not exist.",
      too_large: "The playlist is larger than 15 MB.", too_many_redirects: "The link redirects too many times.",
      port_not_allowed: "Only the standard web ports are accepted.", read_failed: "The playlist could not be read.",
      verify_failed: "The link could not be checked.", not_found: "Not found.", internal: "Server error. Try again later.",
      "failed-precondition": "That cannot be done right now. Reload the page.", aborted: "There was a conflict; try again.",
      "deadline-exceeded": "The server took too long. Try again.", "resource-exhausted": "The service is busy. Try again later.",
      unknown: "Something went wrong. Try again.",    },
  },
};

export const CATEGORIES = Object.keys(TEXT.en.categories);
export const LANGUAGES = ["vi", "en", "multi", "zh", "ja", "ko", "th", "id", "fr", "de", "es", "pt", "ru", "ar", "hi"];

const LANG_KEY = "tsiptv-lang";
let lang = (() => {
  const requested = new URLSearchParams(location.search).get("lang");
  let stored = null;
  try { stored = localStorage.getItem(LANG_KEY); } catch { /* private window */ }
  return [requested, stored, "vi"].find((l) => l === "vi" || l === "en");
})();

const pageText = { vi: {}, en: {} };
const listeners = [];

/** Pages register their own strings; shared ones are above. */
export function addText(dict) {
  Object.assign(pageText.vi, dict.vi);
  Object.assign(pageText.en, dict.en);
}

export const getLang = () => lang;
export const t = (key) => pageText[lang][key] ?? TEXT[lang][key] ?? pageText.en[key] ?? TEXT.en[key] ?? key;
export const statusText = (s) => TEXT[lang].status[s] ?? s;
export const reasonText = (code) => TEXT[lang].reason[code] ?? pageText[lang].reason?.[code] ?? code;
export const categoryText = (c) => TEXT[lang].categories[c] ?? c;

export function languageName(code) {
  if (code === "multi") return lang === "vi" ? "Nhiều ngôn ngữ" : "Multiple languages";
  try { return new Intl.DisplayNames([lang], { type: "language" }).of(code) ?? code; } catch { return code; }
}

export function describe(error) {
  // A failed link check carries the specific reason; that is the useful part.
  const code = error?.code === "link_check_failed" && error.details?.reason ? error.details.reason : error?.code;
  const known = code && (TEXT[lang].errors[code] ?? pageText[lang].errors?.[code]);
  if (known) return known;
  // Server messages are English; in Vietnamese show a generic line instead.
  return (lang === "en" && error?.message) || TEXT[lang].errors.unknown;
}

export function onLanguageChange(callback) { listeners.push(callback); }

export function applyLanguage() {
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll("[data-ui-lang]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.uiLang === lang)));
  listeners.forEach((fn) => fn(lang));
}

document.querySelectorAll("[data-ui-lang]").forEach((button) => {
  button.addEventListener("click", () => {
    lang = button.dataset.uiLang;
    try { localStorage.setItem(LANG_KEY, lang); } catch { /* not remembered */ }
    applyLanguage();
  });
});

export const $ = (id) => document.getElementById(id);

export function el(tag, { className, text, attrs } = {}, ...children) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  if (attrs) for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  node.append(...children.filter(Boolean));
  return node;
}

export function show(state) {
  document.querySelectorAll(".state").forEach((node) => node.classList.toggle("on", node.dataset.state === state));
}

export function flash(message, bad = false) {
  const node = $("flash");
  if (!node) return;
  node.textContent = message ?? "";
  node.classList.toggle("bad", bad);
  node.hidden = !message;
  if (message) node.scrollIntoView({ block: "nearest", behavior: "smooth" });
}

export async function busy(button, work) {
  button.disabled = true;
  try { return await work(); } finally { button.disabled = false; }
}

export function formatDate(ms) {
  if (!ms) return "";
  return new Intl.DateTimeFormat(lang, { dateStyle: "medium", timeStyle: "short" }).format(new Date(ms));
}

export function fillSelect(select, entries, placeholder = t("choose")) {
  const keep = select.value;
  select.replaceChildren(new Option(placeholder, ""), ...entries.map(([value, label]) => new Option(label, value)));
  select.value = keep;
}

/** Header account area + the sign-in state panel shared by every page. */
export function wireAccount(backend) {
  $("signIn")?.addEventListener("click", async (event) => {
    try {
      await busy(event.currentTarget, () => backend.signInWithGoogle());
    } catch (error) {
      flash(describe(error), true);
    }
  });
  $("signOut")?.addEventListener("click", () => backend.signOut());
  backend.onUser((user) => {
    if ($("signOut")) $("signOut").hidden = !user;
    if ($("who")) $("who").textContent = user ? `${t("signed_in_as")} ${user.email ?? user.displayName ?? ""}` : "";
  });
}

/** Downscale to ≤512 px and re-encode as JPEG ≤300 KB; drops EXIF/GPS metadata. */
export async function prepareImage(file) {
  if (!/^image\/(png|jpeg|webp)$/.test(file.type)) throw { code: "invalid_image" };
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject({ code: "invalid_image" });
      image.src = url;
    });
    const scale = Math.min(1, 512 / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#0c0d2c"; // JPEG has no alpha
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.86, 0.75, 0.62, 0.5, 0.38]) {
      const dataUrl = canvas.toDataURL("image/jpeg", quality);
      const data = dataUrl.slice(dataUrl.indexOf(",") + 1);
      if (Math.floor((data.length * 3) / 4) <= 300 * 1024) return { mime: "image/jpeg", data, dataUrl };
    }
    throw { code: "image_too_large" };
  } finally {
    URL.revokeObjectURL(url);
  }
}
