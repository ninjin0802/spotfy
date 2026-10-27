// Web Player とアプリ（Kotlin の画面）をつなぐ橋。
// 画面はアプリ側で描き、ここはデータの取得と再生の操作だけを受け持つ。
// 呼び出し: __mb.call(id, method, args) → 結果は { type: 'bres', id, ok, value | error } で返す。
(function () {
  if (window.__mb) return;
  var send = function (m) { if (window.__native && __native.send) __native.send(m); };
  var pageDebug = function (event, detail) { send({ type: 'debug', event: event, detail: detail || '' }); };
  // 表示の言語（端末の言語が日本語なら日本語、それ以外は英語）。アプリ側の tr() と同じ決め方
  var JA = /^ja\b/i.test(navigator.language || '');
  function T(ja, en) { return JA ? ja : en; }
  function count(n) { return JA ? n.toLocaleString() : n.toLocaleString('en-US'); }

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
  // 登録簿のキーは変わらないので、名前→キーを覚えておく（再生の見張りで毎秒何十回も呼ぶため、毎回全件を探さない）
  var serviceKeys = {};
  function service(name) {
    if (!reg && platform()) reg = platform().getRegistry();
    if (!reg) return null;
    var key = serviceKeys[name];
    if (!key) {
      reg._map.forEach(function (_, rk) { if (rk && rk.description === name) key = rk; });
      if (key) serviceKeys[name] = key;
    }
    return key ? reg.resolve(key) : null;
  }

  // Web Player が自分で有効化する音声／インストリーム広告マネージャーを停止する。
  // ページ再読込後やプレーヤー更新後にも再適用できるよう、呼び出しは冪等にしておく。
  function adBlockingEnabled() {
    return !window.__native || !__native.getSetting || __native.getSetting('adblock', 'true') !== 'false';
  }
  function syncAdManagers() {
    var managers;
    try { managers = service('AdManagers'); } catch (e) { return; }
    var audio = managers && managers.audio;
    [audio, audio && audio.inStreamApi].forEach(function (manager) {
      if (!manager) return;
      var saved = manager.__nativeAdBlockState;
      try {
        if (adBlockingEnabled()) {
          if (saved) return;
          saved = { enable: manager.enable, descriptor: Object.getOwnPropertyDescriptor(manager, 'enable'), enabled: manager.enabled };
          if (typeof manager.disable === 'function') manager.disable();
          if (typeof manager.enable === 'function') Object.defineProperty(manager, 'enable', { value: function () { return false; }, configurable: true, writable: true });
          if ('enabled' in manager) manager.enabled = false;
          Object.defineProperty(manager, '__nativeAdBlockState', { value: saved, configurable: true });
          pageDebug('ad.manager.disabled');
        } else if (saved) {
          if (saved.descriptor) Object.defineProperty(manager, 'enable', saved.descriptor);
          else delete manager.enable;
          if (typeof saved.enable === 'function') saved.enable.call(manager);
          else if ('enabled' in manager) manager.enabled = saved.enabled;
          delete manager.__nativeAdBlockState;
          pageDebug('ad.manager.enabled');
        }
      } catch (e) {}
    });
  }

  // 広告は通常の曲と同じ PlayerAPI state に入る。広告識別子がある場合だけ扱い、
  // 通常の曲やポッドキャストには影響させない。
  function isAd(item) {
    var md = item && item.metadata || {};
    return !!item && (item.type === 'ad' || /^spotify:ad:/.test(item.uri || '') ||
      item.provider === 'ad' || !!item.advertiser || md.is_advertisement === true ||
      md.is_advertisement === 'true' || !!md.ad_id);
  }
  var mutedAdUri = '', mutedAdVolume = 1, mutedAdElements = [], adMuteAppliedAt = 0, adMuteTimedOut = false, lastAdLogUri = '';
  var adBreakActive = false, AD_RATE = 16, adSpeedLoggedUri = '', adSkipTried = false, adBreakStartedAt = 0, adCount = 0, adItemStartedAt = 0, adRateResets = 0;
  // 一部の広告はサーバー側キューから挿入され、広告 manager の停止だけでは外れないのでスキップも送る。
  // 無料プランでは大抵 restricted で断られるので、広告ブレイクにつき 1 回だけ
  function trySkipAd(p) {
    if (adSkipTried) return;
    adSkipTried = true;
    pageDebug('ad.skip.request');
    try {
      Promise.resolve(p.skipToNext()).then(function () {
        pageDebug('ad.skip.accepted');
      }).catch(function (error) {
        var message = String(error && (error.message || error.name) || '').toLowerCase();
        pageDebug('ad.skip.rejected', /restrict|disallow|not allowed|forbidden/.test(message) ? 'restricted' : 'unknown');
      });
    } catch (e) {}
  }
  function unmuteEl(el, volume) {
    try { el.muted = false; el.volume = volume; el.playbackRate = 1; } catch (e) {}
  }
  // 広告のあとで Web Player が音量を小さいまま付け直すことがあるので、しばらく見張って戻す。
  // クロスフェード中は曲の始まりがわざと小さいので、そのときは見張らない
  var adVolumeGuardTimer = 0;
  function guardVolumeAfterAd(restore) {
    if (adVolumeGuardTimer) clearInterval(adVolumeGuardTimer);
    var started = Date.now();
    adVolumeGuardTimer = setInterval(function () {
      if (Date.now() - started > 15000 || mutedAdUri) { clearInterval(adVolumeGuardTimer); adVolumeGuardTimer = 0; return; }
      if (xfSec) return;
      audioEl().then(function (el) {
        if (!el || mutedAdUri || !(el.muted || el.volume < restore * 0.5)) return;
        pageDebug('ad.volume.fixed', 'volume=' + el.volume + ' muted=' + el.muted + ' -> ' + restore);
        unmuteEl(el, restore);
      });
    }, 500);
  }
  function restoreAdVolume(event) {
    if (!mutedAdUri) return;
    var restore = mutedAdVolume, elements = mutedAdElements;
    guardVolumeAfterAd(restore);
    mutedAdUri = '';
    mutedAdElements = [];
    adMuteAppliedAt = 0;
    elements.forEach(function (el) { unmuteEl(el, restore); });
    if (elements.length) pageDebug(event, 'elements=' + elements.length + ' volume=' + restore);
    else audioEl().then(function (el) {
      if (!el || mutedAdUri) return pageDebug('ad.mute.restore_missing');
      unmuteEl(el, restore);
      pageDebug(event, 'fallback volume=' + restore);
    });
  }
  // 広告 1 本ごとのまとめ。かかった秒数と、Web Player に速度を戻されて付け直した回数
  function logAdItemDone() {
    if (lastAdLogUri) pageDebug('ad.item.done', 'n=' + adCount + ' secs=' + ((Date.now() - adItemStartedAt) / 1000).toFixed(1) + ' resets=' + adRateResets);
  }
  function endAdBreak(event) {
    logAdItemDone();
    if (lastAdLogUri) pageDebug(event, 'ads=' + adCount + ' secs=' + Math.round((Date.now() - adBreakStartedAt) / 1000));
    lastAdLogUri = '';
    adCount = 0;
    restoreAdVolume('ad.mute.restored');
    adMuteTimedOut = false;
    adBreakActive = false;
    adSkipTried = false;
  }
  function handleAdPlayback() {
    syncAdManagers();
    if (!adBlockingEnabled()) return endAdBreak('ad.state.blocking_off');
    var p = service('PlayerAPI');
    var state = p && p.getState();
    var item = state && state.item;
    if (!isAd(item)) return endAdBreak('ad.state.ended');
    var uri = item.uri || item.metadata && item.metadata.ad_id || 'advertisement';
    if (!adBreakActive) adBreakStartedAt = Date.now();
    adBreakActive = true;
    if (uri !== lastAdLogUri) {
      logAdItemDone();
      adCount++;
      adItemStartedAt = Date.now();
      adRateResets = 0;
      pageDebug('ad.detected', 'type=' + (item.type || 'unknown') + ' id=' + (item.uri ? 'uri' : item.metadata && item.metadata.ad_id ? 'metadata' : 'fallback') + ' n=' + adCount);
      // 速くなっているかを後でログから確かめられるように、2 秒後の再生位置を残す（16 倍速なら 30 秒ほど進む）
      setTimeout(function () {
        audioEl().then(function (el) {
          if (el && lastAdLogUri === uri) pageDebug('ad.media', 'pos=' + Math.round(el.currentTime) + ' dur=' + (isFinite(el.duration) ? Math.round(el.duration) : '?') + ' rate=' + el.playbackRate + ' paused=' + el.paused);
        });
      }, 2000);
    }
    lastAdLogUri = uri;
    if (uri !== mutedAdUri && !adMuteTimedOut) {
      mutedAdUri = uri;
      // 広告が何本も続くときは、1 本ごとに安全装置の時間を数え直す（続けて数えると 3 本目の途中で音が戻る）
      adMuteAppliedAt = 0;
    }
    if (!adMuteTimedOut) audioEl().then(function (el) {
      if (!el || mutedAdUri !== uri) return;
      // Spotify sometimes leaves the ad as the current item after its audio break ended.
      // Only start the failsafe after the media element really received the mute.
      if (adMuteAppliedAt && Date.now() - adMuteAppliedAt >= 75000) {
        adMuteTimedOut = true;
        if (mutedAdElements.indexOf(el) < 0) mutedAdElements.push(el);
        return restoreAdVolume('ad.mute.failsafe');
      }
      if (el.volume > 0.001) mutedAdVolume = el.volume;
      el.muted = false;
      if (mutedAdElements.indexOf(el) < 0) mutedAdElements.push(el);
      el.volume = 0.001;
      // スキップは断られ、音声を止めると先へ進めなくなるので、広告を速く最後まで再生させる
      if (el.playbackRate < AD_RATE) {
        if (adSpeedLoggedUri === uri) adRateResets++;
        try { el.playbackRate = AD_RATE; } catch (e) { try { el.playbackRate = 4; } catch (e2) {} }
        // Web Player が速度を何度も戻してくるので付け直すが、ログは広告 1 本につき 1 回だけ
        if (adSpeedLoggedUri !== uri) {
          adSpeedLoggedUri = uri;
          pageDebug('ad.speed.applied', 'rate=' + el.playbackRate + ' duration=' + (isFinite(el.duration) ? Math.round(el.duration) : '?'));
        }
      }
      if (!adMuteAppliedAt) {
        adMuteAppliedAt = Date.now();
        pageDebug('ad.mute.applied');
      }
    });
    trySkipAd(p);
  }

  // ---- GraphQL（Web Player 自身のローダーを使う。ログイン情報もそのまま使われる） ----
  var GQL = {
    home: '5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1',
    getAlbum: '0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f',
    fetchPlaylist: '21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf',
    queryArtistOverview: '9f8134ef565e78621f1e1793555bd6633c5ac144ae0f89604ed3ae3f80b3c8e6',
    queryWhatsNewFeed: 'd889c8c936ab192af8ced595427f5ba2acdf63478fdc0a181c8d477f8322630e',
    browsePage: 'f5c4e6d668f5716464a231c1cc8b22c1cbf6ad68b09929fd7de813a30581298b',
    browseAll: 'dbd8b55e09a58afc52eab438bc228ba28fd72ac2f2148c6c26354980e4579001',
    recentSearches: 'ece96d7b82c820fb9938dd1df50dee3e53737d808c3883e3d01c467c423601b0',
    saveRecentSearches: 'ece96d7b82c820fb9938dd1df50dee3e53737d808c3883e3d01c467c423601b0',
    removeRecentSearches: 'ece96d7b82c820fb9938dd1df50dee3e53737d808c3883e3d01c467c423601b0',
    isFollowingUsers: 'c00e0cb6c7766e7230fc256cf4fe07aec63b53d1160a323940fce7b664e95596',
    followUsers: 'c00e0cb6c7766e7230fc256cf4fe07aec63b53d1160a323940fce7b664e95596',
    unfollowUsers: 'c00e0cb6c7766e7230fc256cf4fe07aec63b53d1160a323940fce7b664e95596',
    getTrack: 'fe16b8083eec2305854db612ca75589bcb71e009dc7a4d014f29bd534a4c01f5',
    searchTopResultsList: 'dd78eaff943eba629ed70ee25517b9cea0dcaa41193e2592ae2727660b21892c'
  };
  function gql(name, vars, op) {
    return platform().getGraphQLLoader()({ name: name, operation: op || 'query', sha256Hash: GQL[name], value: null }, vars);
  }
  // Spotify の API を直接呼ぶときの認証（Web Player 自身が持っているアクセストークンを使う）
  function apiHeaders() {
    var h = { accept: 'application/json', 'app-platform': 'WebPlayer' };
    try {
      var st = service('AuthorizationAPI').getState();
      var tok = st && st.token && st.token.accessToken;
      if (tok) h.authorization = 'Bearer ' + tok;
    } catch (e) {}
    if (!h.authorization && window.__mConnect && __mConnect.auth()) h.authorization = __mConnect.auth();
    return h;
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
  function darkColor(art) { var c = art && art.extractedColors && art.extractedColors.colorDark; return c && !c.isFallback ? c.hex : ''; }
  function names(list) {
    return ((list && list.items) || []).map(function (a) { return a.profile && a.profile.name || a.name || ''; }).filter(Boolean).join(', ');
  }
  // 説明文の HTML から文字だけを取り出す（DOMParser なので中の画像やスクリプトは動かない）
  function strip(html) { return new DOMParser().parseFromString(html || '', 'text/html').body.textContent || ''; }

  // GraphQL の各種カードを { uri, title, subtitle, image, round } にそろえる
  function card(w, uri) {
    var d = w && (w.data || w);
    if (w && uri && !w.uri) w.uri = uri;
    if (!d) return null;
    switch (d.__typename) {
      case 'Playlist': return { uri: d.uri, title: d.name, subtitle: strip(d.description) || (d.ownerV2 && d.ownerV2.data && d.ownerV2.data.name) || '', image: pickImg(d.images) };
      case 'Album': return { uri: d.uri, title: d.name, subtitle: names(d.artists), image: pickImg(d.coverArt), color: darkColor(d.coverArt), kind: (d.albumType || d.type || 'album').toLowerCase() };
      case 'Artist': return { uri: d.uri, title: d.profile && d.profile.name, subtitle: T('アーティスト', 'Artist'), image: pickImg(d.visuals || d), round: true };
      case 'Podcast': return { uri: d.uri, title: d.name, subtitle: d.publisher && d.publisher.name || T('ポッドキャスト', 'Podcast'), image: pickImg(d.coverArt), kind: 'show' };
      case 'Episode': return { uri: d.uri, title: d.name, subtitle: (d.podcastV2 && d.podcastV2.data && d.podcastV2.data.name || T('エピソード', 'Episode')) + (d.releaseDate && d.releaseDate.isoString ? ' • ' + d.releaseDate.isoString.slice(0, 10).replace(/^(\d+)-0?(\d+)-0?(\d+)$/, JA ? '$1年$2月$3日' : '$2/$3/$1') : ''), image: pickImg(d.coverArt), color: darkColor(d.coverArt), kind: 'episode' };
      case 'Track': return { uri: d.uri, title: d.name, subtitle: names(d.artists), image: pickImg(d.albumOfTrack) };
      case 'BrowseSectionContainer': {
        var cr = d.data && d.data.cardRepresentation;
        return cr ? { uri: w.uri || d.uri || '', title: cr.title && cr.title.transformedLabel || '', subtitle: cr.backgroundColor && cr.backgroundColor.hex || '', image: pickImg(cr.artwork), kind: 'genre' } : null;
      }
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
    home: function (a) {
      return gql('home', {
        homeEndUserIntegration: 'INTEGRATION_WEB_PLAYER', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        sp_t: cookie('sp_t'), facet: a.facet || '', sectionItemsLimit: 12, includeHomeChapterVideoCards: false, includeEpisodeContentRatingsV2: false
      }).then(function (r) {
        var h = r.data && r.data.home;
        if (!h) throw new Error('no home');
        // ポッドキャストの「フォロー中」は、Web 版では新着フィード（エピソードのみ）から作る
        if (a.facet === 'podcasts-following-chip') return gql('queryWhatsNewFeed', { offset: 0, limit: 30, onlyUnPlayedItems: false, includedContentTypes: ['EPISODE'] }).then(function (w) {
          var items = (((w.data || {}).whatsNewFeedItems || {}).items || []).map(function (x) { return card(x.content || x); }).filter(function (c) { return c && c.uri; });
          return { greeting: '', shortcuts: [], chips: (h.homeChips || []).map(function (c) { return { id: c.id, label: c.label && c.label.transformedLabel || '', sub: (c.subChips || []).map(function (x) { return { id: x.id, label: x.label && x.label.transformedLabel || '' }; }) }; }),
            sections: items.map(function (c) { return { title: '', items: [c], feed: true }; }), emptyPodcasts: !items.length };
        });
        var out = { greeting: h.greeting && h.greeting.transformedLabel || '', shortcuts: [], sections: [],
          // 上の「音楽」「ポッドキャスト」（選ぶと「フォロー中」が続く）
          chips: (h.homeChips || []).map(function (c) { return { id: c.id, label: c.label && c.label.transformedLabel || '', sub: (c.subChips || []).map(function (x) { return { id: x.id, label: x.label && x.label.transformedLabel || '' }; }) }; }) };
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
          // ポッドキャストのタブの「視聴番組」は、公式アプリと同じく上に小さく並べる
          else if (a.facet === 'podcasts-chip' && items.every(function (c) { return c.kind === 'show'; })) out.shows = items;
          // ポッドキャストのおすすめなどは 1 件ずつの大きなカード（公式アプリと同じ）
          else out.sections.push({ title: s.data && s.data.title && s.data.title.transformedLabel || '', items: items, feed: type === 'HomeFeedBaselineSectionData' || items.length === 1 });
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
        var kind = d.type === 'SINGLE' ? T('シングル', 'Single') : d.type === 'EP' ? 'EP' : d.type === 'COMPILATION' ? T('コンピレーション', 'Compilation') : T('アルバム', 'Album');
        return {
          kind: 'album', uri: d.uri, title: d.name, image: pickImg(d.coverArt), artist: names(d.artists),
          artistUri: d.artists && d.artists.items[0] && d.artists.items[0].uri || '',
          subtitle: kind + (year ? ' • ' + year : ''), saved: !!d.saved, date: d.date && d.date.isoString || '',
          tracks: (d.tracksV2.items || []).map(function (x) { var t = x.track; return { uri: t.uri, title: t.name, artist: names(t.artists), image: '', dur: t.duration && t.duration.totalMilliseconds || 0 }; }),
          shelves: d.moreAlbumsByArtist ? [{ title: T(names(d.artists) + 'のその他の作品', 'More by ' + names(d.artists)), items: ((d.moreAlbumsByArtist.items[0] || {}).discography || { popularReleasesAlbums: { items: [] } }).popularReleasesAlbums.items.map(card).filter(Boolean) }] : []
        };
      });
    },
    playlist: function (a) {
      return gql('fetchPlaylist', { uri: a.uri, offset: 0, limit: 200, enableWatchFeedEntrypoint: false }).then(function (r) {
        var d = r.data.playlistV2;
        var owner = d.ownerV2 && d.ownerV2.data && d.ownerV2.data.name || '';
        return {
          kind: 'playlist', uri: d.uri, title: d.name, image: pickImg(d.images), description: strip(d.description),
          artist: owner, subtitle: (d.followers ? T(d.followers.toLocaleString() + '件の保存 • ', count(d.followers) + ' saves • ') : '') + T((d.content.totalCount || 0) + '曲', (d.content.totalCount || 0) + ((d.content.totalCount || 0) === 1 ? ' song' : ' songs')),
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
        add(T('人気リリース', 'Popular releases'), disc.popularReleasesAlbums && disc.popularReleasesAlbums.items);
        add(T('アルバム', 'Albums'), disc.albums && disc.albums.items);
        add(T('シングルと EP', 'Singles and EPs'), disc.singles && disc.singles.items);
        add(T('ファンの間で人気', 'Fans also like'), d.relatedContent && d.relatedContent.relatedArtists && d.relatedContent.relatedArtists.items);
        var ml = d.stats && d.stats.monthlyListeners || 0;
        return {
          kind: 'artist', uri: d.uri, title: d.profile && d.profile.name, image: header || pickImg(d.visuals), round: !header, header: !!header,
          subtitle: ml ? (JA ? (ml >= 10000 ? (Math.round(ml / 1000) / 10) + '万' : ml.toLocaleString()) + '人の月間リスナー' : count(ml) + ' monthly listeners') : '', saved: !!d.saved,
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
          c.kind = (d.__typename || '').toLowerCase();
          if (d.__typename === 'Track') { c.subtitle = T('曲', 'Song') + ' • ' + names(d.artists); }
          else if (d.__typename === 'Album') { c.subtitle = (d.type === 'SINGLE' ? T('シングル', 'Single') : T('アルバム', 'Album')) + ' • ' + names(d.artists); }
          else if (d.__typename === 'Playlist') c.subtitle = T('プレイリスト', 'Playlist');
          return c;
        }).filter(Boolean);
      });
    },
    // 検索ページの「すべてを見る」（ジャンルのタイル）
    browse: function () {
      return gql('browseAll', { browseEndUserIntegration: 'INTEGRATION_WEB_PLAYER', pagePagination: { offset: 0, limit: 10 }, sectionPagination: { offset: 0, limit: 99 } }).then(function (r) {
        var out = [];
        (r.data.browseStart.sections.items || []).forEach(function (sec) {
          (sec.sectionItems.items || []).forEach(function (it) {
            var cr = it.content && it.content.data && it.content.data.data && it.content.data.data.cardRepresentation;
            if (!cr) return;
            out.push({ uri: it.uri, title: cr.title && cr.title.transformedLabel || '', subtitle: cr.backgroundColor && cr.backgroundColor.hex || '#555555', image: pickImg(cr.artwork) });
          });
        });
        return out;
      });
    },
    recent: function () {
      return gql('recentSearches', { limit: 50, includeAuthors: true, includeEpisodeContentRatingsV2: true }).then(function (r) {
        var items = (((r.data || {}).recentSearches || {}).recentSearchesItems || {}).items || [];
        return items.map(function (w) {
          var c = card(w); if (!c) return null;
          var t = w.data && w.data.__typename;
          c.kind = (t || '').toLowerCase();
          c.subtitle = t === 'Track' ? T('曲', 'Song') + ' • ' + names(w.data.artists) : t === 'Album' ? T('アルバム', 'Album') + ' • ' + names(w.data.artists) : t === 'Playlist' ? T('プレイリスト', 'Playlist') : t === 'Artist' ? T('アーティスト', 'Artist') : c.subtitle;
          return c;
        }).filter(Boolean);
      });
    },
    saveRecent: function (a) { return gql('saveRecentSearches', { uris: [a.uri] }, 'mutation').then(function () { return true; }); },
    removeRecent: function (a) { return gql('removeRecentSearches', { uris: [a.uri] }, 'mutation').then(function () { return true; }); },
    // 歌詞（Web Player と同じ color-lyrics。ログイン情報はページのものをそのまま使い、Spotify に直接聞く）
    lyrics: function (a) {
      var id = a.uri.split(':')[2];
      var img = encodeURIComponent(a.image || '');
      var h = apiHeaders();
      return fetch('https://spclient.wg.spotify.com/color-lyrics/v2/track/' + id + '/image/' + img + '?format=json&vocalRemoval=false&market=from_token', { headers: h })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (j) {
          var lines = (j && j.lyrics && j.lyrics.lines || []).filter(function (l) { return l.words && l.words !== '♪'; }).map(function (l) { return { t: +l.startTimeMs, w: l.words }; });
          var bg = j && j.colors && j.colors.background;
          return { synced: !!(j && j.lyrics && j.lyrics.syncType === 'LINE_SYNCED'), lines: lines, bg: bg == null ? 0 : bg | 0 };
        });
    },
    // 全画面プレーヤーの「アーティストについて」
    about: function (a) {
      return gql('queryArtistOverview', { uri: a.uri, locale: '', includePrerelease: false }).then(function (r) {
        var d = r.data.artistUnion;
        var hi = d.headerImage && d.headerImage.data && d.headerImage.data.sources || [];
        var av = d.visuals && d.visuals.avatarImage && d.visuals.avatarImage.sources || [];
        var bio = d.profile && d.profile.biography && d.profile.biography.text || '';
        return {
          uri: d.uri, name: d.profile && d.profile.name || '', image: (hi[0] || av[0] || {}).url || '',
          listeners: d.stats && d.stats.monthlyListeners || 0, saved: !!d.saved, bio: strip(bio),
          verified: !!((d.profile && d.profile.verified) || (d.onPlatformReputationTrait && d.onPlatformReputationTrait.verification && d.onPlatformReputationTrait.verification.isVerified))
        };
      });
    },
    // 次に流れる曲
    nextUp: function () {
      var p = service('PlayerAPI');
      return Promise.resolve(p.getQueue()).then(function (q) {
        var list = [].concat(q.queued || [], q.nextUp || []);
        var t = list[0] && (list[0].contextTrack || list[0]);
        if (!t || !t.uri) return null;
        var md = t.metadata || {};
        return { uri: t.uri, title: t.name || md.title || '', artist: (t.artists || []).map(function (x) { return x.name; }).join(', ') || md.artist_name || '', image: (t.images && t.images[0] && t.images[0].url) || md.image_url || '', dur: 0 };
      });
    },
    // ジャンルのページ（棚が並ぶ）
    genre: function (a) {
      return gql('browsePage', { uri: a.uri, browseEndUserIntegration: 'INTEGRATION_WEB_PLAYER', includeEpisodeContentRatingsV2: false, pagePagination: { offset: 0, limit: 20 }, sectionPagination: { offset: 0, limit: 20 } }).then(function (r) {
        var b = r.data.browse;
        var shelves = [];
        (b.sections.items || []).forEach(function (sec) {
          var items = (sec.sectionItems.items || []).map(function (it) { return card(it.content, it.uri); }).filter(function (c) { return c && c.uri && c.title; });
          if (items.length) shelves.push({ title: sec.data && sec.data.title && sec.data.title.transformedLabel || '', items: items });
        });
        return { kind: 'genre', uri: a.uri, title: b.header && b.header.title && b.header.title.transformedLabel || '', image: '', subtitle: b.header && b.header.color && b.header.color.hex || '', tracks: [], shelves: shelves };
      });
    },
    // プロフィール（公開プレイリスト・最近聴いたアーティスト・フォロワー）。id がなければ自分
    profile: function (a) {
      var u = service('UserAPI');
      return Promise.resolve(u.getUser()).then(function (me) {
        var id = a.id || me.username, mine = id === me.username;
        var base = 'https://spclient.wg.spotify.com/user-profile-view/v3/profile/' + encodeURIComponent(id);
        var pr = fetch(base + '?playlist_limit=20&artist_limit=10&episode_limit=10&market=from_token', { headers: apiHeaders() }).then(function (r) { return r.ok ? r.json() : {}; });
        var fol = mine ? Promise.resolve(false) : gql('isFollowingUsers', { uris: ['spotify:user:' + id] }).then(function (r) {
          var d = r.data || {}, k = Object.keys(d)[0], v = d[k];
          return !!(Array.isArray(v) ? (v[0] && (v[0].following !== undefined ? v[0].following : v[0])) : v && (v.following || (v.items && v.items[0] && v.items[0].following)));
        }).catch(function () { return false; });
        return Promise.all([pr, fol]).then(function (x) {
          var j = x[0];
          return {
            id: id, mine: mine, following: x[1],
            name: j.name || (mine ? me.displayName : '') || '', image: j.image_url || (mine && me.images && me.images[0] && me.images[0].url) || '',
            followers: j.followers_count || 0, followingCount: j.following_count || 0,
            playlists: (j.public_playlists || []).map(function (p) { return { uri: p.uri, title: p.name, subtitle: (p.followers_count ? T(p.followers_count + '人のフォロワー', count(p.followers_count) + ' followers') : T('プレイリスト', 'Playlist')), image: p.image_url || '' }; }),
            artists: (j.recently_played_artists || []).map(function (x) { return { uri: x.uri, title: x.name, subtitle: T('アーティスト', 'Artist'), image: x.image_url || '', round: true }; })
          };
        });
      });
    },
    // フォロワー／フォロー中の一覧（which: followers | following）
    followList: function (a) {
      return fetch('https://spclient.wg.spotify.com/user-profile-view/v3/profile/' + encodeURIComponent(a.id) + '/' + a.which + '?market=from_token', { headers: apiHeaders() })
        .then(function (r) { return r.ok ? r.json() : {}; })
        .then(function (j) {
          return (j.profiles || []).map(function (p) {
            var artist = /:artist:/.test(p.uri);
            return { uri: p.uri, title: p.name, subtitle: (artist ? T('アーティスト', 'Artist') : T('プロフィール', 'Profile')) + (p.followers_count ? ' • ' + T(p.followers_count.toLocaleString() + '人のフォロワー', count(p.followers_count) + ' followers') : ''), image: p.image_url || '', round: true, pinned: !!p.is_following, kind: artist ? 'artist' : 'user' };
          });
        });
    },
    // ユーザーのフォロー（アーティストはライブラリ）
    followUser: function (a) {
      if (/:artist:/.test(a.uri)) { var lib = service('LibraryAPI'); return Promise.resolve(a.on ? lib.add({ uris: [a.uri] }) : lib.remove({ uris: [a.uri] })).then(function () { return true; }); }
      return gql(a.on ? 'followUsers' : 'unfollowUsers', { uris: [a.uri] }, 'mutation').then(function () { return true; });
    },
    // キュー（再生中・次に再生・その後に続く曲）
    queueList: function () {
      var p = service('PlayerAPI');
      return Promise.resolve(p.getQueue()).then(function (q) {
        function t(x) {
          if (!x) return null;
          var md = x.metadata || {};
          var img = (x.album && x.album.images && (x.album.images[1] || x.album.images[0]) || {}).url || (x.images && x.images[0] && x.images[0].url) || md.image_url || '';
          return { uri: x.uri, uid: x.uid || '', title: x.name || md.title || '', artist: (x.artists || []).map(function (z) { return z.name; }).join(', ') || md.artist_name || '', image: img };
        }
        var st = p.getState();
        return { current: t(q.current), queued: (q.queued || []).map(t).filter(Boolean), next: (q.nextUp || []).slice(0, 50).map(t).filter(Boolean), context: st && st.context && st.context.metadata && st.context.metadata.context_description || '' };
      });
    },
    skipTo: function (a) { return Promise.resolve(service('PlayerAPI').skipTo({ uri: a.uri, uid: a.uid })).then(function () { return true; }); },
    // キューの並べ替え：item を target の前（before）／後ろ（after）へ
    reorder: function (a) {
      var opt = a.after ? { after: { uri: a.target.uri, uid: a.target.uid } } : { before: { uri: a.target.uri, uid: a.target.uid } };
      return Promise.resolve(service('PlayerAPI').reorderQueue([{ uri: a.item.uri, uid: a.item.uid }], opt)).then(function () { return true; });
    },
    unqueue: function (a) { return Promise.resolve(service('PlayerAPI').removeFromQueue([{ uri: a.uri, uid: a.uid }])).then(function () { return true; }); },
    createPlaylist: function (a) { return Promise.resolve(service('RootlistAPI').createPlaylist(a.name, { before: 'start' })).then(function (uri) { return uri || ''; }); },
    createFolder: function (a) { return Promise.resolve(service('RootlistAPI').createFolder(a.name, { before: 'start' })).then(function () { return true; }); },
    // プロフィールの名前と写真を変える（Web Player 自身の編集画面を裏で操作する。写真は JPEG の base64）
    editProfile: function (a) {
      var w = sleep;
      var back = location.pathname;
      function go(path) { history.pushState({}, '', path); dispatchEvent(new PopStateEvent('popstate', { state: {} })); }
      function find(sel, n) { return new Promise(function (res, rej) { (function t(k) { var e = document.querySelector(sel); if (e) return res(e); if (k > (n || 50)) return rej(new Error('not found ' + sel)); setTimeout(function () { t(k + 1); }, 100); })(0); }); }
      return Promise.resolve(service('UserAPI').getUser()).then(function (me) {
        go('/user/' + me.username);
        return find('main [data-testid="edit-image-button"]', 80);
      }).then(function (btn) {
        btn.click();
        return find('[data-testid="user-edit-name-input"]');
      }).then(function (input) {
        var form = input.closest('form');
        if (a.name != null) {
          var set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
          set.call(input, a.name);
          input.dispatchEvent(new Event('input', { bubbles: true }));
        }
        var step = Promise.resolve();
        if (a.image) {
          var bin = atob(a.image), bytes = new Uint8Array(bin.length);
          for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
          var file = new File([bytes], 'profile.jpg', { type: 'image/jpeg' });
          var fi = form.querySelector('input[type="file"]');
          var dt = new DataTransfer(); dt.items.add(file);
          fi.files = dt.files;
          fi.dispatchEvent(new Event('change', { bubbles: true }));
          step = w(300);
        }
        // 写真を選ぶと Spotify はすぐ画像を送り始め、その間は「保存…」で押せない。押せるようになるまで待つ
        function saveButton() { return [].find.call(form.querySelectorAll('button'), function (b) { return b.type === 'submit' && /保存|Save/.test(b.textContent); }); }
        function ready(n) {
          var b = saveButton();
          if (b && !b.disabled && b.getAttribute('aria-disabled') !== 'true' && !/…|\.\.\./.test(b.textContent)) return Promise.resolve(b);
          if (n > 200) return Promise.reject(new Error('save not ready'));
          return w(100).then(function () { return ready(n + 1); });
        }
        // 保存が終わって編集画面が閉じるまで待つ
        function closed(n) {
          if (!document.contains(form)) return Promise.resolve(true);
          if (n > 200) return Promise.reject(new Error('not saved'));
          return w(100).then(function () { return closed(n + 1); });
        }
        return step.then(function () { return ready(0); }).then(function (b) { b.click(); return closed(0); }).then(function () { return w(500); });
      }).then(function () { go(back); return true; }, function (e) {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); go(back); throw e;
      });
    },
    // クロスフェード（秒。0 でオフ）
    crossfade: function (a) { xfSec = a.sec | 0; xfWatch(); gapWatch(); seamSet(gapOn || xfSec > 0); return true; },
    gapless: function (a) { gapOn = !!a.on; gapWatch(); seamSet(gapOn || xfSec > 0); return true; },
    prewarm: function () { return prewarm(); },
    library: function () {
      var lib = service('LibraryAPI');
      return Promise.resolve(lib.getContents({ limit: 300, offset: 0 })).then(function (c) {
        return (c.items || []).map(function (x) {
          var img = x.images && x.images.slice().sort(function (p, q) { return (p.width || 300) - (q.width || 300); }).find(function (i) { return (i.width || 300) >= 150; });
          var kind = x.type === 'artist' ? T('アーティスト', 'Artist') : x.type === 'album' ? T('アルバム', 'Album') : x.type === 'show' ? T('ポッドキャスト', 'Podcast') : x.type === 'folder' ? T('フォルダ', 'Folder') : T('プレイリスト', 'Playlist');
          var by = x.owner && x.owner.name || (x.artists && x.artists.map(function (z) { return z.name; }).join(', ')) || '';
          if (x.type === 'liked-songs' || /:collection$/.test(x.uri)) { kind = T('プレイリスト', 'Playlist'); }
          return { uri: x.uri, title: x.name, subtitle: kind + (by ? ' • ' + by : ''), image: img && img.url || (x.images && x.images[0] && x.images[0].url) || '', round: x.type === 'artist', pinned: !!x.pinned, kind: x.type || '' };
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
    // 曲の「⋮」メニュー用：アルバム・アーティスト・お気に入りかどうか
    trackInfo: function (a) {
      var lib = service('LibraryAPI');
      return Promise.all([
        gql('getTrack', { uri: a.uri }).catch(function () { return null; }),
        Promise.resolve(lib.contains(a.uri)).catch(function () { return [false]; })
      ]).then(function (r) {
        var t = r[0] && r[0].data && r[0].data.trackUnion || {};
        var ar = (t.firstArtist && t.firstArtist.items && t.firstArtist.items[0]) || (t.artists && t.artists.items && t.artists.items[0]) || null;
        return {
          album: t.albumOfTrack && t.albumOfTrack.uri || '', albumName: t.albumOfTrack && t.albumOfTrack.name || '',
          artist: ar && ar.uri || '', artistName: ar && ar.profile && ar.profile.name || '', liked: !!(r[1] && r[1][0])
        };
      });
    },
    inLibrary: function (a) { return Promise.resolve(service('LibraryAPI').contains(a.uri)).then(function (x) { return !!(x && x[0]); }); },
    queue: function (a) { return Promise.resolve(service('PlayerAPI').addToQueue([{ uri: a.uri }])).then(function () { return true; }); },
    // ---- Spotify Connect（キャスト）：同じアカウントのスピーカー・PC・テレビなど ----
    devices: function () {
      var st = window.__mConnect ? __mConnect.get() : {}, c = st.cluster || {};
      var list = Object.keys(c.devices || {}).map(function (k) { var d = c.devices[k]; return { id: d.device_id || k, name: d.name || '', type: (d.device_type || '').toLowerCase(), hidden: !!(d.capabilities && d.capabilities.hidden) }; })
        .filter(function (d) { return !d.hidden; });
      return { self: st.self || '', active: c.active_device_id || st.self || '', list: list };
    },
    // 「あなたへのおすすめ新着」：フォロー中のアーティスト・番組の新着（type は '' / 'music' / 'podcast'）
    whatsNew: function (a) {
      var types = a.type === 'music' ? ['ALBUM'] : a.type === 'podcast' ? ['EPISODE'] : ['ALBUM', 'EPISODE'];
      return gql('queryWhatsNewFeed', { offset: 0, limit: 50, onlyUnPlayedItems: false, includedContentTypes: types }).then(function (w) {
        return (((w.data || {}).whatsNewFeedItems || {}).items || []).map(function (x) { return card(x.content || x); }).filter(function (c) { return c && c.uri; });
      });
    },
    // 「最近」：最近再生したアルバム・プレイリスト・アーティスト（新しい順、再生した時刻つき）
    recents: function () {
      var u = service('UserAPI');
      return Promise.resolve(u && u.getUser()).then(function (me) {
        var id = me && (me.username || me.id);
        if (!id) throw new Error('no user');
        return fetch('https://spclient.wg.spotify.com/recently-played/v3/user/' + encodeURIComponent(id) + '/recently-played?format=json&limit=50&filter=default,collection-new-episodes', { headers: apiHeaders() });
      }).then(function (r) {
        if (!r.ok) throw new Error('recently-played ' + r.status);
        return r.json();
      }).then(function (j) {
        var list = (j.playContexts || []).filter(function (c) { return /^spotify:(album|playlist|artist|user:[^:]+:collection)/.test(c.uri || ''); });
        // 名前と画像もここでまとめて取る（曲の一覧は要らないので 1 曲だけ頼む）
        return Promise.all(list.map(function (c) {
          return recentInfo(c.uri).then(null, function () { return {}; }).then(function (i) {
            i.uri = c.uri; i.at = c.lastPlayedTime || 0;
            return i;
          });
        }));
      });
    },
    transfer: function (a) {
      if (!window.__mConnect) throw new Error('not ready');
      return __mConnect.transfer(a.id).then(function (r) { return !!(r && (r.ok || r.status < 300)); });
    },
    follow: function (a) {
      var lib = service('LibraryAPI');
      return Promise.resolve(a.on ? lib.add({ uris: [a.uri] }) : lib.remove({ uris: [a.uri] })).then(function () { return true; });
    },
    play: function (a) {
      claimPlayback();
      var p = service('PlayerAPI');
      var opts = a.track ? { skipTo: { uri: a.track } } : {};
      return Promise.resolve(p.play({ uri: a.uri }, {}, opts)).then(function () { return true; });
    },
    toggle: function () {
      // 先読みで鳴らしている途中に押されたら、止めずにそのまま本当の再生にする
      if (warming) { claimPlayback(); return true; }
      if (playingNow()) return Promise.resolve(service('PlayerAPI').pause()).then(function () { return true; });
      return resumeHere();
    },
    pause: function () { seamDrop('user'); return Promise.resolve(service('PlayerAPI').pause()).then(function () { return true; }); },
    resume: function () { return resumeHere(); },
    // 先読みの途中に操作されたら、先読みが後で止めたり位置を戻したりしないよう、先に終わらせる
    next: function () { claimPlayback(); return Promise.resolve(service('PlayerAPI').skipToNext()).then(function () { return true; }); },
    prev: function () { claimPlayback(); return Promise.resolve(service('PlayerAPI').skipToPrevious()).then(function () { return true; }); },
    seek: function (a) { claimPlayback(); return Promise.resolve(service('PlayerAPI').seekTo(a.ms)).then(function () { return true; }); },
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

  // ---- クロスフェード ----
  // Web Player は再生要素を 1 つしか持たないため 2 曲を重ねて鳴らせない。
  // 代わりに、曲の終わりと次の曲の始まりを、それぞれ指定秒数の半分ずつで小さく／大きくする。
  // 小さくなりきる直前に次の曲へ進め（ギャップレスと同じ仕組み）、切り替えの無音を挟まない。
  // 音量は耳に自然に聞こえるよう、直線ではなく正弦の曲線で変える
  var xfSec = 0, xfTimer = null, xfBase = null;
  function audioEl() {
    var sdk = service('PlayerSDK'), st = sdk && sdk.harmony && sdk.harmony._streamer;
    if (!st || !st._playerPromise) return Promise.resolve(null);
    return st._playerPromise.then(function (p) { return p && p._player instanceof HTMLMediaElement ? p._player : null; }, function () { return null; });
  }
  function xfWatch() {
    if (xfTimer) return;
    xfTimer = setInterval(function () {
      if (!xfSec && xfBase == null) return;
      // 曲のつなぎ目は 2 台目の再生器がやる（失敗した曲だけ、ここで音量を上げ下げする）
      if (seam.on && seam.phase !== 'failed') return;
      // 広告のミュート中に音量を拾うと、広告後の曲がミュートの音量のままになる
      if (adBreakActive) return;
      audioEl().then(function (el) {
        if (!el || adBreakActive) return;
        if (!xfSec || el.paused || !isFinite(el.duration) || el.duration < xfSec * 3) {
          if (xfBase != null) { el.volume = xfBase; xfBase = null; }
          return;
        }
        if (xfBase == null) xfBase = el.volume > 0.01 ? el.volume : 1;
        var half = xfSec / 2, t = el.currentTime, rest = el.duration - t;
        var x = Math.max(0, Math.min(1, t / half, rest / half));
        el.volume = Math.max(0, Math.min(1, xfBase * Math.sin(x * Math.PI / 2)));
      });
    }, 100);
  }

  // ---- ギャップレス再生 ----
  // Web Player は 1 つの再生要素で曲を順番に切り替えるので、曲の終わりから次の曲が鳴るまで無音ができる。
  //  1) 曲が始まったら次の曲を先読みし、
  //  2) 残りが「切り替えにかかる時間」を切ったところで次の曲へ進める（時間は実際に測って合わせる）
  var gapOn = false, gapTimer = null, gapUri = '', gapLatency = 0.4, gapSkippedAt = 0;
  function gapWatch() {
    if (gapTimer) return;
    gapTimer = setInterval(function () {
      // クロスフェード中も、曲の切り替えの無音をなくすためにこの仕組みを使う
      if (!gapOn && !xfSec) return;
      var p = service('PlayerAPI'), st = p && p.getState();
      if (!st || !st.item) return;
      if (st.item.uri !== gapUri) {
        gapUri = st.item.uri;
        setTimeout(function () {
          var sdk = service('PlayerSDK'), s2 = sdk && sdk.harmony && sdk.harmony._streamer, lp = s2 && s2._listPlayer;
          if (lp && typeof lp._preloadUpcomingTrack === 'function') { try { lp._preloadUpcomingTrack(); } catch (e) {} }
        }, 3000);
      }
      audioEl().then(function (el) {
        if (!el) return;
        if (gapSkippedAt) {
          var waited = (performance.now() - gapSkippedAt) / 1000;
          if (!el.paused && el.currentTime > 0 && el.currentTime < 2) {
            var took = Math.max(0, waited - el.currentTime);
            if (took < 3) gapLatency = Math.min(1.5, Math.max(0.1, gapLatency * 0.6 + took * 0.4));
            gapSkippedAt = 0;
          } else if (waited > 5) gapSkippedAt = 0;
          return;
        }
        if (el.paused || !isFinite(el.duration) || el.duration < 10 || st.repeat === 2 || adBreakActive) return;
        // シームレス再生がつなぐ曲では、早めに次へ送らない（曲の最後まで鳴らす）
        if (seam.on && seam.phase !== 'failed') return;
        var rest = el.duration - el.currentTime;
        if (rest > 0 && rest <= gapLatency && !(st.restrictions && st.restrictions.canSkipNext === false)) {
          gapSkippedAt = performance.now();
          Promise.resolve(p.skipToNext()).catch(function () {});
        }
      });
    }, 100);
  }

  // ---- シームレス再生（2 台目の再生器で、次の曲を先に鳴らす） ----
  // Web Player は再生要素が 1 つで、曲ごとに作り直すので 0.3 秒ほど途切れる。次の曲を 2 台目の再生器に先に読み込んでおき、
  // 曲の終わりに 2 台目で鳴らし始める（クロスフェードなら重ねる）。曲の管理は Web Player（1 台目）のままなので、
  // 1 台目が次の曲へ進んだら音を消したまま位置を 2 台目に合わせ、そろったら音を 1 台目に戻して 2 台目を片付ける。
  // phase: '' → loading → ready → playing2（2 台目が鳴っている）→ handoff（1 台目に戻す）→ ''。失敗した曲は failed（今までの処理に任せる）
  // ギャップレス再生かクロスフェードがオンのとき使う。2 台目は鍵の仕組み（emeManager）も別に持つ（共有すると 1 台目の鍵が外れて止まる）
  // base：クロスフェードを始めたときの 1 台目の音量、gain2：2 台目と、入れ替えたあとの 1 台目の音量（base と同じ）
  var seam = { on: false, timer: null, tp: null, eme: null, uri: '', forUri: '', phase: '', startAt: 0, lead: 0, base: 1, gain2: 1, main: null, mutedMain: null, seekedAt: 0 };
  var SEAM_PRELOAD = 15;
  // 2 台目の再生は Spotify に報告しない（再生回数などは 1 台目が報告する）
  var seamTracker = (function () {
    var f = new Proxy(function () {}, { get: function (o, k) { return k === 'then' ? undefined : f; }, apply: function () { return f; } });
    return new Proxy({}, { get: function (o, k) { return k === 'then' ? undefined : k === 'getPlaybackMetadataStore' ? function () { return {}; } : f; } });
  })();
  function seamListPlayer() {
    var sdk = service('PlayerSDK'), st = sdk && sdk.harmony && sdk.harmony._streamer;
    return st && st._listPlayer;
  }
  function seamSet(on) {
    seam.on = on;
    if (!on) { seamDrop(); clearInterval(seam.timer); seam.timer = null; return; }
    if (!seam.timer) seam.timer = setInterval(seamTick, 50);
  }
  function seamDrop(reason) {
    if (!seam.tp && !seam.mutedMain) return;
    if (reason) pageDebug('seamless.abort', reason + ' phase=' + seam.phase);
    var tp = seam.tp, main = seam.mutedMain, moved = !!main || seam.phase === 'swap';
    seam.tp = null; seam.mutedMain = null; seam.uri = '';
    if (seam.phase !== 'failed') seam.phase = '';
    if (main) { main.muted = false; main.playbackRate = 1; }
    // 1 台目の音量を戻す（次の曲へ進んでいれば次の曲の音量、まだ前の曲ならクロスフェードで下げた分を戻す）
    if (seam.main && moved) seam.main.volume = seam.gain2;
    else if (seam.main && seam.lead) seam.main.volume = seam.base;
    seam.main = null;
    if (tp) {
      // 片付けるだけでは要素が鳴り続けることがあるので、要素も止めて空にする
      var e2 = tp._player;
      if (e2) { try { e2.muted = true; e2.pause(); e2.removeAttribute('src'); e2.load(); } catch (e) {} }
      try { Promise.resolve(tp.destroy()).catch(function () {}); } catch (e) {}
      // 2 台目の鍵のセッションも閉じる（残ると曲ごとに溜まって、再生が止まりやすくなる）
      if (seam.eme) { try { Promise.resolve(seam.eme.destroySessions()).catch(function () {}); } catch (e) {} }
    }
  }
  function seamPreload(curUri) {
    var lp = seamListPlayer();
    if (!lp || !lp._loadedList) { seam.phase = 'failed'; return; }
    seam.phase = 'loading';
    var started = Date.now();
    var fail = function (e) {
      if (seam.forUri !== curUri) return;
      seamDrop('preload ' + String(e && (e.message || e)));
      seam.phase = 'failed';
    };
    Promise.resolve(lp._loadedList.peekNext({ reason: 'trackdone', listConstants: {} })).then(function (next) {
      if (!next || typeof next !== 'object' || !next.fileId || next.isAd || next.playable === false) throw new Error('no-next');
      // Web Player が実際に次に流す曲と違うことがある（シャッフルやキューの変化）。違えば使わない
      if (seamUpcoming() !== next.uri) throw new Error('mismatch');
      return lp._getTrackPlayer().then(function (tp) {
        if (!seam.eme) {
          var m = tp._emeManager;
          var m2 = new m.constructor({ transport: m._transport, emeImpl: m._emeImpl, disallowCodecs: m._disallowCodecs, preferredKeySystems: m._preferredKeySystems, unauthServerCertificateBase: m._unauthServerCertificateBase });
          return Promise.resolve(m2.init()).then(function () { seam.eme = m2; return tp; });
        }
        return tp;
      }).then(function (tp) {
        if (seam.forUri !== curUri || seam.phase !== 'loading') return;
        var tp2 = new tp.constructor({
          audioResolver: tp._audioResolver, videoResolver: tp._videoResolver, transport: tp._transport, tracker: seamTracker,
          emeManager: seam.eme, licenseURLResolver: tp._licenseURLResolver, newBufferPerTrack: true, createPlayer: tp._createPlayer,
          audioPlayerContainer: tp._audioPlayerContainer, videoPlayerContainer: tp._videoPlayerContainer, playabilityChecker: tp._playabilityChecker,
          cubicVolume: tp._cubicVolume
        });
        seam.tp = tp2; seam.uri = next.uri; seam.next = next;
        return tp2.load(next, { position: 0, paused: true, muted: true, initialPosition: 0, playbackSpeed: 1, duration: -1 }).then(function () {
          var e2 = tp2._player;
          if (!e2) throw new Error('no-element');
          // 読み込むと鳴り始めるので、再生器ごと止めて頭に戻しておく（要素だけ止めても再生器が鳴らし直す）
          e2.muted = true;
          return Promise.resolve(tp2.pause()).then(function () { return tp2.seek(0); }).then(function () {
          if (seam.tp !== tp2) return;
          seam.phase = 'ready';
          pageDebug('seamless.preload', 'ms=' + (Date.now() - started));
          });
        });
      });
    }).catch(fail);
  }
  // Web Player が次に流す曲
  function seamUpcoming() {
    var st = service('PlayerAPI').getState(), n = st && st.nextItems && st.nextItems[0];
    return n ? n.uri : '';
  }
  function seamStart(el) {
    var e2 = seam.tp._player;
    seam.base = xfBase != null ? xfBase : (el.volume > 0.01 ? el.volume : 1);
    // 2 台目は音量 1 で作られるので、1 台目の音量（Web Player の音量）にそろえる。ずれていると、入れ替えたときに大きさが変わる
    seam.gain2 = seam.base;
    seam.lead = xfSec;
    seam.main = el;
    e2.volume = seam.lead ? 0 : seam.gain2;
    e2.muted = false;
    Promise.resolve(seam.tp.resume()).catch(function (e) { seamDrop('play ' + (e && e.message)); seam.phase = 'failed'; });
    seam.phase = 'playing2'; seam.startAt = Date.now();
    // 1 台目が次の曲に切り替わった瞬間に音を消す（見張りの間隔を待つと、頭が一瞬重なる）
    var onEmptied = function () {
      el.removeEventListener('emptied', onEmptied);
      if (seam.phase !== 'playing2') return;
      el.muted = true; seam.mutedMain = el; seam.phase = 'handoff'; seam.startAt = Date.now();
      seam.seekedAt = 0; seam.seeks = 0; seam.seekLead = seam.seekLead || 0.25; seam.e2Last = -1; seam.e2StillAt = Date.now();
    };
    el.addEventListener('emptied', onEmptied);
    pageDebug('seamless.start', 'lead=' + seam.lead);
    // クロスフェードでは次の曲が聞こえ始めたので、画面と通知も次の曲にする
    if (seam.lead) { lastKey = ''; pushState(); }
  }
  // 1 台目を音なしのまま 2 台目の位置に合わせ、ずれが 20ms 未満になったら音を 1 台目に戻す。
  // 合わせ方はシークだけ（速さを変えると Web Player が位置を戻してくる）。シークにかかる時間を見込んで先へ飛ばし、ずれた分だけ見込みを直して数回やり直す
  function seamHandoff(el) {
    var e2 = seam.tp && seam.tp._player;
    if (!e2) return seamDrop('lost');
    // 位置合わせの間も、2 台目の音量がずれないよう付け直す（ずれていたらログに残す）
    if (Math.abs(e2.volume - seam.gain2) > 0.01) { pageDebug('seamless.volume', 'fixed ' + e2.volume.toFixed(2) + ' -> ' + seam.gain2.toFixed(2)); e2.volume = seam.gain2; }
    // 2 台目が止まったら（読み込みが追いつかないなど）、無音にならないようすぐ 1 台目の音を戻す
    if (e2.currentTime === seam.e2Last) {
      if (Date.now() - seam.e2StillAt > 300) { pageDebug('seamless.handoff', 'stalled'); return seamDrop(); }
    } else { seam.e2Last = e2.currentTime; seam.e2StillAt = Date.now(); }
    if (Date.now() - seam.startAt > 10000) {
      pageDebug('seamless.handoff', 'timeout off=' + Math.round((el.currentTime - e2.currentTime) * 1000) + 'ms');
      return seamDrop();
    }
    if (el.paused || el.readyState < 3 || el.seeking) return;
    if (seam.seekedAt) {
      var off = el.currentTime - e2.currentTime;
      if (Math.abs(off) < 0.02 || (seam.seeks >= 5 && Math.abs(off) < 0.06)) {
        // 一瞬で入れ替えるとぶつっと鳴るので、0.4 秒かけて 2 台目から 1 台目へ音を移す
        pageDebug('seamless.handoff', 'off=' + Math.round(off * 1000) + 'ms seeks=' + seam.seeks + ' ms=' + (Date.now() - seam.startAt));
        el.volume = 0; el.muted = false;
        seam.mutedMain = null; seam.main = el;
        seam.phase = 'swap'; seam.swapAt = Date.now();
        return;
      }
      if (seam.seeks >= 5) { pageDebug('seamless.handoff', 'unaligned off=' + Math.round(off * 1000) + 'ms'); return seamDrop(); }
      // 先に行き過ぎたら見込みを減らし、遅れたら増やす
      seam.seekLead = Math.max(0, Math.min(1, seam.seekLead - off));
    }
    el.currentTime = e2.currentTime + seam.seekLead;
    seam.seekedAt = Date.now(); seam.seeks++;
  }
  function seamTick() {
    if (!seam.on) return;
    var p = service('PlayerAPI'), st = p && p.getState();
    if (!st || !st.item) return;
    audioEl().then(function (el) {
      if (!el || !seam.on) return;
      if (seam.phase === 'handoff') return seamHandoff(el);
      if (seam.phase === 'swap') {
        // 同じ曲の同じ位置を重ねるので、足して一定になる直線で移す（sin/cos だと途中で 1.4 倍に大きくなる）
        var s2 = seam.tp && seam.tp._player, y = Math.min(1, (Date.now() - seam.swapAt) / 400);
        el.volume = seam.gain2 * y;
        if (s2) s2.volume = seam.gain2 * (1 - y);
        if (y >= 1) seamDrop();
        return;
      }
      if (seam.phase === 'playing2') {
        // クロスフェード：2 台目を少しずつ大きくする（1 台目は crossfade の見張りが小さくしていく）
        var e2 = seam.tp && seam.tp._player;
        if (!e2) return seamDrop('lost');
        // 2 台目が止まったら（読み込みが追いつかないなど）、無音にならないようすぐやめて 1 台目に任せる
        if (e2.currentTime === seam.e2Last) { if (Date.now() - seam.e2StillAt > 400) return seamDrop('stalled'); }
        else { seam.e2Last = e2.currentTime; seam.e2StillAt = Date.now(); }
        // クロスフェード：今の曲を少しずつ小さく、次の曲を少しずつ大きくする
        if (seam.lead) {
          var x = Math.min(1, (Date.now() - seam.startAt) / (seam.lead * 1000));
          e2.volume = seam.gain2 * Math.sin(x * Math.PI / 2);
          if (!seam.mutedMain) el.volume = seam.base * Math.cos(x * Math.PI / 2);
        }
        if (st.isPaused) seamDrop('paused');
        else if (st.item.uri !== seam.uri && st.item.uri !== seam.forUri) seamDrop('track-changed');
        else if (Date.now() - seam.startAt > (seam.lead + 10) * 1000) seamDrop('no-advance');
        return;
      }
      var cur = st.item.uri;
      if (seam.forUri !== cur) { seamDrop(seam.tp ? 'track-changed' : ''); seam.forUri = cur; seam.phase = ''; }
      if (seam.phase === 'failed' || st.isPaused || el.paused || isAd(st.item) || adBreakActive || st.repeat === 2) return;
      if (!isFinite(el.duration) || el.duration < 20) return;
      var rest = el.duration - el.currentTime;
      // 読み込みが進むと 2 台目が勝手に鳴り始めるので、出番まで止めて頭に戻し続ける
      var w2 = seam.phase === 'ready' && seam.tp._player;
      if (w2 && (!w2.paused || w2.currentTime > 0.05)) { w2.muted = true; Promise.resolve(seam.tp.pause()).catch(function () {}); w2.currentTime = 0; }
      if (!seam.phase && rest <= SEAM_PRELOAD + xfSec) seamPreload(cur);
      // 見張りは 50ms ごとなので、残り 60ms を切ったら鳴らし始める（少し重なっても途切れるよりよい）
      if (seam.phase === 'ready' && rest <= Math.max(xfSec, 0.06)) {
        // 読み込んだあとで次の曲が変わっていたら（キューに追加したなど）、使わずに今までのつなぎ方にする
        if (seamUpcoming() !== seam.uri) { pageDebug('seamless.preload', 'stale'); seamDrop(); seam.phase = 'failed'; return; }
        seamStart(el);
      }
    });
  }

  // ---- 再生状態 ----
  // クロスフェードで次の曲が鳴り始めてから、音が 1 台目に戻るまでは、表示を 2 台目（次の曲）に合わせる
  function snapshot() {
    var s = snapshotOfPlayer();
    var e2 = seam.tp && seam.tp._player;
    if (!s.has || !e2 || !seam.lead || !(seam.phase === 'playing2' || seam.phase === 'handoff' || seam.phase === 'swap')) return s;
    var pos = { pos: e2.currentTime * 1000, dur: isFinite(e2.duration) ? e2.duration * 1000 : s.dur, playing: true, buffering: false };
    // Web Player が次の曲へ進んだあとは、曲の情報は本物のまま、位置だけ 2 台目（1 台目はまだ位置合わせ中）
    if (seam.phase !== 'playing2') return Object.assign({}, s, pos);
    var st = service('PlayerAPI').getState();
    var it = (st && st.nextItems || []).filter(function (x) { return x.uri === seam.uri; })[0];
    if (!it) return s;
    return Object.assign({}, s, pos, {
      uri: it.uri, liked: false, artistUri: it.artists && it.artists[0] && it.artists[0].uri || '',
      title: it.name || '', artist: (it.artists || []).map(function (a) { return a.name; }).join(', '),
      album: it.album && it.album.name || '', image: it.images && it.images[0] && it.images[0].url || s.image
    });
  }
  function snapshotOfPlayer() {
    var p = service('PlayerAPI');
    var st = p && p.getState();
    if (!st || !st.item) return { has: false };
    var it = st.item, md = it.metadata || {};
    var img = md.image_xlarge_url || md.image_large_url || md.image_url || (it.images && it.images[0] && it.images[0].url) || '';
    // ほかの機器で再生中は spotify:image:… の形で来るので、画像の URL に直す
    if (img.indexOf('spotify:image:') === 0) img = 'https://i.scdn.co/image/' + img.slice(14);
    if (it.uri !== likedFor) checkLiked(it.uri);
    var ctx = st.context || {};
    // ほかの機器で再生中なら、その名前（このスマホなら空）
    var cst = window.__mConnect ? __mConnect.get() : {}, cl = cst.cluster || {};
    var act = cl.active_device_id && cl.active_device_id !== cst.self && cl.devices && cl.devices[cl.active_device_id];
    return {
      has: true, artistUri: it.artists && it.artists[0] && it.artists[0].uri || '', device: act ? act.name || '' : '', deviceType: act ? (act.device_type || '').toLowerCase() : '', uri: it.uri, liked: likedFor === it.uri ? liked : false,
      contextName: ctx.metadata && ctx.metadata.context_description || '', contextUri: ctx.uri || '', title: it.name || md.title || '', artist: (it.artists || []).map(function (a) { return a.name; }).join(', ') || md.artist_name || '',
      album: it.album && it.album.name || md.album_title || '', image: img, playing: !st.isPaused, buffering: !!st.isBuffering,
      // 位置は「いま」の値にして渡す（Web Player は最後に変化した時点の値と時刻を持っている）
      pos: (st.positionAsOfTimestamp || 0) + (!st.isPaused && st.timestamp ? Math.max(0, Date.now() - st.timestamp) : 0), dur: st.duration || it.duration && it.duration.milliseconds || 0,
      shuffle: !!st.shuffle, repeat: st.repeat || 0, context: st.context && st.context.uri || ''
    };
  }
  // 「最近」の 1 件分の名前・画像・作った人（ページを丸ごと読むと重いので、要るところだけ）
  function recentInfo(uri) {
    var kind = uri.split(':')[1];
    if (uri.indexOf(':collection') > 0) return Promise.resolve({ title: T('お気に入りの曲', 'Liked Songs') });
    if (kind === 'album') return gql('getAlbum', { uri: uri, locale: '', offset: 0, limit: 1 }).then(function (r) {
      var d = r.data.albumUnion;
      return { title: d.name, image: pickImg(d.coverArt), artist: names(d.artists) };
    });
    if (kind === 'artist') return gql('queryArtistOverview', { uri: uri, locale: '', includePrerelease: false }).then(function (r) {
      var d = r.data.artistUnion;
      return { title: d.profile && d.profile.name, image: pickImg(d.visuals), round: true };
    });
    return gql('fetchPlaylist', { uri: uri, offset: 0, limit: 1, enableWatchFeedEntrypoint: false }).then(function (r) {
      var d = r.data.playlistV2;
      return { title: d.name, image: pickImg(d.images), artist: d.ownerV2 && d.ownerV2.data && d.ownerV2.data.name || '' };
    });
  }

  // 起動直後の 1 回目の再生は、曲の場所・暗号の鍵・データを順に取るので 1 秒ほどかかる。
  // ほかの端末が再生する端末になっていなければ、音を消して一瞬だけ再生して止め、押したらすぐ鳴るようにしておく
  // playRequested：準備の前や途中に再生などの操作が頼まれた（そのときは準備で止めてはいけない）
  var warming = false, warmEl = null, playRequested = false;
  function endWarm() {
    if (!warming) return;
    if (warmEl) warmEl.muted = false;
    warming = false; warmEl = null;
    lastKey = ''; pushState();
  }
  // 再生の操作が来たら呼ぶ。準備で鳴らしている途中なら、音を戻してそのまま本当の再生にする
  function claimPlayback() {
    playRequested = true;
    endWarm();
    // 曲のつなぎ目の途中に操作されたら、2 台目をやめて Web Player に任せる
    if (seam.phase === 'playing2' || seam.phase === 'handoff' || seam.phase === 'swap') seamDrop('user');
  }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function prewarm() {
    var started = Date.now();
    var skip = function (why) { pageDebug('play.prewarm', 'skip ' + why); return false; };
    // 起動直後は端末の一覧も前回の曲もまだ届いていないので、揃うまで待つ（最大 10 秒）
    return (function waitCluster(n) {
      var c = window.__mConnect ? __mConnect.get() : {}, st = service('PlayerAPI').getState();
      if (c.self && c.cluster && st && st.item) return Promise.resolve(c);
      if (n >= 50) return Promise.resolve(null);
      return sleep(200).then(function () { return waitCluster(n + 1); });
    })(0).then(function (c) {
      if (!c) return skip('no-device-list');
      var cl = c.cluster, act = cl.active_device_id;
      // このスマホの ID は、端末一覧のキーの先頭部分だけが取れている（前方一致で比べる）
      // PC やスピーカーなど、ほかの端末が再生する端末になっているときは、止まっていても奪わない
      if (act && act.indexOf(c.self) !== 0 && (cl.devices || {})[act]) return skip('other-device');
      var p = service('PlayerAPI'), st = p.getState();
      if (!st || !st.item) return skip('no-track');
      if (!st.isPaused) return skip('playing');
      if (isAd(st.item)) return skip('ad');
      return audioEl().then(function (el) {
        if (!el) return skip('no-element');
        if (playRequested) return skip('tapped');
        var pos = st.positionAsOfTimestamp || 0;
        // muted なら音は出ず、ほかのアプリの音も止めない（音の出ない再生は音声フォーカスを取らない）
        warming = true; warmEl = el; el.muted = true;
        return new Promise(function (resolve) {
          var timer = 0;
          var finish = function (result) { clearTimeout(timer); el.removeEventListener('playing', onPlaying); resolve(result); };
          var onPlaying = function () { finish('ok'); };
          timer = setTimeout(function () { finish('timeout'); }, 8000);
          el.addEventListener('playing', onPlaying);
          Promise.resolve(p.resume()).catch(function () { finish('failed'); });
        }).then(function (result) {
          // 準備の途中に操作されたら、止めずに任せる（claimPlayback が音を戻している）
          if (playRequested) return skip('tapped');
          return Promise.resolve(p.pause()).catch(function () {}).then(function () {
            return Promise.resolve(p.seekTo(pos)).catch(function () {});
          }).then(function () {
            if (playRequested) return skip('tapped');
            endWarm();
            pageDebug('play.prewarm', result + ' ms=' + (Date.now() - started));
            return result === 'ok';
          });
        });
      });
    });
  }

  // 続きから再生する。起動直後はこのスマホが再生する端末としてまだ登録されておらず、resume が「No such device」で断られたり、
  // 返事が来ないまま止まったりする。すぐ送り、断られたか 4 秒返事が無ければ、端末一覧の知らせか 1 秒の早いほうで送り直す
  function registered() {
    var c = window.__mConnect ? __mConnect.get() : {}, devs = c.cluster && c.cluster.devices;
    return !!(c.self && devs && Object.keys(devs).some(function (k) { return k.indexOf(c.self) === 0; }));
  }
  var clusterWaiters = [];
  function nextClusterOr(ms) {
    return new Promise(function (resolve) {
      var timer = 0;
      var done = function () {
        clearTimeout(timer);
        var i = clusterWaiters.indexOf(done);
        if (i >= 0) clusterWaiters.splice(i, 1);
        resolve();
      };
      timer = setTimeout(done, ms);
      clusterWaiters.push(done);
    });
  }
  if (window.__mConnect) __mConnect.onChange(function () { clusterWaiters.slice().forEach(function (done) { done(); }); });
  function playingNow() {
    var st = service('PlayerAPI').getState();
    return !!(st && st.item && !st.isPaused);
  }
  function resumeHere() {
    claimPlayback();
    var started = Date.now();
    var done = function (tries) { pageDebug('play.resume', 'tries=' + tries + ' ms=' + (Date.now() - started)); return true; };
    return (function attempt(n) {
      if (playingNow()) return Promise.resolve(done(n));
      var timer = 0;
      var timeout = new Promise(function (r) { timer = setTimeout(function () { r('timeout'); }, 4000); });
      var sent = Promise.resolve().then(function () { return service('PlayerAPI').resume(); }).then(function () { return 'ok'; });
      return Promise.race([sent, timeout]).then(function (r) {
        clearTimeout(timer);
        if (r === 'ok' || playingNow()) return done(n + 1);
        throw new Error('timeout');
      }).catch(function (e) {
        clearTimeout(timer);
        var msg = String(e && (e.message || e));
        pageDebug('play.resume.retry', 'try=' + (n + 1) + ' registered=' + registered() + ' ' + msg);
        if (n >= 9 || !/no such device|timeout/i.test(msg)) throw e;
        return nextClusterOr(1000).then(function () { return attempt(n + 1); });
      });
    })(0);
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
    handleAdPlayback();
    // 先読みの一瞬の再生は、画面にも通知にも出さない
    if (warming) return;
    var s = snapshot();
    var key = JSON.stringify([s.uri, s.device, s.liked, s.playing, s.shuffle, s.repeat, s.buffering, Math.round(s.pos / 5000)]);
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
    if (action === 'play') resumeHere().catch(function () {});
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

  function checkHealth() {
    var sdk = service('PlayerSDK'), st = sdk && sdk.harmony && sdk.harmony._streamer;
    var player = service('PlayerAPI'), managers = service('AdManagers');
    var checks = [
      [T('データの取得（ホーム・検索・ライブラリ）', 'Loading data (Home, Search, Library)'), function () { return typeof platform().getGraphQLLoader === 'function'; }],
      [T('再生の操作', 'Playback controls'), function () { return ['getState', 'skipToNext', 'seekTo'].every(function (k) { return typeof player[k] === 'function'; }); }],
      [T('音量・広告の早送り・クロスフェード', 'Volume, ad fast-forward, crossfade'), function () { return !!st._playerPromise; }],
      [T('音質の切り替え', 'Audio quality'), function () { return typeof st.setMediaPlaybackQuality === 'function'; }],
      [T('ギャップレス再生', 'Gapless playback'), function () { return typeof st._listPlayer._preloadUpcomingTrack === 'function'; }],
      [T('広告の停止', 'Ad blocking'), function () { return !!managers.audio; }],
      [T('ログイン情報', 'Login info'), function () { return typeof service('AuthorizationAPI').getState === 'function'; }]
    ];
    var missing = checks.filter(function (c) { try { return !c[1](); } catch (e) { return true; } }).map(function (c) { return c[0]; });
    send({ type: 'bhealth', missing: missing });
  }

  // 準備ができたらアプリに知らせる。以後、Spotify のページ自体は描かない（アプリの画面の裏で動くだけ）
  (function waitReady(n) {
    var p = service('PlayerAPI');
    if (!p) return setTimeout(function () { waitReady(n + 1); }, n < 50 ? 200 : 1000);
    try { p.getEvents().addListener('update', pushState); } catch (e) {}
    if (window.__mConnect) __mConnect.onChange(function () { lastKey = ''; pushState(); });
    setInterval(pushState, 1000);
    // update event が来ない広告切替にも対応する。
    setInterval(handleAdPlayback, 300);
    var st = document.createElement('style');
    st.textContent = '.Root, #main > div { content-visibility: hidden !important; }';
    document.head.appendChild(st);
    send({ type: 'bready' });
    // Spotify が Web Player を更新すると、ここで使っている中の部品が見つからなくなることがある。
    // 準備が終わって少し待ってから確かめ、見つからない機能をアプリに知らせる
    setTimeout(checkHealth, 5000);
    pushState();
  })(0);
})();
