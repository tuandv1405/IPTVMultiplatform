/* /contributor/submit/ — playlist upload. Only ACTIVE contributors get the form;
 * everyone else sees why, and the backend (rules / server) refuses them anyway. */
import { getBackend, normaliseUrl } from "/assets/contributor-backend.js";
import { POLICY_VERSIONS } from "/assets/contributor-config.js";
import {
  $, CATEGORIES, LANGUAGES, addText, applyLanguage, busy, categoryText, describe, el, fillSelect, flash, formatDate,
  languageName, onLanguageChange, prepareImage, reasonText, show, statusText, t, wireAccount,
} from "/assets/contributor-ui.js";

addText({
  vi: {
    title: "Đăng playlist", hello: "Xin chào",
    locked_title: "Chỉ dành cho người đóng góp",
    locked_none: "Tài khoản này chưa là người đóng góp. Hãy gửi yêu cầu và chờ quản trị viên duyệt.",
    locked_pending: "Yêu cầu trở thành người đóng góp của bạn đang chờ duyệt.",
    locked_suspended: "Tài khoản người đóng góp của bạn đang bị đình chỉ nên không thể đăng playlist.",
    locked_cta: "Đăng ký trở thành người đóng góp", locked_cta_status: "Xem trạng thái yêu cầu",
    new_title: "Gửi danh sách phát mới", f_name: "Tên danh sách",
    f_url: "Đường dẫn danh sách (M3U, M3U8, XSPF, JSON)", check_link: "Kiểm tra",
    f_image: "Hình ảnh (PNG, JPEG hoặc WebP)", f_desc: "Mô tả (không bắt buộc)", f_category: "Thể loại", f_language: "Ngôn ngữ",
    decl_title: "Cam kết về danh sách này",
    decl_own_work: "Tôi tự tạo ra và duy trì danh sách này.",
    decl_not_copied: "Danh sách không được sao chép, thu thập hay lấy cắp từ người khác hoặc dịch vụ khác.",
    decl_rights_ok: "Tôi có quyền chia sẻ mọi đường dẫn trong đó, và hiểu rằng danh sách vi phạm bản quyền sẽ bị gỡ bỏ bất cứ lúc nào.",
    submit: "Gửi để kiểm duyệt", submit_hint: "Bấm “Kiểm tra” đường dẫn trước khi gửi.",
    submitted: "Đã gửi! Danh sách đang ở trạng thái ĐANG DUYỆT.",
    verify_ok: "Đường dẫn hợp lệ", verify_channels: "kênh",
    verify_unchecked: "Không kiểm tra được từ trình duyệt (máy chủ của đường dẫn chặn truy cập chéo). Bạn vẫn gửi được; quản trị viên sẽ kiểm tra khi duyệt.",
    mine_title: "Danh sách của tôi", mine_empty: "Bạn chưa gửi danh sách nào.",
    withdraw: "Rút lại", withdraw_confirm: "Rút lại danh sách này? Nếu đã công khai, nó sẽ bị gỡ khỏi thư mục.",
    view_public: "Xem công khai", sent_on: "Gửi", channels: "kênh",
    e_name: "Tên danh sách dài 3–80 ký tự.", e_category: "Hãy chọn thể loại.", e_language: "Hãy chọn ngôn ngữ.",
    e_image: "Hãy chọn hình ảnh cho danh sách.", e_decl: "Hãy đánh dấu cả ba cam kết.",
  },
  en: {
    title: "Upload a playlist", hello: "Hello",
    locked_title: "Contributors only",
    locked_none: "This account is not a contributor yet. Send a request and wait for an admin to approve it.",
    locked_pending: "Your request to become a contributor is waiting for review.",
    locked_suspended: "Your contributor account is suspended, so you cannot upload playlists.",
    locked_cta: "Apply to become a contributor", locked_cta_status: "See your request",
    new_title: "Submit a new playlist", f_name: "Playlist name",
    f_url: "Playlist link (M3U, M3U8, XSPF, JSON)", check_link: "Check",
    f_image: "Image (PNG, JPEG or WebP)", f_desc: "Description (optional)", f_category: "Category", f_language: "Language",
    decl_title: "Declarations for this playlist",
    decl_own_work: "I created and maintain this playlist myself.",
    decl_not_copied: "It is not copied, scraped or taken from another person or service.",
    decl_rights_ok: "I have the right to share every link in it, and I understand infringing playlists are removed at any time.",
    submit: "Submit for review", submit_hint: "Press “Check” on the link before submitting.",
    submitted: "Submitted! The playlist is now IN REVIEW.",
    verify_ok: "Link works", verify_channels: "channels",
    verify_unchecked: "Could not be checked from the browser (the link's server blocks cross-origin reads). You can still submit; the admin checks it during review.",
    mine_title: "My playlists", mine_empty: "You have not submitted any playlist yet.",
    withdraw: "Withdraw", withdraw_confirm: "Withdraw this playlist? If it is public it will be removed from the directory.",
    view_public: "View public page", sent_on: "Sent", channels: "channels",
    e_name: "The playlist name must be 3–80 characters.", e_category: "Choose a category.", e_language: "Choose a language.",
    e_image: "Choose an image for the playlist.", e_decl: "Tick all three declarations.",
  },
});

