package com.ninjin.spotifyweb

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** 「⋮」で開くメニューの対象 */
sealed interface MenuTarget {
    /** liked：分かっていればお気に入りかどうか（お気に入りの曲のページなら true） */
    data class OfTrack(val track: Track, val liked: Boolean? = null) : MenuTarget
    data class OfPage(val uri: String, val title: String, val subtitle: String, val image: String, val artistUri: String, val artist: String) : MenuTarget
}

private object MenuIcons {
    val share = line("M12 3v12M7.5 7.5 12 3l4.5 4.5M5 12v7.5A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V12", 1.6f)
    val addLib = line("M12 7.5v9M7.5 12h9M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", 1.6f)
    val queue = line("M4 6h11M4 12h11M4 18h7M18 13v8M14 17h8", 1.6f)
    val album = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", 1.6f)
    val artist = line("M10 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM3 21c0-3.5 3-6 7-6M17 11v7.5a2 2 0 1 1-2-2h2M17 11h4", 1.6f)
}

/** 公式アプリと同じく、下から出るメニュー。下にスワイプすると閉じる */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(bridge: Bridge, host: AppHost, target: MenuTarget, onOpen: (Card) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    fun close(then: () -> Unit = {}) { if (Theme.glass) { onDismiss(); then() } else scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); then() } }

    val glass = Theme.glass
    MenuContainer(glass, state, onDismiss) {
        Column(if (glass) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) {
            when (target) {
                is MenuTarget.OfTrack -> {
                    val t = target.track
                    // 項目は最初から全部出しておく（あとから増えるとシートの高さが変わってガタつくため）。
                    // アルバム・アーティスト・お気に入りかどうかは裏で調べ、押されたらその結果を待って動く
                    val info = remember(t.uri) { kotlinx.coroutines.CompletableDeferred<Bridge.TrackInfo?>() }
                    var liked by remember(t.uri) { mutableStateOf(target.liked) }
                    LaunchedEffect(t.uri) {
                        val i2 = runCatching { bridge.trackInfo(t.uri) }.getOrNull()
                        if (i2 != null) liked = i2.liked
                        info.complete(i2)
                    }
                    Header(t.image, t.title, t.artist, false)
                    MenuGroup {
                    Item(MenuIcons.share, tr("シェア", "Share")) { close { host.share("https://open.spotify.com/track/" + t.uri.substringAfterLast(':'), t.title) } }
                    LikedItem(if (liked == true) tr("お気に入りの曲から削除", "Remove from Liked Songs") else tr("お気に入りの曲に追加", "Add to Liked Songs")) {
                        val was = liked == true
                        close { scope.launch { runCatching { bridge.like(t.uri, !was) }.onSuccess { toast(if (was) tr("お気に入りの曲から削除しました", "Removed from Liked Songs") else tr("お気に入りの曲に追加しました", "Added to Liked Songs")) } } }
                    }
                    Item(MenuIcons.queue, tr("[次に再生] に追加", "Add to queue")) {
                        close { scope.launch { runCatching { bridge.addToQueue(t.uri) }.onSuccess { toast(tr("[次に再生] に追加しました", "Added to queue")) } } }
                    }
                    Item(MenuIcons.album, tr("アルバムに移動", "Go to album")) {
                        scope.launch { info.await()?.takeIf { it.album.isNotEmpty() }?.let { i2 -> close { onOpen(Card(i2.album, i2.albumName, "", "", false)) } } ?: toast(tr("アルバムが見つかりませんでした", "Couldn't find the album")) }
                    }
                    Item(MenuIcons.artist, tr("アーティストに移動", "Go to artist")) {
                        scope.launch { info.await()?.takeIf { it.artist.isNotEmpty() }?.let { i2 -> close { onOpen(Card(i2.artist, i2.artistName, "", "", true)) } } ?: toast(tr("アーティストが見つかりませんでした", "Couldn't find the artist")) }
                    }
                    }
                }
                is MenuTarget.OfPage -> {
                    var inLib by remember(target.uri) { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(target.uri) { inLib = runCatching { bridge.inLibrary(target.uri) }.getOrNull() }
                    val kind = target.uri.split(':').getOrNull(1)
                    Header(target.image, target.title, target.subtitle, kind == "artist")
                    MenuGroup {
                    Item(MenuIcons.share, tr("シェア", "Share")) { close { host.share("https://open.spotify.com/$kind/" + target.uri.substringAfterLast(':'), target.title) } }
                    if (inLib != null) {
                        val on = inLib == true
                        val label = if (kind == "artist") (if (on) tr("フォローをやめる", "Unfollow") else tr("フォローする", "Follow")) else (if (on) tr("マイライブラリから削除", "Remove from Your Library") else tr("マイライブラリに追加", "Add to Your Library"))
                        Item(MenuIcons.addLib, label) {
                            close { scope.launch { runCatching { bridge.follow(target.uri, !on) }.onSuccess { toast(if (on) tr("マイライブラリから削除しました", "Removed from Your Library") else tr("マイライブラリに追加しました", "Added to Your Library")) } } }
                        }
                    }
                    Item(MenuIcons.queue, tr("[次に再生] に追加", "Add to queue")) {
                        close { scope.launch { runCatching { bridge.addToQueue(target.uri) }.onSuccess { toast(tr("[次に再生] に追加しました", "Added to queue")) } } }
                    }
                    if (target.artistUri.isNotEmpty()) Item(MenuIcons.artist, tr("アーティストに移動", "Go to artist")) { close { onOpen(Card(target.artistUri, target.artist, "", "", true)) } }
                    }
                }
            }
        }
    }
}

