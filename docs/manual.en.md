# spotfy user guide

[日本語](manual.md) ・ **English**

How to use spotfy, and what each setting does.
For installation, see the [README](../README.en.md#-install).

- [Finding your way around](#finding-your-way-around)
- [Settings](#settings)
  - [Account](#account)
  - [Content and display](#content-and-display)
  - [Playback](#playback)
  - [Media quality](#media-quality)
  - [Language](#language)
  - [Updates](#updates)
  - [Share debug log](#share-debug-log)
  - [About this app](#about-this-app)
- [FAQ](#faq)

<br>

## Finding your way around

| Where | What you can do |
|:--|:--|
| **Home** | Recommended playlists and albums. Switch with All / Music / Podcasts at the top |
| **Search** | Find songs, artists, albums and playlists, or browse by genre |
| **Library** | Liked Songs and your saved playlists and albums. Sort, filter and pin |
| **Mini player** (bottom) | The current song. ▶ / ⏸ plays and pauses; tap it to open the full player |
| **Full player** | Seek, previous / next, shuffle / repeat, lyrics, Next up (queue), sleep timer |
| **🎧 icon** | Choose where to play (this phone, Bluetooth, PC, speakers, TV and more) |
| **Top-left icon** | Menu (profile, Recents, What's new, Settings) |

<br>

## Settings

Open them from the top-left icon → Settings.

### Account

| Item | What it does |
|:--|:--|
| Username | The Spotify account you're signed in with |
| Log out | Logs out of this app. The debug log is cleared too |

### Content and display

| Item | What it does |
|:--|:--|
| Theme color | The color used for buttons and the now-playing highlight (9 colors) |
| AMOLED black | A pure black background. Saves a little battery on OLED screens |

### Playback

| Item | Default | What it does |
|:--|:--|:--|
| Background playback | On | Keeps playing when you close the app or switch to another app. When off, playback pauses when you leave the app |
| Handle ads | On | Mutes ads and plays them to the end at 16× speed (a 30-second ad is over in 2–3 seconds). If an ad comes between songs after the next song has started (gapless or crossfade), the next song keeps playing while the ad finishes silently in the background. This may violate Spotify's Terms of Use |
| Get ready to play at launch | On | When the app opens, it briefly plays muted and pauses, so your first ▶ starts right away (about 1.2 s → about 0.15 s). Skipped when another device such as a PC or speaker is playing |
| Gapless playback | On | Shortens the silence between songs. See [Gapless playback and crossfade](#gapless-playback-and-crossfade) below |
| Crossfade | Off | Fades the end of one song out and the start of the next in. When on, choose the length (1–12 s) |

#### Gapless playback and crossfade

These control what happens where one song ends and the next begins.

```
Normal     ♪♪♪♪♪♪ …… (silence) …… ♪♪♪♪♪♪
           current song            next song

Gapless    ♪♪♪♪♪♪♪♪♪♪♪ ♪♪♪♪♪♪♪♪♪♪♪
           keeps the silence as short as possible

Crossfade  ♪♪♪♪♪♪♪♪＼ ／♪♪♪♪♪♪♪♪♪♪
           fades the current song out, then the next one in
```

- **Gapless playback** removes the silence between songs, so one runs straight into the next. Great for live albums and albums where tracks run into each other.
  - How it works: near the end of a song, the next one is prepared on a second player and starts the moment the current one ends. Shortly after the next song starts, sound moves back to the usual player.
  - If the next song can't be prepared in time, the app moves on the usual way (a very short break can remain).
- **Crossfade** smoothly lowers and raises the volume at the join, like a radio station. The two songs really overlap.
  - The artwork and title switch to the next song as soon as it starts playing.
  - The seconds are how long the two songs overlap (the start of the next song plays over the last seconds of the current one). Longer means a slower change.
  - If the next song can't be prepared in time, the current song fades out and then the next fades in (half the seconds each).
  - **We recommend 5–6 seconds.**

    | Seconds | Feel | Good for |
    |:--|:--|:--|
    | 1–3 | Just removes the silence | Listening to albums start to finish |
    | 5–6 | Melts naturally into the next song | Most playlists |
    | 8–12 | Slow change | Background music |

  - Shorter suits songs with hard endings; longer suits songs that fade out slowly.
- You can turn both on.
- An ad can come between songs after the next song has already started. The next song keeps playing on the second player; when the ad is over, sound moves back to the usual player from where you are (the song doesn't start over). With "Handle ads" off, the next song pauses during the ad.

### Media quality

| Item | What it does |
|:--|:--|
| Streaming on Wi-Fi | Audio quality on Wi-Fi |
| Streaming on mobile data | Audio quality on mobile data. Lower saves data |
| Current status | Your connection and the actual quality of the song that's playing |

| Quality | Meaning |
|:--|:--|
| Automatic | Adjusts to your connection |
| Low | Uses less data |
| Normal | Uses a little less data |
| High | AAC 128 kbps (the free plan's limit) |
| Very high | AAC 256 kbps (Spotify Premium only) |

### Language

| Item | What it does |
|:--|:--|
| System default | Japanese if your device is set to Japanese, otherwise English (default) |
| 日本語 / English | Always use that language. Switching doesn't stop playback |

### Updates

Checks whether a new version is out. If there is one, download and install it with a single button.
The app also checks on its own when you open it (at most once every 6 hours).

### Share debug log

Shares a record of what the app did (ad handling, playback commands, errors and so on).
Sending it when something goes wrong helps track down the cause. It contains no passwords or login details.

**Debug mode**: tap the icon on the About page 10 times in a row to turn it on. It records every network destination and detailed playback events such as crossfade, and "Share full log" in Settings sends it as a file. It turns off automatically when the app restarts.

### About this app

The version number, the author, and links to the source code and license.

<br>

## FAQ

<details>
<summary><b>Right after opening the app, ▶ takes a moment to start</b></summary>

Just after the app opens, it takes a few seconds for Spotify to register this phone as a device that can play.
Playback starts by itself as soon as that's done, so just wait. With "Get ready to play at launch" on, ▶ starts right away once the app is ready.

</details>

<details>
<summary><b>Will opening the app take over playback from my PC or speaker?</b></summary>

No. "Get ready to play at launch" does nothing when another device is playing.
To play on your phone, pick "This phone" from the 🎧 icon.

</details>

<details>
<summary><b>"⚠ Some features aren't working" appears</b></summary>

A Spotify update may have changed something the app relies on.
Tap the message to share the debug log; sending it helps get it fixed.

</details>

<details>
<summary><b>Can I use Premium features (downloads, Jam and so on)?</b></summary>

No. The app is built on the Spotify Web Player, so it can only do what the Web Player can.

</details>
