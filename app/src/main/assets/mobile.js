// Spotify Web Player をスマホ幅向けに組み替えるスクリプト（ページ読み込みごとに注入、冪等）
(function () {
  if (location.hostname !== "open.spotify.com" || window.__mobileUi) return;
  window.__mobileUi = true;

  var NAV_H = 56;
  var css = `
    html, body, #main, .Root, [data-testid="now-playing-bar"] { min-width: 0 !important; }
    html, body { width: 100vw !important; overflow: hidden !important; }
    .Root { height: calc(100vh - ${NAV_H}px) !important; width: 100vw !important; overflow: hidden !important; }
    #onetrust-consent-sdk { display: none !important; }

    /* 3 カラムのグリッドを、メインビューだけの 1 カラムに */
    .Root > div:first-child {
      grid-template-columns: 0 100vw 0 !important;
      grid-template-rows: 0 0 minmax(0, 1fr) auto !important;
      column-gap: 0 !important; row-gap: 0 !important; padding: 0 !important;
      width: 100vw !important; height: 100% !important; overflow: hidden !important;
    }
    html.m-search .Root > div:first-child { grid-template-rows: 0 120px minmax(0, 1fr) auto !important; }
    #main-view { margin: 0 !important; border-radius: 0 !important; }
    [data-testid="LayoutResizer__resize-bar"] { display: none !important; }

    /* ヘッダー（検索バー）は検索タブのときだけ表示 */
    #global-nav-bar { display: none !important; }
    /* 検索タブ：大きな見出し＋白い検索ボックス */
    html.m-search #global-nav-bar {
      display: flex !important; flex-direction: column !important; align-items: stretch !important;
      inset: 0 !important; width: 100vw !important; height: 120px !important; padding: 16px 16px 8px !important;
      box-sizing: border-box !important; gap: 12px !important; background: #121212 !important;
    }
    html.m-search #global-nav-bar::before {
      content: "検索"; font-size: 24px; font-weight: 700; color: #fff; line-height: 32px; padding-left: 44px;
    }
    html.m-search #global-nav-bar > :not(:has(form)), #global-nav-bar a[href*="download"],
    #global-nav-bar [data-testid="home-button"], html.m-search #global-nav-bar [data-testid="browse-button"] { display: none !important; }
    html.m-search #global-nav-bar > :has(form), html.m-search #global-nav-bar > :has(form) div:has(form) {
      margin: 0 !important; flex: none !important; max-width: none !important; width: 100% !important; height: 48px !important;
    }
    html.m-search #global-nav-bar form, html.m-search #global-nav-bar form > div { width: 100% !important; }
    html.m-search #global-nav-bar input {
      width: 100% !important; height: 48px !important; background: #fff !important; color: #000 !important;
      border-radius: 6px !important; box-shadow: none !important; font-weight: 600 !important; font-size: 16px !important;
    }
    html.m-search #global-nav-bar input::placeholder { color: #535353 !important; }
    html.m-search #global-nav-bar form button, html.m-search #global-nav-bar form svg { color: #121212 !important; fill: #121212 !important; }
    html.m-search #m-avatar { display: block; top: 16px; }
    /* ジャンルのカードは 2 列 */
    html.m-search main [role="grid"], html.m-search main .contentSpacing > div > div[style*="grid"] { grid-template-columns: repeat(2, minmax(0,1fr)) !important; }

    /* ライブラリ（左サイドバー）と再生中ビュー（右サイドバー）は全画面オーバーレイ */
    #Desktop_LeftSidebar_Id, .m-right { display: none !important; }
    html.m-lib #Desktop_LeftSidebar_Id, html.m-np .m-right {
      display: flex !important; position: fixed !important; top: 0 !important; left: 0 !important;
      width: 100vw !important; max-width: none !important; height: calc(100vh - ${NAV_H}px) !important;
      box-sizing: border-box !important;
      z-index: 9000 !important; background: #121212 !important; margin: 0 !important;
    }
    /* ライブラリ表示中もミニプレーヤーは見せる */
    html.m-lib #Desktop_LeftSidebar_Id { height: calc(100vh - ${NAV_H}px - 64px) !important; }
    html.m-lib #Desktop_LeftSidebar_Id *, html.m-np .m-right > * { max-width: none !important; }
    html.m-lib #Desktop_LeftSidebar_Id > *, html.m-np .m-right * { width: 100% !important; }

    /* ミニプレーヤー */
    .Root > div:first-child > div:has(> [data-testid="now-playing-bar"]) { padding: 0 !important; }
    [data-testid="now-playing-bar"] {
      margin: 0 8px 4px !important; padding: 0 !important; height: 56px !important; width: calc(100vw - 16px) !important; box-sizing: border-box !important;
      background: #2a2a2a !important; border-radius: 6px !important; overflow: hidden !important; position: relative !important; left: 8px !important;
    }
    [data-testid="now-playing-bar"] > div { padding: 0 8px 0 0 !important; height: 56px !important; min-width: 0 !important; }
    [data-testid="now-playing-bar"] > div > div:first-child { width: auto !important; flex: 1 1 0 !important; min-width: 0 !important; overflow: hidden !important; }
    [data-testid="now-playing-bar"] > div > div:first-child * { min-width: 0 !important; }
    [data-testid="cover-art-container"], [data-testid="cover-art-container"] * { width: 40px !important; height: 40px !important; min-width: 40px !important; border-radius: 4px; }
    [data-testid="now-playing-widget-v2"] { gap: 10px !important; }
    [data-testid="now-playing-widget-v2"] { padding-left: 8px !important; }
    [data-testid="now-playing-widget-v2"] [aria-label*="追加"],
    [data-testid="control-button-skip-back"], [data-testid="control-button-skip-forward"],
    [data-testid="control-button-repeat"], [data-testid="general-controls"] button[aria-label*="シャッフル"],
    [data-testid="playback-position"], [data-testid="playback-duration"],
    .m-hide { display: none !important; }
    [data-testid="now-playing-bar"] > div > div:nth-child(2) { width: auto !important; flex: 0 0 auto !important; position: static !important; }
    [data-testid="player-controls"] { width: auto !important; min-width: 48px !important; position: static !important; }
    [data-testid="general-controls"] { width: auto !important; margin: 0 !important; justify-content: flex-end !important; }
    [data-testid="general-controls"] > div { flex: 0 0 auto !important; width: auto !important; }
    [data-testid="player-controls"] > div:last-child {
      position: absolute !important; left: 8px; right: 8px; bottom: 0; height: 2px !important; margin: 0 !important; width: auto !important;
    }
    [data-testid="playback-progressbar"] { width: 100% !important; height: 2px !important; pointer-events: none; }
    [data-testid="playback-progressbar"] [data-testid="progress-bar-handle"] { display: none !important; }
    [data-testid="signup-bar"] { padding: 8px !important; }

    /* コンテンツ */
    .contentSpacing { padding-left: 16px !important; padding-right: 16px !important; }
    h1 { word-break: break-word; }
    [role="presentation"] > [role="row"], [data-testid="tracklist-row"] {
      grid-template-columns: [index] 24px [first] minmax(0,1fr) [last] 72px !important;
    }
    [role="row"] > div[aria-colindex="3"], [role="row"] > div[aria-colindex="4"] { display: none !important; }

    /* アカウント（左上のアイコンと左ドロワー） */
    #m-avatar {
      position: fixed; top: 20px; left: 16px; width: 32px; height: 32px; border-radius: 50%; z-index: 8000;
      border: 0; padding: 0; background: #535353 center/cover no-repeat; color: #000; font-weight: 700; font-size: 14px;
      display: none;
    }
    html.m-home #m-avatar { display: block; }
    html.m-home main [data-testid="carousel-scroller"]:has([role="checkbox"]) { padding-left: 44px !important; }
    #m-drawer-bg { position: fixed; inset: 0; background: rgba(0,0,0,.6); z-index: 10000; display: none; }
    #m-drawer {
      position: fixed; top: 0; bottom: 0; left: 0; width: 82vw; max-width: 340px; z-index: 10001;
      background: #1f1f1f; transform: translateX(-100%); transition: transform .22s ease; color: #fff;
      display: flex; flex-direction: column; font-family: inherit;
    }
    html.m-drawer #m-drawer-bg { display: block; }
    html.m-drawer #m-drawer { transform: none; }
    #m-drawer .me { display: flex; align-items: center; gap: 12px; padding: 24px 16px 16px; border-bottom: 1px solid #333; }
    #m-drawer .me .pic { width: 48px; height: 48px; border-radius: 50%; background: #535353 center/cover no-repeat; flex: none; }
    #m-drawer .me b { display: block; font-size: 18px; }
    #m-drawer .me small { color: #b3b3b3; font-size: 13px; }
    #m-drawer button.item {
      display: flex; align-items: center; gap: 16px; width: 100%; padding: 16px; background: none; border: 0;
      color: #fff; font-size: 16px; text-align: left;
    }
    #m-drawer button.item svg { width: 24px; height: 24px; fill: currentColor; flex: none; }

    /* アクセントカラーと AMOLED ブラック */
    #m-tabbar button.on, #m-drawer .accent { color: var(--m-accent, #1ed760) !important; }
    main [data-testid="play-button"] > span, [data-testid="action-bar-row"] [data-testid="play-button"] > span {
      background-color: var(--m-accent, #1ed760) !important;
    }
    [data-testid="playback-progressbar"] [data-testid="progress-bar"] { --fg-color: var(--m-accent, #1ed760) !important; }
    html.m-amoled body, html.m-amoled #main-view, html.m-amoled .Root, html.m-amoled #Desktop_LeftSidebar_Id,
    html.m-amoled #Desktop_LeftSidebar_Id *, html.m-amoled #m-settings, html.m-amoled #m-tabbar { background-color: #000 !important; }
    html.m-amoled.m-search #global-nav-bar { background-color: #000 !important; }
    html.m-amoled .main-view-container, html.m-amoled #global-nav-bar { background: #000 !important; }
    [data-testid="now-playing-bar"] { transition: background-color .4s ease; }

    /* アプリの設定画面 */
    #m-settings {
      position: fixed; inset: 0 0 ${NAV_H}px 0; z-index: 9500; background: #121212; color: #fff;
      overflow-y: auto; display: none; font-family: inherit; padding-bottom: 72px; box-sizing: border-box;
    }
    html.m-settings #m-settings { display: block; }
    #m-settings header { position: sticky; top: 0; display: flex; align-items: center; gap: 8px; padding: 8px; background: inherit; z-index: 1; }
    #m-settings header button { background: none; border: 0; color: #fff; width: 48px; height: 48px; }
    #m-settings header svg { width: 24px; height: 24px; fill: currentColor; }
    #m-settings header h1 { font-size: 20px; font-weight: 700; margin: 0; }
    #m-settings h2 { font-size: 15px; font-weight: 700; margin: 24px 16px 4px; color: #fff; }
    #m-settings .row { display: flex; align-items: center; gap: 16px; padding: 12px 16px; min-height: 48px; }
    #m-settings .row .t { flex: 1; min-width: 0; }
    #m-settings .row .t b { display: block; font-weight: 500; font-size: 16px; }
    #m-settings .row .t small { display: block; color: #a7a7a7; font-size: 13px; margin-top: 2px; }
    #m-settings select {
      background: #2a2a2a; color: #fff; border: 0; border-radius: 4px; padding: 8px; font-size: 14px; max-width: 40vw !important; flex: none;
    }
    #m-settings .sw { position: relative; width: 44px; height: 24px; flex: none; }
    #m-settings .sw input { opacity: 0; width: 0; height: 0; position: absolute; }
    #m-settings .sw span { position: absolute; inset: 0; border-radius: 12px; background: #727272; transition: .2s; }
    #m-settings .sw span::after { content: ""; position: absolute; left: 3px; top: 3px; width: 18px; height: 18px; border-radius: 50%; background: #fff; transition: .2s; }
    #m-settings .sw input:checked + span { background: var(--m-accent, #1ed760); }
    #m-settings .sw input:checked + span::after { transform: translateX(20px); }
    #m-settings .colors { display: flex; gap: 12px; }
    #m-settings .colors button { width: 28px; height: 28px; border-radius: 50%; border: 2px solid transparent; }
    #m-settings .colors button.on { border-color: #fff; }
    #m-settings .link { color: #a7a7a7; }

    /* 下部タブバー（固定） */
    #m-tabbar {
      position: fixed; left: 0; right: 0; bottom: 0; height: ${NAV_H}px; z-index: 9999;
      display: flex; background: rgba(0,0,0,.92); font-family: inherit;
    }
    #m-tabbar button {
      flex: 1; background: none; border: 0; color: #b3b3b3; font-size: 11px;
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px;
    }
    #m-tabbar button.on { color: #fff; font-weight: 700; }
    #m-tabbar svg { width: 24px; height: 24px; fill: currentColor; }
  `;

  var ICONS = {
    home: '<svg viewBox="0 0 24 24"><path d="M12 3 2 11h3v10h6v-6h2v6h6V11h3z"/></svg>',
    search: '<svg viewBox="0 0 24 24"><path d="M10 3a7 7 0 0 1 5.6 11.2l5.1 5.1-1.4 1.4-5.1-5.1A7 7 0 1 1 10 3zm0 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10z"/></svg>',
    lib: '<svg viewBox="0 0 24 24"><path d="M4 3h2v18H4zm5 0h2v18H9zm4.7.6 1.9-.6 5.6 17.5-1.9.6z"/></svg>'
  };

  var DRAWER_ICONS = {
    news: '<svg viewBox="0 0 24 24"><path d="M12 2a6 6 0 0 0-6 6v4l-2 3v1h16v-1l-2-3V8a6 6 0 0 0-6-6zm0 20a2.5 2.5 0 0 0 2.4-2H9.6A2.5 2.5 0 0 0 12 22z"/></svg>',
    friends: '<svg viewBox="0 0 24 24"><path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4 0-7 2-7 4.5V20h14v-2.5C16 15 13 13 9 13zm7.5-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm1 2.1c1.9.7 3.5 2.1 3.5 4.4V20h2v-2.5c0-2.1-2.3-3.9-5.5-4.4z"/></svg>',
    settings: '<svg viewBox="0 0 24 24"><path d="M19.4 13a7.5 7.5 0 0 0 0-2l2.1-1.6-2-3.5-2.5 1a7.4 7.4 0 0 0-1.7-1L15 3h-4l-.4 2.7a7.4 7.4 0 0 0-1.7 1l-2.5-1-2 3.5L6.6 11a7.5 7.5 0 0 0 0 2l-2.1 1.6 2 3.5 2.5-1c.5.4 1.1.7 1.7 1L11 21h4l.4-2.7c.6-.3 1.2-.6 1.7-1l2.5 1 2-3.5zM13 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z"/></svg>',
    logout: '<svg viewBox="0 0 24 24"><path d="M10 3H4v18h6v-2H6V5h4zm6.6 4.6-1.4 1.4 2 2H9v2h8.2l-2 2 1.4 1.4L21 12z"/></svg>'
  };

  // Spotify 自身のユーザーメニューを開き、該当項目を選ぶ
  function userMenu(match) {
    if (!click('[data-testid="user-widget-link"]')) return;
    var tries = 0;
    (function pick() {
      var items = document.querySelectorAll('[role="menu"] a, [role="menu"] button, [role="menuitem"]');
      for (var i = 0; i < items.length; i++) if (match(items[i])) return items[i].click();
      if (++tries < 20) setTimeout(pick, 100);
    })();
  }

  function account() {
    var b = document.querySelector('[data-testid="user-widget-link"]');
    var img = b && b.querySelector('img');
    return { name: b ? b.getAttribute('aria-label') || '' : '', pic: img ? img.src : '' };
  }

  function setPic(el, a) {
    el.style.backgroundImage = a.pic ? 'url("' + a.pic + '")' : '';
    el.textContent = a.pic ? '' : (a.name || '?').charAt(0);
  }

  // ---- アプリの設定（端末内の localStorage とアプリ側の設定のみ。外部には送らない） ----
  var ACCENTS = { green: '#1ed760', blue: '#4b9bff', purple: '#b06bff', pink: '#ff5fa2', orange: '#ff8a3d' };

  function pref(key, fallback) {
    try { var v = localStorage.getItem('m.' + key); return v == null ? fallback : v; } catch (e) { return fallback; }
  }
  function setPref(key, value) { try { localStorage.setItem('m.' + key, value); } catch (e) {} applyLook(); }

  function applyLook() {
    var root = document.documentElement;
    root.style.setProperty('--m-accent', ACCENTS[pref('accent', 'green')] || ACCENTS.green);
    root.classList.toggle('m-amoled', pref('amoled', '0') === '1');
  }

  function nativeSetting(name, value) {
    try { if (window.AndroidBridge && AndroidBridge.setSetting) AndroidBridge.setSetting(name, String(value)); } catch (e) {}
  }
  function nativeValue(name, fallback) {
    try { return window.AndroidBridge && AndroidBridge.getSetting ? AndroidBridge.getSetting(name, fallback) : fallback; } catch (e) { return fallback; }
  }

  function spaGo(path) {
    history.pushState({}, '', path);
    window.dispatchEvent(new PopStateEvent('popstate', { state: {} }));
  }

  // Spotify の設定ページ（/preferences）の項目をラベルで探す
  function spotifyControl(re) {
    var labels = document.querySelectorAll('main label[for]');
    for (var i = 0; i < labels.length; i++) {
      if (re.test(labels[i].textContent)) return document.getElementById(labels[i].htmlFor);
    }
    return null;
  }

  function setSelect(el, value) {
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(el, value);
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  var BACK = '<svg viewBox="0 0 24 24"><path d="M20 11H7.8l5.6-5.6L12 4l-8 8 8 8 1.4-1.4L7.8 13H20z"/></svg>';

  function row(title, detail, control) {
    var r = document.createElement('div');
    r.className = 'row';
    r.innerHTML = '<div class="t"><b></b>' + (detail ? '<small></small>' : '') + '</div>';
    r.querySelector('b').textContent = title;
    if (detail) r.querySelector('small').textContent = detail;
    if (control) r.appendChild(control);
    return r;
  }

  function switchEl(checked, onChange) {
    var l = document.createElement('label');
    l.className = 'sw';
    l.innerHTML = '<input type="checkbox"><span></span>';
    var i = l.querySelector('input');
    i.checked = checked;
    i.addEventListener('change', function () { onChange(i.checked); });
    return l;
  }

  function selectEl(options, value, onChange) {
    var sel = document.createElement('select');
    options.forEach(function (o) {
      var op = document.createElement('option');
      op.value = o[0]; op.textContent = o[1]; op.disabled = !!o[2];
      sel.appendChild(op);
    });
    sel.value = value;
    sel.addEventListener('change', function () { onChange(sel.value); });
    return sel;
  }

  // Spotify 側のスイッチをそのまま操作する行
  function spotifySwitch(title, detail, re) {
    var c = spotifyControl(re);
    if (!c) return null;
    return row(title, detail, switchEl(c.checked, function () { c.click(); }));
  }

  function spotifySelect(title, detail, re) {
    var c = spotifyControl(re);
    if (!c) return null;
    var opts = [].map.call(c.options, function (o) { return [o.value, o.text, o.disabled]; });
    return row(title, detail, selectEl(opts, c.value, function (v) { setSelect(c, v); }));
  }

  function renderSettings() {
    var box = document.getElementById('m-settings');
    box.innerHTML = '<header><button aria-label="戻る">' + BACK + '</button><h1>設定</h1></header>';
    box.querySelector('header button').addEventListener('click', closeSettings);
    function section(name, rows) {
      rows = rows.filter(Boolean);
      if (!rows.length) return;
      var h = document.createElement('h2');
      h.textContent = name;
      box.appendChild(h);
      rows.forEach(function (r) { box.appendChild(r); });
    }

    var quality = spotifyControl(/ストリーミングの音質|Streaming quality/);
    var lossless = quality && [].some.call(quality.options, function (o) { return /ロスレス|Lossless/i.test(o.text); });
    section('再生', [
      spotifySelect('ストリーミングの音質', lossless ? 'ロスレスは Premium で選べます' : 'ロスレスは Spotify Premium のみ（このアカウントでは選べません）', /ストリーミングの音質|Streaming quality/),
      spotifySwitch('音量の均一化', 'すべての曲を同じ音量で再生する', /音量の均一|Normalize volume/),
      spotifySwitch('自動再生', '曲が終わったら似た曲を再生する', /自動再生|Autoplay/),
      row('バックグラウンド再生', 'アプリを閉じても再生を続ける', switchEl(nativeValue('background', 'true') === 'true', function (v) { nativeSetting('background', v); }))
    ]);
    var colors = document.createElement('div');
    colors.className = 'colors';
    Object.keys(ACCENTS).forEach(function (k) {
      var b = document.createElement('button');
      b.style.background = ACCENTS[k];
      b.setAttribute('aria-label', k);
      if (pref('accent', 'green') === k) b.className = 'on';
      b.addEventListener('click', function () {
        setPref('accent', k);
        colors.querySelectorAll('button').forEach(function (x) { x.className = x === b ? 'on' : ''; });
      });
      colors.appendChild(b);
    });
    section('表示', [
      row('AMOLED ブラック', '背景を真っ黒にして電池を節約', switchEl(pref('amoled', '0') === '1', function (v) { setPref('amoled', v ? '1' : '0'); })),
      row('アクセントカラー', null, colors),
      row('画面の向き', null, selectEl([['portrait', '縦に固定'], ['auto', '自動回転']], nativeValue('orientation', 'portrait'), function (v) { nativeSetting('orientation', v); })),
      spotifySwitch('Canvas', '曲の再生中に短いループ動画を表示', /^Canvas$/),
      spotifySwitch('ミュージックビデオ', 'オフにすると音声のみで再生', /ミュージックビデオ|Music videos/)
    ]);
    section('ソーシャル', [
      spotifySwitch('再生状況を公開', 'フォロワーに聴いている曲を表示', /再生状況|listening activity/i),
      spotifySwitch('フォロワーとフォロー中を公開', null, /フォロワー＆フォロー中|followers/i)
    ]);
    var restart = document.createElement('button');
    restart.className = 'link';
    restart.style.cssText = 'background:none;border:0;font-size:14px';
    restart.textContent = '実行';
    restart.addEventListener('click', function () { location.href = '/'; });
    var more = document.createElement('button');
    more.className = 'link';
    more.style.cssText = 'background:none;border:0;font-size:14px';
    more.textContent = '開く';
    more.addEventListener('click', function () { document.documentElement.classList.remove('m-settings'); });
    section('アプリ', [
      spotifySelect('言語', '変更後はプレーヤーを再起動', /言語を選択|language/i),
      row('プレーヤーを再起動', '再生が止まったときに', restart),
      row('Spotify の詳細設定', 'イコライザー・ライブラリ表示など', more)
    ]);
  }

  function openSettings() {
    closeDrawer();
    document.documentElement.classList.add('m-settings');
    var box = document.getElementById('m-settings');
    box.innerHTML = '<header><button aria-label="戻る">' + BACK + '</button><h1>設定</h1></header><p style="padding:16px;color:#a7a7a7">読み込み中…</p>';
    box.querySelector('header button').addEventListener('click', closeSettings);
    if (location.pathname !== '/preferences') spaGo('/preferences');
    var tries = 0;
    (function wait() {
      if (spotifyControl(/ストリーミングの音質|Streaming quality/) || ++tries > 50) return renderSettings();
      setTimeout(wait, 100);
    })();
  }

  function closeSettings() {
    document.documentElement.classList.remove('m-settings');
    if (location.pathname === '/preferences') history.back();
  }

  // ミニプレーヤーをジャケットの色で染める
  var tintedSrc = '';
  function tintMiniPlayer() {
    var img = document.querySelector('[data-testid="now-playing-bar"] [data-testid="cover-art-image"]');
    var bar = document.querySelector('[data-testid="now-playing-bar"]');
    if (!img || !bar || !img.src || img.src === tintedSrc) return;
    tintedSrc = img.src;
    var probe = new Image();
    probe.crossOrigin = 'anonymous';
    probe.onload = function () {
      try {
        var c = document.createElement('canvas');
        c.width = c.height = 8;
        var g = c.getContext('2d');
        g.drawImage(probe, 0, 0, 8, 8);
        var d = g.getImageData(0, 0, 8, 8).data, r = 0, gr = 0, b = 0;
        for (var i = 0; i < d.length; i += 4) { r += d[i]; gr += d[i + 1]; b += d[i + 2]; }
        var n = d.length / 4, k = 0.45;
        bar.style.setProperty('background-color', 'rgb(' + Math.round(r / n * k) + ',' + Math.round(gr / n * k) + ',' + Math.round(b / n * k) + ')', 'important');
      } catch (e) {}
    };
    probe.src = img.src;
  }

  function closeDrawer() { document.documentElement.classList.remove('m-drawer'); }

  function openDrawer() {
    var a = account();
    var d = document.getElementById('m-drawer');
    setPic(d.querySelector('.pic'), a);
    d.querySelector('b').textContent = a.name;
    document.documentElement.classList.add('m-drawer');
  }

  function buildDrawer() {
    var av = document.createElement('button');
    av.id = 'm-avatar';
    av.setAttribute('aria-label', 'アカウント');
    av.addEventListener('click', openDrawer);
    document.body.appendChild(av);

    var bg = document.createElement('div');
    bg.id = 'm-drawer-bg';
    bg.addEventListener('click', closeDrawer);
    document.body.appendChild(bg);

    var d = document.createElement('nav');
    d.id = 'm-drawer';
    d.innerHTML = '<button class="me item"><div class="pic"></div><div><b></b><small>プロフィールを表示</small></div></button>';
    d.querySelector('.me').addEventListener('click', function () {
      closeDrawer();
      setTab('home');
      userMenu(function (e) { return /^\/user\//.test(e.getAttribute('href') || ''); });
    });
    [
      ['news', '最新情報', function () { click('[data-testid="whats-new-feed-button"]'); }],
      ['friends', '再生状況（フレンド）', function () { click('[data-testid="friend-activity-button"]'); document.documentElement.classList.add('m-np'); }],
      ['settings', '設定', openSettings],
      ['logout', 'ログアウト', function () { userMenu(function (e) { return /ログアウト|Log out/.test(e.textContent); }); }]
    ].forEach(function (it) {
      var b = document.createElement('button');
      b.className = 'item';
      b.innerHTML = DRAWER_ICONS[it[0]] + '<span>' + it[1] + '</span>';
      b.addEventListener('click', function () {
        closeDrawer();
        if (it[0] !== 'friends' && it[0] !== 'settings') setTab('home');
        it[2]();
      });
      d.appendChild(b);
    });
    document.body.appendChild(d);

    var st = document.createElement('section');
    st.id = 'm-settings';
    document.body.appendChild(st);
    applyLook();
  }

  function click(sel) { var el = document.querySelector(sel); if (el) el.click(); return !!el; }

  function setTab(name) {
    var h = document.documentElement.classList;
    h.toggle('m-lib', name === 'lib');
    h.toggle('m-search', name === 'search');
    h.remove('m-np');
    if (h.contains('m-settings')) closeSettings();
    if (name === 'lib') {
      var expand = document.querySelector('#Desktop_LeftSidebar_Id button[aria-label*="開く"], #Desktop_LeftSidebar_Id button[aria-label*="Open"]');
      if (expand) expand.click();
    }
    document.querySelectorAll('#m-tabbar button').forEach(function (b) {
      b.classList.toggle('on', b.dataset.tab === name);
    });
  }

  function buildTabbar() {
    var bar = document.createElement('nav');
    bar.id = 'm-tabbar';
    [['home', 'ホーム'], ['search', '検索'], ['lib', 'ライブラリ']].forEach(function (t) {
      var b = document.createElement('button');
      b.dataset.tab = t[0];
      b.innerHTML = ICONS[t[0]] + '<span>' + t[1] + '</span>';
      b.addEventListener('click', function () {
        if (t[0] === 'home') {
          click('[data-testid="home-button"]') || (location.href = '/');
        } else if (t[0] === 'search') {
          if (location.pathname.indexOf('/search') !== 0) spaGo('/search');
          setTimeout(function () { var i = document.querySelector('[data-testid="search-input"] input, input[data-testid="search-input"]'); if (i) i.focus(); }, 300);
        } else if (document.documentElement.classList.contains('m-lib')) {
          return setTab(currentTab());
        }
        setTab(t[0]);
      });
      bar.appendChild(b);
    });
    document.body.appendChild(bar);
    setTab(currentTab());
  }

  function currentTab() { return location.pathname.indexOf('/search') === 0 ? 'search' : 'home'; }

  // 再生バー右側（音量・キューなど）を隠し、右サイドバーに目印を付ける
  function tidy() {
    var grid = document.querySelector('.Root > div:first-child');
    if (grid) for (var i = 0; i < grid.children.length; i++) {
      var c = grid.children[i];
      if (getComputedStyle(c).gridArea.indexOf('right-sidebar') === 0) c.classList.add('m-right');
    }
    var vol = document.querySelector('[data-testid="now-playing-bar"] [data-testid="volume-bar"]');
    var ctl = document.querySelector('[data-testid="now-playing-bar"] [data-testid="player-controls"]');
    if (vol && ctl) {
      var n = vol;
      while (n.parentElement && !n.parentElement.contains(ctl)) n = n.parentElement;
      n.classList.add('m-hide');
    }
    if (!document.getElementById('m-tabbar') && document.body) { buildDrawer(); buildTabbar(); }
    tintMiniPlayer();
    // アカウントアイコンは公式アプリと同じくホーム画面にだけ出す
    var h = document.documentElement.classList;
    h.toggle('m-home', /^\/(intl-[a-z-]+\/?)?$/i.test(location.pathname) && !h.contains('m-lib') && !h.contains('m-search'));
    var av = document.getElementById('m-avatar');
    if (av && !av.dataset.ready) {
      var a = account();
      if (a.name) { setPic(av, a); av.dataset.ready = '1'; }
    }
    // 再生状態をアプリ側へ通知（バックグラウンド再生用のサービス制御）
    var pp = document.querySelector('[data-testid="control-button-playpause"]');
    var playing = !!pp && /一時停止|Pause/.test(pp.getAttribute('aria-label') || '');
    if (window.AndroidBridge && playing !== window.__mPlaying) {
      window.__mPlaying = playing;
      AndroidBridge.setPlaying(playing);
    }
  }

  var style = document.createElement('style');
  style.id = 'm-style';
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);

  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('#Desktop_LeftSidebar_Id a, #Desktop_LeftSidebar_Id [role="row"]');
    // ライブラリ内でリンクを開いたらオーバーレイを閉じる
    if (a) setTimeout(function () { setTab('home'); }, 0);
    // ミニプレーヤーのボタン以外をタップしたら全画面の再生中ビューを開く
    var bar = e.target.closest && e.target.closest('[data-testid="now-playing-bar"]');
    if (bar && !e.target.closest('button, a, [data-testid="playback-progressbar"]')) {
      e.preventDefault();
      e.stopPropagation();
      var h = document.documentElement.classList;
      var open = !h.contains('m-np');
      h.toggle('m-np', open);
      var right = document.querySelector('.m-right');
      if (open && right && right.textContent.length < 20) click('[data-testid="cover-art-button"]');
    }
  }, true);

  // Android の戻るボタン用：オーバーレイが開いていれば閉じて true を返す
  window.__mobileBack = function () {
    var h = document.documentElement.classList;
    if (h.contains('m-drawer')) { closeDrawer(); return true; }
    if (h.contains('m-settings')) { closeSettings(); return true; }
    if (h.contains('m-np') || h.contains('m-lib')) { setTab(currentTab()); return true; }
    return false;
  };

  var pending = false;
  new MutationObserver(function () {
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () { pending = false; tidy(); });
  }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label'] });
  tidy();
})();
