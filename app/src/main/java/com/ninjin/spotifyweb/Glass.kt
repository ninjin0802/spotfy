package com.ninjin.spotifyweb

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
@Composable
internal fun Modifier.glass(backdrop: Backdrop, shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier {
    val tint = if (LocalLight.current) Color.White.copy(alpha = 0.55f) else Color(0xFF2C2C2E).copy(alpha = 0.55f)
    val solid = if (LocalLight.current) Color(0xF2F7F7F7) else Color(0xF22C2C2E)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (Build.VERSION.SDK_INT >= 31) { vibrancy(); blur(6.dp.toPx()) }
            if (Build.VERSION.SDK_INT >= 33) lens(14.dp.toPx(), 28.dp.toPx())
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

/** 3 つのタブのガラスのバー。選んだタブの下の丸い印は、替えるとばねのように伸びて移る */
@Composable
private fun GlassTabPill(backdrop: Backdrop, current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight().glass(backdrop).padding(4.dp)) {
        val w = maxWidth / GlassTabs.size
        val index = GlassTabs.indexOf(current).coerceAtLeast(0).toFloat()
        val pos by animateFloatAsState(index, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "tab")
        // 動いているあいだだけ横に伸ばす（止まると元の幅）
        val stretch = 1f + 0.35f * kotlin.math.abs(index - pos).coerceAtMost(1f)
        if (current != null) Box(
            Modifier.offset(x = w * pos).width(w).fillMaxHeight().graphicsLayer { scaleX = stretch }
                .clip(CircleShape).background(Fg.copy(alpha = if (LocalLight.current) 0.08f else 0.12f)),
        )
        Row(Modifier.fillMaxSize()) {
            GlassTabs.forEach { t ->
                val on = t == current
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable(remember { MutableInteractionSource() }, null) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    TabIcon(t.icon, on, 22.dp)
                    Text(t.label, color = if (on) Accent else Fg, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** ガラスのミニプレーヤー：ジャケット、曲名とアーティスト、再生／次へ。縮んだときは再生だけ */
@Composable
internal fun GlassMiniPlayer(bridge: Bridge, p: PlayerState, backdrop: Backdrop, compact: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(
        modifier.height(if (compact) 62.dp else 58.dp).glass(backdrop).clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(p.image, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Card2), contentScale = ContentScale.Crop)
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
        Text(title, color = Fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
        if (fresh.isNotEmpty()) item { ShelfRow(Shelf(tr("新着", "New releases"), fresh), onOpen) }
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
internal fun GlassPlayerMain(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onClose: () -> Unit, onDevices: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit) {
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
                Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = 24.dp.toPx(); shape = RoundedCornerShape(14.dp); clip = true },
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
            // 下：シャッフル・リピート／再生先／キュー
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Ico(Icons.shuffle, 22, if (p.shuffle) Accent else Color.White.copy(alpha = 0.7f), 44) { scope.launch { runCatching { bridge.shuffle(!p.shuffle) } } }
                val out = if (p.device.isEmpty()) host.outputName() else ""
                val on = p.device.isNotEmpty() || out.isNotEmpty()
                Ico(if (p.device.isNotEmpty()) DevIcons.of(p.deviceType) else if (out.isNotEmpty()) DevIcons.headphones else DevIcons.phone, 22, if (on) Accent else Color.White.copy(alpha = 0.7f), 44, onClick = onDevices)
                Ico(Icons.queue, 22, Color.White.copy(alpha = 0.7f), 44, onClick = onQueue)
                Box(contentAlignment = Alignment.Center) {
                    Ico(Icons.repeat, 22, if (p.repeat > 0) Accent else Color.White.copy(alpha = 0.7f), 44) { scope.launch { runCatching { bridge.repeat((p.repeat + 1) % 3) } } }
                    if (p.repeat == 2) Text("1", color = Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-1).dp))
                }
            }
        }
    }
}
