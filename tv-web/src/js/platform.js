/*
 * Platform layer: which TV this is, remote-control key codes, exit, screensaver.
 *
 * LG webOS: Back 461 (appinfo.json disableBackHistoryAPI so the app handles it).
 * Samsung Tizen: Back 10009; media/colour keys only arrive after tizen.tvinputdevice.registerKey.
 * Browser (development): arrows, Enter, Escape or Backspace as Back, PageUp/PageDown as CH+/CH-.
 */
(function (root) {
  'use strict';

  var ua = (root.navigator && root.navigator.userAgent) || '';
  var isTizen = typeof root.tizen !== 'undefined' || /Tizen/i.test(ua);
  var isWebOS = typeof root.PalmSystem !== 'undefined' || /Web0S|webOS/i.test(ua);

  var KEYS = {
    37: 'LEFT', 38: 'UP', 39: 'RIGHT', 40: 'DOWN', 13: 'OK',
    461: 'BACK', 10009: 'BACK', 27: 'BACK', 8: 'BACK',
    427: 'CH_UP', 33: 'CH_UP', 428: 'CH_DOWN', 34: 'CH_DOWN',
    415: 'PLAY', 19: 'PAUSE', 10252: 'PLAY_PAUSE', 413: 'STOP',
    403: 'RED', 457: 'INFO',
    65376: 'OK', 65385: 'BACK', // Tizen on-screen keyboard Done / Cancel
  };

  var TIZEN_KEYS = ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'ChannelUp', 'ChannelDown', 'ColorF0Red', 'Info'];

  function registerTizenKeys() {
    var input = root.tizen && root.tizen.tvinputdevice;
    if (!input) return;
    if (input.registerKeyBatch) {
      try { input.registerKeyBatch(TIZEN_KEYS); return; } catch (e) { /* fall back to one by one */ }
    }
    TIZEN_KEYS.forEach(function (k) {
      try { input.registerKey(k); } catch (e) { /* key not on this model */ }
    });
  }

  var Platform = {
    name: isTizen ? 'tizen' : (isWebOS ? 'webos' : 'browser'),
    isTizen: isTizen,
    isWebOS: isWebOS,

    init: function () {
      if (isTizen) registerTizenKeys();
    },

    /** The action for a keydown event, or null. Backspace inside a text field stays Backspace. */
    action: function (event) {
      var code = event.keyCode || event.which;
      var target = event.target;
      var typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA');
      if (typing && (code === 8 || code === 37 || code === 39)) return null;
      return KEYS[code] || null;
    },

    exit: function () {
      try {
        if (isTizen && root.tizen && root.tizen.application) {
          root.tizen.application.getCurrentApplication().exit();
          return;
        }
      } catch (e) { /* fall through */ }
      try { root.close(); } catch (e2) { /* browsers may refuse */ }
    },

    /** Keeps the TV screensaver away while video plays (Samsung requirement). */
    setScreenSaver: function (enabled) {
      var common = root.webapis && root.webapis.appcommon;
      if (!common || !common.setScreenSaver) return;
      try {
        var state = common.AppCommonScreenSaverState;
        common.setScreenSaver(enabled ? state.SCREEN_SAVER_ON : state.SCREEN_SAVER_OFF);
      } catch (e) { /* not available */ }
    },
  };

  root.Platform = Platform;
})(this);
