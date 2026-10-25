package com.ninjin.spotifyweb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * WebView が無い・古い端末で出す案内。MainActivity は WebView が前提なので、
 * 使えないときは起動直後にこちらへ切り替え、MainActivity 側には中途半端な状態を残さない。
 */
class WebViewMissingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = runCatching { WebViewCompat.getCurrentWebViewPackage(this)?.versionName }.getOrNull()
        setContentView(messageView {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WEBVIEW_PACKAGE"))) }
                .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$WEBVIEW_PACKAGE"))) } }
        }.apply {
            text = "このアプリには新しい「Android System WebView」が必要です" +
                (if (version != null) "（今は $version）" else "（入っていないか、無効になっています）") +
                "\n\nタップして Play ストアで更新してください"
        })
    }

    companion object {
        private const val WEBVIEW_PACKAGE = "com.google.android.webview"

        /** ページとの連携に使う機能まで揃っているか（揃っていないとページが動かず真っ暗のままになる） */
        fun webViewUsable(context: Context) = runCatching {
            WebViewCompat.getCurrentWebViewPackage(context) != null &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        }.getOrDefault(false)
    }
}

/** 真っ暗な画面の代わりに出す、黒地に白い文字の案内 */
internal fun Context.messageView(onClick: () -> Unit) = TextView(this).apply {
    gravity = android.view.Gravity.CENTER
    setPadding(64, 0, 64, 0)
    setTextColor(0xFFFFFFFF.toInt())
    setBackgroundColor(0xFF121212.toInt())
    textSize = 16f
    setOnClickListener { onClick() }
}
