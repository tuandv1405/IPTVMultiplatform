/*
 * Local state in localStorage (each TV app has its own origin). Every read and write is guarded:
 * a full or unavailable storage must never stop the app.
 *
 * Keys: tsiptv.playlists, tsiptv.active, tsiptv.favorites, tsiptv.recent, tsiptv.settings,
 * tsiptv.cache.<playlistId> (parsed channels, skipped when too large).
 */
(function (root) {
  'use strict';

  var PREFIX = 'tsiptv.';
  var RECENT_MAX = 30;
  var CACHE_MAX_CHARS = 1500000; // ~3 MB as UTF-16; leaves room under the usual 5 MB quota.

  function read(key, fallback) {
    try {
      var raw = root.localStorage.getItem(PREFIX + key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch (e) {
      return fallback;
    }
  }

  function write(key, value) {
    try {
      root.localStorage.setItem(PREFIX + key, JSON.stringify(value));
      return true;
    } catch (e) {
      return false;
    }
  }

  function remove(key) {
    try { root.localStorage.removeItem(PREFIX + key); } catch (e) { /* ignore */ }
  }

  /** Stable key of a channel inside a playlist: favorites and history survive a refresh. */
  function channelKey(playlistId, channel) {
    return playlistId + '|' + channel.url;
  }

  var Store = {
    channelKey: channelKey,

    settings: function () { return read('settings', {}); },
    saveSettings: function (patch) {
      var s = Store.settings();
      Object.keys(patch).forEach(function (k) { s[k] = patch[k]; });
      write('settings', s);
      return s;
    },

    playlists: function () { return read('playlists', []); },
    addPlaylist: function (name, url) {
      var list = Store.playlists();
      var id = 'p' + Date.now().toString(36);
      list.push({ id: id, name: name, url: url, addedAt: Date.now() });
      write('playlists', list);
      return id;
    },
    removePlaylist: function (id) {
      write('playlists', Store.playlists().filter(function (p) { return p.id !== id; }));
      remove('cache.' + id);
      var prefix = id + '|';
      write('favorites', Store.favorites().filter(function (k) { return k.indexOf(prefix) !== 0; }));
      write('recent', Store.recent().filter(function (r) { return r.key.indexOf(prefix) !== 0; }));
      if (Store.activeId() === id) remove('active');
    },
    activeId: function () { return read('active', null); },
    setActive: function (id) { write('active', id); },

    cachedChannels: function (id) { return read('cache.' + id, null); },
    cacheChannels: function (id, channels) {
      var json = JSON.stringify(channels);
      if (json.length > CACHE_MAX_CHARS) { remove('cache.' + id); return false; }
      try {
        root.localStorage.setItem(PREFIX + 'cache.' + id, json);
        return true;
      } catch (e) {
        remove('cache.' + id);
        return false;
      }
    },

    favorites: function () { return read('favorites', []); },
    isFavorite: function (key) { return Store.favorites().indexOf(key) >= 0; },
    toggleFavorite: function (key) {
      var list = Store.favorites();
      var at = list.indexOf(key);
      if (at >= 0) list.splice(at, 1); else list.unshift(key);
      write('favorites', list);
      return at < 0;
    },

    /** Most recent first: [{ key, at }]. */
    recent: function () { return read('recent', []); },
    pushRecent: function (key) {
      var list = Store.recent().filter(function (r) { return r.key !== key; });
      list.unshift({ key: key, at: Date.now() });
      write('recent', list.slice(0, RECENT_MAX));
    },
  };

  root.Store = Store;
})(this);
