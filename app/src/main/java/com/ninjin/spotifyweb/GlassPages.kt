package com.ninjin.spotifyweb

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/*
 * 新しいテーマ（Apple Music）の、ページ（アルバム・プレイリスト・アーティスト）と、上の帯
 */

/** 大きな見出しがスクロールで隠れたら、上のガラスの帯に小さく出す見出し（null なら帯を出さない） */
internal object GlassChrome {
    var smallTitle by mutableStateOf<String?>(null)
}

/** 大きな見出しの位置を見張り、隠れたら上の帯に名前を出す。リストの外へ流れて消えたときも隠れたとみなす */
@Composable
internal fun Modifier.reportTitle(title: String): Modifier {
    val limit = with(LocalDensity.current) { 56.dp.toPx() }
    DisposableEffect(title) { onDispose { GlassChrome.smallTitle = title } }
    return onGloballyPositioned { c ->
        val bottom = c.positionInRoot().y + c.size.height
        GlassChrome.smallTitle = if (bottom < limit + c.size.height / 2) title else null
    }
}

/** 上のガラスの帯（Apple Music と同じく、見出しが隠れると現れ、真ん中に小さく名前） */
@Composable
internal fun GlassTopBar(backdrop: Backdrop, title: String?, left: @Composable () -> Unit = {}, right: @Composable () -> Unit = {}) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (title != null) 1f else 0f, label = "bar")
    Box(Modifier.fillMaxWidth()) {
        if (alpha > 0f) Box(
            Modifier.matchParentSize().graphicsLayer { this.alpha = alpha }
                .glass(backdrop, androidx.compose.ui.graphics.RectangleShape, refract = false),
        )
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(96.dp)) { left() }
            Text(
                title ?: "", color = Fg.copy(alpha = alpha), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.End) { right() }
        }
    }
}

/** ガラスの丸いボタン（ページの上の戻る・追加・その他） */
@Composable
internal fun GlassCircleButton(backdrop: Backdrop, icon: ImageVector, tint: Color = Fg, size: Int = 20, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).glass(backdrop).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Image(rememberVectorPainter(icon), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint)) }
}

