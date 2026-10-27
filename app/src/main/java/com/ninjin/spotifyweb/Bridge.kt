package com.ninjin.spotifyweb

import android.os.SystemClock
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** ホームなどに並ぶカード（アルバム・プレイリスト・アーティストなど） */
data class Card(val uri: String, val title: String, val subtitle: String, val image: String, val round: Boolean, val kind: String = "", val pinned: Boolean = false, val color: String = "")

data class Shelf(val title: String, val items: List<Card>, val feed: Boolean = false)

data class Track(val uri: String, val title: String, val artist: String, val image: String, val dur: Long)

/** アルバム・プレイリスト・アーティストのページ */
data class Page(
    val kind: String, val uri: String, val title: String, val subtitle: String, val image: String,
    val round: Boolean, val header: Boolean, val description: String, val artist: String, val artistUri: String,
    val saved: Boolean, val tracks: List<Track>, val shelves: List<Shelf>, val date: String = "",
)

data class Chip(val id: String, val label: String, val sub: List<Chip> = emptyList())

data class HomeFeed(val greeting: String, val shortcuts: List<Card>, val shelves: List<Shelf>, val chips: List<Chip> = emptyList(), val shows: List<Card> = emptyList(), val emptyPodcasts: Boolean = false)

/** 再生状態。位置は [at]（Web Player の時刻）時点の値なので、表示時に経過分を足す */
data class PlayerState(
    val has: Boolean = false,
    val uri: String = "",
    val title: String = "",
    val artist: String = "",
    val image: String = "",
    val playing: Boolean = false,
    val pos: Long = 0,
    val dur: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val liked: Boolean = false,
    val contextName: String = "",
    val contextUri: String = "",
    /** ほかの機器（Spotify Connect）で再生中ならその名前。このスマホなら空 */
    val device: String = "",
    val artistUri: String = "",
    val deviceType: String = "",
    val receivedAt: Long = 0,
) {
    /** 「プレイリストから再生中」などの見出し */
    val contextLabel: String get() = when {
        contextUri.startsWith("spotify:album:") -> tr("アルバムから再生中", "Playing from album")
        contextUri.startsWith("spotify:artist:") -> tr("アーティストから再生中", "Playing from artist")
        contextUri.contains(":collection") -> tr("お気に入りの曲から再生中", "Playing from Liked Songs")
        contextUri.startsWith("spotify:show:") -> tr("ポッドキャストから再生中", "Playing from podcast")
        contextUri.isNotEmpty() -> tr("プレイリストから再生中", "Playing from playlist")
        else -> tr("再生中", "Now playing")
    }

    fun positionNow(): Long =
        if (!playing) pos else (pos + (SystemClock.elapsedRealtime() - receivedAt)).coerceAtMost(dur.coerceAtLeast(pos))
}

/**
 * 裏で動く Web Player（assets/bridge.js）とのやりとり。
 * データの取得と再生の操作は Web Player 自身の仕組み（ログイン情報・DRM を含む）にそのまま任せる。
 */
