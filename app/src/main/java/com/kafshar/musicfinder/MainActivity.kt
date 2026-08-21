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

                    if (volume in 0..100) {
                        volumeSeekBar.progress = volume
                        volumeText.text = "$volume%"
                    }
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(
        view: WebView
    ) {

        view.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false

            mediaPlaybackRequiresUserGesture = false

            allowFileAccess = false
            allowContentAccess = false

            javaScriptCanOpenWindowsAutomatically =
                false

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

                    return !ServerConfig.isAllowedPageUrl(
                        url
                    )
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

                    if (
                        url.contains(
                            "google.com/search",
                            ignoreCase = true
                        )
                    ) {

                        extractGoogleResults()

                        return
                    }

                    if (
                        resultGeneration ==
                        searchGeneration &&
                        expectedPageUrl.isNotBlank() &&
                        ServerConfig.isAllowedPageUrl(
                            url
                        )
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

    private fun setupButtons() {

        findViewById<TextView>(
            R.id.search
        ).setOnClickListener {

            searchMusic()
        }

        query.setOnEditorActionListener {
                _, actionId, _ ->

            if (
                actionId ==
                EditorInfo.IME_ACTION_SEARCH
            ) {

                searchMusic()

                true

            } else {

                false
            }
        }

        playButton.setOnClickListener {

            val url =
                currentAudioUrl

            if (url.isBlank()) {
                return@setOnClickListener
            }

            sendServiceAction(
                MusicService.ACTION_TOGGLE,
                url,
                titleText.text.toString(),
                artistText.text.toString()
            )
        }

        previousButton.setOnClickListener {
            previousSong()
        }

        nextButton.setOnClickListener {
            nextSong()
        }

        randomButton.setOnClickListener {

            randomMode =
                !randomMode

            randomButton.text =
                if (randomMode) {
                    "🔀"
                } else {
                    "🔁"
                }
        }

        seekBar.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (!fromUser) return

                    val duration =
                        parseTime(
                            durationText.text.toString()
                        )

                    if (duration > 0) {

                        val position =
                            duration *
                                    progress /
                                    100L

                        currentTimeText.text =
                            formatTime(position)
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {

                    val percent =
                        seekBar?.progress ?: 0

                    val intent =
                        Intent(
                            this@MainActivity,
                            MusicService::class.java
                        ).apply {

                            action =
                                MusicService.ACTION_SEEK_PERCENT

                            putExtra(
                                MusicService.EXTRA_PERCENT,
                                percent
                            )
                        }

                    safelyStartService(intent)
                }
            }
        )

        downloadButton.setOnClickListener {
            downloadCurrentSong()
        }

        pauseDownloadButton.setOnClickListener {
            toggleDownloadPause()
        }

        cancelDownloadButton.setOnClickListener {
            cancelDownload()
        }

        saveButton.setOnClickListener {
            saveCurrentSong()
        }

        libraryButton.setOnClickListener {

            try {

                startActivity(
                    Intent(
                        this,
                        LibraryActivity::class.java
                    )
                )

            } catch (_: Exception) {

                Toast.makeText(
                    this,
                    "کتابخانه باز نشد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        historyButton.setOnClickListener {
            toggleHistory()
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

        searchGeneration++

        cancelSearchCallbacks()

        resultPages = emptyList()
        resultPageIndex = 0

        resultGeneration =
            searchGeneration

        expectedPageUrl = ""

        songs.clear()
        currentIndex = -1

        resultsContainer.removeAllViews()

        titleText.text = text

        artistText.text =
            "در حال جستجو..."

        status.text =
            "در حال جستجوی سایت‌ها..."

        seekBar.progress = 0

        currentTimeText.text =
            "00:00"

        durationText.text =
            "00:00"

        vinyl.clearCover()
        vinyl.stopRotation()

        val searchQuery = SearchEngine.buildGoogleQuery(text)
        val encoded = try {
            java.net.URLEncoder.encode(searchQuery, "UTF-8")
        } catch (_: Exception) {
            return
        }

        val url = "https://www.google.com/search?q=$encoded&num=50"























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

        val generation = searchGeneration
        if (generation <= 0) return

        val hosts = ServerConfig.MUSIC_SITES.joinToString(
            prefix = "[",
            postfix = "]"
        ) { "\"$it\"" }

        val script = """
            (function() {
                try {
                    var links = document.querySelectorAll("a");
                    var found = [];
                    var hosts = $hosts;

                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href || "";
                        var text = links[i].innerText || "";
                        var lower = href.toLowerCase();
                        var allowed = false;

                        for (var h = 0; h < hosts.length; h++) {
                            if (lower.indexOf(hosts[h]) >= 0) {
                                allowed = true;
                                break;
                            }
                        }

                        if (allowed && lower.indexOf("google.com") < 0 && found.indexOf(href) < 0) {
                            found.push(href + "|||" + text.replace(/[\r\n]+/g, " "));
                        }
                    }

                    MusicFinder.results(found.join("###"));
                } catch (e) {
                    MusicFinder.results("");
                }
            })();
        """.trimIndent()

        try {
            web.evaluateJavascript(script, null)
        } catch (_: Exception) {
            if (!destroyed) status.text = "خطا در استخراج نتایج"
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

                    if (!title && h1) {
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

                    var media =
                        document.querySelectorAll(
                            "audio source, audio, video source, video, a"
                        );

                    for (
                        var i = 0;
                        i < media.length;
                        i++
                    ) {
                        var el = media[i];

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
                                audioLinks.indexOf(src) < 0
                            ) {
                                audioLinks.push(src);
                            }
                        }
                    }

                    MusicFinder.page(
                        encodeURIComponent(title) +
                        "###" +
                        encodeURIComponent(artist) +
                        "###" +
                        encodeURIComponent(cover) +
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

    inner class Bridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

            runOnUiThread {

                if (destroyed) {
                    return@runOnUiThread
                }

                val items =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .take(50)

                if (items.isEmpty()) {

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                status.text =
                    "در حال بررسی نتایج..."

                resultPages = items
                resultPageIndex = 0

                resultGeneration =
                    searchGeneration

                processNextResultPage()
            }
        }

        @JavascriptInterface
        fun page(
            data: String
        ) {

            runOnUiThread {

                if (destroyed) {
                    return@runOnUiThread
                }

                if (
                    resultGeneration !=
                    searchGeneration
                ) {
                    return@runOnUiThread
                }

                val parts =
                    data.split("###")

                if (parts.size < 4) {

                    finishCurrentResultPage()

                    return@runOnUiThread
                }

                val title =
                    decode(parts[0])

                val artist =
                    decode(parts[1])

                val cover =
                    decode(parts[2])

                val audioString =
                    decode(parts[3])

                val audio =
                    audioString
                        .split("|||")
                        .firstOrNull {
                            it.isNotBlank()
                        }
                        ?: ""

                if (audio.isBlank()) {

                    finishCurrentResultPage()

                    return@runOnUiThread
                }

                val pageUrl =
                    web.url
                        ?: expectedPageUrl

                val song =
                    SongResult(
                        url = audio.trim(),

                        title =
                            if (title.isBlank()) {
                                query.text
                                    .toString()
                                    .trim()
                            } else {
                                cleanTitle(title)
                            },

                        artist =
                            if (artist.isBlank()) {
                                query.text
                                    .toString()
                                    .trim()
                            } else {
                                artist.trim()
                            },

                        site =
                            getSiteName(pageUrl),

                        cover =
                            cover.trim()
                    )

                addSong(song)

                finishCurrentResultPage()
            }
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

        expectedPageUrl = url

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

        if (
            songs.isNotEmpty() &&
            currentIndex == -1
        ) {
            currentIndex = 0
        }

        saveSearchResults()
    }

    private fun addSong(
        song: SongResult
    ) {

        if (
            song.url.isBlank() ||
            songs.size >= 60
        ) {
            return
        }

        if (
            songs.any {
                it.url == song.url
            }
        ) {
            return
        }

        songs.add(song)

        addSongView(
            song,
            songs.lastIndex
        )
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

        if (destroyed) return

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    12,
                    12,
                    12,
                    12
                )

                setBackgroundColor(
                    0xFF15151D.toInt()
                )
            }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            0,
            0,
            8
        )

        resultsContainer.addView(
            row,
            params
        )

        val cover =
            ImageView(this).apply {

                scaleType =
                    ImageView.ScaleType.CENTER_CROP

                setBackgroundColor(
                    0xFF22222A.toInt()
                )
            }

        row.addView(
            cover,
            LinearLayout.LayoutParams(
                62,
                62
            )
        )

        loadCover(
            song.cover,
            cover
        )

        val textLayout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    14,
                    0,
                    8,
                    0
                )
            }

        val textParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        val title =
            TextView(this).apply {

                text =
                    "${index + 1}. ${song.title}"

                textSize = 15f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                maxLines = 2
            }

        val info =
            TextView(this).apply {

                text =
                    "${song.artist} • ${song.site}"

                textSize = 12f

                setTextColor(
                    0xFFAAAAAA.toInt()
                )

                maxLines = 2
            }

        textLayout.addView(title)
        textLayout.addView(info)

        row.addView(
            textLayout,
            textParams
        )

        val save =
            TextView(this).apply {

                text =
                    if (
                        LibraryManager.contains(
                            this@MainActivity,
                            song
                        )
                    ) {
                        "♥"
                    } else {
                        "♡"
                    }

                textSize = 25f

                setTextColor(
                    turquoiseColor
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    10,
                    8,
                    8,
                    8
                )

                setOnClickListener {

                    toggleLibrarySong(
                        song,
                        this
                    )
                }
            }

        row.addView(
            save,
            LinearLayout.LayoutParams(
                52,
                62
            )
        )

        row.setOnClickListener {

            val position =
                songs.indexOfFirst {
                    it.url == song.url
                }

            if (position >= 0) {

                currentIndex = position

                playSong(song)
            }
        }
    }

    private fun toggleLibrarySong(
        song: SongResult,
        button: TextView
    ) {

        try {

            if (
                LibraryManager.contains(
                    this,
                    song
                )
            ) {

                LibraryManager.remove(
                    this,
                    song
                )

                button.text = "♡"

                button.setTextColor(
                    turquoiseColor
                )

                Toast.makeText(
                    this,
                    "از کتابخانه حذف شد",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                LibraryManager.add(
                    this,
                    song
                )

                button.text = "♥"

                button.setTextColor(
                    turquoiseColor
                )

                Toast.makeText(
                    this,
                    "به کتابخانه اضافه شد",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "ذخیره‌سازی انجام نشد",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadCover(
        url: String,
        target: ImageView
    ) {

        if (url.isBlank()) return

        val cached =
            coverCache.get(url)

        if (cached != null) {

            target.setImageBitmap(cached)

            return
        }

        imageExecutor.execute {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                            as? HttpURLConnection
                        ?: return@execute

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                connection.connect()

                val bitmap =
                    connection.inputStream.use {
                        decodeBitmap(it)
                    }

                connection.disconnect()

                if (
                    bitmap != null &&
                    !destroyed
                ) {

                    coverCache.put(
                        url,
                        bitmap
                    )

                    runOnUiThread {

                        if (!destroyed) {

                            target.setImageBitmap(
                                bitmap
                            )
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun decodeBitmap(
        input: java.io.InputStream
    ): Bitmap? {

        val bytes =
            input.readBytes()

        if (bytes.isEmpty()) return null

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            bounds
        )

        val maxSize = 512

        var sample = 1

        while (
            bounds.outWidth / sample > maxSize ||
            bounds.outHeight / sample > maxSize
        ) {

            sample *= 2
        }

        val options =
            BitmapFactory.Options().apply {

                inSampleSize = sample

                inPreferredConfig =
                    Bitmap.Config.RGB_565
            }

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            options
        )
    }

    private fun saveCurrentSong() {

        if (
            currentIndex < 0 ||
            currentIndex >= songs.size
        ) {

            Toast.makeText(
                this,
                "ابتدا یک آهنگ انتخاب کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            LibraryManager.add(
                this,
                songs[currentIndex]
            )

            Toast.makeText(
                this,
                "در کتابخانه ذخیره شد ♥",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "ذخیره انجام نشد",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun playSong(
        song: SongResult
    ) {

        if (
            destroyed ||
            song.url.isBlank()
        ) {
            return
        }

        currentSong = song
        currentAudioUrl = song.url

        titleText.text =
            song.title

        artistText.text =
            "${song.artist} • ${song.site}"

        currentTimeText.text =
            "00:00"

        durationText.text =
            "00:00"

        seekBar.progress = 0

        status.text =
            "در حال پخش..."

        if (song.cover.isNotBlank()) {
            vinyl.setCover(song.cover)
        } else {
            vinyl.clearCover()
        }

        sendServiceAction(
            MusicService.ACTION_PLAY,
            song.url,
            song.title,
            song.artist,
            song.cover
        )
    }

    private fun nextSong() {

        if (songs.isEmpty()) return

        currentIndex =
            if (randomMode) {

                if (songs.size == 1) {

                    0

                } else {

                    var next: Int

                    do {

                        next =
                            (0 until songs.size).random()

                    } while (
                        next == currentIndex
                    )

                    next
                }

            } else {

                if (currentIndex < 0) {

                    0

                } else {

                    (
                        currentIndex + 1
                    ) % songs.size
                }
            }

        playSong(
            songs[currentIndex]
        )
    }

    private fun previousSong() {

        if (songs.isEmpty()) return

        currentIndex =
            if (currentIndex <= 0) {
                songs.lastIndex
            } else {
                currentIndex - 1
            }

        playSong(
            songs[currentIndex]
        )
    }

    private fun sendServiceAction(
        action: String,
        url: String,
        title: String,
        artist: String = "Music Finder",
        cover: String = ""
    ) {

        if (destroyed) return

        if (
            url.isBlank() &&
            action != MusicService.ACTION_GET_POSITION
        ) {
            return
        }

        val intent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                this.action = action

                if (url.isNotBlank()) {

                    putExtra(
                        MusicService.EXTRA_URL,
                        url
                    )
                }

                putExtra(
                    MusicService.EXTRA_TITLE,
                    title
                )

                putExtra(
                    MusicService.EXTRA_ARTIST,
                    artist
                )

                putExtra(
                    MusicService.EXTRA_COVER,
                    cover
                )
            }

        safelyStartService(intent)
    }

    private fun safelyStartService(
        intent: Intent
    ) {

        if (destroyed) return

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(intent)

            } else {

                startService(intent)
            }

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "سرویس پخش در دسترس نیست",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updatePlayerProgress(
        playing: Boolean,
        position: Long,
        duration: Long
    ) {

        if (destroyed) return

        if (duration > 0) {

            val percent =
                (
                    position.toDouble() /
                            duration.toDouble() *
                            100.0
                    )
                    .toInt()
                    .coerceIn(0, 100)

            seekBar.progress =
                percent

            currentTimeText.text =
                formatTime(position)

            durationText.text =
                formatTime(duration)
        }

        playButton.text =
            if (playing) {
                "⏸"
            } else {
                "▶"
            }

        if (playing) {

            playButton.backgroundTintList =
                ColorStateList.valueOf(
                    turquoiseDarkColor
                )

            vinyl.startRotation()

            status.text =
                "در حال پخش"

        } else {

            playButton.backgroundTintList =
                ColorStateList.valueOf(
                    turquoiseColor
                )

            vinyl.stopRotation()
        }
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

        if (milliseconds <= 0) {
            return "00:00"
        }

        val seconds =
            milliseconds / 1000

        val minutes =
            seconds / 60

        val remainder =
            seconds % 60

        return String.format(
            "%02d:%02d",
            minutes,
            remainder
        )
    }

    private fun parseTime(
        value: String
    ): Long {

        return try {

            val parts =
                value.split(":")

            if (parts.size != 2) {

                0L

            } else {

                val minutes =
                    parts[0].toLong()

                val seconds =
                    parts[1].toLong()

                (
                    minutes * 60 +
                            seconds
                    ) * 1000L
            }

        } catch (_: Exception) {

            0L
        }
    }

    private fun downloadCurrentSong() {

        val url =
            currentAudioUrl

        if (url.isBlank()) {

            Toast.makeText(
                this,
                "اول یک آهنگ پخش کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            downloadFuture?.isDone == false
        ) {

            Toast.makeText(
                this,
                "یک دانلود در حال انجام است",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        cancelDownloadRequested = false
        pauseDownloadRequested = false

        downloadProgress.progress = 0

        downloadProgress.visibility =
            View.VISIBLE

        downloadText.visibility =
            View.VISIBLE

        pauseDownloadButton.visibility =
            View.VISIBLE

        cancelDownloadButton.visibility =
            View.VISIBLE

        downloadButton.isEnabled =
            false

        pauseDownloadButton.text =
            "⏸"

        downloadText.text =
            "0%"

        status.text =
            "در حال دانلود..."

        val fileName =
            makeSafeFileName(
                titleText.text.toString()
            ) + ".mp3"

        downloadFuture =
            downloadExecutor.submit {

                try {

                    if (Build.VERSION.SDK_INT >= 29) {

                        downloadMediaStore(
                            url,
                            fileName
                        )

                    } else {

                        downloadOld(
                            url,
                            fileName
                        )
                    }

                    runOnUiThread {

                        if (destroyed) {
                            return@runOnUiThread
                        }

                        downloadProgress.progress =
                            100

                        downloadText.text =
                            "100%"

                        status.text =
                            "دانلود کامل شد ✓"

                        resetDownloadButtons()
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        if (destroyed) {
                            return@runOnUiThread
                        }

                        status.text =
                            when {

                                e.message ==
                                        "CANCELLED" ->
                                    "دانلود لغو شد"

                                e.message ==
                                        "INVALID_RESPONSE" ->
                                    "سرور فایل صوتی معتبری نداد"

                                else ->
                                    "دانلود ناموفق بود"
                            }

                        resetDownloadButtons()
                    }
                }
            }
    }

    private fun toggleDownloadPause() {

        if (
            downloadFuture?.isDone != false
        ) {
            return
        }

        pauseDownloadRequested =
            !pauseDownloadRequested

        pauseDownloadButton.text =
            if (pauseDownloadRequested) {
                "▶"
            } else {
                "⏸"
            }

        downloadText.text =
            if (pauseDownloadRequested) {
                "دانلود متوقف شد"
            } else {
                "در حال دانلود..."
            }
    }

    private fun cancelDownload() {

        cancelDownloadRequested = true
        pauseDownloadRequested = false

        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }

        downloadText.text =
            "در حال لغو..."

        downloadFuture?.cancel(true)
    }

    private fun resetDownloadButtons() {

        downloadButton.isEnabled =
            true

        pauseDownloadButton.visibility =
            View.GONE

        cancelDownloadButton.visibility =
            View.GONE
    }

    private fun openConnection(
        urlString: String
    ): HttpURLConnection {

        val url =
            URL(urlString)

        val connection =
            url.openConnection()
                as? HttpURLConnection
                ?: throw Exception(
                    "INVALID_CONNECTION"
                )

        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true

        activeConnection =
            connection

        connection.connect()

        if (
            connection.responseCode !in 200..299
        ) {

            throw Exception(
                "HTTP_${connection.responseCode}"
            )
        }

        return connection
    }

    private fun checkCancelled() {

        if (
            cancelDownloadRequested ||
            Thread.currentThread().isInterrupted
        ) {

            throw Exception(
                "CANCELLED"
            )
        }
    }

    private fun waitIfPaused() {

        while (
            pauseDownloadRequested &&
            !cancelDownloadRequested
        ) {

            try {

                Thread.sleep(150)

            } catch (_: InterruptedException) {

                throw Exception(
                    "CANCELLED"
                )
            }
        }

        checkCancelled()
    }

    private fun updateDownloadProgress(
        downloaded: Long,
        total: Long
    ) {

        if (
            total <= 0 ||
            destroyed
        ) {
            return
        }

        val percent =
            (
                downloaded.toDouble() /
                        total.toDouble() *
                        100.0
                )
                .toInt()
                .coerceIn(0, 100)

        val now =
            System.currentTimeMillis()

        if (
            percent == lastProgressValue ||
            now - lastProgressUpdate < 250L
        ) {
            return
        }

        lastProgressUpdate = now
        lastProgressValue = percent

        runOnUiThread {

            if (destroyed) {
                return@runOnUiThread
            }

            downloadProgress.progress =
                percent

            if (!pauseDownloadRequested) {

                downloadText.text =
                    "$percent%"
            }
        }
    }

    private fun downloadMediaStore(
        urlString: String,
        fileName: String
    ) {

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.Audio.Media.MIME_TYPE,
                    "audio/mpeg"
                )

                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC
                )

                put(
                    MediaStore.Audio.Media.IS_PENDING,
                    1
                )
            }

        val uri =
            contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw Exception(
                "CREATE_FAILED"
            )

        var connection:
                HttpURLConnection? = null

        try {

            connection =
                openConnection(urlString)

            val total =
                connection.contentLengthLong

            var downloaded = 0L

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->

                        val buffer =
                            ByteArray(16 * 1024)

                        while (true) {

                            checkCancelled()
                            waitIfPaused()

                            val count =
                                input.read(buffer)

                            if (count == -1) {
                                break
                            }

                            output.write(
                                buffer,
                                0,
                                count
                            )

                            downloaded += count

                            updateDownloadProgress(
                                downloaded,
                                total
                            )
                        }

                        output.flush()
                    }
                    ?: throw Exception(
                        "OUTPUT_FAILED"
                    )
            }

            checkCancelled()

            contentResolver.update(
                uri,
                ContentValues().apply {

                    put(
                        MediaStore.Audio.Media.IS_PENDING,
                        0
                    )
                },
                null,
                null
            )

        } catch (e: Exception) {

            try {

                contentResolver.delete(
                    uri,
                    null,
                    null
                )

            } catch (_: Exception) {
            }

            throw e

        } finally {

            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }

            activeConnection = null
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadOld(
        urlString: String,
        fileName: String
    ) {

        val directory =
            Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC
                )

        if (
            !directory.exists() &&
            !directory.mkdirs()
        ) {

            throw Exception(
                "DIRECTORY_FAILED"
            )
        }

        val file =
            File(
                directory,
                fileName
            )

        var connection:
                HttpURLConnection? = null

        try {

            connection =
                openConnection(urlString)

            val total =
                connection.contentLengthLong

            var downloaded = 0L

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                FileOutputStream(file)
                    .use { output ->

                        val buffer =
                            ByteArray(16 * 1024)

                        while (true) {

                            checkCancelled()
                            waitIfPaused()

                            val count =
                                input.read(buffer)

                            if (count == -1) {
                                break
                            }

                            output.write(
                                buffer,
                                0,
                                count
                            )

                            downloaded += count

                            updateDownloadProgress(
                                downloaded,
                                total
                            )
                        }

                        output.flush()
                    }
            }

            checkCancelled()

        } catch (e: Exception) {

            try {
                file.delete()
            } catch (_: Exception) {
            }

            throw e

        } finally {

            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }

            activeConnection = null
        }
    }

    private fun toggleHistory() {

        if (
            historyContainer.visibility ==
            View.VISIBLE
        ) {

            historyContainer.visibility =
                View.GONE

            return
        }

        historyContainer.visibility =
            View.VISIBLE

        loadHistory()
    }

    private fun loadHistory() {

        historyContainer.removeAllViews()

        val prefs =
            getSharedPreferences(
                "music_history",
                MODE_PRIVATE
            )

        val raw =
            prefs.getString(
                "items",
                ""
            ) ?: ""

        if (raw.isBlank()) {

            val empty =
                TextView(this).apply {

                    text =
                        "تاریخچه خالی است"

                    textSize = 15f

                    setTextColor(
                        0xFFAAAAAA.toInt()
                    )

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        20,
                        30,
                        20,
                        30
                    )
                }

            historyContainer.addView(
                empty
            )

            return
        }

        raw.split("\n")
            .take(50)
            .forEachIndexed {
                    index,
                    line ->

                val parts =
                    line.split(
                        "|||",
                        limit = 4
                    )

                if (
                    parts.size < 4
                ) {
                    return@forEachIndexed
                }

                val song =
                    SongResult(
                        url = parts[0],
                        title = parts[1],
                        artist = parts[2],
                        site = "History",
                        cover = parts[3]
                    )

                val item =
                    TextView(this).apply {

                        text =
                            "${index + 1}. ${song.title}\n${song.artist}"

                        textSize = 14f

                        setTextColor(
                            0xFFFFFFFF.toInt()
                        )

                        setPadding(
                            14,
                            12,
                            14,
                            12
                        )

                        setOnClickListener {
                            playSong(song)
                        }
                    }

                historyContainer.addView(
                    item
                )
            }
    }

    private fun cleanTitle(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    "دانلود|آهنگ|موزیک|\\|.*"
                ),
                ""
            )
            .trim()
            .ifBlank {
                "Music Finder"
            }
    }

    private fun decode(
        value: String
    ): String {

        return try {

            URLDecoder.decode(
                value,
                "UTF-8"
            )

        } catch (_: Exception) {

            value
        }
    }

    private fun getSiteName(
        url: String
    ): String {

        val lower =
            url.lowercase()

        return when {

            lower.contains(
                "rozmusic.com"
            ) ->
                "RozMusic"

            lower.contains(
                "mybia2music.com"
            ) ->
                "Bia2Music"

            lower.contains(
                "musicdel.ir"
            ) ->
                "Musicdel"

            lower.contains(
                "musics-fa.com"
            ) ->
                "Musics-FA"

            else ->
                "سایت موسیقی"
        }
    }

    private fun makeSafeFileName(
        text: String
    ): String {

        var name =
            text.trim()

        if (name.isEmpty()) {
            name = "Music_Finder"
        }

        name =
            name.replace(
                Regex(
                    "[\\\\/:*?\"<>|]"
                ),
                "_"
            )

        return name
            .take(100)
            .ifBlank {
                "Music_Finder"
            }
    }

    private fun saveSearchResults() {

        if (destroyed) return

        val limited =
            songs.take(60)

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

        if (receiverRegistered) {
            return
        }

        val filter =
            IntentFilter(
                MusicService.UPDATE
            )

        try {

            if (Build.VERSION.SDK_INT >= 33) {

                registerReceiver(
                    playerReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )

            } else {

                @Suppress("DEPRECATION")
                registerReceiver(
                    playerReceiver,
                    filter
                )
            }

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
