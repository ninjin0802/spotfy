package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class Tab(private val ja: String, private val en: String, val icon: ImageVector) {
    Home("ホーム", "Home", Icons.home), Search("検索", "Search", Icons.search), Library("ライブラリ", "Your Library", Icons.library);

    val label get() = tr(ja, en)
}

/** アプリの画面（Compose）。データと再生は [bridge] 経由で裏の Web Player に任せる */
@Composable
fun NativeApp(bridge: Bridge, host: AppHost) {
    remember { Theme.load(host); true }
    var tab by remember { mutableStateOf(Tab.Home) }
    var playerOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var devicesOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf(false) }
    var otherUser by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = otherUser != null && !playerOpen) { otherUser = null }
    BackHandler(enabled = profileOpen && !playerOpen) { profileOpen = false }
    val rawPlayer by bridge.player.collectAsState()
    // ほかの機器の名前は、2.5 秒続いたときだけ出す（起動直後に前の再生先が一瞬見えるのを防ぐ）
    val shownDevice by androidx.compose.runtime.produceState("" to "", rawPlayer.device) {
        if (rawPlayer.device.isEmpty()) value = "" to "" else { delay(2500); value = rawPlayer.device to rawPlayer.deviceType }
    }
    val player = rawPlayer.copy(device = shownDevice.first, deviceType = shownDevice.second)
    val ready by bridge.ready.collectAsState()
    var user by remember { mutableStateOf(bridge.cachedUser()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ready) { if (ready) runCatching { bridge.user() }.getOrNull()?.let { user = it } }
    val tint = rememberArtColor(player.image)

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = settingsOpen && !playerOpen) { settingsOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    val stacks = remember { Tab.entries.associateWith { androidx.compose.runtime.mutableStateListOf<String>() } }
    BackHandler(enabled = !playerOpen && !settingsOpen && !drawerOpen && stacks[tab]!!.isNotEmpty()) { stacks[tab]!!.removeAt(stacks[tab]!!.lastIndex) }
    BackHandler(enabled = !playerOpen && !settingsOpen && !drawerOpen && stacks[tab]!!.isEmpty() && tab != Tab.Home) { tab = Tab.Home }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // カードを押したら：アルバム・プレイリスト・アーティストはページを開き、曲は再生する
        val open: (Card) -> Unit = { c ->
            // ユーザーはプロフィールの画面で開く
            if (c.uri.startsWith("spotify:user:") && !c.uri.contains(":playlist:")) { otherUser = c.uri.split(':')[2] }
            else if (isPage(c.uri)) stacks[tab]!!.add(c.uri) else scope.launch { runCatching { bridge.play(c.uri) } }
        }
        val stack = stacks[tab]!!
        // タブごとに開いたページを覚えておく（タブを切り替えて戻っても、そのまま）
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.Home -> HomeScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onPlay = open, onMenu = { menu = it }, onBrowse = { tab = Tab.Search })
                Tab.Search -> SearchScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onOpen = open)
                Tab.Library -> LibraryScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onOpen = open, onCreate = { createOpen = true })
            }
            stack.lastOrNull()?.let { uri ->
                androidx.compose.runtime.key(uri, stack.size) {
                    PageScreen(bridge, uri, player, onBack = { stack.removeAt(stack.lastIndex) }, onOpen = open, onMenu = { menu = it })
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (player.has) MiniPlayer(bridge, host, player, tint, onOpen = { playerOpen = true }, onDevices = { devicesOpen = true })
            TabBar(tab) { if (it == tab) stacks[it]!!.clear() else tab = it }
        }
        AnimatedVisibility(profileOpen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            ProfileScreen(bridge, user, onBack = { profileOpen = false }, onOpen = { c -> profileOpen = false; open(c) },
                onEdited = { scope.launch { runCatching { bridge.user() }.getOrNull()?.let { user = it } } })
        }
        otherUser?.let { uid ->
            androidx.compose.runtime.key(uid) {
                ProfileScreen(bridge, user, onBack = { otherUser = null }, onOpen = { c -> otherUser = null; open(c) }, userId = uid)
            }
        }
        AnimatedVisibility(settingsOpen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            SettingsScreen(bridge, host, user, onBack = { settingsOpen = false })
        }
        // サイドメニュー（公式アプリと同じく、アイコンを押すと左から出る）
        AnimatedVisibility(drawerOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { drawerOpen = false })
        }
        AnimatedVisibility(drawerOpen, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            Drawer(
                user, player,
                onProfile = { drawerOpen = false; profileOpen = true },
                onAddAccount = { drawerOpen = false; confirmSwitch = true },
                onReport = { drawerOpen = false; profileOpen = true },
                onRecent = { drawerOpen = false; tab = Tab.Library },
                onNews = { drawerOpen = false; tab = Tab.Home },
                onSettings = { drawerOpen = false; settingsOpen = true },
                onInvite = { drawerOpen = false; host.share("https://open.spotify.com/", tr("Spotify に招待", "Invite to Spotify")) },
            )
        }
        AnimatedVisibility(
            visible = playerOpen && player.has,
            enter = slideInVertically(tween(280)) { it }, exit = slideOutVertically(tween(240)) { it },
        ) {
            FullPlayer(bridge, host, player, tint, onClose = { playerOpen = false }, onDevices = { devicesOpen = true }, onQueue = { queueOpen = true },
                onMenu = { menu = MenuTarget.OfTrack(Track(player.uri, player.title, player.artist, player.image, player.dur)) },
                onOpen = { c -> playerOpen = false; open(c) })
        }
        // 別のアカウントを使うには、いまのアカウントからログアウトしてログインし直す（確認してから）
        if (confirmSwitch) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSwitch = false },
            title = { Text(tr("アカウントを追加する", "Add account")) },
            text = { Text(tr("いまのアカウントからログアウトして、別のアカウントでログインします。", "You'll be logged out of this account so you can log in with another one.")) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false; host.logout() }) { Text(tr("ログアウトして続ける", "Log out and continue"), color = Accent) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false }) { Text(tr("キャンセル", "Cancel"), color = Color.White) } },
            containerColor = Color(0xFF282828), titleContentColor = Color.White, textContentColor = Sub,
        )
        if (devicesOpen) DevicesSheet(bridge, host) { devicesOpen = false }
        if (queueOpen) QueueSheet(bridge, player) { queueOpen = false }
        if (createOpen) CreateSheet(bridge, onCreated = { c -> open(c) }) { createOpen = false }
        menu?.let { m ->
            MenuSheet(bridge, host, m, onOpen = { c -> playerOpen = false; settingsOpen = false; open(c) }, onDismiss = { menu = null })
        }
    }
}

