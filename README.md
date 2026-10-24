<div align="center">

# spotfy

**Spotify Web Player を、公式アプリのような操作感で使える Android アプリ**

[![Release](https://img.shields.io/github/v/release/ninjin0802/spotfy?label=release&color=1ed760)](https://github.com/ninjin0802/spotfy/releases/latest)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7f52ff?logo=kotlin&logoColor=white)

[**⬇ 最新の APK をダウンロード**](https://github.com/ninjin0802/spotfy/releases/latest)

</div>

---

> [!IMPORTANT]
> これは **非公式** の個人プロジェクトです。Spotify AB とは一切関係がなく、承認や支援も受けていません。
> 「Spotify」の名称とロゴは Spotify AB の商標です。利用は自己責任でお願いします。

## これは何？

Android の WebView で [open.spotify.com](https://open.spotify.com)（Spotify Web Player）を開き、
画面を **スマホ向けに作り替えて**、公式アプリに近い見た目と操作感で使えるようにしたアプリです。

再生は Spotify 自身の Web Player がそのまま行います（Widevine DRM も端末のものを利用）。
アプリはその上に、ボトムタブ・ミニプレーヤー・全画面プレーヤー・検索ページなどを描いています。

- 🔐 **ログインは Spotify 公式のページ**で行います。アプリがパスワードを扱うことはありません
- 🌐 **通信は Spotify と直接**。中継サーバーはありません
- 💾 **曲を端末に保存しません**（ダウンロード機能はありません）

## 主な機能

| | |
|---|---|
| 🏠 **ホーム / 検索 / ライブラリ** | 公式アプリと同じ 3 タブ構成。半透明のタブバーとミニプレーヤー |
| 🔎 **検索** | 入力に合わせて結果をその場で表示。最近の検索、⊕ で追加、× で履歴から削除 |
| 🎵 **全画面プレーヤー** | ジャケット色の背景、シークバー、シャッフル・リピート、キュー、共有、歌詞カード |
| ⋮ **ボトムシートのメニュー** | 曲・アルバム・プレイリストの「シェア / お気に入り / プレイリストに追加 / 次に再生 …」 |
| 📡 **デバイス切り替え** | Spotify Connect で PC やスピーカーへ再生を移す |
| 🔔 **メディアコントロール** | 通知・ロック画面に曲情報と操作ボタン。バックグラウンド再生 |
| 🎚 **再生設定** | 音質（自動〜最高）、音量の均一化、ギャップレス再生、クロスフェード（1〜12 秒） |
| 🎨 **見た目** | AMOLED ブラック、アクセントカラー 5 色、ステータスバーまで描く全画面表示 |
| 👤 **プロフィール** | 表示・編集（写真の変更）、公開範囲の設定 |

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
│   ├─ WebView … open.spotify.com（デスクトップ Chrome として表示）│
│   │    ├─ polyfill.js  … ページ読み込み前に注入                  │
│   │    │    ・Media Session の補完                               │
│   │    │    ・Connect 操作を端末から送り直す（X-Requested-With 回避）│
│   │    └─ mobile.js    … 画面をスマホ向けに組み替え、自前 UI を描画 │
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

| 項目 | バージョン |
|---|---|
| minSdk / targetSdk | 26 / 36 |
| Android Gradle Plugin | 8.13 |
| Kotlin | 2.2 |
| 依存ライブラリ | `androidx.webkit` のみ |

## 免責事項

本ソフトウェアは現状のまま提供され、動作の保証はありません。Spotify の仕様変更により予告なく動かなくなることがあります。
Spotify の利用規約に従ってご利用ください。
