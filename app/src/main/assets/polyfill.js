// Android WebView に無い Media Session API を補う（Web Player が利用するため、無いと再生処理が止まる）
(function () {
  if (navigator.mediaSession) return;
  var handlers = {};
  var session = {
    metadata: null,
    playbackState: 'none',
    setActionHandler: function (action, handler) { handlers[action] = handler; },
    setPositionState: function () {},
    setCameraActive: function () {},
    setMicrophoneActive: function () {}
  };
  Object.defineProperty(navigator, 'mediaSession', { value: session, configurable: true });
  // アプリ側（通知のボタンなど）から操作を呼び出せるようにする
  window.__mediaAction = function (action) {
    var h = handlers[action];
    if (h) h({ action: action });
    return !!h;
  };
  if (typeof window.MediaMetadata === 'undefined') {
    window.MediaMetadata = function (init) {
      init = init || {};
      this.title = init.title || '';
      this.artist = init.artist || '';
      this.album = init.album || '';
      this.artwork = init.artwork || [];
    };
  }
})();

// アプリとの連絡口。AndroidBridge は open.spotify.com のメインフレームにだけ注入される
(function () {
  if (window.__native || !window.AndroidBridge) return;
  var settings = {};
  var send = function (msg) { AndroidBridge.postMessage(JSON.stringify(msg)); };
  AndroidBridge.onmessage = function (e) {
    var m = JSON.parse(e.data);
    if (m.type === 'settings') settings = m.values || {};
    if (m.type === 'relay' && window.__relayDone) window.__relayDone(m.id, m.status, m.contentType, m.body);
  };
  window.__native = {
    relay: function (id, url, method, headers, body) {
      send({ type: 'relay', id: id, url: url, method: method, headers: headers, body: body });
    },
    send: function (msg) { send(msg); },
    setPlaying: function (playing) { send({ type: 'playing', value: playing }); },
    setSetting: function (name, value) { settings[name] = value; send({ type: 'setting', name: name, value: value }); },
    getSetting: function (name, fallback) { return name in settings ? settings[name] : fallback; }
  };
  send({ type: 'hello' });
})();

// Connect の操作リクエストはアプリ（端末内）から送り直す。
// WebView は全リクエストに X-Requested-With を付け、Spotify はそれが付いたコマンドを受理しても
// プレーヤーへ配信しないため、ページから直接送ると再生が始まらない。外部サーバーは経由しない。
(function () {
  if (window.__relayInstalled || !window.__native) return;
  window.__relayInstalled = true;
  var RELAYED = /^https:\/\/([a-z0-9-]+\.)*spotify\.com\/(connect-state|track-playback)\/v1\//;
  var original = window.fetch;
  var pending = {};
  var seq = 0;

  window.__relayDone = function (id, status, contentType, body) {
    var p = pending[id];
    if (!p) return;
    delete pending[id];
    if (status === 0) return p.reject(new TypeError('Failed to fetch'));
    if (status >= 200 && status < 300) remember(p.url, p.headers, body);
    var empty = status === 204 || status === 205 || status === 304;
    var headers = contentType ? { 'content-type': contentType } : {};
    p.resolve(new Response(empty ? null : body, { status: status, headers: headers }));
  };

  // Spotify Connect（キャスト先の切り替え）用に、デバイス一覧と認証ヘッダーを控えておく
  var connect = { headers: null, base: null, self: null, cluster: null, listeners: [] };
  function setCluster(c) {
    if (!c || !c.devices) return;
    connect.cluster = c;
    connect.listeners.forEach(function (f) { try { f(c); } catch (e) {} });
  }
  function remember(url, headers, text) {
    var m = url.match(/^(https:\/\/[^/]+)\/connect-state\/v1\/(devices\/hobs_([0-9a-f]+)|player\/command\/from\/([0-9a-f]+)|cluster)/);
    if (!m) return;
    connect.base = m[1];
    if (m[3] || m[4]) connect.self = m[3] || m[4];
    if (headers.authorization) connect.headers = headers;
    if (text && (m[3] || /cluster$/.test(m[2]))) { try { setCluster(JSON.parse(text)); } catch (e) {} }
  }
  window.__mConnect = {
    get: function () { return { self: connect.self, cluster: connect.cluster }; },
    auth: function () { return connect.headers && connect.headers.authorization; },
    onChange: function (f) { connect.listeners.push(f); },
    transfer: function (target) {
      if (!connect.base || !connect.self || !connect.headers) return Promise.reject(new Error('not ready'));
      var h = {};
      ['authorization', 'client-token', 'spotify-app-version', 'app-platform', 'accept-language'].forEach(function (k) {
        if (connect.headers[k]) h[k] = connect.headers[k];
      });
      h['content-type'] = 'application/json';
      return window.fetch(connect.base + '/connect-state/v1/connect/transfer/from/' + connect.self + '/to/' + target, {
        method: 'POST', headers: h, body: JSON.stringify({ transfer_options: { restore_paused: 'resume' } })
      });
    }
  };
  // dealer の WebSocket から届くデバイス一覧の更新も拾う
  var NativeWS = window.WebSocket;
  window.WebSocket = function (url, protocols) {
    var ws = protocols === undefined ? new NativeWS(url) : new NativeWS(url, protocols);
    if (/dealer/.test(String(url))) ws.addEventListener('message', function (e) {
      if (typeof e.data !== 'string' || e.data.indexOf('connect-state/v1/cluster') < 0) return;
      try {
        var m = JSON.parse(e.data), p = m.payloads && m.payloads[0];
        if (typeof p === 'string') p = JSON.parse(p);
        if (p && p.cluster) setCluster(p.cluster);
      } catch (err) {}
    });
    return ws;
  };
  window.WebSocket.prototype = NativeWS.prototype;
  ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (k) { window.WebSocket[k] = NativeWS[k]; });

  window.fetch = function (input, init) {
    var req = input instanceof Request ? input : null;
    var url = req ? req.url : String(input);
    var body = init && init.body !== undefined ? init.body : null;
    if (!RELAYED.test(url) || (body != null && typeof body !== 'string')) return original.apply(this, arguments);
    var method = ((init && init.method) || (req && req.method) || 'GET').toUpperCase();
    var headers = new Headers(req ? req.headers : undefined);
    new Headers((init && init.headers) || undefined).forEach(function (v, k) { headers.set(k, v); });
    var bodyReady = body == null && req && method !== 'GET' && method !== 'HEAD' ? req.text() : Promise.resolve(body);
    return bodyReady.then(function (b) {
      return new Promise(function (resolve, reject) {
        var id = ++seq;
        pending[id] = { resolve: resolve, reject: reject };
        var h = {};
        headers.forEach(function (v, k) { h[k] = v; });
        pending[id].url = url;
        pending[id].headers = h;
        window.__native.relay(id, url, method, h, b == null ? null : b);
      });
    });
  };
})();