@Composable
internal fun TabBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x99121212), Color(0xF2121212), Color(0xFF121212))))
            .windowInsetsPadding(WindowInsets.navigationBars).padding(top = 12.dp, bottom = 4.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Column(
                Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(rememberVectorPainter(t.icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (on) Color.White else Sub))
                Text(t.label, color = if (on) Color.White else Sub, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

internal object DrawerIcons {
    val report = line("M4 20V10M9.5 20V4M15 20v-7M20.5 20V8", 1.8f)
    val history = line("M12 21a9 9 0 1 0-8.5-6M3 20v-5h5M12 7v5l3 2", 1.8f)
    val news = line("M13 2 4 14h7l-1 8 9-12h-7z", 1.6f)
    val plus = line("M12 5v14M5 12h14", 2f)
}

/** サイドメニュー（公式アプリと同じ：アカウントのカード、リスニングレポート・最近・新着・設定、アクティビティと友達を招待） */
@Composable
internal fun Drawer(user: Bridge.User?, player: PlayerState, onProfile: () -> Unit, onAddAccount: () -> Unit, onReport: () -> Unit, onRecent: () -> Unit, onNews: () -> Unit, onSettings: () -> Unit, onInvite: () -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(310.dp).background(Color(0xFF1F1F1F))
            .clickable(remember { MutableInteractionSource() }, null) {}
            .windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(user, 52)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(user?.name ?: "", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("プロフィールを表示", "View profile"), color = Sub, fontSize = 13.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
            Row(Modifier.fillMaxWidth().clickable(onClick = onAddAccount).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF3E3E3E)), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(DrawerIcons.plus), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Sub))
                }
                Text(tr("アカウントを追加する", "Add account"), color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
            }
        }
        DrawerItem(DrawerIcons.report, tr("リスニングレポート", "Listening stats"), onReport)
        DrawerItem(DrawerIcons.history, tr("最近", "Recents"), onRecent)
        DrawerItem(DrawerIcons.news, tr("あなたへのおすすめ新着", "What's new"), onNews)
        DrawerItem(Icons.gear, tr("設定とプライバシー", "Settings and privacy"), onSettings)
        // アクティビティ（自分のアイコンに再生中のジャケット）と友達を招待
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.clickable(onClick = onProfile), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF3E3E3E)).padding(3.dp)) { Avatar(user, 58) }
                    if (player.image.isNotEmpty()) AsyncImage(player.image, null, Modifier.size(26.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Crop)
                }
                Text(tr("アクティビティ", "Activity"), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.clickable(onClick = onInvite), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF3E3E3E)), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(DrawerIcons.plus), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Color.White))
                }
                Text(tr("友達を招待", "Invite friends"), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
internal fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(icon), null, Modifier.size(26.dp))
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
    }
}
