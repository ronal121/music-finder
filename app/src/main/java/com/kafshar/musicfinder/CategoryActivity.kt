package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

class CategoryActivity : Activity() {
    private lateinit var categorySearch: EditText
    private lateinit var chips: LinearLayout
    private lateinit var web: WebView

    private val categories = listOf(
        "آهنگ جدید", "پاپ", "رپ", "راک", "سنتی", "شاد", "غمگین", "عاشقانه",
        "قدیمی", "ریمیکس", "بی کلام", "موسیقی محلی", "مازندرانی", "کردی", "لری",
        "ترکی", "آذربایجانی", "عربی", "افغانی", "انگلیسی", "هندی", "اسپانیایی",
        "موسیقی الکترونیک", "هاوس", "تکنو", "ترنس", "جاز", "موسیقی فیلم", "نوحه", "دکلمه"
    )

    private val categorySites = listOf(
        "shabamusic.com", "matnmusic.com", "biya2ahang.ir", "trackmelody.com",
        "musicaz.ir", "hailymusic.ir", "songsun.ir", "rozsong.com", "sultanmusics.com",
        "myspotify.ir", "behmusic.com", "music-fa.com", "upmusics.com", "rozmusic.com",
        "sahand-music.ir", "musicdel.ir", "radiojavan.com", "behmusics.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 11, 15))
            setPadding(dp(16), dp(18), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "CATEGORIES"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = "جستجوی یکدست دسته‌بندی‌ها در سایت‌های موسیقی"
            setTextColor(Color.rgb(167, 167, 176))
            textSize = 12f
            setPadding(0, dp(5), 0, dp(12))
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        categorySearch = EditText(this).apply {
            hint = "مثلاً رپ، شاد، مازندرانی..."
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(120, 120, 130))
            setBackgroundColor(Color.rgb(28, 28, 36))
            setPadding(dp(12), 0, dp(12), 0)
        }
        searchRow.addView(categorySearch, LinearLayout.LayoutParams(0, dp(50), 1f))

        val searchButton = HarmonizedButton(this).apply {
            text = "جستجو"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { searchCategory(categorySearch.text.toString()) }
        }
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(92), dp(50)).apply {
            marginStart = dp(8)
        })
        root.addView(searchRow)

        chips = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val chipScroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            addView(chips)
        }
        root.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(210)).apply {
            topMargin = dp(12)
        })

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(Color.rgb(11, 11, 15))
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10)
        })

        setContentView(root)
        renderCategories(categories)
    }

    private fun renderCategories(source: List<String>) {
        chips.removeAllViews()
        var row: LinearLayout? = null
        source.forEachIndexed { index, category ->
            if (index % 3 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chips.addView(row, LinearLayout.LayoutParams(-1, dp(48)))
            }
            val button = HarmonizedButton(this).apply {
                text = category
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setOnClickListener {
                    categorySearch.setText(category)
                    searchCategory(category)
                }
            }
            row?.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(5)
                topMargin = dp(3)
                bottomMargin = dp(3)
            })
        }
    }

    private fun searchCategory(raw: String) {
        val category = raw.trim()
        if (category.isBlank()) {
            Toast.makeText(this, "یک دسته‌بندی وارد کن", Toast.LENGTH_SHORT).show()
            return
        }

        val sites = categorySites.joinToString(" OR ") { "site:$it" }
        val query = "(\"$category\" OR \"$category music\" OR \"آهنگ $category\") ($sites)"
        val url = "https://www.google.com/search?hl=fa&gbv=1&num=20&q=${URLEncoder.encode(query, "UTF-8")}"
        web.loadUrl(url)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
