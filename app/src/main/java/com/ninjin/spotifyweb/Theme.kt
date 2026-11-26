package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** テーマ（myspoti と同じく、アクセントカラー 5 色と真っ黒の背景を選べる） */
object Theme {
    val accents = listOf("green" to Color(0xFF1ED760), "blue" to Color(0xFF509BF5), "purple" to Color(0xFFAF7AF6), "pink" to Color(0xFFF573A0), "orange" to Color(0xFFFF9E3D), "red" to Color(0xFFF15E6C), "yellow" to Color(0xFFFFC864), "cyan" to Color(0xFF4CC9E0), "mint" to Color(0xFF7FE7C4))
    var accent by mutableStateOf(accents[0].second)
    var amoled by mutableStateOf(false)
    /** 新しいテーマ（Apple Music の見た目）。false は Classic */
    var glass by mutableStateOf(false)
    fun load(host: AppHost) {
        glass = host.setting("theme", "classic") == "glass"
        accent = accents.firstOrNull { it.first == host.setting("accent", "green") }?.second ?: accents[0].second
        amoled = host.setting("amoled", "false") == "true"
    }
}

/** 明るい背景で描くか（新しいテーマで、端末がライトのとき）。全画面プレーヤーはジャケットの色の上なので、いつも暗い側で描く */
internal val LocalLight = androidx.compose.runtime.compositionLocalOf { false }

private val light: Boolean @Composable @ReadOnlyComposable get() = LocalLight.current

internal val Bg: Color @Composable @ReadOnlyComposable get() = when { light -> Color.White; Theme.glass || Theme.amoled -> Color.Black; else -> Color(0xFF121212) }
/** 文字とアイコンの色、その色を背景にしたときの上の色 */
internal val Fg: Color @Composable @ReadOnlyComposable get() = if (light) Color.Black else Color.White
internal val OnFg: Color @Composable @ReadOnlyComposable get() = if (light) Color.White else Color.Black
internal val Sub: Color @Composable @ReadOnlyComposable get() = when { light -> Color(0xFF8A8A8E); Theme.glass -> Color(0xFF98989F); else -> Color(0xFFB3B3B3) }
/** カード・シートの面と、その上の一段濃い面（区切り線・入力欄など） */
internal val Surface: Color @Composable @ReadOnlyComposable get() = when { light -> Color(0xFFF2F2F7); Theme.glass -> Color(0xFF1C1C1E); else -> Color(0xFF242424) }
internal val Surface2: Color @Composable @ReadOnlyComposable get() = when { light -> Color(0xFFE5E5EA); Theme.glass -> Color(0xFF2C2C2E); else -> Color(0xFF3A3A3A) }
internal val Card2: Color @Composable @ReadOnlyComposable get() = when { light -> Color(0xFFE5E5EA); Theme.glass -> Color(0xFF2C2C2E); else -> Color(0xFF2A2A2A) }
/** 新しいテーマは Apple Music と同じ赤 */
internal val Accent: Color get() = if (Theme.glass) Color(0xFFFA2D48) else Theme.accent
internal val OnAccent: Color get() = if (Theme.glass) Color.White else Color.Black

/** 24x24 の塗りのアイコン */
internal fun icon(d: String): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White)).build()

/** 24x24 の線のアイコン（公式アプリと同じ細い線） */
internal fun line(d: String, w: Float = 1.8f): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()

