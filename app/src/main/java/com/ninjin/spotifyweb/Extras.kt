package com.ninjin.spotifyweb

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun Grip() = Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(36.dp).height(4.dp).clip(CircleShape).background(Color(0xFF6A6A6A)))

/** おやすみタイマー（アプリ全体で 1 つ） */
object SleepTimer {
    var endsAt by mutableLongStateOf(0L)
    var job: Job? = null
    fun minutesLeft(): Long = if (endsAt == 0L) 0 else ((endsAt - System.currentTimeMillis()) / 60000 + 1).coerceAtLeast(0)
}

/** 再生リスト（キュー）：再生中・次に再生・その後に続く曲。押すとその曲へ、−で外す。下にシャッフル・リピート・タイマー */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(bridge: Bridge, player: PlayerState, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf<Bridge.Queue?>(null) }
    var edit by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    // 並べ替えで指を動かしている間は、シートや一覧が一緒に動かないようにする（引っかかりの原因）
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(player.uri) { q = runCatching { bridge.queue() }.getOrNull() ?: q }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF242424), dragHandle = { Grip() }, sheetGesturesEnabled = !dragging) {
        Column(Modifier.fillMaxHeight(0.92f).windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("次に再生", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    q?.context?.takeIf { it.isNotEmpty() }?.let { Text("$it を再生中", color = Sub, fontSize = 13.sp) }
                }
                Text(if (edit) "完了" else "編集", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { edit = !edit }.padding(10.dp))
            }
            LazyColumn(Modifier.weight(1f), userScrollEnabled = !dragging) {
                q?.current?.let { c -> item { QRow(c, true, false, {}, {}) } }
                fun refresh() { scope.launch { kotlinx.coroutines.delay(300); q = runCatching { bridge.queue() }.getOrNull() ?: q } }
                q?.queued?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { Text("次に再生", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                    item { DragList(list, edit, onDragging = { dragging = it }, onSkip = { scope.launch { runCatching { bridge.skipTo(it) }; refresh() } }, onRemove = { scope.launch { runCatching { bridge.unqueue(it) }; refresh() } }) { item, target, after -> scope.launch { runCatching { bridge.reorder(item, target, after) }; refresh() } } }
                }
                q?.next?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { Text("この後に再生" + (q?.context?.takeIf { it.isNotEmpty() }?.let { "：$it" } ?: ""), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                    item { DragList(list, edit, onDragging = { dragging = it }, onSkip = { scope.launch { runCatching { bridge.skipTo(it) }; refresh() } }, onRemove = { scope.launch { runCatching { bridge.unqueue(it) }; refresh() } }) { item, target, after -> scope.launch { runCatching { bridge.reorder(item, target, after) }; refresh() } } }
                }
            }
            // 下：シャッフル・リピート・タイマー
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarButton(Icons.shuffle, "シャッフル", player.shuffle) { scope.launch { runCatching { bridge.shuffle(!player.shuffle) } } }
                BarButton(Icons.repeat, if (player.repeat == 2) "1曲リピート" else "リピート", player.repeat > 0) { scope.launch { runCatching { bridge.repeat((player.repeat + 1) % 3) } } }
                BarButton(QIcons.timer, if (SleepTimer.endsAt > 0) "あと${SleepTimer.minutesLeft()}分" else "タイマー", SleepTimer.endsAt > 0) { timerOpen = true }
            }
        }
    }
    if (timerOpen) SleepTimerSheet(bridge) { timerOpen = false }
}

