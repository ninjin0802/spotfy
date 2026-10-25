package com.ninjin.spotifyweb

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** カードを押したとき：曲なら再生、それ以外はそのページを開く */
internal fun isPage(uri: String) = uri.split(':').getOrNull(1) in setOf("album", "playlist", "artist", "collection", "user") || uri.contains(":collection")

/** アルバム・プレイリスト・アーティスト・お気に入りの曲のページ */
@Composable
internal fun PageScreen(bridge: Bridge, uri: String, playing: PlayerState, onBack: () -> Unit, onOpen: (Card) -> Unit) {
    var page by remember(uri) { mutableStateOf<Page?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var saved by remember(uri) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uri) { runCatching { bridge.page(uri) }.onSuccess { page = it; saved = it.saved }.onFailure { failed = true } }
    val tint = rememberArtColorPublic(page?.image ?: "")
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 400 } }
    Box(Modifier.fillMaxSize().background(Bg)) {
        val p = page
        if (p == null) {
            Placeholder(if (failed) "読み込めませんでした" else "読み込み中…")
        } else LazyColumn(state = list, contentPadding = PaddingValues(bottom = 170.dp)) {
            item {
                if (p.kind == "artist" && p.header) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.25f)) {
                        AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC121212)))))
                        Text2(p.title, 44, FontWeight.Black, Modifier.align(Alignment.BottomStart).padding(16.dp))
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint, Bg))).windowInsetsPadding(WindowInsets.statusBars).padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (p.image.isNotEmpty()) AsyncImage(
                            p.image, null,
                            Modifier.size(240.dp).clip(if (p.round) CircleShape else RoundedCornerShape(4.dp)).background(Card2),
                            contentScale = ContentScale.Crop,
                        ) else Box(Modifier.size(240.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))))
                    }
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                        Text2(p.title, 24, FontWeight.Bold)
                        if (p.description.isNotEmpty()) Text(p.description, color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        if (p.artist.isNotEmpty()) Text(
                            p.artist, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)
                                .let { m -> if (p.artistUri.isNotEmpty()) m.clickable { onOpen(Card(p.artistUri, p.artist, "", "", true)) } else m },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (p.subtitle.isNotEmpty()) Text(p.subtitle, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.kind == "artist") Text(
                            if (saved) "フォロー中" else "フォローする", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Color.Transparent).clickable {
                                saved = !saved; scope.launch { runCatching { bridge.follow(p.uri, saved) } }
                            }.padding(1.dp).clip(CircleShape).background(Color(0x22FFFFFF)).padding(horizontal = 16.dp, vertical = 7.dp),
                        ) else if (!p.uri.contains(":collection")) Ico(if (saved) Icons.check else Icons.add, 28, if (saved) Accent else Sub, 44) {
                            saved = !saved; scope.launch { runCatching { bridge.follow(p.uri, saved) } }
                        }
                        Spacer(Modifier.weight(1f))
                        val isThis = playing.has && playing.playing && (playing.contextUri == p.uri)
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(Accent).clickable {
                                scope.launch { runCatching { if (isThis) bridge.toggle() else if (p.uri.contains(":collection")) p.tracks.firstOrNull()?.let { bridge.play(p.uri, it.uri) } else bridge.play(p.uri) } }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(rememberVectorPainter(if (isThis) Icons.pause else Icons.play), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.Black))
                        }
                    }
                    if (p.kind == "artist" && p.tracks.isNotEmpty()) Text2("人気曲", 20, FontWeight.Bold, Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
            }
            itemsIndexed(p.tracks, key = { i, t -> "$i${t.uri}" }) { i, t ->
                TrackRow(t, if (p.kind == "artist") (i + 1).toString() else null, playing.uri == t.uri) {
                    scope.launch { runCatching { if (p.kind == "artist") bridge.play(t.uri) else bridge.play(p.uri, t.uri) } }
                }
            }
            items(p.shelves) { s -> ShelfRow(s, onOpen) }
        }
        // 上の戻るボタン（スクロールしたら見出しの帯の中に名前も出す）
        Row(
            Modifier.fillMaxWidth().background(if (scrolled) tint.copy(alpha = 1f) else Color.Transparent)
                .windowInsetsPadding(WindowInsets.statusBars).height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (scrolled) Color.Transparent else Color(0x66000000)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Image(rememberVectorPainter(Icons.back), null, Modifier.size(22.dp))
            }
            if (scrolled) Text(page?.title ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Text2(t: String, size: Int, w: FontWeight, m: Modifier = Modifier) {
    Text(t, color = Color.White, fontSize = size.sp, fontWeight = w, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (size * 1.15).sp, modifier = m)
}

@Composable
private fun Text(t: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, fontWeight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) =
    androidx.compose.material3.Text(t, color = color, fontSize = fontSize, modifier = modifier, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow, lineHeight = lineHeight)

@Composable
internal fun TrackRow(t: Track, number: String?, current: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (number != null) Text(number, color = Sub, fontSize = 15.sp, modifier = Modifier.width(28.dp))
        if (t.image.isNotEmpty()) {
            AsyncImage(t.image, null, Modifier.size(48.dp).clip(RoundedCornerShape(2.dp)).background(Card2), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, color = if (current) Accent else Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Ico(Icons.more, 18, Sub, 36) {}
    }
}

@Composable
private fun ResultRow(c: Card, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(c.image, null, Modifier.size(56.dp).clip(if (c.round) CircleShape else RoundedCornerShape(2.dp)).background(Card2), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(c.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.subtitle.isNotEmpty()) Text(c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 検索：入力に合わせてその場で結果を出す */
@Composable
internal fun SearchScreen(bridge: Bridge, onOpen: (Card) -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Card>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(q) {
        if (q.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        busy = true
        runCatching { bridge.search(q.trim()) }.onSuccess { results = it }
        busy = false
    }
    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Text2("検索", 24, FontWeight.Bold, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(rememberVectorPainter(Icons.search), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.Black))
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (q.isEmpty()) Text("何を聴きたいですか？", color = Color(0xFF6A6A6A), fontSize = 16.sp)
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.Black), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (q.isNotEmpty()) Ico(Icons.close, 20, Color.Black, 36) { q = "" }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), contentPadding = PaddingValues(bottom = 170.dp)) {
            items(results, key = { it.uri }) { c -> ResultRow(c) { focus.clearFocus(); onOpen(c) } }
            if (q.isNotBlank() && results.isEmpty() && !busy) item { Text("「$q」に一致するものは見つかりませんでした", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
        }
    }
}

/** マイライブラリ */
@Composable
internal fun LibraryScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onOpen: (Card) -> Unit) {
    var items by remember { mutableStateOf(bridge.cachedLibrary()) }
    LaunchedEffect(ready) { if (ready) runCatching { bridge.library() }.onSuccess { items = it } }
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 170.dp)) {
        item {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(user, 32, onAvatar)
                Text2("マイライブラリ", 24, FontWeight.Bold, Modifier.padding(start = 12.dp))
            }
        }
        item {
            ResultRow(Card("spotify:collection:tracks", "お気に入りの曲", "プレイリスト", "", false)) { onOpen(Card("spotify:collection:tracks", "お気に入りの曲", "", "", false)) }
        }
        items(items.filter { !it.uri.contains(":collection") }, key = { it.uri }) { c -> ResultRow(c) { onOpen(c) } }
    }
}