const backend = await getBackend();
wireAccount(backend);

let contributor = null;
let verified = null; // { url, result } of the last successful check
let image = null;
let submissions = [];

function fillSelects() {
  fillSelect($("plCategory"), CATEGORIES.map((c) => [c, categoryText(c)]));
  fillSelect($("plLanguage"), LANGUAGES.map((l) => [l, languageName(l)]));
}

onLanguageChange(() => {
  if (formRemoved) return;
  fillSelects();
  renderSubmissions();
});

async function route() {
  flash("");
  const user = backend.currentUser();
  if (!user) return show("signin");
  show("loading");
  let request = null;
  try {
    contributor = await backend.getMyContributor();
    if (!contributor) request = await backend.getMyRequest();
  } catch (error) {
    $("retryMsg").textContent = describe(error);
    return show("retry");
  }
  if (!contributor || contributor.status !== "ACTIVE") {
    const reason = contributor ? "locked_suspended" : request?.status === "PENDING" ? "locked_pending" : "locked_none";
    $("lockedMsg").textContent = t(reason);
    $("lockedCta").hidden = reason === "locked_suspended";
    $("lockedCta").textContent = t(request ? "locked_cta_status" : "locked_cta");
    // Not hidden but removed: a non-contributor gets no upload form at all.
    // (Firestore rules / the server refuse the writes regardless.)
    document.querySelector('[data-state="form"]')?.remove();
    formRemoved = true;
    return show("locked");
  }
  $("publicNameOut").textContent = contributor.publicName;
  fillSelects();
  show("form");
  await loadSubmissions();
}

function updateSubmitState() {
  const ok = verified !== null && verified.url === $("plUrl").value.trim();
  $("submitBtn").disabled = !ok;
  $("submitHint").hidden = ok;
}

$("plUrl").addEventListener("input", () => {
  if (verified && verified.url !== $("plUrl").value.trim()) {
    verified = null;
    $("verifyResult").hidden = true;
  }
  updateSubmitState();
});