/** 右の「≡」を押さえたまま上下に動かすと並べ替えられる一覧（公式アプリと同じ） */
@Composable
private fun DragList(items: List<Bridge.QItem>, edit: Boolean, onDragging: (Boolean) -> Unit, onSkip: (Bridge.QItem) -> Unit, onRemove: (Bridge.QItem) -> Unit, onMove: (Bridge.QItem, Bridge.QItem, Boolean) -> Unit) {
    val list = remember(items) { androidx.compose.runtime.mutableStateListOf<Bridge.QItem>().apply { addAll(items) } }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(0f) }
    var startIndex by remember { mutableStateOf(-1) }
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
    Column {
        // 行ごとに key を付ける（並びが変わっても、つかんでいる行の指の動きが途切れないように）
        list.forEach { it -> androidx.compose.runtime.key(it.uid + it.uri) {
            val key = it.uid + it.uri
            val dragging = key == dragKey
            Box(
                Modifier.zIndexIf(dragging).graphicsLayerY(if (dragging) offset else 0f).background(if (dragging) Color(0xFF333333) else Color.Transparent),
            ) {
                QRow(it, false, edit, { onSkip(it) }, { onRemove(it) }, handle = Modifier.pointerInput(key) {
                    detectDragGestures(
                        onDragStart = { onDragging(true); dragKey = key; offset = 0f; startIndex = list.indexOfFirst { x -> x.uid + x.uri == key } },
                        onDragEnd = {
                            val from = startIndex
                            val to = list.indexOfFirst { x -> x.uid + x.uri == key }
                            dragKey = null; offset = 0f; onDragging(false)
                            if (from >= 0 && to >= 0 && from != to) {
                                val moved = list[to]
                                if (to > from) onMove(moved, list[to - 1], true) else onMove(moved, list[to + 1], false)
                            }
                        },
                        onDragCancel = { dragKey = null; offset = 0f; onDragging(false) },
                        onDrag = { change, d ->
                            change.consume()
                            offset += d.y
                            val cur = list.indexOfFirst { x -> x.uid + x.uri == key }
                            if (offset > rowPx / 2 && cur < list.lastIndex) { list.add(cur + 1, list.removeAt(cur)); offset -= rowPx }
                            else if (offset < -rowPx / 2 && cur > 0) { list.add(cur - 1, list.removeAt(cur)); offset += rowPx }
                        },
                    )
                })
            }
        } }
    }
}

private fun Modifier.zIndexIf(on: Boolean) = if (on) this.then(androidx.compose.ui.Modifier.zIndex(1f)) else this
private fun Modifier.graphicsLayerY(y: Float) = this.then(androidx.compose.ui.Modifier.graphicsLayer { translationY = y })

private object QIcons {
    val handle = line("M4 8h16M4 12h16M4 16h16", 1.8f)
    val timer = line("M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM12 9v4l2.5 2.5M9.5 2.5h5", 1.6f)
    val remove = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM7.5 12h9", 1.6f)
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(rememberVectorPainter(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (on) Accent else Color.White))
        Text(label, color = if (on) Accent else Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun QRow(i: Bridge.QItem, current: Boolean, edit: Boolean, onClick: () -> Unit, onRemove: () -> Unit, handle: Modifier? = null) {
    Row(Modifier.fillMaxWidth().height(60.dp).clickable(enabled = !current, onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (edit && !current) Box(Modifier.padding(end = 10.dp)) { Ico(QIcons.remove, 24, Color(0xFFE91429), 36, onClick = onRemove) }
        AsyncImage(i.image, null, Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Card2), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(i.title, color = if (current) Accent else Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(i.artist, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (handle != null) Box(handle.size(44.dp), contentAlignment = Alignment.Center) {
            Image(rememberVectorPainter(QIcons.handle), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Sub))
        }
    }
}

/** おやすみタイマー：指定の時間がたったら一時停止 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(bridge: Bridge, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val app = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main) }
    fun set(min: Int) {
        SleepTimer.job?.cancel()
        if (min == 0) { SleepTimer.endsAt = 0; Toast.makeText(ctx, "タイマーをオフにしました", Toast.LENGTH_SHORT).show(); return }
        SleepTimer.endsAt = System.currentTimeMillis() + min * 60_000L
        SleepTimer.job = app.launch { delay(min * 60_000L); runCatching { bridge.pause() }; SleepTimer.endsAt = 0 }
        Toast.makeText(ctx, "${if (min >= 60) "${min / 60}時間" else "${min}分"}後に停止します", Toast.LENGTH_SHORT).show()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF242424), dragHandle = { Grip() }) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) {
            Text("おやすみタイマー", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (SleepTimer.endsAt > 0) Text("あと ${SleepTimer.minutesLeft()} 分で停止", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            listOf(5 to "5分", 10 to "10分", 15 to "15分", 30 to "30分", 45 to "45分", 60 to "1時間").forEach { (m, label) ->
                Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { set(m); scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }.padding(horizontal = 20.dp, vertical = 14.dp))
            }
            if (SleepTimer.endsAt > 0) Text("タイマーをオフにする", color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { set(0); scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }.padding(horizontal = 20.dp, vertical = 14.dp))
        }
    }
}

/** ライブラリの「＋」：プレイリスト・フォルダを作る */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(bridge: Bridge, onCreated: (Card) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var naming by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF242424), dragHandle = { Grip() }) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
            if (naming == null) {
                CreateRow(line("M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", 1.6f), "プレイリスト", "楽曲やエピソードでプレイリストを作成します") { naming = "playlist"; name = "マイプレイリスト" }
                CreateRow(line("M3 6.5A1.5 1.5 0 0 1 4.5 5H10l2 2.5h7.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z", 1.6f), "フォルダ", "プレイリストを整理できます") { naming = "folder"; name = "新しいフォルダ" }
            } else {
                val req = remember { FocusRequester() }
                LaunchedEffect(Unit) { req.requestFocus() }
                Text(if (naming == "playlist") "プレイリストに名前を付けよう" else "フォルダに名前を付けよう", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                BasicTextField(
                    name, { name = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp).focusRequester(req),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "作成", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Accent).clickable {
                            val n = name.trim().ifEmpty { return@clickable }
                            val kind = naming
                            scope.launch {
                                if (kind == "playlist") runCatching { bridge.createPlaylist(n) }.onSuccess { uri ->
                                    Toast.makeText(ctx, "「$n」を作成しました", Toast.LENGTH_SHORT).show()
                                    if (uri.isNotEmpty()) onCreated(Card(uri, n, "", "", false))
                                }.onFailure { Toast.makeText(ctx, "作成できませんでした", Toast.LENGTH_SHORT).show() }
                                else runCatching { bridge.createFolder(n) }.onSuccess { Toast.makeText(ctx, "「$n」を作成しました", Toast.LENGTH_SHORT).show() }
                                state.hide(); onDismiss()
                            }
                        }.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF3A3A3A)), contentAlignment = Alignment.Center) {
            Image(rememberVectorPainter(icon), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Sub))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Sub, fontSize = 13.sp)
        }
    }
}

