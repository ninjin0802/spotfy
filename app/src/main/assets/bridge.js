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
    home: '5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1',
    getAlbum: '0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f',
    fetchPlaylist: '21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf',
    queryArtistOverview: '9f8134ef565e78621f1e1793555bd6633c5ac144ae0f89604ed3ae3f80b3c8e6',
    searchTopResultsList: 'dd78eaff943eba629ed70ee25517b9cea0dcaa41193e2592ae2727660b21892c'
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
    // ---- 詳細ページ：{ kind, uri, title, subtitle, image, round, description, tracks[], shelves[] } ----
    album: function (a) {
      return gql('getAlbum', { uri: a.uri, locale: '', offset: 0, limit: 300 }).then(function (r) {
        var d = r.data.albumUnion;
        var year = d.date && d.date.isoString ? d.date.isoString.slice(0, 4) : '';
        var kind = d.type === 'SINGLE' ? 'シングル' : d.type === 'EP' ? 'EP' : d.type === 'COMPILATION' ? 'コンピレーション' : 'アルバム';
        return {
          kind: 'album', uri: d.uri, title: d.name, image: pickImg(d.coverArt), artist: names(d.artists),
          artistUri: d.artists && d.artists.items[0] && d.artists.items[0].uri || '',
          subtitle: kind + (year ? ' • ' + year : ''), saved: !!d.saved,
          tracks: (d.tracksV2.items || []).map(function (x) { var t = x.track; return { uri: t.uri, title: t.name, artist: names(t.artists), image: '', dur: t.duration && t.duration.totalMilliseconds || 0 }; }),
          shelves: d.moreAlbumsByArtist ? [{ title: names(d.artists) + 'のその他の作品', items: ((d.moreAlbumsByArtist.items[0] || {}).discography || { popularReleasesAlbums: { items: [] } }).popularReleasesAlbums.items.map(card).filter(Boolean) }] : []
        };
      });
    },
    playlist: function (a) {
      return gql('fetchPlaylist', { uri: a.uri, offset: 0, limit: 200, enableWatchFeedEntrypoint: false }).then(function (r) {
        var d = r.data.playlistV2;
        var owner = d.ownerV2 && d.ownerV2.data && d.ownerV2.data.name || '';
        return {
          kind: 'playlist', uri: d.uri, title: d.name, image: pickImg(d.images), description: strip(d.description),
          artist: owner, subtitle: (d.followers ? d.followers.toLocaleString() + '件の保存 • ' : '') + (d.content.totalCount || 0) + '曲',
          tracks: (d.content.items || []).map(function (x) {
            var t = x.itemV2 && x.itemV2.data;
            if (!t || !t.uri) return null;
            return { uri: t.uri, title: t.name, artist: names(t.artists), image: pickImg(t.albumOfTrack && t.albumOfTrack.coverArt), dur: t.trackDuration && t.trackDuration.totalMilliseconds || 0 };
          }).filter(Boolean),
          shelves: []
        };
      });
    },
    artist: function (a) {
      return gql('queryArtistOverview', { uri: a.uri, locale: '', includePrerelease: false }).then(function (r) {
        var d = r.data.artistUnion, disc = d.discography || {};
        var header = d.headerImage && d.headerImage.data ? pickImg(d.headerImage.data) : '';
        var shelves = [];
        function add(title, list) { var items = (list || []).map(function (x) { return card(x.releases ? x.releases.items[0] : x); }).filter(Boolean); if (items.length) shelves.push({ title: title, items: items }); }
        add('人気リリース', disc.popularReleasesAlbums && disc.popularReleasesAlbums.items);
        add('アルバム', disc.albums && disc.albums.items);
        add('シングルと EP', disc.singles && disc.singles.items);
        add('ファンの間で人気', d.relatedContent && d.relatedContent.relatedArtists && d.relatedContent.relatedArtists.items);
        var ml = d.stats && d.stats.monthlyListeners || 0;
        return {
          kind: 'artist', uri: d.uri, title: d.profile && d.profile.name, image: header || pickImg(d.visuals), round: !header, header: !!header,
          subtitle: ml ? (ml >= 10000 ? (Math.round(ml / 1000) / 10) + '万' : ml.toLocaleString()) + '人の月間リスナー' : '', saved: !!d.saved,
          tracks: ((disc.topTracks && disc.topTracks.items) || []).slice(0, 10).map(function (x) { var t = x.track; return { uri: t.uri, title: t.name, artist: names(t.artists), image: pickImg(t.albumOfTrack && t.albumOfTrack.coverArt), dur: t.duration && t.duration.totalMilliseconds || 0 }; }),
          shelves: shelves
        };
      });
    },
    search: function (a) {
      return gql('searchTopResultsList', { query: a.q, limit: 30, offset: 0, numberOfTopResults: 30, includeArtistHasConcertsField: false, includeAudiobooks: true, includeAuthors: true, includePreReleases: true, includeAlbumPreReleases: false, includeEpisodeContentRatingsV2: true, sectionFilters: ['GENERIC'] }).then(function (r) {
        var items = (((r.data || {}).searchV2 || {}).topResultsV2 || {}).itemsV2 || [];
        return items.map(function (x) {
          var w = x.item, d = w && w.data;
          if (!d) return null;
          var c = card(w);
          if (!c) return null;
          if (d.__typename === 'Track') { c.subtitle = '曲 • ' + names(d.artists); c.kind = 'track'; }
          else if (d.__typename === 'Album') { c.subtitle = (d.type === 'SINGLE' ? 'シングル' : 'アルバム') + ' • ' + names(d.artists); }
          else if (d.__typename === 'Playlist') c.subtitle = 'プレイリスト';
          return c;
        }).filter(Boolean);
      });
    },
    library: function () {
      var lib = service('LibraryAPI');
      return Promise.resolve(lib.getContents({ limit: 300, offset: 0 })).then(function (c) {
        return (c.items || []).map(function (x) {
          var img = x.images && x.images.slice().sort(function (p, q) { return (p.width || 300) - (q.width || 300); }).find(function (i) { return (i.width || 300) >= 150; });
          var kind = x.type === 'artist' ? 'アーティスト' : x.type === 'album' ? 'アルバム' : x.type === 'show' ? 'ポッドキャスト' : x.type === 'folder' ? 'フォルダ' : 'プレイリスト';
          var by = x.owner && x.owner.name || (x.artists && x.artists.map(function (z) { return z.name; }).join(', ')) || '';
          if (x.type === 'liked-songs' || /:collection$/.test(x.uri)) { kind = 'プレイリスト'; }
          return { uri: x.uri, title: x.name, subtitle: kind + (by ? ' • ' + by : ''), image: img && img.url || (x.images && x.images[0] && x.images[0].url) || '', round: x.type === 'artist', pinned: !!x.pinned };
        });
      });
    },
    liked: function () {
      var lib = service('LibraryAPI');
      return Promise.resolve(lib.getTracks({ limit: 500, offset: 0 })).then(function (c) {
        return (c.items || []).map(function (t) {
          return { uri: t.uri, title: t.name, artist: (t.artists || []).map(function (z) { return z.name; }).join(', '), image: (t.album && t.album.images && (t.album.images[1] || t.album.images[0]) || {}).url || '', dur: t.duration && t.duration.milliseconds || 0 };
        });
      });
    },
    follow: function (a) {
      var lib = service('LibraryAPI');
      return Promise.resolve(a.on ? lib.add({ uris: [a.uri] }) : lib.remove({ uris: [a.uri] })).then(function () { return true; });
    },
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
    // お気に入りの曲に追加／削除
    like: function (a) {
      var lib = service('LibraryAPI');
      return Promise.resolve(a.on ? lib.add({ uris: [a.uri] }) : lib.remove({ uris: [a.uri] })).then(function () {
        if (a.uri === likedFor) { liked = !!a.on; lastKey = ''; pushState(); }
        return true;
      });
    },
    // ストリーミングの音質（auto / low / normal / high / very_high）
    quality: function (a) {
      var sdk = service('PlayerSDK'), st = sdk && sdk.harmony && sdk.harmony._streamer;
      if (!st || typeof st.setMediaPlaybackQuality !== 'function') return false;
      return Promise.resolve(st.setMediaPlaybackQuality({ audio: a.level })).then(function (ok) { return ok !== false; }, function () { return false; });
    },
    bitrate: function () {
      var sdk = service('PlayerSDK'), st = sdk && sdk.harmony && sdk.harmony._streamer;
      if (!st || !st._playerPromise) return 0;
      return st._playerPromise.then(function (p) { return p && p._currentContent && p._currentContent._bitrate || 0; }, function () { return 0; });
    },
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
    if (it.uri !== likedFor) checkLiked(it.uri);
    var ctx = st.context || {};
    return {
      has: true, uri: it.uri, liked: likedFor === it.uri ? liked : false,
      contextName: ctx.metadata && ctx.metadata.context_description || '', contextUri: ctx.uri || '', title: it.name || md.title || '', artist: (it.artists || []).map(function (a) { return a.name; }).join(', ') || md.artist_name || '',
      album: it.album && it.album.name || md.album_title || '', image: img, playing: !st.isPaused, buffering: !!st.isBuffering,
      // 位置は「いま」の値にして渡す（Web Player は最後に変化した時点の値と時刻を持っている）
      pos: (st.positionAsOfTimestamp || 0) + (!st.isPaused && st.timestamp ? Math.max(0, Date.now() - st.timestamp) : 0), dur: st.duration || it.duration && it.duration.milliseconds || 0,
      shuffle: !!st.shuffle, repeat: st.repeat || 0, context: st.context && st.context.uri || ''
    };
  }
  // 再生中の曲がお気に入りかどうか（曲が変わったときに一度だけ調べる）
  var likedFor = '', liked = false;
  function checkLiked(uri) {
    likedFor = uri; liked = false;
    var lib = service('LibraryAPI');
    if (!lib) return;
    Promise.resolve(lib.contains(uri)).then(function (x) { if (likedFor === uri) { liked = !!(x && x[0]); lastKey = ''; pushState(); } }, function () {});
  }
  var lastKey = '';
  function pushState() {
    var s = snapshot();
    var key = JSON.stringify([s.uri, s.liked, s.playing, s.shuffle, s.repeat, s.buffering, Math.round(s.pos / 5000)]);
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
