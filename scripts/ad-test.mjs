// 広告の処理（bridge.js）を、偽物の Web Player で動かして確かめる。
// 本物の広告は CI では出せないので、PlayerAPI と再生要素をまねて、広告が来たときの動きを再現する。
// Spotify 側の変更は分からない（それは bridge.health とデバッグログで気づく）。ここで防ぐのは、こちらの書き換えで壊すこと。
// 実行: node scripts/ad-test.mjs
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const BRIDGE = readFileSync(new URL('../app/src/main/assets/bridge.js', import.meta.url), 'utf8');
const TICK = 100; // ms
const VOLUME = 0.8;

class FakeMedia {
  constructor() { this.volume = VOLUME; this.muted = false; this.playbackRate = 1; this.currentTime = 0; this.duration = NaN; this.paused = false; }
}

// items: [{ ad: true, secs: 30 }, ..., { ad: false, secs: 200 }]
// opts.resetRate: Web Player が速度を 1 倍に戻してくる間隔（tick 数）。0 なら戻さない
// opts.ignoreRate: 速度を変えても効かない（普通の速さで進む）
// opts.stuck: 広告の音声が終わっても、広告のまま先へ進まない
// opts.adblock: 設定の「広告の処理」
function run(items, opts = {}) {
  let now = 1_700_000_000_000;
  const timers = new Map();
  let nextId = 1;
  const addTimer = (fn, ms, repeat) => { const id = nextId++; timers.set(id, { fn, at: now + Math.max(ms || 0, 1), ms: Math.max(ms || 0, 1), repeat }); return id; };
  const logs = [];
  const el = new FakeMedia();
  let index = 0, itemTicks = 0;
  const item = () => items[index];
  const loadItem = () => {
    const it = item();
    el.currentTime = 0;
    el.duration = it ? it.secs : NaN;
    // 新しい項目を読み込むと、Web Player は速度を 1 倍に戻す
    el.playbackRate = 1;
    itemTicks = 0;
  };
  loadItem();

  const state = () => {
    const it = item();
    if (!it) return null;
    return {
      item: it.ad
        ? { type: 'ad', uri: 'spotify:ad:' + index, name: 'Ad ' + index, metadata: { is_advertisement: 'true' } }
        : { type: 'track', uri: 'spotify:track:' + index, name: 'Track ' + index, metadata: {} },
      isPaused: false, positionAsOfTimestamp: 0, timestamp: now, duration: it.secs * 1000, restrictions: {},
    };
  };
  const manager = () => ({ enabled: true, disable() { this.enabled = false; }, enable() { this.enabled = true; } });
  const audioManager = Object.assign(manager(), { inStreamApi: manager() });
  const services = {
    PlayerAPI: {
      getState: state,
      getEvents: () => ({ addListener() {} }),
      skipToNext: () => Promise.reject(new Error('Restricted')),
      resume() {}, pause() {}, seekTo() {}, skipToPrevious() {},
    },
    PlayerSDK: { harmony: { _streamer: { _playerPromise: Promise.resolve({ _player: el }), setMediaPlaybackQuality() {}, _listPlayer: { _preloadUpcomingTrack() {} } } } },
    AdManagers: { audio: audioManager },
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
      getSetting: (key, def) => (key === 'adblock' && opts.adblock === false ? 'false' : def),
    },
  };
  window.window = window;
  vm.createContext(window);
  vm.runInContext(BRIDGE, window, { filename: 'bridge.js' });

  const flush = () => new Promise((r) => setImmediate(r));
  const samples = [];
  const start = now;
  const step = async () => {
    now += TICK;
    for (const [id, t] of [...timers].sort((a, b) => a[1].at - b[1].at)) {
      if (!timers.has(id) || t.at > now) continue;
      if (t.repeat) t.at += t.ms; else timers.delete(id);
      try { t.fn(); } catch (e) { throw new Error('bridge.js threw: ' + e.stack); }
      await flush();
    }
    await flush();
    // 再生を進める
    const it = item();
    if (!it) return;
    itemTicks++;
    if (opts.resetRate && itemTicks % opts.resetRate === 0) el.playbackRate = 1;
    const rate = opts.ignoreRate ? 1 : el.playbackRate;
    el.currentTime = Math.min(el.duration, el.currentTime + (TICK / 1000) * rate);
    if (el.currentTime >= el.duration && !(it.ad && opts.stuck)) { index++; loadItem(); }
    samples.push({ t: now - start, ad: !!(item() && item().ad), volume: el.volume, muted: el.muted, rate: el.playbackRate, index });
  };

  return {
    logs, el, samples,
    async until(cond, maxSecs) {
      for (let i = 0; i < (maxSecs * 1000) / TICK; i++) { await step(); if (cond()) return (now - start) / 1000; }
      return Infinity;
    },
    async wait(secs) { for (let i = 0; i < (secs * 1000) / TICK; i++) await step(); },
    get index() { return index; },
    get onTrack() { return item() && !item().ad; },
  };
}

// ---- 確かめる ----
let failed = 0;
const results = [];
async function test(name, fn) {
  try { await fn(); results.push('  ✓ ' + name); } catch (e) { failed++; results.push('  ✗ ' + name + '\n      ' + e.message); }
}
function assert(cond, message) { if (!cond) throw new Error(message); }
const events = (s, name) => s.logs.filter((l) => l.event === name);
// 広告が始まってから最初のミュートまでの猶予（見張りは 0.3 秒ごと。再生要素は非同期で取る）
const GRACE_MS = 700;
function assertMutedDuringAds(s) {
  let adStart = null;
  for (const x of s.samples) {
    if (!x.ad) { adStart = null; continue; }
    if (adStart === null) adStart = x.t;
    if (x.t - adStart < GRACE_MS) continue;
    assert(x.volume <= 0.001 || x.muted, `広告中なのに音が出ている（${(x.t / 1000).toFixed(1)} 秒、${x.index} 本目の項目、volume=${x.volume}）`);
  }
}
const ads = (n, secs = 30) => Array.from({ length: n }, () => ({ ad: true, secs }));
const TRACK = { ad: false, secs: 200 };

