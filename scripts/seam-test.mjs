// つなぎ目（bridge.js の 2 台目の再生器）を、偽物の Web Player で動かして確かめる。
// 実機で見つかった再生要素のくせ（音を出し始めると止まる・シークに時間がかかる・鍵の処理が重いと鳴っている方が止まる）をまねて、
// 「聞こえる音」が途切れない・巻き戻らない・ぶつっと切り替わらないことを確かめる。
// Spotify 側の変更は分からない（それはデバッグログで気づく）。ここで防ぐのは、こちらの書き換えで壊すこと。
// 実行: node scripts/seam-test.mjs
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const BRIDGE = readFileSync(process.env.BRIDGE || new URL('../app/src/main/assets/bridge.js', import.meta.url), 'utf8');
const TICK = 10; // ms
const VOLUME = 0.8;

// opts:
//   xf: クロスフェードの秒数（0 ならギャップレス）
//   secs: 曲の長さ（秒）。secsList なら曲ごとの長さ
//   startPausedMs: 最初にこの長さ止まっている（そのあと再生）
//   seekMs: シークにかかる時間（配列なら順番に使う）
//   postSeekStallMs: シークのあと、この長さ止まってから進む
//   unmuteSlowMs: 音なし（muted か音量 0）から音を出し始めると、この長さ 0.3 倍の速さでしか進まない（その分遅れる）
//   mainLoadMs: Web Player が次の曲へ進んでから、1 台目が鳴り始めるまで
//   p2StallAt: { secs, ms }：2 台目が次の曲のこの位置でこの長さ止まる
//   drm: false なら、鍵の処理が重くても鳴っている要素を止めない（ほかの壊れ方だけを見る）
//   skipAt: Web Player が次の曲へ進んでからこの ms 後に、利用者が「次へ」を押す
//   adBefore: { index, secs }：Web Player が index の曲へ進む前に、この長さの広告を流す（並びには出てこない。曲の終わりに差し込まれる）
//   adblock: false なら、設定の「広告の処理」をオフにする
function run(opts = {}) {
  const W = Object.assign({ xf: 10, secs: 40, startPausedMs: 1000, seekMs: 150, postSeekStallMs: 200, unmuteSlowMs: 500, mainLoadMs: 300 }, opts);
  let now = 1_700_000_000_000;
  const timers = new Map();
  let nextId = 1;
  const addTimer = (fn, ms, repeat) => { const id = nextId++; timers.set(id, { fn, at: now + Math.max(ms || 0, 1), ms: Math.max(ms || 0, 1), repeat }); return id; };
  const later = (ms, fn) => addTimer(fn, ms, false);
  const logs = [];
  const tracks = ['A', 'B', 'C', 'D'].map((n, i) => ({ uri: 'spotify:track:' + n, secs: W.secsList ? W.secsList[i] || W.secs : W.secs }));
  let seekIndex = 0;
  const seekMs = () => (Array.isArray(W.seekMs) ? W.seekMs[seekIndex++ % W.seekMs.length] : W.seekMs);

  // ---- 鍵（DRM）：要求と登録が続けて重く走ると、鳴っている再生要素が止まる ----
  // 1 回の処理は DRM_OP_MS。前の処理の終わりから DRM_GAP_MS 以内に次が始まると続けて走ったことになり、
  // 続けて DRM_LIMIT_MS を超えたら、超えた分だけ鳴っている要素が止まる（実機では 0.6 秒ほどで 0.3〜0.5 秒止まった）
  const DRM_OP_MS = 250, DRM_GAP_MS = 300, DRM_LIMIT_MS = 450;
  const drm = { chainStart: 0, chainEnd: 0, ops: [] };
  const elements = new Set();
  function drmOp(ms, label) {
    drm.ops.push({ at: now, label });
    if (now - drm.chainEnd > DRM_GAP_MS) drm.chainStart = now;
    drm.chainEnd = now + ms;
    const over = drm.chainEnd - drm.chainStart - DRM_LIMIT_MS;
    if (over > 0 && W.drm !== false) for (const el of elements) if (!el.paused) el.stallUntil = Math.max(el.stallUntil, drm.chainEnd - DRM_LIMIT_MS + over);
  }

  class FakeMedia {
    constructor(name) {
      this.name = name; this._volume = 1; this._muted = false; this.playbackRate = 1; this._t = 0; this.duration = NaN;
      this.paused = true; this.readyState = 0; this.seeking = false; this.stallUntil = 0; this.slowUntil = 0; this.keyReady = false; this.track = null; this.gain = 1;
      this.listeners = {}; this.currentSrc = ''; elements.add(this);
    }
    get volume() { return this._volume; }
    set volume(v) { if (this._volume <= 0 && v > 0 && !this._muted) this.startSound(); this._volume = v; }
    get muted() { return this._muted; }
    set muted(m) { if (this._muted && !m && this._volume > 0) this.startSound(); this._muted = m; }
    startSound() { if (!this.paused) this.slowUntil = Math.max(this.slowUntil, now + W.unmuteSlowMs); }
    get currentTime() { return this._t; }
    set currentTime(v) {
      if (this.readyState < 1) { this._t = v; return; }
      this.seeking = true; this._seekTo = v; this._seekDone = now + seekMs();
    }
    addEventListener(n, f) { (this.listeners[n] = this.listeners[n] || []).push(f); }
    removeEventListener(n, f) { this.listeners[n] = (this.listeners[n] || []).filter((x) => x !== f); }
    emit(n) { for (const f of (this.listeners[n] || []).slice()) f(); }
    play() { this.paused = false; return Promise.resolve(); }
    pause() { this.paused = true; }
    removeAttribute() {}
    load() { this.paused = true; this.duration = NaN; this.readyState = 0; elements.delete(this); }
    advance() {
      if (this.seeking) {
        if (now < this._seekDone) return;
        this.seeking = false; this._t = this._seekTo; this.stallUntil = Math.max(this.stallUntil, now + W.postSeekStallMs);
        return;
      }
      if (this.paused || this.readyState < 3 || !this.keyReady || now < this.stallUntil) return;
      if (this.p2Stall && this._t >= this.p2Stall.secs) { this.stallUntil = now + this.p2Stall.ms; this.p2Stall = null; return; }
      this._t = Math.min(this.duration, this._t + (now < this.slowUntil ? 0.3 : 1) * this.playbackRate * TICK / 1000);
    }
    // 今聞こえている大きさ（止まっている・音なしなら 0）。gain は再生器がかける正規化
    level() { return this.paused || this.seeking || this.readyState < 3 || !this.keyReady || now < this.stallUntil || this._muted ? 0 : this._volume * this.gain; }
  }

  class FakeEme {
    constructor(o) { this.main = !o; this.inits = []; }
    init() { this.inits.push(now); drmOp(1700, 'init'); return new Promise((r) => later(1700, r)); }
    // 鍵の応答を受け取って登録する（bridge.js が間を空けるためにこれを包む）
    _handleLicenseResponse(t) { drmOp(DRM_OP_MS, (this.main ? 'main' : 'p2') + '.update'); later(DRM_OP_MS, () => { t.el.keyReady = true; }); }
    destroySessions() { return Promise.resolve(); }
  }
  // 鍵の要求を出して、応答が届いたら登録する
  function license(eme, el) {
    el.keyReady = false;
    drmOp(DRM_OP_MS, (eme.main ? 'main' : 'p2') + '.generate');
    later(DRM_OP_MS + 100, () => eme._handleLicenseResponse({ el }, { status: 200, body: 1 }));
  }
  const mainEme = new FakeEme();
  const p2s = [];
  class FakeTrackPlayer {
    constructor(o) {
      this._emeManager = o ? o.emeManager : mainEme; this._player = null; this._audioProcessor = { _audioContext: null };
      // 音の加工の指定が無いと、自前で曲ごとの音量の正規化をかける（実機では -5.5 dB ほど小さくなった）。1 台目は加工しない
      const ap = o && o.audioProcessorOptions;
      this.gain = o && !(ap && (ap.disable || ap.audioContext)) ? 0.53 : 1;
    }
    load(next) {
      const el = new FakeMedia('p2'); this._player = el; p2s.push(el); el.gain = this.gain;
      el.track = next.uri; el.duration = next.secs; el.readyState = 4; el._muted = true; el._t = 0;
      if (W.p2StallAt) el.p2Stall = W.p2StallAt;
      license(this._emeManager, el);
      // 読み込むと勝手に鳴り始める（bridge.js が止めて頭に戻す）
      el.paused = false;
      return Promise.resolve();
    }
    pause() { if (this._player) this._player.paused = true; return Promise.resolve(); }
    seek(t) { if (this._player) this._player._t = t; return Promise.resolve(); }
    resume() { if (this._player) this._player.paused = false; return Promise.resolve(); }
    destroy() { return Promise.resolve(); }
  }
  const mainTp = new FakeTrackPlayer();

  // ---- Web Player（1 台目） ----
  const main = new FakeMedia('main');
  main._volume = VOLUME;
  let index = 0, paused = W.startPausedMs > 0, trackChangedAt = 0, adFor = -1, adDone = false;
  function loadMain(i, startPaused) {
    index = i; trackChangedAt = now;
    main.track = tracks[i].uri; main._t = 0; main.duration = NaN; main.readyState = 0; main.paused = true; main.playbackRate = 1;
    elements.add(main);
    license(mainEme, main);
    later(W.mainLoadMs, () => {
      if (index !== i) return;
      main.duration = tracks[i].secs; main.readyState = 4;
      if (!startPaused && !paused) main.paused = false;
    });
  }
  function advanceTrack(i) {
    main.emit('emptied');
    if (W.adBefore && W.adBefore.index === i && !adDone) return loadAd(i);
    adFor = -1;
    loadMain(i, false);
  }
  // 広告は鍵が要らず、読み込むとすぐ鳴る。Web Player は広告のあいだ index を前の曲のままにして、次の曲を並びに出し続ける
  function loadAd(i) {
    adFor = i; adDone = true; trackChangedAt = now;
    main.track = 'spotify:ad:' + i; main._t = 0; main.duration = W.adBefore.secs; main.readyState = 4; main.keyReady = true; main.playbackRate = 1;
    main.paused = paused;
  }
  loadMain(0, paused);
  if (paused) later(W.startPausedMs, () => { paused = false; main.paused = false; });
  if (W.skipAt != null) {
    // 2 曲目に進んでから skipAt ms 後に「次へ」
    const watch = addTimer(() => { if (index === 1 && now - trackChangedAt >= W.skipAt) { timers.delete(watch); advanceTrack(2); } }, 5, true);
  }

  const state = () => ({
    item: adFor >= 0
      ? { type: 'ad', uri: 'spotify:ad:' + adFor, name: 'Ad', metadata: { is_advertisement: 'true' } }
      : { type: 'track', uri: tracks[index].uri, name: tracks[index].uri, metadata: {} },
    isPaused: paused, nextItems: tracks[index + 1] ? [{ uri: tracks[index + 1].uri }] : [], repeat: 0, restrictions: {},
    positionAsOfTimestamp: main._t * 1000, timestamp: now, duration: (adFor >= 0 ? W.adBefore.secs : tracks[index].secs) * 1000,
  });
  const loadedList = {
    peekNext: () => Promise.resolve(tracks[index + 1] ? { uri: tracks[index + 1].uri, fileId: 'f' + (index + 1), isAd: false, playable: true, secs: tracks[index + 1].secs } : null),
    _currentState: {},
  };
  const services = {
    PlayerAPI: {
      getState: state,
      getEvents: () => ({ addListener() {} }),
      seekTo: (ms) => { main.currentTime = ms / 1000; return Promise.resolve(); },
      // 無料プランでは広告を飛ばせない
      skipToNext: () => { if (adFor >= 0) return Promise.reject(new Error('Restricted')); advanceTrack(index + 1); return Promise.resolve(); },
      resume() { paused = false; main.paused = false; }, pause() { paused = true; main.paused = true; }, skipToPrevious() {},
    },
    PlayerSDK: { harmony: { _streamer: { _playerPromise: Promise.resolve({ _player: main }), setMediaPlaybackQuality() {}, _listPlayer: { _loadedList: loadedList, _getTrackPlayer: () => Promise.resolve(mainTp), _preloadUpcomingTrack() {} } } } },
    AdManagers: { audio: { enabled: true, disable() {}, enable() {}, inStreamApi: { disable() {}, enable() {} } } },
    AuthorizationAPI: { getState: () => ({}) },
    LibraryAPI: { contains: () => Promise.resolve([false]) },
  };
  const keys = new Map(Object.keys(services).map((k) => [Symbol(k), true]));
  const registry = { _map: keys, resolve: (key) => services[key.description] };
  const host = { '__reactContainer$test': { memoizedProps: { value: { getRegistry: () => registry } } }, querySelectorAll: () => [] };
  const window = {
    navigator: { language: 'ja-JP' },
    document: { getElementById: (id) => (id === 'main' ? host : null), createElement: () => ({}), head: { appendChild() {} }, querySelector: () => null },
    HTMLMediaElement: FakeMedia,
    performance: { now: () => now },
    Date: class extends Date { constructor(...a) { super(...(a.length ? a : [now])); } static now() { return now; } },
    setTimeout: (fn, ms) => addTimer(fn, ms, false),
    setInterval: (fn, ms) => addTimer(fn, ms, true),
    clearTimeout: (id) => timers.delete(id),
    clearInterval: (id) => timers.delete(id),
    console,
    __native: {
      send: (m) => { if (m.type === 'debug') logs.push({ t: now, event: m.event, detail: m.detail }); },
      getSetting: (key, def) => (key === 'adblock' && W.adblock === false ? 'false' : def),
    },
  };
  window.window = window;
  vm.createContext(window);
  vm.runInContext(BRIDGE, window, { filename: 'bridge.js' });
  window.__mb.call(1, 'debug', { on: true });
  window.__mb.call(2, 'gapless', { on: true });
  window.__mb.call(3, 'crossfade', { sec: W.xf });

  const flush = () => new Promise((r) => setImmediate(r));
  const samples = [];
  const step = async () => {
    now += TICK;
    for (const [id, t] of [...timers].sort((a, b) => a[1].at - b[1].at)) {
      if (!timers.has(id) || t.at > now) continue;
      if (t.repeat) t.at += t.ms; else timers.delete(id);
      try { t.fn(); } catch (e) { throw new Error('bridge.js threw: ' + e.stack); }
      await flush();
    }
    await flush();
    for (const el of elements) el.advance();
    // Web Player は曲の終わりで次の曲へ進む
    if (main.readyState >= 3 && !main.paused && main._t >= main.duration && adFor >= 0) advanceTrack(adFor);
    else if (main.readyState >= 3 && !main.paused && main._t >= main.duration && tracks[index + 1]) advanceTrack(index + 1);
    const heard = [...elements].map((el) => ({ el, level: el.level() })).filter((x) => x.level > 0);
    const top = heard.sort((a, b) => b.level - a.level)[0];
    samples.push({
      t: now, paused, ad: adFor >= 0, total: heard.reduce((s, x) => s + x.level, 0),
      track: top && top.el.track, pos: top && top.el._t, levels: new Map(heard.map((x) => [x.el, x.level])),
    });
  };
  return {
    logs, samples, drm, mainEme, p2s, main,
    get index() { return index; },
    get onAd() { return adFor >= 0; },
    // アプリの画面のボタンと同じ呼び出し（pause・resume・seek など）
    call(method, args) { window.__mb.call(0, method, args); },
    async wait(secs) { for (let i = 0; i < (secs * 1000) / TICK; i++) await step(); },
    async until(cond, maxSecs) { for (let i = 0; i < (maxSecs * 1000) / TICK; i++) { await step(); if (cond()) return; } throw new Error('時間内に終わらなかった'); },
  };
}

