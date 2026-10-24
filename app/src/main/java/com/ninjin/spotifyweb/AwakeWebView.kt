package com.ninjin.spotifyweb

import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * アプリがバックグラウンドに回っても Chromium にはウィンドウが見えていると伝える WebView。
 * 非表示と判断されるとページのメディアが止まるため、バックグラウンド再生にはこれが必要。
 */
class AwakeWebView(context: Context) : WebView(context) {
    override fun getWindowVisibility(): Int = View.VISIBLE

    override fun onWindowVisibilityChanged(visibility: Int) = super.onWindowVisibilityChanged(View.VISIBLE)
}
