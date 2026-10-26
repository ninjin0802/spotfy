package com.ninjin.spotifyweb

import java.util.Locale

/**
 * 表示の言語。端末の言語が日本語なら日本語、それ以外は英語（日本語と英語の 2 つだけ対応）。
 * 端末の言語を変えると画面は作り直されるので、そのたびに読み直せばよい。
 */
internal val isJapanese: Boolean get() {
    // 言語の設定を変えたら、この値を読んだ画面が描き直される
    AppLanguage.revision.intValue
    return Locale.getDefault().language == "ja"
}

/** 日本語と英語の文言を並べて書き、端末の言語に合うほうを使う */
internal fun tr(ja: String, en: String): String = if (isJapanese) ja else en

/** 「3曲」「3 songs」（英語は 1 曲のときだけ単数） */
internal fun songs(n: Int) = tr("${n}曲", if (n == 1) "1 song" else "$n songs")

/** 「5分」「1時間」「5 min」「1 hr」 */
internal fun duration(min: Int) =
    if (min >= 60) tr("${min / 60}時間", "${min / 60} hr") else tr("${min}分", "$min min")

/**
 * 表示の言語の設定。"" は端末に合わせる、"ja" / "en" はその言語に固定。
 * Android 13 以降はシステムの「アプリの言語」と同じ設定を使う（どちらで変えても揃う）。
 * 画面は作り直さず（再生を止めないため）、[revision] を変えて文言を読んでいる部分だけ描き直す
 */
object AppLanguage {
    val revision = androidx.compose.runtime.mutableIntStateOf(0)

    fun get(context: android.content.Context): String =
        if (android.os.Build.VERSION.SDK_INT >= 33) context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.get(0)?.language.orEmpty()
        else context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("language", "").orEmpty()

    fun set(context: android.content.Context, tag: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33) context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(tag)
        else context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putString("language", tag).apply()
        apply(context)
    }

    /** 設定に合わせて既定の言語を切り替え、画面を描き直す */
    fun apply(context: android.content.Context) {
        val tag = get(context)
        val locale = if (tag.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
        revision.intValue++
    }
}
