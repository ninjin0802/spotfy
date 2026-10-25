package com.ninjin.spotifyweb

import java.util.Locale

/**
 * 表示の言語。端末の言語が日本語なら日本語、それ以外は英語（日本語と英語の 2 つだけ対応）。
 * 端末の言語を変えると画面は作り直されるので、そのたびに読み直せばよい。
 */
internal val isJapanese: Boolean get() = Locale.getDefault().language == "ja"

/** 日本語と英語の文言を並べて書き、端末の言語に合うほうを使う */
internal fun tr(ja: String, en: String): String = if (isJapanese) ja else en

/** 「3曲」「3 songs」（英語は 1 曲のときだけ単数） */
internal fun songs(n: Int) = tr("${n}曲", if (n == 1) "1 song" else "$n songs")

/** 「5分」「1時間」「5 min」「1 hr」 */
internal fun duration(min: Int) =
    if (min >= 60) tr("${min / 60}時間", "${min / 60} hr") else tr("${min}分", "$min min")
