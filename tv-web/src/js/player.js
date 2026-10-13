/*
 * Video playback with one interface over three engines:
 *  - Samsung AVPlay (webapis.avplay) on Tizen: HLS, DASH, MPEG-TS on the TV's hardware player.
 *  - The TV browser's <video> (LG webOS plays HLS natively).
 *  - hls.js over Media Source Extensions when <video> cannot play HLS (desktop browsers) or the
 *    native attempt fails.
 *
 * Callbacks: onState(state, detail) with state 'loading' | 'playing' | 'paused' | 'ended' | 'error'.
 */
(function (root) {
  'use strict';

  var START_TIMEOUT_MS = 25000;

  var video = null;
  var avObject = null;
  var onState = function () {};
  var hls = null;
  var current = null; // { url, isHls, triedHls, engine }
  var startTimer = null;
  var paused = false;

  function avplay() {
    return root.webapis && root.webapis.avplay ? root.webapis.avplay : null;
  }

  function looksHls(url) {
    return /\.m3u8($|[?#])|[?&](type|format|output|extension)=m3u8/i.test(url);
  }

  function nativeHls() {
    if (!video || !video.canPlayType) return false;
    return !!(video.canPlayType('application/vnd.apple.mpegurl') || video.canPlayType('application/x-mpegURL'));
  }

  function emit(state, detail) {
    // After a failure the engine may still send 'waiting' etc.: the error stays until the next play().
    if (current && current.failed) return;
    if (state === 'error' && current) current.failed = true;
    if (state === 'playing' || state === 'error' || state === 'ended') clearTimeout(startTimer);
    paused = state === 'paused';
    onState(state, detail);
  }

  function armStartTimeout() {
    clearTimeout(startTimer);
    startTimer = setTimeout(function () {
      if (current) fail('timeout');
    }, START_TIMEOUT_MS);
  }

  function fail(reason) {
    // A native <video> failure on HLS gets one more try with hls.js.
    if (current && current.engine === 'video' && current.isHls && !current.triedHls && root.Hls && root.Hls.isSupported()) {
      current.triedHls = true;
      startHlsJs(current.url);
      return;
    }
    emit('error', reason);
  }

  // ---- <video> and hls.js ----

  function destroyHls() {
    if (hls) {
      try { hls.destroy(); } catch (e) { /* ignore */ }
      hls = null;
    }
  }

  function resetVideo() {
    destroyHls();
    if (!video) return;
    try { video.pause(); } catch (e) { /* ignore */ }
    video.removeAttribute('src');
    try { video.load(); } catch (e2) { /* ignore */ }
  }

  function startHlsJs(url) {
    resetVideo();
    current.engine = 'hlsjs';
    var networkRetries = 0;
    var mediaRetries = 0;
    hls = new root.Hls({ enableWorker: true, lowLatencyMode: false, backBufferLength: 30 });
    hls.on(root.Hls.Events.ERROR, function (event, data) {
      if (!data.fatal) return;
      // startLoad() resumes segment loading only: a playlist that could not be loaded (hls.js has
      // already retried it) would then wait for the start timeout instead of failing.
      var playlistFailed = /^(manifest|level)/i.test(data.details || '');
      if (data.type === root.Hls.ErrorTypes.NETWORK_ERROR && !playlistFailed && networkRetries++ < 1) {
        hls.startLoad();
      } else if (data.type === root.Hls.ErrorTypes.MEDIA_ERROR && mediaRetries++ < 1) {
        hls.recoverMediaError();
      } else {
        emit('error', data.details || data.type);
      }
    });
    hls.loadSource(url);
    hls.attachMedia(video);
    playVideoElement();
  }

  function playVideoElement() {
    var p = video.play();
    if (p && p.catch) p.catch(function () { /* the 'error' event reports real failures */ });
  }

  function startVideo(url, isHls) {
    resetVideo();
    if (isHls && !nativeHls() && root.Hls && root.Hls.isSupported()) {
      current.triedHls = true;
      startHlsJs(url);
      return;
    }
    current.engine = 'video';
    video.src = url;
    playVideoElement();
  }

  function bindVideoEvents() {
    video.addEventListener('playing', function () { if (current) emit('playing'); });
    video.addEventListener('waiting', function () { if (current) emit('loading'); });
    video.addEventListener('pause', function () { if (current && !video.ended && video.currentTime > 0) emit('paused'); });
    video.addEventListener('ended', function () { if (current) emit('ended'); });
    video.addEventListener('error', function () {
      if (current && current.engine === 'video') fail('media-error-' + (video.error ? video.error.code : '?'));
    });
  }

  // ---- Samsung AVPlay ----

  function stopAvPlay() {
    var av = avplay();
    if (!av) return;
    try { av.stop(); } catch (e) { /* ignore */ }
    try { av.close(); } catch (e2) { /* ignore */ }
  }

  function startAvPlay(channel) {
    var av = avplay();
    stopAvPlay();
    current.engine = 'avplay';
    try {
      av.open(channel.url);
      // AVPlay coordinates are on a 1920x1080 base whatever the app's resolution.
      av.setDisplayRect(0, 0, 1920, 1080);
      av.setDisplayMethod && av.setDisplayMethod('PLAYER_DISPLAY_MODE_LETTER_BOX');
      if (channel.userAgent) {
        try { av.setStreamingProperty('USER_AGENT', channel.userAgent); } catch (e) { /* older models */ }
      }
      av.setListener({
        onbufferingstart: function () { emit('loading'); },
        onbufferingcomplete: function () { emit('playing'); },
        onstreamcompleted: function () { emit('ended'); },
        onerror: function (type) { emit('error', String(type)); },
      });
      var url = channel.url;
      av.prepareAsync(function () {
        if (!current || current.url !== url) return; // switched channel meanwhile
        av.play();
        emit('playing');
      }, function (err) {
        if (current && current.url === url) emit('error', err && err.name);
      });
    } catch (e) {
      emit('error', e && e.name);
    }
  }

  var Player = {
    init: function (options) {
      video = options.video;
      avObject = options.avObject;
      onState = options.onState || onState;
      if (avplay() && avObject) {
        avObject.style.display = 'block';
        video.style.display = 'none';
      } else {
        if (avObject) avObject.style.display = 'none';
        bindVideoEvents();
      }
    },

    engineName: function () { return avplay() ? 'avplay' : 'html5'; },

    play: function (channel) {
      Player.stop();
      current = { url: channel.url, isHls: looksHls(channel.url), triedHls: false, engine: null };
      emit('loading');
      armStartTimeout();
      if (avplay()) startAvPlay(channel);
      else startVideo(channel.url, current.isHls);
    },

    stop: function () {
      clearTimeout(startTimer);
      current = null;
      paused = false;
      if (avplay()) stopAvPlay();
      else resetVideo();
    },

    isPaused: function () { return paused; },

    setPaused: function (pause) {
      if (!current) return;
      var av = avplay();
      try {
        if (av) {
          if (pause) av.pause(); else av.play();
          emit(pause ? 'paused' : 'playing');
        } else if (pause) {
          video.pause();
        } else {
          playVideoElement();
        }
      } catch (e) { /* ignore */ }
    },

    /** Seekable (VOD) streams only; returns false for live. */
    seekBy: function (seconds) {
      var av = avplay();
      try {
        if (av) {
          var duration = av.getDuration();
          if (!duration || duration <= 0) return false;
          if (seconds >= 0) av.jumpForward(seconds * 1000); else av.jumpBackward(-seconds * 1000);
          return true;
        }
        if (!video || !isFinite(video.duration) || video.duration <= 0) return false;
        video.currentTime = Math.max(0, Math.min(video.duration - 1, video.currentTime + seconds));
        return true;
      } catch (e) {
        return false;
      }
    },
  };

  root.Player = Player;
})(this);
