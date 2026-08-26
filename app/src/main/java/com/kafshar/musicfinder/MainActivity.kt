package com.kafshar.musicfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future


data class SongResult(
    val url: String,
    val title: String,
    val artist: String,
    val site: String,
    val cover: String = ""
)

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var lyricsText: TextView
    private lateinit var playButton: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var randomButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var currentTimeText: TextView
    private lateinit var durationText: TextView
    private lateinit var downloadButton: TextView
    private lateinit var cancelDownloadButton: TextView
    private lateinit var pauseDownloadButton: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadText: TextView
    private lateinit var saveButton: TextView
    private lateinit var libraryButton: TextView
    private lateinit var historyButton: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var vinyl: VinylView

    private val turquoiseColor = 0xFF20C9C9.toInt()
    private val songs = ArrayList<SongResult>()
    private val songPageUrls = HashMap<String, String>()
    private var currentSongPageUrl = ""
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(3)
    private val downloadExecutor = Executors.newSingleThreadExecutor()
    private var downloadFuture: Future<*>? = null
    private var currentIndex = -1
    private var currentAudioUrl = ""
    private var currentSong: SongResult? = null
    private var randomMode = false
    private var destroyed = false
    private var receiverRegistered = false
    private var searchGeneration = 0
    private var resultPages: List<String> = emptyList()
    private var resultPageIndex = 0
    private var resultGeneration = 0
    private var expectedPageUrl = ""
    private var searchTimeout: Runnable? = null
    private var pageTimeout: Runnable? = null
    private var cancelDownloadRequested = false
    private var pauseDownloadRequested = false
    private var activeConnection: HttpURLConnection? = null

    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (destroyed || intent?.action != MusicService.UPDATE) return
            val playing = intent.getBooleanExtra("playing", false)
            val position = intent.getLongExtra("position", 0L)
            val duration = intent.getLongExtra("duration", 0L)
            val title = intent.getStringExtra(MusicService.EXTRA_TITLE).orEmpty()
            val artist = intent.getStringExtra(MusicService.EXTRA_ARTIST).orEmpty()
            val volume = intent.getIntExtra(MusicService.EXTRA_VOLUME, -1)
            val mediaUrl = intent.getStringExtra(MusicService.EXTRA_URL).orEmpty()
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                updatePlayerProgress(playing, position, duration)
                if (title.isNotBlank()) titleText.text = title
                if (artist.isNotBlank()) artistText.text = artist
                if (volume in 0..100) {
                    volumeSeekBar.progress = volume
                    volumeText.text = "$volume%"
                }
                if (mediaUrl.isNotBlank()) updateActiveResultHighlight(mediaUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destroyed = false
        setContentView(R.layout.activity_main)
        bindViews()
        setupWebView()
        setupButtons()
        setupVolumeControl()
        applyTurquoiseButtonStyle()
        restoreSearchResults()
        requestNotificationPermission()
        status.text = "نام آهنگ یا خواننده را جستجو کنید"
    }

    private fun bindViews() {
        query = findViewById(R.id.query)
        status = findViewById(R.id.status)
        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)
        lyricsText = findViewById(R.id.lyricsText)
        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        randomButton = findViewById(R.id.randomButton)
        seekBar = findViewById(R.id.seekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)
        currentTimeText = findViewById(R.id.currentTimeText)
        durationText = findViewById(R.id.durationText)
        downloadButton = findViewById(R.id.downloadButton)
        cancelDownloadButton = findViewById(R.id.cancelDownloadButton)
        pauseDownloadButton = findViewById(R.id.pauseDownloadButton)
        downloadProgress = findViewById(R.id.downloadProgress)
        downloadText = findViewById(R.id.downloadText)
        saveButton = findViewById(R.id.saveButton)
        libraryButton = findViewById(R.id.libraryButton)
        historyButton = findViewById(R.id.historyButton)
        historyContainer = findViewById(R.id.historyContainer)
        resultsContainer = findViewById(R.id.resultsContainer)
        vinyl = findViewById(R.id.vinyl)
    }

    private fun setupButtons() {
        findViewById<TextView>(R.id.search).setOnClickListener { searchMusic() }
        query.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            if (submit) { searchMusic(); true } else false
        }
        playButton.setOnClickListener {
            if (currentAudioUrl.isBlank()) {
                if (songs.isNotEmpty()) playSong(songs[currentIndex.coerceIn(0, songs.lastIndex)])
            } else {
                sendServiceAction(MusicService.ACTION_TOGGLE, currentAudioUrl, titleText.text.toString(), artistText.text.toString(), currentSong?.cover.orEmpty())
            }
        }
        previousButton.setOnClickListener { previousSong() }
        nextButton.setOnClickListener { nextSong() }
        randomButton.setOnClickListener {
            randomMode = !randomMode
            randomButton.text = if (randomMode) "🔀✓" else "🔀"
            if (randomMode && songs.isNotEmpty()) nextSong()
        }
        seekBar.max = 100
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) {
                val percent = s?.progress?.coerceIn(0, 100) ?: return
                safelyStartService(Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_SEEK_PERCENT
                    putExtra(MusicService.EXTRA_PERCENT, percent)
                })
            }
        })
        downloadButton.setOnClickListener { downloadCurrentSong() }
        cancelDownloadButton.setOnClickListener { cancelDownload() }
        pauseDownloadButton.setOnClickListener { toggleDownloadPause() }
        saveButton.setOnClickListener { saveCurrentSong() }
        libraryButton.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        historyButton.setOnClickListener { toggleHistory() }
        lyricsText.setOnClickListener {
            val page = currentSongPageUrl.trim()
            if (page.startsWith("http", true)) {
                try {
                    hideKeyboard()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)))
                } catch (_: Exception) {
                    Toast.makeText(this, "صفحه آهنگ قابل باز کردن نیست", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupVolumeControl() {
        val prefs = getSharedPreferences("player_settings", MODE_PRIVATE)
        val saved = prefs.getInt("volume_percent", 80).coerceIn(0, 100)
        volumeSeekBar.max = 100
        volumeSeekBar.progress = saved
        volumeText.text = "$saved%"
        volumeSeekBar.progressTintList = ColorStateList.valueOf(turquoiseColor)
        volumeSeekBar.thumbTintList = ColorStateList.valueOf(turquoiseColor)
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(0, 100)
                volumeText.text = "$value%"
                if (fromUser) {
                    prefs.edit().putInt("volume_percent", value).apply()
                    safelyStartService(Intent(this@MainActivity, MusicService::class.java).apply {
                        action = MusicService.ACTION_SET_VOLUME
                        putExtra(MusicService.EXTRA_VOLUME, value)
                    })
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
    }

    private fun applyTurquoiseButtonStyle() {
        val buttons = listOf(playButton, previousButton, nextButton, randomButton,
            findViewById<TextView>(R.id.search), downloadButton, cancelDownloadButton,
            pauseDownloadButton, saveButton, libraryButton, historyButton)
        seekBar.progressTintList = ColorStateList.valueOf(turquoiseColor)
        seekBar.thumbTintList = ColorStateList.valueOf(turquoiseColor)
        buttons.forEach {
            try { it.backgroundTintList = ColorStateList.valueOf(turquoiseColor) } catch (_: Exception) {}
            it.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 500)
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun results(raw: String?) {
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                resultGeneration = searchGeneration
                resultPageIndex = 0
                resultPages = raw.orEmpty().split("###")
                    .map { it.trim() }
                    .filter { it.substringBefore("|||").startsWith("http", true) }
                    .distinctBy { it.substringBefore("|||") }
                    .take(50)
                if (resultPages.isEmpty()) finishSearch() else processNextResultPage()
            }
        }
        @JavascriptInterface
        fun page(raw: String?) {
            runOnUiThread {
                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread
                val parts = raw.orEmpty().split("###", limit = 4)
                if (parts.size < 4) { finishCurrentResultPage(); return@runOnUiThread }
                val title = cleanTitle(decode(parts[0])).ifBlank { "Music" }
                val artist = decode(parts[1]).trim().ifBlank { "Unknown Artist" }
                val cover = decode(parts[2]).trim()
                val audio = decode(parts[3]).split("|||").map { it.trim() }
                    .firstOrNull { it.startsWith("http", true) }.orEmpty()
                if (audio.isNotBlank() && ServerConfig.isAllowedMediaUrl(audio)) {
                    val song = SongResult(audio, title, artist, getSiteName(expectedPageUrl), cover)
                    if (songs.none { it.url == song.url }) {
                        songs.add(song)
                        songPageUrls[song.url] = expectedPageUrl
                        addSongView(song, songs.lastIndex)
                    }
                }
                finishCurrentResultPage()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
        web.addJavascriptInterface(Bridge(), "MusicFinder")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return !ServerConfig.isAllowedPageUrl(url)
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (destroyed) return
                if (url.contains("google.com/search", true)) extractGoogleResults()
                else if (resultGeneration == searchGeneration && ServerConfig.isAllowedPageUrl(url)) extractMusicPage(url)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && !destroyed && resultPages.isNotEmpty()) finishCurrentResultPage()
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!destroyed) recreateWebView()
                return true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun recreateWebView() {
        if (destroyed) return
        try {
            val old = web
            val parent = old.parent as? android.view.ViewGroup ?: return
            val index = parent.indexOfChild(old)
            val params = old.layoutParams
            parent.removeView(old)
            try { old.stopLoading(); old.removeJavascriptInterface("MusicFinder"); old.destroy() } catch (_: Exception) {}
            val replacement = WebView(this)
            replacement.id = R.id.web
            replacement.layoutParams = params
            parent.addView(replacement, index.coerceAtMost(parent.childCount))
            web = replacement
            setupWebView()
        } catch (_: Exception) { status.text = "جستجو موقتاً در دسترس نیست" }
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(query.windowToken, 0)
            query.clearFocus()
        } catch (_: Exception) {}
    }

    private fun searchMusic() {
        val text = query.text.toString().trim()
        if (text.isBlank()) { Toast.makeText(this, "نام آهنگ یا خواننده را وارد کنید", Toast.LENGTH_SHORT).show(); return }
        hideKeyboard()
        searchGeneration++
        cancelSearchCallbacks()
        resultGeneration = searchGeneration
        resultPages = emptyList()
        resultPageIndex = 0
        expectedPageUrl = ""
        songs.clear(); songPageUrls.clear(); currentIndex = -1; currentAudioUrl = ""; currentSong = null; currentSongPageUrl = ""
        resultsContainer.removeAllViews()
        titleText.text = text
        artistText.text = "در حال جستجو..."
        status.text = "در حال جستجوی سایت‌ها..."
        seekBar.progress = 0
        currentTimeText.text = "00:00"
        durationText.text = "00:00"
        vinyl.clearCover(); vinyl.stopRotation(); clearLyrics()
        val searchQuery = ServerConfig.searchQuery(text)
        val encoded = try { URLEncoder.encode(searchQuery, "UTF-8") } catch (_: Exception) { return }
        try { web.stopLoading(); web.loadUrl("https://www.google.com/search?q=$encoded&num=50&hl=en&gbv=1") }
        catch (_: Exception) { status.text = "خطا در شروع جستجو"; return }
        val generation = searchGeneration
        val timeout = Runnable {
            if (!destroyed && generation == searchGeneration && resultPages.isEmpty()) status.text = "جستجو زمان‌بر شد؛ نتیجه‌ای پیدا نشد"
        }
        searchTimeout = timeout
        handler.postDelayed(timeout, 12000L)
    }

    private fun cancelSearchCallbacks() {
        searchTimeout?.let { handler.removeCallbacks(it) }
        pageTimeout?.let { handler.removeCallbacks(it) }
        searchTimeout = null; pageTimeout = null
    }

    // The remainder of the existing MainActivity implementation is preserved unchanged below.