/** Classic は下から出るシート。Glass は Apple と同じく、後ろをぼかして真ん中に浮かぶメニュー */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuContainer(glass: Boolean, state: androidx.compose.material3.SheetState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (!glass) {
        ModalBottomSheet(
            onDismissRequest = onDismiss, sheetState = state, containerColor = Surface,
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(36.dp).height(4.dp).clip(CircleShape).background(Sub)) },
        ) { content() }
        return
    }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // 後ろの画面をぼかす（Android 12 から。それより古い端末は暗くするだけ）
        val w = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            w?.setDimAmount(0.25f)
            if (w != null && android.os.Build.VERSION.SDK_INT >= 31) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 80 }
            }
        }
        // 出るときは少し小さい所からふわっと広がる
        val shown = remember { androidx.compose.animation.core.Animatable(0.85f) }
        LaunchedEffect(Unit) { shown.animateTo(1f, androidx.compose.animation.core.spring(0.7f, 500f)) }
        Box(
            Modifier.fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 320.dp).graphicsLayer { scaleX = shown.value; scaleY = shown.value; alpha = ((shown.value - 0.85f) / 0.15f).coerceIn(0f, 1f) }
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

@Composable
private fun Header(image: String, title: String, sub: String, round: Boolean) {
    // Glass：押した物を大きく浮かせ、その下にメニュー（Apple の長押しと同じ）
    if (Theme.glass) {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(image, null, Modifier.size(200.dp).graphicsLayer { shadowElevation = 20.dp.toPx(); shape = if (round) CircleShape else RoundedCornerShape(14.dp); clip = true }.background(Card2), contentScale = ContentScale.Crop)
            Text(title, color = Fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            if (sub.isNotEmpty()) Text(sub, color = Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(image, null, Modifier.size(52.dp).clip(if (round) CircleShape else RoundedCornerShape(if (Theme.glass) 8.dp else 2.dp)).background(Card2), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (!Theme.glass) Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Surface2))
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    // Glass は Apple のメニューと同じく、左に名前、右にアイコン、項目のあいだに細い線
    if (Theme.glass) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Image(rememberVectorPainter(icon), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Fg))
        }
        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Fg.copy(alpha = 0.12f)))
        return
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(icon), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Sub))
        Text(label, color = Fg, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun LikedItem(label: String, onClick: () -> Unit) {
    if (Theme.glass) return Item(GlassIcons.heart, label, onClick)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
            contentAlignment = Alignment.Center,
        ) { Text("♥", color = Fg, fontSize = 12.sp) }
        Text(label, color = Fg, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp))
    }
}

