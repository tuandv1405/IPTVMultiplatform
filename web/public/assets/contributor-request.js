/* /contributor/request/ — apply to become a contributor. One request per user at
 * a time: a new one is possible only after the previous one was rejected. */
import { getBackend } from "/assets/contributor-backend.js";
import { encryptContact } from "/assets/contact-crypto.js";
import { CONTACT_PUBLIC_KEY, POLICY_VERSIONS, API_CONFIGURED } from "/assets/contributor-config.js";
import {
  $, addText, applyLanguage, busy, describe, flash, formatDate, onLanguageChange, reasonText, show, statusText, t,
  wireAccount,
} from "/assets/contributor-ui.js";

addText({
  vi: {
    title: "Đăng ký trở thành người đóng góp",
    closed: "Chương trình người đóng góp sắp mở. Hãy quay lại sau.",
    status_title: "Yêu cầu của bạn",
    explain_PENDING: "Yêu cầu đang chờ quản trị viên xem xét. Bạn sẽ không gửi được yêu cầu mới cho tới khi có kết quả.",
    explain_APPROVED: "Chúc mừng! Bạn đã là người đóng góp và có thể đăng playlist.",
    explain_REJECTED: "Yêu cầu chưa được chấp thuận. Bạn có thể sửa thông tin và gửi lại.",
    sent_on: "Gửi lúc", attempt: "Lần gửi", decided_on: "Kết quả lúc", reason_label: "Lý do:",
    go_submit: "Đăng playlist", reapply: "Gửi lại yêu cầu", cancel_request: "Huỷ yêu cầu",
    cancel_confirm: "Huỷ yêu cầu này? Bạn có thể gửi lại sau.",
    privacy_note: "🔒 Họ tên, email và số điện thoại là bắt buộc. Chúng được mã hoá ngay trên trình duyệt của bạn trước khi gửi đi; chỉ quản trị viên của nhà phát hành giữ khoá giải mã. Chúng không bao giờ hiển thị công khai.",
    f_public_name: "Tên hiển thị công khai",
    f_public_name_hint: "Tên duy nhất công chúng nhìn thấy. Không dùng email hay số điện thoại.",
    f_full_name: "Họ và tên", f_email: "Email (từ tài khoản Google)", f_phone: "Số điện thoại (kèm mã quốc gia)",
    f_about: "Giới thiệu bản thân và các danh sách bạn duy trì",
    f_about_hint: "20–1000 ký tự. Bạn tự xây dựng danh sách như thế nào, nguồn kênh đến từ đâu?",
    f_links: "Đường dẫn mẫu (không bắt buộc, tối đa 3)",
    consents: "Xác nhận", c_policy: "Tôi đã đọc và chấp thuận", c_policy_link: "chính sách người đóng góp",
    c_terms: "Tôi đồng ý với", c_terms_link: "điều khoản sử dụng",
    c_privacy: "Tôi đã đọc", c_privacy_link: "chính sách quyền riêng tư", c_privacy_tail: "và đồng ý cho xử lý dữ liệu như mô tả.",
    send_request: "Gửi yêu cầu", sent: "Đã gửi yêu cầu. Quản trị viên sẽ xem xét sớm.",
    e_public_name: "Tên hiển thị dài 2–40 ký tự, chỉ gồm chữ, số, khoảng trắng, dấu chấm, gạch ngang, gạch dưới và không chứa số điện thoại.",
    e_full_name: "Hãy nhập họ và tên (2–100 ký tự).",
    e_phone: "Nhập số điện thoại dạng quốc tế, ví dụ +84912345678.",
    e_about: "Phần giới thiệu cần 20–1000 ký tự.",
    e_links: "Đường dẫn mẫu phải bắt đầu bằng http:// hoặc https://.",
    e_consents: "Hãy đánh dấu cả ba ô xác nhận.",
  },
  en: {
    title: "Apply to become a contributor",
    closed: "The contributor programme opens soon. Please check back later.",
    status_title: "Your request",
    explain_PENDING: "Your request is waiting for an admin. You cannot send a new one until it is decided.",
    explain_APPROVED: "Congratulations! You are a contributor and can upload playlists.",
    explain_REJECTED: "Your request was not approved. You can update it and apply again.",
    sent_on: "Sent", attempt: "Attempt", decided_on: "Decided", reason_label: "Reason:",
    go_submit: "Upload a playlist", reapply: "Apply again", cancel_request: "Cancel request",
    cancel_confirm: "Cancel this request? You can apply again later.",
    privacy_note: "🔒 Full name, email and phone number are mandatory. They are encrypted in your browser before they are sent; only the publisher's administrators hold the decryption key. They are never shown publicly.",
    f_public_name: "Public display name",
    f_public_name_hint: "The only name the public sees. Do not use your email or phone number.",
    f_full_name: "Full name", f_email: "Email (from your Google account)", f_phone: "Phone number (with country code)",
    f_about: "About you and the playlists you maintain",
    f_about_hint: "20–1000 characters. How do you build your playlists, and where do the channels come from?",
    f_links: "Sample links (optional, up to 3)",
    consents: "Consent", c_policy: "I have read and accept the", c_policy_link: "contributor policy",
    c_terms: "I agree to the", c_terms_link: "terms of use",
    c_privacy: "I have read the", c_privacy_link: "privacy policy", c_privacy_tail: "and agree to the processing described there.",
    send_request: "Send request", sent: "Request sent. An admin will review it soon.",
    e_public_name: "The display name must be 2–40 letters, digits, spaces, dots, dashes or underscores, with no phone number.",
    e_full_name: "Enter your full name (2–100 characters).",
    e_phone: "Enter the number in international format, e.g. +84912345678.",
    e_about: "The introduction needs 20–1000 characters.",
    e_links: "Sample links must start with http:// or https://.",
    e_consents: "Tick all three boxes.",
  },
});