$("checkLink").addEventListener("click", (event) =>
  busy(event.currentTarget, async () => {
    const url = $("plUrl").value.trim();
    const box = $("verifyResult");
    verified = null;
    box.hidden = false;
    box.replaceChildren();
    try {
      normaliseUrl(url);
      const result = await backend.checkLink(url);
      if (result.ok && result.checked) {
        box.className = "verify ok";
        box.textContent = `✓ ${t("verify_ok")} · ${String(result.format).toUpperCase()} · ${result.channelCount} ${t("verify_channels")}`;
        verified = { url, result };
      } else if (result.ok && !result.checked) {
        box.className = "verify warnbox";
        box.textContent = `⚠ ${t("verify_unchecked")}`;
        verified = { url, result };
      } else {
        box.className = "verify bad";
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
  image = null;
  $("plPreview").hidden = true;
  $("submitErr").textContent = "";
  const file = $("plImage").files[0];
  if (!file) return;
  try {
    image = await prepareImage(file);
    $("plPreview").src = image.dataUrl;
    $("plPreview").hidden = false;
  } catch (error) {
    $("submitErr").textContent = describe(error);
    $("plImage").value = "";
  }
});

$("submitForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  $("submitErr").textContent = "";
  const name = $("plName").value.trim().replace(/\s+/g, " ");
  const declarations = Object.fromEntries([...form.querySelectorAll('input[type="checkbox"]')].map((b) => [b.name, b.checked]));
  const problem =
    ((name.length < 3 || name.length > 80) && "e_name") ||
    (!$("plCategory").value && "e_category") ||
    (!$("plLanguage").value && "e_language") ||
    (!image && "e_image") ||
    (!(declarations.own_work && declarations.not_copied && declarations.rights_ok) && "e_decl");
  if (problem) {
    $("submitErr").textContent = t(problem);
    return;
  }
  await busy($("submitBtn"), async () => {
    try {
      const r = verified.result;
      await backend.submitPlaylist({
        name,
        url: $("plUrl").value.trim(),
        description: $("plDesc").value.trim(),
        category: $("plCategory").value,
        language: $("plLanguage").value,
        image: { mime: image.mime, data: image.data },
        declarations,
        policyVersion: POLICY_VERSIONS.policy,
        verification: r.checked
          ? { ok: true, method: r.method, format: r.format ?? null, channelCount: r.channelCount ?? null, groupCount: r.groupCount ?? null }
          : { ok: null, method: r.method, format: null, channelCount: null, groupCount: null },
      });
      form.reset();
      image = null;
      verified = null;
      $("plPreview").hidden = true;
      $("verifyResult").hidden = true;
      flash(t("submitted"));
      await loadSubmissions();
    } catch (error) {
      $("submitErr").textContent = describe(error);
    }
  });
  updateSubmitState();
});

async function loadSubmissions() {
  try {
    submissions = await backend.listMySubmissions();
    renderSubmissions();
  } catch (error) {
    flash(describe(error), true);
  }
}

function renderSubmissions() {
  $("mineEmpty").hidden = submissions.length > 0;
  $("mine").replaceChildren(...submissions.map((s) => {
    const count = s.verification?.channelCount != null ? ` · ${s.verification.channelCount} ${t("channels")}` : "";
    const li = el("li", {},
      el("div", { className: "top" },
        el("div", { className: "name", text: s.name }),
        el("span", { className: `badge ${s.status}`, text: statusText(s.status) })),
      el("div", { className: "meta", text: `${s.url}${count} · ${t("sent_on")} ${formatDate(s.createdAt)} · ${s.id}` }));
    const code = s.statusReason?.code;
    if (code && !["APPROVED", "IN_REVIEW"].includes(s.status)) {
      li.append(el("p", { className: "reason", text: reasonText(code) + (s.statusReason.note ? ` (${s.statusReason.note})` : "") }));
    }
    const actions = el("div", { className: "row" });
    if (s.status === "APPROVED") {
      actions.append(el("a", { className: "ghost", text: t("view_public"), attrs: { href: `/playlists/#${encodeURIComponent(s.id)}` } }));
    }
    if (s.status === "IN_REVIEW" || s.status === "APPROVED") {
      const withdraw = el("button", { className: "ghost", text: t("withdraw"), attrs: { type: "button" } });
      withdraw.addEventListener("click", () => {
        if (!confirm(t("withdraw_confirm"))) return;
        busy(withdraw, async () => {
          try {
            await backend.withdrawSubmission(s);
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
  }));
}

$("retryBtn").addEventListener("click", route);
let formRemoved = false;
let firstAuthState = true;
backend.onUser(() => {
  // After the form was removed, another account needs a fresh page to get it back.
  if (formRemoved && !firstAuthState) return location.reload();
  firstAuthState = false;
  route();
});
applyLanguage();