/** 赤い文字の「再生」「シャッフル」のボタン（Apple Music のページの真ん中の 2 つ） */
@Composable
private fun PillButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(CircleShape).background(Surface2).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Accent))
        Text(label, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Apple Music のページ：中央に大きなジャケット、名前、赤いアーティスト、「再生」「シャッフル」、細い線で区切った曲 */
@Composable
internal fun GlassPageScreen(bridge: Bridge, uri: String, playing: PlayerState, onBack: () -> Unit, onOpen: (Card) -> Unit, onMenu: (MenuTarget) -> Unit) {
    var page by remember(uri) { mutableStateOf(bridge.cachedPage(uri)) }
    var failed by remember(uri) { mutableStateOf(false) }
    var saved by remember(uri) { mutableStateOf(page?.saved ?: false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uri) { runCatching { bridge.page(uri) }.onSuccess { page = it; saved = it.saved }.onFailure { failed = true } }
    val list = rememberLazyListState()
    val bg = Bg
    val backdrop = rememberLayerBackdrop(onDraw = { drawRect(bg); drawContent() })
    val p = page
    // 見出し（ジャケットの下の名前）が上の帯に隠れたら、帯に名前を出す
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 1 || (list.firstVisibleItemIndex == 1 && list.firstVisibleItemScrollOffset > 40) } }
    val artistHeader = p?.kind == "artist" && p.header
    val collection = uri.contains(":collection")
    val playAll: (Boolean) -> Unit = { shuffle ->
        scope.launch {
            runCatching {
                bridge.shuffle(shuffle)
                if (p == null) return@runCatching
                if (p.kind == "artist") p.tracks.firstOrNull()?.let { bridge.play(it.uri) }
                else if (collection) p.tracks.firstOrNull()?.let { bridge.play(uri, it.uri) } else bridge.play(uri)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        if (p == null) {
            if (failed) Placeholder(tr("読み込めませんでした", "Couldn't load")) else Loading()
        } else LazyColumn(Modifier.fillMaxSize().layerBackdrop(backdrop), state = list, contentPadding = PaddingValues(bottom = 170.dp)) {
            if (artistHeader) item {
                // アーティスト：画面の幅いっぱいの写真に、左下へ大きく白い名前、右下に赤い再生ボタン
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
                    Text(p.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 90.dp, bottom = 20.dp))
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(16.dp).size(52.dp).clip(CircleShape).background(Accent).clickable { playAll(false) },
                        contentAlignment = Alignment.Center,
                    ) { Image(rememberVectorPainter(Icons.play), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White)) }
                }
            } else item {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (p.image.isNotEmpty()) AsyncImage(
                        p.image, null,
                        Modifier.fillMaxWidth(0.72f).aspectRatio(1f)
                            .graphicsLayer { shadowElevation = 18.dp.toPx(); shape = if (p.round) CircleShape else RoundedCornerShape(10.dp); clip = true }
                            .background(Card2),
                        contentScale = ContentScale.Crop,
                    ) else Box(Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))))
                }
            }
            if (!artistHeader) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.title, color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val who = p.artist.ifEmpty { p.subtitle.substringBefore(" • ") }
                    if (who.isNotEmpty()) Text(
                        who, color = Accent, fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp).let { m -> if (p.artistUri.isNotEmpty()) m.clickable { onOpen(Card(p.artistUri, p.artist, "", "", true)) } else m },
                    )
                    val meta = listOfNotNull(
                        p.subtitle.takeIf { p.artist.isNotEmpty() && it.isNotEmpty() },
                        p.date.takeIf { it.isNotEmpty() }?.take(4),
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, color = Sub, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (p.tracks.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton(Icons.play, tr("再生", "Play"), Modifier.weight(1f)) { playAll(false) }
                    PillButton(Icons.shuffle, tr("シャッフル", "Shuffle"), Modifier.weight(1f)) { playAll(true) }
                }
            }
            if (p.description.isNotEmpty()) item {
                Text(p.description, color = Sub, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp))
            }
            if (artistHeader && p.tracks.isNotEmpty()) item {
                Text(tr("トップソング", "Top Songs"), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp))
            }
            // アルバムは番号、プレイリストとアーティストはジャケット
            val album = p.kind == "album"
            itemsIndexed(p.tracks, key = { i, t -> "$i${t.uri}" }) { i, t ->
                GlassTrackRow(t, if (album) (i + 1).toString() else null, playing.uri == t.uri, playing.playing,
                    onMore = { onMenu(MenuTarget.OfTrack(t, if (collection) true else null)) }) {
                    scope.launch { runCatching { if (p.kind == "artist") bridge.play(t.uri) else bridge.play(uri, t.uri) } }
                }
            }
            if (p.tracks.isNotEmpty()) item {
                val mins = p.tracks.sumOf { it.dur } / 60000
                Text(
                    tr("${p.tracks.size}曲" + (if (mins > 0) "、${mins}分" else ""), "${p.tracks.size} songs" + (if (mins > 0) ", $mins minutes" else "")),
                    color = Sub, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp),
                )
            }
            items(p.shelves) { s -> GlassShelfRow(s, onOpen) }
        }
        // 上：ガラスの丸い戻る、右に追加とその他。下へスクロールすると帯になり、名前が出る
        GlassTopBar(
            backdrop, if (scrolled || (artistHeader && list.firstVisibleItemIndex > 0)) p?.title else null,
            left = { GlassCircleButton(backdrop, Icons.back, if (artistHeader && !scrolled) Color.White else Accent, onClick = onBack) },
            right = {
                if (p != null && !collection) {
                    GlassCircleButton(backdrop, if (saved) Icons.check else Icons.add, Accent) {
                        saved = !saved; scope.launch { runCatching { bridge.follow(uri, saved) } }
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassCircleButton(backdrop, Icons.more, Accent) {
                        onMenu(MenuTarget.OfPage(p.uri, p.title, p.artist.ifEmpty { p.subtitle }, p.image, p.artistUri, p.artist))
                    }
                }
            },
        )
    }
}

