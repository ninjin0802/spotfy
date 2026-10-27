package com.ninjin.spotifyweb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** デバッグモード。設定には保存せず、アプリを起動し直すと必ずオフに戻る */
object DebugMode {
    @Volatile var on = false
    /** 画面が開いていれば、切り替えをそちらに伝える（ログ・WebView の調査・ページの記録をまとめて切り替える） */
    @Volatile var changed: ((Boolean) -> Unit)? = null
}

/**
 * adb から `am broadcast -n com.ninjin.spotifyweb/.DebugModeReceiver --ez on true|false` でデバッグモードを切り替える。
 * 送り主に DUMP 権限を求める（adb の shell は持っていて、ふつうのアプリは持てない）ので、ほかのアプリからは切り替えられない
 */
class DebugModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val on = intent.getBooleanExtra("on", true)
        DebugMode.on = on
        DebugMode.changed?.invoke(on)
    }
}

/**
 * 広告処理・ページの読み込み・Web Player の部品の確認などの直近イベント。
 * Spotify の更新で壊れたときに後から追えるよう、共有や adb で取り出せるようにする。
 * 1 行ごとに設定を書き直すと重いので、手元に溜めて少し後にまとめて保存する。
 */
class DebugLog(private val prefs: SharedPreferences) {

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var savePending = false

    /**
     * デバッグモード（「このアプリについて」のアイコンを 10 回押す）のとき、全部の記録をこのファイルに足していく。
     * 置き場所は、Android 11 以降は adb で取り出せる Android/data/…/files/logs、10 以前はアプリの中（adb では取り出せない。共有で送る）。
     * 大きくなったら 1 世代だけ残して作り直す
     */
    @Volatile var fullLog: java.io.File? = null
        set(value) {
            // オフにするときは、まだ書いていない分（最大 3 秒）を今のファイルに書いてから切り替える
            val old = field
            if (value == null && old != null) writeFull(old)
            field = value
        }
    private val fullBuf = StringBuilder()
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val lines by lazy {
        ArrayDeque(prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(MAX_LINES))
    }
    private val save = Runnable {
        savePending = false
        synchronized(lines) { prefs.edit().putString(KEY, lines.joinToString("\n")).apply() }
        writeFull()
    }

    private fun writeFull(file: java.io.File? = fullLog) {
        if (file == null) return
        val chunk = synchronized(fullBuf) { fullBuf.toString().also { fullBuf.setLength(0) } }
        if (chunk.isEmpty()) return
        writer.execute {
            runCatching {
                file.parentFile?.mkdirs()
                if (file.length() > FULL_MAX_BYTES) file.renameTo(java.io.File(file.path + ".1"))
                file.appendText(chunk)
            }
        }
    }

    fun record(event: String, detail: String = "") {
        val safeEvent = event.filter { it.isLetterOrDigit() || it in "._-" }.take(64).ifBlank { "unknown" }
        val full = fullLog != null
        val flat = detail.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
        val stamp = STAMP.format(LocalDateTime.now())
        val line = "$stamp $safeEvent ${flat.take(160)}"
        // 通信 1 件ごとなどの細かい記録は全部の記録にだけ入れる。いつものログ（設定に保存する 800 行）に入れると、広告や再生の記録を押し出してしまう
        if (!full || !FULL_ONLY.containsMatchIn(safeEvent)) {
            Log.i(TAG, line)
            synchronized(lines) {
                lines.addLast(line)
                while (lines.size > MAX_LINES) lines.removeFirst()
            }
        }
        if (full) synchronized(fullBuf) { fullBuf.append(stamp).append(' ').append(safeEvent).append(' ').append(flat.take(2000)).append('\n') }
        // 続けて記録が来ても先送りし続けないよう、最初の 1 行から 3 秒後にまとめて保存する
        if (!savePending) { savePending = true; handler.postDelayed(save, 3_000) }
    }

    fun lines(): List<String> = synchronized(lines) { lines.toList() }

    /** 待たずにすぐ保存する（アプリが裏に回るとき） */
    fun flush() {
        handler.removeCallbacks(save)
        save.run()
    }

    /** デバッグモードの全部の記録を、書き終わるのを待ってから返す（共有する前） */
    fun fullLogFile(): java.io.File? {
        val file = fullLog ?: return null
        flush()
        runCatching { writer.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS) }
        return file.takeIf { it.exists() }
    }

    /** ログアウトしたら前のアカウントの記録は消す。全部の記録のファイルも、デバッグモードがオフでも消す */
    fun clear(fullLogFile: java.io.File) {
        synchronized(lines) { lines.clear() }
        synchronized(fullBuf) { fullBuf.setLength(0) }
        deleteFullLog(fullLogFile)
        handler.removeCallbacks(save)
        prefs.edit().remove(KEY).apply()
    }

    /** 全部の記録のファイル（と 1 世代前）を消す */
    private fun deleteFullLog(file: java.io.File) {
        writer.execute { file.delete(); java.io.File(file.path + ".1").delete() }
    }

    /** adb から `am start --ez dumpDebugLog true` で起こされたときに、全体を logcat に流す */
    fun dumpToLogcat() {
        val all = lines()
        Log.i(TAG, "---- dump begin (${all.size} lines) ----")
        all.forEach { Log.i(TAG, it) }
        Log.i(TAG, "---- dump end ----")
    }

    // ---- spotify.com へのリクエストの形（ホスト＋パス先頭 3 段） ----
    // ID っぽい段は * に置き換え、クエリは残さない。同じ形は 1 回だけ、広告らしいパスは毎回（上限あり）。
    @Volatile var adPlaying = false
    private val seenRequestPaths = java.util.Collections.synchronizedSet(HashSet<String>())
    @Volatile private var adPathCount = 0

    fun traceRequest(host: String, url: Uri, method: String) {
        // デバッグモードでは、すべての通信の行き先を残す（クエリにはトークンなどが入るので残さない）
        if (fullLog != null) { record("net.req", "$method $host${url.path.orEmpty()}" + if (adPlaying) " [ad]" else ""); return }
        if (!host.endsWith("spotify.com") || host == "open.spotify.com" && url.pathSegments.firstOrNull() != "api") return
        if (adPathCount >= 150 && seenRequestPaths.size >= 400) return
        val segs = url.pathSegments.take(3).map { seg ->
            if (seg.length > 20 || seg.any { it.isDigit() } && seg.length > 8) "*" else seg.take(40)
        }
        val key = host + "/" + segs.joinToString("/")
        val tag = if (adPlaying) " [ad]" else ""
        if (AD_PATH.containsMatchIn(url.path.orEmpty()) && adPathCount < 150) {
            adPathCount++
            record("net.adpath", key + tag)
        } else if (seenRequestPaths.size < 400 && seenRequestPaths.add(key)) {
            record("net", key + tag)
        }
    }

    companion object {
        const val TAG = "spotfy-debug"
        private const val KEY = "debug.log"
        private const val MAX_LINES = 800
        private const val FULL_MAX_BYTES = 8L * 1024 * 1024
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private val FULL_ONLY = Regex("^(net\\.(req|fetch|xhr|canvas)|ws\\.|seam\\.tick|xf\\.fade)")
        private val AD_PATH = Regex("/(ads?|ad-logic|adlogic|ad-?break|sponsor[a-z-]*|instream[a-z-]*|slots?)(/|$)", RegexOption.IGNORE_CASE)
    }
}
