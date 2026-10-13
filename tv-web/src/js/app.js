/*
 * TS IPTV for TV: screens, remote-control focus, playlists, player.
 *
 * Screens: disclaimer (first launch) -> home (groups + channel grid) <-> player.
 * Dialogs (add playlist, playlists, confirm) sit on top and own the keys while open.
 * Focus on home is by zone and index ('header' buttons, 'groups' list, 'grid' of channels);
 * dialogs move focus geometrically between their focusable elements.
 *
 * Kept to what Chromium 53 (webOS 4, 2018) runs: no async/await, no Object.entries/values.
 */
(function (root) {
  'use strict';

  var doc = root.document;
  var t = I18n.t;
  var $ = function (id) { return doc.getElementById(id); };

  var GRID_COLS = 5;
  var OSD_HIDE_MS = 5000;
  var LOAD_TIMEOUT_MS = 30000;

  var state = {
    screen: null, // 'home' | 'player'
    playlist: null, // { id, name, url }
    channels: [], // parsed channels, each with .key
    byKey: {},
    groups: [], // [{ id, label, items: [channel] }]
    groupIndex: 0, // selected group
    zone: 'grid',
    focus: { header: 0, groups: 0, grid: 0 },
    playing: null, // { list, index }
    osdIndex: 0,
    loadingPlaylist: false,
  };

  var dialogStack = []; // { el, onBack, focusables(), focusedEl }

  // ------------------------------------------------------------------ helpers

  function el(tag, cls, text) {
    var node = doc.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  function each(list, fn) { Array.prototype.forEach.call(list, fn); }

  function initials(name) {
    // No \p{L}: Unicode property escapes are a syntax error before Chromium 64.
    var words = String(name).replace(/[^0-9A-Za-zÀ-ɏḀ-ỿ ]/g, ' ').trim().split(/\s+/);
    var s = (words[0] || '').charAt(0) + (words[1] || '').charAt(0);
    return (s || '?').toUpperCase();
  }

  var toastTimer = null;
  function toast(message) {
    var box = $('toast');
    box.textContent = message;
    box.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { box.hidden = true; }, 2600);
  }

  function applyI18n() {
    doc.documentElement.lang = I18n.lang;
    each(doc.querySelectorAll('[data-i18n]'), function (node) { node.textContent = t(node.getAttribute('data-i18n')); });
    $('home-hint').textContent = t('hintHome');
    $('player-hint').textContent = t('hintPlayer');
  }

  // ------------------------------------------------------------------ playlist loading

  function download(url) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', url, true);
      xhr.timeout = LOAD_TIMEOUT_MS;
      xhr.onload = function () {
        // status 0 with a body happens for file:// and some TV webviews.
        if ((xhr.status >= 200 && xhr.status < 300) || (xhr.status === 0 && xhr.responseText)) resolve(xhr.responseText);
        else reject(new Error(xhr.status ? t('httpError') + ' ' + xhr.status : t('networkError')));
      };
      xhr.onerror = function () { reject(new Error(t('networkError'))); };
      xhr.ontimeout = function () { reject(new Error(t('networkError'))); };
      xhr.send();
    });
  }

  function fetchChannels(url) {
    return download(url).then(function (text) {
      if (!M3U.looksLikeM3u(text)) throw new Error(t('notM3u'));
      var channels = M3U.parse(text, url).channels;
      if (!channels.length) throw new Error(t('noChannels'));
      return channels;
    });
  }

  function setChannels(playlist, channels) {
    state.playlist = playlist;
    state.byKey = {};
    state.channels = channels.map(function (c) {
      c.key = Store.channelKey(playlist.id, c);
      state.byKey[c.key] = c;
      return c;
    });
    buildGroups();
    $('playlist-title').textContent = playlist.name + ' · ' + state.channels.length + ' ' + t('channels');
  }

  /** Opens the active playlist: cached channels at once, then a silent refresh from the network. */
  function openActivePlaylist() {
    var lists = Store.playlists();
    var id = Store.activeId();
    var playlist = lists.filter(function (p) { return p.id === id; })[0] || lists[0] || null;
    if (!playlist) {
      state.playlist = null;
      state.channels = [];
      buildGroups();
      $('playlist-title').textContent = '';
      showGridMessage(t('emptyTitle'), t('emptyBody'));
      setZone('header', 1);
      return;
    }
    Store.setActive(playlist.id);
    var cached = Store.cachedChannels(playlist.id);
    if (cached && cached.length) {
      setChannels(playlist, cached);
      renderHome();
    } else {
      state.channels = [];
      buildGroups();
      $('playlist-title').textContent = playlist.name;
      showGridMessage('', '<span class="spinner"></span>' + t('loading'), true);
    }
    state.loadingPlaylist = true;
    fetchChannels(playlist.url).then(function (channels) {
      state.loadingPlaylist = false;
      Store.cacheChannels(playlist.id, channels);
      if (Store.activeId() !== playlist.id) return; // another playlist was chosen meanwhile
      var unchanged = cached && cached.length && JSON.stringify(cached.map(stripKey)) === JSON.stringify(channels);
      if (unchanged) return;
      setChannels(playlist, channels);
      if (state.screen === 'home') renderHome();
    }, function (err) {
      state.loadingPlaylist = false;
      if (cached && cached.length) return; // keep the cached copy; the network may come back
      if (state.screen === 'home' && Store.activeId() === playlist.id) {
        showGridMessage(t('loadFailed'), err.message);
        setZone('header', 0);
      }
    });
  }

  function stripKey(c) {
    var copy = {};
    Object.keys(c).forEach(function (k) { if (k !== 'key') copy[k] = c[k]; });
    return copy;
  }

  // ------------------------------------------------------------------ groups and grid

  function buildGroups() {
    var groups = [];
    var favKeys = Store.favorites();
    groups.push({ id: 'fav', label: t('groupFavorites'), items: favKeys.map(function (k) { return state.byKey[k]; }).filter(Boolean), empty: t('noFavorites') });
    groups.push({ id: 'recent', label: t('groupRecent'), items: Store.recent().map(function (r) { return state.byKey[r.key]; }).filter(Boolean), empty: t('noRecent') });
    groups.push({ id: 'all', label: t('groupAll'), items: state.channels });
    var named = {};
    var order = [];
    var other = [];
    state.channels.forEach(function (c) {
      if (!c.group) { other.push(c); return; }
      if (!named[c.group]) { named[c.group] = []; order.push(c.group); }
      named[c.group].push(c);
    });
    order.forEach(function (g) { groups.push({ id: 'g:' + g, label: g, items: named[g] }); });
    if (order.length && other.length) groups.push({ id: 'other', label: t('groupOther'), items: other });

    var previous = state.groups[state.groupIndex];
    state.groups = groups;
    var keep = previous ? groups.map(function (g) { return g.id; }).indexOf(previous.id) : -1;
    state.groupIndex = keep >= 0 ? keep : 2; // "All" by default
    state.focus.groups = state.groupIndex;
  }

  function currentItems() {
    var g = state.groups[state.groupIndex];
    return g ? g.items : [];
  }

  function renderGroups() {
    var nav = $('groups');
    nav.innerHTML = '';
    state.groups.forEach(function (g, i) {
      var row = el('div', 'group' + (i === state.groupIndex ? ' selected' : ''));
      row.setAttribute('data-zone', 'groups');
      row.setAttribute('data-index', i);
      row.appendChild(el('span', 'label', g.label));
      row.appendChild(el('span', 'count', String(g.items.length)));
      nav.appendChild(row);
    });
    scrollGroups();
  }

  function scrollGroups() {
    var nav = $('groups');
    var row = nav.children[state.focus.groups];
    if (!row) return;
    var top = row.offsetTop;
    var bottom = top + row.offsetHeight;
    if (top < nav.scrollTop) nav.scrollTop = top;
    else if (bottom > nav.scrollTop + nav.clientHeight) nav.scrollTop = bottom - nav.clientHeight;
  }

  function showGridMessage(title, body, isHtml) {
    $('grid-inner').innerHTML = '';
    var box = $('grid-message');
    box.innerHTML = '';
    if (title) box.appendChild(el('h2', null, title));
    var p = el('div');
    if (isHtml) p.innerHTML = body; else p.textContent = body;
    box.appendChild(p);
    box.hidden = false;
  }

  var gridMetrics = null;
  function measureGrid() {
    var grid = $('grid');
    var width = grid.clientWidth;
    var gap = width * 0.012;
    var colW = (width - gap * (GRID_COLS - 1)) / GRID_COLS;
    var rowH = colW * 0.7;
    gridMetrics = { colW: colW, rowH: rowH, gap: gap, visibleRows: Math.max(1, Math.floor((grid.clientHeight + gap) / (rowH + gap))) };
  }

  var gridTopRow = 0;
  function renderGrid() {
    var items = currentItems();
    var inner = $('grid-inner');
    var msg = $('grid-message');
    if (!state.playlist) return;
    if (!items.length) {
      var g = state.groups[state.groupIndex];
      if (state.loadingPlaylist && !state.channels.length) showGridMessage('', '<span class="spinner"></span>' + t('loading'), true);
      else showGridMessage('', (g && g.empty) || t('noChannels'));
      return;
    }
    msg.hidden = true;
    if (!gridMetrics) measureGrid();
    var m = gridMetrics;
    var focusIndex = Math.min(state.focus.grid, items.length - 1);
    state.focus.grid = focusIndex;
    var focusRow = Math.floor(focusIndex / GRID_COLS);
    if (focusRow < gridTopRow) gridTopRow = focusRow;
    if (focusRow >= gridTopRow + m.visibleRows) gridTopRow = focusRow - m.visibleRows + 1;
    var totalRows = Math.ceil(items.length / GRID_COLS);
    gridTopRow = Math.max(0, Math.min(gridTopRow, Math.max(0, totalRows - m.visibleRows)));

    var favs = Store.favorites();
    var first = gridTopRow * GRID_COLS;
    var last = Math.min(items.length, (gridTopRow + m.visibleRows + 1) * GRID_COLS);
    inner.innerHTML = '';
    inner.style.height = (totalRows * (m.rowH + m.gap)) + 'px';
    inner.style.transform = 'translateY(' + (-gridTopRow * (m.rowH + m.gap)) + 'px)';
    for (var i = first; i < last; i++) inner.appendChild(card(items[i], i, favs));
    updateFocusClasses();
  }

  function card(c, i, favs) {
    var m = gridMetrics;
    var node = el('div', 'card' + (c.drm ? ' drm' : ''));
    node.setAttribute('data-zone', 'grid');
    node.setAttribute('data-index', i);
    node.style.left = ((i % GRID_COLS) * (m.colW + m.gap)) + 'px';
    node.style.top = (Math.floor(i / GRID_COLS) * (m.rowH + m.gap)) + 'px';
    node.style.width = m.colW + 'px';
    node.style.height = m.rowH + 'px';
    var thumb = el('div', 'thumb');
    if (c.logo) {
      var img = el('img');
      img.alt = '';
      img.onerror = function () { thumb.innerHTML = ''; thumb.appendChild(el('div', 'initials', initials(c.name))); };
      img.src = c.logo;
      thumb.appendChild(img);
    } else {
      thumb.appendChild(el('div', 'initials', initials(c.name)));
    }
    node.appendChild(thumb);
    node.appendChild(el('div', 'name', c.name));
    node.appendChild(el('div', 'num', String(c.number != null ? c.number : i + 1)));
    if (favs.indexOf(c.key) >= 0) node.appendChild(el('div', 'star', '★'));
    return node;
  }

  function renderHome() {
    renderGroups();
    renderGrid();
    updateFocusClasses();
  }

  // ------------------------------------------------------------------ home focus

  function setZone(zone, index) {
    state.zone = zone;
    if (index != null) state.focus[zone] = index;
    updateFocusClasses();
  }

  function updateFocusClasses() {
    if (state.screen !== 'home' || dialogStack.length) return;
    each(doc.querySelectorAll('#home .focused'), function (n) { n.classList.remove('focused'); });
    var target = doc.querySelector('#home [data-zone="' + state.zone + '"][data-index="' + state.focus[state.zone] + '"]');
    if (target) target.classList.add('focused');
  }

  var groupRenderTimer = null;
  function selectGroup(index) {
    state.focus.groups = index;
    each($('groups').children, function (row, i) { row.classList.toggle('selected', i === index); });
    scrollGroups();
    updateFocusClasses();
    // Rendering a big group on every key press would lag when the remote key repeats.
    clearTimeout(groupRenderTimer);
    groupRenderTimer = setTimeout(function () {
      if (state.groupIndex !== index) {
        state.groupIndex = index;
        state.focus.grid = 0;
        gridTopRow = 0;
        renderGrid();
      }
    }, 180);
  }

  function homeKey(action) {
    var z = state.zone;
    var f = state.focus;
    var items = currentItems();
    if (action === 'BACK') { confirmExit(); return; }

    if (z === 'header') {
      if (action === 'LEFT') setZone('header', Math.max(0, f.header - 1));
      else if (action === 'RIGHT') setZone('header', Math.min(2, f.header + 1));
      else if (action === 'DOWN') { if (state.groups.length && state.playlist) setZone(items.length ? 'grid' : 'groups'); }
      else if (action === 'OK') activateHeader(f.header);
      return;
    }

    if (z === 'groups') {
      if (action === 'UP') { if (f.groups > 0) selectGroup(f.groups - 1); else setZone('header'); }
      else if (action === 'DOWN') { if (f.groups < state.groups.length - 1) selectGroup(f.groups + 1); }
      else if (action === 'RIGHT' || action === 'OK') {
        flushGroupSelection();
        if (currentItems().length) setZone('grid');
      }
      return;
    }

    // grid
    if (!items.length) { if (action === 'LEFT') setZone('groups'); else if (action === 'UP') setZone('header'); return; }
    var i = f.grid;
    var col = i % GRID_COLS;
    if (action === 'LEFT') { if (col === 0) setZone('groups', state.groupIndex); else moveGrid(i - 1); }
    else if (action === 'RIGHT') { if (col < GRID_COLS - 1 && i + 1 < items.length) moveGrid(i + 1); }
    else if (action === 'UP') { if (i - GRID_COLS >= 0) moveGrid(i - GRID_COLS); else setZone('header'); }
    else if (action === 'DOWN') { if (i + GRID_COLS < items.length) moveGrid(i + GRID_COLS); else if (Math.floor(i / GRID_COLS) < Math.floor((items.length - 1) / GRID_COLS)) moveGrid(items.length - 1); }
    else if (action === 'CH_DOWN') moveGrid(Math.min(items.length - 1, i + GRID_COLS * gridMetrics.visibleRows));
    else if (action === 'CH_UP') moveGrid(Math.max(0, i - GRID_COLS * gridMetrics.visibleRows));
    else if (action === 'OK' || action === 'PLAY') playFromList(items, i);
    else if (action === 'RED') toggleFavorite(items[i]);
  }

  function flushGroupSelection() {
    clearTimeout(groupRenderTimer);
    if (state.groupIndex !== state.focus.groups) {
      state.groupIndex = state.focus.groups;
      state.focus.grid = 0;
      gridTopRow = 0;
      renderGrid();
    }
  }

  function moveGrid(index) {
    state.focus.grid = index;
    var m = gridMetrics;
    var row = Math.floor(index / GRID_COLS);
    if (row < gridTopRow || row >= gridTopRow + m.visibleRows) renderGrid();
    else updateFocusClasses();
  }

  function activateHeader(index) {
    if (index === 0) openPlaylistsDialog();
    else if (index === 1) openAddDialog();
    else {
      Store.saveSettings({ lang: I18n.toggle() });
      applyI18n();
      buildGroups();
      if (state.playlist) $('playlist-title').textContent = state.playlist.name + ' · ' + state.channels.length + ' ' + t('channels');
      if (state.playlist) renderHome(); else { renderGroups(); showGridMessage(t('emptyTitle'), t('emptyBody')); }
      updateFocusClasses();
    }
  }

  function toggleFavorite(channel) {
    if (!channel) return;
    var added = Store.toggleFavorite(channel.key);
    toast(added ? t('favoriteAdded') : t('favoriteRemoved'));
    var focusedGroup = state.groups[state.groupIndex];
    buildGroups();
    if (state.screen === 'home') {
      renderGroups();
      if (focusedGroup && focusedGroup.id === 'fav') state.focus.grid = Math.min(state.focus.grid, Math.max(0, currentItems().length - 1));
      renderGrid();
    }
    return added;
  }

  // ------------------------------------------------------------------ player

  var osdTimer = null;

  function showScreen(name) {
    state.screen = name;
    $('home').hidden = name !== 'home';
    $('player-ui').hidden = name !== 'player';
    doc.body.classList.toggle('playing', name === 'player');
    doc.documentElement.classList.toggle('playing', name === 'player');
  }

  function playFromList(list, index) {
    state.playing = { list: list.slice(), index: index };
    showScreen('player');
    startPlayback();
  }

  function startPlayback() {
    var p = state.playing;
    var c = p.list[p.index];
    $('osd-number').textContent = c.number != null ? c.number : p.index + 1;
    $('osd-name').textContent = c.name;
    $('osd-group').textContent = c.group || '';
    $('osd-fav').classList.toggle('on', Store.isFavorite(c.key));
    showOsd();
    Store.pushRecent(c.key);
    if (c.drm) {
      Player.stop();
      setStatus(t('drmUnsupported'), true);
      return;
    }
    Player.play(c);
    Platform.setScreenSaver(false);
  }

  function setStatus(text, isError, spinner) {
    var box = $('osd-status');
    if (!text) { box.hidden = true; return; }
    box.innerHTML = '';
    if (spinner) box.appendChild(el('span', 'spinner'));
    box.appendChild(doc.createTextNode(text));
    box.className = 'osd-status' + (isError ? ' error' : '');
    box.hidden = false;
  }

  function onPlayerState(s, detail) {
    if (state.screen !== 'player') return;
    if (s === 'loading') setStatus(t('buffering'), false, true);
    else if (s === 'playing') { setStatus(null); setPauseIcon(false); }
    else if (s === 'paused') setPauseIcon(true);
    else if (s === 'error') {
      setStatus(t('playFailed'), true);
      showOsd(true);
      if (root.console) root.console.warn('playback error', detail);
    } else if (s === 'ended') showOsd(true);
  }

  function setPauseIcon(isPaused) {
    doc.querySelector('[data-osd="pause"]').textContent = isPaused ? '▶' : '❚❚';
  }

  function osdButtons() { return doc.querySelectorAll('#player-ui [data-osd]'); }

  function showOsd(sticky) {
    $('osd').classList.remove('hidden');
    each(osdButtons(), function (b, i) { b.classList.toggle('focused', i === state.osdIndex); });
    clearTimeout(osdTimer);
    if (!sticky) osdTimer = setTimeout(hideOsd, OSD_HIDE_MS);
  }

  function hideOsd() { $('osd').classList.add('hidden'); }

  function osdVisible() { return !$('osd').classList.contains('hidden'); }

  function zap(delta) {
    var p = state.playing;
    if (!p || p.list.length < 2) return;
    p.index = (p.index + delta + p.list.length) % p.list.length;
    startPlayback();
  }

  function leavePlayer() {
    Player.stop();
    Platform.setScreenSaver(true);
    clearTimeout(osdTimer);
    setStatus(null);
    var p = state.playing;
    showScreen('home');
    buildGroups(); // "Recently watched" changed
    if (p) {
      // Back on the channel that was playing, in the group that is shown.
      var key = p.list[p.index].key;
      var at = currentItems().map(function (c) { return c.key; }).indexOf(key);
      if (at >= 0) { state.focus.grid = at; state.zone = 'grid'; }
    }
    renderHome();
  }

  function activateOsd(index) {
    var p = state.playing;
    var which = osdButtons()[index].getAttribute('data-osd');
    if (which === 'fav') {
      var added = toggleFavorite(p.list[p.index]);
      $('osd-fav').classList.toggle('on', !!added);
    } else if (which === 'pause') {
      Player.setPaused(!Player.isPaused());
    } else if (which === 'list') {
      leavePlayer();
    }
  }

  function playerKey(action) {
    var visible = osdVisible();
    switch (action) {
      case 'BACK': if (visible) hideOsd(); else leavePlayer(); return;
      case 'STOP': leavePlayer(); return;
      case 'UP': case 'CH_UP': zap(1); return;
      case 'DOWN': case 'CH_DOWN': zap(-1); return;
      case 'OK':
        if (visible) { activateOsd(state.osdIndex); showOsd(); } else showOsd();
        return;
      case 'INFO': if (visible) hideOsd(); else showOsd(); return;
      case 'LEFT': case 'RIGHT':
        if (visible) {
          var count = osdButtons().length;
          state.osdIndex = (state.osdIndex + (action === 'RIGHT' ? 1 : -1) + count) % count;
          showOsd();
        } else if (!Player.seekBy(action === 'RIGHT' ? 10 : -10)) {
          showOsd();
        }
        return;
      case 'PLAY': Player.setPaused(false); showOsd(); return;
      case 'PAUSE': Player.setPaused(true); showOsd(true); return;
      case 'PLAY_PAUSE': Player.setPaused(!Player.isPaused()); showOsd(); return;
      case 'RED': {
        var p = state.playing;
        var added = toggleFavorite(p.list[p.index]);
        $('osd-fav').classList.toggle('on', !!added);
        showOsd();
        return;
      }
      default:
    }
  }

  // ------------------------------------------------------------------ dialogs

  function openDialog(content, options) {
    var overlay = $('overlay');
    var box = el('div', 'dialog');
    box.appendChild(content);
    overlay.innerHTML = '';
    overlay.appendChild(box);
    overlay.hidden = false;
    var entry = { el: box, onBack: options.onBack, focusedEl: null };
    dialogStack.push(entry);
    each(doc.querySelectorAll('#home .focused'), function (n) { n.classList.remove('focused'); });
    focusInDialog(options.initial || box.querySelector('input, button'));
    return entry;
  }

  function closeDialog() {
    var entry = dialogStack.pop();
    var overlay = $('overlay');
    if (entry && entry.el.parentNode) overlay.removeChild(entry.el);
    if (!dialogStack.length) {
      overlay.hidden = true;
      if (doc.activeElement && doc.activeElement.blur) doc.activeElement.blur();
      updateFocusClasses();
    } else {
      // Show the dialog below again.
      var top = dialogStack[dialogStack.length - 1];
      overlay.innerHTML = '';
      overlay.appendChild(top.el);
      focusInDialog(top.focusedEl);
    }
  }

  function topDialog() { return dialogStack[dialogStack.length - 1]; }

  function focusInDialog(node) {
    var d = topDialog();
    if (!d || !node) return;
    each(d.el.querySelectorAll('.focused'), function (n) { n.classList.remove('focused'); });
    node.classList.add('focused');
    d.focusedEl = node;
    // A field is only highlighted: OK opens the TV keyboard (see dialogKey).
    if (doc.activeElement && doc.activeElement.tagName === 'INPUT' && doc.activeElement !== node) doc.activeElement.blur();
    // Keep the focused row visible in a scrolling list.
    var list = node.closest && node.closest('.pl-list');
    if (list) {
      var row = node.closest('.pl-row') || node;
      if (row.offsetTop < list.scrollTop) list.scrollTop = row.offsetTop;
      else if (row.offsetTop + row.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = row.offsetTop + row.offsetHeight - list.clientHeight;
    }
  }

  /** Nearest focusable in the pressed direction, preferring alignment on the other axis. */
  function moveInDialog(action) {
    var d = topDialog();
    var from = d.focusedEl;
    var nodes = Array.prototype.filter.call(d.el.querySelectorAll('input, button'), function (n) { return !n.disabled; });
    if (!from) { focusInDialog(nodes[0]); return; }
    var a = from.getBoundingClientRect();
    var ax = a.left + a.width / 2;
    var ay = a.top + a.height / 2;
    var best = null;
    var bestScore = Infinity;
    nodes.forEach(function (n) {
      if (n === from) return;
      var b = n.getBoundingClientRect();
      var bx = b.left + b.width / 2;
      var by = b.top + b.height / 2;
      var dx = bx - ax;
      var dy = by - ay;
      var main;
      var cross;
      // Vertical moves compare left edges: Down from a wide field lands on the first button of a row.
      if (action === 'UP') { main = -dy; cross = Math.abs(b.left - a.left); }
      else if (action === 'DOWN') { main = dy; cross = Math.abs(b.left - a.left); }
      else if (action === 'LEFT') { main = -dx; cross = Math.abs(dy); }
      else { main = dx; cross = Math.abs(dy); }
      if (main <= 1) return;
      var score = main + cross * 2;
      if (score < bestScore) { bestScore = score; best = n; }
    });
    if (best) focusInDialog(best);
  }

  function dialogKey(action, event) {
    var d = topDialog();
    var focused = d.focusedEl;
    var typing = focused && focused.tagName === 'INPUT' && doc.activeElement === focused;
    if (action === 'BACK') {
      if (typing) { focused.blur(); event.preventDefault(); return; } // close the keyboard only
      if (d.onBack) d.onBack(); else closeDialog();
      return;
    }
    if (action === 'OK') {
      if (focused && focused.tagName === 'INPUT') {
        // OK on a field opens the TV keyboard; OK again (or the keyboard's Done) moves on.
        if (!typing) focused.focus();
        else { focused.blur(); moveInDialog('DOWN'); }
        event.preventDefault();
        return;
      }
      if (focused) focused.click();
      return;
    }
    if (action === 'UP' || action === 'DOWN' || action === 'LEFT' || action === 'RIGHT') {
      moveInDialog(action);
      event.preventDefault();
    }
  }

  function button(label, cls, onClick) {
    var b = el('button', 'btn' + (cls ? ' ' + cls : ''), label);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function openAddDialog() {
    var wrap = el('div');
    wrap.appendChild(el('h2', null, t('addPlaylist')));
    wrap.appendChild(el('label', null, t('name')));
    var name = el('input');
    name.type = 'text';
    name.placeholder = t('namePlaceholder');
    wrap.appendChild(name);
    wrap.appendChild(el('label', null, t('url')));
    var url = el('input');
    url.type = 'url';
    url.placeholder = t('urlPlaceholder');
    url.autocapitalize = 'off';
    url.spellcheck = false;
    wrap.appendChild(url);
    var error = el('div', 'error');
    wrap.appendChild(error);
    var actions = el('div', 'actions');
    var busy = false;
    var save = button(t('save'), 'primary', function () {
      if (busy) return;
      var link = url.value.trim();
      if (!/^https?:\/\/\S+$/i.test(link)) { error.textContent = t('invalidUrl'); focusInDialog(url); return; }
      busy = true;
      error.innerHTML = '<span class="spinner"></span>' + t('loading');
      fetchChannels(link).then(function (channels) {
        busy = false;
        var label = name.value.trim() || link.replace(/^https?:\/\//i, '').split(/[/?#]/)[0];
        var id = Store.addPlaylist(label, link);
        Store.setActive(id);
        Store.cacheChannels(id, channels);
        closeDialog();
        state.groups = [];
        setChannels({ id: id, name: label, url: link }, channels);
        state.zone = 'grid';
        state.focus.grid = 0;
        gridTopRow = 0;
        renderHome();
      }, function (err) {
        busy = false;
        error.textContent = t('loadFailed') + ': ' + err.message;
      });
    });
    actions.appendChild(save);
    actions.appendChild(button(t('cancel'), null, closeDialog));
    wrap.appendChild(actions);
    openDialog(wrap, { initial: url });
  }

  function openPlaylistsDialog() {
    var wrap = el('div');
    wrap.appendChild(el('h2', null, t('playlists')));
    var list = el('div', 'pl-list');
    var active = Store.activeId();
    var playlists = Store.playlists();
    if (!playlists.length) list.appendChild(el('p', null, t('emptyBody')));
    playlists.forEach(function (p) {
      var row = el('div', 'pl-row' + (p.id === active ? ' active' : ''));
      var text = el('div', 'pl-text');
      text.appendChild(el('div', 'pl-name', p.name));
      text.appendChild(el('div', 'pl-url', p.url));
      row.appendChild(text);
      row.appendChild(button(t('use'), null, function () {
        closeDialog();
        Store.setActive(p.id);
        state.groups = [];
        state.zone = 'grid';
        state.focus.grid = 0;
        gridTopRow = 0;
        openActivePlaylist();
      }));
      row.appendChild(button(t('refresh'), null, function () {
        closeDialog();
        Store.setActive(p.id);
        Store.cacheChannels(p.id, []);
        state.groups = [];
        openActivePlaylist();
      }));
      row.appendChild(button(t('delete'), 'danger', function () {
        confirmDialog(t('confirmDelete'), p.name, t('delete'), function () {
          Store.removePlaylist(p.id);
          closeDialog(); // the playlists dialog
          state.groups = [];
          openActivePlaylist();
          if (state.playlist) renderHome();
        });
      }));
      list.appendChild(row);
    });
    wrap.appendChild(list);
    var actions = el('div', 'actions');
    var add = button(t('addPlaylist'), 'primary', function () { closeDialog(); openAddDialog(); });
    actions.appendChild(add);
    actions.appendChild(button(t('close'), null, closeDialog));
    wrap.appendChild(actions);
    openDialog(wrap, { initial: list.querySelector('.pl-row.active button') || add });
  }

  function confirmDialog(title, body, confirmLabel, onConfirm) {
    var wrap = el('div');
    wrap.appendChild(el('h2', null, title));
    if (body) wrap.appendChild(el('p', null, body));
    var actions = el('div', 'actions');
    var cancel = button(t('cancel'), null, closeDialog);
    actions.appendChild(button(confirmLabel, 'danger', function () { closeDialog(); onConfirm(); }));
    actions.appendChild(cancel);
    wrap.appendChild(actions);
    openDialog(wrap, { initial: cancel });
  }

  function confirmExit() {
    var wrap = el('div');
    wrap.appendChild(el('h2', null, t('exitTitle')));
    var actions = el('div', 'actions');
    var stay = button(t('stay'), null, closeDialog);
    actions.appendChild(button(t('exit'), 'danger', function () { Platform.exit(); }));
    actions.appendChild(stay);
    wrap.appendChild(actions);
    openDialog(wrap, { initial: stay });
  }

  function showDisclaimer(then) {
    var wrap = el('div');
    wrap.appendChild(el('h2', null, t('disclaimerTitle')));
    wrap.appendChild(el('p', null, t('disclaimerBody')));
    var actions = el('div', 'actions');
    var ok = button(t('disclaimerOk'), 'primary', function () {
      Store.saveSettings({ disclaimerAccepted: true });
      closeDialog();
      then();
    });
    actions.appendChild(ok);
    wrap.appendChild(actions);
    openDialog(wrap, { initial: ok, onBack: confirmExit });
  }

  // ------------------------------------------------------------------ input

  function onKeyDown(event) {
    var action = Platform.action(event);
    if (!action) return;
    if (dialogStack.length) { dialogKey(action, event); return; }
    event.preventDefault();
    if (state.screen === 'player') playerKey(action);
    else if (state.screen === 'home') homeKey(action);
  }

  /** LG Magic Remote and mouse: hovering focuses, clicking activates. */
  function bindPointer() {
    doc.addEventListener('mouseover', function (e) {
      if (dialogStack.length) {
        var d = topDialog();
        var target = e.target.closest && e.target.closest('input, button');
        if (target && d.el.contains(target) && target !== d.focusedEl) focusInDialog(target);
        return;
      }
      if (state.screen === 'player') {
        var ob = e.target.closest && e.target.closest('[data-osd]');
        if (ob) { state.osdIndex = Array.prototype.indexOf.call(osdButtons(), ob); showOsd(); }
        return;
      }
      var node = e.target.closest && e.target.closest('#home [data-zone]');
      if (!node) return;
      var zone = node.getAttribute('data-zone');
      var index = parseInt(node.getAttribute('data-index'), 10);
      if (state.zone !== zone || state.focus[zone] !== index) {
        state.zone = zone;
        state.focus[zone] = index;
        updateFocusClasses();
      }
    });
    doc.addEventListener('click', function (e) {
      if (dialogStack.length) return; // dialog buttons have their own handlers
      if (state.screen === 'player') {
        var ob = e.target.closest && e.target.closest('[data-osd]');
        if (ob) activateOsd(Array.prototype.indexOf.call(osdButtons(), ob));
        else if (osdVisible()) hideOsd(); else showOsd();
        return;
      }
      var node = e.target.closest && e.target.closest('#home [data-zone]');
      if (!node) return;
      var zone = node.getAttribute('data-zone');
      var index = parseInt(node.getAttribute('data-index'), 10);
      state.zone = zone;
      state.focus[zone] = index;
      if (zone === 'header') activateHeader(index);
      else if (zone === 'groups') { selectGroup(index); flushGroupSelection(); }
      else playFromList(currentItems(), index);
    });
    $('grid').addEventListener('wheel', function (e) {
      if (state.screen !== 'home' || dialogStack.length) return;
      var items = currentItems();
      if (!items.length) return;
      state.zone = 'grid';
      var next = state.focus.grid + (e.deltaY > 0 ? GRID_COLS : -GRID_COLS);
      moveGrid(Math.max(0, Math.min(items.length - 1, next)));
    });
  }

  function bindNetwork() {
    root.addEventListener('offline', function () { $('offline').textContent = t('offline'); $('offline').hidden = false; });
    root.addEventListener('online', function () { $('offline').hidden = true; toast(t('online')); });
    root.addEventListener('resize', function () { gridMetrics = null; if (state.screen === 'home') renderGrid(); });
  }

  // ------------------------------------------------------------------ start

  function start() {
    var settings = Store.settings();
    I18n.init(settings.lang);
    Platform.init();
    applyI18n();
    Player.init({ video: $('video'), avObject: $('av-player'), onState: onPlayerState });
    doc.addEventListener('keydown', onKeyDown);
    bindPointer();
    bindNetwork();
    if (root.navigator && root.navigator.onLine === false) { $('offline').textContent = t('offline'); $('offline').hidden = false; }
    // Tizen: pause when the app goes to the background, resume when it comes back.
    doc.addEventListener('visibilitychange', function () {
      if (state.screen !== 'player') return;
      Player.setPaused(doc.hidden);
    });

    showScreen('home');
    var go = function () { openActivePlaylist(); };
    if (settings.disclaimerAccepted) go(); else { renderGroups(); showDisclaimer(go); }
  }

  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', start);
  else start();
})(this);