/** 曲の行（Apple Music と同じく、細い線で区切り、再生中の曲は赤い波の印） */
@Composable
private fun GlassTrackRow(t: Track, number: String?, current: Boolean, playing: Boolean, onMore: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(if (number != null) 48.dp else 60.dp), verticalAlignment = Alignment.CenterVertically) {
            if (number != null) Box(Modifier.width(30.dp)) {
                if (current) NowPlayingBars(playing) else Text(number, color = Sub, fontSize = 15.sp)
            } else {
                Box {
                    AsyncImage(t.image, null, Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(Card2), contentScale = ContentScale.Crop)
                    if (current) Box(Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { NowPlayingBars(playing, Color.White) }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (number == null && t.artist.isNotEmpty()) Text(t.artist, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ico(Icons.more, 18, Fg, 40, onClick = onMore)
        }
        // 線は文字の頭から右端まで（Apple Music と同じ）
        Box(Modifier.padding(start = if (number != null) 50.dp else 78.dp).fillMaxWidth().height(0.5.dp).background(Fg.copy(alpha = 0.12f)))
    }
}

/** 再生中の印（赤い 3 本の棒が上下する。止まっているときは止める） */
@Composable
internal fun NowPlayingBars(playing: Boolean, color: Color = Accent) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
    Row(Modifier.size(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val h by t.animateFloat(
                0.3f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween<Float>(380 + i * 120), androidx.compose.animation.core.RepeatMode.Reverse),
                label = "b$i",
            )
            Box(Modifier.weight(1f).fillMaxHeight(if (playing) h else 0.35f).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}


/** 見出し（Apple Music と同じく、太字の名前の右に「＞」） */
@Composable
internal fun GlassSectionTitle(title: String) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Image(rememberVectorPainter(GlassIcons.chevron), null, Modifier.padding(start = 4.dp).size(18.dp), colorFilter = ColorFilter.tint(Sub))
    }
}

/** 横に流れる棚（角の丸いジャケット、下に名前と説明。アーティストは丸） */
@Composable
internal fun GlassShelfRow(s: Shelf, onPlay: (Card) -> Unit) {
    if (s.items.isEmpty()) return
    if (s.title.isNotEmpty()) GlassSectionTitle(s.title)
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(s.items, key = { it.uri }) { c ->
            Column(Modifier.width(165.dp).clickable(remember { MutableInteractionSource() }, null) { onPlay(c) }) {
                AsyncImage(c.image, null, Modifier.size(165.dp).clip(if (c.round) CircleShape else RoundedCornerShape(10.dp)).background(Card2), contentScale = ContentScale.Crop)
                Text(c.title, color = Fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (c.round) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (c.subtitle.isNotEmpty() && !c.round) Text(c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 「あなたへのおすすめ」：大きな縦長のカード（上にジャケット、下にジャケットの色の帯と白い名前） */
@Composable
internal fun GlassPicks(items: List<Card>, onPlay: (Card) -> Unit) {
    GlassSectionTitle(tr("あなたへのおすすめ", "Top Picks for You"))
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.uri }) { c ->
            val color = rememberArtColor(c.image)
            // Apple Music のトップピック：カードの上に小さな説明、縦長のカードはジャケットが下の色帯へなめらかに溶ける
            Column(Modifier.width(250.dp)) {
                Text(c.subtitle.ifEmpty { " " }, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                Box(
                    Modifier.fillMaxWidth().height(330.dp).graphicsLayer { shadowElevation = 6.dp.toPx(); shape = RoundedCornerShape(16.dp); clip = true }
                        .background(color).clickable { onPlay(c) },
                ) {
                    AsyncImage(c.image, null, Modifier.fillMaxWidth().aspectRatio(1f).background(Card2), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxWidth().padding(top = 170.dp).height(80.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, color))))
                    Text(c.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, lineHeight = 21.sp, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp))
                }
            }
        }
    }
}

/** メニューの項目をまとめる角の丸いカード（Apple のメニューと同じ。Classic はそのまま並べる） */
@Composable
internal fun MenuGroup(content: @Composable () -> Unit) {
    if (!Theme.glass) { Column { content() }; return }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (LocalLight.current) Color.White else Surface2)) { content() }
}
