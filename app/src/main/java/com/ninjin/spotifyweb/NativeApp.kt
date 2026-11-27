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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

internal enum class Tab(private val ja: String, private val en: String) {
    Home("ホーム", "Home"), Search("検索", "Search"), Library("ライブラリ", "Your Library"),
    // 新しいテーマだけのタブ
    Browse("見つける", "New");

    val label get() = tr(ja, en)
    val icon: ImageVector get() = when (this) { Home -> Icons.home; Search -> Icons.search; Library -> Icons.library; Browse -> Icons.browse }
}

/** アプリの画面（Compose）。データと再生は [bridge] 経由で裏の Web Player に任せる */
@Composable
fun NativeApp(bridge: Bridge, host: AppHost) {
    remember { Theme.load(host); true }
    // 新しいテーマは、端末のライト／ダークに合わせる
    val light = Theme.glass && !androidx.compose.foundation.isSystemInDarkTheme()
    androidx.compose.animation.SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(LocalLight provides light, LocalShared provides this) { AppContent(bridge, host) }
    }
}

@Composable
private fun AppContent(bridge: Bridge, host: AppHost) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var playerOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var devicesOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var newsOpen by remember { mutableStateOf(false) }
    var recentsOpen by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf(false) }
    var otherUser by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = otherUser != null && !playerOpen) { otherUser = null }
    BackHandler(enabled = profileOpen && !playerOpen) { profileOpen = false }
    BackHandler(enabled = newsOpen && !playerOpen) { newsOpen = false }
    BackHandler(enabled = recentsOpen && !playerOpen) { recentsOpen = false }
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
    // 新しい版が出ていないか、起動して準備ができたら確かめる（6 時間に 1 回まで）
    LaunchedEffect(ready) { if (ready) host.updater.checkIfDue() }
    val tint = rememberArtColor(player.image)

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = settingsOpen && !playerOpen) { settingsOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    val stacks = remember { Tab.entries.associateWith { androidx.compose.runtime.mutableStateListOf<String>() } }
    BackHandler(enabled = !playerOpen && !settingsOpen && !drawerOpen && stacks[tab]!!.isNotEmpty()) { stacks[tab]!!.removeAt(stacks[tab]!!.lastIndex) }
    BackHandler(enabled = !playerOpen && !settingsOpen && !drawerOpen && stacks[tab]!!.isEmpty() && tab != Tab.Home) { tab = Tab.Home }

    // 時計や電池のアイコンは、明るい背景のときだけ黒（全画面プレーヤーはいつも暗い）
    val view = androidx.compose.ui.platform.LocalView.current
    val lightBars = LocalLight.current && !playerOpen
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).run { isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars }
        }
    }
    val bg = Bg
    // 下のタブのガラスに写すため、画面の中身を写し取っておく
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(onDraw = { drawRect(bg); drawContent() })
    val collapse = remember { CollapseOnScroll() }
    // Classic に戻したら、Classic に無い「見つける」タブからホームへ
    LaunchedEffect(Theme.glass) { if (!Theme.glass && tab == Tab.Browse) tab = Tab.Home; collapse.collapsed = false }
    // 上の帯の見出しは、タブを替えたら一度消す（前のタブの名前が残らないように）
    LaunchedEffect(tab) { GlassChrome.smallTitle = null }
    androidx.compose.runtime.SideEffect {
        GlassChrome.covered = stacks[tab]!!.isNotEmpty() || playerOpen || settingsOpen || drawerOpen || profileOpen || newsOpen || recentsOpen || otherUser != null
    }
    // タブを替えたら、Apple Music と同じく軽く震わせる
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val select: (Tab) -> Unit = {
        if (Theme.glass && it != tab) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
        if (it == tab) stacks[it]!!.clear() else tab = it; collapse.collapsed = false
    }


    Box(Modifier.fillMaxSize().background(if (Theme.glass) Color.Black else Bg)) {
        // カードを押したら：アルバム・プレイリスト・アーティストはページを開き、曲は再生する
        val open: (Card) -> Unit = { c ->
            // ユーザーはプロフィールの画面で開く
            if (c.uri.startsWith("spotify:user:") && !c.uri.contains(":playlist:")) { otherUser = c.uri.split(':')[2] }
            else if (isPage(c.uri)) stacks[tab]!!.add(c.uri) else scope.launch { runCatching { bridge.play(c.uri) } }
        }
        val stack = stacks[tab]!!
        // タブごとに開いたページを覚えておく（タブを切り替えて戻っても、そのまま）
        // 全画面プレーヤーを開くと、後ろの画面が少し縮んで角が丸くなり、奥に下がる（Apple Music と同じ）
        val behind by androidx.compose.animation.core.animateFloatAsState(if (Theme.glass && playerOpen && player.has) 1f else 0f, tween(320), label = "behind")
        Box(Modifier.fillMaxSize().graphicsLayer {
            val sc = 1f - 0.08f * behind
            scaleX = sc; scaleY = sc
            shape = RoundedCornerShape((28 * behind).dp); clip = behind > 0f
        }.background(Bg)) {
            Box(Modifier.fillMaxSize().then(if (Theme.glass) Modifier.layerBackdrop(backdrop).nestedScroll(collapse) else Modifier)) {
                when (tab) {
                    Tab.Home -> HomeScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onPlay = open, onMenu = { menu = it }, onBrowse = { tab = Tab.Search })
                    Tab.Search -> SearchScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onOpen = open)
                    Tab.Library -> LibraryScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onOpen = open, onCreate = { createOpen = true })
                    Tab.Browse -> BrowseScreen(bridge, ready, user, onAvatar = { drawerOpen = true }, onOpen = open)
                }
                stack.lastOrNull()?.let { uri ->
                    androidx.compose.runtime.key(uri, stack.size) {
                        PageScreen(bridge, uri, player, onBack = { stack.removeAt(stack.lastIndex) }, onOpen = open, onMenu = { menu = it })
                    }
                }
            }
            // 大きな見出しがスクロールで隠れたら、上にガラスの帯と小さな見出し（ページは自分の帯を持つ）。写し取る中身の外に置く
            if (Theme.glass && stack.isEmpty() && !GlassChrome.overlay) GlassTopBar(backdrop, GlassChrome.smallTitle)
            // ミニプレーヤーの左右の余白も、裏の画面に押した判定が届かないようにする
            if (Theme.glass) Box(Modifier.align(Alignment.BottomCenter)) {
                GlassBottom(
                    backdrop, tab, collapse.collapsed, onSelect = select, onExpand = { collapse.collapsed = false },
                    mini = if (player.has) { compact, m -> GlassMiniPlayer(bridge, player, backdrop, compact, m, playerOpen) { playerOpen = true } } else null,
                )
            } else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {}) {
                if (player.has) MiniPlayer(bridge, host, player, tint, onOpen = { playerOpen = true }, onDevices = { devicesOpen = true })
                TabBar(tab, select)
            }
        }
        AnimatedVisibility(newsOpen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            WhatsNewScreen(bridge, onBack = { newsOpen = false }, onPlay = { c -> newsOpen = false; open(c) }, onMenu = { menu = it })
        }
        AnimatedVisibility(recentsOpen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            RecentsScreen(bridge, onBack = { recentsOpen = false }, onOpen = { c -> recentsOpen = false; open(c) })
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
                user,
                onProfile = { drawerOpen = false; profileOpen = true },
                onAddAccount = { drawerOpen = false; confirmSwitch = true },
                onRecent = { drawerOpen = false; recentsOpen = true },
                onNews = { drawerOpen = false; newsOpen = true },
                onSettings = { drawerOpen = false; settingsOpen = true },
            )
        }
        AnimatedVisibility(
            visible = playerOpen && player.has,
            enter = slideInVertically(tween(280)) { it }, exit = slideOutVertically(tween(240)) { it },
        ) {
            // ジャケットの色の上なので、いつも暗い側の色で描く
            androidx.compose.runtime.CompositionLocalProvider(LocalLight provides false, LocalPlayerScope provides this) {
                FullPlayer(bridge, host, player, tint, onClose = { playerOpen = false }, onDevices = { devicesOpen = true }, onQueue = { queueOpen = true },
                    onMenu = { menu = MenuTarget.OfTrack(Track(player.uri, player.title, player.artist, player.image, player.dur)) },
                    onOpen = { c -> playerOpen = false; open(c) })
            }
        }
        // 別のアカウントを使うには、いまのアカウントからログアウトしてログインし直す（確認してから）
        if (confirmSwitch) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSwitch = false },
            title = { Text(tr("アカウントを追加する", "Add account")) },
            text = { Text(tr("いまのアカウントからログアウトして、別のアカウントでログインします。", "You'll be logged out of this account so you can log in with another one.")) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false; host.logout() }) { Text(tr("ログアウトして続ける", "Log out and continue"), color = Accent) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false }) { Text(tr("キャンセル", "Cancel"), color = Fg) } },
            containerColor = classic(0xFF282828, Surface), titleContentColor = Fg, textContentColor = Sub,
        )
        UpdateDialog(host.updater, loggedOut = !ready)
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
            .background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0.6f), Bg.copy(alpha = 0.95f), Bg)))
            // タブのすき間やジェスチャーバーの上を押しても、裏の画面に届かないようにする
            .clickable(remember { MutableInteractionSource() }, null) {}
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        listOf(Tab.Home, Tab.Search, Tab.Library).forEach { t ->
            val on = t == tab
            Column(
                // 当たり判定はタブの上下の余白まで含める
                Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onSelect(t) }.padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(rememberVectorPainter(t.icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (on) Fg else Sub))
                Text(t.label, color = if (on) Fg else Sub, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

internal object DrawerIcons {
    val history = line("M12 21a9 9 0 1 0-8.5-6M3 20v-5h5M12 7v5l3 2", 1.8f)
    val news = line("M13 2 4 14h7l-1 8 9-12h-7z", 1.6f)
    val plus = line("M12 5v14M5 12h14", 2f)
}

/** サイドメニュー（公式アプリと同じ：アカウントのカード、最近・新着・設定） */
@Composable
internal fun Drawer(user: Bridge.User?, onProfile: () -> Unit, onAddAccount: () -> Unit, onRecent: () -> Unit, onNews: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(310.dp).background(classic(0xFF1F1F1F, Surface))
            .clickable(remember { MutableInteractionSource() }, null) {}
            .windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(classic(0xFF2A2A2A, Surface2))) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(user, 52)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(user?.name ?: "", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("プロフィールを表示", "View profile"), color = Sub, fontSize = 13.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Fg.copy(alpha = 0.12f)))
            Row(Modifier.fillMaxWidth().clickable(onClick = onAddAccount).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(DrawerIcons.plus), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Sub))
                }
                Text(tr("アカウントを追加する", "Add account"), color = Fg, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
            }
        }
        DrawerItem(DrawerIcons.history, tr("最近", "Recents"), onRecent)
        DrawerItem(DrawerIcons.news, tr("あなたへのおすすめ新着", "What's new"), onNews)
        DrawerItem(Icons.gear, tr("設定とプライバシー", "Settings and privacy"), onSettings)
    }
}

@Composable
internal fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(icon), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Fg))
        Text(label, color = Fg, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
    }
}
