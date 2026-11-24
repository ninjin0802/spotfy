package com.ninjin.spotifyweb

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Web Player を「デスクトップの Chrome」として動かすための WebView の設定 */
object SpotifyWebView {
    private const val CHROME_MAJOR = "141"
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$CHROME_MAJOR.0.0.0 Safari/537.36"

    fun configure(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        setDesktop(webView, true)
        // WebView が付ける X-Requested-With ヘッダーでアプリ内ブラウザだと判定されないようにする
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
        }
        // 上の設定は新しい WebView では効かず、アプリの名前が付いたままになる。ログインのページでは、ページが自分で値を入れておく（入っていれば WebView は上書きしない）
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, PLAIN_REQUESTED_WITH, setOf("https://accounts.spotify.com"))
        }
    }

    /**
     * 名乗るブラウザを切り替える。変えたら true。
     * open.spotify.com は「デスクトップの Chrome」（モバイルだとアプリのダウンロード案内に飛ばされ、再生デバイスとしても動かない）。
     * ログインなどそれ以外のページは、端末のとおり「スマホの Chrome」。Windows を名乗ると中身（スマホ）と食い違い、ログインのコードの送信が断られる
     */
    fun setDesktop(webView: WebView, desktop: Boolean): Boolean {
        if ((webView.settings.userAgentString == DESKTOP_UA) == desktop) return false
        // スマホを名乗るときは、WebView の本当の版に合わせる
        val major = if (desktop) CHROME_MAJOR
        else WebViewCompat.getCurrentWebViewPackage(webView.context)?.versionName?.substringBefore('.')?.takeIf { it.toIntOrNull() != null } ?: CHROME_MAJOR
        webView.settings.userAgentString = if (desktop) DESKTOP_UA
        else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
        // UA 文字列だけでなく Client Hints (navigator.userAgentData / Sec-CH-UA) もそろえる
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(
                webView.settings,
                UserAgentMetadata.Builder()
                    .setBrandVersionList(
                        listOf(
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome")
                                .setMajorVersion(major).setFullVersion("$major.0.0.0").build(),
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium")
                                .setMajorVersion(major).setFullVersion("$major.0.0.0").build(),
                        )
                    )
                    .setFullVersion("$major.0.0.0")
                    .setPlatform(if (desktop) "Windows" else "Android")
                    .setPlatformVersion(if (desktop) "15.0.0" else "${Build.VERSION.RELEASE}.0.0")
                    .setArchitecture(if (desktop) "x86" else "")
                    .setModel("")
                    .setMobile(!desktop)
                    .build()
            )
        }
        return true
    }

    // 同じサイトへの fetch / XHR に、普通のブラウザでも付く値で X-Requested-With を入れる
    private const val PLAIN_REQUESTED_WITH = """(function(){
var same=function(u){try{return new URL(u,location.href).origin===location.origin}catch(e){return false}};
var f=window.fetch;window.fetch=function(i,o){if(same(typeof i==='string'?i:i.url)){o=Object.assign({},o);var h=new Headers(o.headers||(typeof i!=='string'&&i.headers)||{});h.set('X-Requested-With','XMLHttpRequest');o.headers=h}return f.call(this,i,o)};
var op=XMLHttpRequest.prototype.open,sd=XMLHttpRequest.prototype.send;
XMLHttpRequest.prototype.open=function(m,u){this.__same=same(u);return op.apply(this,arguments)};
XMLHttpRequest.prototype.send=function(){if(this.__same)try{this.setRequestHeader('X-Requested-With','XMLHttpRequest')}catch(e){}return sd.apply(this,arguments)};
})()"""
}