// ---- 聞こえる音の確かめ方 ----
function assert(cond, message) { if (!cond) throw new Error(message); }
const events = (s, name) => s.logs.filter((l) => l.event === name);
const at = (s, t) => ((t - s.samples[0].t) / 1000).toFixed(2) + ' 秒';
// 再生中に音が消えない（クロスフェードの重なりでも、2 曲の大きさの合計は十分ある）
function assertNoGap(s) {
  let silentFrom = null;
  for (const x of s.samples) {
    if (x.paused || x.t - s.samples[0].t < 3000) { silentFrom = null; continue; }
    if (x.total >= VOLUME * 0.25) { silentFrom = null; continue; }
    if (silentFrom === null) silentFrom = x.t;
    assert(x.t - silentFrom < 60, `${at(s, silentFrom)} から音が途切れた（${x.t - silentFrom}ms 以上）`);
  }
}
// 聞こえている曲の位置が巻き戻らない（聞いた所がもう一度鳴らない）・飛ばない
function assertContinuous(s, maxJump = 0.04) {
  const heardMax = new Map();
  let prev = null;
  for (const x of s.samples) {
    if (!x.track) { prev = null; continue; }
    const max = heardMax.get(x.track);
    assert(max == null || x.pos >= max - 0.08, `${at(s, x.t)} に ${x.track} が ${max && max.toFixed(2)} 秒から ${x.pos.toFixed(2)} 秒へ巻き戻った`);
    if (prev && prev.track === x.track) {
      const d = x.pos - prev.pos - TICK / 1000;
      assert(d <= maxJump, `${at(s, x.t)} に ${x.track} が ${(d * 1000).toFixed(0)}ms 飛んだ（${prev.pos.toFixed(2)} → ${x.pos.toFixed(2)}）`);
    }
    heardMax.set(x.track, Math.max(max || 0, x.pos));
    prev = x;
  }
}
// ぶつっと鳴らない：同じ曲を鳴らす 2 台のあいだで、音を一瞬で移さない（少しずれた所へ飛んで、ぶつっと鳴る）。
// 曲の変わり目（ギャップレスで前の曲が終わった瞬間に次の曲が鳴る）は別の曲なので数えない。聞こえる大きさの合計も一瞬で大きく下げない
function assertNoClick(s) {
  const STEP = VOLUME * 0.35;
  let prev = null;
  for (const x of s.samples) {
    // 再生を始めた直後（クロスフェードの見張りが音量を下げるまでの一瞬）は数えない
    if (!prev || x.paused || x.t - s.samples[0].t < 3000) { prev = x; continue; }
    const els = new Set([...prev.levels.keys(), ...x.levels.keys()]);
    const change = (el) => (x.levels.get(el) || 0) - (prev.levels.get(el) || 0);
    for (const a of els) {
      if (change(a) > -STEP) continue;
      for (const b of els) {
        assert(!(b !== a && b.track === a.track && change(b) >= STEP), `${at(s, x.t)} に ${a.track} の音を ${a.name} から ${b.name} へ一瞬で移した（${a.name}: ${(prev.levels.get(a) || 0).toFixed(2)} → ${(x.levels.get(a) || 0).toFixed(2)}）`);
      }
    }
    // 大きくなる方と、重なりが終わって元の大きさに戻るのは数えない（ギャップレスでは、途切れないよう前の曲の最後の 60ms に次の曲を重ねる）
    assert(prev.total - x.total <= STEP || x.total >= VOLUME * 0.6, `${at(s, x.t)} に聞こえる大きさが ${prev.total.toFixed(2)} → ${x.total.toFixed(2)} に一瞬で下がった`);
    prev = x;
  }
}
function assertJoined(s, n) {
  const ok = events(s, 'seamless.handoff').filter((l) => /^off=/.test(l.detail));
  assert(ok.length >= n, `1 台目へ戻せたのが ${ok.length} 回（${n} 回のはず）：${events(s, 'seamless.handoff').map((l) => l.detail).join(' / ')}`);
}

