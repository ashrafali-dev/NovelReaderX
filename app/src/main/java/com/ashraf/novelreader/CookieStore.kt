package com.ashraf.novelreader

import android.webkit.CookieManager
import android.webkit.WebView

object CookieStore {
    fun configure(webView: WebView) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
            loadsImagesAutomatically = true
        }
    }

    fun flush() { CookieManager.getInstance().flush() }
}