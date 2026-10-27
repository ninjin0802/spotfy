package com.ninjin.spotifyweb

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.View
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
import java.util.concurrent.Executors
import androidx.webkit.JavaScriptReplyProxy
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dumpDebugLogIfAsked(intent)
        // WebView が無い・古いと、ページとの連携が始まらず真っ暗のままになるので、先に案内を出す
        if (!WebViewMissingActivity.webViewUsable(this)) {
            startActivity(Intent(this, WebViewMissingActivity::class.java))
            finish()
            return
        }

        AppLanguage.apply(this)
        applyOrientation()
        webView = AwakeWebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(BG)
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        bridge = Bridge(webView, getSharedPreferences("cache", MODE_PRIVATE), ::recordDebug)
        bridge.onWillPlay = ::willPlay
        // アプリの画面（Compose）は Web Player の上に重ねる。Web Player は裏で再生とデータ取得だけを受け持つ
        composeView = ComposeView(this).apply {
            visibility = View.GONE
            setContent { NativeApp(bridge, host) }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(BG)
            addView(webView)
            addView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(loadError, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        lifecycleScope.launch {
            bridge.ready.collect { ready ->
                // ログインが切れていたら、アプリの画面は出さずにログインページへ
                if (ready && !isLoggedIn()) { bridge.reset(); webView.loadUrl(LOGIN_URL); updateSurface(); return@collect }
                if (ready) { applyQuality(); lifecycleScope.launch { runCatching { bridge.crossfade(settings.getString("crossfade", "0")?.toIntOrNull() ?: 0); bridge.gapless(settings.getString("gapless", "true") == "true") } }
                    if (settings.getString("prewarm", "true") == "true") lifecycleScope.launch { runCatching { bridge.prewarm() } }
                    if (DebugMode.on) lifecycleScope.launch { runCatching { bridge.debug(true) } }
                }
                updateSurface()
            }
        }
        setContentView(root)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        // 設定の「開発者向けデバッグ」をオンにしたときだけ、PC から WebView を調べられるようにする
        applyDebugMode()
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

        SpotifyWebView.configure(webView)
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

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { pageFailed = false; updateSurface(url) }

            // 裏の Web Player は画面に出さないので、画像・動画（ジャケット・Canvas など）は読み込ませない。
            // 画面に出す画像はアプリ側で直接読み込む。これでページの読み込みと通信が軽くなる
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                if (!nativeUi) return null
                val host = request.url.host ?: return null
                debug.traceRequest(host, request.url, request.method)
                val skip = host == "i.scdn.co" || host == "mosaic.scdn.co" || host == "image-cdn-ak.spotifycdn.com" || host == "image-cdn-fa.spotifycdn.com" ||
                    host == "pickasso.spotifycdn.com" || host == "canvaz.scdn.co" || host.endsWith("video.spotifycdn.com") || host == "seed-mix-image.spotifycdn.com"
                return if (skip) android.webkit.WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0))) else null
            }
            // 開けなかったときも WebView のエラーページ（黒い背景に黒い文字）が表示されたことになるので、そのときは消さない
            override fun onPageCommitVisible(view: WebView, url: String?) { if (!pageFailed) loadError.visibility = View.GONE; injectMobileUi(view) }
            // ページが開けないと黒い背景のままで何も出ないので、理由と再読み込みを出す
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (!request.isForMainFrame) return
                pageFailed = true
                failedUrl = request.url.toString()
                recordDebug("page.error", "${error.errorCode} ${request.url.host}")
                loadError.text = tr("Spotify に接続できません（${error.description}）\n\nタップして再読み込み", "Can't connect to Spotify (${error.description})\n\nTap to reload")
                loadError.visibility = View.VISIBLE
            }
            // WebView の描画プロセスが落ちると真っ暗のまま固まるので、画面を作り直す
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                recordDebug("page.renderer_gone", if (detail.didCrash()) "crash" else "killed")
                recreate()
                return true
            }
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
        }

        // アプリの画面で戻れないときは、Web のページを戻るか、アプリを裏に回す
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        if (savedInstanceState != null && isLoggedIn()) {
            webView.restoreState(savedInstanceState)
        } else {
            // ログインしていなければ、最初から Spotify のログインページを開く
            webView.loadUrl(if (isLoggedIn()) initialUrl(intent) else LOGIN_URL)
        }
    }

    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private var pageFailed = false
    private var failedUrl: String? = null

    /** reload() だとエラーページを読み直すだけなので、開けなかった URL をもう一度開く */
    private fun retryFailedPage() {
        if (loadError.visibility != View.VISIBLE) return
        loadError.visibility = View.GONE
        webView.loadUrl(failedUrl ?: if (isLoggedIn()) HOME_URL else LOGIN_URL)
    }

    private val loadError by lazy { messageView { retryFailedPage() }.apply { visibility = View.GONE } }

    @Deprecated("Activity の結果 API（ComponentActivity 不使用のため）")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE) return
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        fileCallback = null
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.apply(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (dumpDebugLogIfAsked(intent)) return
        intent.data?.takeIf(::isSpotifyUrl)?.let { webView.loadUrl(it.toString()) }
    }

    private fun initialUrl(intent: Intent?): String {
        val data: Uri? = intent?.data
        return if (data != null && isSpotifyUrl(data)) data.toString() else HOME_URL
    }

    private fun isSpotifyUrl(uri: Uri) = uri.scheme == "https" && uri.host == "open.spotify.com"


    private fun finishRelay(reply: JavaScriptReplyProxy, id: Int, status: Int, type: String?, text: String) = runOnUiThread {
        reply.postMessage(
            JSONObject().put("type", "relay").put("id", id).put("status", status)
                .put("contentType", type.orEmpty()).put("body", text).toString()
        )
    }

    private val mobileUiJs by lazy { assets.open("bridge.js").bufferedReader().use { it.readText() } }

    private lateinit var bridge: Bridge
    private lateinit var composeView: ComposeView

    /** ほかの機器から見たこのスマホの名前（設定の「端末名」、なければ機種名） */
    private fun deviceName(): String =
        runCatching { android.provider.Settings.Global.getString(contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /** Spotify のログイン Cookie（sp_dc）があるか */
    private fun isLoggedIn(): Boolean =
        CookieManager.getInstance().getCookie(HOME_URL)?.split(';')?.any { it.trim().startsWith("sp_dc=") } == true

    private var statusBarCss = 0f
    private var navBarCss = 0f

    private fun pushStatusBar() = webView.evaluateJavascript(
        "window.__mSetTop && __mSetTop($statusBarCss, $navBarCss)", null,
    )

    private fun injectMobileUi(view: WebView) {
        // 橋は open.spotify.com のページにだけ入れる（ログインページなどには入れない）
        if (!isSpotifyUrl(Uri.parse(view.url ?: return))) { bridge.reset(); return }
        view.evaluateJavascript(mobileUiJs, null)
        pushStatusBar()
        pushAudioOutput()
        pushMetered()
    }

    // ---- Bluetooth イヤホンなどの出力先（公式アプリと同じく、ミニプレーヤーに機器名を出す） ----
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private fun currentOutputName(): String {
        val wireless = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID,
        )
        val wired = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        outs.firstOrNull { it.type in wireless }?.let { return it.productName?.toString().orEmpty().ifBlank { "Bluetooth" } }
        outs.firstOrNull { it.type in wired }?.let { return it.productName?.toString().orEmpty().ifBlank { tr("ヘッドホン", "Headphones") } }
        return ""
    }

    // ---- いまの通信がモバイルデータ（従量制）か。音質を Wi-Fi 用／モバイル用で切り替えるのに使う ----
    private val connectivity by lazy { getSystemService(android.net.ConnectivityManager::class.java) }
    private fun isMetered() = connectivity.isActiveNetworkMetered

    private var lastMetered: Boolean? = null
    private fun pushMetered() {
        val m = isMetered()
        if (m == lastMetered) return
        lastMetered = m
        applyQuality()
    }

    /** 通信（Wi-Fi／モバイル）に合わせた音質を Web Player に指定する */
    private fun applyQuality() {
        if (!bridge.ready.value) return
        val level = host.quality(isMetered())
        lifecycleScope.launch { runCatching { bridge.quality(level) } }
    }

    /** ログイン済みで open.spotify.com を開いているときだけアプリの画面を出す（ログインページは Web のまま見せる） */
    @Volatile private var nativeUi = false
    private fun updateSurface(url: String? = webView.url) {
        val onSpotify = url == null || isSpotifyUrl(Uri.parse(url))
        nativeUi = isLoggedIn() && onSpotify
        composeView.visibility = if (nativeUi) View.VISIBLE else View.GONE
    }

    /** アプリの画面から使う、端末側の機能と設定 */
    private val host = object : AppHost {
        override fun setting(name: String, fallback: String) = settings.getString(name, fallback) ?: fallback
        override fun setSetting(name: String, value: String) {
            settings.edit().putString(name, value).apply()
            if (name.startsWith("quality")) applyQuality()
            if (name == "gapless") lifecycleScope.launch { runCatching { bridge.gapless(value == "true") } }
            if (name == "crossfade") lifecycleScope.launch { runCatching { bridge.crossfade(value.toIntOrNull() ?: 0) } }
            if (name == "orientation") applyOrientation()
        }
        override fun quality(cellular: Boolean): String {
            val wifi = settings.getString("quality", "auto") ?: "auto"
            return if (cellular) settings.getString("quality.cell", wifi) ?: wifi else wifi
        }
        override val metered get() = isMetered()
        override fun share(url: String, title: String) {
            if (!url.startsWith("https://open.spotify.com/")) return
            shareText(url, title.ifEmpty { null })
        }
        override fun outputSwitch() = showOutputSwitcher()
        override fun outputName() = currentOutputName()
        override var debugMode: Boolean
            get() = DebugMode.on
            set(value) = setDebugMode(value)
        override val fullLogPath get() = fullLogFile().absolutePath
        override fun shareDebugLog() {
            // デバッグモードでは、全部の記録をファイルで送る（文字で送ると大きすぎて送れない）
            val file = if (DebugMode.on) debug.fullLogFile() else null
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.updates", file)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "spotfy ${BuildConfig.VERSION_NAME} full debug log").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { startActivity(Intent.createChooser(send, tr("全部のログを共有", "Share full log"))) }
                return
            }
            val log = debug.lines().joinToString("\n").ifBlank { "No playback debug events recorded." }
            shareText("spotfy ${BuildConfig.VERSION_NAME} debug log\n$log", tr("デバッグログを共有", "Share debug log"), subject = "spotfy debug log")
        }
        override val updater get() = this@MainActivity.updater
        override fun logout() {
            // Cookie を消して最初からログインページへ
            // 前のアカウントの情報（覚えておいたホーム・ユーザー名、ページの保存データ、デバッグログ）も消し、画面を作り直す
            getSharedPreferences("cache", MODE_PRIVATE).edit().clear().apply()
            debug.clear()
            debug.deleteFullLog(fullLogFile())
            android.webkit.WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush(); webView.post { recreate() } }
        }
    }

    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
            webView.post { pushMetered() }
            // つながらなくて開けなかったページは、ネットにつながったら自動で開き直す
            if (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) webView.post { retryFailedPage() }
        }
        override fun onLost(network: android.net.Network) { webView.post { pushMetered() } }
    }

    // スピーカー／イヤホンの切り替え（Android の出力切り替え画面を開く。公式アプリと同じく端末の一覧から選ぶ）
    private fun showOutputSwitcher() {
        if (Build.VERSION.SDK_INT >= 34 &&
            runCatching { android.media.MediaRouter2.getInstance(this).showSystemOutputSwitcher() }.getOrDefault(false)
        ) return
        val dialog = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui").putExtra("package_name", packageName)
        if (Build.VERSION.SDK_INT >= 30 && runCatching { sendBroadcast(dialog) }.isSuccess) return
        runCatching { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    // 機器名は open.spotify.com のページにだけ渡す（ログイン画面など別のページには送らない）
    private fun pushAudioOutput() {
        val url = webView.url ?: return
        if (!isSpotifyUrl(Uri.parse(url))) return
        webView.evaluateJavascript("window.__mSetOutput && __mSetOutput(${JSONObject.quote(currentOutputName())})", null)
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = pushAudioOutput()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = pushAudioOutput()
    }

    // イヤホンが外れたら一時停止（スピーカーから急に音が出ないように）
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pausePlayback()
        }
    }

    private fun pausePlayback() = webView.evaluateJavascript(
        "(function(){var b=document.querySelector('[data-testid=\"control-button-playpause\"]');" +
            "if(b&&/一時停止|Pause/.test(b.getAttribute('aria-label')||''))b.click();})()",
        null,
    )

    private fun handleBack() {
        if (composeView.visibility == View.VISIBLE) { moveTaskToBack(true); return }
        webView.evaluateJavascript("window.__mobileBack && window.__mobileBack()") { closed ->
            if (closed == "true") return@evaluateJavascript
            if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
        }
    }


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
        if (bridge.onMessage(m)) return
        when (m.optString("type")) {
            "hello" -> reply.postMessage(
                JSONObject().put("type", "settings").put(
                    "values",
                    JSONObject().put("orientation", settings.getString("orientation", "portrait"))
                        .put("background", settings.getString("background", "true"))
                        .put("adblock", settings.getString("adblock", "true"))
                        .put("webdebug", settings.getString("webdebug", "false"))
                        .put("deviceName", deviceName()),
                ).toString()
            )
            "setting" -> {
                val name = m.optString("name")
                if (name !in setOf("orientation", "background", "adblock", "webdebug")) return
                settings.edit().putString(name, m.optString("value")).apply()
                if (name == "adblock") recordDebug("adblock.setting", m.optString("value"))
                if (name == "orientation") applyOrientation()
            }
            "bhealth" -> recordDebug("bridge.health", bridge.missing.value.joinToString(", ").ifEmpty { "ok" })
            "debug" -> {
                val event = m.optString("event")
                if (event == "ad.detected") debug.adPlaying = true
                if (event == "ad.state.ended" || event == "ad.state.blocking_off") debug.adPlaying = false
                recordDebug(event, m.optString("detail"))
            }
            "playing" -> setPlaying(m.optBoolean("value"))
            "outputSwitch" -> showOutputSwitcher()
            "hideKeyboard" -> getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(webView.windowToken, 0)
            "share" -> {
                // Spotify のリンクだけ Android の共有シートで送る
                val url = m.optString("url")
                if (url.startsWith("https://open.spotify.com/")) shareText(url, m.optString("title").ifEmpty { null })
            }
            "meta" -> media.update(
                m.optString("title"), m.optString("artist"), m.optString("album"), m.optString("art"),
                m.optBoolean("playing"), m.optLong("pos"), m.optLong("dur"),
            )
            "relay" -> {
                val id = m.optInt("id")
                val url = m.optString("url")
                val method = m.optString("method")
                if (!ConnectRelay.isRelayable(url, method)) return finishRelay(reply, id, 0, null, "")
                val headers = m.optJSONObject("headers") ?: JSONObject()
                val body = if (m.isNull("body")) null else m.optString("body")
                // Connect 操作を X-Requested-With を付けずに端末から直接送る
                relayExecutor.execute {
                    val (status, type, text) = try {
                        ConnectRelay.send(url, method, headers, body)
                    } catch (e: Exception) {
                        Triple(0, null, "")
                    }
                    finishRelay(reply, id, status, type, text)
                }
            }
        }
    }

    /**
     * adb から起こされたときの開発用の指示。
     * `am start --ez dumpDebugLog true`：保存済みのログ全体を logcat に流す。
     * `am start --ez debugMode true|false`：デバッグモードを切り替える（アイコンを 10 回押すのと同じ）
     */
    private fun dumpDebugLogIfAsked(intent: Intent?): Boolean {
        // ほかのアプリからも送れてしまうので、USB デバッグがオンの端末でだけ受け付ける
        if (intent?.hasExtra("debugMode") == true) {
            val adb = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
            if (adb) setDebugMode(intent.getBooleanExtra("debugMode", false))
            return true
        }
        if (intent?.getBooleanExtra("dumpDebugLog", false) != true) return false
        debug.dumpToLogcat()
        return true
    }

    private fun setDebugMode(value: Boolean) {
        DebugMode.on = value
        recordDebug("debug.mode", if (value) "on" else "off")
        applyDebugMode()
        lifecycleScope.launch { runCatching { bridge.debug(value) } }
    }

    private val debug by lazy { DebugLog(settings) }
    // Android 11 以降は Android/data をほかのアプリが読めないので、adb で取り出せるそこに置く。10 以前はアプリの中に置く
    private fun fullLogFile() = java.io.File(
        if (Build.VERSION.SDK_INT >= 30) getExternalFilesDir(null) ?: filesDir else filesDir, "logs/debug-full.log")

    /** デバッグモードなら、PC から WebView を調べられるようにし、全部の記録をファイルにも残す */
    private fun applyDebugMode() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || DebugMode.on || settings.getString("webdebug", "false") == "true")
        debug.fullLog = if (DebugMode.on) fullLogFile() else null
    }
    private val updater by lazy { Updater(applicationContext, ::recordDebug) }
    private fun recordDebug(event: String, detail: String = "") = debug.record(event, detail)

    private fun shareText(text: String, chooserTitle: String?, subject: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) send.putExtra(Intent.EXTRA_SUBJECT, subject)
        runCatching { startActivity(Intent.createChooser(send, chooserTitle)) }
    }

    /** 通知・ロック画面のメディアコントロールからの操作を Web Player に伝える */
    private val media by lazy {
        MediaControls(applicationContext) { action, pos ->
            runOnUiThread {
                webView.evaluateJavascript("window.__mMedia && __mMedia(${JSONObject.quote(action)}, $pos)", null)
            }
        }
    }

    // 曲の切り替わりで一瞬「停止」になってもサービスを止めない（止めると裏では起動し直せず、そこで再生が止まる）
    private val stopHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopService = Runnable {
        serviceRunning = false
        PlaybackService.stop(this)
    }

    /** バックグラウンド再生用のサービスを起動する（裏に回ってからは Android の制限で起動できない） */
    private fun ensureService() {
        stopHandler.removeCallbacks(stopService)
        if (serviceRunning || !backgroundPlayback) return
        serviceRunning = PlaybackService.start(this)
    }

    /** 再生ボタンが押された瞬間（まだ画面が前にある）に、サービスを起動しておく */
    private fun willPlay() {
        if (!backgroundPlayback) return
        PlaybackService.hold(this, true)
        ensureService()
        // 鳴らなかったときは 1 分で片付ける（鳴れば setPlaying(true) が取り消す）
        stopHandler.postDelayed(stopService, 60_000L)
    }

    private fun setPlaying(playing: Boolean) {
        PlaybackService.hold(this, playing)
        if (playing) ensureService()
        else if (serviceRunning) {
            // 一時停止してから 10 分たったらサービスを止める
            stopHandler.removeCallbacks(stopService)
            stopHandler.postDelayed(stopService, 10 * 60 * 1000L)
        }
    }

    override fun onStop() {
        super.onStop()
        debug.flush()
        // バックグラウンド再生をオフにしている場合は裏に回ったら一時停止する
        if (!backgroundPlayback) pausePlayback()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        // WebView が使えず onCreate の途中で WebViewMissingActivity に切り替えたときは、何も作っていない
        if (!::webView.isInitialized) { super.onDestroy(); return }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        unregisterReceiver(noisyReceiver)
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        PlaybackService.stop(this)
        media.release()
        CookieManager.getInstance().flush()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://open.spotify.com/"
        private const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
        private const val REQUEST_FILE = 42
        private const val SPOTIFY_ORIGIN = "https://open.spotify.com"
        private const val BG = 0xFF121212.toInt()
    }
}