internal object ClassicIcons {
    val home = icon("M12.5 3.25a.5.5 0 0 0-1 0L3 10.4V21h6.5v-6h5v6H21V10.4z")
    val search = icon("M10.5 3a7.5 7.5 0 0 1 5.96 12.06l4.24 4.24-1.4 1.4-4.24-4.24A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z")
    val browse = icon("M4 4h7v7H4zm9 0h7v7h-7zM4 13h7v7H4zm9 0h7v7h-7z")
    val library = icon("M4 3h2v18H4zM9 3h2v18H9zm4.6.6 1.9-.6 5.6 17.4-1.9.6z")
    val play = icon("M7 4.5v15l12.5-7.5z")
    val pause = icon("M6 4h4v16H6zm8 0h4v16h-4z")
    val next = icon("M5 4.5v15l10-7.5zM17 4h2v16h-2z")
    val prev = icon("M19 4.5v15L9 12zM5 4h2v16H5z")
    val down = line("M5 9l7 7 7-7", 2f)
    val more = icon("M12 6.5a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0 7a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0 7a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z")
    val shuffle = line("M3 7h3.5c2 0 3 1 4.2 2.7l2.6 4.6C14.5 16 15.5 17 17.5 17H21M17.5 14l3 3-3 3M3 17h3.5c1.2 0 2-.4 2.7-1.1M13 8.1C13.7 7.4 14.5 7 15.7 7H21M17.5 4l3 3-3 3")
    val repeat = line("M4 12V9.5A3.5 3.5 0 0 1 7.5 6H19M16 3l3 3-3 3M20 12v2.5a3.5 3.5 0 0 1-3.5 3.5H5M8 21l-3-3 3-3")
    val add = line("M12 7.5v9M7.5 12h9M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", 1.6f)
    val check = icon("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1.3 14.2-4-4 1.4-1.4 2.6 2.6 5.6-5.6 1.4 1.4z")
    val device = line("M4 15V6a1 1 0 0 1 1-1h12M2 19h11M16 9h5v11h-5zM18.5 17.5h0", 1.6f)
    val share = line("M12 3v12M7.5 7.5 12 3l4.5 4.5M5 12v7.5A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V12", 1.6f)
    val queue = line("M4 6h16M4 12h10M4 18h10M17 14v7l4-3.5z", 1.6f)
    val back = line("M15 5l-7 7 7 7", 2f)
    val close = line("M6 6l12 12M18 6 6 18", 2f)
    val gear = line("M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 13.5l1.6 1.2-2 3.4-1.9-.7a7 7 0 0 1-2 1.2L14.8 21h-4l-.3-2.4a7 7 0 0 1-2-1.2l-1.9.7-2-3.4 1.6-1.2a7 7 0 0 1 0-2.3L4.6 10l2-3.4 1.9.7a7 7 0 0 1 2-1.2L10.8 3h4l.3 2.1a7 7 0 0 1 2 1.2l1.9-.7 2 3.4-1.6 1.2a7 7 0 0 1 0 2.3z", 1.6f)
}

