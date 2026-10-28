# spotfy の開発の決まり

Spotify Web Player を見えない WebView で動かし、画面は Jetpack Compose で描く非公式の Android アプリ。
返事・コメント・コミットメッセージ・PR は日本語で書く。コードのコメントは、まわりの書き方（量・言葉づかい）に合わせる。

## しくみ

- `app/src/main/assets/bridge.js`：Web Player の中で動く橋。データの取得、再生の操作、広告の処理、クロスフェード・ギャップレス（2 台目の再生器でつなぐ）。Kotlin とは `__mb.call` / `__native.send` でやりとりする
- `app/src/main/java/com/ninjin/spotifyweb/`：Kotlin 側。`Bridge.kt`（橋の呼び出し）、`MainActivity.kt`、`PlaybackService.kt`（バックグラウンド再生）、画面（`*Screen.kt`・`Player.kt` など）
- `scripts/`：テスト（`ad-test.mjs`・`seam-test.mjs`・`smoke-test.sh`）とリリース（`release.sh`）
- 版の番号は `VERSION` だけで決める。変更履歴は `CHANGELOG.md`

## 作業の流れ

1. **main から作業ブランチを切る。** main へ直接 push しない
2. **直す。** 直したら手元でテストを通す（下の「テスト」）。壊れ方を見つけて直したときは、同じ壊れ方を CI で捕まえられるようにテストを足す
3. **実機で確かめる。** 再生・つなぎ目・広告など、偽物の Web Player では分からない所を変えたとき。デバッグログやデバッグモードの記録で、直ったことを数字で確かめる（下の「実機」）
4. **コードレビュー：`/code-review`。** 指摘を直してから次へ
5. **セキュリティレビュー：`/security-review`。** 通信・ログ（トークンなどを残さない）・WebView と Kotlin の橋・権限・設定・外から受け取る値に触れたときは必ずやる。文書だけの変更では省いてよい
6. **PR を作る。** 説明には「何が起きていたか・原因・直したこと・確かめたこと」を書く。最後に `🤖 Generated with [Claude Code](https://claude.com/claude-code)` を付ける
7. **CI が全部通ったらマージする**（build と、エミュレーターのスモークテスト 2 つ）。失敗したらマージせずに直す
8. **リリースするとき**は、`VERSION` を上げて `CHANGELOG.md` に「## <版>」の節を書き、それも PR で main に入れる。main の最新で `scripts/release.sh`（ビルド → スモークテスト → タグ → GitHub Releases に APK）

利用者の判断が要ること（マージしてよいか、リリースするか、元に戻せない操作）は聞いてから進める。

## テスト

```sh
node --check app/src/main/assets/bridge.js
node scripts/ad-test.mjs     # 広告の処理（偽物の Web Player で広告を流す）
node scripts/seam-test.mjs   # つなぎ目（実機の再生要素のくせをまね、途切れ・巻き戻り・ぶつっと鳴る切り替えがないか）
./gradlew assembleRelease
```

`BRIDGE=<path> node scripts/seam-test.mjs` で、別の版の bridge.js を試せる（テストが古い壊れ方を捕まえるかの確認に使う）。

## GitHub

- アカウントは `ninjin0802`。gh は `GH_TOKEN="$(gh auth token --user ninjin0802)" gh ...`
- push は `git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push ...`
- `release.sh` には同じ設定を環境変数で渡す：`GIT_CONFIG_COUNT=2 GIT_CONFIG_KEY_0=credential.helper GIT_CONFIG_VALUE_0= GIT_CONFIG_KEY_1=credential.helper GIT_CONFIG_VALUE_1='!gh auth git-credential'`
- トークンは画面にもファイルにも出さない
- コミットメッセージの最後に `Co-Authored-By: Claude <noreply@anthropic.com>`（使っているモデル名に合わせる）

## 実機

- 端末は Pixel 9a。ワイヤレスデバッグで `adb connect <IP>:<ポート>`（ポートは変わるので、利用者に聞く）
- **利用者がスマホを使っているあいだ、adb のタップで画面を操作しない。** intent・broadcast・CDP を使う
- デバッグモード（アプリを起動し直すと切れる）：`adb shell am broadcast -n com.ninjin.spotifyweb/.DebugModeReceiver --ez on true`
- 全部のログ：`adb pull /sdcard/Android/data/com.ninjin.spotifyweb/files/logs/debug-full.log`（通信、`seam.phase` / `seam.tick`、`seamless.*`、`xf.fade`、`gap.*`、`ad.*`）
- WebView を CDP で調べる（デバッグモード中だけ）：`adb forward tcp:9333 localabstract:webview_devtools_remote_$(adb shell pidof com.ninjin.spotifyweb)`
- 2 台目の再生要素は DOM に無い。調べるときは `HTMLMediaElement.prototype.play` を包んで集める
- 入れ直しは `adb install -r`（署名が同じリリースビルドなら、ログインが消えない）。入れ直すと再生が止まるので、利用者に断ってから
