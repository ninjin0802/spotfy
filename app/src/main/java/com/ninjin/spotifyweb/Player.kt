package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 公式アプリと同じミニプレーヤー：ジャケットの色、曲名（長いと流れる）／アーティスト、デバイス・追加・再生、下に細い進み具合 */
@Composable
internal fun MiniPlayer(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onOpen: () -> Unit, onDevices: () -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(p.positionNow()) }
    LaunchedEffect(p) { while (true) { now = p.positionNow(); delay(500) } }
    Column(
        Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(tint).clickable(onClick = onOpen),
    ) {
        Row(Modifier.height(56.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(p.image, null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                // 1行目は「曲名 • アーティスト」（長いと流れる）、2行目は再生先（ほかの機器・イヤホン・このスマホ）
                Text(p.title + " • " + p.artist, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                val out = if (p.device.isEmpty()) host.outputName() else ""
                val onOther = p.device.isNotEmpty() || out.isNotEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        rememberVectorPainter(if (p.device.isNotEmpty()) DevIcons.of(p.deviceType) else if (out.isNotEmpty()) DevIcons.headphones else DevIcons.phone),
                        null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(if (onOther) Accent else Color(0xCCFFFFFF)),
                    )
                    Text(
                        p.device.ifEmpty { out.ifEmpty { tr("このスマホ", "This phone") } }, color = if (onOther) Accent else Color(0xCCFFFFFF), fontSize = 13.sp,
                        fontWeight = if (onOther) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            val devIcon = when { p.device.isNotEmpty() -> DevIcons.of(p.deviceType); host.outputName().isNotEmpty() -> DevIcons.headphones; else -> DevIcons.phone }
            Ico(devIcon, 22, if (p.device.isNotEmpty() || host.outputName().isNotEmpty()) Accent else Color.White, onClick = onDevices)
            Ico(if (p.liked) Icons.check else Icons.add, 24, if (p.liked) Accent else Color.White) { scope.launch { runCatching { bridge.like(p.uri, !p.liked) } } }
            Ico(if (p.playing) Icons.pause else Icons.play, 24) { scope.launch { runCatching { bridge.toggle() } } }
        }
        val frac = if (p.dur > 0) (now.toFloat() / p.dur).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Color(0x44FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
        }
    }
}

/** 公式アプリと同じ全画面プレーヤー */
@Composable
internal fun FullPlayer(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onClose: () -> Unit, onDevices: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit, onOpen: (Card) -> Unit = {}) {
    var lyricsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    var lyrics by remember(p.uri) { mutableStateOf<Bridge.Lyrics?>(null) }
    LaunchedEffect(p.uri) { lyrics = null; if (p.uri.startsWith("spotify:track:")) lyrics = runCatching { bridge.lyrics(p.uri, p.image) }.getOrNull() }
    // 公式アプリと同じく、プレーヤーの下にスクロールすると歌詞・アーティストについて・次に再生が続く
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        val h = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.height(h).fillMaxWidth()) { PlayerMain(bridge, host, p, tint, onClose, onDevices, onQueue, onMenu) }
            PlayerExtras(bridge, p, tint, lyrics, onLyrics = { lyricsOpen = true }, onOpen = onOpen)
        }
        AnimatedVisibility(lyricsOpen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            LyricsScreen(p, lyrics, tint) { lyricsOpen = false }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerMain(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onClose: () -> Unit, onDevices: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(p.positionNow()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p) { while (true) { if (dragging == null) now = p.positionNow(); delay(250) } }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.85f).compositeOver(Bg), Bg)))
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
    ) {
        // 上：閉じる／「プレイリストから再生中」と名前／その他
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.down, 26, onClick = onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.contextLabel, color = Color.White, fontSize = 11.sp, maxLines = 1)
                if (p.contextName.isNotEmpty()) Text(p.contextName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ico(Icons.more, 22, onClick = onMenu)
        }
        Spacer(Modifier.weight(1f))
        AsyncImage(p.image, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(p.artist, color = Color(0xCCFFFFFF), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ico(if (p.liked) Icons.check else Icons.add, 28, if (p.liked) Accent else Color.White, 48) { scope.launch { runCatching { bridge.like(p.uri, !p.liked) } } }
        }
        Spacer(Modifier.height(10.dp))
        val dur = p.dur.coerceAtLeast(1)
        Slider(
            value = dragging ?: (now.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { val v = dragging ?: 0f; dragging = null; now = (v * dur).toLong(); scope.launch { runCatching { bridge.seek((v * dur).toLong()) } } },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x4DFFFFFF)),
            thumb = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White)) },
            track = { st ->
                BoxWithConstraints(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x4DFFFFFF))) {
                    Box(Modifier.fillMaxHeight().width(maxWidth * st.value).background(Color.White))
                }
            },
            modifier = Modifier.height(24.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(((dragging ?: (now.toFloat() / dur)) * dur).toLong()), color = Color(0xB3FFFFFF), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(mmss(p.dur), color = Color(0xB3FFFFFF), fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Ico(Icons.shuffle, 26, if (p.shuffle) Accent else Color.White, 48) { scope.launch { runCatching { bridge.shuffle(!p.shuffle) } } }
            Ico(Icons.prev, 34, box = 56) { scope.launch { runCatching { bridge.prev() } } }
            Box(
                Modifier.size(66.dp).clip(CircleShape).background(Color.White).clickable { scope.launch { runCatching { bridge.toggle() } } },
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(if (p.playing) Icons.pause else Icons.play), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(Color.Black))
            }
            Ico(Icons.next, 34, box = 56) { scope.launch { runCatching { bridge.next() } } }
            Box(contentAlignment = Alignment.Center) {
                Ico(Icons.repeat, 26, if (p.repeat > 0) Accent else Color.White, 48) { scope.launch { runCatching { bridge.repeat((p.repeat + 1) % 3) } } }
                if (p.repeat == 2) Text("1", color = Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-1).dp))
            }
        }
        // 下：デバイス／共有・キュー
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val out = if (p.device.isEmpty()) host.outputName() else ""
            Row(Modifier.weight(1f).clip(CircleShape).clickable(onClick = onDevices).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val on = p.device.isNotEmpty() || out.isNotEmpty()
                Image(rememberVectorPainter(if (p.device.isNotEmpty()) DevIcons.of(p.deviceType) else if (out.isNotEmpty()) DevIcons.headphones else DevIcons.phone), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (on) Accent else Color.White))
                if (on) Text(p.device.ifEmpty { out }, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
            Ico(Icons.share, 22) { host.share("https://open.spotify.com/track/" + p.uri.substringAfterLast(':'), p.title) }
            Spacer(Modifier.width(8.dp))
            Ico(Icons.queue, 22, onClick = onQueue)
        }
    }
}

