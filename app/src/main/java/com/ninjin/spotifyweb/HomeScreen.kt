package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onPlay: (Card) -> Unit, onMenu: (MenuTarget) -> Unit = {}, onBrowse: () -> Unit = {}) {
    // 起動直後は前回のホームをすぐ出し、Web Player の準備ができたら新しい内容に差し替える
    var feed by remember { mutableStateOf(bridge.cachedHome()) }
    var chips by remember { mutableStateOf(feed?.chips ?: emptyList()) }
    var facet by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val feeds = remember { mutableMapOf<String, HomeFeed>() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(ready, facet) {
        // タブを切り替えたら一番上から。読み込むまでは前のタブの中身を出さない
        // 前回のホームが無い（初回ログイン直後）ときはリストがまだ画面に無く、scrollToItem が返ってこないので呼ばない
        if (feed != null) listState.scrollToItem(0)
        feeds[facet]?.let { feed = it; loading = false } ?: run { if (facet.isNotEmpty()) loading = true }
        if (ready) runCatching { bridge.home(facet) }.onSuccess { feeds[facet] = it; feed = it; loading = false; if (it.chips.isNotEmpty()) chips = it.chips }.onFailure { loading = false; if (feed == null) error = it.message }
    }
    val f = feed
    if (f == null) {
        if (error != null) Placeholder(tr("読み込めませんでした", "Couldn't load")) else Loading()
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 170.dp)) {
        // 上のアイコンとタブはスクロールしても上に残す（公式アプリと同じ）
        stickyHeader {
            Row(
                Modifier.fillMaxWidth().background(Bg).windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user, 32, onAvatar)
                Spacer(Modifier.width(8.dp))
                Chips(chips, facet) { facet = it }
            }
        }
        if (loading) { item { Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { LoadingDots() } }; return@LazyColumn }
        if (f.shortcuts.isNotEmpty()) item { Shortcuts(f.shortcuts, onPlay) }
        // フォロー中のページなど、見出しのない 1 件ずつのカードが並ぶときは「最新リリース」の見出し
        val podcastFollow = facet == "podcasts-following-chip"
        if (podcastFollow || f.shelves.firstOrNull()?.let { it.feed && it.title.isEmpty() } == true) item {
            Text(if (podcastFollow) tr("最新エピソード", "New episodes") else tr("最新リリース", "New releases"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        }
        // 公式アプリと同じ、フォローしている番組がないときの案内
        if (podcastFollow && f.emptyPodcasts) item {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF242424)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tr("フォローしているポッドキャストはありません", "No podcasts followed yet"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(tr("お気に入りをフォローして、最新情報を逃さずチェックしましょう。", "Follow your favorites so you never miss a new episode."), color = Sub, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Text(tr("ポッドキャストをブラウズ", "Browse podcasts"), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp).clip(CircleShape).background(Color.White).clickable { onBrowse() }.padding(horizontal = 22.dp, vertical = 11.dp))
            }
        }
        // ポッドキャストのタブの上：視聴番組の小さいタイルと「＋」
        if (f.shows.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(f.shows) { c -> AsyncImage(c.image, null, Modifier.size(100.dp).clip(RoundedCornerShape(6.dp)).background(Card2).clickable { onPlay(c) }, contentScale = ContentScale.Crop) }
                item { Box(Modifier.size(100.dp).clip(RoundedCornerShape(6.dp)).background(Card2).clickable { onBrowse() }, contentAlignment = Alignment.Center) { Image(rememberVectorPainter(Icons.close), null, Modifier.size(28.dp).rotate(45f), colorFilter = ColorFilter.tint(Sub)) } }
            }
        }
        items(f.shelves) { s -> if (s.feed) FeedCard(bridge, s, onPlay, onMenu) else ShelfRow(s, onPlay) }
    }
}

/** 上の「すべて／音楽／ポッドキャスト」。公式アプリと同じく全部並んだまま、選んだものの右に「フォロー中」がつながる */
@Composable
internal fun Chips(chips: List<Chip>, facet: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ChipText(tr("すべて", "All"), facet.isEmpty()) { onSelect("") } }
        items(chips) { c ->
            val selected = c.id == facet || c.sub.any { it.id == facet }
            if (!selected) ChipText(c.label, false) { onSelect(c.id) }
            else {
                // 「フォロー中」まで選ぶと、つながった丸ボタン全体が緑になる（公式アプリと同じ）
                val subOn = c.sub.any { it.id == facet }
                Row(Modifier.clip(CircleShape).background(if (subOn) Accent else Card2)) {
                    ChipText(c.label, true) { onSelect(if (facet == c.id) "" else c.id) }
                    // 「フォロー中」を選んだときは、そこだけ少し濃い緑（公式アプリと同じ）
                    c.sub.forEach { sc -> ChipText(sc.label, facet == sc.id, Color(Accent.red * 0.87f, Accent.green * 0.87f, Accent.blue * 0.87f)) { onSelect(if (facet == sc.id) c.id else sc.id) } }
                }
            }
        }
    }
}

