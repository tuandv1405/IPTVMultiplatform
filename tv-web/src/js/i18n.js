/* UI strings, Vietnamese and English. */
(function (root) {
  'use strict';

  var STRINGS = {
    vi: {
      appName: 'TS IPTV',
      disclaimerTitle: 'Trước khi bắt đầu',
      disclaimerBody: 'TS IPTV là trình phát. Ứng dụng không cung cấp kênh, danh sách phát hay nội dung nào. ' +
        'Bạn tự thêm danh sách phát (M3U) của mình và chịu trách nhiệm có quyền xem các nội dung đó.',
      disclaimerOk: 'Tôi hiểu',
      addPlaylist: 'Thêm danh sách',
      playlists: 'Danh sách phát',
      language: 'English',
      emptyTitle: 'Chưa có danh sách phát',
      emptyBody: 'Thêm đường dẫn M3U/M3U8 từ nhà cung cấp IPTV của bạn để bắt đầu.',
      name: 'Tên',
      namePlaceholder: 'Ví dụ: IPTV nhà mình',
      url: 'Đường dẫn M3U',
      urlPlaceholder: 'http://...',
      save: 'Lưu',
      cancel: 'Huỷ',
      delete: 'Xoá',
      use: 'Dùng',
      refresh: 'Tải lại',
      close: 'Đóng',
      loading: 'Đang tải danh sách…',
      loadFailed: 'Không tải được danh sách phát',
      notM3u: 'Đường dẫn không trả về danh sách M3U.',
      noChannels: 'Danh sách không có kênh nào.',
      httpError: 'Máy chủ trả lỗi',
      networkError: 'Không kết nối được tới máy chủ (mạng, địa chỉ sai hoặc máy chủ chặn).',
      invalidUrl: 'Đường dẫn phải bắt đầu bằng http:// hoặc https://',
      groupAll: 'Tất cả',
      groupFavorites: 'Yêu thích',
      groupRecent: 'Xem gần đây',
      groupOther: 'Khác',
      channels: 'kênh',
      noFavorites: 'Chưa có kênh yêu thích. Trong lúc xem, bấm ★ (hoặc nút Đỏ) để thêm.',
      noRecent: 'Chưa xem kênh nào.',
      buffering: 'Đang tải…',
      playFailed: 'Không phát được kênh này',
      drmUnsupported: 'Kênh có DRM, TV không phát được.',
      favoriteAdded: 'Đã thêm vào Yêu thích',
      favoriteRemoved: 'Đã bỏ khỏi Yêu thích',
      exitTitle: 'Thoát TS IPTV?',
      exit: 'Thoát',
      stay: 'Ở lại',
      offline: 'Mất kết nối mạng',
      online: 'Đã kết nối lại',
      hintHome: 'OK: xem  ·  ★/Đỏ: yêu thích',
      hintPlayer: '▲▼ / CH: đổi kênh  ·  OK: thông tin  ·  Back: danh sách',
      confirmDelete: 'Xoá danh sách này?',
    },
    en: {
      appName: 'TS IPTV',
      disclaimerTitle: 'Before you start',
      disclaimerBody: 'TS IPTV is a player. It provides no channels, playlists or content. ' +
        'You add your own M3U playlists and are responsible for having the right to watch them.',
      disclaimerOk: 'I understand',
      addPlaylist: 'Add playlist',
      playlists: 'Playlists',
      language: 'Tiếng Việt',
      emptyTitle: 'No playlist yet',
      emptyBody: 'Add an M3U/M3U8 link from your IPTV provider to get started.',
      name: 'Name',
      namePlaceholder: 'e.g. Home IPTV',
      url: 'M3U link',
      urlPlaceholder: 'http://...',
      save: 'Save',
      cancel: 'Cancel',
      delete: 'Delete',
      use: 'Use',
      refresh: 'Reload',
      close: 'Close',
      loading: 'Loading playlist…',
      loadFailed: 'Could not load the playlist',
      notM3u: 'The link did not return an M3U playlist.',
      noChannels: 'The playlist has no channels.',
      httpError: 'Server error',
      networkError: 'Could not reach the server (network, wrong address, or the server refused).',
      invalidUrl: 'The link must start with http:// or https://',
      groupAll: 'All',
      groupFavorites: 'Favorites',
      groupRecent: 'Recently watched',
      groupOther: 'Other',
      channels: 'channels',
      noFavorites: 'No favorites yet. While watching, press ★ (or the Red button) to add one.',
      noRecent: 'Nothing watched yet.',
      buffering: 'Loading…',
      playFailed: 'This channel could not be played',
      drmUnsupported: 'This channel uses DRM, which the TV cannot play.',
      favoriteAdded: 'Added to Favorites',
      favoriteRemoved: 'Removed from Favorites',
      exitTitle: 'Exit TS IPTV?',
      exit: 'Exit',
      stay: 'Stay',
      offline: 'Network connection lost',
      online: 'Back online',
      hintHome: 'OK: watch  ·  ★/Red: favorite',
      hintPlayer: '▲▼ / CH: change channel  ·  OK: info  ·  Back: list',
      confirmDelete: 'Delete this playlist?',
    },
  };

  var I18n = {
    lang: 'en',
    init: function (saved) {
      var nav = (root.navigator && root.navigator.language || '').toLowerCase();
      I18n.lang = saved === 'vi' || saved === 'en' ? saved : (nav.indexOf('vi') === 0 ? 'vi' : 'en');
    },
    t: function (key) {
      var table = STRINGS[I18n.lang] || STRINGS.en;
      return table[key] != null ? table[key] : (STRINGS.en[key] != null ? STRINGS.en[key] : key);
    },
    toggle: function () {
      I18n.lang = I18n.lang === 'vi' ? 'en' : 'vi';
      return I18n.lang;
    },
  };

  root.I18n = I18n;
})(this);
