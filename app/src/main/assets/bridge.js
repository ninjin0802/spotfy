// Web Player とアプリ（Kotlin の画面）をつなぐ橋。
// 画面はアプリ側で描き、ここはデータの取得と再生の操作だけを受け持つ。
// 呼び出し: __mb.call(id, method, args) → 結果は { type: 'bres', id, ok, value | error } で返す。
(function () {
  if (window.__mb) return;
  var send = function (m) { if (window.__native && __native.send) __native.send(m); };

  // ---- Web Player の Platform（サービスの登録簿と GraphQL ローダー）を React の木から探す ----
  var plat = null, reg = null;
  function platform() {
    if (plat) return plat;
    var host = document.getElementById('main');
    if (!host) return null;
    var els = [host].concat([].slice.call(host.querySelectorAll('*')));
    for (var i = 0; i < els.length; i++) {
      var k = Object.keys(els[i]).find(function (x) { return x.indexOf('__reactContainer$') === 0; });
      if (!k) continue;
      var stack = [els[i][k]];
      while (stack.length) {
        var f = stack.pop(), v = f.memoizedProps && f.memoizedProps.value;
        if (v && typeof v === 'object' && typeof v.getRegistry === 'function') return (plat = v);
        if (f.sibling) stack.push(f.sibling);
        if (f.child) stack.push(f.child);
      }
    }
    return null;
  }
  function service(name) {
    if (!reg && platform()) reg = platform().getRegistry();
    if (!reg) return null;
    var key = null;
    reg._map.forEach(function (_, rk) { if (rk && rk.description === name) key = rk; });
    return key ? reg.resolve(key) : null;
  }

  // ---- GraphQL（Web Player 自身のローダーを使う。ログイン情報もそのまま使われる） ----
  var GQL = {
    home: '5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1'
  };
  function gql(name, vars) {
    return platform().getGraphQLLoader()({ name: name, operation: 'query', sha256Hash: GQL[name], value: null }, vars);
  }
  function cookie(name) {
    var m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
    return m ? decodeURIComponent(m[1]) : '';
  }

  // 画像は 300px 前後のものを選ぶ
  function pickImg(o) {
    var best = '';
    (function walk(x, d) {
      if (!x || typeof x !== 'object' || d > 6 || best) return;
      if (Array.isArray(x.sources) && x.sources.length) {
        var s = x.sources.slice().sort(function (a, b) { return (a.width || 300) - (b.width || 300); });
        var hit = s.find(function (z) { return (z.width || 300) >= 250; }) || s[s.length - 1];
        best = hit && hit.url || '';
        return;
      }
      for (var k in x) if (Object.prototype.hasOwnProperty.call(x, k)) walk(x[k], d + 1);
    })(o, 0);
    return best;
  }
  function names(list) {
    return ((list && list.items) || []).map(function (a) { return a.profile && a.profile.name || a.name || ''; }).filter(Boolean).join(', ');
  }
  // 説明文の HTML から文字だけを取り出す（DOMParser なので中の画像やスクリプトは動かない）
  function strip(html) { return new DOMParser().parseFromString(html || '', 'text/html').body.textContent || ''; }

  // GraphQL の各種カードを { uri, title, subtitle, image, round } にそろえる
  function card(w) {
    var d = w && (w.data || w);
    if (!d) return null;
    switch (d.__typename) {
      case 'Playlist': return { uri: d.uri, title: d.name, subtitle: strip(d.description) || (d.ownerV2 && d.ownerV2.data && d.ownerV2.data.name) || '', image: pickImg(d.images) };
      case 'Album': return { uri: d.uri, title: d.name, subtitle: names(d.artists), image: pickImg(d.coverArt) };
      case 'Artist': return { uri: d.uri, title: d.profile && d.profile.name, subtitle: 'アーティスト', image: pickImg(d.visuals || d), round: true };
      case 'Podcast': return { uri: d.uri, title: d.name, subtitle: d.publisher && d.publisher.name || 'ポッドキャスト', image: pickImg(d.coverArt) };
      case 'Episode': return { uri: d.uri, title: d.name, subtitle: d.podcastV2 && d.podcastV2.data && d.podcastV2.data.name || 'エピソード', image: pickImg(d.coverArt) };
      case 'Track': return { uri: d.uri, title: d.name, subtitle: names(d.artists), image: pickImg(d.albumOfTrack) };
      case 'List':
        // 「最近」の棚は中にまとめて入っている
        return null;
      default:
        if (d.uri && (d.name || d.profile)) return { uri: d.uri, title: d.name || d.profile.name, subtitle: '', image: pickImg(d) };
        return null;
    }
  }

  var methods = {
    // ホーム：あいさつ・ショートカット・棚
    home: function () {
      return gql('home', {
        homeEndUserIntegration: 'INTEGRATION_WEB_PLAYER', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        sp_t: cookie('sp_t'), facet: '', sectionItemsLimit: 12, includeHomeChapterVideoCards: false, includeEpisodeContentRatingsV2: false
      }).then(function (r) {
        var h = r.data && r.data.home;
        if (!h) throw new Error('no home');
        var out = { greeting: h.greeting && h.greeting.transformedLabel || '', shortcuts: [], sections: [] };
        (h.sectionContainer && h.sectionContainer.sections.items || []).forEach(function (s) {
          var type = s.data && s.data.__typename;
          var items = ((s.sectionItems && s.sectionItems.items) || []).map(function (it) { return card(it.content); });
          if (type === 'HomeRecentlyPlayedSectionData') {
            items = [];
            ((s.sectionItems && s.sectionItems.items) || []).forEach(function (it) {
              var l = it.content && it.content.data && it.content.data.items;
              ((l && l.items) || []).forEach(function (e) { var c = card(e.entity && e.entity.data && e.entity.data.identityTrait ? null : e.entity); if (c) items.push(c); });
            });
          }
          items = items.filter(function (c) { return c && c.uri && c.title; });
          if (!items.length) return;
          if (type === 'HomeShortsSectionData') out.shortcuts = items.slice(0, 8);
          else out.sections.push({ title: s.data && s.data.title && s.data.title.transformedLabel || '', items: items });
        });
        return out;
      });
    },
    state: function () { return snapshot(); },
    play: function (a) {
      var p = service('PlayerAPI');
      var opts = a.track ? { skipTo: { uri: a.track } } : {};
      return Promise.resolve(p.play({ uri: a.uri }, {}, opts)).then(function () { return true; });
    },
    toggle: function () { var p = service('PlayerAPI'); var st = p.getState(); return Promise.resolve(st && st.isPaused ? p.resume() : p.pause()).then(function () { return true; }); },
    pause: function () { return Promise.resolve(service('PlayerAPI').pause()).then(function () { return true; }); },
    resume: function () { return Promise.resolve(service('PlayerAPI').resume()).then(function () { return true; }); },
    next: function () { return Promise.resolve(service('PlayerAPI').skipToNext()).then(function () { return true; }); },
    prev: function () { return Promise.resolve(service('PlayerAPI').skipToPrevious()).then(function () { return true; }); },
    seek: function (a) { return Promise.resolve(service('PlayerAPI').seekTo(a.ms)).then(function () { return true; }); },
    shuffle: function (a) { return Promise.resolve(service('PlayerAPI').setShuffle(!!a.on)).then(function () { return true; }); },
    repeat: function (a) { return Promise.resolve(service('PlayerAPI').setRepeat(a.mode | 0)).then(function () { return true; }); },
    user: function () {
      var u = service('UserAPI');
      return Promise.resolve(u && u.getUser()).then(function (x) { return x ? { name: x.displayName || x.username || '', image: x.avatarUrl || (x.images && x.images[0] && x.images[0].url) || '' } : null; });
    }
  };

  // ---- 再生状態 ----
  function snapshot() {
    var p = service('PlayerAPI');
    var st = p && p.getState();
    if (!st || !st.item) return { has: false };
    var it = st.item, md = it.metadata || {};
    var img = md.image_xlarge_url || md.image_large_url || md.image_url || (it.images && it.images[0] && it.images[0].url) || '';
    return {
      has: true, uri: it.uri, title: it.name || md.title || '', artist: (it.artists || []).map(function (a) { return a.name; }).join(', ') || md.artist_name || '',
      album: it.album && it.album.name || md.album_title || '', image: img, playing: !st.isPaused, buffering: !!st.isBuffering,
      // 位置は「いま」の値にして渡す（Web Player は最後に変化した時点の値と時刻を持っている）
      pos: (st.positionAsOfTimestamp || 0) + (!st.isPaused && st.timestamp ? Math.max(0, Date.now() - st.timestamp) : 0), dur: st.duration || it.duration && it.duration.milliseconds || 0,
      shuffle: !!st.shuffle, repeat: st.repeat || 0, context: st.context && st.context.uri || ''
    };
  }
  var lastKey = '';
  function pushState() {
    var s = snapshot();
    var key = JSON.stringify([s.uri, s.playing, s.shuffle, s.repeat, s.buffering, Math.round(s.pos / 5000)]);
    if (key === lastKey) return;
    lastKey = key;
    send({ type: 'bstate', value: s });
    // 通知のメディア操作と、裏で動き続けるためのサービスにも伝える
    send({ type: 'playing', value: !!s.playing });
    if (s.has) send({ type: 'meta', title: s.title, artist: s.artist, album: s.album, art: s.image, playing: s.playing, pos: Math.round(s.pos), dur: Math.round(s.dur) });
  }

  // 通知のボタン（Android のメディアコントロール）から
  window.__mMedia = function (action, pos) {
    var p = service('PlayerAPI');
    if (!p) return;
    if (action === 'play') p.resume();
    else if (action === 'pause') p.pause();
    else if (action === 'next') p.skipToNext();
    else if (action === 'prev') p.skipToPrevious();
    else if (action === 'seek') p.seekTo(pos);
  };

  window.__mb = {
    call: function (id, method, args) {
      var fn = methods[method];
      if (!fn) return send({ type: 'bres', id: id, ok: false, error: 'unknown method ' + method });
      Promise.resolve().then(function () { return fn(args || {}); }).then(function (v) {
        send({ type: 'bres', id: id, ok: true, value: v === undefined ? null : v });
      }, function (e) {
        send({ type: 'bres', id: id, ok: false, error: String(e && (e.message || e.name) || e) });
      });
    }
  };

  // 準備ができたらアプリに知らせる。以後、Spotify のページ自体は描かない（アプリの画面の裏で動くだけ）
  (function waitReady(n) {
    var p = service('PlayerAPI');
    if (!p) return setTimeout(function () { waitReady(n + 1); }, n < 50 ? 200 : 1000);
    try { p.getEvents().addListener('update', pushState); } catch (e) {}
    setInterval(pushState, 1000);
    var st = document.createElement('style');
    st.textContent = '.Root, #main > div { content-visibility: hidden !important; }';
    document.head.appendChild(st);
    send({ type: 'bready' });
    pushState();
  })(0);
})();
