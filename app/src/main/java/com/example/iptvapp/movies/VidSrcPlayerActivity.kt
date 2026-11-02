package com.example.iptvapp.movies

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.iptvapp.R

/**
 * Plays a VidSrc movie or TV embed in a WebView with strict security.
 *
 * Only HTTPS embed URLs constructed by [VidSrcUrlBuilder] are accepted —
 * arbitrary URLs from the UI or feed are never loaded. JavaScript is
 * enabled (required by the embedded player) but no JavaScript interfaces
 * are exposed, file/content access is disabled, mixed content stays
 * blocked, and Safe Browsing is enabled where available.
 *
 * Full-screen video is supported via [WebChromeClient.onShowCustomView]
 * and [onHideCustomView]. Back navigation exits full-screen or finishes
 * the activity. The WebView is paused and destroyed when the activity
 * finishes.
 */
class VidSrcPlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebView.CustomViewCallback? = null

    companion object {
        private const val EXTRA_EMBED_URL = "embed_url"

        /**
         * Creates an intent to open a VidSrc embed URL. The URL must have
         * been constructed by [VidSrcUrlBuilder.buildEmbedUrl] — callers
         * should not pass arbitrary strings.
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

        // Reject any URL that wasn't constructed by VidSrcUrlBuilder:
        // only https://vidsrc.sh/embed/{movie,tv}/{id} is allowed.
        if (!isValidEmbedUrl(embedUrl)) {
            finish()
            return
        }

        setContentView(R.layout.activity_vidsrc_player)

        webView = findViewById(R.id.webview)
        configureWebView(webView)
        webView.loadUrl(embedUrl)
    }

    /**
     * Strictly validates that [url] is an HTTPS VidSrc embed URL. Any other
     * scheme, host, or path is rejected.
     *
     * Validation uses [android.net.Uri] parsing rather than string
     * prefix checks, so the URL structure is verified component by
     * component: scheme, host, port, user info, path segments, query,
     * and fragment.
     */
    private fun isValidEmbedUrl(url: String): Boolean {
        val uri = Uri.parse(url) ?: return false

        // Scheme must be exactly HTTPS.
        if (uri.scheme != "https") return false

        // Host must be exactly vidsrc.sh.
        if (uri.host != "vidsrc.sh") return false

        // No user information (e.g. user@host).
        if (uri.userInfo != null && uri.userInfo.isNotEmpty()) return false

        // No explicit port (default HTTPS port only).
        if (uri.port != -1) return false

        // No query parameters or fragment.
        if (uri.query != null) return false
        if (uri.fragment != null) return false

        // Path must be exactly /embed/movie/{id} or /embed/tv/{id}.
        val segments = uri.pathSegments
        if (segments.size != 3) return false
        if (segments[0] != "embed") return false
        val mediaKind = segments[1]
        if (mediaKind != "movie" && mediaKind != "tv") return false

        // The ID must be a valid IMDb or TMDB ID.
        val id = segments[2]
        return VidSrcUrlBuilder.isValidId(id)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val settings = webView.settings
        with(settings) {
            // Required by the embedded player.
            javaScriptEnabled = true
            // Required for some player features.
            domStorageEnabled = true
            // Mixed content stays blocked — never allow HTTP resources
            // from an HTTPS page.
            mixedContentMode = WebView.MIXED_CONTENT_NEVER_ALLOW
            // Disable file and content access — the page must not read
            // local files or expose content providers.
            setAllowFileAccess(false)
            setAllowContentAccess(false)
            setAllowFileAccessFromFileURLs(false)
            setUniversalAccessFromFileURLs(false)
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
                return url !startsWith "https://"
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

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            // Full-screen video support.
            override fun onShowCustomView(view: View?, callback: WebView.CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                view?.let { v =>
                    val decor = window.decorView
                    decor.findViewById<ViewGroup>(android.R.id.content)?.addView(v)
                }
                setFullScreen(true)
            }

            override fun onHideCustomView() {
                customView?.let { v ->
                    val decor = window.decorView
                    decor.findViewById<ViewGroup>(android.R.id.content)?.removeView(v)
                }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                setFullScreen(false)
            }
        }
    }

    private fun showError(message: String) {
        webView.visibility = android.view.View.GONE
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
            onHideCustomView()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
