/* /support/ page: draws one card per payment method from window.TSIPTV_SUPPORT
   (assets/support-config.js) into #methods-vi and #methods-en. No dependencies, no inline code
   (CSP friendly), no third-party requests: QR codes are static images under /assets/support/.

   - A method shows only when enabled and filled in (no "CHANGE_ME" left in its required fields).
   - Preview mode (?preview=1, or localhost) shows every method, marking the unfilled ones
     "Not configured" with a placeholder QR. Visitors never see that state.
   - Config values are written with textContent / attributes only, never as HTML. Links must be
     https://; "Open app" links only use the schemes in APP_SCHEMES. */
(function () {
  "use strict";

  var CONFIG = window.TSIPTV_SUPPORT || { methods: [] };
  var PLACEHOLDER_QR = "/assets/support/qr-coming-soon.svg";
  var APP_SCHEMES = ["momo:", "zalopay:"];
  var params = new URLSearchParams(location.search);
  var PREVIEW = params.get("preview") === "1" ||
    location.hostname === "localhost" || location.hostname === "127.0.0.1";

  var UI = {
    vi: {
      copy: "Chép", copied: "Đã chép", copyFail: "Không chép được",
      copyAria: "Chép {label}",
      openApp: "Mở ứng dụng", openLink: "Mở trang", openWeb: "Mở trên web",
      notConfigured: "Chưa cấu hình", disabled: "Đang tắt",
      previewNote: "Chế độ xem trước: hiện cả các phương thức chưa điền thông tin. Khách truy cập không thấy chúng.",
      empty: "Các phương thức ủng hộ sẽ sớm có ở đây. Cảm ơn bạn đã ghé!",
      qrAlt: "Mã QR {name}",
      scopeVN: "Trong nước", scopeIntl: "Quốc tế", scopeBoth: "Trong nước và quốc tế",
      bank: "Ngân hàng", account: "Số tài khoản", holder: "Chủ tài khoản", branch: "Chi nhánh",
      note: "Nội dung chuyển khoản", phone: "Số điện thoại", name: "Tên tài khoản",
      address: "Địa chỉ ví", memo: "Memo / Tag", payId: "Binance Pay ID", link: "Liên kết",
      network: "Mạng", warnNetwork: "Chỉ gửi {asset} trên mạng {network}. Gửi sai mạng hoặc sai loại tiền có thể mất vĩnh viễn.",
      cryptoTip: "Mỗi địa chỉ chỉ nhận đúng loại tiền và đúng mạng ghi bên cạnh."
    },
    en: {
      copy: "Copy", copied: "Copied", copyFail: "Copy failed",
      copyAria: "Copy {label}",
      openApp: "Open app", openLink: "Open page", openWeb: "Open on the web",
      notConfigured: "Not configured", disabled: "Disabled",
      previewNote: "Preview mode: methods without details are shown too. Visitors do not see them.",
      empty: "Ways to support will be listed here soon. Thank you for stopping by!",
      qrAlt: "{name} QR code",
      scopeVN: "Vietnam", scopeIntl: "International", scopeBoth: "Vietnam and international",
      bank: "Bank", account: "Account number", holder: "Account holder", branch: "Branch",
      note: "Transfer note", phone: "Phone number", name: "Account name",
      address: "Wallet address", memo: "Memo / Tag", payId: "Binance Pay ID", link: "Link",
      network: "Network", warnNetwork: "Send only {asset} on {network}. Funds sent on another network or in another coin can be lost for good.",
      cryptoTip: "Each address accepts only the coin and network shown next to it."
    }
  };

  // Names stay as the services write them; descriptions per language.
  var METHODS = {
    vietqr: { name: "VietQR · Napas 247", icon: "bank", scope: "vn",
      vi: "Chuyển khoản nhanh 24/7 từ ứng dụng của bất kỳ ngân hàng nào tại Việt Nam: quét mã QR hoặc nhập số tài khoản.",
      en: "Instant 24/7 transfer from any Vietnamese banking app: scan the QR code or type the account number." },
    momo: { name: "MoMo", icon: "wallet", scope: "vn",
      vi: "Quét mã bằng ứng dụng MoMo hoặc chuyển tới số điện thoại.",
      en: "Scan the code with the MoMo app or send to the phone number." },
    zypage: { name: "Zypage", icon: "heart", scope: "vn",
      vi: "Ủng hộ qua trang nhà sáng tạo trên Zypage.",
      en: "Support through the creator page on Zypage." },
    crypto: { name: "Crypto", icon: "coin", scope: "intl",
      vi: "USDT, BTC hoặc ETH. Kiểm tra kỹ mạng trước khi gửi.",
      en: "USDT, BTC or ETH. Check the network carefully before you send." },
    zalopay: { name: "ZaloPay", icon: "wallet", scope: "vn",
      vi: "Quét mã bằng ZaloPay hoặc chuyển tới số điện thoại.",
      en: "Scan the code with ZaloPay or send to the phone number." },
    vnpay: { name: "VNPAY-QR", icon: "qr", scope: "vn",
      vi: "Quét bằng ứng dụng ngân hàng hoặc ví có hỗ trợ VNPAY-QR.",
      en: "Scan with a banking or wallet app that supports VNPAY-QR." },
    shopeepay: { name: "ShopeePay", icon: "wallet", scope: "vn",
      vi: "Quét mã bằng ứng dụng Shopee (ShopeePay).",
      en: "Scan the code with the Shopee app (ShopeePay)." },
    paypal: { name: "PayPal", icon: "card", scope: "intl",
      vi: "Thẻ quốc tế hoặc số dư PayPal.",
      en: "International cards or a PayPal balance." },
    kofi: { name: "Ko-fi", icon: "coffee", scope: "intl",
      vi: "Mời một ly cà phê qua Ko-fi (thẻ hoặc PayPal).",
      en: "Buy a coffee on Ko-fi (card or PayPal)." },
    bmac: { name: "Buy Me a Coffee", icon: "coffee", scope: "intl",
      vi: "Mời một ly cà phê bằng thẻ quốc tế.",
      en: "Buy a coffee with an international card." },
    github: { name: "GitHub Sponsors", icon: "heart", scope: "intl",
      vi: "Tài trợ một lần hoặc hằng tháng qua GitHub.",
      en: "One-time or monthly sponsorship through GitHub." },
    stripe: { name: "Stripe", icon: "card", scope: "intl",
      vi: "Thanh toán bằng thẻ quốc tế, Apple Pay hoặc Google Pay qua trang Stripe.",
      en: "Pay by card, Apple Pay or Google Pay on a Stripe page." },
    binance: { name: "Binance Pay", icon: "coin", scope: "intl",
      vi: "Gửi miễn phí giữa các tài khoản Binance bằng Pay ID.",
      en: "Free transfers between Binance accounts with the Pay ID." }
  };

  // Required fields; an array inside means "at least one of".
  var REQUIRED = {
    vietqr: ["bankName", "accountNumber", "accountName"],
    momo: [["phone", "link"]],
    zalopay: [["phone", "link"]],
    vnpay: ["qr"],
    shopeepay: [["phone", "qr"]],
    zypage: ["url"], paypal: ["url"], kofi: ["url"], bmac: ["url"], github: ["url"], stripe: ["url"],
    binance: ["payId"],
    crypto: []
  };

  // 24×24 outline icons (stroke = currentColor), drawn for this page.
  var ICONS = {
    bank: '<path d="M3 10h18L12 4z"/><path d="M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"/>',
    wallet: '<path d="M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/><path d="M4 7l11-3v3"/><path d="M16 13.5h.01"/>',
    heart: '<path d="M12 20.5 4.6 13.2A4.9 4.9 0 0 1 12 6.8a4.9 4.9 0 0 1 7.4 6.4z"/>',
    coin: '<circle cx="12" cy="12" r="8.5"/><path d="M9.5 8.5h4a2 2 0 0 1 0 4h-4h4.5a2 2 0 0 1 0 4h-4.5zM11 7v2M11 16v1.5"/>',
    qr: '<path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4z"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2"/>',
    card: '<rect x="3" y="5.5" width="18" height="13" rx="2"/><path d="M3 10h18M7 15h4"/>',
    coffee: '<path d="M4 9h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z"/><path d="M17 10h1.5a2.5 2.5 0 0 1 0 5H17"/><path d="M8 3v3M12 3v3"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>',
    external: '<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4"/>'
  };

  // Own keys only: ids such as "constructor" or "__proto__" must not match Object.prototype.
  function has(obj, key) {
    return typeof key === "string" && Object.prototype.hasOwnProperty.call(obj, key);
  }

  // A number typed without quotes (e.g. an account number) still counts; keep values in quotes
  // anyway, because long numbers lose digits and leading zeros as JavaScript numbers.
  function str(v) {
    return typeof v === "number" && isFinite(v) ? String(v) : v;
  }

  function filled(v) {
    v = str(v);
    return typeof v === "string" && v.trim() !== "" && v.indexOf("CHANGE_ME") === -1;
  }

  function isConfigured(m) {
    if (!has(REQUIRED, m.id)) return false;
    var req = REQUIRED[m.id];
    if (m.id === "crypto") {
      return (m.addresses || []).some(function (a) { return filled(a.address); });
    }
    if (m.url !== undefined && req.indexOf("url") !== -1 && !safeHttps(m.url)) return false;
    return req.every(function (field) {
      if (Array.isArray(field)) return field.some(function (f) { return filled(m[f]); });
      return filled(m[field]);
    });
  }

  function safeHttps(url) {
    if (!filled(url)) return null;
    try {
      var u = new URL(url);
      return u.protocol === "https:" ? u.href : null;
    } catch (e) {
      return null;
    }
  }

  function safeApp(url) {
    if (!filled(url)) return null;
    url = String(url);
    var scheme = url.split("//")[0].toLowerCase();
    return APP_SCHEMES.indexOf(scheme) !== -1 ? url : null;
  }

  function safeImage(path) {
    // Only our own static files.
    return filled(path) && /^\/assets\/support\/[\w.\-\/]+\.(png|svg|webp|jpe?g)$/i.test(path) &&
      path.indexOf("..") === -1 ? path : null;
  }

  function fmt(s, vars) {
    return s.replace(/\{(\w+)\}/g, function (_, k) { return vars[k] !== undefined ? vars[k] : ""; });
  }

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }

  function icon(name, cls) {
    var span = el("span", cls || "pay-icon");
    span.setAttribute("aria-hidden", "true");
    // Constant markup from ICONS only (never config values).
    span.innerHTML = '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" ' +
      'stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" focusable="false">' + ICONS[name] + "</svg>";
    return span;
  }

  // ---- clipboard (same approach as guides.js) ------------------------------------------------

  // Fixed at the top of the viewport so focusing it never scrolls the page; focus goes back to
  // the button that was pressed.
  function fallbackCopy(text, returnFocus) {
    var area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.setAttribute("aria-hidden", "true");
    area.className = "pay-copy-area";
    document.body.appendChild(area);
    area.focus();
    area.select();
    area.setSelectionRange(0, text.length);
    var ok = false;
    try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
    document.body.removeChild(area);
    if (returnFocus && returnFocus.focus) returnFocus.focus();
    return ok;
  }

  function copyText(text, returnFocus) {
    text = String(text);
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(
        function () { return true; },
        function () { return fallbackCopy(text, returnFocus); });
    }
    return Promise.resolve(fallbackCopy(text, returnFocus));
  }

  // Phones and tablets, where momo:// or zalopay:// can open an installed app.
  var MOBILE = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent || "") ||
    (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent || ""));

  var live = { vi: null, en: null };

  function announce(lang, msg) {
    var region = live[lang];
    if (!region) return;
    region.textContent = "";
    setTimeout(function () { region.textContent = msg; }, 30);
  }

  // ---- building blocks ------------------------------------------------------------------------

  function field(lang, label, value, opts) {
    opts = opts || {};
    var t = UI[lang];
    var row = el("div", "pay-field");
    row.appendChild(el("dt", null, label));
    var dd = el("dd");
    value = str(value);
    var unset = !filled(value);
    var code = el("code", "pay-value" + (unset ? " pay-unset" : ""), unset ? "CHANGE_ME" : value);
    if (opts.mono === false) code.className += " pay-text";
    dd.appendChild(code);
    if (!unset && opts.copy !== false) {
      var btn = el("button", "pay-copy");
      btn.type = "button";
      btn.setAttribute("aria-label", fmt(t.copyAria, { label: label }));
      btn.appendChild(icon("copy", "pay-btn-icon"));
      var lbl = el("span", null, t.copy);
      btn.appendChild(lbl);
      var timer = null;
      btn.addEventListener("click", function () {
        copyText(value, btn).then(function (ok) {
          lbl.textContent = ok ? t.copied : t.copyFail;
          btn.classList.toggle("is-done", ok);
          announce(lang, (ok ? t.copied : t.copyFail) + ": " + label);
          clearTimeout(timer);
          timer = setTimeout(function () {
            lbl.textContent = t.copy;
            btn.classList.remove("is-done");
          }, 2000);
        });
      });
      dd.appendChild(btn);
    }
    row.appendChild(dd);
    return row;
  }

  function qrFigure(lang, path, name) {
    var src = safeImage(path);
    if (!src && !PREVIEW) return null;
    var fig = el("figure", "pay-qr");
    var img = el("img");
    img.loading = "lazy";
    img.decoding = "async";
    img.width = 240;
    img.height = 240;
    img.alt = fmt(UI[lang].qrAlt, { name: name });
    img.addEventListener("error", function () {
      // A missing image: placeholder in preview, nothing for visitors.
      if (PREVIEW && img.getAttribute("src") !== PLACEHOLDER_QR) img.src = PLACEHOLDER_QR;
      else if (!PREVIEW) {
        var owner = fig.closest ? fig.closest(".pay-card") : null;
        fig.remove();
        // A card left with nothing to copy or open (QR-only methods such as VNPAY-QR) goes too.
        if (owner && !owner.querySelector(".pay-copy, .pay-actions a, .pay-coin")) {
          var grid = owner.parentNode;
          owner.remove();
          if (grid && !grid.querySelector(".pay-card") && grid.parentNode) {
            var lang2 = grid.parentNode.id === "methods-en" ? "en" : "vi";
            grid.parentNode.replaceChild(el("p", "card pay-empty", UI[lang2].empty), grid);
          }
        }
      }
    });
    img.src = src || PLACEHOLDER_QR;
    fig.appendChild(img);
    return fig;
  }

  function linkButton(href, text, primary) {
    var a = el("a", primary ? "btn pay-btn" : "copy-btn pay-btn-secondary");
    a.href = href;
    a.rel = "noopener noreferrer";
    a.target = "_blank";
    a.appendChild(el("span", null, text));
    a.appendChild(icon("external", "pay-btn-icon"));
    return a;
  }

  // "Open app" tries the app scheme; if the page is still visible after 1.5 s the app is not
  // installed (or the browser blocked it), so the web link opens in a new tab instead.
  // Shown only when there is a web fallback, or on a phone/tablet (see card()).
  function appButton(lang, appHref, webHref) {
    var a = el("a", "btn pay-btn", UI[lang].openApp);
    a.href = appHref;
    a.addEventListener("click", function () {
      if (!webHref) return;
      var t = setTimeout(function () {
        if (!document.hidden) window.open(webHref, "_blank", "noopener,noreferrer");
      }, 1500);
      document.addEventListener("visibilitychange", function once() {
        if (document.hidden) clearTimeout(t);
        document.removeEventListener("visibilitychange", once);
      });
    });
    return a;
  }

  // ---- cards ------------------------------------------------------------------------------------

  function card(lang, m) {
    var t = UI[lang];
    var meta = METHODS[m.id];
    var configured = isConfigured(m);
    var art = el("article", "card pay-card");
    var headingId = "pay-" + m.id + "-" + lang;
    art.setAttribute("aria-labelledby", headingId);
    art.id = "method-" + m.id + (lang === "en" ? "-en" : "");

    var head = el("div", "pay-head");
    head.appendChild(icon(meta.icon));
    var titles = el("div", "pay-titles");
    var h3 = el("h3", null, meta.name);
    h3.id = headingId;
    titles.appendChild(h3);
    var badges = el("div", "pay-badges");
    var scopeText = meta.scope === "vn" ? t.scopeVN : meta.scope === "intl" ? t.scopeIntl : t.scopeBoth;
    badges.appendChild(el("span", "badge pay-scope", scopeText));
    if (PREVIEW && !configured) badges.appendChild(el("span", "badge no", t.notConfigured));
    if (PREVIEW && !m.enabled) badges.appendChild(el("span", "badge partial", t.disabled));
    titles.appendChild(badges);
    head.appendChild(titles);
    art.appendChild(head);
    art.appendChild(el("p", "pay-desc", meta[lang]));

    var body = el("div", "pay-body");
    var dl = el("dl", "pay-fields");
    var fig = null;
    var actions = el("div", "pay-actions");

    switch (m.id) {
      case "vietqr":
        fig = qrFigure(lang, m.qr, meta.name);
        dl.appendChild(field(lang, t.bank, m.bankName, { copy: false }));
        dl.appendChild(field(lang, t.account, m.accountNumber));
        dl.appendChild(field(lang, t.holder, m.accountName));
        if (filled(m.branch)) dl.appendChild(field(lang, t.branch, m.branch, { copy: false }));
        if (filled(CONFIG.transferNote)) dl.appendChild(field(lang, t.note, CONFIG.transferNote));
        break;
      case "momo":
      case "zalopay":
      case "shopeepay":
        fig = qrFigure(lang, m.qr, meta.name);
        if (filled(m.phone) || PREVIEW) dl.appendChild(field(lang, t.phone, m.phone));
        if (filled(m.accountName) || PREVIEW) dl.appendChild(field(lang, t.name, m.accountName, { copy: false }));
        if (filled(CONFIG.transferNote) && m.id !== "shopeepay") dl.appendChild(field(lang, t.note, CONFIG.transferNote));
        var web = safeHttps(m.link);
        // "Open app" needs a web fallback, or a phone/tablet where the app can be installed.
        var app = (web || MOBILE) ? safeApp(m.appLink) : null;
        if (app && (configured || PREVIEW)) actions.appendChild(appButton(lang, app, web));
        if (web) actions.appendChild(linkButton(web, t.openWeb, !app));
        break;
      case "vnpay":
        fig = qrFigure(lang, m.qr, meta.name);
        if (filled(m.accountName) || PREVIEW) dl.appendChild(field(lang, t.name, m.accountName, { copy: false }));
        break;
      case "binance":
        fig = qrFigure(lang, m.qr, meta.name);
        dl.appendChild(field(lang, t.payId, m.payId));
        if (filled(m.accountName) || PREVIEW) dl.appendChild(field(lang, t.name, m.accountName, { copy: false }));
        break;
      case "crypto":
        art.appendChild(el("p", "pay-note", t.cryptoTip));
        var list = el("ul", "pay-coins");
        (m.addresses || []).forEach(function (a) {
          if (!filled(a.address) && !PREVIEW) return;
          var li = el("li", "pay-coin");
          var top = el("div", "pay-coin-head");
          top.appendChild(el("strong", "pay-asset", a.asset));
          top.appendChild(el("span", "badge pay-network", a.network));
          li.appendChild(top);
          var inner = el("div", "pay-body");
          var cfig = qrFigure(lang, a.qr, a.asset + " · " + a.network);
          if (cfig) { cfig.className += " pay-qr-small"; inner.appendChild(cfig); }
          var cdl = el("dl", "pay-fields");
          cdl.appendChild(field(lang, t.address, a.address));
          if (filled(a.memo)) cdl.appendChild(field(lang, t.memo, a.memo));
          inner.appendChild(cdl);
          li.appendChild(inner);
          var warn = el("p", "pay-warn", fmt(t.warnNetwork, { asset: a.asset, network: a.network }));
          warn.setAttribute("role", "note");
          li.appendChild(warn);
          list.appendChild(li);
        });
        art.appendChild(list);
        return art;
      default: // link-only methods: zypage, paypal, kofi, bmac, github, stripe
        var url = safeHttps(m.url);
        dl.appendChild(field(lang, t.link, url || m.url, { copy: !!url }));
        if (url) actions.appendChild(linkButton(url, t.openLink, true));
    }

    if (fig) body.appendChild(fig);
    if (dl.childNodes.length) body.appendChild(dl);
    if (body.childNodes.length) art.appendChild(body);
    if (actions.childNodes.length) art.appendChild(actions);
    return art;
  }

  function render(lang) {
    var host = document.getElementById("methods-" + lang);
    if (!host) return;
    host.textContent = "";
    var region = el("p", "pay-offscreen");
    region.setAttribute("aria-live", "polite");
    live[lang] = region;
    host.appendChild(region);

    if (PREVIEW) {
      var note = el("div", "card callout pay-preview");
      note.appendChild(el("p", null, UI[lang].previewNote));
      host.appendChild(note);
    }
    var grid = el("div", "pay-grid");
    var count = 0;
    (CONFIG.methods || []).forEach(function (m) {
      // One bad entry never blanks the page: it is skipped (and logged in preview).
      try {
        if (!m || typeof m !== "object" || !has(METHODS, m.id)) return;
        var show = PREVIEW || (m.enabled === true && isConfigured(m));
        if (!show) return;
        grid.appendChild(card(lang, m));
        count++;
      } catch (e) {
        if (PREVIEW && window.console) console.warn("support-config: skipped a method", e);
      }
    });
    if (count) host.appendChild(grid);
    else host.appendChild(el("p", "card pay-empty", UI[lang].empty));
  }

  render("vi");
  render("en");
})();
