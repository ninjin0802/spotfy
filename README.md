<div align="center">

# spotfy

**Spotify Web Player を、公式アプリのような操作感で使える Android アプリ**

[![Release](https://img.shields.io/github/v/release/ninjin0802/spotfy?label=release&color=1ed760)](https://github.com/ninjin0802/spotfy/releases/latest)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7f52ff?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[**⬇ 最新の APK をダウンロード**](https://github.com/ninjin0802/spotfy/releases/latest)

**日本語** | [English](README.en.md)

</div>

---

> [!IMPORTANT]
> これは **非公式** の個人プロジェクトです。Spotify AB とは一切関係がなく、承認や支援も受けていません。
> 「Spotify」の名称とロゴは Spotify AB の商標です。利用は自己責任でお願いします。

## これは何？

画面を **Android ネイティブ（Jetpack Compose）で描き**、データと再生は裏で動かす
[open.spotify.com](https://open.spotify.com)（Spotify Web Player）に任せるアプリです。
Web Player 自身と同じ通信（GraphQL など）を直接呼ぶので、Web のページを組み替えていた v1 より軽く、公式アプリに近い見た目と動きになっています。

- 🔐 **ログインは Spotify 公式のページ**で行います。アプリがパスワードを扱うことはありません
- 🌐 **通信は Spotify と直接**。中継サーバーはありません
- 💾 **曲を端末に保存しません**（ダウンロード機能はありません）
- 🎧 再生（DRM を含む）は Spotify 自身の Web Player が行います

## 主な機能

| | |
|---|---|
| 🏠 **ホーム** | すべて／音楽／ポッドキャスト（フォロー中つき）、ショートカット、棚、最新リリースのカード |
| 🔎 **検索** | ジャンルのタイル、最近の検索、入力に合わせた結果と種類の絞り込み |
| 📚 **ライブラリ** | 絞り込み・並べ替え・リスト／グリッド、ピン、ライブラリ内検索、プレイリスト・フォルダの作成 |
| 🎵 **プレーヤー** | ジャケット色のミニ／全画面プレーヤー、歌詞、アーティストについて、次に再生 |
| 🚫 **広告の抑制** | 設定から切り替え可能。Web Player の広告機能を停止し、広告として識別された再生をミュートしてスキップ |
| 📋 **キュー** | ドラッグで並べ替え、外す、シャッフル・リピート・おやすみタイマー |
| ⋮ **メニュー** | 曲・アルバム・プレイリスト・アーティストのボトムシート（下にスワイプで閉じる） |
| 📡 **キャスト** | Spotify Connect で PC・スピーカー・テレビへ、スピーカー／Bluetooth イヤホンの切り替え |
| 👤 **プロフィール** | 名前と写真の変更、公開プレイリスト、最近聴いたアーティスト |
| 🎨 **テーマ** | アクセントカラー 5 色、AMOLED ブラック |
| 🎚 **音質** | Wi-Fi／モバイルデータ通信で別々に設定 |

## インストール

1. [Releases](https://github.com/ninjin0802/spotfy/releases/latest) から `spotfy-*.apk` をダウンロード
2. 端末で開いてインストール（初回は「提供元不明のアプリ」の許可が必要です）
3. アプリを開き、Spotify アカウントでログイン

> [!NOTE]
> 無料アカウントでも使えます。ロスレス音質・Jam・ダウンロードなど Premium／公式アプリ専用の機能は使えません。

## 仕組み

```
┌──────────────────────── Android アプリ ────────────────────────┐
│  MainActivity（Kotlin）                                         │
│   ├─ Compose の画面（NativeApp / Screens / Sheets …）           │
│   │    └─ Bridge.kt … 裏の Web Player にデータ取得・再生を頼む    │
│   ├─ WebView … open.spotify.com（画面には出さない）             │
│   │    ├─ polyfill.js … Connect 操作を端末から送り直す など      │
│   │    └─ bridge.js   … GraphQL・PlayerAPI を呼んで結果を返す     │
│   ├─ MediaControls / PlaybackService … 通知・バックグラウンド再生  │
│   └─ WebMessageListener … open.spotify.com からのみメッセージ受信  │
└────────────────────────────────────────────────────────────────┘
```

- Android WebView はすべての通信に `X-Requested-With` ヘッダーを付けるため、Spotify Connect の再生コマンドが
  届かなくなります。再生操作の通信だけをアプリから送り直して解決しています
- 検索・履歴は Web Player 自身の GraphQL ローダーを呼び、結果をアプリ側で描画しています
  （[Myspoti](https://github.com/kota-zo/myspoti) と同じ考え方。コードは流用していません）

## ビルド

```bash
./gradlew assembleDebug      # デバッグ版
./gradlew assembleRelease    # リリース版（署名鍵が必要）
```

リリース版の署名鍵は `~/.config/spotfy/keystore.properties`（リポジトリ外）から読み込みます。

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## リリース

版の番号はリポジトリ直下の `VERSION`（`メジャー.マイナー.パッチ`）だけで管理します。
`versionName` はこの値、`versionCode` はここから計算（`2.0.9` → `20009`）するので、手で書き換える場所はありません。

1. `VERSION` を上げる（不具合の修正はパッチ、機能追加はマイナー）
2. `CHANGELOG.md` の先頭に `## <版>` の節を書く
3. コミットして `scripts/release.sh` を実行する
   - 署名付きでビルドし、`v<版>` のタグを付けて main・作業ブランチ・タグを push、GitHub Releases に APK とその節の内容を上げます
   - 同じ版のタグがある・コミットしていない変更がある・main から分かれている・CHANGELOG に節がない、のどれかなら止まります
   - タグを付ける前に `scripts/smoke-test.sh` をエミュレーターで実行し、失敗したら止まります（急ぐときだけ `SKIP_SMOKE=1`）

### スモークテスト

`scripts/smoke-test.sh` はエミュレーターで起動まわりを自動で確かめます。

- 初回起動でログイン画面が出る
- 通信できないときに「Spotify に接続できません」が出て、つながると自動でログイン画面に戻る
- WebView が使えない端末（Android 8 の素のイメージ）で、落ちずに更新の案内が出る

AVD は `SMOKE_AVD`（既定 `keyboard-test`、Android 15）と `SMOKE_AVD_OLD`（既定 `keyboard-test-api26`）で変えられます。

### Spotify の更新で壊れたとき

Web Player の中の部品を使っているので、Spotify の更新で一部の機能が動かなくなることがあります。

- 起動して少しすると、使っている部品が揃っているかを確かめます。足りなければ設定のいちばん上に「⚠ 一部の機能が使えません」と出ます
- 部品の確認結果（`bridge.health`）、呼び出しの失敗（`bridge.error`・`bridge.timeout`）、広告の処理、ページの読み込みエラーはデバッグログに残ります
- 設定の「デバッグログを共有」で送ってもらうか、adb で `adb shell am start -n com.ninjin.spotifyweb/.MainActivity --ez dumpDebugLog true` のあと `adb logcat -s spotfy-debug` で取り出せます

| 項目 | バージョン |
|---|---|
| minSdk / targetSdk | 26 / 36 |
| Android Gradle Plugin | 8.13 |
| Kotlin | 2.2 |
| 依存ライブラリ | `androidx.webkit`、Jetpack Compose、Coil、Palette |

## ライセンス

[MIT](LICENSE)

## 免責事項

本ソフトウェアは現状のまま提供され、動作の保証はありません。Spotify の仕様変更により予告なく動かなくなることがあります。
広告のブロックは Spotify の利用規約に反する可能性があり、アカウントに影響する場合があります。Spotify の利用規約に従ってご利用ください。
