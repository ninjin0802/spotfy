package com.ninjin.spotifyweb

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * 新しいテーマ（Apple Music の iOS 26 の見た目）の部品。
 * 下のタブとミニプレーヤーは Liquid Glass：後ろの画面を写してぼかし、ふちで光を曲げる（backdrop ライブラリ）
 */

/** Liquid Glass の面。光を曲げるのは Android 13 から、ぼかしは 12 から。それより古い端末は半透明の色だけ */
/** 光を曲げる（lens）は角の丸い形にしか使えないので、四角い帯では [refract] を false にする */
@Composable
internal fun Modifier.glass(backdrop: Backdrop, shape: androidx.compose.ui.graphics.Shape = CircleShape, refract: Boolean = true): Modifier {
    val tint = if (LocalLight.current) Color.White.copy(alpha = 0.55f) else Color(0xFF2C2C2E).copy(alpha = 0.55f)
    val solid = if (LocalLight.current) Color(0xF2F7F7F7) else Color(0xF22C2C2E)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (Build.VERSION.SDK_INT >= 31) { vibrancy(); blur(6.dp.toPx()) }
            if (refract && Build.VERSION.SDK_INT >= 33) lens(14.dp.toPx(), 28.dp.toPx())
        },
        highlight = { Highlight.Default },
        onDrawSurface = { drawRect(if (Build.VERSION.SDK_INT >= 31) tint else solid) },
    )
}

/** 下にスクロールしたら下のタブを縮め、上に戻したら広げる（Apple Music と同じ） */
internal class CollapseOnScroll : NestedScrollConnection {
    var collapsed by mutableStateOf(false)
    private var acc = 0f
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = consumed.y
        if (dy == 0f) return Offset.Zero
        if ((dy < 0) != (acc < 0)) acc = 0f
        acc += dy
        if (acc < -120f) collapsed = true
        if (acc > 60f) collapsed = false
        return Offset.Zero
    }
}

/** タブの並び：ガラスのバーに 3 つ、検索は右に離した丸いボタン */
internal val GlassTabs = listOf(Tab.Home, Tab.Browse, Tab.Library)

