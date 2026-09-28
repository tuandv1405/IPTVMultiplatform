/* Format guides: copy buttons and cross-pane anchors. No dependencies.
   - Every <pre data-copy> gets a "Sao chép / Copy" button (label in its pane's language).
   - Anchors: the Vietnamese pane uses ids like "syntax", the English pane "syntax-en".
     A link to #syntax opened in English (or #syntax-en in Vietnamese) scrolls to the
     same section in the visible pane. Load after lang.js. */
(function () {
  "use strict";

  var LABELS = {
    vi: { copy: "Sao chép", done: "Đã chép", fail: "Không chép được", aria: "Sao chép đoạn mã" },
    en: { copy: "Copy", done: "Copied", fail: "Copy failed", aria: "Copy code" }
  };

  function paneOf(el) {
    return el.closest ? el.closest("[data-lang-pane]") : null;
  }

  function langOf(el) {
    var pane = paneOf(el);
    var lang = pane ? pane.getAttribute("data-lang-pane") : document.documentElement.lang;
    return LABELS[lang] ? lang : "en";
  }

  function fallbackCopy(text) {
    var area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.top = "0";
    area.style.left = "0";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.focus();
    area.select();
    area.setSelectionRange(0, text.length); // iOS Safari
    var ok = false;
    try {
      ok = document.execCommand("copy");
    } catch (e) {
      ok = false;
    }
    document.body.removeChild(area);
    return ok;
  }

  function copy(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(
        function () { return true; },
        function () { return fallbackCopy(text); }
      );
    }
    return Promise.resolve(fallbackCopy(text));
  }

  Array.prototype.forEach.call(document.querySelectorAll("pre[data-copy]"), function (pre) {
    var labels = LABELS[langOf(pre)];
    var bar = pre.previousElementSibling;
    if (!bar || !bar.classList.contains("codebar")) {
      bar = document.createElement("div");
      bar.className = "codebar";
      pre.parentNode.insertBefore(bar, pre);
    }
    var button = document.createElement("button");
    button.type = "button";
    button.className = "copy-btn";
    button.textContent = labels.copy;
    button.setAttribute("aria-label", labels.aria);
    var status = document.createElement("span");
    status.className = "visually-hidden";
    status.setAttribute("aria-live", "polite");
    status.style.position = "absolute";
    status.style.width = "1px";
    status.style.height = "1px";
    status.style.overflow = "hidden";
    status.style.clip = "rect(0 0 0 0)";
    var timer = null;
    button.addEventListener("click", function () {
      copy(pre.textContent.replace(/\n$/, "")).then(function (ok) {
        button.textContent = ok ? labels.done : labels.fail;
        status.textContent = button.textContent;
        clearTimeout(timer);
        timer = setTimeout(function () {
          button.textContent = labels.copy;
          status.textContent = "";
        }, 2000);
      });
    });
    bar.appendChild(button);
    bar.appendChild(status);
  });

  function followHash() {
    var id = decodeURIComponent(location.hash.replace(/^#/, ""));
    if (!id) return;
    var target = document.getElementById(id);
    if (!target) return;
    var pane = paneOf(target);
    if (!pane || !pane.hidden) return;
    var twin = document.getElementById(/-en$/.test(id) ? id.slice(0, -3) : id + "-en");
    if (twin && paneOf(twin) && !paneOf(twin).hidden) twin.scrollIntoView();
  }

  window.addEventListener("hashchange", followHash);
  followHash();
})();
