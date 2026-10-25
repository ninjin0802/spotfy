package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ジャケットの色（公式アプリと同じく、くすんだ濃い色）。曲が変わるとなめらかに変わる */
@Composable
internal fun rememberArtColorPublic(url: String): Color = rememberArtColor(url)

@Composable
internal fun rememberArtColor(url: String): Color {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(Color(0xFF3A3A3A)) }
    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(64).allowHardware(false).build())
            (r as? SuccessResult)?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
        } ?: return@LaunchedEffect
        val p = withContext(Dispatchers.Default) { Palette.from(bmp).generate() }
        val sw = p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch ?: return@LaunchedEffect
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(sw.rgb, hsv)
        hsv[2] = hsv[2].coerceAtMost(0.42f)
        hsv[1] = hsv[1].coerceAtMost(0.75f)
        c = Color(android.graphics.Color.HSVToColor(hsv))
    }
    val anim by animateColorAsState(c, tween(500), label = "art")
    return anim
}

@Composable
internal fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Sub, fontSize = 15.sp) }
}

/** 画面いっぱいの読み込み中 */
@Composable
internal fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingDots() }
}

/** 読み込み中の印（公式アプリと同じ、3 つの点が順に大きく明るくなる） */
@Composable
internal fun LoadingDots() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val p by t.animateFloat(
                0f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.keyframes {
                        durationMillis = 1200
                        0f at 0; 1f at 300; 0f at 600; 0f at 1200
                    },
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * 160),
                ),
                label = "dot$i",
            )
            Box(
                Modifier.size(10.dp).graphicsLayer { scaleX = 0.7f + 0.3f * p; scaleY = 0.7f + 0.3f * p; alpha = 0.35f + 0.65f * p }
                    .clip(CircleShape).background(Color.White)
            )
        }
    }
}

@Composable
internal fun Avatar(user: Bridge.User?, size: Int, onClick: (() -> Unit)? = null) {
    val m = Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE8A87C)).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Box(m, contentAlignment = Alignment.Center) {
        if (user?.image?.isNotEmpty() == true) AsyncImage(user.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(user?.name?.take(1) ?: "", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = (size / 2.2).sp)
    }
}

@Composable
internal fun Ico(icon: ImageVector, size: Int, tint: Color = Color.White, box: Int = 40, onClick: () -> Unit) {
    Box(Modifier.size(box.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(icon), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))
    }
}

internal fun mmss(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

internal fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a))
}
