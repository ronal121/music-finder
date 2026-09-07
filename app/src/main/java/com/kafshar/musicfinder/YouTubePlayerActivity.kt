package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView

class YouTubePlayerActivity : Activity() {
    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "video_title"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_player)

        val title = findViewById<TextView>(R.id.youtubeTitle)
        webView = findViewById(R.id.youtubeWebView)
        title.text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "YouTube" }

        val id = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        if (id.isBlank()) {
            finish()
            return
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = false
        }

        val embed = "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0"
        webView.loadUrl(embed)
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
