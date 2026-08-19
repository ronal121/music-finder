package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        query = findViewById(R.id.query)
        web = findViewById(R.id.web)
        status = findViewById(R.id.status)
        val search: Button = findViewById(R.id.search)
        val menu: TextView = findViewById(R.id.menu)
        val filter: TextView = findViewById(R.id.filter)

        web.setBackgroundColor(Color.TRANSPARENT)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                status.text = "منبع: $url"
            }
        }

        fun doSearch() {
            val q = query.text.toString().trim()
            if (q.isEmpty()) return

            status.text = "در حال جستجوی سایت‌های ایرانی و خارجی…"

            // Google remains the discovery layer. The query explicitly
            // asks for music pages, while allowing both Persian and foreign sources.
            val smartQuery = "\"$q\" (آهنگ OR موزیک OR music OR song OR mp3)"
            val url = "https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(smartQuery, "UTF-8")

            web.loadUrl(url)
        }

        search.setOnClickListener { doSearch() }

        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        menu.setOnClickListener {
            Toast.makeText(this, "Music Finder • طراحی: KAFSHAR", Toast.LENGTH_SHORT).show()
        }

        filter.setOnClickListener {
            Toast.makeText(
                this,
                "نتایج ایرانی + خارجی • پخش مستقیم در صورت امکان",
                Toast.LENGTH_SHORT
            ).show()
        }

        web.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(
                this,
                "برای پخش، از پلیر خود سایت استفاده کنید.",
                Toast.LENGTH_SHORT
            ).show()
        }

        web.loadUrl("https://www.google.com/")
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack()
        else super.onBackPressed()
    }
}