/** 新しいテーマのアイコン（Apple Music と同じく、角の丸い塗りと、太めの線） */
internal object GlassIcons {
    val home = icon("M12 3.2 3 10.5V20a1 1 0 0 0 1 1h5v-6h6v6h5a1 1 0 0 0 1-1v-9.5z")
    val browse = icon("M4 6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zm9 0a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2zM4 15a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zm9 0a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2z")
    val library = line("M8 3h11a2 2 0 0 1 2 2v11M5 7h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM12 18V11.5l-2.5.6M12 18a1.6 1.6 0 1 1-3.2 0 1.6 1.6 0 0 1 3.2 0z", 1.9f)
    val search = line("M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM15.5 15.5 20 20", 2.3f)
    val play = icon("M8 5.6v12.8c0 .8.9 1.3 1.6.9l10.2-6.4c.6-.4.6-1.4 0-1.8L9.6 4.7C8.9 4.3 8 4.8 8 5.6z")
    val pause = icon("M7 5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1zm6 0a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1z")
    val next = icon("M2.5 6.6v10.8c0 .7.8 1.1 1.4.7l7.6-5.4a.8.8 0 0 0 0-1.4L3.9 5.9c-.6-.4-1.4 0-1.4.7zm10 0v10.8c0 .7.8 1.1 1.4.7l7.6-5.4a.8.8 0 0 0 0-1.4l-7.6-5.4c-.6-.4-1.4 0-1.4.7z")
    val prev = icon("M21.5 6.6v10.8c0 .7-.8 1.1-1.4.7l-7.6-5.4a.8.8 0 0 1 0-1.4l7.6-5.4c.6-.4 1.4 0 1.4.7zm-10 0v10.8c0 .7-.8 1.1-1.4.7l-7.6-5.4a.8.8 0 0 1 0-1.4l7.6-5.4c.6-.4 1.4 0 1.4.7z")
    val more = icon("M5 13.6a1.6 1.6 0 1 1 0-3.2 1.6 1.6 0 0 1 0 3.2zm7 0a1.6 1.6 0 1 1 0-3.2 1.6 1.6 0 0 1 0 3.2zm7 0a1.6 1.6 0 1 1 0-3.2 1.6 1.6 0 0 1 0 3.2z")
    val add = line("M12 5v14M5 12h14", 2.2f)
    val check = line("M5 12.5l4.5 4.5L19 7", 2.4f)
    val back = line("M15 4.5 7.5 12l7.5 7.5", 2.4f)
    val down = line("M5 9l7 7 7-7", 2.4f)
    val queue = line("M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01", 2.2f)
    val share = line("M12 3v12M8 7l4-4 4 4M7 10H6a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-9a1 1 0 0 0-1-1h-1", 1.9f)
    val close = line("M6 6l12 12M18 6 6 18", 2.4f)
    val heart = line("M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z", 1.9f)
    val playlist = line("M4 6h11M4 11h11M4 16h6M18 18.5V8l3-1M18 18.5a2 2 0 1 1-4 0 2 2 0 0 1 4 0z", 1.9f)
    val mic = line("M14.5 4.5a4 4 0 1 1 5 5M15.5 9.5l-9 8.5-2-2 8.5-9M5.5 18.5 3 21", 1.9f)
    val album = line("M7 3h12a2 2 0 0 1 2 2v12M4 7h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM9.5 16a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", 1.8f)
    val podcast = line("M12 13.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM12 14.5V21M8.5 15.5a5 5 0 1 1 7 0M6 18a8.5 8.5 0 1 1 12 0", 1.8f)
    val chevron = line("M9 5l7 7-7 7", 2.4f)
    val speakerLow = icon("M4 9h3.5L12 5v14l-4.5-4H4z")
    val speakerHigh = line("M4 9h3.5L12 5v14l-4.5-4H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11", 1.8f)
    val lyrics = line("M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v10a1.5 1.5 0 0 1-1.5 1.5H10l-4.5 3.5V17h0A1.5 1.5 0 0 1 4 15.5zM9 9.5v2.2M9 9.5h0M14.5 9.5v2.2", 1.9f)
}

/** いまのテーマのアイコン。Glass に無いものは Classic と同じ */
internal object Icons {
    private val g get() = Theme.glass
    val home get() = if (g) GlassIcons.home else ClassicIcons.home
    val browse get() = if (g) GlassIcons.browse else ClassicIcons.browse
    val search get() = if (g) GlassIcons.search else ClassicIcons.search
    val library get() = if (g) GlassIcons.library else ClassicIcons.library
    val play get() = if (g) GlassIcons.play else ClassicIcons.play
    val pause get() = if (g) GlassIcons.pause else ClassicIcons.pause
    val next get() = if (g) GlassIcons.next else ClassicIcons.next
    val prev get() = if (g) GlassIcons.prev else ClassicIcons.prev
    val down get() = if (g) GlassIcons.down else ClassicIcons.down
    val more get() = if (g) GlassIcons.more else ClassicIcons.more
    val shuffle get() = ClassicIcons.shuffle
    val repeat get() = ClassicIcons.repeat
    val add get() = if (g) GlassIcons.add else ClassicIcons.add
    val check get() = if (g) GlassIcons.check else ClassicIcons.check
    val device get() = ClassicIcons.device
    val share get() = if (g) GlassIcons.share else ClassicIcons.share
    val queue get() = if (g) GlassIcons.queue else ClassicIcons.queue
    val back get() = if (g) GlassIcons.back else ClassicIcons.back
    val close get() = if (g) GlassIcons.close else ClassicIcons.close
    val gear get() = ClassicIcons.gear
    val lyrics get() = GlassIcons.lyrics
}
