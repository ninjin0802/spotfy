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

// Connect の操作リクエストはアプリ（端末内）から送り直す。
// WebView は全リクエストに X-Requested-With を付け、Spotify はそれが付いたコマンドを受理しても
// プレーヤーへ配信しないため、ページから直接送ると再生が始まらない。外部サーバーは経由しない。
(function () {
  if (window.__relayInstalled || !window.AndroidBridge) return;
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
    var empty = status === 204 || status === 205 || status === 304;
    var headers = contentType ? { 'content-type': contentType } : {};
    p.resolve(new Response(empty ? null : body, { status: status, headers: headers }));
  };

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
        AndroidBridge.relay(id, url, method, JSON.stringify(h), b == null ? null : b);
      });
    });
  };
})();