let failed = 0;
const results = [];
async function test(name, fn) {
  try { await fn(); results.push('  ✓ ' + name); } catch (e) { failed++; results.push('  ✗ ' + name + '\n      ' + e.message); }
}

await test('クロスフェード：途切れず・巻き戻らず・ぶつっと鳴らずに 2 回つなぐ', async () => {
  const s = run();
  await s.until(() => s.index === 2 && s.main._t > 20, 120);
  assertJoined(s, 2); assertNoGap(s); assertContinuous(s); assertNoClick(s);
});

await test('ギャップレス：途切れず・巻き戻らず・ぶつっと鳴らずにつなぐ', async () => {
  const s = run({ xf: 0 });
  await s.until(() => s.index === 1 && s.main._t > 20, 120);
  assertJoined(s, 1); assertNoGap(s); assertContinuous(s); assertNoClick(s);
});

await test('鍵：2 台目の鍵は要求から 1 秒以上空けて登録する（続けて走ると鳴っている曲が途切れる）', async () => {
  const s = run();
  await s.until(() => s.index === 1 && s.main._t > 20, 120);
  const ops = s.drm.ops;
  const gen = ops.find((o) => o.label === 'p2.generate'), upd = ops.find((o) => o.label === 'p2.update');
  assert(gen && upd && upd.at - gen.at >= 1000, `2 台目の鍵の要求から登録まで ${upd && gen ? upd.at - gen.at : '?'}ms`);
  assertNoGap(s);
});

