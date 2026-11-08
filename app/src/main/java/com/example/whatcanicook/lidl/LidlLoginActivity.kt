package com.example.whatcanicook.lidl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Signs the user in to Lidl in a WebView and hands back the resulting cookie jar.
 *
 * The user types their own credentials; the app never sees or stores them. Lidl
 * defends this page with reCAPTCHA Enterprise, FingerprintJS and Akamai Bot
 * Manager, so a real browser engine driven by a real person is the only way
 * through — which is exactly what this is.
 *
 * Success is detected by watching for Lidl's authenticated cookies rather than
 * by matching a URL, because the post-login redirect chain varies.
 */
class LidlLoginActivity : Activity() {

    private lateinit var webView: WebView
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // The stock WebView UA contains "; wv", a well-known automation
            // tell. Present as ordinary mobile Chrome instead.
            settings.userAgentString = LidlApi.USER_AGENT

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    maybeFinish(url)
                }
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        setContentView(webView)
        webView.loadUrl(LidlApi.ACCOUNT_URL)
    }

    /**
     * If we are back on lidl.co.uk holding authenticated cookies, we are done.
     */
    private fun maybeFinish(url: String?) {
        if (finished || url == null) return
        if (!url.startsWith(LidlApi.BASE)) return

        val jar = CookieManager.getInstance().getCookie(LidlApi.BASE)
        if (!LidlSession.looksAuthenticated(jar)) return

        finished = true
        CookieManager.getInstance().flush()
        LidlSession(this).cookieHeader = jar
        Toast.makeText(this, "Connected to Lidl", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CONNECTED, true))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONNECTED = "connected"
        fun intent(context: Context) = Intent(context, LidlLoginActivity::class.java)
    }
}
