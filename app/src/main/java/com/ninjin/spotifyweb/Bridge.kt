package com.ninjin.spotifyweb

import android.os.SystemClock
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** ホームなどに並ぶカード（アルバム・プレイリスト・アーティストなど） */
data class Card(val uri: String, val title: String, val subtitle: String, val image: String, val round: Boolean)

data class Shelf(val title: String, val items: List<Card>)

data class HomeFeed(val greeting: String, val shortcuts: List<Card>, val shelves: List<Shelf>)

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
    val receivedAt: Long = 0,
) {
    /** 「プレイリストから再生中」などの見出し */
    val contextLabel: String get() = when {
        contextUri.startsWith("spotify:album:") -> "アルバムから再生中"
        contextUri.startsWith("spotify:artist:") -> "アーティストから再生中"
        contextUri.contains(":collection") -> "お気に入りの曲から再生中"
        contextUri.startsWith("spotify:show:") -> "ポッドキャストから再生中"
        contextUri.isNotEmpty() -> "プレイリストから再生中"
        else -> "再生中"
    }

    fun positionNow(): Long =
        if (!playing) pos else (pos + (SystemClock.elapsedRealtime() - receivedAt)).coerceAtMost(dur.coerceAtLeast(pos))
}

/**
 * 裏で動く Web Player（assets/bridge.js）とのやりとり。
 * データの取得と再生の操作は Web Player 自身の仕組み（ログイン情報・DRM を含む）にそのまま任せる。
 */
class Bridge(private val web: WebView, private val cache: android.content.SharedPreferences) {
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Any?>>()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player

    /** bridge.js のメソッドを呼ぶ。結果は JSON（JSONObject / JSONArray / 値） */
    suspend fun call(method: String, args: JSONObject = JSONObject()): Any? {
        val id = nextId.getAndIncrement()
        val done = CompletableDeferred<Any?>()
        pending[id] = done
        web.post {
            web.evaluateJavascript("window.__mb && __mb.call($id, ${JSONObject.quote(method)}, $args)", null)
        }
        return try {
            withTimeout(20_000) { done.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** ページからのメッセージのうち、橋の分を受け取る。受け取ったら true */
    fun onMessage(m: JSONObject): Boolean {
        when (m.optString("type")) {
            "bready" -> _ready.value = true
            "bres" -> {
                val d = pending[m.optInt("id")] ?: return true
                if (m.optBoolean("ok")) d.complete(m.opt("value")) else d.completeExceptionally(RuntimeException(m.optString("error")))
            }
            "bstate" -> _player.value = parseState(m.optJSONObject("value") ?: JSONObject())
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

    suspend fun home(): HomeFeed {
        val o = call("home") as JSONObject
        cache.edit().putString("home", o.toString()).apply()
        return parseHome(o)
    }

    private fun parseHome(o: JSONObject): HomeFeed {
        return HomeFeed(
            greeting = o.optString("greeting"),
            shortcuts = cards(o.optJSONArray("shortcuts")),
            shelves = (o.optJSONArray("sections") ?: JSONArray()).let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    Shelf(s.optString("title"), cards(s.optJSONArray("items")))
                }
            },
        )
    }

    suspend fun play(uri: String, track: String? = null) = call("play", JSONObject().put("uri", uri).apply { if (track != null) put("track", track) })
    suspend fun toggle() = call("toggle")
    suspend fun next() = call("next")
    suspend fun prev() = call("prev")
    suspend fun seek(ms: Long) = call("seek", JSONObject().put("ms", ms))
    suspend fun shuffle(on: Boolean) = call("shuffle", JSONObject().put("on", on))
    suspend fun repeat(mode: Int) = call("repeat", JSONObject().put("mode", mode))
    suspend fun like(uri: String, on: Boolean) = call("like", JSONObject().put("uri", uri).put("on", on))
    suspend fun quality(level: String) = call("quality", JSONObject().put("level", level)) == true
    suspend fun bitrate(): Int = (call("bitrate") as? Number)?.toInt() ?: 0

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
        Card(c.optString("uri"), c.optString("title"), c.optString("subtitle"), c.optString("image"), c.optBoolean("round"))
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
        receivedAt = SystemClock.elapsedRealtime(),
    )
}