await test('広告 1 本：ミュートして 16 倍速、終わったら元に戻る', async () => {
  const s = run([...ads(1), TRACK]);
  const secs = await s.until(() => s.onTrack, 60);
  assert(secs < 4, `30 秒の広告に ${secs} 秒かかった（16 倍速なら 2 秒ほど）`);
  assertMutedDuringAds(s);
  await s.wait(1);
  assert(s.el.volume === VOLUME && !s.el.muted, `広告のあとの音量が戻っていない（volume=${s.el.volume} muted=${s.el.muted}）`);
  assert(s.el.playbackRate === 1, `広告のあとの速度が戻っていない（rate=${s.el.playbackRate}）`);
  assert(events(s, 'ad.manager.disabled').length > 0, 'ad.manager.disabled が出ていない');
});

await test('広告 3 本続く：毎回 16 倍速になり、ずっとミュート', async () => {
  const s = run([...ads(3), TRACK]);
  const secs = await s.until(() => s.onTrack, 120);
  assert(secs < 10, `30 秒の広告 3 本に ${secs} 秒かかった（16 倍速なら 6 秒ほど）`);
  assertMutedDuringAds(s);
  await s.wait(1);
  assert(s.el.volume === VOLUME, `広告のあとの音量が戻っていない（volume=${s.el.volume}）`);
});

await test('Web Player が速度を何度も戻してきても、付け直す', async () => {
  const s = run([...ads(2), TRACK], { resetRate: 5 });
  const secs = await s.until(() => s.onTrack, 120);
  assert(secs < 15, `30 秒の広告 2 本に ${secs} 秒かかった`);
  assertMutedDuringAds(s);
  const done = events(s, 'ad.item.done');
  assert(done.some((l) => !/resets=0\b/.test(l.detail)), `速度を戻された回数が記録されていない（${done.map((l) => l.detail).join(' / ')}）`);
});

await test('速度が効かず 30 秒ずつ流れても、3 本目の途中でミュートが外れない', async () => {
  const s = run([...ads(3), TRACK], { ignoreRate: true });
  const secs = await s.until(() => s.onTrack, 200);
  assert(secs >= 89 && secs < 95, `広告 3 本に ${secs} 秒（速度が効かないので約 90 秒のはず）`);
  assertMutedDuringAds(s);
  assert(events(s, 'ad.mute.failsafe').length === 0, '広告が続いているのに安全装置が働いた');
});

await test('広告のまま止まったら、75 秒で安全装置が音量を戻す', async () => {
  const s = run([...ads(1), TRACK], { stuck: true });
  await s.wait(80);
  const fs = events(s, 'ad.mute.failsafe');
  assert(fs.length === 1, `ad.mute.failsafe が ${fs.length} 回（1 回のはず）`);
  assert(s.el.volume === VOLUME, `安全装置のあとの音量が戻っていない（volume=${s.el.volume}）`);
});

await test('ログ：広告ごとに本数・2 秒後の位置・かかった秒数、最後に合計が残る', async () => {
  const s = run([...ads(3), TRACK]);
  await s.until(() => s.onTrack, 120);
  await s.wait(3);
  const detected = events(s, 'ad.detected').map((l) => l.detail);
  assert(detected.length === 3 && detected.every((d, i) => d.endsWith('n=' + (i + 1))), `ad.detected: ${detected.join(' / ')}`);
  const done = events(s, 'ad.item.done');
  assert(done.length === 3, `ad.item.done が ${done.length} 回（3 回のはず）`);
  const ended = events(s, 'ad.state.ended');
  assert(ended.length === 1 && /^ads=3 secs=\d+$/.test(ended[0].detail), `ad.state.ended: ${ended.map((l) => l.detail).join(' / ')}`);
  assert(events(s, 'ad.speed.applied').length >= 1, 'ad.speed.applied が出ていない');
});

await test('ログ：速度が効かないと ad.media の位置で分かる', async () => {
  const s = run([...ads(1, 60), TRACK], { ignoreRate: true });
  await s.wait(3);
  const media = events(s, 'ad.media');
  assert(media.length === 1, `ad.media が ${media.length} 回`);
  const pos = Number(/pos=(\d+)/.exec(media[0].detail)[1]);
  assert(pos <= 3, `速度が効いていないのに位置が ${pos} 秒（${media[0].detail}）`);
});

await test('設定で広告の処理をオフにすると、何もしない', async () => {
  const s = run([...ads(1), TRACK], { adblock: false });
  const secs = await s.until(() => s.onTrack, 60);
  assert(secs >= 29, `オフなのに広告が ${secs} 秒で終わった`);
  assert(s.samples.every((x) => x.volume === VOLUME), 'オフなのに音量が変わった');
});

await test('普通の曲だけなら、音量も速度も触らない', async () => {
  const s = run([TRACK]);
  await s.wait(10);
  assert(s.samples.every((x) => x.volume === VOLUME && x.rate === 1), '曲の再生中に音量か速度が変わった');
  assert(events(s, 'ad.detected').length === 0, '曲なのに ad.detected が出た');
});

console.log(results.join('\n'));
if (failed) { console.error(`\n${failed} 件失敗`); process.exit(1); }
console.log('\nすべて通過');
