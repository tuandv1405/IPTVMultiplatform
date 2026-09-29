/* TS IPTV Source validator (/guides/tsiptv-source/validate/).
   Runs entirely in the browser: the only network request is /schema/tsiptv-source-v1.json.
   Nothing is uploaded and includes are never fetched.
   - JSON Schema 2020-12 check with the vendored Ajv bundle (assets/vendor/ajv2020.min.js, MIT);
   - a hand-written pass for rules the schema cannot express (spec docs/tsiptv-source-format.md
     §5.4 uniqueness, §7 references, §8.3 endYear, §10 document codes).
   The core, TsiptvValidator.validate(), is a pure function so web/scripts/test_validator.js can run
   it in Node against the examples and fixtures. */
(function (root, factory) {
  "use strict";
  var api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.TsiptvValidator = api;
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  var MAX_BYTES = 5 * 1024 * 1024;

  var T = {
    en: {
      root: "(root)",
      notJsonAt: "Not valid JSON: the error is near line %l, column %c.",
      notJsonNoPos: "Not valid JSON.",
      compressed: "This file looks compressed (gzip). Unzip it first, then check the .json file.",
      notUtf8: "This file looks compressed, or it is not UTF-8 (for example saved as UTF-16). Save it as UTF-8 JSON and try again.",
      badDateTimeShape: "must be a date-time such as 2026-09-27T10:00:00Z; TS IPTV ignores this field",
      dateTimeSchemaOnly: "the schema expects the form 2026-09-27T10:00:00Z (uppercase T and Z); TS IPTV accepts this value",
      spacesSchemaOnly: "the schema expects no surrounding spaces; TS IPTV accepts this value",
      controlsReplaced: "control characters are not allowed here; TS IPTV replaces them with spaces",
      badTime: "is not a valid time (hours 00–23, minutes and seconds 00–59, offset up to ±23:59); TS IPTV ignores this field",
      updatedAtTooLong: "is longer than 64 characters; TS IPTV ignores this field",
      badDateShape: "must be a date YYYY-MM-DD; TS IPTV ignores this field",
      textNoEntryMeta: "has no entry with a valid language tag and a non-blank text, so the source has no name",
      textNoEntryName: "has no entry with a valid language tag and a non-blank text; the item is skipped",
      textNoEntryField: "has no entry with a valid language tag and a non-blank text; TS IPTV ignores this field",
      badDate: "is not a real calendar date; TS IPTV ignores this field",
      stremioUrl: "a stremio include URL must be a manifest link ending with /manifest.json",
      stremioHeaders: "headers are not allowed on a stremio include (TS IPTV ignores them)",
      keysOnlyClearkey: "keys are only for clearkey; widevine and playready use licenseUrl",
      clearkeyNeeds: "clearkey needs licenseUrl or keys",
      notObject: "The root must be a JSON object { … }.",
      notSource: 'format must be "tsiptv-source".',
      version: "version must be the integer 1 (this validator knows version 1).",
      tooLarge: "The file is larger than 5 MiB.",
      empty: "The source has no channels, movies, series or includes.",
      emptyAfterDrop: "Every channel, movie, series and include would be dropped, so nothing is left to show.",
      missing: "missing required field",
      unknown: "unknown field (typo?)",
      type: "must be ",
      types: { string: "a string", number: "a number", integer: "an integer", object: "an object", array: "an array", boolean: "true or false", "null": "null" },
      constant: "must be ",
      oneOfValues: "must be one of: ",
      format: "is not a valid ",
      formats: { date: "date (YYYY-MM-DD)", "date-time": "date-time (e.g. 2026-09-27T10:00:00Z)", uri: "URL" },
      tooShort: "is too short (at least %n)",
      tooLong: "is too long (at most %n)",
      tooFewItems: "needs at least %n entries",
      tooManyItems: "has more than %n entries",
      tooFewProps: "needs at least %n entries",
      tooManyProps: "has more than %n entries",
      min: "must be at least %n",
      max: "must be at most %n",
      exclusive: "is out of range",
      shape: "does not match any allowed form of this field",
      notAllowed: "this combination is not allowed here",
      badName: "invalid key",
      badLangKey: "is not a language tag (such as en, vi, zh-CN); this entry is ignored",
      forbiddenHeader: "this header cannot be set (Host, Content-Length, Connection and Transfer-Encoding are forbidden)",
      bothUrlStreams: "has both url and streams; use exactly one of them",
      noUrlStreams: "needs url or streams",
      defs: {
        id: "must be an id: letters, digits, . _ - (no “:”), starting with a letter or digit, at most 128 characters",
        itemRef: "must be an item id, or includeId:itemId",
        httpUrl: "must be an absolute http:// or https:// URL without spaces or control characters, at most 2,048 characters",
        languageTag: "must be a language tag such as en, vi, zh-CN",
        color: "must be a colour #RRGGBB",
        fullDate: "must be a date YYYY-MM-DD",
        text: "must be non-blank text (1–200 characters, no line breaks or control characters), or an object of language → text",
        longText: "must be non-blank text (1–5,000 characters, no control characters except line breaks), or an object of language → text",
        shortStrings: "must be a non-blank string without control characters",
        names: "must be non-blank names without control characters",
        headers: "must be header names (letters, digits, - …) with string values without control characters",
        mimeType: "must be a MIME type such as application/x-mpegURL"
      },
      dupId: 'id "%v" is already used at %p',
      dupSeason: "season number %v is already used at %p",
      dupEpisode: "episode number %v is already used at %p",
      unknownInclude: 'no include has the id "%v"; the section is skipped',
      xmltvInclude: 'include "%v" is an xmltv guide and cannot be shown in a section; the section is skipped',
      notStremio: 'include "%v" is not a stremio include; the section is skipped',
      unknownRef: '"%v" is not an item of this document; it will be skipped',
      unknownRefInclude: '"%v" names an include that does not exist',
      unknownTarget: '"%v" is not an item of this document; the banner will be decorative',
      endYear: "endYear must not be before year; it will be ignored",
      epgPrefix: 'include ids must not start with "epg-" (reserved for epg links)',
      valid: "Valid.",
      validDetail: "This file matches the TS IPTV Source schema version 1 and the reference checks. TS IPTV checks its includes when you import it.",
      invalid: "Not valid.",
      errors: "Errors",
      warnings: "Warnings",
      noWarnings: "No warnings.",
      more: "… and %n more",
      loading: "Loading the schema…",
      schemaFailed: "Could not load the schema. Check your connection and try again.",
      emptyInput: "Paste a file or choose one first.",
      fileTooLarge: "This file is larger than 5 MiB."
    },
    vi: {
      root: "(gốc)",
      notJsonAt: "Không phải JSON hợp lệ: lỗi ở gần dòng %l, cột %c.",
      notJsonNoPos: "Không phải JSON hợp lệ.",
      compressed: "Tệp này có vẻ đã được nén (gzip). Hãy giải nén trước rồi kiểm tra tệp .json.",
      notUtf8: "Tệp này có vẻ đã được nén, hoặc không phải UTF-8 (ví dụ được lưu dạng UTF-16). Hãy lưu thành JSON UTF-8 rồi thử lại.",
      badDateTimeShape: "phải là thời điểm dạng 2026-09-27T10:00:00Z; TS IPTV bỏ qua trường này",
      dateTimeSchemaOnly: "schema yêu cầu dạng 2026-09-27T10:00:00Z (T và Z viết hoa); TS IPTV vẫn chấp nhận giá trị này",
      spacesSchemaOnly: "schema không cho phép khoảng trắng ở hai đầu; TS IPTV vẫn chấp nhận giá trị này",
      controlsReplaced: "không được dùng ký tự điều khiển ở đây; TS IPTV thay chúng bằng dấu cách",
      badTime: "không phải giờ hợp lệ (giờ 00–23, phút và giây 00–59, độ lệch tối đa ±23:59); TS IPTV bỏ qua trường này",
      updatedAtTooLong: "dài hơn 64 ký tự; TS IPTV bỏ qua trường này",
      badDateShape: "phải là ngày dạng YYYY-MM-DD; TS IPTV bỏ qua trường này",
      textNoEntryMeta: "không có mục nào có mã ngôn ngữ hợp lệ và chữ không rỗng, nên nguồn không có tên",
      textNoEntryName: "không có mục nào có mã ngôn ngữ hợp lệ và chữ không rỗng; mục này bị bỏ qua",
      textNoEntryField: "không có mục nào có mã ngôn ngữ hợp lệ và chữ không rỗng; TS IPTV bỏ qua trường này",
      badDate: "không phải ngày có thật trên lịch; TS IPTV bỏ qua trường này",
      stremioUrl: "URL của include stremio phải là đường dẫn manifest kết thúc bằng /manifest.json",
      stremioHeaders: "include stremio không được có headers (TS IPTV bỏ qua chúng)",
      keysOnlyClearkey: "keys chỉ dùng cho clearkey; widevine và playready dùng licenseUrl",
      clearkeyNeeds: "clearkey cần licenseUrl hoặc keys",
      notObject: "Gốc của tệp phải là một đối tượng JSON { … }.",
      notSource: 'format phải là "tsiptv-source".',
      version: "version phải là số nguyên 1 (trình kiểm tra này hiểu phiên bản 1).",
      tooLarge: "Tệp lớn hơn 5 MiB.",
      empty: "Nguồn không có kênh, phim lẻ, phim bộ hay include nào.",
      emptyAfterDrop: "Mọi kênh, phim lẻ, phim bộ và include đều sẽ bị bỏ, nên không còn gì để hiển thị.",
      missing: "thiếu trường bắt buộc",
      unknown: "trường không xác định (gõ nhầm?)",
      type: "phải là ",
      types: { string: "chuỗi", number: "số", integer: "số nguyên", object: "đối tượng", array: "mảng", boolean: "true hoặc false", "null": "null" },
      constant: "phải là ",
      oneOfValues: "phải là một trong: ",
      format: "không phải ",
      formats: { date: "ngày hợp lệ (YYYY-MM-DD)", "date-time": "thời điểm hợp lệ (ví dụ 2026-09-27T10:00:00Z)", uri: "URL hợp lệ" },
      tooShort: "quá ngắn (ít nhất %n)",
      tooLong: "quá dài (tối đa %n)",
      tooFewItems: "cần ít nhất %n phần tử",
      tooManyItems: "có nhiều hơn %n phần tử",
      tooFewProps: "cần ít nhất %n mục",
      tooManyProps: "có nhiều hơn %n mục",
      min: "phải từ %n trở lên",
      max: "phải từ %n trở xuống",
      exclusive: "nằm ngoài khoảng cho phép",
      shape: "không khớp với dạng nào được phép của trường này",
      notAllowed: "tổ hợp này không được phép ở đây",
      badName: "khoá không hợp lệ",
      badLangKey: "không phải mã ngôn ngữ (như en, vi, zh-CN); mục này sẽ bị bỏ qua",
      forbiddenHeader: "không được đặt header này (Host, Content-Length, Connection và Transfer-Encoding bị cấm)",
      bothUrlStreams: "có cả url và streams; chỉ dùng một trong hai",
      noUrlStreams: "cần có url hoặc streams",
      defs: {
        id: "phải là một id: chữ cái, chữ số, . _ - (không có “:”), bắt đầu bằng chữ cái hoặc chữ số, tối đa 128 ký tự",
        itemRef: "phải là id của một mục, hoặc includeId:itemId",
        httpUrl: "phải là URL tuyệt đối http:// hoặc https://, không có khoảng trắng hay ký tự điều khiển, tối đa 2.048 ký tự",
        languageTag: "phải là mã ngôn ngữ như en, vi, zh-CN",
        color: "phải là màu dạng #RRGGBB",
        fullDate: "phải là ngày dạng YYYY-MM-DD",
        text: "phải là chữ không rỗng (1–200 ký tự, không xuống dòng, không ký tự điều khiển), hoặc đối tượng ngôn ngữ → chữ",
        longText: "phải là chữ không rỗng (1–5.000 ký tự, không ký tự điều khiển ngoài xuống dòng), hoặc đối tượng ngôn ngữ → chữ",
        shortStrings: "phải là chuỗi không rỗng, không có ký tự điều khiển",
        names: "phải là các tên không rỗng, không có ký tự điều khiển",
        headers: "phải là tên header (chữ, số, - …) với giá trị là chuỗi không có ký tự điều khiển",
        mimeType: "phải là kiểu MIME như application/x-mpegURL"
      },
      dupId: 'id "%v" đã được dùng ở %p',
      dupSeason: "số mùa %v đã được dùng ở %p",
      dupEpisode: "số tập %v đã được dùng ở %p",
      unknownInclude: 'không có include nào có id "%v"; mục này sẽ bị bỏ qua',
      xmltvInclude: 'include "%v" là lịch XMLTV, không hiển thị được trong một mục; mục này sẽ bị bỏ qua',
      notStremio: 'include "%v" không phải include stremio; mục này sẽ bị bỏ qua',
      unknownRef: '"%v" không phải một mục trong tệp này; nó sẽ bị bỏ qua',
      unknownRefInclude: '"%v" trỏ tới một include không tồn tại',
      unknownTarget: '"%v" không phải một mục trong tệp này; banner sẽ chỉ để trang trí',
      endYear: "endYear không được nhỏ hơn year; giá trị này sẽ bị bỏ qua",
      epgPrefix: 'id của include không được bắt đầu bằng "epg-" (dành cho các đường dẫn epg)',
      valid: "Hợp lệ.",
      validDetail: "Tệp này khớp với schema TS IPTV Source phiên bản 1 và các bước kiểm tra tham chiếu. TS IPTV kiểm tra các include của nó khi bạn nhập.",
      invalid: "Không hợp lệ.",
      errors: "Lỗi",
      warnings: "Cảnh báo",
      noWarnings: "Không có cảnh báo.",
      more: "… và %n mục khác",
      loading: "Đang tải schema…",
      schemaFailed: "Không tải được schema. Hãy kiểm tra kết nối mạng rồi thử lại.",
      emptyInput: "Hãy dán nội dung hoặc chọn một tệp trước.",
      fileTooLarge: "Tệp này lớn hơn 5 MiB."
    }
  };

  function fmt(s, v, p, n) {
    // replacer functions: "$&" or "$'" in a value must not be expanded
    return s.replace("%v", function () { return String(v); })
      .replace("%p", function () { return String(p); })
      .replace("%n", function () { return String(n); });
  }

  // C1 controls, soft hyphen, bidi marks and embeddings, zero-width characters, word joiners, BOM
  var INVISIBLE = /[\u0080-\u009F\u00AD\u061C\u180E\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u206F\uFEFF]/g;

  /* "/channels/3/url" → "channels[3].url" */
  function pathOf(pointer, extra, t) {
    var parts = pointer ? pointer.split("/").slice(1) : [];
    if (extra !== undefined && extra !== null) parts.push(String(extra));
    var out = "";
    parts.forEach(function (raw) {
      var p = raw.replace(/~1/g, "/").replace(/~0/g, "~");
      if (/^\d+$/.test(p)) out += "[" + p + "]";
      else if (/^[A-Za-z_$][A-Za-z0-9_$-]*$/.test(p)) out += (out ? "." : "") + p;
      else out += "[" + JSON.stringify(p).replace(INVISIBLE, function (c) {
        return "\\u" + ("000" + c.charCodeAt(0).toString(16).toUpperCase()).slice(-4);
      }) + "]";
    });
    return out || t.root;
  }

  /* Map every sub-schema object inside $defs.<name> to <name>, so an error (Ajv verbose mode gives
     e.parentSchema) can be explained by the definition it belongs to. */
  function defIndex(schema) {
    var index = new Map();
    var defs = schema && schema.$defs ? schema.$defs : {};
    function walk(node, name) {
      if (!node || typeof node !== "object") return;
      if (!index.has(node)) index.set(node, name);
      Object.keys(node).forEach(function (k) { walk(node[k], name); });
    }
    Object.keys(defs).forEach(function (name) { index.set(defs[name], name); });
    Object.keys(defs).forEach(function (name) { walk(defs[name], name); });
    return index;
  }

  /* Every object inside the root "anyOf" (the "has content" rule), so its branch errors can be
     dropped when E_EMPTY already says the same thing. */
  function rootAnyOfIndex(schema) {
    var set = new Set();
    function walk(node) {
      if (!node || typeof node !== "object" || set.has(node)) return;
      set.add(node);
      Object.keys(node).forEach(function (k) { walk(node[k]); });
    }
    if (schema && Array.isArray(schema.anyOf)) schema.anyOf.forEach(walk);
    return set;
  }

  function isObj(v) { return v !== null && typeof v === "object" && !Array.isArray(v); }
  function arr(v) { return Array.isArray(v) ? v : []; }
  function has(o, k) { return Object.prototype.hasOwnProperty.call(o, k); }

  /* One finding: level "error" or "warning". */
  function finding(level, path, msg, code, extra) {
    var f = { level: level, path: path, msg: msg };
    if (code) f.code = code;
    if (extra) Object.keys(extra).forEach(function (k) { f[k] = extra[k]; });
    return f;
  }

  var LANG_TAG = /^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/;
  function usableEntry(obj) {
    return Object.keys(obj).some(function (k) {
      return LANG_TAG.test(k) && typeof obj[k] === "string" && obj[k].replace(/[\u0000-\u001F]/g, " ").trim() !== "";
    });
  }
  var REQUIRED_NAME = /^(channels|movies|series)\[\d+\](\.seasons\[\d+\]\.episodes\[\d+\])?\.name$/;

  function schemaMessage(e, t, defs) {
    var p = e.params || {};
    var def = defs.get(e.parentSchema) || null;
    var here = pathOf(e.instancePath, null, t);
    if (e.propertyName !== undefined) {           // error about a map key (propertyNames)
      var keyPath = pathOf(e.instancePath, e.propertyName, t);
      if (def === "headers" && e.keyword === "not") return finding("error", keyPath, t.forbiddenHeader, null, { keyIssue: true });
      if (def === "languageTag") return finding("warning", keyPath, t.badLangKey, "W_TEXT", { keyIssue: true });
      return finding("error", keyPath, def && t.defs[def] ? t.defs[def] : t.badName, null, { keyIssue: true });
    }
    if (def === "playable" && e.keyword === "oneOf") {
      if (!isObj(e.data)) return null;             // the "type" error says "must be an object"
      return finding("error", here, Array.isArray(p.passingSchemas) ? t.bothUrlStreams : t.noUrlStreams);
    }
    if (def === "include") {
      if (e.keyword === "pattern" && /\/url$/.test(e.instancePath)) return finding("error", here, t.stremioUrl);
      if (e.keyword === "not" && /\/id$/.test(e.instancePath)) return finding("error", here, t.epgPrefix, null, { epgPrefix: true });
      if (e.keyword === "not") return finding("error", pathOf(e.instancePath, "headers", t), t.stremioHeaders);
    }
    if (def === "drm") {
      if (e.keyword === "not") return finding("error", pathOf(e.instancePath, "keys", t), t.keysOnlyClearkey);
      if (e.keyword === "anyOf") return finding("error", here, t.clearkeyNeeds);
    }
    switch (e.keyword) {
      case "required": return finding("error", pathOf(e.instancePath, p.missingProperty, t), t.missing);
      case "unevaluatedProperties": return finding("error", pathOf(e.instancePath, p.unevaluatedProperty, t), t.unknown);
      case "additionalProperties": return finding("error", pathOf(e.instancePath, p.additionalProperty, t), t.unknown);
      case "propertyNames": return null;          // the inner error (above) says why
      case "type": return finding("error", here, t.type + String(p.type).split(",").map(function (x) { return t.types[x] || x; }).join(" / "));
      case "const": return finding("error", here, t.constant + JSON.stringify(p.allowedValue));
      case "enum": return finding("error", here, t.oneOfValues + (p.allowedValues || []).join(", "));
      case "format":
        if (p.format === "date-time") return dateFinding("date-time", here, e.data, t);
        if (p.format === "date") return dateFinding("date", here, e.data, t);
        return finding("error", here, t.format + (t.formats[p.format] || p.format), null, { formatOnly: true });
      case "minLength": return finding("error", here, fmt(t.tooShort, "", "", p.limit));
      case "maxLength": return finding("error", here, fmt(t.tooLong, "", "", p.limit));
      case "minItems": return finding("error", here, fmt(t.tooFewItems, "", "", p.limit));
      case "maxItems": return finding("error", here, fmt(t.tooManyItems, "", "", p.limit));
      case "minProperties": return finding("error", here, fmt(t.tooFewProps, "", "", p.limit));
      case "maxProperties": return finding("error", here, fmt(t.tooManyProps, "", "", p.limit));
      case "minimum": return finding("error", here, fmt(t.min, "", "", p.limit));
      case "maximum": return finding("error", here, fmt(t.max, "", "", p.limit));
      case "exclusiveMinimum": case "exclusiveMaximum": return finding("error", here, t.exclusive);
      case "pattern":
        // date and date-time fields: the app warns and drops the field (W_FIELD), like a bad format
        if (e.parentSchema && (e.parentSchema.format === "date-time" || e.parentSchema.format === "date")) {
          return dateFinding(e.parentSchema.format, here, e.data, t);
        }
        return finding("error", here, def && t.defs[def] ? t.defs[def] : t.shape);
      case "not": return finding("error", here, t.notAllowed);
      default: return finding("error", here, def && t.defs[def] ? t.defs[def] : t.shape, null, { generic: true, def: def, data: e.data });
    }
  }

  /* Ajv with allErrors reports every failed branch of oneOf/anyOf/if. Keep the useful ones. */
  function schemaIssues(errors, t, defs, rootAnyOf, emptyReported) {
    var list = [], typeGroups = {}, typeOrder = [];
    // anyOf errors that get their own message (clearkey) make their branch errors redundant
    var explainedAnyOf = {}, explainedPlayable = {};
    (errors || []).forEach(function (e) {
      if (e.keyword === "anyOf" && defs.get(e.parentSchema) === "drm") explainedAnyOf[e.instancePath] = true;
      if (e.keyword === "oneOf" && defs.get(e.parentSchema) === "playable" && isObj(e.data)) explainedPlayable[e.instancePath] = true;
    });
    (errors || []).forEach(function (e) {
      if (e.keyword === "if" || e.keyword === "false schema") return;
      if (explainedAnyOf[e.instancePath] && /\/anyOf\/\d+\//.test(e.schemaPath)) return;
      // "needs url or streams" already says it; drop the two branch "missing url / streams" lines
      if (explainedPlayable[e.instancePath] && e.keyword === "required" && /\/oneOf\/\d+\/required$/.test(e.schemaPath)) return;
      // E_EMPTY already explains the root "has content" rule.
      if (emptyReported && (rootAnyOf.has(e.parentSchema) || (e.keyword === "anyOf" && e.instancePath === ""))) return;
      // A failed oneOf branch "must be a string" is noise when the value is an object (and the
      // other way round); when the value is neither, the type error is the real message.
      if (e.keyword === "type" && /\/(oneOf|anyOf)\/\d+\/type$/.test(e.schemaPath)) {
        var want = String(e.params.type);
        if (isObj(e.data) && want !== "object") return;
        if (typeof e.data === "string" && want !== "string") return;
        // neither branch fits (a number, an array…): one message listing every allowed type
        var tp = pathOf(e.instancePath, null, t);
        if (!typeGroups[tp]) { typeGroups[tp] = []; typeOrder.push(tp); list.push({ typeGroup: tp }); }
        typeGroups[tp].push(want);
        return;
      }
      var m = schemaMessage(e, t, defs);
      if (m) list.push(m);
    });
    list = list.map(function (x) {
      if (!x.typeGroup) return x;
      var types = typeGroups[x.typeGroup].join(",").split(",").filter(function (v, i, a) { return a.indexOf(v) === i; });
      return finding("error", x.typeGroup, t.type + types.map(function (v) { return t.types[v] || v; }).join(" / "));
    });
    // Index every ancestor path once (linear in the total path length): which ancestors have a
    // key-only problem below them, and which have a real finding below them.
    var specific = Object.create(null), patternAt = Object.create(null);
    var keyBelow = Object.create(null), realBelow = Object.create(null);
    list.forEach(function (x) {
      if (!x.generic) specific[x.path] = true;
      if (!x.formatOnly && !x.generic) patternAt[x.path] = true;
      var index = x.keyIssue ? keyBelow : realBelow;
      for (var i = 1; i < x.path.length; i++) {
        var c = x.path.charAt(i);
        if (c === "." || c === "[") index[x.path.slice(0, i)] = true;
      }
    });
    var keysOnly = list.filter(function (x) { return x.msg === t.keysOnlyClearkey; }).map(function (x) { return x.path; });
    function underKeysOnly(path) {
      return keysOnly.some(function (k) { return path.indexOf(k + ".") === 0 || path.indexOf(k + "[") === 0 || path === k; });
    }
    var seen = Object.create(null), out = [];
    list.forEach(function (x) {
      if (keysOnly.length && x.msg !== t.keysOnlyClearkey && underKeysOnly(x.path)) return;
      if (x.generic && specific[x.path]) return;
      if (x.generic && keyBelow[x.path] && !realBelow[x.path]) {
        // Only language-key problems inside a text object: fine if a usable entry remains,
        // otherwise the whole text is unusable, like the app (E_META / E_ITEM_NAME / field ignored).
        if ((x.def === "text" || x.def === "longText") && isObj(x.data) && !usableEntry(x.data)) {
          if (x.path === "meta.name") x = finding("error", x.path, t.textNoEntryMeta, "E_META");
          else if (REQUIRED_NAME.test(x.path)) x = finding("error", x.path, t.textNoEntryName, "E_ITEM_NAME");
          else x = finding("warning", x.path, t.textNoEntryField, "W_TEXT");
        } else return;
      }
      if (x.formatOnly && patternAt[x.path]) return;   // "not a valid URL" next to the URL rule
      var key = x.level + "\u0000" + x.path + "\u0000" + x.msg;
      if (seen[key]) return;
      seen[key] = true;
      out.push(x);
    });
    return out;
  }

  /* ---- what the app drops (so a dropped item does not claim its id, spec §10) ---- */
  var ID_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
  var URL_RE = /^[Hh][Tt][Tt][Pp][Ss]?:\/\/[^\s\/?#\u0000-\u001F\u007F-\u009F]+[^\s\u0000-\u001F\u007F-\u009F]*$/;
  var LANG_RE = /^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/;
  function validId(v) { return typeof v === "string" && ID_RE.test(v); }
  // The parser trims URLs, the include type and the DRM system with ECMA-262 whitespace (trimJs);
  // String.prototype.trim uses exactly that set.
  function trimmed(v) { return typeof v === "string" ? v.trim() : v; }
  function validUrl(v) { v = trimmed(v); return typeof v === "string" && v.length <= 2048 && URL_RE.test(v); }
  /* TsiptvRules.isStremioManifestUrl: the path (before ? and #) ends with /manifest.json */
  function isStremioManifestUrl(url) {
    url = trimmed(url);
    if (typeof url !== "string") return false;
    var schemeEnd = url.indexOf("://");
    if (schemeEnd < 0) return false;
    var rest = url.slice(schemeEnd + 3), end = rest.search(/[\/?#]/);
    if (end < 0 || rest.charAt(end) !== "/") return false;
    return rest.slice(end).split("?")[0].split("#")[0].slice(-"/manifest.json".length) === "/manifest.json";
  }
  function validText(v) {
    function ok(s) { return typeof s === "string" && s.replace(/[\u0000-\u001F]/g, " ").trim() !== ""; }
    if (typeof v === "string") return ok(v);
    if (!isObj(v)) return false;
    return Object.keys(v).some(function (k) { return LANG_RE.test(k) && ok(v[k]); });
  }
  function validInt(v, min, max) { return typeof v === "number" && Math.floor(v) === v && v >= min && v <= max; }
  var HEX32 = /^[0-9A-Fa-f]{32}$/;
  function absent(v) { return v === undefined || v === null; }
  /* spec §8.5 / §10: an invalid drm object drops the stream (E_DRM, E_URL for a bad licenseUrl). */
  function drmOk(d) {      // same order as TsiptvSourceParser.drm()
    if (absent(d)) return true;
    if (!isObj(d)) return false;
    var system = typeof d.system === "string" ? d.system.trim().toLowerCase() : "";
    if (!system) return false;                                        // missing or blank: E_DRM
    if (["widevine", "playready", "clearkey"].indexOf(system) < 0) return true;   // unknown: kept (W_UNKNOWN_TYPE)
    if (!absent(d.licenseUrl) && !validUrl(d.licenseUrl)) return false;          // E_URL
    if (system !== "clearkey") return !absent(d.licenseUrl);         // keys are ignored here (W_FIELD)
    var keys = d.keys;
    if (!absent(keys)) {
      var names = isObj(keys) ? Object.keys(keys) : null;
      if (!names || !names.length || names.length > 20) return false;
      if (!names.every(function (k) { return HEX32.test(k) && typeof keys[k] === "string" && HEX32.test(keys[k]); })) return false;
      return true;
    }
    return !absent(d.licenseUrl);
  }
  function playable(o) {
    var hasUrl = !absent(o.url), hasStreams = !absent(o.streams);
    if (hasUrl === hasStreams) return false;
    if (hasUrl) return validUrl(o.url) && drmOk(o.drm);
    return arr(o.streams).some(function (s) {
      return isObj(s) && validUrl(s.url) && drmOk(absent(s.drm) ? o.drm : s.drm);
    });
  }
  function keptItem(o) { return isObj(o) && validId(o.id) && validText(o.name) && playable(o); }
  function keptEpisode(o) { return keptItem(o) && validInt(o.number, 0, 9999); }

  /* Rules the schema cannot express. */
  function referencePass(doc, t, out) {
    var ids = Object.create(null);           // id → first path
    var kept = 0;
    function claim(id, path) {
      kept++;
      if (has(ids, id)) { kept--; out.push(finding("error", path, fmt(t.dupId, id, ids[id]), "E_DUPLICATE_ID")); }
      else ids[id] = path;
    }
    // Order of spec §10: channels → movies → series (each followed by its episodes) → includes.
    arr(doc.channels).forEach(function (c, i) { if (keptItem(c)) claim(c.id, "channels[" + i + "].id"); });
    arr(doc.movies).forEach(function (m, i) { if (keptItem(m)) claim(m.id, "movies[" + i + "].id"); });
    arr(doc.series).forEach(function (s, i) {
      if (!isObj(s)) return;
      var sp0 = "series[" + i + "]";
      if (typeof s.year === "number" && typeof s.endYear === "number" && s.endYear < s.year) {
        out.push(finding("warning", sp0 + ".endYear", t.endYear, "W_FIELD"));
      }
      var keptSeasons = [], seasonNumbers = Object.create(null);
      arr(s.seasons).forEach(function (se, j) {
        if (!isObj(se) || !validInt(se.number, 0, 999)) return;
        var sp = sp0 + ".seasons[" + j + "]";
        if (has(seasonNumbers, se.number)) {
          out.push(finding("error", sp + ".number", fmt(t.dupSeason, se.number, seasonNumbers[se.number]), "E_DUPLICATE_ID"));
          return;
        }
        var eps = arr(se.episodes).map(function (ep, k) { return { ep: ep, path: sp + ".episodes[" + k + "]" }; }).filter(function (x) { return keptEpisode(x.ep); });
        if (!eps.length) return;
        seasonNumbers[se.number] = sp + ".number";
        keptSeasons.push({ path: sp, eps: eps });
      });
      if (!(validId(s.id) && validText(s.name) && keptSeasons.length)) return;   // dropped: releases its ids
      claim(s.id, sp0 + ".id");
      keptSeasons.forEach(function (season) {
        var numbers = Object.create(null);
        season.eps.forEach(function (x) {
          if (has(numbers, x.ep.number)) {
            out.push(finding("error", x.path + ".number", fmt(t.dupEpisode, x.ep.number, numbers[x.ep.number]), "E_DUPLICATE_ID"));
            return;
          }
          numbers[x.ep.number] = x.path + ".number";
          claim(x.ep.id, x.path + ".id");
        });
      });
    });
    var includes = Object.create(null);     // id → type ("constructor" is just an id)
    arr(doc.includes).forEach(function (inc, i) {
      if (!isObj(inc)) return;
      var ip = "includes[" + i + "]";
      if (typeof inc.id === "string" && inc.id.indexOf("epg-") === 0) {
        out.push(finding("error", ip + ".id", t.epgPrefix, "E_INCLUDE"));
        return;
      }
      var type = typeof inc.type === "string" ? inc.type.trim().toLowerCase() : "";
      if (!validId(inc.id) || ["m3u", "xmltv", "stremio", "tsiptv-source"].indexOf(type) < 0 || !validUrl(inc.url)) return;
      if (type === "stremio" && !isStremioManifestUrl(inc.url)) return;   // E_INCLUDE: dropped
      claim(inc.id, ip + ".id");
      includes[inc.id] = type;
    });
    // epg links are xmltv includes named epg-<index> (spec §9.1); they are not in the id set.
    arr(doc.epg).forEach(function (_, i) { includes["epg-" + i] = "xmltv"; });

    function checkRef(ref, path, target) {
      if (typeof ref !== "string") return;
      var colon = ref.indexOf(":");
      if (colon > 0) {
        if (!has(includes, ref.slice(0, colon))) out.push(finding("warning", path, fmt(t.unknownRefInclude, ref)));
      } else if (!has(ids, ref) || /^includes\[/.test(ids[ref])) {
        out.push(finding("warning", path, fmt(target ? t.unknownTarget : t.unknownRef, ref)));
      }
    }

    var home = isObj(doc.layout) ? arr(doc.layout.home) : [];
    home.forEach(function (sec, i) {
      if (!isObj(sec)) return;
      var sp = "layout.home[" + i + "]";
      var q = sec.query;
      if (isObj(q)) {
        if (typeof q.include === "string") {
          var type = has(includes, q.include) ? includes[q.include] : undefined;
          if (type === undefined) out.push(finding("warning", sp + ".query.include", fmt(t.unknownInclude, q.include), "W_QUERY_REF"));
          else if (type === "xmltv") out.push(finding("warning", sp + ".query.include", fmt(t.xmltvInclude, q.include), "W_QUERY_REF"));
          else if (q.from === "catalog" && type !== "stremio") out.push(finding("warning", sp + ".query.include", fmt(t.notStremio, q.include), "W_QUERY_REF"));
        }
        arr(q.ids).forEach(function (ref, k) { checkRef(ref, sp + ".query.ids[" + k + "]", false); });
      }
      arr(sec.items).forEach(function (item, k) {
        if (isObj(item)) checkRef(item.target, sp + ".items[" + k + "].target", true);
      });
    });
    return kept;   // items and includes the app would keep (episodes included)
  }

  /* Document order of a finding's path ("channels[2].url"): a list of member/array positions. */
  function orderKey(doc, path, t) {
    if (path === t.root) return [];
    var key = [], node = doc, m;
    var re = /\[(\d+)\]|\[("(?:[^"\\]|\\.)*")\]|\.?([^.\[\]]+)/g;
    while ((m = re.exec(path))) {
      var token = m[1] !== undefined ? Number(m[1]) : m[2] !== undefined ? JSON.parse(m[2]) : m[3];
      var pos;
      if (Array.isArray(node) && typeof token === "number") pos = token;
      else if (isObj(node)) { pos = Object.keys(node).indexOf(String(token)); if (pos < 0) pos = Object.keys(node).length; }
      else pos = 0;
      key.push(pos);
      node = node !== null && typeof node === "object" ? node[token] : undefined;
    }
    return key;
  }
  function compareKeys(a, b) {
    for (var i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] - b[i];
    return a.length - b.length;
  }

  function jsonErrorMessage(e, text, t) {
    var msg = String(e && e.message || "");
    var line, col, m;
    if ((m = msg.match(/line (\d+) column (\d+)/))) { line = +m[1]; col = +m[2]; }
    else if ((m = msg.match(/position (\d+)/))) {
      var before = text.slice(0, +m[1]).split("\n");
      line = before.length; col = before[before.length - 1].length + 1;
    }
    return line ? fmt(t.notJsonAt, "", "", "").replace("%l", line).replace("%c", col) : t.notJsonNoPos;
  }

  var ajvCache = null;
  /* Dates, as the app reads them (spec §5, lead decision 2026-09-28):
     - the value is trimmed (updatedAt: C0 controls become spaces first), then
     - date-time: YYYY-MM-DDThh:mm:ss[.f](Z|±hh:mm), T and Z in either case, hours 00–23,
       minutes and seconds 00–59 (no leap second), offset hours 00–23 and minutes 00–59,
       and a day that exists;
     - date: YYYY-MM-DD with a day that exists. */
  var DATE_TIME_FORM = /^([0-9]{4}-[0-9]{2}-[0-9]{2})[Tt]([0-9]{2}):([0-9]{2}):([0-9]{2})(\.[0-9]+)?([Zz]|[+-][0-9]{2}:[0-9]{2})$/;
  var DATE_FORM = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
  function dateStatus(kind, raw) {
    if (typeof raw !== "string") return "shape";
    var v = (kind === "date-time" ? raw.replace(/[\u0000-\u001F]/g, " ") : raw).trim();
    if (kind === "date") return !DATE_FORM.test(v) ? "shape" : !isRealDate(v) ? "day" : v !== raw ? "spaces" : "ok";
    var m = DATE_TIME_FORM.exec(v);
    if (!m) return "shape";
    if (!isRealDate(m[1])) return "day";
    if (+m[2] > 23 || +m[3] > 59 || +m[4] > 59) return "time";
    var off = /^[+-]([0-9]{2}):([0-9]{2})$/.exec(m[6]);                 // PRD follow-up #3 row 5
    if (off && (+off[1] > 23 || +off[2] > 59)) return "time";
    if (kind === "date-time" && /[\u0000-\u001F]/.test(raw)) return "controls";   // app: shortText → W_FIELD
    if (v !== raw) return "spaces";
    return /[tz]/.test(v.slice(10)) ? "case" : "ok";
  }
  function strictDate(kind, s) {                    // the Ajv "format": valid, exactly as written
    var st = dateStatus(kind, s);
    return st === "ok" || (kind === "date-time" && st === "case" && /T/.test(s));   // RFC 3339 allows a lowercase z
  }
  function dateFinding(kind, here, raw, t) {
    switch (dateStatus(kind, raw)) {
      case "controls": return finding("warning", here, t.controlsReplaced, "W_FIELD");
      case "spaces": return finding("warning", here, t.spacesSchemaOnly);
      case "case": return finding("warning", here, t.dateTimeSchemaOnly);
      case "ok": return null;
      case "day": return finding("warning", here, t.badDate, "W_FIELD");
      case "time": return finding("warning", here, t.badTime, "W_FIELD");
      default: return finding("warning", here, kind === "date" ? t.badDateShape : t.badDateTimeShape, "W_FIELD");
    }
  }
  function isRealDate(s) {
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
    if (!m) return false;
    var y = +m[1], mo = +m[2], d = +m[3];
    if (mo < 1 || mo > 12 || d < 1) return false;
    var leap = (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
    var days = mo === 2 ? (leap ? 29 : 28) : [4, 6, 9, 11].indexOf(mo) >= 0 ? 30 : 31;
    return d <= days;
  }
  function compile(Ajv, schema) {
    if (ajvCache && ajvCache.schema === schema) return ajvCache;
    var ajv = new Ajv({ allErrors: true, strict: false, verbose: true });
    // Same calendar check as the app (TsiptvRules.isFullDate / isDateTime): 2026-02-30 is not a date.
    ajv.addFormat("date", { type: "string", validate: function (s) { return strictDate("date", s); } });
    ajv.addFormat("date-time", {
      type: "string",
      validate: function (s) {
        return strictDate("date-time", s);
      }
    });
    ajv.addFormat("uri", /^[A-Za-z][A-Za-z0-9+.-]*:[^\s]*$/);
    ajvCache = { schema: schema, validate: ajv.compile(schema), defs: defIndex(schema), rootAnyOf: rootAnyOfIndex(schema) };
    return ajvCache;
  }

  /* text: file content; schema: parsed JSON Schema; Ajv: the Ajv2020 constructor; lang: "vi"|"en".
     Returns { valid, errors: [{path, msg, code?}], warnings: [...] } in document order. */
  function validate(text, schema, Ajv, lang) {
    var t = T[lang] || T.en;
    function strip(list) { return list.map(function (f) { var o = { path: f.path, msg: f.msg }; if (f.code) o.code = f.code; return o; }); }
    function fail(msg, code) { return { valid: false, errors: [{ path: t.root, msg: msg, code: code }], warnings: [] }; }
    var bytes = typeof TextEncoder !== "undefined" ? new TextEncoder().encode(text).length : text.length;
    if (bytes > MAX_BYTES) return fail(t.tooLarge, "E_TOO_LARGE");
    var body = text.replace(/^\uFEFF/, "");
    if (body.charCodeAt(0) === 0x1F) return fail(t.compressed, "E_NOT_JSON");
    if (body.charAt(0) === "\uFFFD") return fail(t.notUtf8, "E_NOT_JSON");
    var doc;
    try {
      doc = JSON.parse(body);
    } catch (e) {
      return fail(jsonErrorMessage(e, body, t), "E_NOT_JSON");
    }
    if (!isObj(doc)) return fail(t.notObject, "E_NOT_JSON");

    var findings = [];
    if (doc.format !== "tsiptv-source") findings.push(finding("error", "format", t.notSource, "E_NOT_SOURCE"));
    if (doc.version !== 1) findings.push(finding("error", "version", t.version, "E_VERSION"));
    var hasContent = ["channels", "movies", "series", "includes"].some(function (k) { return arr(doc[k]).length > 0; });
    if (!hasContent) findings.push(finding("error", t.root, t.empty, "E_EMPTY"));

    var compiled = compile(Ajv, schema);
    if (!compiled.validate(doc)) {
      var own = findings.slice();
      schemaIssues(compiled.validate.errors, t, compiled.defs, compiled.rootAnyOf, !hasContent).forEach(function (x) {
        if ((x.path === "format" || x.path === "version") && own.some(function (o) { return o.path === x.path; })) return;
        findings.push(x);
      });
    }
    var hand = [];
    var kept = referencePass(doc, t, hand);
    if (isObj(doc.meta) && typeof doc.meta.updatedAt === "string" && Array.from(doc.meta.updatedAt.replace(/[\u0000-\u001F]/g, " ").trim()).length > 64) {
      findings = findings.filter(function (f) { return f.path !== "meta.updatedAt"; });
      hand.push(finding("warning", "meta.updatedAt", t.updatedAtTooLong, "W_FIELD"));
    }
    if (hasContent && kept === 0) findings.push(finding("error", t.root, t.emptyAfterDrop, "E_EMPTY"));
    hand.forEach(function (h) {
      // the hand-written epg- check carries the code; drop the schema's copy of it
      if (h.code === "E_INCLUDE") findings = findings.filter(function (f) { return !(f.epgPrefix && f.path === h.path); });
      findings.push(h);
    });

    findings.forEach(function (f, i) { f.order = orderKey(doc, f.path, t); f.seq = i; });
    findings.sort(function (a, b) { return compareKeys(a.order, b.order) || a.seq - b.seq; });
    var errors = findings.filter(function (f) { return f.level === "error"; });
    var warnings = findings.filter(function (f) { return f.level === "warning"; });
    return { valid: errors.length === 0, errors: strip(errors), warnings: strip(warnings) };
  }

  /* ------------------------------------------------------------------ browser UI */

  function mount() {
    if (typeof document === "undefined") return;
    var schemaPromise = null;
    function loadSchema() {
      if (!schemaPromise) {
        schemaPromise = fetch("/schema/tsiptv-source-v1.json", { cache: "no-cache" }).then(function (r) {
          if (!r.ok) throw new Error(String(r.status));
          return r.json();
        });
        schemaPromise.catch(function () { schemaPromise = null; });
      }
      return schemaPromise;
    }

    function el(tag, cls, text) {
      var n = document.createElement(tag);
      if (cls) n.className = cls;
      if (text !== undefined) n.textContent = text;
      return n;
    }

    function list(title, items, t) {
      var box = el("div", "vresult-group");
      box.appendChild(el("h3", null, title + " (" + items.length + ")"));
      var ul = el("ul", "vresult-list");
      items.slice(0, 200).forEach(function (x) {
        var li = el("li");
        li.appendChild(el("code", null, x.path));
        li.appendChild(document.createTextNode(" — " + x.msg + (x.code ? " (" + x.code + ")" : "")));
        ul.appendChild(li);
      });
      if (items.length > 200) ul.appendChild(el("li", null, fmt(t.more, "", "", items.length - 200)));
      box.appendChild(ul);
      return box;
    }

    Array.prototype.forEach.call(document.querySelectorAll("[data-validator]"), function (box) {
      var pane = box.closest("[data-lang-pane]");
      var lang = pane ? pane.getAttribute("data-lang-pane") : "en";
      var t = T[lang] || T.en;
      var area = box.querySelector("textarea");
      var file = box.querySelector("input[type=file]");
      var button = box.querySelector("[data-validate]");
      var out = box.querySelector("[data-result]");

      function show(nodes, ok) {
        out.textContent = "";
        out.className = "vresult " + (ok === true ? "vresult-ok" : ok === false ? "vresult-bad" : "");
        nodes.forEach(function (n) { out.appendChild(n); });
      }

      function run() {
        var text = area.value;
        if (!text.trim()) { show([el("p", null, t.emptyInput)]); return; }
        show([el("p", null, t.loading)]);
        loadSchema().then(function (schema) {
          var r = validate(text, schema, window.ajv2020, lang);
          var nodes = [el("p", "vresult-head", r.valid ? t.valid : t.invalid)];
          if (r.valid) nodes.push(el("p", null, t.validDetail));
          if (r.errors.length) nodes.push(list(t.errors, r.errors, t));
          if (r.warnings.length) nodes.push(list(t.warnings, r.warnings, t));
          else if (r.valid) nodes.push(el("p", null, t.noWarnings));
          show(nodes, r.valid);
        }, function () {
          show([el("p", null, t.schemaFailed)], false);
        });
      }

      button.addEventListener("click", run);
      file.addEventListener("change", function () {
        var f = file.files && file.files[0];
        if (!f) return;
        if (f.size > MAX_BYTES) { show([el("p", null, t.fileTooLarge)], false); return; }
        var reader = new FileReader();
        reader.onload = function () { area.value = String(reader.result || ""); run(); };
        reader.readAsText(f, "utf-8");
      });
    });
  }

  if (typeof document !== "undefined") {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount);
    else mount();
  }

  return { validate: validate, strings: T };
});