class Bridge(
    private val web: WebView,
    private val cache: android.content.SharedPreferences,
    /** 呼び出しの失敗をデバッグログに残す（Spotify の更新で壊れたとき、どの機能かを後から追えるように） */
    private val log: (event: String, detail: String) -> Unit = { _, _ -> },
) {
    /**
     * 再生を頼む直前に呼ぶ。押してすぐ裏に回すと、鳴り始めた時点ではもうフォアグラウンドサービスを起動できず
     * （Android の制限）、裏でアプリごと止められてしまうので、画面が前にある押した瞬間に起動しておく
     */
    var onWillPlay: () -> Unit = {}
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Any?>>()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    /** Web Player の中の部品が見つからず使えない機能（Spotify 側の更新で起きる）。設定画面に出す */
    private val _missing = MutableStateFlow<List<String>>(emptyList())
    val missing: StateFlow<List<String>> = _missing

    /** ライブラリの中身が変わったら増える（ライブラリの画面が読み直す） */
    val libraryVersion = androidx.compose.runtime.mutableIntStateOf(0)

    private val _player = MutableStateFlow(
        cache.getString("player", null)?.let { runCatching { parseState(JSONObject(it)) }.getOrNull() } ?: PlayerState()
    )
    val player: StateFlow<PlayerState> = _player

    /** bridge.js のメソッドを呼ぶ。結果は JSON（JSONObject / JSONArray / 値） */
    suspend fun call(method: String, args: JSONObject = JSONObject()): Any? {
        val id = nextId.getAndIncrement()
        val done = CompletableDeferred<Any?>()
        pending[id] = done
        return try {
            withTimeout(40_000) {
                // 起動直後は橋がまだ無く、送っても誰も受け取らない（40 秒待って失敗していた）ので、準備ができるまで待ってから送る
                _ready.first { it }
                web.post {
                    web.evaluateJavascript("window.__mb && __mb.call($id, ${JSONObject.quote(method)}, $args)", null)
                }
                done.await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            log("bridge.timeout", method)
            throw e
        } catch (e: RuntimeException) {
            if (e !is kotlinx.coroutines.CancellationException) log("bridge.error", "$method ${e.message.orEmpty()}")
            throw e
        } finally {
            pending.remove(id)
        }
    }

    /** ページからのメッセージのうち、橋の分を受け取る。受け取ったら true */
    fun onMessage(m: JSONObject): Boolean {
        when (m.optString("type")) {
            "bready" -> _ready.value = true
            "bhealth" -> {
                val list = m.optJSONArray("missing")
                _missing.value = List(list?.length() ?: 0) { list!!.optString(it) }
                return false  // アプリ側でもログに残す
            }
            "bres" -> {
                val d = pending[m.optInt("id")] ?: return true
                if (m.optBoolean("ok")) d.complete(m.opt("value")) else d.completeExceptionally(RuntimeException(m.optString("error")))
            }
            "bstate" -> {
                val v = m.optJSONObject("value") ?: JSONObject()
                // 起動直後の Web Player はまだ曲を読み込んでおらず「曲なし」と言ってくる。
                // そのたびにミニプレーヤーを消すと、出て→消えて→また出るので、前の曲を一時停止のまま出しておく
                // 押した結果を待っている間は、Web Player の古い状態で表示を戻さない（▶ → ⏸ → ▶ → ⏸ とちらつく）
                val s = if (!v.optBoolean("has") && _player.value.has) _player.value.copy(playing = false) else parseState(v)
                _player.value = expectPlaying?.let { s.copy(playing = it) } ?: s
                // 次の起動ですぐミニプレーヤーを出せるよう、曲名と画像だけ覚えておく（一時停止の状態で出す）
                if (v.optBoolean("has")) cache.edit().putString("player", v.put("playing", false).toString()).apply()
            }
            else -> return false
        }
        return true
    }

    /** ページを読み込み直したら、準備ができるまで待つ */
    fun reset() {
        _ready.value = false
    }

    /** 前回のホーム（起動直後、Web Player の準備ができるまでの間に表示する。曲名や画像の URL だけで、音声は保存しない） */
    fun cachedHome(): HomeFeed? = cache.getString("home", null)?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() }

    suspend fun home(facet: String = ""): HomeFeed {
        val o = call("home", JSONObject().put("facet", facet)) as JSONObject
        if (facet.isEmpty()) cache.edit().putString("home", o.toString()).apply()
        return parseHome(o)
    }

    private fun parseHome(o: JSONObject): HomeFeed {
        return HomeFeed(
            greeting = o.optString("greeting"),
            shortcuts = cards(o.optJSONArray("shortcuts")),
            shows = cards(o.optJSONArray("shows")),
            emptyPodcasts = o.optBoolean("emptyPodcasts"),
            chips = (o.optJSONArray("chips") ?: JSONArray()).let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { c ->
                    Chip(c.optString("id"), c.optString("label"), (c.optJSONArray("sub") ?: JSONArray()).let { b -> (0 until b.length()).map { j -> b.getJSONObject(j).let { Chip(it.optString("id"), it.optString("label")) } } })
                } }
            },
            shelves = (o.optJSONArray("sections") ?: JSONArray()).let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    Shelf(s.optString("title"), cards(s.optJSONArray("items")), s.optBoolean("feed"))
                }
            },
        )
    }

    suspend fun play(uri: String, track: String? = null): Any? {
        onWillPlay()
        return call("play", JSONObject().put("uri", uri).apply { if (track != null) put("track", track) })
    }
    /** 押されたあと、結果が出るまで表示しておく再生状態（起動直後に押すと、準備ができるまで数秒かかる） */
    @Volatile private var expectPlaying: Boolean? = null

    /**
     * 再生／一時停止。結果を待たずに表示だけ先に切り替え、失敗したら戻す。
     * 押したときに見えていた表示で「再生」か「一時停止」かを決めて送る（待っている間に状態が変わっても、押した意図どおりにする）
     */
    suspend fun toggle(): Any? {
        val before = _player.value
        val want = !before.playing
        fun show(playing: Boolean) { _player.value = _player.value.copy(playing = playing, pos = before.positionNow(), receivedAt = SystemClock.elapsedRealtime()) }
        if (want) onWillPlay()
        expectPlaying = want
        show(want)
        return try {
            call(if (want) "resume" else "pause")
        } catch (e: Exception) {
            if (_player.value.playing == want) show(before.playing)
            throw e
        } finally {
            expectPlaying = null
        }
    }
    suspend fun next() = call("next")
    suspend fun prev() = call("prev")
    suspend fun seek(ms: Long) = call("seek", JSONObject().put("ms", ms))
    suspend fun shuffle(on: Boolean) = call("shuffle", JSONObject().put("on", on))
    suspend fun repeat(mode: Int) = call("repeat", JSONObject().put("mode", mode))
    suspend fun like(uri: String, on: Boolean) = call("like", JSONObject().put("uri", uri).put("on", on))
    suspend fun quality(level: String) = call("quality", JSONObject().put("level", level)) == true
    suspend fun gapless(on: Boolean) = call("gapless", JSONObject().put("on", on))
    suspend fun prewarm() = call("prewarm")
    suspend fun crossfade(sec: Int) = call("crossfade", JSONObject().put("sec", sec))
    suspend fun bitrate(): Int = (call("bitrate") as? Number)?.toInt() ?: 0

    /** URI に合わせてページを読む（お気に入りの曲はライブラリから） */
    // 開いたページは覚えておき、もう一度開いたときはすぐ出す（裏で新しい内容に差し替える）
    private val pages = object : LinkedHashMap<String, Page>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Page>?) = size > 40
    }
    fun cachedPage(uri: String): Page? = synchronized(pages) { pages[uri] }

    suspend fun page(uri: String): Page = loadPage(uri).also { synchronized(pages) { pages[uri] = it } }

    private suspend fun loadPage(uri: String): Page {
        if (uri.contains(":collection")) {
            val a = call("liked") as JSONArray
            return Page("playlist", uri, tr("お気に入りの曲", "Liked Songs"), songs(a.length()), "", false, false, "", "", "", true, tracks(a), emptyList())
        }
        val method = when (uri.split(':').getOrNull(1)) { "album" -> "album"; "artist" -> "artist"; "page", "genre" -> "genre"; else -> "playlist" }
        val o = call(method, JSONObject().put("uri", uri)) as JSONObject
        return Page(
            o.optString("kind"), o.optString("uri"), o.optString("title"), o.optString("subtitle"), o.optString("image"),
            o.optBoolean("round"), o.optBoolean("header"), o.optString("description"), o.optString("artist"), o.optString("artistUri"),
            o.optBoolean("saved"), tracks(o.optJSONArray("tracks")),
            (o.optJSONArray("shelves") ?: JSONArray()).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { Shelf(it.optString("title"), cards(it.optJSONArray("items"))) } } },
            o.optString("date"),
        )
    }

    suspend fun search(q: String): List<Card> = cards(call("search", JSONObject().put("q", q)) as? JSONArray)
    suspend fun library(): List<Card> = cards(call("library") as? JSONArray).also { cache.edit().putString("library", JSONArray(it.map { c -> JSONObject().put("uri", c.uri).put("title", c.title).put("subtitle", c.subtitle).put("image", c.image).put("round", c.round).put("kind", c.kind).put("pinned", c.pinned) }).toString()).apply() }
    fun cachedLibrary(): List<Card> = cache.getString("library", null)?.let { runCatching { cards(JSONArray(it)) }.getOrNull() } ?: emptyList()
    suspend fun browse(): List<Card> = cards(call("browse") as? JSONArray).also { cache.edit().putString("browse", JSONArray(it.map { c -> JSONObject().put("uri", c.uri).put("title", c.title).put("subtitle", c.subtitle).put("image", c.image) }).toString()).apply() }
    fun cachedBrowse(): List<Card> = cache.getString("browse", null)?.let { runCatching { cards(JSONArray(it)) }.getOrNull() } ?: emptyList()
    suspend fun recent(): List<Card> = cards(call("recent") as? JSONArray)
    suspend fun saveRecent(uri: String) = call("saveRecent", JSONObject().put("uri", uri))
    suspend fun removeRecent(uri: String) = call("removeRecent", JSONObject().put("uri", uri))
    suspend fun follow(uri: String, on: Boolean) = call("follow", JSONObject().put("uri", uri).put("on", on)).also { libraryVersion.intValue++ }

    private fun tracks(a: JSONArray?): List<Track> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val t = a.optJSONObject(i) ?: return@mapNotNull null
        Track(t.optString("uri"), t.optString("title"), t.optString("artist"), t.optString("image"), t.optLong("dur"))
    }

    data class TrackInfo(val album: String, val albumName: String, val artist: String, val artistName: String, val liked: Boolean)

    suspend fun trackInfo(uri: String): TrackInfo {
        val o = call("trackInfo", JSONObject().put("uri", uri)) as JSONObject
        return TrackInfo(o.optString("album"), o.optString("albumName"), o.optString("artist"), o.optString("artistName"), o.optBoolean("liked"))
    }
    suspend fun inLibrary(uri: String) = call("inLibrary", JSONObject().put("uri", uri)) == true
    suspend fun addToQueue(uri: String) = call("queue", JSONObject().put("uri", uri))

    data class Device(val id: String, val name: String, val type: String)
    data class Devices(val self: String, val active: String, val list: List<Device>)

    suspend fun devices(): Devices {
        val o = call("devices") as JSONObject
        val a = o.optJSONArray("list") ?: JSONArray()
        return Devices(o.optString("self"), o.optString("active"), (0 until a.length()).map { i -> a.getJSONObject(i).let { Device(it.optString("id"), it.optString("name"), it.optString("type")) } })
    }
    suspend fun whatsNew(type: String): List<Card> = cards(call("whatsNew", JSONObject().put("type", type)) as? JSONArray)
    /** 最近再生したもの（URI と再生した時刻 ms、新しい順） */
    data class Recent(val uri: String, val at: Long, val title: String, val artist: String, val image: String, val round: Boolean)

    /** 「最近」。前回の結果を覚えておき、次に開いたときに先に出す */
    suspend fun recents(): List<Recent> = (call("recents") as? JSONArray)
        ?.also { cache.edit().putString("recents", it.toString()).apply() }.let(::parseRecents)
    fun cachedRecents(): List<Recent>? = cache.getString("recents", null)?.let { runCatching { parseRecents(JSONArray(it)) }.getOrNull() }
    private fun parseRecents(a: JSONArray?): List<Recent> = if (a == null) emptyList() else (0 until a.length()).map { i ->
        a.getJSONObject(i).let { Recent(it.optString("uri"), it.optLong("at"), it.optString("title"), it.optString("artist"), it.optString("image"), it.optBoolean("round")) }
    }
    suspend fun transfer(id: String) = call("transfer", JSONObject().put("id", id)) == true

    data class Lyrics(val synced: Boolean, val lines: List<Pair<Long, String>>, val bg: Int)

    suspend fun lyrics(uri: String, image: String): Lyrics {
        val o = call("lyrics", JSONObject().put("uri", uri).put("image", image)) as JSONObject
        val a = o.optJSONArray("lines") ?: JSONArray()
        return Lyrics(o.optBoolean("synced"), (0 until a.length()).map { i -> a.getJSONObject(i).let { it.optLong("t") to it.optString("w") } }, o.optInt("bg"))
    }

    data class About(val uri: String, val name: String, val image: String, val listeners: Long, val saved: Boolean, val bio: String, val verified: Boolean)

    suspend fun about(uri: String): About {
        val o = call("about", JSONObject().put("uri", uri)) as JSONObject
        return About(o.optString("uri"), o.optString("name"), o.optString("image"), o.optLong("listeners"), o.optBoolean("saved"), o.optString("bio"), o.optBoolean("verified"))
    }

    suspend fun nextUp(): Track? = (call("nextUp") as? JSONObject)?.let { Track(it.optString("uri"), it.optString("title"), it.optString("artist"), it.optString("image"), 0) }

    data class Profile(
        val id: String, val mine: Boolean, val isFollowing: Boolean, val name: String, val image: String,
        val followers: Int, val following: Int, val playlists: List<Card>, val artists: List<Card>,
    )

    /** id が null なら自分のプロフィール */
    suspend fun profile(id: String? = null): Profile {
        val o = call("profile", JSONObject().apply { if (id != null) put("id", id) }) as JSONObject
        return Profile(
            o.optString("id"), o.optBoolean("mine"), o.optBoolean("following"), o.optString("name"), o.optString("image"),
            o.optInt("followers"), o.optInt("followingCount"), cards(o.optJSONArray("playlists")), cards(o.optJSONArray("artists")),
        )
    }
    suspend fun followList(id: String, followers: Boolean): List<Card> = cards(call("followList", JSONObject().put("id", id).put("which", if (followers) "followers" else "following")) as? JSONArray)
    suspend fun followUser(uri: String, on: Boolean) = call("followUser", JSONObject().put("uri", uri).put("on", on)).also { libraryVersion.intValue++ }

    data class QItem(val uri: String, val uid: String, val title: String, val artist: String, val image: String)
    data class Queue(val current: QItem?, val queued: List<QItem>, val next: List<QItem>, val context: String)

    suspend fun queue(): Queue {
        val o = call("queueList") as JSONObject
        fun q(j: JSONObject?) = j?.let { QItem(it.optString("uri"), it.optString("uid"), it.optString("title"), it.optString("artist"), it.optString("image")) }
        fun ql(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { q(a.optJSONObject(it)) }
        return Queue(q(o.optJSONObject("current")), ql(o.optJSONArray("queued")), ql(o.optJSONArray("next")), o.optString("context"))
    }
    suspend fun skipTo(i: QItem) = call("skipTo", JSONObject().put("uri", i.uri).put("uid", i.uid))
    suspend fun reorder(item: QItem, target: QItem, after: Boolean) = call(
        "reorder",
        JSONObject().put("item", JSONObject().put("uri", item.uri).put("uid", item.uid)).put("target", JSONObject().put("uri", target.uri).put("uid", target.uid)).put("after", after),
    )
    suspend fun unqueue(i: QItem) = call("unqueue", JSONObject().put("uri", i.uri).put("uid", i.uid))
    suspend fun createPlaylist(name: String) = (call("createPlaylist", JSONObject().put("name", name)) as? String ?: "").also { libraryVersion.intValue++ }
    suspend fun createFolder(name: String) = call("createFolder", JSONObject().put("name", name)).also { libraryVersion.intValue++ }
    suspend fun pause() = call("pause")

    /** 名前（null なら変えない）と写真（JPEG、null なら変えない）を保存 */
    suspend fun editProfile(name: String?, jpeg: ByteArray?) = call(
        "editProfile",
        JSONObject().apply {
            if (name != null) put("name", name)
            if (jpeg != null) put("image", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP))
        },
    ) == true

    data class User(val name: String, val image: String)

    /** ログイン中のユーザー（名前とアイコン）。前回の値も覚えておく */
    fun cachedUser(): User? = cache.getString("user", null)?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { User(it.optString("name"), it.optString("image")) }

    suspend fun user(): User? {
        val o = call("user") as? JSONObject ?: return null
        cache.edit().putString("user", o.toString()).apply()
        return User(o.optString("name"), o.optString("image"))
    }

    private fun cards(a: JSONArray?): List<Card> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val c = a.optJSONObject(i) ?: return@mapNotNull null
        Card(c.optString("uri"), c.optString("title"), c.optString("subtitle"), c.optString("image"), c.optBoolean("round"), c.optString("kind"), c.optBoolean("pinned"), c.optString("color"))
    }

    private fun parseState(o: JSONObject) = PlayerState(
        has = o.optBoolean("has"),
        uri = o.optString("uri"),
        title = o.optString("title"),
        artist = o.optString("artist"),
        image = o.optString("image"),
        playing = o.optBoolean("playing"),
        pos = o.optLong("pos"),
        dur = o.optLong("dur"),
        shuffle = o.optBoolean("shuffle"),
        repeat = o.optInt("repeat"),
        liked = o.optBoolean("liked"),
        contextName = o.optString("contextName"),
        contextUri = o.optString("contextUri"),
        device = o.optString("device"),
        artistUri = o.optString("artistUri"),
        deviceType = o.optString("deviceType"),
        receivedAt = SystemClock.elapsedRealtime(),
    )
}