await test('鍵：2 台目の鍵の仕組みは、止まっているあいだに作る', async () => {
  const s = run({ startPausedMs: 2000 });
  await s.until(() => s.index === 1 && s.main._t > 20, 120);
  const inits = s.p2s.length && s.drm.ops.filter((o) => o.label === 'init');
  assert(inits.length === 1, `鍵の仕組みを ${inits.length} 回作った（1 回のはず）`);
  assert(inits[0].at - s.samples[0].t < 2000, `鍵の仕組みを作ったのが再生中（${at(s, inits[0].at)}）`);
  assertNoGap(s);
});

await test('1 台目に戻した直後に遅れて、聞いた所が繰り返されない（音を出し始めた要素は遅れる）', async () => {
  const s = run({ drm: false });
  await s.until(() => s.index === 2 && s.main._t > 20, 120);
  assertJoined(s, 2); assertNoGap(s); assertContinuous(s); assertNoClick(s);
});

await test('位置合わせ中に 2 台目が止まっても、次の曲が頭から鳴り直さない', async () => {
  const s = run({ mainLoadMs: 4000, p2StallAt: { secs: 11, ms: 2000 } });
  await s.until(() => s.index === 1 && s.main._t > 20, 120);
  assertContinuous(s, 2.5);
});

// シークにかかった時間は、実機（Pixel 9a）で測った値
await test('シークにかかる時間がばらついても、20ms 以内にそろえてから音を移す', async () => {
  const s = run({ seekMs: [335, 150, 200, 130, 210, 110, 160, 205, 105, 190] });
  await s.until(() => s.index === 1 && s.main._t > 25, 120);
  assertJoined(s, 1); assertNoGap(s); assertNoClick(s); assertContinuous(s);
});