/** 下のタブとミニプレーヤー。縮むとバーはいまのタブだけになり、ミニプレーヤーがその横に入る */
@Composable
internal fun GlassBottom(
    backdrop: Backdrop, tab: Tab, collapsed: Boolean, onSelect: (Tab) -> Unit, onExpand: () -> Unit,
    mini: (@Composable (compact: Boolean, modifier: Modifier) -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(mini != null && !collapsed, enter = fadeIn(), exit = fadeOut()) {
            mini?.invoke(false, Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val current = if (tab in GlassTabs) tab else null
            if (collapsed) {
                // 縮んだバー：いまのタブのアイコンだけ。押すと広げる
                Box(Modifier.size(62.dp).glass(backdrop).clickable(remember { MutableInteractionSource() }, null, onClick = onExpand), contentAlignment = Alignment.Center) {
                    TabIcon((current ?: Tab.Home).icon, current != null)
                }
                if (mini != null) mini(true, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            } else {
                GlassTabPill(backdrop, current, onSelect, Modifier.weight(1f))
            }
            Box(
                Modifier.size(62.dp).glass(backdrop).clickable(remember { MutableInteractionSource() }, null) { onSelect(Tab.Search) },
                contentAlignment = Alignment.Center,
            ) { TabIcon(Icons.search, tab == Tab.Search) }
        }
    }
}

@Composable
private fun TabIcon(icon: ImageVector, on: Boolean, size: Dp = 24.dp) =
    Image(rememberVectorPainter(icon), null, Modifier.size(size), colorFilter = ColorFilter.tint(if (on) Accent else Fg))

/**
 * 選んだタブの下の印の動き（Apple Music の「ぷるぷる」）。
 * 押すとふくらみ、縦と横を別々のばねで戻すので揺れる。動く速さに合わせて横に伸び縦に縮む。
 * backdrop の見本アプリ（Kyant0/AndroidLiquidGlass の DampedDragAnimation、Apache 2.0）の値を参考にした
 */
private class JellyIndicator(private val scope: kotlinx.coroutines.CoroutineScope, initial: Float, private val last: Float) {
    private val pos = Animatable(initial, 0.001f)
    private val vel = Animatable(0f, 5f)
    private val pressAnim = Animatable(0f, 0.001f)
    private val sx = Animatable(1f, 0.001f)
    private val sy = Animatable(1f, 0.001f)
    private val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
    val value get() = pos.value
    val target get() = pos.targetValue
    val press get() = pressAnim.value
    /** 速さで伸び縮みさせた横・縦の大きさ */
    val scaleX get() = sx.value / (1f - (vel.value / 10f * 0.75f).coerceIn(-0.2f, 0.2f))
    val scaleY get() = sy.value * (1f - (vel.value / 10f * 0.25f).coerceIn(-0.2f, 0.2f))

    fun down() {
        tracker.resetTracking()
        scope.launch { pressAnim.animateTo(1f, spring(1f, 1000f)) }
        scope.launch { sx.animateTo(78f / 56f, spring(0.6f, 250f)) }
        scope.launch { sy.animateTo(78f / 56f, spring(0.7f, 250f)) }
    }

    fun up() {
        scope.launch {
            // 目的のタブの近くまで来てから縮める
            if (pos.value != pos.targetValue) snapshotFlow { pos.value }.first { kotlin.math.abs(it - pos.targetValue) < last * 0.025f + 0.01f }
            launch { pressAnim.animateTo(0f, spring(1f, 1000f)) }
            launch { sx.animateTo(1f, spring(0.6f, 250f)) }
            launch { sy.animateTo(1f, spring(0.7f, 250f)) }
        }
    }

    /** 指について動かす */
    fun follow(v: Float) {
        scope.launch {
            pos.animateTo(v.coerceIn(0f, last), spring(1f, 1000f)) {
                tracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
                val t = tracker.calculateVelocity().x / last.coerceAtLeast(1f)
                scope.launch { vel.animateTo(t, spring(0.5f, 300f)) }
            }
        }
    }

    /** 押して替えたとき：ふくらんで移り、揺れながら戻る */
    fun jump(v: Float) {
        down()
        scope.launch { pos.animateTo(v.coerceIn(0f, last), spring(1f, 1000f)) }
        scope.launch { vel.animateTo(0f, spring(0.5f, 300f)) }
        up()
    }
}

/**
 * 3 つのタブのガラスのバー。押しても、なぞっても替えられる（Apple Music と同じく、なぞるあいだ印が指についてきてふくらみ、
 * 離したタブに替わる）。なぞるとバーごと少し引っぱられ、離すとばねで戻る
 */
@Composable
private fun GlassTabPill(backdrop: Backdrop, current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pad = with(density) { 4.dp.toPx() }
        val wPx = (constraints.maxWidth - 2 * pad) / GlassTabs.size
        val last = (GlassTabs.size - 1).toFloat()
        val scope = rememberCoroutineScope()
        val index = GlassTabs.indexOf(current).coerceAtLeast(0).toFloat()
        val jelly = remember { JellyIndicator(scope, index, last) }
        val pull = remember { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }
        // ほかの所（Classic から戻る、戻るボタンなど）でタブが替わったときも印を動かす
        LaunchedEffect(index) { if (!dragging && kotlin.math.round(jelly.target) != index) jelly.jump(index) }
        val light = LocalLight.current
        val pullX = { val f = (pull.value / constraints.maxWidth).coerceIn(-1f, 1f); pad * kotlin.math.sign(f) * androidx.compose.animation.core.EaseOut.transform(kotlin.math.abs(f)) }
        val shown = if (dragging) GlassTabs[kotlin.math.round(jelly.target).toInt()] else current
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationX = pullX()
                    val s = 1f + 16.dp.toPx() / size.width * jelly.press
                    scaleX = s; scaleY = s
                }
                .glass(backdrop)
                .padding(4.dp),
        ) {
            // 印：ふだんは薄い面、押しているあいだはガラスのレンズになって後ろを曲げる
            if (current != null || dragging) Box(
                Modifier.graphicsLayer {
                    translationX = jelly.value * wPx
                    scaleX = jelly.scaleX; scaleY = jelly.scaleY
                }.width(with(density) { wPx.toDp() }).fillMaxHeight()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = { if (Build.VERSION.SDK_INT >= 33) lens(10.dp.toPx() * jelly.press, 14.dp.toPx() * jelly.press, chromaticAberration = true) },
                        highlight = { Highlight.Default.copy(alpha = jelly.press) },
                        shadow = { com.kyant.backdrop.shadow.Shadow(alpha = jelly.press) },
                        onDrawSurface = {
                            drawRect(if (light) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.24f), alpha = 1f - jelly.press)
                            drawRect(Color.Black.copy(alpha = 0.03f * jelly.press))
                        },
                    ),
            )
            Row(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; jelly.down(); jelly.follow(o.x / wPx - 0.5f) },
                        onHorizontalDrag = { ch, dx ->
                            ch.consume()
                            jelly.follow(jelly.target + dx / wPx)
                            scope.launch { pull.snapTo(pull.value + dx) }
                        },
                        onDragEnd = {
                            val t = kotlin.math.round(jelly.target)
                            jelly.follow(t); jelly.up(); dragging = false
                            scope.launch { pull.animateTo(0f, spring(1f, 300f, 0.5f)) }
                            GlassTabs[t.toInt()].let { if (it != current) onSelect(it) }
                        },
                        onDragCancel = { jelly.follow(index); jelly.up(); dragging = false; scope.launch { pull.animateTo(0f, spring(1f, 300f, 0.5f)) } },
                    )
                },
            ) {
                GlassTabs.forEachIndexed { i, t ->
                    val on = t == shown
                    // なぞっている印の下のアイコンは、印に合わせて少し大きく
                    val near = (1f - kotlin.math.abs(jelly.value - i)).coerceIn(0f, 1f) * jelly.press
                    Column(
                        Modifier.weight(1f).fillMaxHeight().graphicsLayer { val s = 1f + 0.2f * near; scaleX = s; scaleY = s }
                            .clickable(remember { MutableInteractionSource() }, null) { if (t != current) { jelly.jump(i.toFloat()); onSelect(t) } else onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        TabIcon(t.icon, on, 22.dp)
                        Text(t.label, color = if (on) Accent else Fg, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** ガラスのミニプレーヤー：ジャケット、曲名とアーティスト、再生／次へ。縮んだときは再生だけ */
@Composable
internal fun GlassMiniPlayer(bridge: Bridge, p: PlayerState, backdrop: Backdrop, compact: Boolean, modifier: Modifier, playerOpen: Boolean, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(
        modifier.height(if (compact) 62.dp else 58.dp).glass(backdrop).clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 全画面プレーヤーが開いているあいだは隠す（ジャケットは全画面の側へ移る）
        Box(Modifier.size(40.dp)) {
            androidx.compose.animation.AnimatedVisibility(!playerOpen, enter = fadeIn(), exit = fadeOut()) {
                AsyncImage(p.image, null, Modifier.size(40.dp).sharedArt(this).clip(RoundedCornerShape(8.dp)).background(Card2), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.title, color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
            if (!compact) Text(p.artist, color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Ico(if (p.playing) Icons.pause else Icons.play, 24, Fg) { scope.launch { runCatching { bridge.toggle() } } }
        if (!compact) Ico(Icons.next, 22, Fg) { scope.launch { runCatching { bridge.next() } } }
    }
}

/** 大きな見出し（Apple Music と同じく、左に大きく、右にアカウントのアイコン） */
@Composable
internal fun GlassTitle(title: String, user: Bridge.User?, onAvatar: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).reportTitle(title))
        actions()
        Spacer(Modifier.width(8.dp))
        Avatar(user, 34, onAvatar)
    }
}

/** 「見つける」：フォロー中のアーティストの新着と、ジャンル */
@Composable
internal fun BrowseScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onOpen: (Card) -> Unit) {
    var fresh by remember { mutableStateOf<List<Card>>(emptyList()) }
    var tiles by remember { mutableStateOf(bridge.cachedBrowse()) }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        runCatching { bridge.browse() }.onSuccess { tiles = it }
        runCatching { bridge.whatsNew("music") }.onSuccess { fresh = it }
    }
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 170.dp)) {
        item { GlassTitle(tr("見つける", "New"), user, onAvatar) }
        if (fresh.isNotEmpty()) item { GlassShelfRow(Shelf(tr("新着", "New releases"), fresh), onOpen) }
        if (tiles.isNotEmpty()) item {
            Text(tr("ジャンル", "Browse by category"), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp))
        }
        items(tiles.chunked(2)) { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> GlassGenreTile(t, Modifier.weight(1f)) { onOpen(t) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** ジャンルのタイル（角の丸い横長のカード、左下に名前） */
@Composable
internal fun GlassGenreTile(t: Card, m: Modifier, onClick: () -> Unit) {
    val bg = runCatching { Color(android.graphics.Color.parseColor(t.subtitle)) }.getOrDefault(Card2)
    Box(m.aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick)) {
        AsyncImage(t.image, null, Modifier.align(Alignment.TopEnd).padding(10.dp).size(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

/** 全画面プレーヤー（Apple Music と同じく、ジャケットをぼかした背景に、大きなジャケットと白い操作） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun GlassPlayerMain(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onClose: () -> Unit, onDevices: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit, onLyrics: () -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(p.positionNow()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p) { while (true) { if (dragging == null) now = p.positionNow(); delay(250) } }
    Box(Modifier.fillMaxSize().background(tint)) {
        // ぼかしは Android 12 から。それより古い端末はジャケットの色だけ
        if (Build.VERSION.SDK_INT >= 31) AsyncImage(
            p.image, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(90.dp), contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 28.dp),
        ) {
            // 上のつまみ（押すと閉じる）
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(28.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClose), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
            }
            Spacer(Modifier.weight(1f))
            val scale by animateFloatAsState(if (p.playing) 1f else 0.82f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "art")
            AsyncImage(
                p.image, null,
                Modifier.fillMaxWidth().aspectRatio(1f).sharedArt(LocalPlayerScope.current).graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = 24.dp.toPx(); shape = RoundedCornerShape(14.dp); clip = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(p.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Ico(if (p.liked) Icons.check else Icons.add, 26, if (p.liked) Accent else Color.White, 44) { scope.launch { runCatching { bridge.like(p.uri, !p.liked) } } }
                Ico(Icons.more, 22, Color.White, 44, onClick = onMenu)
            }
            Spacer(Modifier.height(14.dp))
            val dur = p.dur.coerceAtLeast(1)
            val frac = dragging ?: (now.toFloat() / dur).coerceIn(0f, 1f)
            // 押しているあいだ太くなるつまみのない線（Apple Music と同じ）
            val thick by animateDpAsState(if (dragging != null) 10.dp else 6.dp, label = "bar")
            Slider(
                value = frac,
                onValueChange = { dragging = it },
                onValueChangeFinished = { val v = dragging ?: 0f; dragging = null; now = (v * dur).toLong(); scope.launch { runCatching { bridge.seek((v * dur).toLong()) } } },
                colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                thumb = {},
                track = { st ->
                    BoxWithConstraints(Modifier.fillMaxWidth().height(thick).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxHeight().width(maxWidth * st.value).background(Color.White.copy(alpha = 0.85f)))
                    }
                },
                modifier = Modifier.height(24.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(mmss((frac * dur).toLong()), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("-" + mmss((dur - frac * dur).toLong()), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Ico(Icons.prev, 38, Color.White, 64) { scope.launch { runCatching { bridge.prev() } } }
                Ico(if (p.playing) Icons.pause else Icons.play, 52, Color.White, 80) { scope.launch { runCatching { bridge.toggle() } } }
                Ico(Icons.next, 38, Color.White, 64) { scope.launch { runCatching { bridge.next() } } }
            }
            GlassVolume()
            // 下：歌詞／再生先／キュー（シャッフルとリピートはキューの画面に。Apple Music と同じ）
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Ico(Icons.lyrics, 24, Color.White.copy(alpha = 0.7f), 48, onClick = onLyrics)
                val out = if (p.device.isEmpty()) host.outputName() else ""
                val on = p.device.isNotEmpty() || out.isNotEmpty()
                Ico(if (p.device.isNotEmpty()) DevIcons.of(p.deviceType) else if (out.isNotEmpty()) DevIcons.headphones else DevIcons.phone, 24, if (on) Accent else Color.White.copy(alpha = 0.7f), 48, onClick = onDevices)
                Ico(Icons.queue, 24, Color.White.copy(alpha = 0.7f), 48, onClick = onQueue)
            }
        }
    }
}

/** 音量のバー（端末の音楽の音量。両端に小さいスピーカーと大きいスピーカー） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlassVolume() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val am = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val max = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max) }
    var dragging by remember { mutableStateOf(false) }
    // 音量ボタンで変えたときも追いかける
    LaunchedEffect(Unit) { while (true) { if (!dragging) vol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max; delay(500) } }
    val thick by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "vol")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(GlassIcons.speakerLow), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.55f)))
        Slider(
            value = vol,
            onValueChange = { dragging = true; vol = it; am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, kotlin.math.round(it * max).toInt(), 0) },
            onValueChangeFinished = { dragging = false },
            thumb = {},
            track = { st ->
                BoxWithConstraints(Modifier.fillMaxWidth().height(thick).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().width(maxWidth * st.value).background(Color.White.copy(alpha = 0.85f)))
                }
            },
            modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 10.dp),
        )
        Image(rememberVectorPainter(GlassIcons.speakerHigh), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.55f)))
    }
}

/*
 * ミニプレーヤーのジャケットが、そのまま大きくなって全画面プレーヤーのジャケットになる動き（Apple Music と同じ）。
 * 両方のジャケットに同じ名前を付け、開け閉めのあいだ Compose が位置と大きさをつなぐ
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
internal val LocalShared = androidx.compose.runtime.compositionLocalOf<androidx.compose.animation.SharedTransitionScope?> { null }
/** 全画面プレーヤーの出入りのアニメーション（ジャケットをつなぐのに使う） */
internal val LocalPlayerScope = androidx.compose.runtime.compositionLocalOf<androidx.compose.animation.AnimatedVisibilityScope?> { null }

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedArt(vis: androidx.compose.animation.AnimatedVisibilityScope?): Modifier {
    val s = LocalShared.current
    if (s == null || vis == null || !Theme.glass) return this
    return with(s) { this@sharedArt.sharedElement(rememberSharedContentState("player-art"), vis) }
}

/** 歌詞（Apple Music と同じく、ジャケットをぼかした背景に、左寄せの大きな太字。いまの行は白く、ほかは薄く） */
@Composable
internal fun GlassLyrics(p: PlayerState, lyrics: Bridge.Lyrics?, tint: Color, onClose: () -> Unit) {
    var now by remember { mutableLongStateOf(p.positionNow()) }
    LaunchedEffect(p) { while (true) { now = p.positionNow(); delay(200) } }
    val cur = lyricsLine(lyrics, now)
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(cur) { if (cur > 0) list.animateScrollToItem((cur - 1).coerceAtLeast(0)) }
    Box(Modifier.fillMaxSize().background(tint).clickable(remember { MutableInteractionSource() }, null) {}) {
        if (Build.VERSION.SDK_INT >= 31) AsyncImage(p.image, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(90.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            // 上：小さなジャケットと曲名、右に閉じる
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(p.image, null, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(p.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Ico(GlassIcons.close, 18, Color.White, 36, onClick = onClose)
            }
            val lines = lyrics?.lines ?: emptyList()
            LazyColumn(state = list, contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 24.dp, bottom = 200.dp)) {
                items(lines.size) { i ->
                    val on = lyrics?.synced != true || i == cur
                    val a by animateFloatAsState(if (on) 1f else 0.35f, label = "line")
                    Text(lines[i].second, color = Color.White.copy(alpha = a), fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
                if (lines.isEmpty()) item { Text(tr("この曲の歌詞はありません", "No lyrics for this song"), color = Color.White, fontSize = 18.sp) }
            }
        }
    }
}
