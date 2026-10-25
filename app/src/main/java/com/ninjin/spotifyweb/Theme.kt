package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
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
    val accents = listOf("green" to Color(0xFF1ED760), "blue" to Color(0xFF509BF5), "purple" to Color(0xFFAF7AF6), "pink" to Color(0xFFF573A0), "orange" to Color(0xFFFF9E3D))
    var accent by mutableStateOf(accents[0].second)
    var amoled by mutableStateOf(false)
    fun load(host: AppHost) {
        accent = accents.firstOrNull { it.first == host.setting("accent", "green") }?.second ?: accents[0].second
        amoled = host.setting("amoled", "false") == "true"
    }
}

internal val Bg: Color get() = if (Theme.amoled) Color.Black else Color(0xFF121212)
internal val Card2 = Color(0xFF2A2A2A)
internal val Sub = Color(0xFFB3B3B3)
internal val Accent: Color get() = Theme.accent

/** 24x24 の塗りのアイコン */
internal fun icon(d: String): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White)).build()

/** 24x24 の線のアイコン（公式アプリと同じ細い線） */
internal fun line(d: String, w: Float = 1.8f): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()

internal object Icons {
    val home = icon("M12.5 3.25a.5.5 0 0 0-1 0L3 10.4V21h6.5v-6h5v6H21V10.4z")
    val search = icon("M10.5 3a7.5 7.5 0 0 1 5.96 12.06l4.24 4.24-1.4 1.4-4.24-4.24A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z")
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
