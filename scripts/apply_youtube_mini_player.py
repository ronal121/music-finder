from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
MARKER = "// YOUTUBE_MINI_PLAYER_V1"

text = MAIN.read_text(encoding="utf-8")

if MARKER not in text:
    anchor = "    private lateinit var vinyl: VinylView\n"
    if anchor not in text:
        raise SystemExit("vinyl field anchor not found")
    text = text.replace(anchor, anchor + "    private lateinit var youtubeMiniPlayer: WebView\n    private lateinit var youtubeMiniClose: TextView\n", 1)

    anchor = "        setupWebView()\n"
    if anchor not in text:
        raise SystemExit("setupWebView call not found")
    text = text.replace(anchor, anchor + "        setupYouTubeMiniPlayer()\n", 1)

    method = '''    // YOUTUBE_MINI_PLAYER_V1
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupYouTubeMiniPlayer() {
        youtubeMiniPlayer = findViewById(R.id.youtubeMiniPlayer)
        youtubeMiniClose = findViewById(R.id.youtubeMiniClose)
        youtubeMiniPlayer.settings.javaScriptEnabled = true
        youtubeMiniPlayer.settings.domStorageEnabled = true
        youtubeMiniPlayer.settings.mediaPlaybackRequiresUserGesture = false
        youtubeMiniPlayer.settings.userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
        youtubeMiniPlayer.webChromeClient = android.webkit.WebChromeClient()
        youtubeMiniPlayer.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }
        youtubeMiniClose.setOnClickListener {
            youtubeMiniPlayer.stopLoading()
            youtubeMiniPlayer.loadUrl("about:blank")
            youtubeMiniPlayer.visibility = View.GONE
            youtubeMiniClose.visibility = View.GONE
        }
    }

    private fun showMiniYouTubePlayer(url: String) {
        val id = youtubeVideoId(url)
        if (id.isBlank()) {
            Toast.makeText(this, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()
            return
        }
        youtubeMiniPlayer.visibility = View.VISIBLE
        youtubeMiniClose.visibility = View.VISIBLE
        youtubeMiniPlayer.loadUrl("https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0")
    }

'''
    anchor = "    private fun setupButtons() {\n"
    if anchor not in text:
        raise SystemExit("setupButtons anchor not found")
    text = text.replace(anchor, method + anchor, 1)

    pattern = r"    private fun addYouTubeView\(url: String, title: String\) \{.*?\n    \}\n\n    private fun youtubeVideoId"
    replacement = '''    private fun addYouTubeView(url: String, title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(0xFF15151D.toInt())
            setOnClickListener { showMiniYouTubePlayer(url) }
        }
        val cover = ImageView(this).apply {
            setBackgroundColor(0xFF22222A.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        row.addView(cover, LinearLayout.LayoutParams(58, 58))
        val id = youtubeVideoId(url)
        if (id.isNotBlank()) loadCover("https://i.ytimg.com/vi/$id/hqdefault.jpg", cover)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 8, 0)
        }
        val t = TextView(this).apply {
            text = if (title.isBlank()) "YouTube" else title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            maxLines = 2
        }
        val sub = TextView(this).apply {
            text = "YouTube • پخش داخل برنامه"
            setTextColor(0xFFFF5555.toInt())
            textSize = 11f
        }
        box.addView(t)
        box.addView(sub)
        row.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        resultsContainer.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 8)
        })
    }

    private fun youtubeVideoId'''
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("addYouTubeView function not found")
    MAIN.write_text(text, encoding="utf-8")

layout = LAYOUT.read_text(encoding="utf-8")
if 'android:id="@+id/youtubeMiniPlayer"' not in layout:
    anchor = '    <WebView android:id="@+id/web" android:layout_width="1dp" android:layout_height="1dp" android:visibility="visible" tools:ignore="WebViewLayout" />'
    overlay = '''    <WebView android:id="@+id/youtubeMiniPlayer" android:layout_width="240dp" android:layout_height="135dp" android:layout_gravity="top|end" android:layout_marginTop="12dp" android:layout_marginEnd="12dp" android:visibility="gone" android:elevation="20dp" android:background="@android:color/black" tools:ignore="WebViewLayout" />
    <TextView android:id="@+id/youtubeMiniClose" android:layout_width="32dp" android:layout_height="32dp" android:layout_gravity="top|end" android:layout_marginTop="6dp" android:layout_marginEnd="6dp" android:gravity="center" android:text="×" android:textColor="@color/white" android:textSize="22sp" android:background="@drawable/bg_secondary_button" android:elevation="21dp" android:visibility="gone" android:clickable="true" android:focusable="true" android:contentDescription="Close YouTube mini player" />
'''
    if anchor not in layout:
        raise SystemExit("web anchor not found")
    layout = layout.replace(anchor, overlay + anchor, 1)
    LAYOUT.write_text(layout, encoding="utf-8")

print("YouTube mini player patch applied")
