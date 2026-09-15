/* Language toggle shared by every page.
   Panes are marked with data-lang-pane="vi|en"; buttons with data-lang.
   The choice is remembered per browser, and ?lang=en wins over the stored value
   so the Play Console listing can deep-link straight to the English text. */
(function () {
  "use strict";

  var buttons = Array.prototype.slice.call(document.querySelectorAll("[data-lang]"));
  var panes = Array.prototype.slice.call(document.querySelectorAll("[data-lang-pane]"));
  if (!buttons.length || !panes.length) return;

  var KEY = "tsiptv-lang";

  function stored() {
    try {
      return localStorage.getItem(KEY);
    } catch (e) {
      return null; // private window, or site data blocked
    }
  }

  function remember(lang) {
    try {
      localStorage.setItem(KEY, lang);
    } catch (e) {
      /* preference simply is not carried to the next visit */
    }
  }

  function apply(lang) {
    panes.forEach(function (pane) {
      pane.hidden = pane.getAttribute("data-lang-pane") !== lang;
    });
    buttons.forEach(function (button) {
      button.setAttribute("aria-pressed", String(button.getAttribute("data-lang") === lang));
    });
    document.documentElement.lang = lang;
  }

  buttons.forEach(function (button) {
    button.addEventListener("click", function () {
      var lang = button.getAttribute("data-lang");
      remember(lang);
      apply(lang);
    });
  });

  var known = panes.map(function (p) { return p.getAttribute("data-lang-pane"); });
  var requested = new URLSearchParams(location.search).get("lang");
  var initial = [requested, stored(), known[0]].find(function (lang) {
    return lang && known.indexOf(lang) !== -1;
  });

  apply(initial);
})();
