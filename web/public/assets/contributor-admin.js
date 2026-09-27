/* /contributor/admin/ — review requests, contributors and playlists.
 * Admin rights come from firestore.rules (or the server); this page only
 * mirrors them. The contact private key is read from a local file into memory
 * and is never stored or sent anywhere. */
import { getBackend } from "/assets/contributor-backend.js";
import { decryptContact, importPrivateKey, keyId } from "/assets/contact-crypto.js";
import { CONTACT_PUBLIC_KEY } from "/assets/contributor-config.js";
import {
  $, addText, applyLanguage, busy, categoryText, describe, el, fillSelect, flash, formatDate, languageName,
  onLanguageChange, reasonText, show, statusText, t, wireAccount,
} from "/assets/contributor-ui.js";

addText({
  vi: {
    title: "Quản trị người đóng góp",
    admin_signin: "Đăng nhập bằng tài khoản Google của chủ sở hữu dự án Firebase.",
    denied: "Tài khoản {email} không phải quản trị viên.",
    load_key: "Nạp khoá riêng…", forget_key: "Quên khoá",
    key_none: "🔒 Chưa nạp khoá riêng — họ tên, email và số điện thoại đang được mã hoá.",
    key_loaded: "🔓 Đã nạp khoá riêng {kid}. Khoá chỉ nằm trong bộ nhớ của tab này.",
    key_wrong: "Tệp này không phải khoá riêng khớp với khoá công khai đang dùng.",
    tab_requests: "Yêu cầu", tab_contributors: "Người đóng góp", tab_playlists: "Playlist",
    reload: "Tải lại", empty: "Không có mục nào.", all: "Tất cả",
    approve: "Duyệt", reject: "Từ chối", confirm: "Xác nhận", takedown: "Gỡ xuống",
    suspend: "Đình chỉ", reinstate: "Khôi phục", open_link: "Mở", copy_link: "Sao chép",
    reject_reason_ph: "Lý do (người dùng sẽ thấy)", note_ph: "Ghi chú (không bắt buộc)",
    contact_locked: "Liên hệ: đã mã hoá", contact_error: "Không giải mã được liên hệ",
    full_name: "Họ tên", email: "Email", phone: "SĐT", about: "Giới thiệu", links: "Đường dẫn mẫu",
    consents: "Đồng ý", attempt: "lần", sent: "Gửi", decided: "Quyết định", by: "bởi",
    approved_on: "Duyệt ngày", declarations: "Cam kết", unverified_link: "Link chưa kiểm tra được từ trình duyệt — hãy tự mở thử.",
    channels: "kênh", confirm_takedown: "Gỡ playlist này khỏi thư mục công khai?",
    confirm_suspend: "Đình chỉ người đóng góp này? Họ sẽ không đăng được playlist mới.",
    done: "Đã cập nhật.", copied: "Đã sao chép.",
    self_reported: "trình duyệt của người gửi tự báo — hãy tự kiểm tra", server_checked: "máy chủ đã kiểm tra",
  },
  en: {
    title: "Contributor admin",
    admin_signin: "Sign in with the Google account that owns the Firebase project.",
    denied: "{email} is not an admin.",
    load_key: "Load private key…", forget_key: "Forget key",
    key_none: "🔒 No private key loaded — full names, emails and phone numbers stay encrypted.",
    key_loaded: "🔓 Private key {kid} loaded. It lives only in this tab's memory.",
    key_wrong: "This file is not the private key matching the configured public key.",
    tab_requests: "Requests", tab_contributors: "Contributors", tab_playlists: "Playlists",
    reload: "Reload", empty: "Nothing here.", all: "All",
    approve: "Approve", reject: "Reject", confirm: "Confirm", takedown: "Take down",
    suspend: "Suspend", reinstate: "Reinstate", open_link: "Open", copy_link: "Copy",
    reject_reason_ph: "Reason (shown to the user)", note_ph: "Note (optional)",
    contact_locked: "Contact: encrypted", contact_error: "Could not decrypt the contact",
    full_name: "Name", email: "Email", phone: "Phone", about: "About", links: "Sample links",
    consents: "Consents", attempt: "attempt", sent: "Sent", decided: "Decided", by: "by",
    approved_on: "Approved", declarations: "Declarations", unverified_link: "The link could not be checked from the browser — open it yourself.",
    channels: "channels", confirm_takedown: "Take this playlist down from the public directory?",
    confirm_suspend: "Suspend this contributor? They will not be able to upload new playlists.",
    done: "Updated.", copied: "Copied.",
    self_reported: "reported by the submitter's browser — check it yourself", server_checked: "checked by the server",
  },
});

