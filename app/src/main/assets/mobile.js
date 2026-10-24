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
    /* ライブラリ：公式アプリ風（アイコン＋大きな見出し、丸いフィルター、大きめのサムネイル） */
    html.m-lib #Desktop_LeftSidebar_Id header { padding: 8px 16px 0 !important; }
    html.m-lib #Desktop_LeftSidebar_Id header h1 { margin-left: 12px !important; font-size: 24px !important; font-weight: 800 !important; line-height: 32px !important; }
    html.m-lib #Desktop_LeftSidebar_Id header button[aria-label*="折りたたむ"] > span:first-child,
    html.m-lib #Desktop_LeftSidebar_Id header button[aria-label*="展開"],
    html.m-lib #Desktop_LeftSidebar_Id header button[aria-label="作成"] > span,
    html.m-lib #Desktop_LeftSidebar_Id [data-testid="carousel-previous-button"],
    html.m-lib #Desktop_LeftSidebar_Id [data-testid="carousel-next-button"] { display: none !important; }
    html.m-lib #Desktop_LeftSidebar_Id header button[aria-label*="折りたたむ"] { pointer-events: none; padding-left: 0 !important; margin-left: 0 !important; }
    html.m-lib #Desktop_LeftSidebar_Id header button[aria-label="作成"] {
      width: 40px !important; height: 40px !important; padding: 0 !important; justify-content: center !important; background: none !important;
    }
    html.m-lib #Desktop_LeftSidebar_Id header button[aria-label="作成"] svg { width: 24px !important; height: 24px !important; }
    #m-lib-avatar { width: 32px; height: 32px; border-radius: 50%; background: #535353 center/cover no-repeat; flex: none; margin-right: 12px;
      border: 0; padding: 0; color: #000; font-weight: 700; font-size: 14px; }
    html.m-lib #Desktop_LeftSidebar_Id [role="listbox"] [role="option"] > * {
      border-radius: 16px !important; height: 32px !important; padding: 0 14px !important; font-size: 13px !important;
    }
    html.m-lib #Desktop_LeftSidebar_Id [role="grid"] { margin: 0 !important; left: 0 !important; width: 100% !important; }
    html.m-lib #Desktop_LeftSidebar_Id [data-encore-id="listRow"] { padding: 4px 16px !important; }
    html.m-lib #Desktop_LeftSidebar_Id [data-testid="entity-image"],
    html.m-lib #Desktop_LeftSidebar_Id [data-encore-id="listRow"] > div:nth-child(3),
    html.m-lib #Desktop_LeftSidebar_Id [data-encore-id="listRow"] > div:nth-child(3) * { width: 56px !important; height: 56px !important; min-width: 56px !important; }
    html.m-lib #Desktop_LeftSidebar_Id [data-encore-id="listRowTitle"] { font-size: 16px !important; }
    html.m-lib #Desktop_LeftSidebar_Id [data-encore-id="listRowSubtitle"] { font-size: 13px !important; }
    html.m-lib #Desktop_LeftSidebar_Id [data-encore-id="listRow"] [data-testid="play-button"] { display: none !important; }
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

    /* ホーム：公式アプリ風のショートカットと棚 */
    main [data-testid="carousel-previous-button"], main [data-testid="carousel-next-button"] { display: none !important; }
    main [data-testid="shortcut-background"] { height: 56px !important; border-radius: 4px !important; background: rgba(255,255,255,.1) !important; }
    main [data-testid="shortcut-image"] { width: 56px !important; height: 56px !important; }
    main [data-testid="home-page"] [data-testid="component-shelf"] h2 { font-size: 22px !important; font-weight: 800 !important; letter-spacing: -.01em; }
    main [data-testid="home-page"] [data-testid="component-shelf"] [role="listitem"] { padding: 0 12px 0 0 !important; background: none !important; }
    main [data-testid="home-page"] [data-testid="component-shelf"] [data-testid="card-image"] { border-radius: 4px !important; }
    main [data-testid="home-page"] [data-testid="component-shelf"] [data-testid="play-button"] { display: none !important; }

    /* アーティストページ：大きな写真の上に名前、操作は「フォロー・…」左／シャッフル・再生 右 */
    main [data-testid="artist-page"] [data-testid="entity-header"] { min-height: 340px !important; }
    main [data-testid="artist-page"] [data-testid="entityTitle"] h1 { font-size: 44px !important; line-height: 1.05 !important; font-weight: 900 !important; }
    main [data-testid="artist-page"] [data-testid="action-bar-row"] { display: flex !important; align-items: center !important; gap: 12px !important; }
    main [data-testid="artist-page"] [data-testid="action-bar-row"] > :first-child { order: 10; margin-left: auto !important; }
    main [data-testid="artist-page"] [data-testid="action-bar-row"] [data-testid="play-button"] { width: 56px !important; height: 56px !important; }
    main [data-testid="artist-page"] [data-testid="action-bar-row"] [data-testid="play-button"] > span { background-color: var(--m-accent, #1ed760) !important; }
    main [data-testid="artist-page"] [data-encore-id="buttonSecondary"] { height: 32px !important; padding: 0 14px !important; font-size: 13px !important; }
    main [data-testid="artist-page"] h2 { font-size: 20px !important; font-weight: 800 !important; }
    main [data-testid="artist-page"] [data-testid="tracklist-row"] { padding: 0 !important; }
    /* アルバム・プレイリスト：ジャケットを中央に大きく、その下に左寄せでタイトルと情報 */
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] { height: auto !important; min-height: 0 !important; max-height: none !important; padding-top: 56px !important; padding-bottom: 8px !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] > div:last-child {
      display: flex !important; flex-direction: column !important; align-items: stretch !important; height: auto !important; padding: 0 16px !important; gap: 12px !important;
    }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] > div:last-child > :not(:has(h1)):has(img) {
      align-self: center !important; width: 64vw !important; height: 64vw !important; margin: 0 0 8px !important;
    }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] > div:last-child > :not(:has(h1)):has(img) * { width: 100% !important; height: 100% !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] img { box-shadow: 0 8px 32px rgba(0,0,0,.5) !important; border-radius: 4px !important; object-fit: cover; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] > div:last-child > div:last-child { width: 100% !important; padding: 0 !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entityTitle"] h1 { font-size: 24px !important; line-height: 1.2 !important; font-weight: 800 !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="entity-header"] > div:last-child > div:last-child > span:first-child:not(:has(h1)) { display: none !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="action-bar-row"] { display: flex !important; align-items: center !important; gap: 16px !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="action-bar-row"] > :first-child { order: 10; margin-left: auto !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="action-bar-row"] [data-testid="play-button"] { width: 56px !important; height: 56px !important; }
    /* 曲一覧は見出し行・番号・再生時間を消して、曲名とアーティスト＋「…」だけ */
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="track-list"] > div:first-child:has([role="columnheader"]) { display: none !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="tracklist-row"] { grid-template-columns: [first] minmax(0,1fr) [last] 80px !important; padding: 0 !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="tracklist-row"] > [aria-colindex="1"] { display: none !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="tracklist-row"] [aria-colindex]:last-child > div[data-encore-id="text"] { display: none !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="tracklist-row"] [aria-colindex]:last-child { justify-content: flex-end !important; gap: 4px !important; }
    main :is([data-testid="album-page"], [data-testid="playlist-page"]) [data-testid="tracklist-row"] [data-testid="more-button"] { opacity: 1 !important; visibility: visible !important; }

    /* 表示切り替え（リスト／コンパクト）ボタンは公式アプリにないので消す */
    main [data-testid="action-bar-row"] button[aria-haspopup="true"]:not([data-testid]) { display: none !important; }

    /* プロフィール：公式アプリと同じく、左に大きな丸いアイコン、右に名前とフォロー情報 */
    main [data-testid="entity-header"]:has([data-testid="user-image"]) { height: auto !important; max-height: none !important; min-height: 0 !important; padding: 64px 16px 20px !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) > div:last-child { display: flex !important; flex-direction: row !important; align-items: center !important; height: auto !important; gap: 16px !important; padding: 0 !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) > div:last-child > :first-child { width: 112px !important; height: 112px !important; min-width: 112px !important; margin: 0 !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) [data-testid="user-image"], main [data-testid="entity-header"]:has([data-testid="user-image"]) [data-testid="edit-image-button"] { width: 112px !important; height: 112px !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) > div:last-child > div:last-child { flex: 1 1 auto !important; min-width: 0 !important; width: auto !important; align-items: flex-start !important; text-align: left !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) > div:last-child > div:last-child > span:first-child:not(:has(h1)) { display: none !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) [data-testid="entityTitle"] { width: auto !important; }
    main [data-testid="entity-header"]:has([data-testid="user-image"]) [data-testid="entityTitle"] h1 { font-size: 26px !important; font-weight: 800 !important; line-height: 1.15 !important; white-space: normal !important; word-break: normal !important; }

    /* プロフィールの下は公式アプリと同じく「プレイリスト」の一覧だけ */
    main .m-hide-shelf { display: none !important; }
    main .m-pl-shelf [data-testid="grid-container"] { display: flex !important; flex-direction: column !important; margin: 0 !important; gap: 0 !important; }
    main .m-pl-shelf [data-encore-id="card"] {
      display: flex !important; flex-direction: row !important; align-items: center !important; gap: 14px !important;
      width: 100% !important; height: auto !important; padding: 6px 0 !important; background: none !important; position: relative !important;
    }
    main .m-pl-shelf [data-encore-id="card"] > div:has(img) { width: 56px !important; height: 56px !important; min-width: 56px !important; padding: 0 !important; margin: 0 !important; }
    main .m-pl-shelf [data-encore-id="card"] > div:has(img) *, main .m-pl-shelf [data-testid="card-image"] { width: 56px !important; height: 56px !important; border-radius: 2px !important; }
    main .m-pl-shelf [data-encore-id="card"] > div:last-of-type { flex: 1 !important; min-width: 0 !important; padding: 0 !important; }
    main .m-pl-shelf [data-encore-id="cardTitle"] { font-size: 16px !important; font-weight: 400 !important; }
    main .m-pl-shelf [data-testid="play-button"] { display: none !important; }
    .m-row-more { position: absolute; right: 0; top: 50%; transform: translateY(-50%); width: 40px; height: 40px; background: none; border: 0; color: #b3b3b3; z-index: 2;
      display: flex; align-items: center; justify-content: center; }
    .m-row-more svg { width: 22px; height: 22px; fill: currentColor; }
    #m-edit { height: 32px; padding: 0 14px; border-radius: 16px; border: 1px solid #878787; background: none; color: #fff; font-weight: 700; font-size: 13px; }

    /* 詳細ページ左上の戻るボタン */
    #m-back {
      position: fixed; top: 12px; left: 12px; z-index: 8100; width: 36px; height: 36px; border-radius: 50%; border: 0;
      background: rgba(0,0,0,.55); color: #fff; display: none; align-items: center; justify-content: center;
    }
    #m-back svg { width: 22px; height: 22px; fill: currentColor; }
    html.m-detail #m-back { display: flex; }
    /* スクロールすると出る上部バーのタイトルが戻るボタンに重ならないように */
    html.m-detail [data-testid="topbar-content-wrapper"] { padding-left: 44px !important; }
    html.m-lib #m-back, html.m-search #m-back, html.m-player #m-back, html.m-settings #m-back { display: none !important; }

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
      position: fixed; top: 0; bottom: 0; left: 0; width: 90vw; max-width: 380px; z-index: 10003;
      background: #1f1f1f; transform: translateX(-100%); transition: transform .22s ease; color: #fff;
      display: flex; flex-direction: column; font-family: inherit;
    }
    html.m-drawer #m-drawer-bg { display: block; }
    html.m-drawer #m-drawer { transform: none; }
    #m-drawer { background: #1f1f1f !important; padding-top: 8px; }
    #m-drawer .me { display: flex; align-items: center; gap: 12px; padding: 16px 16px 20px !important; border-bottom: 1px solid rgba(255,255,255,.1); }
    #m-drawer .me .pic { width: 40px; height: 40px; border-radius: 50%; background: #535353 center/cover no-repeat; flex: none;
      display: flex; align-items: center; justify-content: center; font-weight: 700; color: #000; }
    #m-drawer .me b { display: block; font-size: 17px; font-weight: 700; line-height: 1.2; }
    #m-drawer .me small { color: #b3b3b3; font-size: 13px; }
    #m-drawer .card { margin: 16px 12px 8px; background: #2a2a2a; border-radius: 8px; overflow: hidden; }
    #m-drawer .card .me { border-bottom: 1px solid rgba(255,255,255,.12); padding: 12px 14px !important; }
    #m-drawer .card .me .pic { width: 52px; height: 52px; }
    #m-drawer .card .me b { font-size: 20px; }
    #m-drawer .card .add { padding: 12px 14px; font-size: 16px; gap: 14px; }
    #m-drawer .card .add .plus { width: 32px; height: 32px; border-radius: 50%; background: #3e3e3e; display: flex; align-items: center; justify-content: center; }
    #m-drawer .card .add .plus svg { width: 18px; height: 18px; fill: #b3b3b3; }
    #m-drawer button.item svg { width: 26px; height: 26px; }
    #m-drawer .social { display: flex; gap: 20px; padding: 16px 20px; }
    #m-drawer .social button { background: none; border: 0; color: #fff; display: flex; flex-direction: column; align-items: center; gap: 6px; width: 72px; padding: 0; font-family: inherit; }
    #m-drawer .social .ring { position: relative; width: 60px; height: 60px; border-radius: 50%; border: 2px solid #b3b3b3; box-sizing: border-box; display: flex; align-items: center; justify-content: center; }
    #m-drawer .social .ring .pic { width: 50px; height: 50px; border-radius: 50%; background: #535353 center/cover no-repeat; display: flex; align-items: center; justify-content: center; color: #000; font-weight: 700; }
    #m-drawer .social .ring .np { position: absolute; right: -8px; bottom: -6px; width: 30px; height: 30px; border-radius: 3px; object-fit: cover; box-shadow: 0 2px 6px rgba(0,0,0,.5); }
    #m-drawer .social .ring.plus { border: 0; background: #2a2a2a; }
    #m-drawer .social .ring.plus svg { width: 26px; height: 26px; fill: #fff; }
    #m-drawer .social .lbl { font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 72px; }
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
    #m-settings button.row { width: 100%; background: none; border: 0; color: #fff; text-align: left; font-family: inherit; }
    #m-settings button.row svg { width: 20px; height: 20px; fill: #a7a7a7; flex: none; }
    #m-settings .row.me { padding: 16px; gap: 14px; }
    #m-settings .row.me .pic { width: 56px; height: 56px; border-radius: 50%; background: #535353 center/cover no-repeat; flex: none;
      display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 22px; }
    #m-settings .row.me b { font-size: 20px; font-weight: 700; }
    #m-settings .row.nav { padding: 14px 16px; }

    /* 全画面プレーヤー */
    #m-player {
      position: fixed; inset: 0; z-index: 10010; color: #fff; font-family: inherit; display: flex; flex-direction: column;
      padding: 0; box-sizing: border-box; transform: translateY(100%); overflow-x: hidden; overflow-y: auto;
      transition: transform .32s cubic-bezier(.2,.8,.2,1); background: #121212; overscroll-behavior: contain;
    }
    /* 公式アプリと同じく、ジャケットの色をそのまま背景に。下の方だけ暗くする */
    #m-player .page {
      min-height: calc(100% - 64px); display: flex; flex-direction: column; padding: 4px 24px 20px; box-sizing: border-box; flex: none;
      background: linear-gradient(to bottom, var(--m-tint2, #535353) 0%, var(--m-tint2, #535353) 35%, var(--m-tint, #404040) 100%);
    }
    #m-player .extras { padding: 8px 16px 32px; display: flex; flex-direction: column; gap: 16px; background: var(--m-tint, #404040); }
    #m-player .card { border-radius: 10px; padding: 16px; background: rgba(0,0,0,.28); text-align: left; color: #fff; display: block; width: 100%; box-sizing: border-box; }
    #m-player .card h3 { margin: 0 0 8px; font-size: 16px; font-weight: 700; }
    #m-player .card p { margin: 0; font-size: 20px; font-weight: 700; line-height: 1.4; color: rgba(255,255,255,.85); }
    #m-player .card.artist p { font-size: 22px; }
    #m-player .card small { display: block; margin-top: 10px; font-size: 13px; color: rgba(255,255,255,.7); }
    #m-player.dragging { transition: none; }
    #m-player::-webkit-scrollbar { display: none; }
    html.m-player #m-player { transform: none; }
    /* ジャケットをぼかした背景＋色のグラデーション */

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
      width: 100%; max-height: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 6px;
      box-shadow: 0 16px 40px rgba(0,0,0,.35);
      transition: transform .45s cubic-bezier(.2,.8,.2,1);
    }
    #m-player.paused .art img { transform: scale(.88); }
    #m-player .meta { display: flex; align-items: center; gap: 12px; flex: none; }
    #m-player .meta .t { flex: 1; min-width: 0; }
    #m-player .meta b { display: block; font-size: 22px; font-weight: 700; white-space: nowrap; overflow: hidden; }
    #m-player .meta b span { display: inline-block; }
    #m-player .meta span { display: block; color: rgba(255,255,255,.7); font-size: 16px; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    #m-player .like svg { width: 28px; height: 28px; }
    #m-player .like.on { color: var(--m-accent, #1ed760); }
    #m-player .like.on svg { color: var(--m-accent, #1ed760); }
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
    [data-testid="now-playing-bar"] > div > div:first-child { padding-right: 136px !important; }
    #m-mini-ctl { position: absolute; right: 8px; top: 8px; height: 40px !important; padding: 0 !important; width: auto !important; display: flex; align-items: center; gap: 4px; z-index: 2; }
    #m-mini-pp { width: 40px; height: 40px; background: none; border: 0; color: #fff; display: flex; align-items: center; justify-content: center; }
    #m-mini-pp svg { width: 26px; height: 26px; fill: currentColor; }
    #m-mini-prog { position: absolute; left: 8px; right: 8px; bottom: 0; height: 2px !important; padding: 0 !important; width: auto !important; background: rgba(255,255,255,.25); border-radius: 1px; overflow: hidden; z-index: 2; }
    #m-mini-prog i { display: block; height: 100%; width: 0; background: #fff; }
    /* 「〜で再生中」の緑のバーは、ミニプレーヤーのデバイスボタンの色で示す */
    [data-testid="now-playing-bar"] > div:not(:first-child):not([id^="m-mini"]) { display: none !important; }
    /* ミニプレーヤーの文字は自前で描く：1 行目に曲名（・アーティスト）、2 行目にアーティストか接続先デバイス */
    [data-testid="now-playing-widget-v2"] > :not([data-testid="cover-art-container"]):not(#m-mini-text) { display: none !important; }
    #m-mini-text { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 2px; overflow: hidden; }
    #m-mini-text .l1, #m-mini-text .l2 { overflow: hidden; white-space: nowrap; }
    #m-mini-text .l1 span { display: inline-block; font-size: 14px; font-weight: 700; color: #fff; }
    #m-mini-text .l2 { font-size: 13px; color: rgba(255,255,255,.7); display: flex; align-items: center; gap: 4px; }
    #m-mini-text .l2.dev { color: var(--m-accent, #1ed760); font-weight: 600; }
    #m-mini-text .l2 svg { width: 14px; height: 14px; fill: currentColor; flex: none; }
    #m-mini-text .l2 span { overflow: hidden; text-overflow: ellipsis; }
    /* ミニプレーヤーの曲名が長いときは横に流す */
    @keyframes m-marquee { 0%, 15% { transform: translateX(0); } 85%, 100% { transform: translateX(var(--m-dist)); } }
    [data-testid="now-playing-bar"] a { text-decoration: none !important; }
    .m-marquee { animation: m-marquee var(--m-dur, 8s) linear infinite alternate; will-change: transform; }
    #m-mini-dev { width: 40px; height: 40px; background: none; border: 0; color: #fff; flex: none; display: flex; align-items: center; justify-content: center; }
    #m-mini-dev svg { width: 22px; height: 22px; fill: currentColor; }
    #m-mini-dev.on { color: var(--m-accent, #1ed760); }
    /* ミニプレーヤーに再生位置の細いバー（Myspoti 風） */
    [data-testid="playback-progressbar"] [data-testid="progress-bar-background"] { background: rgba(255,255,255,.25) !important; }

    /* ページ下部の「会社情報」などのフッターは公式アプリにないので消す */
    .main-view-container__mh-footer-container, main [data-testid="footer-div"], main nav[aria-label*="フッター"] { display: none !important; }
    /* 選択中のフィルター（すべて／音楽…）は公式アプリと同じ緑 */
    main [role="checkbox"][aria-checked="true"] > *, main button[role="checkbox"][aria-checked="true"] {
      background-color: var(--m-accent, #1ed760) !important; color: #000 !important;
    }
    /* ミニプレーヤーの追加ボタン */
    #m-mini-add { width: 40px; height: 40px; background: none; border: 0; color: #fff; display: flex; align-items: center; justify-content: center; }
    #m-mini-add svg { width: 24px; height: 24px; fill: currentColor; }
    #m-mini-add.on { color: var(--m-accent, #1ed760); }

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
    lib: '<svg viewBox="0 0 24 24"><path d="M4 3h2v18H4zm5 0h2v18H9zm4.7.6 1.9-.6 5.6 17.5-1.9.6z"/></svg>',
    create: '<svg viewBox="0 0 24 24"><path d="M11 3h2v8h8v2h-8v8h-2v-8H3v-2h8z"/></svg>'
  };

  var DRAWER_ICONS = {
    news: '<svg viewBox="0 0 24 24"><path d="M19 4v16l-6-3H8l1.5 5H7.6L6 17H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h9zm-1.8 2.9L13.4 8.8H4v6.4h9.4l3.8 1.9zM21 9.5h1.5v5H21z"/></svg>',
    report: '<svg viewBox="0 0 24 24"><path d="M3 3h1.8v16.2H21V21H3zm15.6 3.4 1.5 1-5 7.4-3.6-3-3.2 4.6-1.5-1 4.3-6.2 3.6 3z"/></svg>',
    history: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm0 1.8a8.2 8.2 0 1 0 0 16.4 8.2 8.2 0 0 0 0-16.4zM11.1 6h1.8v5.6l4 2.3-.9 1.6-4.9-2.8z"/></svg>',
    friends: '<svg viewBox="0 0 24 24"><path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4 0-7 2-7 4.5V20h14v-2.5C16 15 13 13 9 13zm7.5-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm1 2.1c1.9.7 3.5 2.1 3.5 4.4V20h2v-2.5c0-2.1-2.3-3.9-5.5-4.4z"/></svg>',
    settings: '<svg viewBox="0 0 24 24"><path d="M10.6 2h2.8l.5 2.6c.6.2 1.2.5 1.7.9l2.5-.9 1.4 2.4-2 1.8a7 7 0 0 1 0 2.4l2 1.8-1.4 2.4-2.5-.9c-.5.4-1.1.7-1.7.9l-.5 2.6h-2.8l-.5-2.6c-.6-.2-1.2-.5-1.7-.9l-2.5.9-1.4-2.4 2-1.8a7 7 0 0 1 0-2.4l-2-1.8 1.4-2.4 2.5.9c.5-.4 1.1-.7 1.7-.9zm1.4 1.8h-.1l-.4 2.2-.9.3c-.7.2-1.3.6-1.8 1l-.7.6-2.1-.8-.2.3 1.7 1.5-.2.9a5.3 5.3 0 0 0 0 2l.2.9-1.7 1.5.2.3 2.1-.8.7.6c.5.4 1.1.8 1.8 1l.9.3.4 2.2h.2l.4-2.2.9-.3c.7-.2 1.3-.6 1.8-1l.7-.6 2.1.8.2-.3-1.7-1.5.2-.9a5.3 5.3 0 0 0 0-2l-.2-.9 1.7-1.5-.2-.3-2.1.8-.7-.6c-.5-.4-1.1-.8-1.8-1l-.9-.3zM12 8.2a3.8 3.8 0 1 1 0 7.6 3.8 3.8 0 0 1 0-7.6zm0 1.8a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" transform="translate(0 3)"/></svg>',
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

  // プロフィールページを公式アプリの構成に（プレイリストの一覧だけ、編集ボタン、行の「…」）
  function tidyProfile() {
    if (!/^\/user\/[^/]+\/?$/.test(location.pathname.replace(/^\/intl-[a-z-]+/i, ''))) return;
    var shelves = document.querySelectorAll('main [data-testid="component-shelf"]');
    for (var i = 0; i < shelves.length; i++) {
      var sh = shelves[i];
      if (sh.dataset.mDone) continue;
      var h = sh.querySelector('h2');
      if (!h) continue;
      sh.dataset.mDone = '1';
      if (/公開プレイリスト|Public Playlists/i.test(h.textContent)) {
        sh.classList.add('m-pl-shelf');
        var link = h.querySelector('a') || h;
        link.textContent = 'プレイリスト';
      } else sh.classList.add('m-hide-shelf');
    }
    var pl = document.querySelector('main .m-pl-shelf');
    if (pl) pl.querySelectorAll('[data-encore-id="card"]').forEach(function (card) {
      var sub = card.querySelector('[data-encore-id="cardSubtitle"] [data-encore-id="text"], [data-encore-id="cardSubtitle"]');
      if (sub && /人のフォロワー/.test(sub.textContent)) sub.textContent = sub.textContent.replace(/([\d,]+)\s*人のフォロワー/, '$1件の保存');
      if (!card.querySelector('.m-row-more')) {
        var mb = document.createElement('button');
        mb.className = 'm-row-more';
        mb.setAttribute('aria-label', 'その他');
        mb.innerHTML = P.more;
        mb.addEventListener('click', function (ev) {
          ev.stopPropagation();
          ev.preventDefault();
          var r = card.getBoundingClientRect();
          card.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX: r.right - 40, clientY: r.top + r.height / 2 }));
        });
        card.appendChild(mb);
      }
    });
    var bar = document.querySelector('main [data-testid="action-bar-row"]');
    var edit = document.querySelector('main [data-testid="edit-image-button"]');
    if (bar && edit && !document.getElementById('m-edit')) {
      var eb = document.createElement('button');
      eb.id = 'm-edit';
      eb.textContent = '編集';
      eb.addEventListener('click', function () { edit.click(); });
      bar.insertBefore(eb, bar.firstChild);
    }
  }

  // 自分のプロフィールを開く（公式アプリの「プロフィールを表示」）
  var myId = null;
  function withMyId(fn) {
    if (myId) return fn(myId);
    var api = service('UserAPI');
    if (!api) return;
    Promise.resolve(api.getUser()).then(function (u) { if (u && u.username) { myId = u.username; fn(myId); } }, function () {});
  }

  function openProfile() {
    if (myId) return spaGo('/user/' + myId);
    var api = service('UserAPI');
    if (!api) return;
    Promise.resolve(api.getUser()).then(function (u) {
      if (u && u.username) { myId = u.username; spaGo('/user/' + myId); }
    }, function () {});
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

  // ---- 音質（Web Player の再生エンジンに直接指定する） ----
  var QUALITIES = [
    ['auto', '自動', '通信状況に合わせて調整'],
    ['low', '低音質', '約 24 kbps'],
    ['normal', '標準', '約 96 kbps'],
    ['high', '高音質', '約 160 kbps'],
    ['very_high', '最高音質', '約 320 kbps（Spotify Premium のみ）']
  ];
  // Web Player 内部のサービス（PlayerSDK・UserAPI など）を名前で取り出す
  var registryCache = null;
  function registry() {
    if (registryCache) return registryCache;
    var host = document.getElementById('main');
    if (!host) return null;
    var els = [host].concat([].slice.call(host.querySelectorAll('*')));
    for (var i = 0; i < els.length; i++) {
      var k = Object.keys(els[i]).find(function (x) { return x.indexOf('__reactContainer$') === 0; });
      if (!k) continue;
      var stack = [els[i][k]];
      while (stack.length) {
        var f = stack.pop();
        var v = f.memoizedProps && f.memoizedProps.value;
        if (v && typeof v === 'object' && typeof v.getRegistry === 'function') return (registryCache = v.getRegistry());
        if (f.sibling) stack.push(f.sibling);
        if (f.child) stack.push(f.child);
      }
    }
    return null;
  }
  function service(name) {
    var reg = registry();
    if (!reg) return null;
    var key = null;
    reg._map.forEach(function (_, rk) { if (rk && rk.description === name) key = rk; });
    return key ? reg.resolve(key) : null;
  }
  function playerSdk() { return service('PlayerSDK'); }
  function streamer() {
    var sdk = playerSdk();
    return sdk && sdk.harmony && sdk.harmony._streamer;
  }
  function setQuality(level) {
    var st = streamer();
    if (!st || typeof st.setMediaPlaybackQuality !== 'function') return Promise.resolve(false);
    return Promise.resolve(st.setMediaPlaybackQuality({ audio: level })).then(function (ok) { return ok !== false; }, function () { return false; });
  }
  function currentBitrate() {
    var st = streamer();
    if (!st || !st._playerPromise) return Promise.resolve(0);
    return st._playerPromise.then(function (p) { return p && p._currentContent && p._currentContent._bitrate || 0; }, function () { return 0; });
  }
  // ---- 音量の均一化 ----
  // Web Player の設定画面のスイッチは保存されない（デスクトップアプリ専用の設定）ため、
  // 再生中の音声にコンプレッサーを挟んで曲ごとの音量差を自前でならす。
  var leveler = null;
  function audioElement() {
    var st = streamer();
    if (!st || !st._playerPromise) return Promise.resolve(null);
    return st._playerPromise.then(function (p) { return p && p._player instanceof HTMLMediaElement ? p : null; }, function () { return null; });
  }
  function applyLeveling(on) {
    return audioElement().then(function (p) {
      if (!p) return false;
      var ap = p._audioProcessor || {};
      if (!leveler || leveler.el !== p._player) {
        var ctx = ap._audioContext, src = ap._mediaElementSource;
        if (!ctx || !src) {
          ctx = new (window.AudioContext || window.webkitAudioContext)();
          src = ctx.createMediaElementSource(p._player);
          // Spotify のイコライザーが後から同じ要素に接続しようとしても使えるよう、作ったものを渡しておく
          if (!ap._audioContext) { ap._audioContext = ctx; ap._mediaElementSource = src; }
        }
        var comp = ctx.createDynamicsCompressor();
        comp.threshold.value = -24; comp.knee.value = 24; comp.ratio.value = 4;
        comp.attack.value = 0.01; comp.release.value = 0.3;
        var makeup = ctx.createGain();
        makeup.gain.value = 1.6;
        comp.connect(makeup);
        leveler = { el: p._player, ctx: ctx, src: src, comp: comp, makeup: makeup, on: null };
      }
      if (leveler.on === on) return true;
      try { leveler.src.disconnect(); } catch (e) {}
      try { leveler.makeup.disconnect(); } catch (e) {}
      if (on) { leveler.src.connect(leveler.comp); leveler.makeup.connect(leveler.ctx.destination); }
      else leveler.src.connect(leveler.ctx.destination);
      if (leveler.ctx.state === 'suspended') leveler.ctx.resume();
      leveler.on = on;
      return true;
    }).catch(function () { return false; });
  }
  var levelingApplied = false;
  function applySavedLeveling() {
    if (levelingApplied || pref('normalize', '0') !== '1' || !streamer()) return;
    levelingApplied = true;
    applyLeveling(true).then(function (ok) { if (!ok) levelingApplied = false; });
  }

  var qualityApplied = false;
  function applySavedQuality() {
    if (qualityApplied) return;
    var lv = pref('quality', 'auto');
    if (lv === 'auto') { qualityApplied = true; return; }
    if (!streamer()) return;
    qualityApplied = true;
    setQuality(lv);
  }

  // 設定は公式アプリと同じく「カテゴリ一覧 → 各ページ」の 2 階層
  var settingsPage = null;
  var CHEVRON = '<svg viewBox="0 0 24 24"><path d="M8.6 5.4 10 4l8 8-8 8-1.4-1.4 6.6-6.6z"/></svg>';

  function settingsHeader(box, title, onBack) {
    box.innerHTML = '<header><button aria-label="戻る">' + BACK + '</button><h1></h1></header>';
    box.querySelector('h1').textContent = title;
    box.querySelector('header button').addEventListener('click', onBack);
    box.scrollTop = 0;
  }

  function settingsSection(box, name, rows) {
    rows = rows.filter(Boolean);
    if (!rows.length) return;
    if (name) {
      var h = document.createElement('h2');
      h.textContent = name;
      box.appendChild(h);
    }
    rows.forEach(function (r) { box.appendChild(r); });
  }

  function linkButton(label, fn) {
    var b = document.createElement('button');
    b.className = 'link';
    b.style.cssText = 'background:none;border:0;font-size:14px';
    b.textContent = label;
    b.addEventListener('click', fn);
    return b;
  }

  var SETTINGS_PAGES = [
    ['account', 'アカウント', 'ユーザー名・ログアウト'],
    ['display', 'コンテンツと表示', 'テーマ・アクセントカラー・Canvas・言語'],
    ['playback', '再生', '音量の均一化・バックグラウンド再生'],
    ['privacy', 'プライバシーとソーシャル', 'フォロワー・プレイリストの公開'],
    ['quality', 'メディアの品質', 'ストリーミングの音質'],
    ['app', 'アプリとデバイス', 'イコライザー・プレーヤーの再起動'],
    ['about', 'このアプリについて', 'バージョン情報']
  ];

  function renderSettingsHome() {
    settingsPage = null;
    var box = document.getElementById('m-settings');
    settingsHeader(box, '設定とプライバシー', closeSettings);
    var a = account();
    var me = document.createElement('button');
    me.className = 'row me';
    me.innerHTML = '<div class="pic"></div><div class="t"><b></b><small>プロフィールを表示</small></div>' + CHEVRON;
    setPic(me.querySelector('.pic'), a);
    me.querySelector('b').textContent = a.name;
    me.addEventListener('click', function () {
      closeSettings();
      setTimeout(openProfile, 300);
    });
    box.appendChild(me);
    SETTINGS_PAGES.forEach(function (pg) {
      var r = document.createElement('button');
      r.className = 'row nav';
      r.innerHTML = '<div class="t"><b></b><small></small></div>' + CHEVRON;
      r.querySelector('b').textContent = pg[1];
      r.querySelector('small').textContent = pg[2];
      r.addEventListener('click', function () { renderSettingsPage(pg[0], pg[1]); });
      box.appendChild(r);
    });
  }

  function renderSettingsPage(id, title) {
    settingsPage = id;
    var box = document.getElementById('m-settings');
    settingsHeader(box, title, renderSettingsHome);
    var sec = function (name, rows) { settingsSection(box, name, rows); };
    if (id === 'account') {
      var a = account();
      if (!a.name) {
        sec(null, [row('ログインしていません', 'Spotify アカウントでログインしてください', linkButton('ログイン', function () {
          location.href = 'https://accounts.spotify.com/ja/login?continue=' + encodeURIComponent('https://open.spotify.com/');
        }))]);
        return;
      }
      sec(null, [
        row('ユーザー名', a.name || '—', null),
        row('ログアウト', 'このアプリからログアウトします', linkButton('ログアウト', function () {
          closeSettings();
          setTimeout(function () { userMenu(function (e) { return /ログアウト|Log out/.test(e.textContent); }); }, 300);
        }))
      ]);
    } else if (id === 'display') {
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
      sec('テーマ', [
        row('AMOLED ブラック', '背景を真っ黒にして電池を節約', switchEl(pref('amoled', '0') === '1', function (v) { setPref('amoled', v ? '1' : '0'); })),
        row('アクセントカラー', null, colors),
        row('画面の向き', null, selectEl([['portrait', '縦に固定'], ['auto', '自動回転']], nativeValue('orientation', 'portrait'), function (v) { nativeSetting('orientation', v); }))
      ]);
      sec('ビデオと Canvas', [
        spotifySwitch('Canvas', '曲の再生中に短いループ動画を表示', /^Canvas$/),
        spotifySwitch('ミュージックビデオ', 'オフにすると音声のみで再生', /ミュージックビデオ|Music videos/),
        spotifySwitch('その他のビデオ', 'ビデオポッドキャストなど', /その他のビデオ|other videos/i)
      ]);
      sec('言語', [spotifySelect('アプリの言語', '変更後はプレーヤーを再起動', /言語を選択|language/i)]);
    } else if (id === 'playback') {
      sec(null, [
        row('音量の均一化', '曲ごとの音量差をならして、同じくらいの音量で再生する', switchEl(pref('normalize', '0') === '1', function (v) {
          try { localStorage.setItem('m.normalize', v ? '1' : '0'); } catch (e) {}
          applyLeveling(v);
        })),
        row('バックグラウンド再生', 'アプリを閉じても再生を続ける', switchEl(nativeValue('background', 'true') === 'true', function (v) { nativeSetting('background', v); }))
      ]);
    } else if (id === 'privacy') {
      sec(null, [
        row('再生状況を公開', 'Spotify の仕様で、この設定は公式アプリ（PC・スマホ）からしか変更できません。Web Player 版では保存されません', null),
        spotifySwitch('フォロワーとフォロー中を公開', null, /フォロワー＆フォロー中|followers/i),
        spotifySwitch('プロフィールにプレイリストを表示', null, /プロフィールに追加したプレイリスト|playlists on/i)
      ]);
    } else if (id === 'quality') {
      var info = row('いま再生中の音質', '確認中…', null);
      var sel = selectEl(QUALITIES.map(function (x) { return [x[0], x[1]]; }), pref('quality', 'auto'), function (v) {
        try { localStorage.setItem('m.quality', v); } catch (e) {}
        detail.textContent = '切り替え中…';
        setQuality(v).then(function (ok) {
          var d = QUALITIES.filter(function (x) { return x[0] === v; })[0];
          detail.textContent = ok ? d[2] + '。次の曲から反映されます' : 'この音質は選べませんでした（Premium 限定の可能性があります）';
        });
      });
      var qrow = row('ストリーミングの音質', null, sel);
      var detail = document.createElement('small');
      detail.textContent = (QUALITIES.filter(function (x) { return x[0] === pref('quality', 'auto'); })[0] || QUALITIES[0])[2];
      qrow.querySelector('.t').appendChild(detail);
      sec('ストリーミング', [qrow, info]);
      sec('ロスレス', [row('ロスレス（FLAC）', 'Spotify Premium の機能です。Web Player では選べません', null)]);
      currentBitrate().then(function (b) {
        info.querySelector('small').textContent = b ? Math.round(b / 1000) + ' kbps' : '不明（再生中の曲がありません）';
      });
    } else if (id === 'app') {
      sec(null, [
        row('イコライザー・詳細設定', 'Spotify の設定ページを開く', linkButton('開く', function () { document.documentElement.classList.remove('m-settings'); })),
        row('プレーヤーを再起動', '再生が止まったときに', linkButton('実行', function () { location.href = '/'; })),
        row('開発者向けデバッグ', 'PC から画面の中身を調べられるようにする（アプリの再起動後に反映）', switchEl(nativeValue('webdebug', 'false') === 'true', function (v) { nativeSetting('webdebug', v); }))
      ]);
    } else if (id === 'about') {
      sec(null, [
        row('Spotify Web', 'Spotify Web Player を使った非公式のアプリです', null),
        row('データの保存', '曲は端末に保存しません。通信は Spotify と直接行います', null)
      ]);
    }
  }

  function renderSettings() { renderSettingsHome(); }

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
        document.documentElement.style.setProperty('--m-tint', 'rgb(' + Math.round(r / n * 0.5) + ',' + Math.round(gr / n * 0.5) + ',' + Math.round(b / n * 0.5) + ')');
        document.documentElement.style.setProperty('--m-tint2', 'rgb(' + Math.round(r / n * 0.8) + ',' + Math.round(gr / n * 0.8) + ',' + Math.round(b / n * 0.8) + ')');
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
    add: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm0 1.6a8.4 8.4 0 1 0 0 16.8 8.4 8.4 0 0 0 0-16.8zM11.2 7h1.6v4.2H17v1.6h-4.2V17h-1.6v-4.2H7v-1.6h4.2z"/></svg>',
    saved: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm4.3 6.3-5.6 5.6-2.9-2.9-1.4 1.4 4.3 4.3 7-7z"/></svg>',
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
      '<div class="page"><div class="grab"></div>' +
      '<div class="top"><button class="ib down" aria-label="閉じる">' + P.down + '</button>' +
      '<div class="ctx"><small>再生中</small><b></b></div><button class="ib more" aria-label="その他">' + P.more + '</button></div>' +
      '<div class="art"><img alt=""></div>' +
      '<div class="meta"><div class="t"><b></b><span></span></div><button class="ib like" aria-label="お気に入りに追加">' + P.add + '</button></div>' +
      '<div class="seek"><input type="range" min="0" max="1000" value="0"><div class="times"><span class="pos">0:00</span><span class="dur">0:00</span></div></div>' +
      '<div class="ctrls"><button class="ib s shuffle" aria-label="シャッフル">' + P.shuffle + '</button>' +
      '<button class="ib m prev" aria-label="前へ">' + P.prev + '</button>' +
      '<button class="pp" aria-label="再生">' + P.play + '</button>' +
      '<button class="ib m next" aria-label="次へ">' + P.next + '</button>' +
      '<button class="ib s repeat" aria-label="リピート">' + P.repeat + '</button></div>' +
      '<div class="bottom"><button class="dev">' + P.device + '<span>このデバイス</span></button>' +
      '<button class="ib share" aria-label="共有">' + P.share + '</button>' +
      '<button class="ib queue" aria-label="キュー">' + P.queue + '</button></div></div>' +
      '<div class="extras"><button class="card lyrics-card"><h3>歌詞</h3><p>タップして歌詞を表示</p></button>' +
      '<button class="card artist"><h3>アーティストについて</h3><p></p><small>アーティストのページを開く</small></button></div>';
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
    on('.lyrics-card', function () { closePlayer(); var b = q('[data-testid="lyrics-button"]'); if (b) b.click(); });
    on('.extras .artist', function () { closePlayer(); var a = q('[data-testid="now-playing-widget-v2"] a[href*="/artist/"]'); if (a) a.click(); });
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
      if (e.target.closest('input, .ctrls, .bottom, .extras') || el.scrollTop > 0) return;
      startY = e.touches[0].clientY; dy = 0;
      el.classList.add('dragging');
    }, { passive: true });
    el.addEventListener('touchmove', function (e) {
      if (startY == null) return;
      dy = Math.max(0, e.touches[0].clientY - startY);
      if (el.scrollTop > 0) { dy = 0; return; }
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
    }
    var tb = el.querySelector('.meta b');
    var tt = title ? title.textContent : '';
    if (tb.dataset.t !== tt) {
      tb.dataset.t = tt;
      tb.innerHTML = '<span></span>';
      var ts = tb.firstChild;
      ts.textContent = tt;
      requestAnimationFrame(function () {
        var dist = ts.scrollWidth - tb.clientWidth;
        if (dist > 4) {
          ts.style.setProperty('--m-dist', -dist + 'px');
          ts.style.setProperty('--m-dur', Math.max(4, dist / 25) + 's');
          ts.classList.add('m-marquee');
        }
      });
    }
    el.querySelector('.meta span').textContent = by ? by.textContent : '';
    // 再生元（プレイリスト・アルバムなど）は Connect の状態から
    var st = window.__mConnect && __mConnect.get();
    var ps = st && st.cluster && st.cluster.player_state;
    var cu = ps && ps.context_uri || '';
    var cname = ps && ps.context_metadata && (ps.context_metadata.context_description || ps.context_metadata.title) || '';
    var kind = /:playlist:/.test(cu) ? 'プレイリスト' : /:album:/.test(cu) ? 'アルバム' : /:artist:/.test(cu) ? 'アーティスト' : /:collection/.test(cu) ? 'お気に入りの曲' : '';
    el.querySelector('.ctx small').textContent = kind ? kind + 'から再生中' : '再生中';
    el.querySelector('.ctx b').textContent = cname || (kind === 'お気に入りの曲' ? 'お気に入りの曲' : (by ? by.textContent : ''));
    var pp = q('[data-testid="control-button-playpause"]');
    var playing = pp && /一時停止|Pause/.test(pp.getAttribute('aria-label') || '');
    el.querySelector('.pp').innerHTML = playing ? P.pause : P.play;
    el.classList.toggle('paused', !playing);
    var like = q('[data-testid="now-playing-widget-v2"] button[aria-checked]');
    var liked = like && like.getAttribute('aria-checked') === 'true';
    el.querySelector('.like').classList.toggle('on', !!liked);
    el.querySelector('.like').innerHTML = liked ? P.saved : P.add;
    var ln = el.querySelector('.extras .artist p');
    if (ln) ln.textContent = by ? by.textContent : '';
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

  // ミニプレーヤーの文字（公式アプリと同じ 2 行構成）
  function renderMiniText() {
    var w = q('[data-testid="now-playing-widget-v2"]');
    if (!w) return;
    var box = document.getElementById('m-mini-text');
    if (!box || box.parentElement !== w) {
      box = document.createElement('div');
      box.id = 'm-mini-text';
      box.innerHTML = '<div class="l1"><span></span></div><div class="l2"></div>';
      w.appendChild(box);
    }
    var t = w.querySelector('a[href*="/album/"], a[href*="/track/"], a[href*="/episode/"]');
    var by = w.querySelector('[data-testid="now-playing-widget-v2-byline"]');
    var title = t ? t.textContent : '', artist = by ? by.textContent : '';
    var remote = activeDeviceName();
    var l1 = remote ? title + ' • ' + artist : title;
    var span = box.querySelector('.l1 span');
    var l2 = box.querySelector('.l2');
    var key = l1 + '|' + remote;
    if (box.dataset.key === key) return;
    box.dataset.key = key;
    span.textContent = l1;
    l2.className = 'l2' + (remote ? ' dev' : '');
    l2.innerHTML = remote ? P.device + '<span></span>' : '<span></span>';
    l2.querySelector('span').textContent = remote || artist;
    // はみ出すときだけ横に流す
    span.classList.remove('m-marquee');
    requestAnimationFrame(function () {
      var dist = span.scrollWidth - box.querySelector('.l1').clientWidth;
      if (dist > 4) {
        span.style.setProperty('--m-dist', -dist + 'px');
        span.style.setProperty('--m-dur', Math.max(4, dist / 25) + 's');
        span.classList.add('m-marquee');
      }
    });
  }

  function updateMini() {
    sendMeta();
    var ab = document.getElementById('m-mini-add');
    if (ab) {
      var lk = q('[data-testid="now-playing-widget-v2"] button[aria-checked]');
      var on = !!lk && lk.getAttribute('aria-checked') === 'true';
      if (ab.dataset.on !== String(on)) { ab.dataset.on = String(on); ab.classList.toggle('on', on); ab.innerHTML = on ? P.saved : P.add; }
    }
    renderMiniText();
    // ライブラリの見出しの左に自分のアイコン（タップでドロワー）
    var lh = document.querySelector('#Desktop_LeftSidebar_Id header button[aria-label*="折りたたむ"], #Desktop_LeftSidebar_Id header button[aria-label*="Collapse"]');
    if (lh && !document.getElementById('m-lib-avatar')) {
      var la = document.createElement('button');
      la.id = 'm-lib-avatar';
      la.setAttribute('aria-label', 'アカウント');
      la.addEventListener('click', function (ev) { ev.stopPropagation(); openDrawer(); });
      lh.parentElement.insertBefore(la, lh);
    }
    var lav = document.getElementById('m-lib-avatar');
    if (lav && !lav.dataset.ready) { var acc = account(); if (acc.name) { setPic(lav, acc); lav.dataset.ready = '1'; } }
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
    setPic(d.querySelector('.me .pic'), a);
    d.querySelector('.me b').textContent = a.name || 'ログインしていません';
    d.querySelector('.me small').textContent = a.name ? 'プロフィールを表示' : 'タップしてログイン';
    // 未ログインのときはログインが前提の項目（ログアウト・最新情報・フレンド）を出さない
    d.querySelectorAll('[data-item]').forEach(function (b) {
      b.style.display = a.name || b.dataset.item === 'settings' ? '' : 'none';
    });
    var sp = d.querySelector('.social .pic');
    if (sp) setPic(sp, a);
    var np = q('[data-testid="cover-art-image"]');
    var npi = d.querySelector('.social .np');
    if (npi) { npi.src = np ? np.src : ''; npi.style.display = np ? '' : 'none'; }
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
    d.innerHTML = '<div class="card"><button class="me item"><div class="pic"></div><div><b></b><small>プロフィールを表示</small></div></button>' +
      '<button class="add item" data-item="add"><span class="plus">' + ICONS.create + '</span><span>アカウントを追加する</span></button></div>';
    d.querySelector('.me').addEventListener('click', function () {
      closeDrawer();
      if (!account().name) { if (!click('[data-testid="login-button"]')) location.href = 'https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F'; return; }
      setTab('home');
      openProfile();
    });
    d.querySelector('.add').addEventListener('click', function () {
      closeDrawer();
      location.href = 'https://accounts.spotify.com/ja/login?continue=' + encodeURIComponent('https://open.spotify.com/');
    });
    [
      ['report', 'リスニングレポート', function () { withMyId(function (id) { spaGo('/user/' + id + '/top/artists'); }); }],
      ['history', '最近', function () { spaGo('/genre/recently-played'); }],
      ['news', 'あなたへのおすすめ新着', function () { spaGo('/content-feed'); }],
      ['settings', '設定とプライバシー', openSettings]
    ].forEach(function (it) {
      var b = document.createElement('button');
      b.className = 'item';
      b.dataset.item = it[0];
      b.innerHTML = DRAWER_ICONS[it[0]] + '<span>' + it[1] + '</span>';
      b.addEventListener('click', function () {
        closeDrawer();
        if (it[0] !== 'settings') setTab('home');
        it[2]();
      });
      d.appendChild(b);
    });
    // アクティビティ（自分のアイコンに再生中のジャケット）と友達を招待
    var social = document.createElement('div');
    social.className = 'social';
    social.dataset.item = 'social';
    social.innerHTML = '<button class="act"><span class="ring"><span class="pic"></span><img class="np" alt=""></span><span class="lbl">アクティビティ</span></button>' +
      '<button class="invite"><span class="ring plus">' + ICONS.create + '</span><span class="lbl">友達を招待</span></button>';
    social.querySelector('.act').addEventListener('click', function () { closeDrawer(); setTab('home'); openProfile(); });
    social.querySelector('.invite').addEventListener('click', function () {
      withMyId(function (id) {
        var url = 'https://open.spotify.com/user/' + id;
        if (navigator.share) navigator.share({ title: 'Spotify', url: url }).catch(function () {});
        else if (navigator.clipboard) navigator.clipboard.writeText(url);
      });
    });
    d.appendChild(social);
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
        '<button id="m-mini-add" aria-label="お気に入りに追加">' + P.add + '</button>' +
        '<button id="m-mini-pp" aria-label="再生">' + P.play + '</button>';
      box.querySelector('#m-mini-add').addEventListener('click', function (ev) {
        ev.stopPropagation();
        var lb = q('[data-testid="now-playing-widget-v2"] button[aria-checked]');
        if (lb) lb.click();
      });
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
    // アーティスト・アルバム・プレイリストなどの詳細ページでは左上に戻るボタン
    var detail = /\/(artist|album|playlist|show|episode|user|genre|section|collection|lyrics)\b/.test(location.pathname);
    document.documentElement.classList.toggle('m-detail', detail);
    if (!document.getElementById('m-back') && document.body) {
      var bb = document.createElement('button');
      bb.id = 'm-back';
      bb.setAttribute('aria-label', '戻る');
      bb.innerHTML = BACK;
      bb.addEventListener('click', function () { history.back(); });
      document.body.appendChild(bb);
    }
    applySavedQuality();
    tidyProfile();
    applySavedLeveling();
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

  // 未ログインならログインページへ（ページの表示が落ち着いてから判定する）
  setTimeout(function check() {
    var loginBtn = document.querySelector('[data-testid="login-button"]');
    var user = document.querySelector('[data-testid="user-widget-link"]');
    if (user) return;
    if (loginBtn) {
      location.href = 'https://accounts.spotify.com/ja/login?continue=' + encodeURIComponent('https://open.spotify.com/');
      return;
    }
    setTimeout(check, 1000);
  }, 2500);

  // Android の戻るボタン用：オーバーレイが開いていれば閉じて true を返す
  window.__mobileBack = function () {
    var h = document.documentElement.classList;
    if (h.contains('m-devices')) { closeDevices(); return true; }
    if (h.contains('m-searching')) { var si = document.querySelector('#global-nav-bar input'); if (si) si.blur(); h.remove('m-searching'); return true; }
    if (h.contains('m-drawer')) { closeDrawer(); return true; }
    if (h.contains('m-player') && h.contains('m-np')) { closePanel(); return true; }
    if (h.contains('m-player')) { closePlayer(); return true; }
    if (h.contains('m-settings')) { if (settingsPage) renderSettingsHome(); else closeSettings(); return true; }
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
