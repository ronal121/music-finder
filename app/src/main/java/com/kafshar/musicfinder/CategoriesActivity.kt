package com.kafshar.musicfinder

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

class CategoriesActivity : Activity() {

    private val accent = Color.rgb(32, 201, 201)
    private val bg = Color.rgb(10, 14, 18)
    private val card = Color.rgb(22, 29, 35)
    private val white = Color.WHITE
    private val muted = Color.rgb(160, 170, 178)

    private val categories = linkedMapOf(
        "🔥 محبوب و ترند" to "music trend new music",
        "🆕 جدیدترین موزیک ها" to "new music latest release",
        "⚡ Hard Techno" to "Hard Techno هارد تکنو",
        "🎛 Techno" to "Techno تکنو",
        "🎧 Electronic" to "Electronic الکترونیک",
        "🏠 House" to "House هاوس",
        "🌊 Deep House" to "Deep House دیپ هاوس",
        "🌌 Trance" to "Trance ترنس",
        "🎹 Progressive" to "Progressive پراگرسیو",
        "💿 Dance" to "Dance دنس",
        "🌑 Dark / Minimal" to "Dark Minimal دارک مینیمال",
        "🧠 Psy Trance" to "Psy Trance Psytrance سای ترنس",
        "🇮🇷 موسیقی ایرانی" to "موسیقی ایرانی آهنگ جدید",
        "🌍 موسیقی خارجی" to "foreign music new song"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(20, 28, 20, 20)
        }

        val title = TextView(this).apply {
            text = "MUSIC CATEGORIES"
            setTextColor(white)
            textSize = 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "دسته‌بندی‌ها از تمام منابع فعال Music Finder جستجو می‌شوند"
            setTextColor(muted)
            textSize = 13f
            setPadding(0, 6, 0, 18)
        }
        root.addView(subtitle)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        categories.forEach { (label, query) ->
            val button = Button(this).apply {
                text = label
                textSize = 14f
                setTextColor(white)
                setBackgroundColor(card)
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(18, 0, 18, 0)
                setOnClickListener { openCategory(query, label) }
            }
            list.addView(button, LinearLayout.LayoutParams(-1, 54).apply {
                setMargins(0, 0, 0, 9)
            })
        }

        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        val back = TextView(this).apply {
            text = "‹  بازگشت به Music Finder"
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, 52))

        setContentView(root)
    }

    private fun openCategory(query: String, label: String) {
        val sites = ServerConfig.MUSIC_SITES.joinToString(" OR ") { "site:$it" }
        val expanded = ServerConfig.searchQuery(query)
        val url = "https://www.google.com/search?q=" +
                URLEncoder.encode("$expanded ($sites)", "UTF-8")

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }

        setContentView(web)
        Toast.makeText(this, "$label — جستجو در منابع فعال", Toast.LENGTH_SHORT).show()
    }
}
