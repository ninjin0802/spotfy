package com.ninjin.spotifyweb

/** アプリの画面（Compose）から使う、端末側の機能と設定 */
interface AppHost {
    fun setting(name: String, fallback: String): String
    fun setSetting(name: String, value: String)
    /** いまの通信に合わせた音質（モバイル用が未設定なら Wi-Fi 用） */
    fun quality(cellular: Boolean): String
    val metered: Boolean
    fun share(url: String, title: String)
    fun outputSwitch()
    /** このスマホの出力先（Bluetooth イヤホンなど。スピーカーなら空） */
    fun outputName(): String
    /** デバッグモード中は、全部の記録をファイルで共有する */
    fun shareDebugLog()
    /** デバッグモード（「このアプリについて」のアイコンを 10 回押すと切り替わる。起動し直すとオフ） */
    var debugMode: Boolean
    /** デバッグモードの記録ファイルの場所（adb pull で取り出せる） */
    val fullLogPath: String
    fun logout()
    /** アプリ内アップデート（GitHub Releases から新しい版を落としてインストール） */
    val updater: Updater
}