/** プロフィール（自分・ほかのユーザー）：アイコン・名前・フォロワー／フォロー中（押すと一覧）、フォローボタン、公開プレイリスト、最近聴いたアーティスト */
@Composable
fun ProfileScreen(bridge: Bridge, user: Bridge.User?, onBack: () -> Unit, onOpen: (Card) -> Unit, onEdited: () -> Unit = {}, userId: String? = null) {
    var p by remember(userId) { mutableStateOf<Bridge.Profile?>(null) }
    var reload by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var listOf by remember { mutableStateOf<Boolean?>(null) }   // true=フォロワー, false=フォロー中
    var following by remember(userId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(userId, reload) { runCatching { bridge.profile(userId) }.getOrNull()?.let { p = it; following = it.isFollowing } }
    val mine = p?.mine ?: (userId == null)
    val name = p?.name ?: if (mine) user?.name ?: "" else ""
    val image = p?.image ?: if (mine) user?.image ?: "" else ""
    if (editing) EditProfileSheet(bridge, name, image, onDone = { reload++; onEdited() }) { editing = false }
    androidx.activity.compose.BackHandler(enabled = listOf != null) { listOf = null }
    val tint = rememberArtColorPublic(image)
    LazyColumn(
        Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {},
        contentPadding = PaddingValues(bottom = 170.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint, Bg))).windowInsetsPadding(WindowInsets.statusBars).padding(bottom = 16.dp)) {
                Ico(Icons.back, 22, onClick = onBack)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(Bridge.User(name, image), 112)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        p?.let { pr ->
                            Row(Modifier.padding(top = 6.dp)) {
                                Text("${pr.followers}人のフォロワー", color = Sub, fontSize = 13.sp, modifier = Modifier.clickable { listOf = true })
                                Text(" • ", color = Sub, fontSize = 13.sp)
                                Text("${pr.following}人をフォロー中", color = Sub, fontSize = 13.sp, modifier = Modifier.clickable { listOf = false })
                            }
                        }
                    }
                }
                if (mine) Text(
                    "編集", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { editing = true }.padding(horizontal = 16.dp, vertical = 7.dp),
                ) else if (p != null) FollowButton(following, Modifier.padding(start = 16.dp, top = 8.dp)) {
                    following = !following
                    scope.launch { runCatching { bridge.followUser("spotify:user:" + p!!.id, following) } }
                }
            }
        }
        p?.let { pr ->
            if (pr.playlists.isNotEmpty()) {
                item { Text("プレイリスト", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                items(pr.playlists, key = { "p" + it.uri }) { c -> ProfileRow(c) { onOpen(c) } }
            }
            if (pr.artists.isNotEmpty()) {
                item { Text("最近聴いたアーティスト", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                items(pr.artists, key = { "a" + it.uri }) { c -> ProfileRow(c) { onOpen(c) } }
            }
        } ?: item { Loading() }
    }
    val id = p?.id
    if (listOf != null && id != null) FollowListScreen(bridge, id, listOf!!, onBack = { listOf = null }, onOpen = { c -> listOf = null; onOpen(c) })
}

@Composable
private fun FollowButton(on: Boolean, m: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        if (on) "フォロー中" else "フォローする", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = m.clip(CircleShape).background(if (on) Color(0x33FFFFFF) else Color.Transparent)
            .then(if (on) Modifier else Modifier.background(Color(0x22FFFFFF))).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

/** フォロワー／フォロー中の一覧。右のボタンでフォローの切り替え */
@Composable
private fun FollowListScreen(bridge: Bridge, id: String, followers: Boolean, onBack: () -> Unit, onOpen: (Card) -> Unit) {
    var list by remember(id, followers) { mutableStateOf<List<Card>?>(null) }
    val state = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, followers) { list = runCatching { bridge.followList(id, followers) }.getOrDefault(emptyList()); list?.forEach { state[it.uri] = it.pinned } }
    Column(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.back, 22, onClick = onBack)
            Text(if (followers) "フォロワー" else "フォロー中", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 170.dp)) {
            val l = list
            if (l == null) item { Loading() }
            else if (l.isEmpty()) item { Text(if (followers) "まだフォロワーはいません" else "まだ誰もフォローしていません", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
            else items(l, key = { it.uri }) { c ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(c) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(c.image, null, Modifier.size(56.dp).clip(CircleShape).background(Card2), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 1)
                    }
                    val on = state[c.uri] == true
                    FollowButton(on) { state[c.uri] = !on; scope.launch { runCatching { bridge.followUser(c.uri, !on) } } }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(c: Card, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(c.image, null, Modifier.size(56.dp).clip(if (c.round) CircleShape else RoundedCornerShape(4.dp)).background(Card2), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 12.dp)) {
            Text(c.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.subtitle, color = Sub, fontSize = 13.sp, maxLines = 1)
        }
    }
}


/** プロフィールを編集（公式アプリと同じ：写真をタップして変更、名前を入力して保存） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(bridge: Bridge, name0: String, image0: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(name0) }
    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u -> if (u != null) picked = u }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF242424), dragHandle = { Grip() }) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("プロフィールを編集", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            Box(Modifier.padding(16.dp).size(150.dp).clip(CircleShape).background(Card2).clickable { pick.launch("image/*") }, contentAlignment = Alignment.Center) {
                AsyncImage(picked ?: image0, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Text("写真を変更", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { pick.launch("image/*") }.padding(8.dp))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("名前", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                BasicTextField(name, { name = it.take(30) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Accent),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A3A)).padding(horizontal = 12.dp, vertical = 10.dp))
            }
            Text(
                if (saving) "保存中…" else "保存", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(Color.White).clickable(enabled = !saving && name.isNotBlank()) {
                    saving = true
                    scope.launch {
                        val jpeg = picked?.let { u -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { toJpeg(ctx, u) }.getOrNull() } }
                        val ok = runCatching { bridge.editProfile(name.trim().takeIf { it != name0 }, jpeg) }.getOrDefault(false)
                        saving = false
                        Toast.makeText(ctx, if (ok) "プロフィールを更新しました" else "保存できませんでした", Toast.LENGTH_SHORT).show()
                        if (ok) { onDone(); state.hide(); onDismiss() }
                    }
                }.padding(horizontal = 36.dp, vertical = 12.dp),
            )
        }
    }
}

/** 選んだ写真を 640px 以下の正方形の JPEG にする（端末には保存しない） */
private fun toJpeg(ctx: android.content.Context, u: android.net.Uri): ByteArray {
    val src = ctx.contentResolver.openInputStream(u)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
    val side = minOf(src.width, src.height)
    val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val out = android.graphics.Bitmap.createScaledBitmap(sq, minOf(640, side), minOf(640, side), true)
    return java.io.ByteArrayOutputStream().also { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}
