package com.ninjin.spotifyweb

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import androidx.webkit.JavaScriptReplyProxy
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedDispatcher
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyOrientation()
        webView = AwakeWebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF121212.toInt())
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF121212.toInt())
            addView(webView)
        }
        setContentView(root)
        // 設定の「開発者向けデバッグ」をオンにしたときだけ、PC から WebView を調べられるようにする
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || settings.getString("webdebug", "false") == "true")
        // targetSdk 35+ は edge-to-edge 強制なので、ステータスバー/ナビバー分の余白を確保する
        // 上はステータスバーの裏までページを描き（公式アプリと同じ）、その高さをページに伝える
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, 0)
            statusBarCss = bars.top / resources.displayMetrics.density
            navBarCss = bars.bottom / resources.displayMetrics.density
            pushStatusBar()
            insets
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // モバイル UA だとアプリのダウンロード案内に飛ばされるため、デスクトップ Chrome を名乗る
            userAgentString = DESKTOP_UA
        }
        // UA 文字列だけでなく Client Hints (navigator.userAgentData / Sec-CH-UA) も
        // デスクトップ Windows を名乗らないと、Web Player が再生デバイスとして動作しない
        // WebView が付ける X-Requested-With ヘッダーでアプリ内ブラウザだと判定されないようにする
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(
                webView.settings,
                UserAgentMetadata.Builder()
                    .setBrandVersionList(
                        listOf(
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome")
                                .setMajorVersion(CHROME_MAJOR).setFullVersion("$CHROME_MAJOR.0.0.0").build(),
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium")
                                .setMajorVersion(CHROME_MAJOR).setFullVersion("$CHROME_MAJOR.0.0.0").build(),
                        )
                    )
                    .setFullVersion("$CHROME_MAJOR.0.0.0")
                    .setPlatform("Windows")
                    .setPlatformVersion("15.0.0")
                    .setArchitecture("x86")
                    .setModel("")
                    .setMobile(false)
                    .build()
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "AndroidBridge", setOf(SPOTIFY_ORIGIN)) { _, message, sourceOrigin, isMainFrame, reply ->
                if (isMainFrame && sourceOrigin.toString() == SPOTIFY_ORIGIN) message.data?.let { onPageMessage(it, reply) }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val polyfill = assets.open("polyfill.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(webView, polyfill, setOf(SPOTIFY_ORIGIN))
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "http" || uri.scheme == "https") return false
                // spotify: / intent: などの独自スキームは WebView では開けないので無視
                return true
            }

            override fun onPageCommitVisible(view: WebView, url: String?) = injectMobileUi(view)
            override fun onPageFinished(view: WebView, url: String?) = injectMobileUi(view)
        }
        webView.webChromeClient = object : WebChromeClient() {
            // プロフィール写真の変更など、ページの <input type="file"> から端末の画像を選ぶ
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val pick = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                return runCatching { startActivityForResult(Intent.createChooser(pick, null), REQUEST_FILE); true }
                    .getOrElse { fileCallback = null; false }
            }

            // Web Player は Widevine (EME) を使うので保護メディア ID を許可する
            override fun onPermissionRequest(request: PermissionRequest) {
                val allowed = request.resources.filter {
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                }
                if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(initialUrl(intent))
        }
    }

    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @Deprecated("Activity の結果 API（ComponentActivity 不使用のため）")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE) return
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        fileCallback = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.takeIf(::isSpotifyUrl)?.let { webView.loadUrl(it.toString()) }
    }

    private fun initialUrl(intent: Intent?): String {
        val data: Uri? = intent?.data
        return if (data != null && isSpotifyUrl(data)) data.toString() else HOME_URL
    }

    private fun isSpotifyUrl(uri: Uri) = uri.scheme == "https" && uri.host == "open.spotify.com"


    private fun isRelayable(url: String, method: String): Boolean {
        if (method !in RELAY_METHODS || !RELAY_URL.matches(url)) return false
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        return parsed.protocol == "https" && parsed.userInfo == null &&
            (parsed.host == "spotify.com" || parsed.host.endsWith(".spotify.com"))
    }

    private fun sendRelayed(url: String, method: String, headers: JSONObject, body: String?): Triple<Int, String?, String> {
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
            conn.setRequestProperty("User-Agent", DESKTOP_UA)
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

    private fun finishRelay(reply: JavaScriptReplyProxy, id: Int, status: Int, type: String?, text: String) = runOnUiThread {
        reply.postMessage(
            JSONObject().put("type", "relay").put("id", id).put("status", status)
                .put("contentType", type.orEmpty()).put("body", text).toString()
        )
    }

    private val mobileUiJs by lazy { assets.open("mobile.js").bufferedReader().use { it.readText() } }

    private var statusBarCss = 0f
    private var navBarCss = 0f

    private fun pushStatusBar() = webView.evaluateJavascript(
        "window.__mSetTop && __mSetTop($statusBarCss, $navBarCss)", null,
    )

    private fun injectMobileUi(view: WebView) {
        view.evaluateJavascript(mobileUiJs, null)
        pushStatusBar()
    }

    private fun handleBack() {
        webView.evaluateJavascript("window.__mobileBack && window.__mobileBack()") { closed ->
            if (closed == "true") return@evaluateJavascript
            if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
        }
    }

    @Deprecated("Android 12 以前向け")
    override fun onBackPressed() = handleBack()

    // バックグラウンド再生を止めないよう webView.onPause() は呼ばず、再生中だけサービスで生存させる
    private var serviceRunning = false

    private val relayExecutor = Executors.newCachedThreadPool()

    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private fun applyOrientation() {
        requestedOrientation = if (settings.getString("orientation", "portrait") == "auto") {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private val backgroundPlayback get() = settings.getString("background", "true") == "true"

    /**
     * ページとの連絡口。addWebMessageListener は指定オリジン（open.spotify.com）にだけ注入され、
     * 送信元オリジンとメインフレームかどうかも確認できる。
     */
    private fun onPageMessage(message: String, reply: JavaScriptReplyProxy) {
        val m = runCatching { JSONObject(message) }.getOrNull() ?: return
        when (m.optString("type")) {
            "hello" -> reply.postMessage(
                JSONObject().put("type", "settings").put(
                    "values",
                    JSONObject().put("orientation", settings.getString("orientation", "portrait"))
                        .put("background", settings.getString("background", "true"))
                        .put("webdebug", settings.getString("webdebug", "false")),
                ).toString()
            )
            "setting" -> {
                val name = m.optString("name")
                if (name !in setOf("orientation", "background", "webdebug")) return
                settings.edit().putString(name, m.optString("value")).apply()
                if (name == "orientation") applyOrientation()
            }
            "playing" -> setPlaying(m.optBoolean("value"))
            "share" -> {
                // Spotify のリンクだけ Android の共有シートで送る
                val url = m.optString("url")
                if (url.startsWith("https://open.spotify.com/")) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    runCatching { startActivity(Intent.createChooser(send, m.optString("title").ifEmpty { null })) }
                }
            }
            "meta" -> media.update(
                m.optString("title"), m.optString("artist"), m.optString("album"), m.optString("art"),
                m.optBoolean("playing"), m.optLong("pos"), m.optLong("dur"),
            )
            "relay" -> {
                val id = m.optInt("id")
                val url = m.optString("url")
                val method = m.optString("method")
                if (!isRelayable(url, method)) return finishRelay(reply, id, 0, null, "")
                val headers = m.optJSONObject("headers") ?: JSONObject()
                val body = if (m.isNull("body")) null else m.optString("body")
                // Connect 操作を X-Requested-With を付けずに端末から直接送る
                relayExecutor.execute {
                    val (status, type, text) = try {
                        sendRelayed(url, method, headers, body)
                    } catch (e: Exception) {
                        Triple(0, null, "")
                    }
                    finishRelay(reply, id, status, type, text)
                }
            }
        }
    }

    /** 通知・ロック画面のメディアコントロールからの操作を Web Player に伝える */
    private val media by lazy {
        MediaControls(applicationContext) { action, pos ->
            runOnUiThread {
                webView.evaluateJavascript("window.__mMedia && __mMedia(${JSONObject.quote(action)}, $pos)", null)
            }
        }
    }

    private fun setPlaying(playing: Boolean) {
        if (playing == serviceRunning) return
        serviceRunning = playing
        if (playing && backgroundPlayback) {
            // バックグラウンドからは起動できない（再生中に裏へ回った場合は既に起動済み）
            if (!PlaybackService.start(this)) serviceRunning = false
        } else {
            PlaybackService.stop(this)
        }
    }

    override fun onStop() {
        super.onStop()
        // バックグラウンド再生をオフにしている場合は裏に回ったら一時停止する
        if (!backgroundPlayback) {
            webView.evaluateJavascript(
                "(function(){var b=document.querySelector('[data-testid=\"control-button-playpause\"]');" +
                    "if(b&&/一時停止|Pause/.test(b.getAttribute('aria-label')||''))b.click();})()",
                null,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        PlaybackService.stop(this)
        media.release()
        CookieManager.getInstance().flush()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://open.spotify.com/"
        private const val REQUEST_FILE = 42
        private const val SPOTIFY_ORIGIN = "https://open.spotify.com"
        private val RELAY_URL = Regex("^https://([a-z0-9-]+\\.)*spotify\\.com/(connect-state|track-playback)/v1/[^#]*$")
        private val RELAY_METHODS = setOf("GET", "POST", "PUT", "DELETE")
        private val SKIPPED_HEADERS = setOf("host", "content-length", "cookie", "user-agent", "x-requested-with")
        private const val CHROME_MAJOR = "141"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$CHROME_MAJOR.0.0.0 Safari/537.36"
    }
}
