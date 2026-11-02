package com.example.iptvapp.movies

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.example.iptvapp.R

/**
 * Plays a VidSrc (vidsrc.wiki) movie or TV embed in a WebView with strict
 * security.
 *
 * Only embed URLs that [VidSrcUrlBuilder] itself could have built are
 * accepted (see [VidSrcUrlBuilder.isAllowedEmbedUrl]) — arbitrary URLs
 * from the UI or network are never loaded. JavaScript is enabled (required
 * by the embedded player) but no JavaScript interfaces are exposed,
 * file/content access is disabled, mixed content stays blocked, and Safe
 * Browsing is enabled where available.
 *
 * Full-screen video is supported via [WebChromeClient.onShowCustomView]
 * and [WebChromeClient.onHideCustomView]. Back navigation exits
 * full-screen or finishes the activity. The WebView is paused and
 * destroyed when the activity finishes.
 */
class VidSrcPlayerActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    companion object {
        private const val EXTRA_EMBED_URL = "embed_url"

        /**
         * Creates an intent to open a VidSrc embed URL. The URL must have
         * been constructed by [VidSrcUrlBuilder] — callers should not pass
         * arbitrary strings.
         */
        fun createIntent(context: Activity, embedUrl: String): Intent {
            return Intent(context, VidSrcPlayerActivity::class.java).apply {
                putExtra(EXTRA_EMBED_URL, embedUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val embedUrl = intent.getStringExtra(EXTRA_EMBED_URL) ?: run {
            finish()
            return
        }

        // Reject any URL that wasn't constructed by VidSrcUrlBuilder.
        if (!VidSrcUrlBuilder.isAllowedEmbedUrl(embedUrl)) {
            finish()
            return
        }

        setContentView(R.layout.activity_vidsrc_player)

        webView = findViewById(R.id.webview)
        configureWebView(webView)
        webView.loadUrl(embedUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        with(webView.settings) {
            // Required by the embedded player.
            javaScriptEnabled = true
            // Required for some player features.
            domStorageEnabled = true
            // Mixed content stays blocked — never allow HTTP resources
            // from an HTTPS page.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Disable file and content access — the page must not read
            // local files or expose content providers.
            setAllowFileAccess(false)
            setAllowContentAccess(false)
            setAllowFileAccessFromFileURLs(false)
            setAllowUniversalAccessFromFileURLs(false)
            // Safe Browsing where available (API 26+).
            safeBrowsingEnabled = true
            // Do NOT spoof the user agent — send the default.
            userAgentString = null
            // No JavaScript interfaces are exposed to the webpage.
        }

        webView.webViewClient = object : WebViewClient() {
            // Only allow https:// navigations. Reject any attempt to load
            // a custom scheme (e.g. javascript:, file:, content:, etc.).
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                return !url.startsWith("https://")
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showError("Unable to load the video player ($errorCode).")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Full-screen video support.
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                customViewCallback = callback
                if (view == null) {
                    // Nothing to show — tell the page it can leave full-screen.
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    return
                }
                customView = view
                window.decorView
                    .findViewById<ViewGroup>(android.R.id.content)
                    ?.addView(view)
                setFullScreen(true)
            }

            override fun onHideCustomView() {
                exitFullScreen()
            }
        }
    }

    /**
     * Single teardown path for full-screen video: removes the custom view,
     * clears state, notifies the page, and restores the system bars.
     *
     * State is cleared *before* the callback fires so a synchronous
     * onHideCustomView callback cannot re-enter this method with stale
     * state.
     */
    private fun exitFullScreen() {
        customView?.let { v ->
            window.decorView
                .findViewById<ViewGroup>(android.R.id.content)
                ?.removeView(v)
        }
        customView = null
        val callback = customViewCallback
        customViewCallback = null
        callback?.onCustomViewHidden()
        setFullScreen(false)
    }

    private fun showError(message: String) {
        webView.visibility = View.GONE
        val errorView = android.widget.TextView(this)
        errorView.text = message
        errorView.setTextColor(0xFFF2F2F2.toInt())
        errorView.textSize = 16f
        errorView.gravity = android.view.Gravity.CENTER
        setContentView(errorView)
    }

    private fun setFullScreen(fullScreen: Boolean) {
        val uiFlags = if (fullScreen) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        window.decorView.systemUiVisibility = uiFlags
    }

    override fun onBackPressed() {
        // Exit full-screen first if active, then finish.
        if (customView != null) {
            exitFullScreen()
            return
        }
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}