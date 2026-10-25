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
    val receivedAt: Long = 0,
) {
    fun positionNow(): Long =
        if (!playing) pos else (pos + (SystemClock.elapsedRealtime() - receivedAt)).coerceAtMost(dur.coerceAtLeast(pos))
}

/**
 * 裏で動く Web Player（assets/bridge.js）とのやりとり。
 * データの取得と再生の操作は Web Player 自身の仕組み（ログイン情報・DRM を含む）にそのまま任せる。
 */
class Bridge(private val web: WebView) {
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

    suspend fun home(): HomeFeed {
        val o = call("home") as JSONObject
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
        receivedAt = SystemClock.elapsedRealtime(),
    )
}
