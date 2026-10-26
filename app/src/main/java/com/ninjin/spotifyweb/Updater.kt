package com.ninjin.spotifyweb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * アプリ内アップデート。GitHub Releases の最新版を調べ、新しければ APK を落として Android のインストーラーに渡す。
 * 署名が同じ APK しか上書きできないので、ほかの人が作った APK に置き換わることはない
 */
class Updater(private val context: Context, private val log: (String, String) -> Unit) {
    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val apk get() = File(context.cacheDir, "updates/spotfy.apk")
    // ダウンロードは画面を閉じても続ける
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 起動時の確認。6 時間に 1 回まで（手動の確認は [check] を直接呼ぶ） */
    suspend fun checkIfDue() {
        if (System.currentTimeMillis() - prefs.getLong("checkedAt", 0) < 6 * 3600_000L) return
        check(manual = false)
    }

    suspend fun check(manual: Boolean = true) {
        if (_state.value is State.Downloading) return
        _state.value = State.Checking
        _state.value = runCatching { fetchLatest() }.fold(
            onSuccess = { r ->
                prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
                when {
                    r == null || !isNewer(r.version, BuildConfig.VERSION_NAME) -> { apk.delete(); State.UpToDate }
                    // 起動時の確認では「あとで」にした版をもう一度は出さない
                    !manual && prefs.getString("skipped", null) == r.version -> State.Idle
                    else -> State.Available(r)
                }
            },
            onFailure = { log("update.check.failed", it.reason); if (manual) State.Failed(tr("確認できませんでした", "Couldn't check for updates")) else State.Idle },
        )
    }

    fun later(release: Release) {
        prefs.edit().putString("skipped", release.version).apply()
        _state.value = State.Idle
    }

    fun download(release: Release) {
        if (_state.value is State.Downloading) return
        scope.launch { fetchApk(release) }
    }

    private suspend fun fetchApk(release: Release) {
        _state.value = State.Downloading(release, 0f)
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                apk.parentFile?.mkdirs()
                val part = File(apk.path + ".part")
                val conn = open(release.apkUrl)
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
                var lastShown = 0f
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf).takeIf { it >= 0 } ?: break
                            out.write(buf, 0, n)
                            done += n
                            // 1% 進むごとに知らせる（毎回だと画面の描き直しが多すぎる）
                            val p = if (total > 0) done.toFloat() / total else 0f
                            if (p - lastShown >= 0.01f) { lastShown = p; _state.value = State.Downloading(release, p) }
                        }
                    }
                }
                if (total > 0 && part.length() != total) error("size ${part.length()} != $total")
                if (!part.renameTo(apk)) error("rename failed")
            }
        }
        ok.onFailure { log("update.download.failed", it.reason) }
        if (ok.isFailure) { _state.value = State.Failed(tr("ダウンロードに失敗しました", "Download failed")); return }
        _state.value = State.Ready(release)
        install()
    }

    /** インストーラーを開く。「この提供元のアプリを許可」がまだなら、先にその設定画面を開く */
    fun install() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** API で調べる。API は IP ごとに 1 時間 60 回までなので、断られたらリリースページの転送先から調べる */
    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        runCatching { fromApi() }.getOrElse { e ->
            log("update.api.failed", e.reason)
            fromReleasePage()
        }
    }

    private fun fromApi(): Release? {
        val json = JSONObject(open("https://api.github.com/repos/$REPO/releases/latest").inputStream.bufferedReader().use { it.readText() })
        val assets = json.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        return Release(
            version = json.getString("tag_name").removePrefix("v"),
            notes = json.optString("body").trim(),
            apkUrl = asset.getString("browser_download_url"),
            size = asset.optLong("size"),
        )
    }

    /** releases/latest は最新のタグのページへ転送される。APK の名前は release.sh が付ける spotfy-<版>.apk */
    private fun fromReleasePage(): Release {
        val conn = open("https://github.com/$REPO/releases/latest", follow = false)
        val tag = conn.getHeaderField("Location")?.substringAfterLast("/tag/", "")?.takeIf { it.isNotEmpty() } ?: error("no release tag")
        conn.disconnect()
        val version = tag.removePrefix("v")
        // リリースノートは、そのタグの CHANGELOG.md の節から取る
        val notes = runCatching {
            val md = open("https://raw.githubusercontent.com/$REPO/$tag/CHANGELOG.md").inputStream.bufferedReader().use { it.readText() }
            md.substringAfter("## $version\n", "").substringBefore("\n## ").trim()
        }.getOrDefault("")
        return Release(version, notes, "https://github.com/$REPO/releases/download/$tag/spotfy-$version.apk", 0)
    }

    private fun open(url: String, follow: Boolean = true): HttpURLConnection {
        // GitHub の APK はほかのホストへリダイレクトされる。https のまま追うだけなので自分で追う
        var target = url
        repeat(5) {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "spotfy/${BuildConfig.VERSION_NAME}")
            when (val code = conn.responseCode) {
                in 200..299 -> return conn
                in 300..399 if !follow -> return conn
                in 300..399 -> {
                    val next = conn.getHeaderField("Location") ?: error("redirect without location")
                    conn.disconnect()
                    target = URL(URL(target), next).toString()
                    if (!target.startsWith("https://")) error("insecure redirect")
                }
                else -> { conn.disconnect(); error("HTTP $code") }
            }
        }
        error("too many redirects")
    }

    private val Throwable.reason get() = message ?: javaClass.simpleName

    companion object {
        const val REPO = "ninjin0802/spotfy"

        /** "2.10.0" と "2.9.3" のように、数字ごとに比べる */
        fun isNewer(latest: String, current: String): Boolean {
            val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val b = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}