internal object DevIcons {
    val phone = line("M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5zM11 18.5h2", 1.6f)
    val computer = line("M4 5h16v10H4zM2 19h20", 1.6f)
    val speaker = line("M7 2.5h10v19H7zM12 17a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM12 7h0", 1.6f)
    val tv = line("M3 5h18v12H3zM8 20h8", 1.6f)
    val headphones = line("M4 15v-3a8 8 0 0 1 16 0v3M3 14h4.5v7H3zM16.5 14H21v7h-4.5z", 1.6f)
    fun of(type: String) = when {
        type.contains("smartphone") || type.contains("tablet") -> phone
        type.contains("speaker") || type.contains("avr") || type.contains("audio") || type.contains("stb") -> speaker
        type.contains("tv") || type.contains("cast") || type.contains("game") -> tv
        else -> computer
    }
}

/** デバイスに接続（Spotify Connect）。このスマホの出力先（スピーカー／イヤホン）もここから切り替える */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(bridge: Bridge, host: AppHost, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var devs by remember { mutableStateOf<Bridge.Devices?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { devs = runCatching { bridge.devices() }.getOrNull() ?: devs; kotlinx.coroutines.delay(2000) } }
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Surface,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(36.dp).height(4.dp).clip(CircleShape).background(Sub)) },
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
            Text(tr("デバイスに接続", "Connect to a device"), color = Fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val d = devs
            if (d == null) {
                Text(tr("デバイスを探しています…", "Looking for devices…"), color = Sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            } else {
                val out = host.outputName()
                // このスマホ（いま出している先：スピーカー／イヤホン）
                DeviceRow(
                    if (out.isNotEmpty()) DevIcons.headphones else DevIcons.phone, tr("このスマホ", "This phone"),
                    when { busy == d.self -> tr("接続中…", "Connecting…"); d.active == d.self -> tr("再生中", "Now playing") + (if (out.isNotEmpty()) " • $out" else ""); else -> tr("このスマホで再生", "Play on this phone") },
                    d.active == d.self,
                ) {
                    if (d.active == d.self) close() else { busy = d.self; scope.launch { val ok = runCatching { bridge.transfer(d.self) }.getOrDefault(false); busy = null; if (ok) close() else failed = d.self } }
                }
                DeviceRow(if (out.isNotEmpty()) DevIcons.headphones else DevIcons.speaker, out.ifEmpty { tr("スマホのスピーカー", "Phone speaker") }, tr("タップしてスピーカー／イヤホンを切り替え", "Tap to switch between speaker and earphones"), false) { close(); host.outputSwitch() }
                Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Surface2))
                val others = d.list.filter { it.id != d.self }
                if (others.isEmpty()) Text(tr("ほかのデバイスは見つかりませんでした", "No other devices found"), color = Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                others.forEach { dev ->
                    DeviceRow(
                        DevIcons.of(dev.type), dev.name,
                        when { busy == dev.id -> tr("接続中…", "Connecting…"); failed == dev.id -> tr("接続できませんでした", "Couldn't connect"); dev.id == d.active -> tr("再生中", "Now playing"); else -> "Spotify Connect" },
                        dev.id == d.active,
                    ) {
                        if (dev.id == d.active) close() else { busy = dev.id; failed = null; scope.launch { val ok = runCatching { bridge.transfer(dev.id) }.getOrDefault(false); busy = null; if (ok) close() else failed = dev.id } }
                    }
                }
                Text(
                    tr("同じ Spotify アカウントでログインしているスピーカー・テレビ・PC・ゲーム機などが表示されます。", "Speakers, TVs, computers and game consoles signed in to the same Spotify account appear here."), color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(icon: ImageVector, name: String, sub: String, on: Boolean, onClick: () -> Unit) {
    val c = if (on) Accent else Fg
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(icon), null, Modifier.size(32.dp), colorFilter = ColorFilter.tint(c))
        Column(Modifier.padding(start = 16.dp)) {
            Text(name, color = c, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = if (on) Accent else Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
