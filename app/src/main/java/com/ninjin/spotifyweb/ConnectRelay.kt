package com.ninjin.spotifyweb

import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spotify Connect の再生操作を、WebView ではなく端末から直接送る。
 * WebView はすべての通信に X-Requested-With を付けるため、そのままだと再生コマンドが届かない。
 */
object ConnectRelay {
    private val RELAY_URL = Regex("^https://([a-z0-9-]+\\.)*spotify\\.com/(connect-state|track-playback)/v1/[^#]*$")
    private val RELAY_METHODS = setOf("GET", "POST", "PUT", "DELETE")
    private val SKIPPED_HEADERS = setOf("host", "content-length", "cookie", "user-agent", "x-requested-with")

    /** 送り直してよいのは Spotify の Connect 関係の通信だけ */
    fun isRelayable(url: String, method: String): Boolean {
        if (method !in RELAY_METHODS || !RELAY_URL.matches(url)) return false
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        return parsed.protocol == "https" && parsed.userInfo == null &&
            (parsed.host == "spotify.com" || parsed.host.endsWith(".spotify.com"))
    }

    /** 送って (status, content-type, body) を返す。ネットワークを使うので UI スレッド以外で呼ぶ */
    fun send(url: String, method: String, headers: JSONObject, body: String?): Triple<Int, String?, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            // リダイレクト先に Cookie を渡さない
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            headers.keys().forEach { key ->
                if (key.lowercase() !in SKIPPED_HEADERS) conn.setRequestProperty(key, headers.getString(key))
            }
            conn.setRequestProperty("User-Agent", SpotifyWebView.DESKTOP_UA)
            conn.setRequestProperty("Origin", "https://open.spotify.com")
            conn.setRequestProperty("Referer", "https://open.spotify.com/")
            CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Triple(status, conn.contentType, text)
        } finally {
            conn.disconnect()
        }
    }
}