await test('シークの時間が大きくばらついて位置が合わなくても、ぶつっと切り替えない', async () => {
  const s = run({ seekMs: [335, 100, 260, 100, 260, 100, 260, 100, 260, 100] });
  await s.until(() => s.index === 1 && s.main._t > 25, 120);
  assertNoGap(s); assertNoClick(s); assertContinuous(s, 0.16);
});

await test('つなぎ目の途中で「次へ」を押したら、その曲は頭から鳴る', async () => {
  const s = run({ skipAt: 1500 });
  await s.until(() => s.index === 2 && s.main._t > 3, 120);
  const c = s.samples.filter((x) => x.track === 'spotify:track:C');
  assert(c.length && c[0].pos < 0.5, `「次へ」で進んだ曲が ${c.length ? c[0].pos.toFixed(2) : '?'} 秒から鳴った`);
});

// 2 台目（p2）だけで鳴っている曲の大きさと、1 台目（main）に戻したあとの大きさ
const levelOf = (s, name, track) => s.samples.filter((x) => x.track === track).flatMap((x) => [...x.levels].filter(([el]) => el.name === name).map(([, v]) => v));
const p2Heard = (s) => s.samples.some((x) => [...x.levels.keys()].some((el) => el.name === 'p2'));

await test('つなぎ目のあと、1 台目に戻しても音の大きさが変わらない（2 台目だけ正規化しない）', async () => {
  const s = run();
  await s.until(() => s.index === 1 && s.main._t > 25, 120);
  assertJoined(s, 1);
  const p2 = Math.max(...levelOf(s, 'p2', 'spotify:track:B')), main = levelOf(s, 'main', 'spotify:track:B').at(-1);
  const db = 20 * Math.log10(main / p2);
  assert(Math.abs(db) < 1, `2 台目 ${p2.toFixed(2)} → 1 台目 ${main.toFixed(2)}（${db.toFixed(1)} dB 変わった）`);
});

