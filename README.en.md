<div align="center">

# spotfy

**An Android app that lets you use the Spotify Web Player with the feel of the official app**

[![Release](https://img.shields.io/github/v/release/ninjin0802/spotfy?label=release&color=1ed760)](https://github.com/ninjin0802/spotfy/releases/latest)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7f52ff?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[**⬇ Download the latest APK**](https://github.com/ninjin0802/spotfy/releases/latest)

[日本語](README.md) | **English**

</div>

---

> [!IMPORTANT]
> This is an **unofficial** personal project. It is not affiliated with, endorsed by, or sponsored by Spotify AB.
> "Spotify" and its logo are trademarks of Spotify AB. Use at your own risk.

## What is this?

The UI is drawn **natively on Android (Jetpack Compose)**, while data and playback are handled by
[open.spotify.com](https://open.spotify.com) (the Spotify Web Player) running in the background.
The app calls the same endpoints the Web Player itself uses (GraphQL, etc.), so it is lighter than v1 — which restyled the web page —
and looks and behaves much closer to the official app.

- 🔐 **You sign in on Spotify's own login page.** The app never handles your password
- 🌐 **Talks directly to Spotify.** No relay server
- 💾 **Nothing is saved to the device** (there is no download feature)
- 🎧 Playback (including DRM) is done by Spotify's own Web Player

## Features

| | |
|---|---|
| 🏠 **Home** | All / Music / Podcasts (with Following), shortcuts, shelves, new-release cards |
| 🔎 **Search** | Genre tiles, recent searches, live results with type filters |
| 📚 **Library** | Filters, sorting, list / grid, pins, search in library, create playlists and folders |
| 🎵 **Player** | Mini and full-screen players tinted by the artwork, lyrics, About the artist, Next up |
| 📋 **Queue** | Drag to reorder, remove, shuffle / repeat, sleep timer |
| ⋮ **Menus** | Bottom sheets for tracks, albums, playlists and artists (swipe down to close) |
| 📡 **Cast** | Spotify Connect to PCs, speakers and TVs; switch between phone speaker and Bluetooth earphones |
| 👤 **Profile** | Change your name and photo, public playlists, recently played artists |
| 🎨 **Theme** | 5 accent colors, AMOLED black |
| 🎚 **Audio quality** | Separate settings for Wi-Fi and mobile data |

## Install

1. Download `spotfy-*.apk` from [Releases](https://github.com/ninjin0802/spotfy/releases/latest)
2. Open it on your device to install (the first time you need to allow "unknown apps")
3. Open the app and sign in with your Spotify account

> [!NOTE]
> Works with free accounts. Premium / official-app-only features such as lossless audio, Jam and downloads are not available.

## How it works

```
┌──────────────────────── Android app ───────────────────────────┐
│  MainActivity (Kotlin)                                          │
│   ├─ Compose UI (NativeApp / Screens / Sheets …)                │
│   │    └─ Bridge.kt … asks the hidden Web Player for data/playback│
│   ├─ WebView … open.spotify.com (never shown)                   │
│   │    ├─ polyfill.js … re-sends Connect requests from the device│
│   │    └─ bridge.js   … calls GraphQL / PlayerAPI, returns results│
│   ├─ MediaControls / PlaybackService … notification, background │
│   └─ WebMessageListener … accepts messages only from open.spotify.com │
└────────────────────────────────────────────────────────────────┘
```

- Android WebView adds an `X-Requested-With` header to every request, which stops Spotify Connect playback
  commands from being delivered. The app re-sends only those playback requests itself
- Search and history call the Web Player's own GraphQL loader and render the results natively
  (the same idea as [Myspoti](https://github.com/kota-zo/myspoti); no code is copied)

## Build

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # release build (needs a signing key)
```

The release signing key is read from `~/.config/spotfy/keystore.properties` (outside the repository).

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

| Item | Version |
|---|---|
| minSdk / targetSdk | 26 / 36 |
| Android Gradle Plugin | 8.13 |
| Kotlin | 2.2 |
| Dependencies | `androidx.webkit`, Jetpack Compose, Coil, Palette |

## License

[MIT](LICENSE)

## Disclaimer

This software is provided as is, without warranty of any kind. It may stop working without notice if Spotify changes its service.
Please use it in accordance with Spotify's Terms of Use.
