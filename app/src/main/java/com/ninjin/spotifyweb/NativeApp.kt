package com.ninjin.spotifyweb

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** テーマ（myspoti と同じく、アクセントカラー 5 色と真っ黒の背景を選べる） */
object Theme {
    val accents = listOf("green" to Color(0xFF1ED760), "blue" to Color(0xFF509BF5), "purple" to Color(0xFFAF7AF6), "pink" to Color(0xFFF573A0), "orange" to Color(0xFFFF9E3D))
    var accent by mutableStateOf(accents[0].second)
    var amoled by mutableStateOf(false)
    fun load(host: AppHost) {
        accent = accents.firstOrNull { it.first == host.setting("accent", "green") }?.second ?: accents[0].second
        amoled = host.setting("amoled", "false") == "true"
    }
}

internal val Bg: Color get() = if (Theme.amoled) Color.Black else Color(0xFF121212)
internal val Card2 = Color(0xFF2A2A2A)
internal val Sub = Color(0xFFB3B3B3)
internal val Accent: Color get() = Theme.accent

/** 24x24 の塗りのアイコン */
internal fun icon(d: String): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White)).build()

/** 24x24 の線のアイコン（公式アプリと同じ細い線） */
internal fun line(d: String, w: Float = 1.8f): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()

internal object Icons {
    val home = icon("M12.5 3.25a.5.5 0 0 0-1 0L3 10.4V21h6.5v-6h5v6H21V10.4z")
    val search = icon("M10.5 3a7.5 7.5 0 0 1 5.96 12.06l4.24 4.24-1.4 1.4-4.24-4.24A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z")
    val library = icon("M4 3h2v18H4zM9 3h2v18H9zm4.6.6 1.9-.6 5.6 17.4-1.9.6z")
    val play = icon("M7 4.5v15l12.5-7.5z")
    val pause = icon("M6 4h4v16H6zm8 0h4v16h-4z")
    val next = icon("M5 4.5v15l10-7.5zM17 4h2v16h-2z")
    val prev = icon("M19 4.5v15L9 12zM5 4h2v16H5z")
    val down = line("M5 9l7 7 7-7", 2f)
    val more = icon("M12 6.5a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0 7a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0 7a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z")
    val shuffle = line("M3 7h3.5c2 0 3 1 4.2 2.7l2.6 4.6C14.5 16 15.5 17 17.5 17H21M17.5 14l3 3-3 3M3 17h3.5c1.2 0 2-.4 2.7-1.1M13 8.1C13.7 7.4 14.5 7 15.7 7H21M17.5 4l3 3-3 3")
    val repeat = line("M4 12V9.5A3.5 3.5 0 0 1 7.5 6H19M16 3l3 3-3 3M20 12v2.5a3.5 3.5 0 0 1-3.5 3.5H5M8 21l-3-3 3-3")
    val add = line("M12 7.5v9M7.5 12h9M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", 1.6f)
    val check = icon("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1.3 14.2-4-4 1.4-1.4 2.6 2.6 5.6-5.6 1.4 1.4z")
    val device = line("M4 15V6a1 1 0 0 1 1-1h12M2 19h11M16 9h5v11h-5zM18.5 17.5h0", 1.6f)
    val share = line("M12 3v12M7.5 7.5 12 3l4.5 4.5M5 12v7.5A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V12", 1.6f)
    val queue = line("M4 6h16M4 12h10M4 18h10M17 14v7l4-3.5z", 1.6f)
    val back = line("M15 5l-7 7 7 7", 2f)
    val close = line("M6 6l12 12M18 6 6 18", 2f)
    val gear = line("M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 13.5l1.6 1.2-2 3.4-1.9-.7a7 7 0 0 1-2 1.2L14.8 21h-4l-.3-2.4a7 7 0 0 1-2-1.2l-1.9.7-2-3.4 1.6-1.2a7 7 0 0 1 0-2.3L4.6 10l2-3.4 1.9.7a7 7 0 0 1 2-1.2L10.8 3h4l.3 2.1a7 7 0 0 1 2 1.2l1.9-.7 2 3.4-1.6 1.2a7 7 0 0 1 0 2.3z", 1.6f)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("ホーム", Icons.home), Search("検索", Icons.search), Library("ライブラリ", Icons.library)
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
                onInvite = { drawerOpen = false; host.share("https://open.spotify.com/", "Spotify に招待") },
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
            title = { Text("アカウントを追加する") },
            text = { Text("いまのアカウントからログアウトして、別のアカウントでログインします。") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false; host.logout() }) { Text("ログアウトして続ける", color = Accent) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false }) { Text("キャンセル", color = Color.White) } },
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