const REJECT_CODES = ["copyright", "broken", "inappropriate", "incomplete", "duplicate", "other"];
const FILTERS = {
  requests: ["PENDING", "APPROVED", "REJECTED"],
  contributors: ["ACTIVE", "SUSPENDED"],
  playlists: ["IN_REVIEW", "APPROVED", "REJECTED", "REMOVED", "WITHDRAWN"],
};

const backend = await getBackend();
wireAccount(backend);

let tab = "requests";
let items = [];
let privateKey = null;
let privateKid = null;
const contactCache = new Map();

const fmt = (key, vars) => t(key).replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? "");
const safeHref = (url) => (/^https?:\/\//i.test(url) ? url : null);

function renderKeyBar() {
  $("keyStatus").textContent = privateKey ? fmt("key_loaded", { kid: privateKid }) : t("key_none");
  $("forgetKey").hidden = !privateKey;
}

$("keyFile").addEventListener("change", async () => {
  const file = $("keyFile").files[0];
  $("keyFile").value = "";
  if (!file) return;
  try {
    const jwk = JSON.parse(await file.text());
    const { kid, ...keyOnly } = jwk;
    // The private JWK carries the public modulus; it must be the configured key.
    if (keyOnly.n !== CONTACT_PUBLIC_KEY.n) throw new Error("mismatch");
    privateKey = await importPrivateKey(keyOnly);
    privateKid = kid ?? (await keyId(CONTACT_PUBLIC_KEY));
    contactCache.clear();
    renderKeyBar();
    render();
  } catch {
    flash(t("key_wrong"), true);
  }
});

$("forgetKey").addEventListener("click", () => {
  privateKey = null;
  privateKid = null;
  contactCache.clear();
  renderKeyBar();
  render();
});

function fillFilter() {
  const select = $("filter");
  const previous = select.value;
  fillSelect(select, FILTERS[tab].map((s) => [s, statusText(s)]), t("all"));
  select.value = FILTERS[tab].includes(previous) ? previous : FILTERS[tab][0];
}

document.querySelectorAll("[data-tab]").forEach((button) => {
  button.addEventListener("click", () => {
    tab = button.dataset.tab;
    document.querySelectorAll("[data-tab]").forEach((b) => b.setAttribute("aria-selected", String(b === button)));
    $("filter").value = "";
    fillFilter();
    load();
  });
});
$("filter").addEventListener("change", load);
$("reload").addEventListener("click", load);
onLanguageChange(() => {
  renderKeyBar();
  fillFilter();
  render();
});

let shownFor;

async function route() {
  flash("");
  const user = backend.currentUser();
  // Signing out (or switching account) drops the key, decrypted contacts and cards.
  if ((user?.uid ?? null) !== shownFor) {
    shownFor = user?.uid ?? null;
    privateKey = null;
    privateKid = null;
    contactCache.clear();
    items = [];
    $("items").replaceChildren();
  }
  if (!user) return show("signin");
  show("loading");
  if (!(await backend.isAdmin())) {
    $("deniedMsg").textContent = fmt("denied", { email: user.email ?? user.uid });
    return show("denied");
  }
  renderKeyBar();
  fillFilter();
  show("console");
  await load();
}

async function load() {
  const status = $("filter").value || undefined;
  try {
    items =
      tab === "requests" ? await backend.listRequests(status)
      : tab === "contributors" ? (await backend.listContributors()).filter((c) => !status || c.status === status)
      : await backend.listSubmissions(status);
    render();
  } catch (error) {
    flash(describe(error), true);
  }
}

function render() {
  $("empty").hidden = items.length > 0;
  const renderer = tab === "requests" ? requestCard : tab === "contributors" ? contributorCard : playlistCard;
  $("items").replaceChildren(...items.map(renderer));
}

async function act(button, work) {
  await busy(button, async () => {
    try {
      await work();
      flash(t("done"));
      await load();
    } catch (error) {
      flash(describe(error), true);
    }
  });
}

const button = (label, onClick, className = "ghost") => {
  const b = el("button", { className, text: label, attrs: { type: "button" } });
  b.addEventListener("click", () => onClick(b));
  return b;
};

/** Reason / note entry that replaces the action row until confirmed or cancelled. */
function inlineConfirm(row, { select, placeholder, onConfirm }) {
  const input = select
    ? el("select", {}, ...REJECT_CODES.map((c) => new Option(reasonText(c), c)))
    : el("input", { attrs: { type: "text", maxlength: "300", placeholder } });
  const note = select ? el("input", { attrs: { type: "text", maxlength: "300", placeholder: t("note_ph") } }) : null;
  const saved = [...row.childNodes];
  const restore = () => row.replaceChildren(...saved);
  row.replaceChildren(input, note, button(t("confirm"), (b) => onConfirm(b, input.value, note?.value ?? ""), "btn danger-btn"), button(t("cancel"), restore));
  input.focus();
}

// ---------------------------------------------------------------- cards ----

function requestCard(r) {
  const li = el("li", {},
    el("div", { className: "top" },
      el("div", { className: "name", text: r.publicName }),
      el("span", { className: `badge ${r.status}`, text: statusText(r.status) })),
    el("div", { className: "meta", text: `uid ${r.uid} · ${t("sent")} ${formatDate(r.createdAt)} · ${t("attempt")} ${r.attempt ?? 1}` }),
    el("p", { className: "reason", text: `${t("about")}: ${r.about}` }));

  if (r.links?.length) {
    const links = el("p", { className: "reason", text: `${t("links")}: ` });
    r.links.forEach((link, i) => {
      const href = safeHref(link);
      links.append(href ? el("a", { text: link, attrs: { href, target: "_blank", rel: "noopener noreferrer nofollow" } }) : el("span", { text: link }));
      if (i < r.links.length - 1) links.append(" · ");
    });
    li.append(links);
  }
  const c = r.consents ?? {};
  li.append(el("div", { className: "meta", text: `${t("consents")}: policy ${c.policy} · terms ${c.terms} · privacy ${c.privacy} · ${formatDate(c.acceptedAt)}` }));
  if (r.decision) {
    li.append(el("div", { className: "meta", text: `${t("decided")} ${formatDate(r.decision.at)} ${t("by")} ${r.decision.name ?? r.decision.by}${r.decision.reason ? ` — ${r.decision.reason}` : ""}` }));
  }

  const contact = el("div", { className: "contact", text: t("contact_locked") });
  li.append(contact);
  if (privateKey) revealContact(r, contact);

  if (r.status === "PENDING") {
    const row = el("div", { className: "row" });
    row.append(
      button(t("approve"), (b) => act(b, () => backend.decideRequest(r, "APPROVED")), "btn"),
      button(t("reject"), () => inlineConfirm(row, {
        placeholder: t("reject_reason_ph"),
        onConfirm: (b, reason) => act(b, () => backend.decideRequest(r, "REJECTED", reason.trim().slice(0, 500))),
      })),
    );
    li.append(row);
  }
  return li;
}

async function revealContact(request, node) {
  try {
    if (!contactCache.has(request.uid)) contactCache.set(request.uid, decryptContact(privateKey, request.contactEnc));
    const info = await contactCache.get(request.uid);
    node.className = "contact open";
    node.textContent = `${t("full_name")}: ${info.fullName} · ${t("email")}: ${info.email} · ${t("phone")}: ${info.phone}`;
  } catch {
    contactCache.delete(request.uid);
    node.textContent = t("contact_error");
  }
}

function contributorCard(c) {
  const li = el("li", {},
    el("div", { className: "top" },
      el("div", { className: "name", text: c.publicName }),
      el("span", { className: `badge ${c.status}`, text: statusText(c.status) })),
    el("div", { className: "meta", text: `uid ${c.uid} · ${t("approved_on")} ${formatDate(c.approvedAt)}${c.statusNote ? ` · ${c.statusNote}` : ""}` }));
  const row = el("div", { className: "row" });
  if (c.status === "ACTIVE") {
    row.append(button(t("suspend"), () => inlineConfirm(row, {
      placeholder: t("note_ph"),
      onConfirm: (b, note) => {
        if (!confirm(t("confirm_suspend"))) return;
        act(b, () => backend.setContributorStatus(c, "SUSPENDED", note.trim().slice(0, 300)));
      },
    })));
  } else {
    row.append(button(t("reinstate"), (b) => act(b, () => backend.setContributorStatus(c, "ACTIVE", ""))));
  }
  li.append(row);
  return li;
}

function playlistCard(s) {
  const thumb = el("img", { className: "admin-thumb", attrs: { alt: "" } });
  backend.getSubmissionImage(s.id).then((img) => {
    if (img && /^image\/(png|jpeg|webp)$/.test(img.mime)) thumb.src = `data:${img.mime};base64,${img.data}`;
  }).catch(() => {});

  const v = s.verification ?? {};
  const verifyText = v.ok ? `${String(v.format).toUpperCase()} · ${v.channelCount} ${t("channels")} (${v.method === "browser" ? t("self_reported") : t("server_checked")})` : t("unverified_link");
  const href = safeHref(s.url);
  const linkRow = el("div", { className: "row" });
  if (href) linkRow.append(el("a", { className: "ghost", text: t("open_link"), attrs: { href, target: "_blank", rel: "noopener noreferrer nofollow" } }));
  linkRow.append(button(t("copy_link"), async () => {
    try { await navigator.clipboard.writeText(s.url); flash(t("copied")); } catch { prompt(t("copy_link"), s.url); }
  }));

  const li = el("li", { className: "with-thumb" },
    thumb,
    el("div", {},
      el("div", { className: "top" },
        el("div", { className: "name", text: s.name }),
        el("span", { className: `badge ${s.status}`, text: statusText(s.status) })),
      el("div", { className: "meta", text: `${s.url}` }),
      el("div", { className: "meta", text: `${s.publicName} · uid ${s.ownerUid} · ${categoryText(s.category)} · ${languageName(s.language)} · ${formatDate(s.createdAt)} · ${s.id}` }),
      el("div", { className: "meta", text: verifyText }),
      s.description ? el("p", { className: "reason", text: s.description }) : null,
      s.statusReason?.code ? el("p", { className: "reason", text: reasonText(s.statusReason.code) + (s.statusReason.note ? ` (${s.statusReason.note})` : "") }) : null,
      linkRow));

  const row = el("div", { className: "row" });
  if (s.status === "IN_REVIEW") {
    row.append(
      button(t("approve"), (b) => act(b, () => backend.decideSubmission(s, "approve")), "btn"),
      button(t("reject"), () => inlineConfirm(row, {
        select: true,
        onConfirm: (b, code, note) => act(b, () => backend.decideSubmission(s, "reject", { code, note })),
      })),
    );
  } else if (s.status === "APPROVED") {
    row.append(button(t("takedown"), () => inlineConfirm(row, {
      placeholder: t("note_ph"),
      onConfirm: (b, note) => {
        if (!confirm(t("confirm_takedown"))) return;
        act(b, () => backend.decideSubmission(s, "takedown", { note: note.trim().slice(0, 300) }));
      },
    })));
  }
  if (row.childElementCount) li.lastChild.append(row);
  return li;
}

backend.onUser(() => route());
applyLanguage();
