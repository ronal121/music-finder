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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.provider.MediaStore
import android.util.LruCache
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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
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

    private val turquoiseColor =
        0xFF20C9C9.toInt()

    private val turquoiseDarkColor =
        0xFF119999.toInt()

    private val songs =
        ArrayList<SongResult>()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val imageExecutor =
        Executors.newFixedThreadPool(2)

    private val downloadExecutor =
        Executors.newSingleThreadExecutor()

    private val coverCache =
        object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        ) {
            override fun sizeOf(
                key: String,
                bitmap: Bitmap
            ): Int {
                return bitmap.byteCount / 1024
            }
        }

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

    private var searchTimeoutRunnable: Runnable? = null
    private var pageTimeoutRunnable: Runnable? = null

    private var downloadFuture: Future<*>? = null

    @Volatile
    private var cancelDownloadRequested = false

    @Volatile
    private var pauseDownloadRequested = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    private var lastProgressUpdate = 0L
    private var lastProgressValue = -1

    private var lyricsRequestKey = ""
    private var lyricsRequestGeneration = 0

    private val playerReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (destroyed) return

                if (intent?.action != MusicService.UPDATE) {
                    return
                }

                val playing =
                    intent.getBooleanExtra(
                        "playing",
                        false
                    )

                val position =
                    intent.getLongExtra(
                        "position",
                        0L
                    )

                val duration =
                    intent.getLongExtra(
                        "duration",
                        0L
                    )

                val title =
                    intent.getStringExtra(
                        "title"
                    ) ?: ""

                val artist =
                    intent.getStringExtra(
                        "artist"
                    ) ?: ""

                val volume =
                    intent.getIntExtra(
                        "volume",
                        -1
                    )

                val mediaUrl = intent.getStringExtra("mediaUrl") ?: ""

                runOnUiThread {

                    if (destroyed) {
                        return@runOnUiThread
                    }

                    updatePlayerProgress(
                        playing,
                        position,
                        duration
                    )

                    if (title.isNotBlank()) {
                        titleText.text = title
                    }

                    if (artist.isNotBlank()) {
                        artistText.text = artist
                    }

                    if (title.isNotBlank() && artist.isNotBlank()) {
                        loadLyricsFor(title, artist)
                    }

                    if (volume in 0..100) {
                        volumeSeekBar.progress = volume
                        volumeText.text = "$volume%"
                    }

                    if (mediaUrl.isNotBlank()) updateActiveResultHighlight(mediaUrl)
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        destroyed = false

        setContentView(
            R.layout.activity_main
        )

        bindViews()
        setupWebView()
        setupButtons()
        setupVolumeControl()
        applyTurquoiseButtonStyle()
        restoreSearchResults()

        requestNotificationPermission()

        status.text =
            "نام آهنگ یا خواننده را جستجو کنید"
    }

    private fun bindViews() {

        query =
            findViewById(R.id.query)

        status =
            findViewById(R.id.status)

        titleText =
            findViewById(R.id.titleText)

        artistText =
            findViewById(R.id.artistText)

        lyricsText =
            findViewById(R.id.lyricsText)
        lyricsText.movementMethod = ScrollingMovementMethod()

        playButton =
            findViewById(R.id.playButton)

        previousButton =
            findViewById(R.id.previousButton)

        nextButton =
            findViewById(R.id.nextButton)

        randomButton =
            findViewById(R.id.randomButton)

        seekBar =
            findViewById(R.id.seekBar)

        volumeSeekBar =
            findViewById(R.id.volumeSeekBar)

        volumeText =
            findViewById(R.id.volumeText)

        currentTimeText =
            findViewById(R.id.currentTimeText)

        durationText =
            findViewById(R.id.durationText)

        downloadButton =
            findViewById(R.id.downloadButton)

        cancelDownloadButton =
            findViewById(R.id.cancelDownloadButton)

        pauseDownloadButton =
            findViewById(R.id.pauseDownloadButton)

        downloadProgress =
            findViewById(R.id.downloadProgress)

        downloadText =
            findViewById(R.id.downloadText)

        saveButton =
            findViewById(R.id.saveButton)

        libraryButton =
            findViewById(R.id.libraryButton)

        historyButton =
            findViewById(R.id.historyButton)

        historyContainer =
            findViewById(R.id.historyContainer)

        resultsContainer =
            findViewById(R.id.resultsContainer)

        vinyl =
            findViewById(R.id.vinyl)

        web =
            findViewById(R.id.web)
    }

    private fun setupButtons() {
        findViewById<TextView>(R.id.search).setOnClickListener { searchMusic() }

        query.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            if (submit) {
                searchMusic()
                true
            } else false
        }

        playButton.setOnClickListener {
            if (currentAudioUrl.isBlank()) {
                if (songs.isNotEmpty()) {
                    currentIndex = currentIndex.coerceIn(0, songs.lastIndex)
                    playSong(songs[currentIndex])
                }
            } else {
                sendServiceAction(
                    MusicService.ACTION_TOGGLE,
                    currentAudioUrl,
                    titleText.text.toString(),
                    artistText.text.toString(),
                    currentSong?.cover.orEmpty()
                )
            }
        }

        previousButton.setOnClickListener { previousSong() }
        nextButton.setOnClickListener { nextSong() }

        randomButton.setOnClickListener {
            randomMode = !randomMode
            randomButton.text = if (randomMode) "🔀✓" else "🔀"
            if (randomMode && songs.isNotEmpty()) {
                val nextIndex = if (songs.size == 1) 0 else {
                    var value: Int
                    do { value = java.util.Random().nextInt(songs.size) } while (value == currentIndex)
                    value
                }
                currentIndex = nextIndex
                playSong(songs[nextIndex])
            }
        }

        seekBar.max = 100
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val percent = seekBar?.progress?.coerceIn(0, 100) ?: return
                val intent = Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_SEEK_PERCENT
                    putExtra(MusicService.EXTRA_PERCENT, percent)
                }
                safelyStartService(intent)
            }
        })

        downloadButton.setOnClickListener { downloadCurrentSong() }
        cancelDownloadButton.setOnClickListener { cancelDownload() }
        pauseDownloadButton.setOnClickListener { toggleDownloadPause() }
        saveButton.setOnClickListener { saveCurrentSong() }
        libraryButton.setOnClickListener {
            try { startActivity(Intent(this, LibraryActivity::class.java)) }
            catch (_: Exception) { Toast.makeText(this, "کتابخانه در دسترس نیست", Toast.LENGTH_SHORT).show() }
        }
        historyButton.setOnClickListener { toggleHistory() }
    }

    private fun setupVolumeControl() {

        val prefs =
            getSharedPreferences(
                "player_settings",
                MODE_PRIVATE
            )

        val savedVolume =
            prefs.getInt(
                "volume_percent",
                80
            ).coerceIn(0, 100)

        volumeSeekBar.max = 100
        volumeSeekBar.progress = savedVolume
        volumeText.text = "$savedVolume%"

        volumeSeekBar.progressTintList =
            ColorStateList.valueOf(
                turquoiseColor
            )

        volumeSeekBar.thumbTintList =
            ColorStateList.valueOf(
                turquoiseColor
            )

        volumeSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    val volume =
                        progress.coerceIn(0, 100)

                    volumeText.text =
                        "$volume%"

                    if (!fromUser) {
                        return
                    }

                    prefs.edit()
                        .putInt(
                            "volume_percent",
                            volume
                        )
                        .apply()

                    val intent =
                        Intent(
                            this@MainActivity,
                            MusicService::class.java
                        ).apply {

                            action =
                                MusicService.ACTION_SET_VOLUME

                            putExtra(
                                MusicService.EXTRA_VOLUME,
                                volume
                            )
                        }

                    safelyStartService(intent)
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }
            }
        )
    }

    private fun applyTurquoiseButtonStyle() {

        val buttons =
            listOf(
                playButton,
                previousButton,
                nextButton,
                randomButton,
                findViewById<TextView>(R.id.search),
                downloadButton,
                cancelDownloadButton,
                pauseDownloadButton,
                saveButton,
                libraryButton,
                historyButton
            )

        seekBar.progressTintList =
            ColorStateList.valueOf(turquoiseColor)

        seekBar.thumbTintList =
            ColorStateList.valueOf(turquoiseColor)

        buttons.forEach { button ->

            try {

                button.setTextColor(
                    0xFFFFFFFF.toInt()
                )

                button.backgroundTintList =
                    ColorStateList.valueOf(
                        turquoiseColor
                    )

            } catch (_: Exception) {

                button.setTextColor(
                    0xFFFFFFFF.toInt()
                )

                button.setBackgroundColor(
                    turquoiseColor
                )
            }
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                500
            )
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun results(raw: String?) {
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                resultGeneration = searchGeneration
                resultPageIndex = 0
                resultPages = raw.orEmpty()
                    .split("###")
                    .map { it.trim() }
                    .filter { it.substringBefore("|||").startsWith("http", ignoreCase = true) }
                    .distinctBy { it.substringBefore("|||") }
                    .take(50)

                if (resultPages.isEmpty()) {
                    finishSearch()
                } else {
                    status.text = "در حال بررسی ${resultPages.size} نتیجه..."
                    processNextResultPage()
                }
            }
        }

        @JavascriptInterface
        fun page(raw: String?) {
            runOnUiThread {
                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread

                val parts = raw.orEmpty().split("###", limit = 4)
                if (parts.size < 4) {
                    finishCurrentResultPage()
                    return@runOnUiThread
                }

                val title = cleanTitle(decode(parts[0])).ifBlank { "Music" }
                val artist = decode(parts[1]).trim().ifBlank { "Unknown Artist" }
                val cover = decode(parts[2]).trim()
                val audioRaw = decode(parts[3]).trim()
                val audioUrl = audioRaw.split("|||")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("http", ignoreCase = true) }
                    .orEmpty()

                if (audioUrl.isNotBlank()) {
                    val song = SongResult(
                        url = audioUrl,
                        title = title,
                        artist = artist,
                        site = getSiteName(expectedPageUrl),
                        cover = cover
                    )
                    if (songs.none { it.url == song.url }) {
                        songs.add(song)
                        addSongView(song, songs.lastIndex)
                    }
                }

                finishCurrentResultPage()
            }
        }
    }

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(
    view: WebView
) {

    view.settings.apply {

        javaScriptEnabled = true
        domStorageEnabled = true

        mediaPlaybackRequiresUserGesture = false

        allowFileAccess = false
        allowContentAccess = false

        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)

        userAgentString =
            "Mozilla/5.0 (Linux; Android 12) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/128 Mobile Safari/537.36"
    }

    view.setLayerType(
        View.LAYER_TYPE_HARDWARE,
        null
    )

    view.addJavascriptInterface(
        Bridge(),
        "MusicFinder"
    )

    view.webViewClient =
        object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val url =
                    request.url.toString()

                /*
                 * Google باید داخل WebView باز بماند.
                 * قبلاً اینجا ServerConfig.isAllowedPageUrl()
                 * باعث می‌شد redirect های Google مسدود شوند.
                 */
                if (
                    url.contains(
                        "google.com",
                        ignoreCase = true
                    ) ||
                    url.contains(
                        "googleusercontent.com",
                        ignoreCase = true
                    ) ||
                    url.contains(
                        "gstatic.com",
                        ignoreCase = true
                    )
                ) {
                    return false
                }

                /*
                 * سایت‌های موسیقی مجاز
                 */
                if (
                    ServerConfig.isAllowedPageUrl(url)
                ) {
                    return false
                }

                return true
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?
            ) {

                super.onPageStarted(
                    view,
                    url,
                    favicon
                )

                if (destroyed) return
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {

                super.onPageFinished(
                    view,
                    url
                )

                if (destroyed) return

                /*
                 * وقتی Google Search کامل شد،
                 * لینک‌های سایت‌های موسیقی را استخراج می‌کنیم.
                 */
                if (
                    url.contains(
                        "google.com/search",
                        ignoreCase = true
                    )
                ) {

                    extractGoogleResults()

                    return
                }

                /*
                 * حالا وارد صفحه یکی از سایت‌های موسیقی شده‌ایم.
                 */
                if (
                    resultGeneration ==
                    searchGeneration &&
                    expectedPageUrl.isNotBlank() &&
                    ServerConfig.isAllowedPageUrl(url)
                ) {

                    extractMusicPage(url)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {

                super.onReceivedError(
                    view,
                    request,
                    error
                )

                if (
                    !request.isForMainFrame ||
                    destroyed
                ) {
                    return
                }

                if (
                    resultGeneration ==
                    searchGeneration &&
                    resultPages.isNotEmpty()
                ) {

                    finishCurrentResultPage()

                } else {

                    status.text =
                        "خطا در اتصال به جستجو"
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {

                if (destroyed) {
                    return true
                }

                status.text =
                    "در حال بازیابی جستجو..."

                recreateWebView()

                return true
            }
        }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebView() {
    configureWebView(web)
}

@SuppressLint("SetJavaScriptEnabled")
private fun recreateWebView() {

    if (destroyed) return

    try {

        val oldWeb = web

        val parent =
            oldWeb.parent as? android.view.ViewGroup
                ?: return

        val index =
            parent.indexOfChild(oldWeb)

        val oldParams =
            oldWeb.layoutParams

        parent.removeView(oldWeb)

        try {

            oldWeb.removeJavascriptInterface(
                "MusicFinder"
            )

            oldWeb.stopLoading()
            oldWeb.loadUrl("about:blank")
            oldWeb.removeAllViews()
            oldWeb.destroy()

        } catch (_: Exception) {
        }

        val newWeb =
            WebView(this)

        newWeb.id =
            R.id.web

        newWeb.layoutParams =
            oldParams
                ?: android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                )

        parent.addView(
            newWeb,
            index.coerceAtMost(
                parent.childCount
            )
        )

        web = newWeb

        configureWebView(web)

    } catch (_: Exception) {

        if (!destroyed) {

            status.text =
                "جستجو موقتاً در دسترس نیست"
        }
    }
}

private fun searchMusic() {

    if (destroyed) return

    val text =
        query.text
            .toString()
            .trim()

    if (text.isEmpty()) {

        Toast.makeText(
            this,
            "نام آهنگ یا خواننده را وارد کنید",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    /*
     * هر جستجوی جدید یک Generation جدید دارد.
     * این کار جلوی قاطی شدن نتایج جستجوی قبلی و جدید را می‌گیرد.
     */
    searchGeneration++

    cancelSearchCallbacks()

    resultPages = emptyList()
    resultPageIndex = 0
    resultGeneration = searchGeneration
    expectedPageUrl = ""

    songs.clear()
    currentIndex = -1

    resultsContainer.removeAllViews()

    titleText.text = text
    artistText.text = "در حال جستجو..."

    status.text =
        "در حال جستجوی سایت‌ها..."

    seekBar.progress = 0

    currentTimeText.text = "00:00"
    durationText.text = "00:00"

    vinyl.clearCover()
    vinyl.stopRotation()

    clearLyrics()

    /*
     * ServerConfig همچنان مسئول هوشمند کردن عبارت جستجو است.
     */
    val searchQuery =
        ServerConfig.searchQuery(text)

    val encoded =
        try {

            URLEncoder.encode(
                searchQuery,
                "UTF-8"
            )

        } catch (_: Exception) {

            status.text =
                "عبارت جستجو نامعتبر است"

            return
        }

    /*
     * num=50 برای گرفتن تعداد بیشتری نتیجه.
     *
     * hl=en:
     * خروجی Google برای WebView پایدارتر می‌شود.
     *
     * gbv=1:
     * نسخه ساده‌تر Google برای مرورگرهای embedded.
     */
    val url =
        "https://www.google.com/search" +
                "?q=$encoded" +
                "&num=50" +
                "&hl=en" +
                "&gbv=1"

    try {

        web.stopLoading()

        web.loadUrl(url)

    } catch (_: Exception) {

        status.text =
            "خطا در شروع جستجو"

        return
    }

    val generation =
        searchGeneration

    val timeout =
        Runnable {

            if (
                !destroyed &&
                generation == searchGeneration
            ) {

                status.text =
                    if (songs.isEmpty()) {
                        "جستجو زمان‌بر شد؛ نتیجه‌ای پیدا نشد"
                    } else {
                        "${songs.size} نتیجه پیدا شد"
                    }
            }
        }

    searchTimeoutRunnable =
        timeout

    mainHandler.postDelayed(
        timeout,
        12000L
    )
}

private fun cancelSearchCallbacks() {

    searchTimeoutRunnable?.let {
        mainHandler.removeCallbacks(it)
    }

    pageTimeoutRunnable?.let {
        mainHandler.removeCallbacks(it)
    }

    searchTimeoutRunnable = null
    pageTimeoutRunnable = null
}

private fun extractGoogleResults() {

    if (destroyed) return

    val generation =
        searchGeneration

    if (generation <= 0) return

    /*
     * دامنه‌های مجاز را از ServerConfig می‌گیریم.
     */
    val allowedHostsJs =
        ServerConfig.MUSIC_SITES
            .joinToString(",") {

                "\"" +
                        it
                            .trim()
                            .lowercase()
                            .replace(
                                "\\",
                                "\\\\"
                            )
                            .replace(
                                "\"",
                                "\\\""
                            ) +
                        "\""
            }

    val script = """
        (function() {

            try {

                var allowedHosts = [
                    $allowedHostsJs
                ];

                var found = [];

                function cleanText(value) {

                    if (!value) {
                        return "";
                    }

                    return value
                        .replace(
                            /[\r\n\t]+/g,
                            " "
                        )
                        .replace(
                            /\s+/g,
                            " "
                        )
                        .trim();
                }

                function decodeUrl(value) {

                    if (!value) {
                        return "";
                    }

                    try {
                        return decodeURIComponent(value);
                    } catch (e) {
                        return value;
                    }
                }

                function getRealUrl(href) {

                    if (!href) {
                        return "";
                    }

                    href =
                        href.trim();

                    /*
                     * Google redirect:
                     *
                     * /url?q=https://site.com/...
                     *
                     * /url?url=https://site.com/...
                     */
                    try {

                        var parsed =
                            new URL(
                                href,
                                window.location.href
                            );

                        var host =
                            (
                                parsed.hostname ||
                                ""
                            ).toLowerCase();

                        if (
                            host.indexOf(
                                "google."
                            ) >= 0
                        ) {

                            var q =
                                parsed.searchParams.get(
                                    "q"
                                );

                            if (
                                q &&
                                q.indexOf(
                                    "http"
                                ) === 0
                            ) {

                                return decodeUrl(q);
                            }

                            var urlParam =
                                parsed.searchParams.get(
                                    "url"
                                );

                            if (
                                urlParam &&
                                urlParam.indexOf(
                                    "http"
                                ) === 0
                            ) {

                                return decodeUrl(
                                    urlParam
                                );
                            }
                        }

                    } catch (e) {
                    }

                    return href;
                }

                function isAllowed(url) {

                    if (!url) {
                        return false;
                    }

                    var lower =
                        url.toLowerCase();

                    if (
                        lower.indexOf(
                            "google.com"
                        ) >= 0
                    ) {

                        /*
                         * لینک Google نباید وارد
                         * مرحله باز کردن سایت شود.
                         */
                        if (
                            lower.indexOf(
                                "google.com/url"
                            ) >= 0
                        ) {

                            return false;
                        }

                        if (
                            lower.indexOf(
                                "google.com/search"
                            ) >= 0
                        ) {

                            return false;
                        }
                    }

                    for (
                        var i = 0;
                        i < allowedHosts.length;
                        i++
                    ) {

                        var host =
                            allowedHosts[i];

                        if (!host) {
                            continue;
                        }

                        if (
                            lower.indexOf(
                                "://" + host
                            ) >= 0 ||
                            lower.indexOf(
                                "://www." + host
                            ) >= 0 ||
                            lower.indexOf(
                                "." + host
                            ) >= 0
                        ) {

                            return true;
                        }
                    }

                    return false;
                }

                var links =
                    document.querySelectorAll(
                        "a"
                    );

                for (
                    var i = 0;
                    i < links.length;
                    i++
                ) {

                    var link =
                        links[i];

                    var href =
                        link.href || "";

                    var realUrl =
                        getRealUrl(href);

                    if (
                        !isAllowed(realUrl)
                    ) {
                        continue;
                    }

                    realUrl =
                        realUrl.split("#")[0];

                    var duplicate =
                        false;

                    for (
                        var j = 0;
                        j < found.length;
                        j++
                    ) {

                        if (
                            found[j]
                                .split("|||")[0] ===
                            realUrl
                        ) {

                            duplicate = true;
                            break;
                        }
                    }

                    if (duplicate) {
                        continue;
                    }

                    var text =
                        cleanText(
                            link.innerText ||
                            link.textContent ||
                            ""
                        );

                    if (!text) {

                        text =
                            cleanText(
                                link.getAttribute(
                                    "aria-label"
                                ) || ""
                            );
                    }

                    found.push(
                        realUrl +
                        "|||" +
                        text
                    );

                    if (
                        found.length >= 50
                    ) {
                        break;
                    }
                }

                /*
                 * بعض نسخه‌های Google لینک اصلی را
                 * در data-href یا data-url قرار می‌دهند.
                 */
                if (
                    found.length === 0
                ) {

                    var dataElements =
                        document.querySelectorAll(
                            "[data-href], [data-url]"
                        );

                    for (
                        var k = 0;
                        k < dataElements.length;
                        k++
                    ) {

                        var element =
                            dataElements[k];

                        var dataUrl =
                            element.getAttribute(
                                "data-href"
                            ) ||
                            element.getAttribute(
                                "data-url"
                            ) ||
                            "";

                        var realDataUrl =
                            getRealUrl(dataUrl);

                        if (
                            !isAllowed(
                                realDataUrl
                            )
                        ) {
                            continue;
                        }

                        realDataUrl =
                            realDataUrl
                                .split("#")[0];

                        var duplicateData =
                            false;

                        for (
                            var x = 0;
                            x < found.length;
                            x++
                        ) {

                            if (
                                found[x]
                                    .split("|||")[0] ===
                                realDataUrl
                            ) {

                                duplicateData = true;
                                break;
                            }
                        }

                        if (duplicateData) {
                            continue;
                        }

                        var dataText =
                            cleanText(
                                element.innerText ||
                                element.textContent ||
                                ""
                            );

                        found.push(
                            realDataUrl +
                            "|||" +
                            dataText
                        );

                        if (
                            found.length >= 50
                        ) {
                            break;
                        }
                    }
                }

                MusicFinder.results(
                    found.join("###")
                );

            } catch (e) {

                MusicFinder.results("");
            }

        })();
    """.trimIndent()

    try {

        web.evaluateJavascript(
            script,
            null
        )

    } catch (_: Exception) {

        if (!destroyed) {

            status.text =
                "خطا در استخراج نتایج"
        }
    }
}

private fun extractMusicPage(
    pageUrl: String
) {

    if (
        destroyed ||
        resultGeneration != searchGeneration
    ) {
        return
    }

    val script = """
        (function() {

            try {

                var title = "";
                var artist = "";
                var cover = "";
                var audioLinks = [];

                var metaTitle =
                    document.querySelector(
                        'meta[property="og:title"]'
                    );

                if (metaTitle) {

                    title =
                        metaTitle.content || "";
                }

                var h1 =
                    document.querySelector("h1");

                if (
                    !title &&
                    h1
                ) {

                    title =
                        h1.innerText || "";
                }

                var metaArtist =
                    document.querySelector(
                        'meta[property="music:musician"]'
                    );

                if (metaArtist) {

                    artist =
                        metaArtist.content || "";
                }

                var image =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if (image) {

                    cover =
                        image.content || "";
                }

                /*
                 * منابع احتمالی فایل صوتی.
                 */
                var media =
                    document.querySelectorAll(
                        "audio source, " +
                        "audio, " +
                        "video source, " +
                        "video, " +
                        "a"
                    );

                for (
                    var i = 0;
                    i < media.length;
                    i++
                ) {

                    var el =
                        media[i];

                    var src =
                        el.src ||
                        el.href ||
                        "";

                    var lower =
                        src.toLowerCase();

                    if (
                        lower.indexOf(".mp3") >= 0 ||
                        lower.indexOf(".m4a") >= 0 ||
                        lower.indexOf(".aac") >= 0 ||
                        lower.indexOf(".ogg") >= 0 ||
                        lower.indexOf(".wav") >= 0 ||
                        lower.indexOf(".flac") >= 0 ||
                        lower.indexOf("dl.") >= 0
                    ) {

                        if (
                            audioLinks.indexOf(
                                src
                            ) < 0
                        ) {

                            audioLinks.push(src);
                        }
                    }
                }

                MusicFinder.page(
                    encodeURIComponent(
                        title
                    ) +
                    "###" +
                    encodeURIComponent(
                        artist
                    ) +
                    "###" +
                    encodeURIComponent(
                        cover
                    ) +
                    "###" +
                    encodeURIComponent(
                        audioLinks.join("|||")
                    )
                );

            } catch (e) {

                MusicFinder.page(
                    "######"
                );
            }

        })();
    """.trimIndent()

    try {

        web.evaluateJavascript(
            script,
            null
        )

    } catch (_: Exception) {

        finishCurrentResultPage()
    }
}

private fun processNextResultPage() {

    if (destroyed) return

    if (
        resultGeneration != searchGeneration
    ) {
        return
    }

    if (
        resultPageIndex >= resultPages.size
    ) {

        finishSearch()

        return
    }

    val raw =
        resultPages[resultPageIndex]

    val url =
        raw.substringBefore("|||")
            .trim()

    resultPageIndex++

    if (
        url.isBlank() ||
        !url.startsWith(
            "http",
            ignoreCase = true
        )
    ) {

        processNextResultPage()

        return
    }

    expectedPageUrl =
        url

    pageTimeoutRunnable?.let {
        mainHandler.removeCallbacks(it)
    }

    val generation =
        searchGeneration

    val timeout =
        Runnable {

            if (
                !destroyed &&
                generation == searchGeneration
            ) {

                processNextResultPage()
            }
        }

    pageTimeoutRunnable =
        timeout

    mainHandler.postDelayed(
        timeout,
        3500L
    )

    try {

        web.loadUrl(url)

    } catch (_: Exception) {

        finishCurrentResultPage()
    }
}

private fun finishCurrentResultPage() {

    pageTimeoutRunnable?.let {
        mainHandler.removeCallbacks(it)
    }

    pageTimeoutRunnable = null

    if (destroyed) return

    if (
        resultGeneration != searchGeneration
    ) {
        return
    }

    processNextResultPage()
}
    private fun finishSearch() {
        if (destroyed) return

        cancelSearchCallbacks()

        status.text =
            if (songs.isEmpty()) {
                "آهنگ قابل پخش پیدا نشد"
            } else {
                "${songs.size} نتیجه پیدا شد"
            }

        if (songs.isNotEmpty() && currentIndex == -1) {
            currentIndex = 0
        }

        saveSearchResults()
    }

    private fun saveSearchResults()
    private fun saveSearchResults() {

        if (destroyed) return

        val prefs = getSharedPreferences("search_results", MODE_PRIVATE)
        val oldData = prefs.getString("songs", "") ?: ""
        val merged = ArrayList<SongResult>()
        oldData.split("\n").forEach { line ->
            val parts = line.split("|||", limit = 5)
            if (parts.size == 5 && merged.none { it.url == parts[0] }) {
                merged.add(SongResult(parts[0], parts[1], parts[2], parts[3], parts[4]))
            }
        }
        songs.forEach { song -> if (merged.none { it.url == song.url }) merged.add(song) }

        val limited = merged.take(200)

        val data =
            limited.joinToString("\n") {

                listOf(
                    it.url,
                    it.title,
                    it.artist,
                    it.site,
                    it.cover
                ).joinToString("|||")
            }

        getSharedPreferences(
            "search_results",
            MODE_PRIVATE
        )
            .edit()
            .putString(
                "songs",
                data
            )
            .apply()
    }

    private fun restoreSearchResults() {

        val data =
            getSharedPreferences(
                "search_results",
                MODE_PRIVATE
            )
                .getString(
                    "songs",
                    ""
                )
                ?: ""

        if (data.isBlank()) {
            return
        }

        songs.clear()

        data.split("\n")
            .take(60)
            .forEach {

                val parts =
                    it.split(
                        "|||",
                        limit = 5
                    )

                if (
                    parts.size < 5
                ) {
                    return@forEach
                }

                val url =
                    parts[0].trim()

                if (url.isBlank()) {
                    return@forEach
                }

                songs.add(
                    SongResult(
                        url = url,
                        title = parts[1],
                        artist = parts[2],
                        site = parts[3],
                        cover = parts[4]
                    )
                )
            }

        songs.forEachIndexed {
                index,
                song ->

            addSongView(
                song,
                index
            )
        }

        if (songs.isNotEmpty()) {

            currentIndex = 0

            status.text =
                "${songs.size} نتیجه ذخیره شده"
        }
    }
    override fun onStart() {
        super.onStart()

        if (receiverRegistered) return

        val filter = IntentFilter(MusicService.UPDATE)

        try {
            ContextCompat.registerReceiver(
                this,
                playerReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }


    override fun onStop() {

        if (receiverRegistered) {

            try {

                unregisterReceiver(
                    playerReceiver
                )

            } catch (_: Exception) {
            }

            receiverRegistered = false
        }

        super.onStop()
    }

    override fun onDestroy() {

        destroyed = true

        cancelSearchCallbacks()

        cancelDownloadRequested = true

        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }

        try {
            downloadFuture?.cancel(true)
        } catch (_: Exception) {
        }

        try {

            mainHandler.removeCallbacksAndMessages(
                null
            )

        } catch (_: Exception) {
        }

        try {

            web.stopLoading()

            web.removeJavascriptInterface(
                "MusicFinder"
            )

            web.loadUrl(
                "about:blank"
            )

            web.clearHistory()
            web.removeAllViews()
            web.destroy()

        } catch (_: Exception) {
        }

        try {
            imageExecutor.shutdownNow()
        } catch (_: Exception) {
        }

        try {
            downloadExecutor.shutdownNow()
        } catch (_: Exception) {
        }

        coverCache.evictAll()

        super.onDestroy()
    }
}