/** ジャケットの色（公式アプリと同じく、くすんだ濃い色）。曲が変わるとなめらかに変わる */
@Composable
internal fun rememberArtColorPublic(url: String): Color = rememberArtColor(url)

@Composable
private fun rememberArtColor(url: String): Color {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(Color(0xFF3A3A3A)) }
    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(64).allowHardware(false).build())
            (r as? SuccessResult)?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
        } ?: return@LaunchedEffect
        val p = withContext(Dispatchers.Default) { Palette.from(bmp).generate() }
        val sw = p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch ?: return@LaunchedEffect
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(sw.rgb, hsv)
        hsv[2] = hsv[2].coerceAtMost(0.42f)
        hsv[1] = hsv[1].coerceAtMost(0.75f)
        c = Color(android.graphics.Color.HSVToColor(hsv))
    }
    val anim by animateColorAsState(c, tween(500), label = "art")
    return anim
}

@Composable
internal fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Sub, fontSize = 15.sp) }
}

/** 画面いっぱいの読み込み中 */
@Composable
internal fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingDots() }
}

/** 読み込み中の印（公式アプリと同じ、3 つの点が順に大きく明るくなる） */
@Composable
internal fun LoadingDots() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val p by t.animateFloat(
                0f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.keyframes {
                        durationMillis = 1200
                        0f at 0; 1f at 300; 0f at 600; 0f at 1200
                    },
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * 160),
                ),
                label = "dot$i",
            )
            Box(
                Modifier.size(10.dp).graphicsLayer { scaleX = 0.7f + 0.3f * p; scaleY = 0.7f + 0.3f * p; alpha = 0.35f + 0.65f * p }
                    .clip(CircleShape).background(Color.White)
            )
        }
    }
}

@Composable
internal fun Avatar(user: Bridge.User?, size: Int, onClick: (() -> Unit)? = null) {
    val m = Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE8A87C)).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Box(m, contentAlignment = Alignment.Center) {
        if (user?.image?.isNotEmpty() == true) AsyncImage(user.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(user?.name?.take(1) ?: "", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = (size / 2.2).sp)
    }
}