@Composable
internal fun ChipText(t: String, on: Boolean, onColor: Color = Accent, onClick: () -> Unit) {
    Text(
        t, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape).background(if (on) onColor else Card2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
internal fun Shortcuts(items: List<Card>, onPlay: (Card) -> Unit) {
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

internal object FeedIcons {
    val mute = line("M3.5 9.5h3.5L12 5v14l-5-4.5H3.5zM15.5 9.5l4 5M19.5 9.5l-4 5", 1.6f)
}

/** 「〇日前」 */
internal fun ago(iso: String): String {
    val t = runCatching { java.time.LocalDate.parse(iso.take(10)) }.getOrNull() ?: return ""
    val d = java.time.temporal.ChronoUnit.DAYS.between(t, java.time.LocalDate.now())
    return when { d <= 0 -> tr("今日", "Today"); d < 7 -> tr("$d 日前", "${d}d ago"); d < 30 -> tr("${d / 7}週間前", "${d / 7}w ago"); d < 365 -> tr("${d / 30}か月前", "${d / 30}mo ago"); else -> tr("${d / 365}年前", "${d / 365}y ago") }
}

/** フォロー中やポッドキャストの、1 件ずつの大きなカード（公式アプリと同じく、ジャケットの色・曲の一覧・プレビュー・追加・再生） */
@Composable
internal fun FeedCard(bridge: Bridge, s: Shelf, onPlay: (Card) -> Unit, onMenu: (MenuTarget) -> Unit) {
    val c = s.items.first()
    val scope = rememberCoroutineScope()
    val isAlbum = c.uri.startsWith("spotify:album:")
    var page by remember(c.uri) { mutableStateOf<Page?>(null) }
    var saved by remember(c.uri) { mutableStateOf(false) }
    LaunchedEffect(c.uri) { if (isAlbum) runCatching { bridge.page(c.uri) }.getOrNull()?.let { page = it; saved = it.saved } }
    // ジャケットの濃い色をさらに少し暗くした色（公式アプリと同じ見え方）
    val bg = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrNull()?.let { Color(it.red * 0.62f, it.green * 0.62f, it.blue * 0.62f) } ?: Card2
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        if (s.title.isNotEmpty()) Text(s.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).clickable { onPlay(c) }.padding(16.dp)) {
            Row {
                AsyncImage(c.image, null, Modifier.size(88.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(start = 16.dp, top = 4.dp)) {
                    Text(if (isAlbum) c.subtitle else c.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = if (isAlbum) c.title + (page?.date?.let { ago(it) }?.takeIf { it.isNotEmpty() }?.let { " • $it" } ?: "") else c.subtitle
                    Text(sub, color = Color(0xB3FFFFFF), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Ico(Icons.more, 18, Color.White, 32) { onMenu(MenuTarget.OfPage(c.uri, c.title, c.subtitle, c.image, "", c.subtitle)) }
            }
            page?.tracks?.takeIf { it.isNotEmpty() }?.let { t ->
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White)); append(songs(t.size)); pop()
                        append(" • " + t.joinToString(" • ") { it.title })
                    },
                    color = Color(0xB3FFFFFF), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // 公式アプリと同じ、音を消したスピーカーのマーク付きの「プレビュー」（押すと再生）
                Row(
                    Modifier.clip(CircleShape).background(Color(0x22000000)).clickable { scope.launch { runCatching { bridge.play(c.uri) } } }.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(rememberVectorPainter(FeedIcons.mute), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color(0x66FFFFFF)))
                    Spacer(Modifier.width(8.dp))
                    Text(if (!isAlbum) tr("エピソードのプレビュー", "Episode preview") else if ((page?.tracks?.size ?: 1) <= 1 || c.kind == "single") tr("シングルをプレビュー", "Preview single") else tr("アルバムをプレビュー", "Preview album"), color = Color(0x80FFFFFF), fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                Ico(if (saved) Icons.check else Icons.add, 28, if (saved) Accent else Color.White, 44) { saved = !saved; scope.launch { runCatching { bridge.follow(c.uri, saved) } } }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White).clickable { scope.launch { runCatching { bridge.play(c.uri) } } }, contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(Icons.play), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.Black))
                }
            }
        }
    }
}

@Composable
internal fun ShelfRow(s: Shelf, onPlay: (Card) -> Unit) {
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
                    Text(c.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    if (c.subtitle.isNotEmpty()) Text(c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
            }
        }
    }
}
