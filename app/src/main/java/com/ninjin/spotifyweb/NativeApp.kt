package com.ninjin.spotifyweb

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF121212)
private val Card2 = Color(0xFF2A2A2A)
private val Sub = Color(0xFFB3B3B3)
private val Accent = Color(0xFF1ED760)

/** 24x24 のパス 1 本からアイコンを作る */
private fun icon(d: String): ImageVector = Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White)).build()

private object Icons {
    val home = icon("M12.5 3.25a.5.5 0 0 0-1 0L3 10.4V21h6.5v-6h5v6H21V10.4z")
    val search = icon("M10.5 3a7.5 7.5 0 0 1 5.96 12.06l4.24 4.24-1.4 1.4-4.24-4.24A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z")
    val library = icon("M4 3h2v18H4zM9 3h2v18H9zm4.6.6 1.9-.6 5.6 17.4-1.9.6z")
    val play = icon("M7 4.5v15l12.5-7.5z")
    val pause = icon("M6 4h4v16H6zm8 0h4v16h-4z")
    val next = icon("M5 4.5v15l10-7.5zM17 4h2v16h-2z")
    val prev = icon("M19 4.5v15L9 12zM5 4h2v16H5z")
    val down = icon("M12 15.6 4.7 8.3l1.4-1.4 5.9 5.9 5.9-5.9 1.4 1.4z")
    val shuffle = icon("M17 4l3 3-3 3V8h-2.2l-8 8H3v-2h3l8-8h3zM3 6h3.8l2.6 2.6-1.4 1.4L6 8H3zm12.6 7.4 1.4-1.4 1 1H17v-2l3 3-3 3v-2h-2.2z")
    val repeat = icon("M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2z")
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("ホーム", Icons.home), Search("検索", Icons.search), Library("ライブラリ", Icons.library)
}

/** アプリの画面（Compose）。データと再生は [bridge] 経由で裏の Web Player に任せる */
@Composable
fun NativeApp(bridge: Bridge) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var playerOpen by remember { mutableStateOf(false) }
    val player by bridge.player.collectAsState()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && tab != Tab.Home) { tab = Tab.Home }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (tab) {
            Tab.Home -> HomeScreen(bridge, onPlay = { scope.launch { runCatching { bridge.play(it.uri) } } })
            Tab.Search -> Placeholder("検索はこれから作ります")
            Tab.Library -> Placeholder("ライブラリはこれから作ります")
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (player.has) MiniPlayer(player, onOpen = { playerOpen = true }, onToggle = { scope.launch { runCatching { bridge.toggle() } } })
            TabBar(tab) { tab = it }
        }
        AnimatedVisibility(
            visible = playerOpen && player.has,
            enter = slideInVertically { it }, exit = slideOutVertically { it },
        ) {
            FullPlayer(bridge, player, onClose = { playerOpen = false })
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Sub, fontSize = 15.sp) }
}

@Composable
private fun HomeScreen(bridge: Bridge, onPlay: (Card) -> Unit) {
    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { bridge.home() }.onSuccess { feed = it }.onFailure { error = it.message }
    }
    val f = feed
    if (f == null) {
        Placeholder(if (error != null) "読み込めませんでした" else "読み込み中…")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp),
    ) {
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(8.dp)) }
        item { Chips() }
        if (f.shortcuts.isNotEmpty()) item { Shortcuts(f.shortcuts, onPlay) }
        items(f.shelves) { s -> ShelfRow(s, onPlay) }
    }
}

@Composable
private fun Chips() {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("すべて", "音楽", "ポッドキャスト").forEachIndexed { i, t ->
            Text(
                t, color = if (i == 0) Color.Black else Color.White, fontSize = 14.sp,
                modifier = Modifier.clip(CircleShape).background(if (i == 0) Accent else Card2).padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Shortcuts(items: List<Card>, onPlay: (Card) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Row(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(4.dp)).background(Card2).clickable { onPlay(c) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(c.image, null, Modifier.size(48.dp), contentScale = ContentScale.Crop)
                        Text(
                            c.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShelfRow(s: Shelf, onPlay: (Card) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        if (s.title.isNotEmpty()) Text(
            s.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(s.items, key = { it.uri }) { c ->
                Column(Modifier.width(150.dp).clickable { onPlay(c) }.padding(top = 8.dp)) {
                    AsyncImage(
                        c.image, null,
                        Modifier.size(150.dp).clip(if (c.round) CircleShape else RoundedCornerShape(4.dp)).background(Card2),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        c.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (c.subtitle.isNotEmpty()) Text(c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(p: PlayerState, onOpen: () -> Unit, onToggle: () -> Unit) {
    var now by remember { mutableLongStateOf(p.positionNow()) }
    LaunchedEffect(p) { while (true) { now = p.positionNow(); delay(500) } }
    Column(
        Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A3A3A)).clickable(onClick = onOpen),
    ) {
        Row(Modifier.height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(p.image, null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(p.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.artist, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(if (p.playing) Icons.pause else Icons.play, 40, onToggle)
        }
        val frac = if (p.dur > 0) (now.toFloat() / p.dur).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(Color.White))
        }
    }
}

@Composable
private fun IconButton(icon: ImageVector, size: Int, onClick: () -> Unit, tint: Color = Color.White) {
    Box(Modifier.size(size.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(icon), null, Modifier.size((size * 0.6f).dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
    }
}

@Composable
private fun TabBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x00121212), Color(0xEE121212), Color(0xFF121212))))
            .windowInsetsPadding(WindowInsets.navigationBars).padding(top = 10.dp, bottom = 6.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Column(
                Modifier.weight(1f).clickable { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(rememberVectorPainter(t.icon), null, Modifier.size(24.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (on) Color.White else Sub))
                Text(t.label, color = if (on) Color.White else Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun mmss(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun FullPlayer(bridge: Bridge, p: PlayerState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(p.positionNow()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p) { while (true) { if (dragging == null) now = p.positionNow(); delay(250) } }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4A4A4A), Bg)))
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Icons.down, 40, onClose)
        }
        Spacer(Modifier.weight(1f))
        AsyncImage(p.image, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(28.dp))
        Text(p.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.artist, color = Sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        val dur = p.dur.coerceAtLeast(1)
        Slider(
            value = dragging ?: (now.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { val v = dragging ?: 0f; dragging = null; now = (v * dur).toLong(); scope.launch { runCatching { bridge.seek((v * dur).toLong()) } } },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(((dragging ?: (now.toFloat() / dur)) * dur).toLong()), color = Sub, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(mmss(p.dur), color = Sub, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(Icons.shuffle, 44, { scope.launch { runCatching { bridge.shuffle(!p.shuffle) } } }, if (p.shuffle) Accent else Color.White)
            IconButton(Icons.prev, 52, { scope.launch { runCatching { bridge.prev() } } })
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable { scope.launch { runCatching { bridge.toggle() } } },
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(if (p.playing) Icons.pause else Icons.play), null, Modifier.size(30.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black))
            }
            IconButton(Icons.next, 52, { scope.launch { runCatching { bridge.next() } } })
            IconButton(Icons.repeat, 44, { scope.launch { runCatching { bridge.repeat((p.repeat + 1) % 3) } } }, if (p.repeat > 0) Accent else Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}
