package com.ninjin.spotifyweb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** サイドメニューから開く画面の共通の見出し（戻る＋タイトル） */
@Composable
private fun DrawerHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(Icons.back, 22, onClick = onBack)
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
    }
}

/** 「あなたへのおすすめ新着」：フォロー中のアーティストのリリースと、フォロー中の番組の新しいエピソード */
@Composable
internal fun WhatsNewScreen(bridge: Bridge, onBack: () -> Unit, onPlay: (Card) -> Unit, onMenu: (MenuTarget) -> Unit) {
    var type by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Card>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(type) {
        items = null; failed = false
        runCatching { bridge.whatsNew(type) }.onSuccess { items = it }.onFailure { failed = true }
    }
    Column(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        DrawerHeader(tr("あなたへのおすすめ新着", "What's new"), onBack)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to tr("すべて", "All"), "music" to tr("音楽", "Music"), "podcast" to tr("ポッドキャスト", "Podcasts")).forEach { (k, label) ->
                FilterChip(label, type == k) { type = k }
            }
        }
        val list = items
        when {
            failed -> Placeholder(tr("読み込めませんでした", "Couldn't load"))
            list == null -> Loading()
            list.isEmpty() -> Placeholder(tr("フォロー中のアーティストや番組の新着はまだありません", "No new releases from artists and shows you follow yet"))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 170.dp)) {
                items(list, key = { it.uri }) { c -> FeedCard(bridge, Shelf("", listOf(c), true), onPlay, onMenu) }
            }
        }
    }
}

/** 「最近」：最近再生したアルバム・プレイリスト・アーティストを、日ごとにまとめて新しい順に */
@Composable
internal fun RecentsScreen(bridge: Bridge, onBack: () -> Unit, onOpen: (Card) -> Unit) {
    var recents by remember { mutableStateOf(bridge.cachedRecents()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { bridge.recents() }.onSuccess { recents = it }.onFailure { if (recents == null) failed = true } }
    Column(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        DrawerHeader(tr("最近", "Recents"), onBack)
        val list = recents
        when {
            failed -> Placeholder(tr("読み込めませんでした", "Couldn't load"))
            list == null -> Loading()
            list.isEmpty() -> Placeholder(tr("最近再生したものはありません", "Nothing played recently"))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 170.dp)) {
                list.groupBy { dayLabel(it.at) }.forEach { (day, entries) ->
                    item(key = "day-$day") { Text(day, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)) }
                    items(entries, key = { it.uri }) { RecentRow(it, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(r: Bridge.Recent, onOpen: (Card) -> Unit) {
    val round = r.round || r.uri.startsWith("spotify:artist:")
    val liked = r.uri.contains(":collection")
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(Card(r.uri, r.title, r.artist, r.image, round)) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (round) CircleShape else RoundedCornerShape(4.dp)
        if (liked) Box(Modifier.size(56.dp).clip(shape).background(Color(0xFF5038A0)), contentAlignment = Alignment.Center) { Text("♥", color = Color.White, fontSize = 22.sp) }
        else AsyncImage(r.image, null, Modifier.size(56.dp).clip(shape).background(Card2), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(r.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kindLabel(r.uri) + (r.artist.takeIf { it.isNotEmpty() }?.let { " • $it" } ?: ""), color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun kindLabel(uri: String) = when {
    uri.contains(":collection") || uri.startsWith("spotify:playlist:") -> tr("プレイリスト", "Playlist")
    uri.startsWith("spotify:album:") -> tr("アルバム", "Album")
    uri.startsWith("spotify:artist:") -> tr("アーティスト", "Artist")
    else -> ""
}

/** 「今日」「昨日」、それより前は日付 */
private fun dayLabel(at: Long): String {
    val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> tr("今日", "Today")
        today.minusDays(1) -> tr("昨日", "Yesterday")
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape).background(if (on) Accent else Card2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