@Composable
private fun HomeScreen(bridge: Bridge, ready: Boolean, user: Bridge.User?, onAvatar: () -> Unit, onPlay: (Card) -> Unit, onMenu: (MenuTarget) -> Unit = {}, onBrowse: () -> Unit = {}) {
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
        if (error != null) Placeholder("読み込めませんでした") else Loading()
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
            Text(if (podcastFollow) "最新エピソード" else "最新リリース", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        }
        // 公式アプリと同じ、フォローしている番組がないときの案内
        if (podcastFollow && f.emptyPodcasts) item {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF242424)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("フォローしているポッドキャストはありません", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("お気に入りをフォローして、最新情報を逃さずチェックしましょう。", color = Sub, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Text("ポッドキャストをブラウズ", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
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
private fun Chips(chips: List<Chip>, facet: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ChipText("すべて", facet.isEmpty()) { onSelect("") } }
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
private fun ChipText(t: String, on: Boolean, onColor: Color = Accent, onClick: () -> Unit) {
    Text(
        t, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape).background(if (on) onColor else Card2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun Shortcuts(items: List<Card>, onPlay: (Card) -> Unit) {
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

private object FeedIcons {
    val mute = line("M3.5 9.5h3.5L12 5v14l-5-4.5H3.5zM15.5 9.5l4 5M19.5 9.5l-4 5", 1.6f)
}

/** 「〇日前」 */
private fun ago(iso: String): String {
    val t = runCatching { java.time.LocalDate.parse(iso.take(10)) }.getOrNull() ?: return ""
    val d = java.time.temporal.ChronoUnit.DAYS.between(t, java.time.LocalDate.now())
    return when { d <= 0 -> "今日"; d < 7 -> "$d 日前"; d < 30 -> "${d / 7}週間前"; d < 365 -> "${d / 30}か月前"; else -> "${d / 365}年前" }
}

/** フォロー中やポッドキャストの、1 件ずつの大きなカード（公式アプリと同じく、ジャケットの色・曲の一覧・プレビュー・追加・再生） */
@Composable
private fun FeedCard(bridge: Bridge, s: Shelf, onPlay: (Card) -> Unit, onMenu: (MenuTarget) -> Unit) {
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
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White)); append("${t.size}曲"); pop()
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
                    Text(if (!isAlbum) "エピソードのプレビュー" else if ((page?.tracks?.size ?: 1) <= 1 || c.kind == "single") "シングルをプレビュー" else "アルバムをプレビュー", color = Color(0x80FFFFFF), fontSize = 14.sp)
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

@Composable
internal fun Ico(icon: ImageVector, size: Int, tint: Color = Color.White, box: Int = 40, onClick: () -> Unit) {
    Box(Modifier.size(box.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(icon), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))
    }
}

/** 公式アプリと同じミニプレーヤー：ジャケットの色、曲名（長いと流れる）／アーティスト、デバイス・追加・再生、下に細い進み具合 */
@Composable
private fun MiniPlayer(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onOpen: () -> Unit, onDevices: () -> Unit) {
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
                        p.device.ifEmpty { out.ifEmpty { "このスマホ" } }, color = if (onOther) Accent else Color(0xCCFFFFFF), fontSize = 13.sp,
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

@Composable
private fun TabBar(tab: Tab, onSelect: (Tab) -> Unit) {
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

internal fun mmss(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** 公式アプリと同じ全画面プレーヤー */
@Composable
private fun FullPlayer(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onClose: () -> Unit, onDevices: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit, onOpen: (Card) -> Unit = {}) {
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
private fun PlayerMain(bridge: Bridge, host: AppHost, p: PlayerState, tint: Color, onClose: () -> Unit, onDevices: () -> Unit, onQueue: () -> Unit, onMenu: () -> Unit) {
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

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a))
}

private object DrawerIcons {
    val report = line("M4 20V10M9.5 20V4M15 20v-7M20.5 20V8", 1.8f)
    val history = line("M12 21a9 9 0 1 0-8.5-6M3 20v-5h5M12 7v5l3 2", 1.8f)
    val news = line("M13 2 4 14h7l-1 8 9-12h-7z", 1.6f)
    val plus = line("M12 5v14M5 12h14", 2f)
}

/** サイドメニュー（公式アプリと同じ：アカウントのカード、リスニングレポート・最近・新着・設定、アクティビティと友達を招待） */
@Composable
private fun Drawer(user: Bridge.User?, player: PlayerState, onProfile: () -> Unit, onAddAccount: () -> Unit, onReport: () -> Unit, onRecent: () -> Unit, onNews: () -> Unit, onSettings: () -> Unit, onInvite: () -> Unit) {
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
                    Text("プロフィールを表示", color = Sub, fontSize = 13.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
            Row(Modifier.fillMaxWidth().clickable(onClick = onAddAccount).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF3E3E3E)), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(DrawerIcons.plus), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Sub))
                }
                Text("アカウントを追加する", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
            }
        }
        DrawerItem(DrawerIcons.report, "リスニングレポート", onReport)
        DrawerItem(DrawerIcons.history, "最近", onRecent)
        DrawerItem(DrawerIcons.news, "あなたへのおすすめ新着", onNews)
        DrawerItem(Icons.gear, "設定とプライバシー", onSettings)
        // アクティビティ（自分のアイコンに再生中のジャケット）と友達を招待
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.clickable(onClick = onProfile), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF3E3E3E)).padding(3.dp)) { Avatar(user, 58) }
                    if (player.image.isNotEmpty()) AsyncImage(player.image, null, Modifier.size(26.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Crop)
                }
                Text("アクティビティ", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.clickable(onClick = onInvite), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF3E3E3E)), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(DrawerIcons.plus), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Color.White))
                }
                Text("友達を招待", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(rememberVectorPainter(icon), null, Modifier.size(26.dp))
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

private val QUALITIES = listOf(
    Triple("auto", "自動", "通信状況に合わせて調整"),
    Triple("low", "低音質", "通信量を抑える"),
    Triple("normal", "標準", "通信量を少し抑える"),
    Triple("high", "高音質", "AAC 128 kbps（無料プランの Web Player の上限）"),
    Triple("very_high", "最高音質", "AAC 256 kbps（Spotify Premium のみ）"),
)

@Composable
private fun SettingsScreen(bridge: Bridge, host: AppHost, user: Bridge.User?, onBack: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }
    Column(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.back, 24) { if (page != null) page = null else onBack() }
            Text(
                when (page) { "quality" -> "メディアの音質"; "playback" -> "再生"; "account" -> "アカウント"; "look" -> "コンテンツと表示"; else -> "設定" },
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp),
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 160.dp)) {
            when (page) {
                null -> {
                    val missing by bridge.missing.collectAsState()
                    // Spotify の更新で使えなくなった機能があれば、いちばん上で知らせる（押すとログを送れる）
                    if (missing.isNotEmpty()) SettingRow(
                        "⚠ 一部の機能が使えません",
                        missing.joinToString("・") + "\nSpotify 側の更新が原因かもしれません。押すとデバッグログを共有できます",
                    ) { host.shareDebugLog() }
                    SettingRow("アカウント", user?.name ?: "") { page = "account" }
                    SettingRow("コンテンツと表示", "テーマカラー・背景") { page = "look" }
                    SettingRow("再生", "バックグラウンド再生・広告抑制・クロスフェード") { page = "playback" }
                    SettingRow("メディアの音質", "Wi-Fi・モバイルデータ通信でのストリーミング") { page = "quality" }
                    SettingRow("デバッグログを共有", "直近の広告の検出・ミュート・早送りなどの記録") { host.shareDebugLog() }
                    SettingRow("このアプリについて", "Spotify Web Player を使った非公式のアプリです。曲は端末に保存しません") {}
                }
                "account" -> {
                    SettingRow("ユーザー名", user?.name ?: "") {}
                    SettingRow("ログアウト", "このアプリからログアウトします") { host.logout() }
                }
                "look" -> {
                    Section("テーマカラー")
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Theme.accents.forEach { (key, color) ->
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(if (Theme.accent == color) Color.White else Color.Transparent).padding(3.dp)
                                    .clip(CircleShape).background(color).clickable { Theme.accent = color; host.setSetting("accent", key) },
                            )
                        }
                    }
                    SwitchRow("AMOLED ブラック", "背景を真っ黒にして電池を節約", Theme.amoled) { Theme.amoled = it; host.setSetting("amoled", it.toString()) }
                }
                "playback" -> {
                    var bg by remember { mutableStateOf(host.setting("background", "true") == "true") }
                    SwitchRow("バックグラウンド再生", "アプリを閉じても再生を続ける", bg) { bg = it; host.setSetting("background", it.toString()) }
                    var ads by remember { mutableStateOf(host.setting("adblock", "true") == "true") }
                    SwitchRow("広告をブロック", "広告マネージャーを停止し、広告再生をミュート・スキップする", ads) { ads = it; host.setSetting("adblock", it.toString()) }
                    var gl by remember { mutableStateOf(host.setting("gapless", "true") == "true") }
                    SwitchRow("ギャップレス再生", "曲と曲のあいだの無音をなくして、続けて再生する", gl) { gl = it; host.setSetting("gapless", it.toString()) }
                    // クロスフェード：公式アプリと同じく、オンにするとスライダーで 1〜12 秒を選ぶ
                    var xf by remember { mutableStateOf(host.setting("crossfade", "0").toIntOrNull() ?: 0) }
                    var xfLast by remember { mutableStateOf(if (xf > 0) xf else 5) }
                    SwitchRow("クロスフェード", "曲と曲のあいだを指定した秒数でフェードさせる", xf > 0) { on ->
                        xf = if (on) xfLast else 0; host.setSetting("crossfade", xf.toString())
                    }
                    if (xf > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${xf} 秒", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                        Slider(
                            value = xf.toFloat(), valueRange = 1f..12f, steps = 10,
                            onValueChange = { xf = it.toInt().coerceIn(1, 12); xfLast = xf },
                            onValueChangeFinished = { host.setSetting("crossfade", xf.toString()) },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent, inactiveTrackColor = Color(0x4DFFFFFF)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                "quality" -> {
                    var wifi by remember { mutableStateOf(host.quality(false)) }
                    var cell by remember { mutableStateOf(host.quality(true)) }
                    var rate by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(wifi, cell) { delay(300); rate = runCatching { bridge.bitrate() }.getOrNull() }
                    Section("Wi-Fi でのストリーミング")
                    QUALITIES.forEach { q -> RadioRow(q, wifi == q.first) { wifi = q.first; host.setSetting("quality", q.first) } }
                    Section("モバイルデータ通信でのストリーミング")
                    QUALITIES.forEach { q -> RadioRow(q, cell == q.first) { cell = q.first; host.setSetting("quality.cell", q.first) } }
                    Section("いまの状態")
                    SettingRow("接続", if (host.metered) "モバイルデータ通信" else "Wi-Fi") {}
                    SettingRow("いま再生中の音質", rate?.let { if (it > 0) "AAC ${it / 1000} kbps" else "不明（このスマホで再生中の曲がありません）" } ?: "確認中…") {}
                }
            }
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        if (detail.isNotEmpty()) Text(detail, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(detail, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun RadioRow(q: Triple<String, String, String>, selected: Boolean, onClick: () -> Unit) {
    val premium = q.first == "very_high"
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !premium, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(q.second, color = if (premium) Color(0x66FFFFFF) else if (selected) Accent else Color.White, fontSize = 16.sp)
            Text(q.third, color = if (premium) Color(0x55B3B3B3) else Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent)
                .let { if (!selected) it.background(Color.Transparent) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(Bg))
            else Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF7C7C7C)).padding(2.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Bg)) }
        }
    }
}


