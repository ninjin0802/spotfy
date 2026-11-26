package com.ninjin.spotifyweb

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ライブラリの種類（Apple Music の「プレイリスト／アーティスト／アルバム」の行） */
private class LibKind(val key: String, val label: String, val icon: ImageVector, val match: (Card) -> Boolean)

private val libKinds get() = listOf(
    LibKind("playlist", tr("プレイリスト", "Playlists"), GlassIcons.playlist) { it.kind == "playlist" || it.kind == "liked" },
    LibKind("artist", tr("アーティスト", "Artists"), GlassIcons.mic) { it.kind == "artist" },
    LibKind("album", tr("アルバム", "Albums"), GlassIcons.album) { it.kind == "album" },
    LibKind("show", tr("ポッドキャスト", "Podcasts"), GlassIcons.podcast) { it.kind == "show" || it.kind == "your-episodes" },
)

/** Apple Music のライブラリ：種類の行（赤いアイコンと「＞」）、その下に「最近追加した項目」の 2 列のジャケット */
@Composable
internal fun GlassLibraryScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onOpen: (Card) -> Unit, onCreate: () -> Unit) {
    var items by remember { mutableStateOf(bridge.cachedLibrary()) }
    LaunchedEffect(bridge.libraryVersion.value) { if (ready && bridge.libraryVersion.value > 0) runCatching { bridge.library() }.onSuccess { items = it } }
    LaunchedEffect(ready) { if (ready) runCatching { bridge.library() }.onSuccess { items = it } }
    var kind by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = kind != null) { kind = null }
    val liked = Card("spotify:collection:tracks", tr("お気に入りの曲", "Liked Songs"), tr("プレイリスト", "Playlist"), "", false, "liked", true)
    val all = listOf(liked) + items.filter { !it.uri.contains(":collection") && it.kind != "liked-songs" }
    val k = libKinds.firstOrNull { it.key == kind }
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 170.dp)) {
        if (k == null) {
            item {
                GlassTitle(tr("ライブラリ", "Library"), user, onAvatar) { Ico(Icons.add, 24, Accent, onClick = onCreate) }
            }
            items(libKinds) { lk ->
                Column(Modifier.fillMaxWidth().clickable { kind = lk.key }) {
                    Row(Modifier.fillMaxWidth().height(50.dp).padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(rememberVectorPainter(lk.icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Accent))
                        Text(lk.label, color = Fg, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
                        Image(rememberVectorPainter(GlassIcons.chevron), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Sub))
                    }
                    Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(Fg.copy(alpha = 0.12f)))
                }
            }
            item { GlassSectionTitle(tr("最近追加した項目", "Recently Added")) }
            items(all.drop(1).chunked(2)) { row ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { c -> GridCard(c, Modifier.weight(1f)) { onOpen(c) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            // 種類の一覧：戻る＋大きな見出し、ジャケットと名前の行
            item {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(Modifier.padding(start = 8.dp, top = 4.dp).clickable { kind = null }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(rememberVectorPainter(Icons.back), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Accent))
                        Text(tr("ライブラリ", "Library"), color = Accent, fontSize = 17.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(k.label, color = Fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                }
            }
            items(all.filter(k.match), key = { it.uri }) { c ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(c) }) {
                    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        LibCover(c, Modifier.size(56.dp), 8.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(c.title, color = Fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = if (c.kind == "liked") user?.name ?: "" else c.subtitle.substringAfter(" • ", c.subtitle)
                            if (sub.isNotEmpty() && c.kind != "artist") Text(sub, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Image(rememberVectorPainter(GlassIcons.chevron), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Sub))
                    }
                    Box(Modifier.padding(start = 90.dp).fillMaxWidth().height(0.5.dp).background(Fg.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

@Composable
private fun GridCard(c: Card, m: Modifier, onClick: () -> Unit) {
    Column(m.clickable(onClick = onClick)) {
        LibCover(c, Modifier.fillMaxWidth().aspectRatio(1f), 10.dp)
        Text(c.title, color = Fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val sub = c.subtitle.substringAfter(" • ", c.subtitle)
        if (sub.isNotEmpty()) Text(sub, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
