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
    fun logout()
}