@Composable
internal fun lyricsLine(l: Bridge.Lyrics?, posMs: Long): Int {
    if (l == null || !l.synced) return -1
    var i = -1
    for (k in l.lines.indices) if (l.lines[k].first <= posMs) i = k else break
    return i
}

internal fun lyricsColor(l: Bridge.Lyrics?, tint: Color): Color =
    if (l != null && l.bg != 0) Color(l.bg or 0xFF000000.toInt()) else tint

/** プレーヤーの下に続くカード（歌詞・アーティストについて・次に再生） */
@Composable
internal fun PlayerExtras(bridge: Bridge, p: PlayerState, tint: Color, lyrics: Bridge.Lyrics?, onLyrics: () -> Unit, onOpen: (Card) -> Unit) {
    val scope = rememberCoroutineScope()
    var about by remember(p.artistUri) { mutableStateOf<Bridge.About?>(null) }
    var following by remember(p.artistUri) { mutableStateOf(false) }
    LaunchedEffect(p.artistUri) { if (p.artistUri.isNotEmpty()) runCatching { bridge.about(p.artistUri) }.getOrNull()?.let { about = it; following = it.saved } }
    var next by remember(p.uri) { mutableStateOf<Track?>(null) }
    LaunchedEffect(p.uri) { next = runCatching { bridge.nextUp() }.getOrNull() }
    var now by remember { mutableLongStateOf(p.positionNow()) }
    LaunchedEffect(p) { while (true) { now = p.positionNow(); delay(300) } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 歌詞
        if (lyrics != null && lyrics.lines.isNotEmpty()) {
            val cur = lyricsLine(lyrics, now)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(lyricsColor(lyrics, tint)).clickable(onClick = onLyrics).padding(16.dp)) {
                Text(tr("歌詞", "Lyrics"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                val start = (cur - 1).coerceAtLeast(0)
                lyrics.lines.drop(start).take(6).forEachIndexed { k, l ->
                    val idx = start + k
                    Text(
                        l.second, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
                        color = if (!lyrics.synced || idx == cur) Color.White else if (idx < cur) Color(0xB3FFFFFF) else Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
        // アーティストについて
        about?.let { a ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF242424)).clickable { onOpen(Card(a.uri, a.name, "", a.image, true)) }) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    AsyncImage(a.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Text(tr("アーティストについて", "About the artist"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (a.listeners > 0) Text(
                                if (isJapanese) (if (a.listeners >= 10000) "%.1f万".format(a.listeners / 10000.0) else "%,d".format(a.listeners)) + "人の月間リスナー"
                                else "%,d monthly listeners".format(a.listeners),
                                color = Sub, fontSize = 13.sp,
                            )
                        }
                        Text(
                            if (following) tr("フォロー中", "Following") else tr("フォローする", "Follow"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).clickable {
                                following = !following; scope.launch { runCatching { bridge.follow(a.uri, following) } }
                            }.padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                    if (a.bio.isNotEmpty()) Text(a.bio, color = Sub, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        // 次に再生
        next?.let { t ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF242424)).padding(16.dp)) {
                Text(tr("次に再生", "Next in queue"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(t.image, null, Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Card2), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(t.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** 歌詞の全画面（再生に合わせて今の行が白くなり、自動で送られる） */
@Composable
internal fun LyricsScreen(p: PlayerState, lyrics: Bridge.Lyrics?, tint: Color, onClose: () -> Unit) {
    var now by remember { mutableLongStateOf(p.positionNow()) }
    LaunchedEffect(p) { while (true) { now = p.positionNow(); delay(200) } }
    val cur = lyricsLine(lyrics, now)
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(cur) { if (cur > 0) list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize().background(lyricsColor(lyrics, tint)).clickable(remember { MutableInteractionSource() }, null) {}
        .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.down, 26, onClick = onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.artist, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(40.dp))
        }
        LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)) {
            val lines = lyrics?.lines ?: emptyList()
            items(lines.size) { i ->
                Text(
                    lines[i].second, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
                    color = if (lyrics?.synced != true || i == cur) Color.White else if (i < cur) Color(0xB3FFFFFF) else Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            if (lines.isEmpty()) item { Text(tr("この曲の歌詞はありません", "No lyrics for this song"), color = Color.White, fontSize = 16.sp) }
        }
    }
}
