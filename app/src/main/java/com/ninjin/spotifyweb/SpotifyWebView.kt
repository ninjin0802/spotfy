package com.ninjin.spotifyweb

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
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
    }
}
