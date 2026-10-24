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
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
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
            setBackgroundColor(0xFF000000.toInt())
            addView(webView)
        }
        setContentView(root)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        // targetSdk 35+ は edge-to-edge 強制なので、ステータスバー/ナビバー分の余白を確保する
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val polyfill = assets.open("polyfill.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(webView, polyfill, setOf("https://open.spotify.com"))
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.takeIf(::isSpotifyUrl)?.let { webView.loadUrl(it.toString()) }
    }

    private fun initialUrl(intent: Intent?): String {
        val data: Uri? = intent?.data
        return if (data != null && isSpotifyUrl(data)) data.toString() else HOME_URL
    }

    private fun isSpotifyUrl(uri: Uri) = uri.scheme == "https" && uri.host == "open.spotify.com"


    private fun sendRelayed(url: String, method: String, headers: JSONObject, body: String?): Triple<Int, String?, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
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

    private fun finishRelay(id: Int, status: Int, type: String?, text: String) = runOnUiThread {
        val js = "window.__relayDone && __relayDone($id, $status, ${JSONObject.quote(type.orEmpty())}, ${JSONObject.quote(text)})"
        webView.evaluateJavascript(js, null)
    }

    private val mobileUiJs by lazy { assets.open("mobile.js").bufferedReader().use { it.readText() } }

    private fun injectMobileUi(view: WebView) = view.evaluateJavascript(mobileUiJs, null)

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

    private inner class Bridge {
        /** 設定画面のうちアプリ側で扱う項目（画面の向き・バックグラウンド再生） */
        @JavascriptInterface
        fun getSetting(name: String, fallback: String): String = settings.getString(name, fallback) ?: fallback

        @JavascriptInterface
        fun setSetting(name: String, value: String) = runOnUiThread {
            if (name !in setOf("orientation", "background")) return@runOnUiThread
            settings.edit().putString(name, value).apply()
            if (name == "orientation") applyOrientation()
        }

        /** ページの fetch から渡された Connect 操作を、X-Requested-With を付けずに端末から直接送る */
        @JavascriptInterface
        fun relay(id: Int, url: String, method: String, headersJson: String, body: String?) {
            if (!url.startsWith("https://") || !Uri.parse(url).host.orEmpty().endsWith(".spotify.com")) {
                return finishRelay(id, 0, null, "")
            }
            relayExecutor.execute {
                val (status, type, text) = try {
                    sendRelayed(url, method, JSONObject(headersJson), body)
                } catch (e: Exception) {
                    Triple(0, null, "")
                }
                finishRelay(id, status, type, text)
            }
        }

        @JavascriptInterface
        fun setPlaying(playing: Boolean) = runOnUiThread {
            if (playing == serviceRunning) return@runOnUiThread
            serviceRunning = playing
            if (playing && backgroundPlayback) {
                // バックグラウンドからは起動できない（再生中に裏へ回った場合は既に起動済み）
                if (!PlaybackService.start(this@MainActivity)) serviceRunning = false
            } else {
                PlaybackService.stop(this@MainActivity)
            }
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
        CookieManager.getInstance().flush()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://open.spotify.com/"
        private val SKIPPED_HEADERS = setOf("host", "content-length", "cookie", "user-agent", "x-requested-with")
        private const val CHROME_MAJOR = "141"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$CHROME_MAJOR.0.0.0 Safari/537.36"
    }
}