await test('次の曲か今の曲が 20 秒より短いときは、2 台目でつながない（重ねている途中に曲が終わる）', async () => {
  const s = run({ secsList: [40, 12, 40] });
  await s.until(() => s.index === 2 && s.main._t > 3, 120);
  assert(!events(s, 'seamless.start').length && !p2Heard(s), `2 台目で鳴らした：${events(s, 'seamless.start').map((l) => l.detail).join(' / ')}`);
  const b = s.samples.filter((x) => x.track === 'spotify:track:B');
  assert(b.length && b[0].pos < 0.5, `短い曲が ${b.length ? b[0].pos.toFixed(2) : '?'} 秒から鳴った`);
  assertContinuous(s);
});

await test('つなぎ目のあとに広告が入っても、次の曲は 2 台目で鳴り続け、広告のあと頭から鳴り直さない', async () => {
  const s = run({ adBefore: { index: 1, secs: 30 } });
  await s.until(() => s.index === 1 && s.main._t > 25, 120);
  assert(events(s, 'seamless.ad').length, '広告のあいだ 2 台目で待たなかった');
  assertJoined(s, 1); assertNoGap(s); assertContinuous(s); assertNoClick(s);
});

await test('広告の処理がオフなら、広告のあいだ次の曲を止めて待ち、広告のあと続きから鳴る', async () => {
  const s = run({ adBefore: { index: 1, secs: 30 }, adblock: false });
  await s.until(() => s.index === 1 && s.main._t > 25, 120);
  const during = s.samples.filter((x) => x.ad);
  assert(during.length * TICK > 25000, `広告が ${during.length * TICK / 1000} 秒で終わった（速めずに 30 秒流すはず）`);
  // 見張りは 50ms ごとなので、広告が始まってから 2 台目を止めるまでの一瞬は数えない
  const late = during.filter((x) => x.t - during[0].t > 200 && [...x.levels.keys()].some((el) => el.name === 'p2'));
  assert(!late.length, `広告のあいだも 2 台目が鳴っていた（${late.length && at(s, late[0].t)} から）`);
  assertJoined(s, 1); assertContinuous(s);
});

