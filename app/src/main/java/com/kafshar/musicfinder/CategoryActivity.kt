package com.kafshar.musicfinder

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class CategoryActivity : Activity() {
    private lateinit var categorySearch: EditText
    private lateinit var chips: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var web: WebView
    private val executor = Executors.newFixedThreadPool(4)
    private var searchToken = 0

    private val categories = listOf(
        "پاپ", "رپ", "راک", "سنتی", "شاد", "غمگین", "عاشقانه", "قدیمی", "ریمیکس",
        "بی کلام", "محلی", "مازندرانی", "گیلکی", "کردی", "لری", "ترکی", "آذری", "عربی",
        "افغانی", "انگلیسی", "هندی", "اسپانیایی", "الکترونیک", "Electro", "EDM", "House",
        "Deep House", "Tech House", "Techno", "Trance", "Dubstep", "Drum & Bass", "Ambient",
        "Jazz", "Blues", "Metal", "Punk", "موسیقی فیلم", "نوحه", "دکلمه",
        "محسن چاوشی", "ابی", "محسن یگانه", "شادمهر", "معین", "داریوش", "گوگوش", "مهستی",
        "ستار", "هایده", "فرهاد", "فریدون فروغی", "لیلا فروهر", "سیاوش قمیشی", "مرتضی پاشایی",
        "رضا بهرام", "همایون شجریان", "علیرضا قربانی"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

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
            text = "هر دسته در تمام سایت‌های مرجع موسیقی جستجو می‌شود"
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
            hint = "مثلاً EDM، ترنس، چاوشی، مازندرانی..."
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
            setOnClickListener { searchCategory(categorySearch.text.toString()) }
        }
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(92), dp(50)).apply { marginStart = dp(8) })
        root.addView(searchRow)

        chips = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chipScroll = android.widget.ScrollView(this).apply { addView(chips) }
        root.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(12) })

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val resultScroll = android.widget.ScrollView(this).apply { addView(results) }
        root.addView(resultScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(Color.rgb(11, 11, 15))
        }
        // Keep the actual page viewer hidden until a result is selected.
        web.visibility = android.view.View.GONE
        root.addView(web, LinearLayout.LayoutParams(-1, dp(1)))

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

        val token = ++searchToken
        results.removeAllViews()
        addStatus("در حال جستجوی Google در تمام سایت‌های مرجع...")
        val queries = ReferenceSiteQueries.build(category)
        val provider = SearchNetwork.providers.first()
        val merged = LinkedHashMap<String, GoogleResultParser.Result>()

        executor.execute {
            queries.forEach { query ->
                if (token != searchToken) return@execute
                try {
                    provider.search(query, 20).forEach { result ->
                        val key = result.url.substringBefore('#').trimEnd('/').lowercase()
                        if (key.isNotBlank()) synchronized(merged) { merged.putIfAbsent(key, result) }
                    }
                } catch (_: Exception) { }
            }

            val list = synchronized(merged) { merged.values.take(100).toList() }
            runOnUiThread {
                if (token != searchToken) return@runOnUiThread
                results.removeAllViews()
                if (list.isEmpty()) {
                    addStatus("برای این دسته نتیجه‌ای از سایت‌های مرجع پیدا نشد")
                    return@runOnUiThread
                }
                addStatus("${list.size} نتیجه از سایت‌های مرجع")
                list.forEach { item ->
                    val button = HarmonizedButton(this).apply {
                        text = "${item.title.ifBlank { "نتیجه موسیقی" }}\n${item.url}"
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        textSize = 12f
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        setOnClickListener {
                            web.visibility = android.view.View.VISIBLE
                            web.loadUrl(item.url)
                        }
                    }
                    results.addView(button, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(6) })
                }
            }
        }
    }

    private fun addStatus(text: String) {
        val status = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(167, 167, 176))
            textSize = 12f
            setPadding(dp(4), dp(5), dp(4), dp(8))
        }
        results.addView(status, 0)
    }

    override fun onDestroy() {
        searchToken++
        executor.shutdownNow()
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
