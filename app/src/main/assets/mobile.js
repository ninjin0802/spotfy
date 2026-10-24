// Spotify Web Player をスマホ幅向けに組み替えるスクリプト（ページ読み込みごとに注入、冪等）
(function () {
  if (location.hostname !== "open.spotify.com" || window.__mobileUi) return;
  window.__mobileUi = true;

  var NAV_H = 56;
  var css = `
    html, body, #main, .Root, [data-testid="now-playing-bar"] { min-width: 0 !important; }
    html, body { width: 100vw !important; overflow: hidden !important; }
    .Root { height: 100vh !important; width: 100vw !important; overflow: hidden !important; }
    #onetrust-consent-sdk { display: none !important; }
    /* 画面全体が横にずれないようにする（棚のカルーセルは従来どおり横スクロール可） */
    html, body { overscroll-behavior: none !important; }
    [data-overlayscrollbars-viewport] { overflow-x: hidden !important; overscroll-behavior-x: none !important; }
    .os-scrollbar-horizontal { display: none !important; }

    /* 3 カラムのグリッドを、メインビューだけの 1 カラムに */
    .Root > div:first-child {
      grid-template-columns: 0 100vw 0 !important;
      grid-template-rows: 0 0 minmax(0, 1fr) 0 !important;
      column-gap: 0 !important; row-gap: 0 !important; padding: 0 !important;
      width: 100vw !important; height: 100% !important; overflow: hidden !important;
    }
    html.m-search .Root > div:first-child { grid-template-rows: 0 120px minmax(0, 1fr) 0 !important; }
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
    html.m-search #global-nav-bar { position: relative !important; }
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
    /* ジャンルのカード：2 列、角丸、公式アプリと同じく画像は右下に傾けて置く */
    html.m-search main [data-testid="grid-container"] {
      grid-template-columns: repeat(2, minmax(0, 1fr)) !important; margin: 0 !important; width: 100% !important;
      gap: 12px !important; column-gap: 12px !important; row-gap: 12px !important;
    }
    html.m-search main [data-testid="grid-container"] > [role="listitem"] { padding: 0 !important; width: auto !important; }
    html.m-search main [data-testid="grid-container"] a { display: block !important; width: 100% !important; height: 100px !important; border-radius: 6px !important; overflow: hidden !important; }
    html.m-search main [data-testid="grid-container"] a > div { width: 100% !important; height: 100% !important; }
    html.m-search main [data-testid="grid-container"] img {
      width: 64px !important; height: 64px !important; right: -12px !important; bottom: -4px !important; left: auto !important; top: auto !important;
      transform: rotate(25deg) !important; position: absolute !important; box-shadow: 0 2px 8px rgba(0,0,0,.4);
    }
    html.m-search main [data-testid="grid-container"] a span { font-size: 16px !important; font-weight: 700 !important; }
    html.m-search main section h2 { font-size: 16px !important; font-weight: 700 !important; }
    html.m-search #m-avatar { top: 16px !important; }

    /* 検索ボックスを触ったら、最近の検索を全画面で出す（公式アプリの検索画面と同じ） */
    html.m-searching #floating-ui-popover-layer > *, html.m-searching [data-testid="search-dropdown"] {
      position: fixed !important; left: 0 !important; right: 0 !important; top: 120px !important; bottom: ${NAV_H}px !important;
      width: 100vw !important; max-width: none !important; max-height: none !important; transform: none !important;
      border-radius: 0 !important; background: #121212 !important; box-shadow: none !important; z-index: 9400 !important; overflow-y: auto !important;
    }
    html.m-searching.m-amoled #floating-ui-popover-layer > * { background: #000 !important; }
    #m-cancel { display: none; position: absolute; right: 16px; top: 16px; background: none; border: 0; color: #fff; font-size: 15px; font-weight: 600; height: 32px; z-index: 2; }
    html.m-search.m-searching #m-cancel { display: block; }

    /* ライブラリ（左サイドバー）と再生中ビュー（右サイドバー）は全画面オーバーレイ */
    #Desktop_LeftSidebar_Id, .m-right { display: none !important; }
    html.m-lib #Desktop_LeftSidebar_Id, html.m-np .m-right {
      display: flex !important; position: fixed !important; top: 0 !important; left: 0 !important;
      width: 100vw !important; max-width: none !important; height: calc(100vh - ${NAV_H}px) !important;
      box-sizing: border-box !important;
      z-index: 9000 !important; background: #121212 !important; margin: 0 !important;
    }
    html.m-lib #Desktop_LeftSidebar_Id button[aria-label*="全画面"], html.m-lib #Desktop_LeftSidebar_Id button[aria-label*="拡大"] { display: none !important; }
    /* ライブラリ表示中もミニプレーヤーは見せる */
    html.m-lib #Desktop_LeftSidebar_Id { height: calc(100vh - ${NAV_H}px - 64px) !important; }
    html.m-lib #Desktop_LeftSidebar_Id *, html.m-np .m-right > * { max-width: none !important; }
    html.m-lib #Desktop_LeftSidebar_Id > *, html.m-np .m-right * { width: 100% !important; }

    /* ミニプレーヤー */
    /* ミニプレーヤーはタブバーのすぐ上に浮かせ、コンテンツはその下までスクロールさせる */
    .Root > div:first-child > div:has(> [data-testid="now-playing-bar"]) {
      padding: 0 !important; position: fixed !important; left: 0; right: 0; bottom: ${NAV_H}px; z-index: 10000;
    }
    .main-view-container__scroll-node-child { padding-bottom: calc(${NAV_H}px + 80px) !important; }
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
    #m-drawer-bg { position: fixed; inset: 0; background: rgba(0,0,0,.6); z-index: 10002; display: none; }
    #m-drawer {
      position: fixed; top: 0; bottom: 0; left: 0; width: 82vw; max-width: 340px; z-index: 10003;
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
    html.m-amoled #Desktop_LeftSidebar_Id > *, html.m-amoled #m-settings { background-color: #000 !important; }
    /* 子要素すべてを塗るとホバー用の重ね要素まで不透明になり文字が隠れるので、色トークンだけ差し替える */
    html.m-amoled #Desktop_LeftSidebar_Id, html.m-amoled #main-view {
      --background-base: #000 !important; --background-elevated-base: #000 !important;
    }
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

    /* 全画面プレーヤー */
    #m-player {
      position: fixed; inset: 0; z-index: 10010; color: #fff; font-family: inherit; display: flex; flex-direction: column;
      padding: 4px 24px 20px; box-sizing: border-box; transform: translateY(100%); overflow: hidden;
      transition: transform .32s cubic-bezier(.2,.8,.2,1); background: #121212; touch-action: none;
    }
    #m-player.dragging { transition: none; }
    html.m-player #m-player { transform: none; }
    /* ジャケットをぼかした背景＋色のグラデーション */
    #m-player .bg { position: absolute; inset: -40px; z-index: -2; background: center/cover no-repeat; filter: blur(60px) saturate(1.4); opacity: .45; }
    #m-player .shade { position: absolute; inset: 0; z-index: -1;
      background: linear-gradient(to bottom, var(--m-tint, #404040) 0%, rgba(18,18,18,.55) 55%, #121212 100%); opacity: .92; }
    #m-player .grab { width: 36px; height: 4px; border-radius: 2px; background: rgba(255,255,255,.35); margin: 6px auto 0; flex: none; }
    #m-player .top { display: flex; align-items: center; height: 52px; margin: 0 -12px; flex: none; }
    #m-player .top .ctx { flex: 1; text-align: center; min-width: 0; }
    #m-player .top .ctx small { display: block; font-size: 10px; letter-spacing: .12em; text-transform: uppercase; color: rgba(255,255,255,.75); }
    #m-player .top .ctx b { display: block; font-size: 13px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    #m-player button { background: none; border: 0; color: inherit; padding: 0; display: flex; align-items: center; justify-content: center; -webkit-tap-highlight-color: transparent; }
    #m-player button:active { transform: scale(.92); }
    #m-player svg { fill: currentColor; }
    #m-player .ib { width: 48px; height: 48px; }
    #m-player .ib svg { width: 24px; height: 24px; }
    #m-player .art { flex: 1; display: flex; align-items: center; justify-content: center; min-height: 0; padding: 20px 0 28px; }
    #m-player .art img {
      width: 100%; max-height: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 10px;
      box-shadow: 0 24px 60px rgba(0,0,0,.55), 0 4px 12px rgba(0,0,0,.35);
      transition: transform .45s cubic-bezier(.2,.8,.2,1);
    }
    #m-player.paused .art img { transform: scale(.88); }
    #m-player .meta { display: flex; align-items: center; gap: 12px; flex: none; }
    #m-player .meta .t { flex: 1; min-width: 0; }
    #m-player .meta b { display: block; font-size: 24px; font-weight: 800; letter-spacing: -.01em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    #m-player .meta span { display: block; color: rgba(255,255,255,.7); font-size: 16px; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    #m-player .like svg { width: 28px; height: 28px; }
    #m-player .like.on { color: var(--m-accent, #1ed760); }
    #m-player .seek { margin-top: 18px; flex: none; }
    #m-player input[type=range] {
      width: 100%; height: 4px; -webkit-appearance: none; appearance: none; border-radius: 2px; margin: 10px 0 6px; outline: none;
      background: linear-gradient(to right, #fff var(--p, 0%), rgba(255,255,255,.25) var(--p, 0%)); transition: height .15s;
    }
    #m-player input[type=range]:active { height: 6px; }
    #m-player input[type=range]::-webkit-slider-thumb { -webkit-appearance: none; width: 14px; height: 14px; border-radius: 50%; background: #fff; box-shadow: 0 2px 6px rgba(0,0,0,.4); }
    #m-player .times { display: flex; justify-content: space-between; font-size: 11px; color: rgba(255,255,255,.65); font-variant-numeric: tabular-nums; }
    #m-player .ctrls { display: flex; align-items: center; justify-content: space-between; margin: 10px -8px 18px; flex: none; }
    #m-player .ctrls .s { position: relative; }
    #m-player .ctrls .s svg { width: 24px; height: 24px; }
    #m-player .ctrls .m svg { width: 34px; height: 34px; }
    #m-player .ctrls .on { color: var(--m-accent, #1ed760); }
    #m-player .ctrls .s.on::after { content: ""; position: absolute; bottom: 6px; width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
    #m-player .ctrls .pp { width: 72px; height: 72px; border-radius: 50%; background: #fff; color: #000; box-shadow: 0 8px 24px rgba(0,0,0,.35); }
    #m-player .ctrls .pp svg { width: 30px; height: 30px; }
    #m-player .bottom { display: flex; align-items: center; gap: 4px; flex: none; }
    #m-player .bottom .dev { flex: 1; justify-content: flex-start; gap: 8px; font-size: 13px; font-weight: 600; color: var(--m-accent, #1ed760); min-width: 0; }
    #m-player .bottom .dev span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    #m-player .bottom svg { width: 22px; height: 22px; }
    /* プレーヤーから開くキュー・デバイス一覧はプレーヤーより手前に出す */
    html.m-player.m-np .m-right { z-index: 10020 !important; height: 100vh !important; }
    #m-panel-close {
      position: fixed; top: 8px; right: 8px; z-index: 10021; display: none; width: 40px; height: 40px; border-radius: 50%;
      border: 0; background: rgba(0,0,0,.6); color: #fff; font-size: 20px;
    }
    html.m-player.m-np #m-panel-close { display: block; }
    /* デバイス（Spotify Connect）一覧のボトムシート */
    #m-devices-bg { position: fixed; inset: 0; z-index: 10030; background: rgba(0,0,0,.6); display: none; }
    #m-devices {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 10031; background: #282828; color: #fff;
      border-radius: 12px 12px 0 0; padding: 8px 0 24px; transform: translateY(100%); transition: transform .22s ease;
      max-height: 75vh; overflow-y: auto; font-family: inherit;
    }
    html.m-devices #m-devices-bg { display: block; }
    html.m-devices #m-devices { transform: none; }
    #m-devices .grip { width: 36px; height: 4px; border-radius: 2px; background: #727272; margin: 4px auto 12px; }
    #m-devices h3 { margin: 0 16px 8px; font-size: 18px; }
    #m-devices .cur { display: flex; align-items: center; gap: 12px; margin: 8px 16px 16px; color: var(--m-accent, #1ed760); }
    #m-devices button.d {
      display: flex; align-items: center; gap: 16px; width: 100%; padding: 12px 16px; border: 0; background: none;
      color: #fff; text-align: left; font-size: 16px;
    }
    #m-devices button.d.on { color: var(--m-accent, #1ed760); }
    #m-devices button.d small { display: block; color: #a7a7a7; font-size: 12px; }
    #m-devices svg { width: 28px; height: 28px; fill: currentColor; flex: none; }
    #m-devices .note { color: #a7a7a7; font-size: 13px; margin: 12px 16px 0; line-height: 1.5; }

    /* ミニプレーヤーのデバイスボタン */
    /* ミニプレーヤーの操作ボタンは自前で描く（Spotify の操作部は隠して裏で操作） */
    [data-testid="now-playing-bar"] > div > div:nth-child(2) { display: none !important; }
    [data-testid="now-playing-bar"] > div > div:first-child { padding-right: 96px !important; }
    #m-mini-ctl { position: absolute; right: 8px; top: 8px; height: 40px !important; padding: 0 !important; width: auto !important; display: flex; align-items: center; gap: 4px; z-index: 2; }
    #m-mini-pp { width: 40px; height: 40px; background: none; border: 0; color: #fff; display: flex; align-items: center; justify-content: center; }
    #m-mini-pp svg { width: 26px; height: 26px; fill: currentColor; }
    #m-mini-prog { position: absolute; left: 8px; right: 8px; bottom: 0; height: 2px !important; padding: 0 !important; width: auto !important; background: rgba(255,255,255,.25); border-radius: 1px; overflow: hidden; z-index: 2; }
    #m-mini-prog i { display: block; height: 100%; width: 0; background: #fff; }
    /* 「〜で再生中」の緑のバーは、ミニプレーヤーのデバイスボタンの色で示す */
    [data-testid="now-playing-bar"] > div:not(:first-child):not([id^="m-mini"]) { display: none !important; }
    /* ミニプレーヤーの曲名が長いときは横に流す */
    @keyframes m-marquee { 0%, 15% { transform: translateX(0); } 85%, 100% { transform: translateX(var(--m-dist)); } }
    [data-testid="now-playing-bar"] a { text-decoration: none !important; }
    .m-marquee { animation: m-marquee var(--m-dur, 8s) linear infinite alternate; will-change: transform; }
    #m-mini-dev { width: 40px; height: 40px; background: none; border: 0; color: #fff; flex: none; display: flex; align-items: center; justify-content: center; }
    #m-mini-dev svg { width: 22px; height: 22px; fill: currentColor; }
    #m-mini-dev.on { color: var(--m-accent, #1ed760); }
    /* ミニプレーヤーに再生位置の細いバー（Myspoti 風） */
    [data-testid="playback-progressbar"] [data-testid="progress-bar-background"] { background: rgba(255,255,255,.25) !important; }

    /* 下部タブバー（固定） */
    #m-tabbar {
      position: fixed; left: 0; right: 0; bottom: 0; height: ${NAV_H}px; z-index: 9999;
      display: flex; font-family: inherit;
      background: linear-gradient(to bottom, rgba(0,0,0,.35), rgba(0,0,0,.9));
      -webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px);
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
    try { if (window.__native) window.__native.setSetting(name, String(value)); } catch (e) {}
  }
  function nativeValue(name, fallback) {
    try { return window.__native ? window.__native.getSetting(name, fallback) : fallback; } catch (e) { return fallback; }
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
        var rgb = 'rgb(' + Math.round(r / n * k) + ',' + Math.round(gr / n * k) + ',' + Math.round(b / n * k) + ')';
        bar.style.setProperty('background-color', rgb, 'important');
        document.documentElement.style.setProperty('--m-tint', 'rgb(' + Math.round(r / n * 0.7) + ',' + Math.round(gr / n * 0.7) + ',' + Math.round(b / n * 0.7) + ')');
      } catch (e) {}
    };
    probe.src = img.src;
  }

  // ---- 全画面プレーヤー（Spotify の再生バーの各ボタンを操作する） ----
  var P = {
    down: '<svg viewBox="0 0 24 24"><path d="M12 15.6 4.7 8.3l1.4-1.4 5.9 5.9 5.9-5.9 1.4 1.4z"/></svg>',
    more: '<svg viewBox="0 0 24 24"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>',
    heart: '<svg viewBox="0 0 24 24"><path d="M12 21 10.6 19.7C5.4 15 2 12 2 8.3 2 5.3 4.4 3 7.4 3c1.7 0 3.4.8 4.6 2.1C13.2 3.8 14.9 3 16.6 3 19.6 3 22 5.3 22 8.3c0 3.7-3.4 6.7-8.6 11.4z"/></svg>',
    heartOff: '<svg viewBox="0 0 24 24"><path d="M12 21 10.6 19.7C5.4 15 2 12 2 8.3 2 5.3 4.4 3 7.4 3c1.7 0 3.4.8 4.6 2.1C13.2 3.8 14.9 3 16.6 3 19.6 3 22 5.3 22 8.3c0 3.7-3.4 6.7-8.6 11.4zM7.4 5C5.5 5 4 6.4 4 8.3c0 2.8 2.9 5.4 8 10 5.1-4.6 8-7.2 8-10C20 6.4 18.5 5 16.6 5c-1.4 0-2.8.9-3.3 2.2h-2.6C10.2 5.9 8.8 5 7.4 5z"/></svg>',
    shuffle: '<svg viewBox="0 0 24 24"><path d="M14.8 4H20v5.2l-1.9-1.9-3.4 3.4-1.4-1.4 3.4-3.4zM4 5.4 5.4 4 20 18.6 18.6 20zm10.7 8 1.4-1.4 2 2L20 12.2V20h-5.2l1.9-1.9z"/></svg>',
    prev: '<svg viewBox="0 0 24 24"><path d="M6 5h2v14H6zm3.5 7L19 5v14z"/></svg>',
    next: '<svg viewBox="0 0 24 24"><path d="M16 5h2v14h-2zM5 5l9.5 7L5 19z"/></svg>',
    play: '<svg viewBox="0 0 24 24"><path d="M7 4.5v15L20 12z"/></svg>',
    pause: '<svg viewBox="0 0 24 24"><path d="M6 4h4v16H6zm8 0h4v16h-4z"/></svg>',
    repeat: '<svg viewBox="0 0 24 24"><path d="M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2z"/></svg>',
    repeatOne: '<svg viewBox="0 0 24 24"><path d="M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2zm-4-2V9h-1l-2 1v1h1.5v4z"/></svg>',
    device: '<svg viewBox="0 0 24 24"><path d="M2 4.5A1.5 1.5 0 0 1 3.5 3h11A1.5 1.5 0 0 1 16 4.5V6h-1.5V4.5h-11v10H11V16H3.5A1.5 1.5 0 0 1 2 14.5zM6 18.5h5V20H6z"/><path d="M13 9.5A1.5 1.5 0 0 1 14.5 8h6A1.5 1.5 0 0 1 22 9.5v10a1.5 1.5 0 0 1-1.5 1.5h-6a1.5 1.5 0 0 1-1.5-1.5zm1.5 0v10h6v-10zM17.5 11a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm0 3.5a2.25 2.25 0 1 1 0 4.5 2.25 2.25 0 0 1 0-4.5zm0 1.3a.95.95 0 1 0 0 1.9.95.95 0 0 0 0-1.9z"/></svg>',
    share: '<svg viewBox="0 0 24 24"><path d="M18 16a3 3 0 0 0-2.4 1.2l-6.7-3.4a3 3 0 0 0 0-1.6l6.7-3.4A3 3 0 1 0 15 7l-6.7 3.4a3 3 0 1 0 0 3.2L15 17a3 3 0 1 0 3-1z"/></svg>',
    queue: '<svg viewBox="0 0 24 24"><path d="M3 5h18v2H3zm0 6h12v2H3zm0 6h12v2H3zm14-3 5 3-5 3z"/></svg>',
    lyrics: '<svg viewBox="0 0 24 24"><path d="M4 4h16v12H7l-3 3zm3 4v2h10V8zm0 4v2h7v-2z"/></svg>'
  };

  function q(sel) { return document.querySelector('[data-testid="now-playing-bar"] ' + sel); }
  function bigArt(src) { return (src || '').replace(/00004851|00001e02/, '0000b273'); }
  function toSec(t) { var p = (t || '0:00').split(':').map(Number); return p.reduce(function (a, b) { return a * 60 + b; }, 0); }

  function deviceButton() {
    return q('button[aria-label*="デバイス"], button[aria-label*="device" i]');
  }

  // ---- デバイス（Spotify Connect）切り替え ----
  var DEV_ICONS = {
    computer: '<svg viewBox="0 0 24 24"><path d="M3 4h18v12H3zm2 2v8h14V6zM1 18h22v2H1z"/></svg>',
    smartphone: '<svg viewBox="0 0 24 24"><path d="M7 2h10v20H7zm2 2v14h6V4zm3 15a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/></svg>',
    speaker: '<svg viewBox="0 0 24 24"><path d="M6 2h12v20H6zm6 4a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zm0 5a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"/></svg>',
    tv: '<svg viewBox="0 0 24 24"><path d="M2 5h20v12H2zm2 2v8h16V7zm4 12h8v2H8z"/></svg>'
  };
  function devIcon(type) {
    type = (type || '').toLowerCase();
    if (/smartphone|tablet/.test(type)) return DEV_ICONS.smartphone;
    if (/speaker|avr|stb|audio/.test(type)) return DEV_ICONS.speaker;
    if (/tv|cast|game/.test(type)) return DEV_ICONS.tv;
    return DEV_ICONS.computer;
  }

  function activeDeviceName() {
    var st = window.__mConnect && __mConnect.get();
    var c = st && st.cluster;
    if (!c || !c.active_device_id || c.active_device_id === st.self) return '';
    var d = c.devices && c.devices[c.active_device_id];
    return d ? d.name : '';
  }

  function renderDevices() {
    var box = document.getElementById('m-devices');
    var st = window.__mConnect ? __mConnect.get() : { cluster: null };
    var c = st.cluster;
    var active = c && c.active_device_id ? c.active_device_id : st.self;
    box.innerHTML = '<div class="grip"></div><h3>デバイスに接続</h3>';
    var devices = c && c.devices ? Object.keys(c.devices).map(function (k) { return c.devices[k]; }) : [];
    devices.sort(function (a, b) { return (b.device_id === st.self) - (a.device_id === st.self); });
    devices.forEach(function (d) {
      if (d.capabilities && d.capabilities.hidden) return;
      var mine = d.device_id === st.self;
      var b = document.createElement('button');
      b.className = 'd' + (d.device_id === active ? ' on' : '');
      b.innerHTML = devIcon(mine ? 'smartphone' : d.device_type) + '<span><span class="n"></span><small></small></span>';
      b.querySelector('.n').textContent = mine ? 'このスマホ' : d.name;
      b.querySelector('small').textContent = d.device_id === active ? '再生中' : (mine ? 'Spotify Web Player' : 'Spotify Connect');
      b.addEventListener('click', function () {
        if (d.device_id === active) return closeDevices();
        b.querySelector('small').textContent = '接続中…';
        __mConnect.transfer(d.device_id).then(function () { setTimeout(closeDevices, 400); }, function () {
          b.querySelector('small').textContent = '接続できませんでした';
        });
      });
      box.appendChild(b);
    });
    if (!devices.length) {
      var p = document.createElement('p');
      p.className = 'note';
      p.textContent = 'デバイスを読み込み中です。少し待ってからもう一度開いてください。';
      box.appendChild(p);
    }
    var note = document.createElement('p');
    note.className = 'note';
    note.textContent = '同じ Spotify アカウントでログインしているスピーカー・テレビ・PC・ゲーム機などが表示されます。';
    box.appendChild(note);
  }

  function openDevices() {
    if (!document.getElementById('m-devices')) {
      var bg = document.createElement('div');
      bg.id = 'm-devices-bg';
      bg.addEventListener('click', closeDevices);
      document.body.appendChild(bg);
      var box = document.createElement('section');
      box.id = 'm-devices';
      document.body.appendChild(box);
      if (window.__mConnect) __mConnect.onChange(function () {
        if (document.documentElement.classList.contains('m-devices')) renderDevices();
      });
    }
    renderDevices();
    document.documentElement.classList.add('m-devices');
  }
  function closeDevices() { document.documentElement.classList.remove('m-devices'); }

  // キュー・デバイス一覧など右サイドバーのパネルをプレーヤーの上に開く
  function openPanel(btn) {
    if (!btn) return;
    var h = document.documentElement.classList;
    var right = document.querySelector('.m-right');
    var wasOpen = h.contains('m-np') && right && right.textContent.length > 20;
    if (!wasOpen || btn.getAttribute('aria-pressed') !== 'true') btn.click();
    h.add('m-np');
  }

  function closePanel() {
    var h = document.documentElement.classList;
    h.remove('m-np');
  }

  function buildPlayer() {
    var el = document.createElement('section');
    el.id = 'm-player';
    el.innerHTML =
      '<div class="bg"></div><div class="shade"></div><div class="grab"></div>' +
      '<div class="top"><button class="ib down" aria-label="閉じる">' + P.down + '</button>' +
      '<div class="ctx"><small>再生中</small><b></b></div><button class="ib more" aria-label="その他">' + P.more + '</button></div>' +
      '<div class="art"><img alt=""></div>' +
      '<div class="meta"><div class="t"><b></b><span></span></div><button class="ib like" aria-label="お気に入り">' + P.heartOff + '</button></div>' +
      '<div class="seek"><input type="range" min="0" max="1000" value="0"><div class="times"><span class="pos">0:00</span><span class="dur">0:00</span></div></div>' +
      '<div class="ctrls"><button class="ib s shuffle" aria-label="シャッフル">' + P.shuffle + '</button>' +
      '<button class="ib m prev" aria-label="前へ">' + P.prev + '</button>' +
      '<button class="pp" aria-label="再生">' + P.play + '</button>' +
      '<button class="ib m next" aria-label="次へ">' + P.next + '</button>' +
      '<button class="ib s repeat" aria-label="リピート">' + P.repeat + '</button></div>' +
      '<div class="bottom"><button class="dev">' + P.device + '<span>このデバイス</span></button>' +
      '<button class="ib lyrics" aria-label="歌詞">' + P.lyrics + '</button>' +
      '<button class="ib share" aria-label="共有">' + P.share + '</button>' +
      '<button class="ib queue" aria-label="キュー">' + P.queue + '</button></div>';
    document.body.appendChild(el);

    var close = document.createElement('button');
    close.id = 'm-panel-close';
    close.textContent = '✕';
    close.setAttribute('aria-label', '閉じる');
    close.addEventListener('click', closePanel);
    document.body.appendChild(close);

    function on(sel, fn) { el.querySelector(sel).addEventListener('click', fn); }
    on('.down', closePlayer);
    on('.pp', function () { var b = q('[data-testid="control-button-playpause"]'); if (b) b.click(); });
    on('.prev', function () { var b = q('[data-testid="control-button-skip-back"]'); if (b) b.click(); });
    on('.next', function () { var b = q('[data-testid="control-button-skip-forward"]'); if (b) b.click(); });
    on('.repeat', function () { var b = q('[data-testid="control-button-repeat"]'); if (b) b.click(); });
    on('.shuffle', function () { var b = q('[data-testid="general-controls"] button[aria-label*="シャッフル"], [data-testid="general-controls"] button[aria-label*="shuffle" i]'); if (b) b.click(); });
    on('.like', function () { var b = q('[data-testid="now-playing-widget-v2"] button[aria-checked]'); if (b) b.click(); });
    on('.dev', openDevices);
    on('.queue', function () { openPanel(q('[data-testid="control-button-queue"]')); });
    on('.lyrics', function () { closePlayer(); var b = q('[data-testid="lyrics-button"]'); if (b) b.click(); });
    on('.more', function () { closePlayer(); var a = q('[data-testid="now-playing-widget-v2"] a[href*="/album/"]'); if (a) a.click(); });
    on('.share', function () {
      var a = q('[data-testid="now-playing-widget-v2"] a[href*="/album/"]');
      var url = a ? 'https://open.spotify.com' + a.getAttribute('href').replace(/^\/intl-[a-z-]+/i, '') : location.href;
      if (navigator.share) navigator.share({ url: url }).catch(function () {});
      else if (navigator.clipboard) navigator.clipboard.writeText(url);
    });
    // 下にスワイプして閉じる
    var startY = null, dy = 0;
    el.addEventListener('touchstart', function (e) {
      if (e.target.closest('input, .ctrls, .bottom')) return;
      startY = e.touches[0].clientY; dy = 0;
      el.classList.add('dragging');
    }, { passive: true });
    el.addEventListener('touchmove', function (e) {
      if (startY == null) return;
      dy = Math.max(0, e.touches[0].clientY - startY);
      el.style.transform = 'translateY(' + dy + 'px)';
    }, { passive: true });
    el.addEventListener('touchend', function () {
      if (startY == null) return;
      startY = null;
      el.classList.remove('dragging');
      el.style.transform = '';
      if (dy > 120) closePlayer();
    });
    var range = el.querySelector('input[type=range]');
    range.addEventListener('input', function () { range.dataset.drag = '1'; range.style.setProperty('--p', range.value / 10 + '%'); });
    range.addEventListener('change', function () {
      delete range.dataset.drag;
      var src = q('[data-testid="playback-progressbar"] input[type=range]');
      if (!src) return;
      var v = Math.round(src.max * range.value / 1000);
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(src, v);
      src.dispatchEvent(new Event('input', { bubbles: true }));
      src.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }

  function updatePlayer() {
    var el = document.getElementById('m-player');
    if (!el || !document.documentElement.classList.contains('m-player')) return;
    var w = q('[data-testid="now-playing-widget-v2"]');
    if (!w) return;
    var title = w.querySelector('a[href*="/album/"], a[href*="/track/"], a[href*="/episode/"]');
    var by = w.querySelector('[data-testid="now-playing-widget-v2-byline"]');
    var img = w.querySelector('[data-testid="cover-art-image"]');
    var src = bigArt(img && img.src);
    var pic = el.querySelector('.art img');
    if (src && pic.getAttribute('src') !== src) {
      pic.src = src;
      el.querySelector('.bg').style.backgroundImage = 'url("' + src + '")';
    }
    el.querySelector('.meta b').textContent = title ? title.textContent : '';
    el.querySelector('.meta span').textContent = by ? by.textContent : '';
    el.querySelector('.ctx b').textContent = by ? by.textContent : '';
    var pp = q('[data-testid="control-button-playpause"]');
    var playing = pp && /一時停止|Pause/.test(pp.getAttribute('aria-label') || '');
    el.querySelector('.pp').innerHTML = playing ? P.pause : P.play;
    el.classList.toggle('paused', !playing);
    var like = q('[data-testid="now-playing-widget-v2"] button[aria-checked]');
    var liked = like && like.getAttribute('aria-checked') === 'true';
    el.querySelector('.like').classList.toggle('on', !!liked);
    el.querySelector('.like').innerHTML = liked ? P.heart : P.heartOff;
    var rep = q('[data-testid="control-button-repeat"]');
    var rs = rep ? rep.getAttribute('aria-checked') : 'false';
    el.querySelector('.repeat').classList.toggle('on', rs === 'true' || rs === 'mixed');
    el.querySelector('.repeat').innerHTML = rs === 'mixed' ? P.repeatOne : P.repeat;
    var sh = q('[data-testid="general-controls"] button[aria-label*="シャッフル"], [data-testid="general-controls"] button[aria-label*="shuffle" i]');
    var shOn = sh && (sh.getAttribute('aria-checked') === 'true' || /無効|disable/i.test(sh.getAttribute('aria-label') || ''));
    el.querySelector('.shuffle').classList.toggle('on', !!shOn);
    var pos = q('[data-testid="playback-position"]'), dur = q('[data-testid="playback-duration"]');
    el.querySelector('.pos').textContent = pos ? pos.textContent : '0:00';
    el.querySelector('.dur').textContent = dur ? dur.textContent : '0:00';
    var range = el.querySelector('input[type=range]');
    if (!range.dataset.drag && pos && dur) {
      var p = toSec(dur.textContent) ? Math.min(1000, Math.round(toSec(pos.textContent) / toSec(dur.textContent) * 1000)) : 0;
      range.value = p;
      range.style.setProperty('--p', p / 10 + '%');
    }
    var remote = activeDeviceName();
    el.querySelector('.dev span').textContent = remote || 'このスマホ';
  }

  // Android のメディアコントロールからの操作
  window.__mMedia = function (action, pos) {
    var pp = q('[data-testid="control-button-playpause"]');
    var playing = pp && /一時停止|Pause/.test(pp.getAttribute('aria-label') || '');
    if ((action === 'play' && !playing) || (action === 'pause' && playing)) pp.click();
    if (action === 'next') { var n = q('[data-testid="control-button-skip-forward"]'); if (n) n.click(); }
    if (action === 'prev') { var b = q('[data-testid="control-button-skip-back"]'); if (b) b.click(); }
    if (action === 'seek') {
      var src = q('[data-testid="playback-progressbar"] input[type=range]');
      if (src) {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(src, pos);
        src.dispatchEvent(new Event('input', { bubbles: true }));
        src.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }
  };

  // 曲情報をアプリへ送る（通知・ロック画面のメディアコントロール用）
  var lastMeta = '', lastMetaAt = 0;
  function sendMeta() {
    if (!window.__native || !window.__native.send) return;
    var w = q('[data-testid="now-playing-widget-v2"]');
    if (!w) return;
    var title = w.querySelector('a[href*="/album/"], a[href*="/track/"], a[href*="/episode/"]');
    var by = w.querySelector('[data-testid="now-playing-widget-v2-byline"]');
    var img = w.querySelector('[data-testid="cover-art-image"]');
    var pp = q('[data-testid="control-button-playpause"]');
    var pos = q('[data-testid="playback-position"]'), dur = q('[data-testid="playback-duration"]');
    var m = {
      type: 'meta',
      title: title ? title.textContent : '',
      artist: by ? by.textContent : '',
      album: '',
      art: bigArt(img && img.src),
      playing: !!pp && /一時停止|Pause/.test(pp.getAttribute('aria-label') || ''),
      dur: dur ? toSec(dur.textContent) * 1000 : 0
    };
    var key = JSON.stringify(m);
    var now = Date.now();
    if (key === lastMeta && now - lastMetaAt < 10000) return;
    lastMeta = key;
    lastMetaAt = now;
    m.pos = pos ? toSec(pos.textContent) * 1000 : 0;
    window.__native.send(m);
  }

  // 曲名・アーティスト名がはみ出すときだけ流す
  function marquee() {
    var w = q('[data-testid="now-playing-widget-v2"]');
    if (!w) return;
    [w.querySelector('a[href*="/album/"], a[href*="/track/"], a[href*="/episode/"]'),
     w.querySelector('[data-testid="now-playing-widget-v2-byline"]')].forEach(function (el) {
      if (!el) return;
      var box = el.parentElement;
      while (box && box !== w && getComputedStyle(box).overflow !== 'hidden') box = box.parentElement;
      if (!box || box === w) return;
      var inner = box.firstElementChild;
      if (!inner) return;
      inner.classList.remove('m-marquee');
      var dist = inner.scrollWidth - box.clientWidth;
      if (dist > 4) {
        inner.style.setProperty('--m-dist', -dist + 'px');
        inner.style.setProperty('--m-dur', Math.max(4, dist / 25) + 's');
        inner.style.display = 'inline-block';
        inner.classList.add('m-marquee');
      }
    });
  }

  var marqueeKey = '';
  function updateMini() {
    sendMeta();
    var nw = q('[data-testid="now-playing-widget-v2"]');
    var key = nw ? nw.getAttribute('aria-label') : '';
    if (key !== marqueeKey) { marqueeKey = key; setTimeout(marquee, 300); }
    var pp = q('[data-testid="control-button-playpause"]');
    var btn = document.getElementById('m-mini-pp');
    if (btn && pp) {
      var playing = /一時停止|Pause/.test(pp.getAttribute('aria-label') || '');
      if (btn.dataset.playing !== String(playing)) { btn.dataset.playing = String(playing); btn.innerHTML = playing ? P.pause : P.play; }
    }
    var bar = document.querySelector('#m-mini-prog i');
    var pos = q('[data-testid="playback-position"]'), dur = q('[data-testid="playback-duration"]');
    if (bar && pos && dur && toSec(dur.textContent)) bar.style.width = Math.min(100, toSec(pos.textContent) / toSec(dur.textContent) * 100) + '%';
  }

  var playerTimer = null;
  function openPlayer() {
    document.documentElement.classList.remove('m-np');
    document.documentElement.classList.add('m-player');
    updatePlayer();
    clearInterval(playerTimer);
    playerTimer = setInterval(updatePlayer, 500);
  }
  function closePlayer() {
    document.documentElement.classList.remove('m-player', 'm-np');
    clearInterval(playerTimer);
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
    // ライブラリが折りたたみ表示（アイコンだけ）なら展開する
    if (name === 'lib') click('#Desktop_LeftSidebar_Id button[aria-label="マイライブラリを開く"], #Desktop_LeftSidebar_Id button[aria-label="Open Your Library"]');
    h.remove('m-np', 'm-player');
    if (h.contains('m-settings')) closeSettings();
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
    if (!document.getElementById('m-tabbar') && document.body) { buildDrawer(); buildTabbar(); buildPlayer(); }
    // ミニプレーヤーに デバイス（キャスト）ボタン → 再生ボタン の順で並べる
    var npb = document.querySelector('[data-testid="now-playing-bar"]');
    if (npb && !document.getElementById('m-mini-ctl')) {
      var box = document.createElement('div');
      box.id = 'm-mini-ctl';
      box.innerHTML = '<button id="m-mini-dev" aria-label="デバイスに接続">' + P.device + '</button>' +
        '<button id="m-mini-pp" aria-label="再生">' + P.play + '</button>';
      box.querySelector('#m-mini-dev').addEventListener('click', function (ev) { ev.stopPropagation(); openDevices(); });
      box.querySelector('#m-mini-pp').addEventListener('click', function (ev) {
        ev.stopPropagation();
        var b = q('[data-testid="control-button-playpause"]');
        if (b) b.click();
      });
      npb.appendChild(box);
      var prog = document.createElement('div');
      prog.id = 'm-mini-prog';
      prog.innerHTML = '<i></i>';
      npb.appendChild(prog);
      setInterval(updateMini, 1000);
    }
    updateMini();
    var mdv = document.getElementById('m-mini-dev');
    if (mdv) mdv.classList.toggle('on', !!activeDeviceName());
    updatePlayer();
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
    if (window.__native && playing !== window.__mPlaying) {
      window.__mPlaying = playing;
      window.__native.setPlaying(playing);
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
    // ミニプレーヤーのボタン以外をタップしたら全画面プレーヤーを開く
    var bar = e.target.closest && e.target.closest('[data-testid="now-playing-bar"]');
    if (bar && !e.target.closest('button, a, [data-testid="playback-progressbar"]')) {
      e.preventDefault();
      e.stopPropagation();
      openPlayer();
    }
  }, true);

  // 検索ボックスにフォーカスがある間は「検索中」表示にする
  document.addEventListener('focusin', function (e) {
    if (e.target.matches && e.target.matches('#global-nav-bar input')) {
      document.documentElement.classList.add('m-searching');
      var bar = document.getElementById('global-nav-bar');
      if (bar && !document.getElementById('m-cancel')) {
        var c = document.createElement('button');
        c.id = 'm-cancel';
        c.textContent = 'キャンセル';
        c.addEventListener('mousedown', function (ev) { ev.preventDefault(); });
        c.addEventListener('click', function () {
          var i = document.querySelector('#global-nav-bar input');
          if (i) i.blur();
          document.documentElement.classList.remove('m-searching');
        });
        bar.appendChild(c);
      }
    }
  });
  document.addEventListener('focusout', function (e) {
    if (e.target.matches && e.target.matches('#global-nav-bar input')) {
      setTimeout(function () {
        if (!document.activeElement || !document.activeElement.matches('#global-nav-bar input')) {
          document.documentElement.classList.remove('m-searching');
        }
      }, 200);
    }
  });

  // Android の戻るボタン用：オーバーレイが開いていれば閉じて true を返す
  window.__mobileBack = function () {
    var h = document.documentElement.classList;
    if (h.contains('m-devices')) { closeDevices(); return true; }
    if (h.contains('m-searching')) { var si = document.querySelector('#global-nav-bar input'); if (si) si.blur(); h.remove('m-searching'); return true; }
    if (h.contains('m-drawer')) { closeDrawer(); return true; }
    if (h.contains('m-player') && h.contains('m-np')) { closePanel(); return true; }
    if (h.contains('m-player')) { closePlayer(); return true; }
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