await test('クロスフェード中に一時停止したら、次の曲のその位置で止まり、再生し直すと続きから鳴る', async () => {
  const s = run();
  await s.until(() => s.p2s.some((el) => el.level() > 0 && el._t > 4), 120);
  const at0 = s.p2s.at(-1)._t, pausedAt = s.samples.at(-1).t;
  s.call('pause');
  await s.wait(4);
  assert(s.index === 1, `止めたとき、Web Player が次の曲へ進んでいない（${s.index + 1} 曲目）`);
  assert(Math.abs(s.main._t - at0) < 1.5, `次の曲の ${at0.toFixed(2)} 秒で止めたのに、Web Player は ${s.main._t.toFixed(2)} 秒`);
  s.call('resume');
  await s.wait(5);
  const after = s.samples.filter((x) => x.t > pausedAt && x.track);
  assert(after.length && after.every((x) => x.track === 'spotify:track:B'), '一時停止のあと前の曲が鳴った');
  assertContinuous(s, 1.5);
});

await test('クロスフェード中に続けてシークしたら、最後のシークの位置になる', async () => {
  const s = run();
  await s.until(() => s.p2s.some((el) => el.level() > 0 && el._t > 3), 120);
  s.call('seek', { ms: 30000 });
  await s.wait(0.2);
  s.call('seek', { ms: 15000 });
  await s.wait(6);
  assert(s.index === 1 && s.main._t > 15 && s.main._t < 22, `${s.index + 1} 曲目の ${s.main._t.toFixed(2)} 秒（2 曲目の 15〜22 秒のはず）`);
});

console.log(results.join('\n'));
if (failed) { console.error(`\n${failed} 件失敗`); process.exit(1); }
console.log('\nすべて通過');