const backend = await getBackend();
wireAccount(backend);
let current = null;

onLanguageChange(() => { if (current) renderStatus(current); });

async function route() {
  flash("");
  if (!API_CONFIGURED || !CONTACT_PUBLIC_KEY) return show("closed");
  const user = backend.currentUser();
  if (!user) return show("signin");
  show("loading");
  try {
    current = await backend.getMyRequest();
  } catch (error) {
    $("retryMsg").textContent = describe(error);
    return show("retry");
  }
  if (!current) return showForm();
  renderStatus(current);
  show("status");
}

function renderStatus(request) {
  const badge = $("reqBadge");
  badge.className = `badge ${request.status}`;
  badge.textContent = statusText(request.status);
  const parts = [`${t("sent_on")} ${formatDate(request.createdAt)}`, `${t("attempt")} ${request.attempt ?? 1}`];
  if (request.decision?.at) parts.push(`${t("decided_on")} ${formatDate(request.decision.at)}`);
  $("reqMeta").textContent = parts.join(" · ");
  $("reqExplain").textContent = t(`explain_${request.status}`);
  const reason = request.decision?.reason;
  $("reqReason").hidden = !(request.status === "REJECTED" && reason);
  $("reqReason").textContent = reason ? `${t("reason_label")} ${reason}` : "";
  $("goSubmit").hidden = request.status !== "APPROVED";
  $("reapply").hidden = request.status !== "REJECTED";
  $("cancelReq").hidden = request.status !== "PENDING";
}

function showForm(previous = null) {
  const user = backend.currentUser();
  $("email").value = user.email ?? "";
  if (previous) {
    // Contact data is encrypted and cannot be read back; only the plain fields are refilled.
    $("publicName").value = previous.publicName ?? "";
    $("about").value = previous.about ?? "";
    document.querySelectorAll(".sample").forEach((input, i) => { input.value = previous.links?.[i] ?? ""; });
  }
  show("form");
}

$("reapply").addEventListener("click", () => showForm(current));
$("retryBtn").addEventListener("click", route);

$("cancelReq").addEventListener("click", (event) => {
  if (!confirm(t("cancel_confirm"))) return;
  busy(event.currentTarget, async () => {
    try {
      await backend.cancelRequest();
      current = null;
      await route();
    } catch (error) {
      flash(describe(error), true);
    }
  });
});

export function validPublicName(name) {
  return name.length >= 2 && name.length <= 40 && /^[\p{L}\p{N} ._-]+$/u.test(name) && !/\d[\d .-]{6,}\d/.test(name);
}

export function normalisePhone(raw) {
  let phone = raw.replace(/[\s().-]/g, "");
  if (/^0\d{9}$/.test(phone)) phone = `+84${phone.slice(1)}`; // Vietnamese local 09… → +849…
  return /^\+\d{8,15}$/.test(phone) ? phone : null;
}

$("requestForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("formErr").textContent = "";
  const publicName = $("publicName").value.trim().replace(/\s+/g, " ");
  const fullName = $("fullName").value.trim().replace(/\s+/g, " ");
  const phone = normalisePhone($("phone").value);
  const about = $("about").value.trim();
  const links = [...document.querySelectorAll(".sample")].map((i) => i.value.trim()).filter(Boolean);
  const boxes = Object.fromEntries([...event.currentTarget.querySelectorAll('input[type="checkbox"]')].map((b) => [b.name, b.checked]));

  const problem =
    (!validPublicName(publicName) && "e_public_name") ||
    ((fullName.length < 2 || fullName.length > 100) && "e_full_name") ||
    (!phone && "e_phone") ||
    ((about.length < 20 || about.length > 1000) && "e_about") ||
    (links.some((l) => !/^https?:\/\/\S{3,}$/i.test(l) || l.length > 500) && "e_links") ||
    (!(boxes.policy && boxes.terms && boxes.privacy) && "e_consents");
  if (problem) {
    $("formErr").textContent = t(problem);
    return;
  }

  await busy($("sendReq"), async () => {
    try {
      const user = backend.currentUser();
      const contactEnc = await encryptContact(CONTACT_PUBLIC_KEY, { fullName, email: user.email, phone });
      await backend.saveRequest({
        publicName,
        about,
        links,
        contactEnc,
        consents: { policy: POLICY_VERSIONS.policy, terms: POLICY_VERSIONS.terms, privacy: POLICY_VERSIONS.privacy },
      });
      event.target.reset();
      flash(t("sent"));
      await route();
    } catch (error) {
      $("formErr").textContent = describe(error);
    }
  });
});

backend.onUser(() => route());
applyLanguage();
