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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
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
internal fun isPage(uri: String) = uri.split(':').getOrNull(1) in setOf("album", "playlist", "artist", "collection", "user", "page", "genre") || uri.contains(":collection")

/** アルバム・プレイリスト・アーティスト・お気に入りの曲のページ */
@Composable
internal fun PageScreen(bridge: Bridge, uri: String, playing: PlayerState, onBack: () -> Unit, onOpen: (Card) -> Unit, onMenu: (MenuTarget) -> Unit) {
    var page by remember(uri) { mutableStateOf(bridge.cachedPage(uri)) }
    var failed by remember(uri) { mutableStateOf(false) }
    var saved by remember(uri) { mutableStateOf(page?.saved ?: false) }
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
            if (p.kind == "genre") item {
                val c = runCatching { Color(android.graphics.Color.parseColor(p.subtitle)) }.getOrDefault(Card2)
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(c, Bg))).windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 64.dp, bottom = 16.dp)) {
                    Text2(p.title, 32, FontWeight.Black)
                }
            } else item {
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
            if (p.kind != "genre") item {
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
                        if (!p.uri.contains(":collection")) Ico(Icons.more, 22, Sub, 44) {
                            onMenu(MenuTarget.OfPage(p.uri, p.title, p.artist.ifEmpty { p.subtitle }, p.image, p.artistUri, p.artist))
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
                TrackRow(t, if (p.kind == "artist") (i + 1).toString() else null, playing.uri == t.uri, onMore = { onMenu(MenuTarget.OfTrack(t, if (p.uri.contains(":collection")) true else null)) }) {
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
internal fun TrackRow(t: Track, number: String?, current: Boolean, onMore: () -> Unit, onClick: () -> Unit) {
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
        Ico(Icons.more, 18, Sub, 36, onClick = onMore)
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

/** 検索：ジャンルのタイル、押すと検索画面（最近の検索・入力に合わせた結果・種類の絞り込み） */
@Composable
internal fun SearchScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onOpen: (Card) -> Unit) {
    var tiles by remember { mutableStateOf(bridge.cachedBrowse()) }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(ready) { if (ready) runCatching { bridge.browse() }.onSuccess { tiles = it } }
    androidx.activity.compose.BackHandler(enabled = typing) { typing = false }
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 170.dp)) {
        item {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(user, 32, onAvatar)
                Text2("検索", 24, FontWeight.Bold, Modifier.padding(start = 12.dp))
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).clickable { typing = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(rememberVectorPainter(Icons.search), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.Black))
                Text("何を聴きたいですか？", color = Color(0xFF6A6A6A), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
        }
        if (tiles.isNotEmpty()) item { Spacer(Modifier.height(16.dp)) }
        items(tiles.chunked(2)) { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t -> GenreTile(t, Modifier.weight(1f)) { onOpen(t) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (typing) SearchTyping(bridge, onClose = { typing = false }, onOpen = onOpen)
}

@Composable
private fun GenreTile(t: Card, m: Modifier, onClick: () -> Unit) {
    val bg = runCatching { Color(android.graphics.Color.parseColor(t.subtitle)) }.getOrDefault(Card2)
    // 公式アプリと同じ、横長で低いタイル（右下にジャケットを斜めに）
    Box(m.height(46.dp).clip(RoundedCornerShape(4.dp)).background(bg).clickable(onClick = onClick)) {
        Text(t.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 15.sp, modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 44.dp))
        AsyncImage(
            t.image, null,
            Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 6.dp).size(42.dp).rotate(25f).clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}


private val FILTERS = listOf("" to "すべて", "track" to "曲", "artist" to "アーティスト", "playlist" to "プレイリスト", "album" to "アルバム", "podcast" to "ポッドキャスト")

/** 検索の入力画面（公式アプリと同じく、検索欄を押すと開く） */
@Composable
private fun SearchTyping(bridge: Bridge, onClose: () -> Unit, onOpen: (Card) -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Card>>(emptyList()) }
    var recent by remember { mutableStateOf<List<Card>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val req = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { req.requestFocus(); recent = runCatching { bridge.recent() }.getOrDefault(emptyList()) }
    LaunchedEffect(q) {
        if (q.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(220)
        busy = true
        runCatching { bridge.search(q.trim()) }.onSuccess { results = it }
        busy = false
    }
    fun pick(c: Card) { focus.clearFocus(); scope.launch { runCatching { bridge.saveRecent(c.uri) } }; onOpen(c) }
    Column(Modifier.fillMaxSize().background(Bg).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.back, 22, onClick = onClose)
            Box(Modifier.weight(1f).padding(start = 4.dp)) {
                if (q.isEmpty()) Text("何を聴きたいですか？", color = Sub, fontSize = 16.sp)
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth().focusRequester(req),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                )
            }
            if (q.isNotEmpty()) Ico(Icons.close, 20, onClick = { q = "" })
        }
        if (q.isBlank()) {
            LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
                if (recent.isNotEmpty()) item { Text2("最近の検索", 16, FontWeight.Bold, Modifier.padding(16.dp)) }
                items(recent, key = { it.uri }) { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ResultRow(c) { pick(c) } }
                        Ico(Icons.close, 18, Sub, 44) { recent = recent - c; scope.launch { runCatching { bridge.removeRecent(c.uri) } } }
                    }
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FILTERS) { (k, label) ->
                    val on = filter == k
                    Text(
                        label, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).background(if (on) Accent else Card2).clickable { filter = k }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            val shown = if (filter.isEmpty()) results else results.filter { it.kind == filter }
            LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
                items(shown, key = { it.uri }) { c -> ResultRow(c) { pick(c) } }
                if (shown.isEmpty() && !busy) item { Text("「$q」に一致するものは見つかりませんでした", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

/** マイライブラリ（公式アプリと同じ：絞り込み・並べ替え・リスト／グリッド、ピン留め） */
@Composable
internal fun LibraryScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onOpen: (Card) -> Unit, onCreate: () -> Unit) {
    var items by remember { mutableStateOf(bridge.cachedLibrary()) }
    var query by remember { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = query != null) { query = null }
    LaunchedEffect(bridge.libraryVersion.value) { if (ready && bridge.libraryVersion.value > 0) runCatching { bridge.library() }.onSuccess { items = it } }
    var filter by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(0) }
    LaunchedEffect(ready) { if (ready) runCatching { bridge.library() }.onSuccess { items = it } }
    val liked = Card("spotify:collection:tracks", "お気に入りの曲", "プレイリスト", "", false, "liked", true)
    val base = listOf(liked) + items.filter { !it.uri.contains(":collection") && (it.kind != "liked-songs") }
    val filtered = base.filter {
        filter.isEmpty() || it.kind == filter || (filter == "playlist" && (it.kind == "liked" || it.kind == "your-episodes")) ||
            (filter == "show" && (it.kind == "show" || it.kind == "your-episodes"))
    }
        .filter { query.isNullOrBlank() || it.title.contains(query!!, ignoreCase = true) || it.subtitle.contains(query!!, ignoreCase = true) }
    val sorted = when (sort) { 1 -> filtered.sortedBy { it.title.lowercase() }; 2 -> filtered.sortedBy { it.subtitle.substringAfter(" • ", "") }; else -> filtered }
        .sortedByDescending { it.pinned }
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 170.dp)) {
        item {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (query == null) {
                    Avatar(user, 32, onAvatar)
                    Text2("マイライブラリ", 24, FontWeight.Bold, Modifier.padding(start = 12.dp).weight(1f))
                    Ico(Icons.search, 24) { query = "" }
                    Ico(Icons.add, 26, onClick = onCreate)
                } else {
                    // マイライブラリ内を検索
                    val req = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(Unit) { req.requestFocus() }
                    Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(6.dp)).background(Card2).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(rememberVectorPainter(Icons.search), null, Modifier.size(18.dp))
                        Box(Modifier.weight(1f).padding(start = 8.dp)) {
                            if (query!!.isEmpty()) Text("マイライブラリを検索", color = Sub, fontSize = 14.sp)
                            BasicTextField(query!!, { query = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth().focusRequester(req))
                        }
                    }
                    Text("キャンセル", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { query = null }.padding(12.dp))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (filter.isNotEmpty()) Ico(Icons.close, 16, Color.White, 34) { filter = "" }
                listOf("playlist" to "プレイリスト", "show" to "ポッドキャスト", "album" to "アルバム", "artist" to "アーティスト").forEach { (k, label) ->
                    if (filter.isEmpty() || filter == k) Text(
                        label, color = if (filter == k) Color.Black else Color.White, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).background(if (filter == k) Accent else Card2).clickable { filter = if (filter == k) "" else k }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        item {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2A)))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).clickable { sort = (sort + 1) % 3 }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(rememberVectorPainter(LibIcons.sort), null, Modifier.size(18.dp))
                    Text(listOf("再生履歴", "アルファベット順", "クリエーター")[sort], color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Ico(if (grid) LibIcons.list else LibIcons.grid, 20) { grid = !grid }
            }
        }
        if (grid) {
            items(sorted.chunked(3)) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        Column(Modifier.weight(1f).clickable { onOpen(c) }) {
                            LibCover(c, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(c.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(c.subtitle.substringBefore(" • "), color = Sub, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else items(sorted, key = { it.uri }) { c ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(c) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LibCover(c, Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(c.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.pinned) Image(rememberVectorPainter(LibIcons.pin), null, Modifier.size(13.dp).padding(end = 2.dp), colorFilter = ColorFilter.tint(Accent))
                        Text(if (c.kind == "liked") "プレイリスト • ${user?.name ?: ""}" else if (c.kind == "your-episodes") "プレイリスト • 保存済み＆ダウンロード済みのエピソード" else c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = if (c.pinned) 4.dp else 0.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibCover(c: Card, m: Modifier) {
    if (c.kind == "your-episodes") Box(m.clip(RoundedCornerShape(4.dp)).background(Color(0xFF006450)), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(LibIcons.bookmark), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Accent))
    } else if (c.kind == "liked") Box(m.clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))), contentAlignment = Alignment.Center) {
        Text("♥", color = Color.White, fontSize = 24.sp)
    } else AsyncImage(c.image, null, m.clip(if (c.round) CircleShape else RoundedCornerShape(4.dp)).background(Card2), contentScale = ContentScale.Crop)
}

private object LibIcons {
    val sort = line("M7 4v16M3.5 7.5 7 4l3.5 3.5M17 20V4M13.5 16.5 17 20l3.5-3.5", 1.8f)
    val grid = line("M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z", 1.8f)
    val list = line("M4 6h16M4 12h16M4 18h16", 1.8f)
    val bookmark = icon("M6 3h12v18l-6-4.5L6 21z")
    val pin = icon("M14.5 2.5l7 7-2.2.7-4.6 4.6.4 4.4-1.6 1.6-3.9-3.9L4 22.5 2.5 21l5.6-5.6-3.9-3.9 1.6-1.6 4.4.4 4.6-4.6z")
}