@Composable
private fun lyricsLine(l: Bridge.Lyrics?, posMs: Long): Int {
    if (l == null || !l.synced) return -1
    var i = -1
    for (k in l.lines.indices) if (l.lines[k].first <= posMs) i = k else break
    return i
}

private fun lyricsColor(l: Bridge.Lyrics?, tint: Color): Color =
    if (l != null && l.bg != 0) Color(l.bg or 0xFF000000.toInt()) else tint

/** プレーヤーの下に続くカード（歌詞・アーティストについて・次に再生） */
@Composable
private fun PlayerExtras(bridge: Bridge, p: PlayerState, tint: Color, lyrics: Bridge.Lyrics?, onLyrics: () -> Unit, onOpen: (Card) -> Unit) {
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
                Text("歌詞", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    Text("アーティストについて", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (a.listeners > 0) Text(
                                (if (a.listeners >= 10000) "%.1f万".format(a.listeners / 10000.0) else "%,d".format(a.listeners)) + "人の月間リスナー",
                                color = Sub, fontSize = 13.sp,
                            )
                        }
                        Text(
                            if (following) "フォロー中" else "フォローする", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
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
                Text("次に再生", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
private fun LyricsScreen(p: PlayerState, lyrics: Bridge.Lyrics?, tint: Color, onClose: () -> Unit) {
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
            if (lines.isEmpty()) item { Text("この曲の歌詞はありません", color = Color.White, fontSize = 16.sp) }
        }
    }
}
