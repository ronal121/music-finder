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
import android.graphics.BitmapFactory
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private lateinit var currentTimeText: TextView
    private lateinit var durationText: TextView
    private lateinit var downloadButton: TextView
    private lateinit var cancelDownloadButton: TextView
    private lateinit var pauseDownloadButton: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadText: TextView
    private lateinit var saveButton: TextView
    private lateinit var libraryButton: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var vinyl: VinylView

    private val songs = ArrayList<SongResult>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val executor: ExecutorService =
        Executors.newFixedThreadPool(2)

    private var currentIndex = -1
    private var currentAudioUrl = ""
    private var randomMode = false

    @Volatile
    private var destroyed = false

    private var receiverRegistered = false

    private var searchGeneration = 0
    private var searchRunning = false
    private var searchItems: List<String> = emptyList()
    private var searchIndex = 0
    private var currentSearchPageUrl = ""

    private var downloadThread: Thread? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var pauseDownloadRequested = false

    private var lastDownloadPercent = -1

    private val searchTimeoutRunnable = Runnable {
        if (!destroyed && searchRunning) {
            processNextSearchPage(searchGeneration)
        }
    }

    private val playerReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    destroyed ||
                    intent?.action != MusicService.UPDATE
                ) {
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

                val newTitle =
                    intent.getStringExtra(
                        "title"
                    ).orEmpty()

                val newArtist =
                    intent.getStringExtra(
                        "artist"
                    ).orEmpty()

                val error =
                    intent.getStringExtra(
                        MusicService.EXTRA_ERROR
                    )

                if (isFinishing) {
                    return
                }

                updatePlayerUi(
                    playing,
                    position,
                    duration,
                    newTitle,
                    newArtist,
                    error
                )
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

        initializeViews()
        setupWebView()
        setupButtons()
        restoreSearchResults()
        registerPlayerReceiver()

        requestNotificationPermission()

        status.text =
            "نام آهنگ یا خواننده را جستجو کنید"
    }

    private fun initializeViews() {

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

        resultsContainer =
            findViewById(R.id.resultsContainer)

        vinyl =
            findViewById(R.id.vinyl)

        web =
            findViewById(R.id.web)
    }

    private fun requestNotificationPermission() {

        if (
            destroyed ||
            Build.VERSION.SDK_INT < 33
        ) {
            return
        }

        try {

            if (
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

        } catch (_: Exception) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        if (destroyed) {
            return
        }

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false

            mediaPlaybackRequiresUserGesture =
                false

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) " +
                        "AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) " +
                        "Chrome/128 Mobile Safari/537.36"

            loadsImagesAutomatically = true
            allowFileAccess = false
            allowContentAccess = true
        }

        web.addJavascriptInterface(
            Bridge(),
            "MusicFinder"
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    if (
                        destroyed ||
                        view !== web
                    ) {
                        return true
                    }

                    return false
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    if (
                        destroyed ||
                        view !== web ||
                        url.isBlank()
                    ) {
                        return
                    }

                    if (
                        !searchRunning
                    ) {
                        return
                    }

                    if (
                        url.contains(
                            "google.com/search"
                        )
                    ) {

                        extractGoogleResults()

                    } else if (
                        url == currentSearchPageUrl
                    ) {

                        extractMusicPage(
                            url
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {

                    if (
                        destroyed ||
                        view !== web ||
                        !request.isForMainFrame
                    ) {
                        return
                    }

                    if (
                        searchRunning &&
                        request.url.toString() ==
                        currentSearchPageUrl
                    ) {

                        processNextSearchPage(
                            searchGeneration
                        )
                    }
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

            if (
                currentAudioUrl.isBlank()
            ) {
                return@setOnClickListener
            }

            sendServiceAction(
                MusicService.ACTION_TOGGLE,
                currentAudioUrl,
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

                    if (
                        !fromUser ||
                        destroyed
                    ) {
                        return
                    }

                    val duration =
                        parseTime(
                            durationText.text.toString()
                        )

                    if (
                        duration > 0L
                    ) {

                        val position =
                            duration *
                                    progress /
                                    100

                        currentTimeText.text =
                            formatTime(
                                position
                            )
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {

                    if (destroyed) {
                        return
                    }

                    val percent =
                        seekBar?.progress ?: 0

                    sendSeekCommand(
                        percent
                    )
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
            }
        }
    }

    private fun registerPlayerReceiver() {

        if (
            destroyed ||
            receiverRegistered
        ) {
            return
        }

        try {

            val filter =
                IntentFilter(
                    MusicService.UPDATE
                )

            if (
                Build.VERSION.SDK_INT >= 33
            ) {

                registerReceiver(
                    playerReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )

            } else {

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

    private fun unregisterPlayerReceiver() {

        if (!receiverRegistered) {
            return
        }

        try {
            unregisterReceiver(
                playerReceiver
            )
        } catch (_: Exception) {
        }

        receiverRegistered = false
    }

    private fun searchMusic() {

        if (
            destroyed ||
            !::web.isInitialized
        ) {
            return
        }

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

        val generation =
            searchGeneration

        cancelCurrentSearch()

        searchRunning = true
        searchItems = emptyList()
        searchIndex = 0
        currentSearchPageUrl = ""

        songs.clear()
        currentIndex = -1

        resultsContainer.removeAllViews()

        titleText.text =
            text

        artistText.text =
            "در حال جستجو..."

        status.text =
            "در حال جستجوی سایت‌ها..."

        seekBar.progress = 0
        currentTimeText.text = "00:00"
        durationText.text = "00:00"

        vinyl.clearCover()
        vinyl.stopRotation()

        val searchQuery =
            "\"$text\" " +
                    "(site:rozmusic.com OR " +
                    "site:mybia2music.com OR " +
                    "site:musicdel.ir OR " +
                    "site:musics-fa.com)"

        val encoded =
            try {
                URLEncoder.encode(
                    searchQuery,
                    "UTF-8"
                )
            } catch (_: Exception) {
                return
            }

        try {

            web.stopLoading()

            currentSearchPageUrl =
                "https://www.google.com/search?q=$encoded&num=50"

            web.loadUrl(
                currentSearchPageUrl
            )

        } catch (_: Exception) {

            searchRunning = false

            status.text =
                "خطا در شروع جستجو"
        }

        mainHandler.postDelayed(
            {
                if (
                    !destroyed &&
                    generation == searchGeneration &&
                    searchRunning
                ) {
                    extractGoogleResults()
                }
            },
            7000L
        )
    }

    private fun cancelCurrentSearch() {

        mainHandler.removeCallbacks(
            searchTimeoutRunnable
        )

        try {
            web.stopLoading()
        } catch (_: Exception) {
        }
    }

    private fun extractGoogleResults() {

        if (
            destroyed ||
            !searchRunning
        ) {
            return
        }

        val script = """
            (function() {
                try {
                    var links = document.querySelectorAll("a");
                    var found = [];
                    var seen = {};

                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href || "";
                        var text = links[i].innerText || "";
                        var lower = href.toLowerCase();

                        var allowed =
                            lower.indexOf("rozmusic.com") >= 0 ||
                            lower.indexOf("mybia2music.com") >= 0 ||
                            lower.indexOf("musicdel.ir") >= 0 ||
                            lower.indexOf("musics-fa.com") >= 0;

                        if (!allowed) continue;
                        if (lower.indexOf("google.com") >= 0) continue;
                        if (seen[href]) continue;

                        seen[href] = true;

                        found.push(
                            href + "|||" +
                            text.replace(/[\r\n]+/g, " ")
                        );

                        if (found.length >= 50) break;
                    }

                    MusicFinder.results(
                        found.join("###")
                    );

                } catch (e) {
                    MusicFinder.results("");
                }
            })();
        """.trimIndent()

        evaluateJavascriptSafely(
            script
        )
    }

    private fun extractMusicPage(
        pageUrl: String
    ) {

        if (
            destroyed ||
            !searchRunning ||
            pageUrl != currentSearchPageUrl
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

                        if (!src) continue;

                        var lower =
                            src.toLowerCase();

                        var valid =
                            lower.indexOf(".mp3") >= 0 ||
                            lower.indexOf(".m4a") >= 0 ||
                            lower.indexOf(".aac") >= 0 ||
                            lower.indexOf(".ogg") >= 0 ||
                            lower.indexOf(".wav") >= 0 ||
                            lower.indexOf(".flac") >= 0 ||
                            lower.indexOf("dl.") >= 0;

                        if (
                            valid &&
                            audioLinks.indexOf(src) < 0
                        ) {
                            audioLinks.push(src);
                        }

                        if (
                            audioLinks.length >= 5
                        ) {
                            break;
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
                        "###" +
                        "###" +
                        "###"
                    );
                }
            })();
        """.trimIndent()

        evaluateJavascriptSafely(
            script
        )
    }

    private fun evaluateJavascriptSafely(
        script: String
    ) {

        if (
            destroyed ||
            !::web.isInitialized
        ) {
            return
        }

        try {

            web.post {

                if (
                    destroyed ||
                    isFinishing
                ) {
                    return@post
                }

                try {
                    web.evaluateJavascript(
                        script,
                        null
                    )
                } catch (_: Exception) {
                }
            }

        } catch (_: Exception) {
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

    inner class Bridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

            if (destroyed) {
                return
            }

            runOnUiThread {

                if (
                    destroyed ||
                    !searchRunning
                ) {
                    return@runOnUiThread
                }

                val generation =
                    searchGeneration

                val items =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .distinctBy {
                            it.substringBefore(
                                "|||"
                            )
                        }
                        .take(50)

                if (
                    items.isEmpty()
                ) {

                    searchRunning = false

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                searchItems =
                    items

                searchIndex = 0

                status.text =
                    "در حال بررسی نتایج..."

                processNextSearchPage(
                    generation
                )
            }
        }

        @JavascriptInterface
        fun page(
            data: String
        ) {

            if (destroyed) {
                return
            }

            runOnUiThread {

                if (
                    destroyed ||
                    !searchRunning
                ) {
                    return@runOnUiThread
                }

                val parts =
                    data.split("###")

                if (
                    parts.size < 4
                ) {
                    processNextSearchPage(
                        searchGeneration
                    )
                    return@runOnUiThread
                }

                val title =
                    decode(
                        parts[0]
                    )

                val artist =
                    decode(
                        parts[1]
                    )

                val cover =
                    decode(
                        parts[2]
                    )

                val audioString =
                    decode(
                        parts[3]
                    )

                val audio =
                    audioString
                        .split("|||")
                        .firstOrNull {
                            isValidHttpUrl(
                                it
                            )
                        }
                        .orEmpty()

                if (
                    audio.isNotBlank()
                ) {

                    val song =
                        SongResult(
                            url = audio,
                            title =
                                if (
                                    title.isBlank()
                                ) {
                                    query.text
                                        .toString()
                                } else {
                                    cleanTitle(
                                        title
                                    )
                                },
                            artist =
                                if (
                                    artist.isBlank()
                                ) {
                                    query.text
                                        .toString()
                                } else {
                                    artist
                                },
                            site =
                                getSiteName(
                                    currentSearchPageUrl
                                ),
                            cover =
                                cover
                        )

                    addSong(
                        song
                    )
                }

                processNextSearchPage(
                    searchGeneration
                )
            }
        }
    }

    private fun processNextSearchPage(
        generation: Int
    ) {

        if (
            destroyed ||
            generation != searchGeneration ||
            !searchRunning
        ) {
            return
        }

        mainHandler.removeCallbacks(
            searchTimeoutRunnable
        )

        if (
            searchIndex >= searchItems.size
        ) {

            finishSearch(
                generation
            )

            return
        }

        val raw =
            searchItems[
                searchIndex
            ]

        val url =
            raw.substringBefore(
                "|||"
            ).trim()

        searchIndex++

        if (
            !isValidHttpUrl(url)
        ) {

            processNextSearchPage(
                generation
            )

            return
        }

        currentSearchPageUrl =
            url

        status.text =
            "در حال بررسی ${searchIndex}/${searchItems.size}"

        try {

            web.stopLoading()

            web.loadUrl(
                url
            )

        } catch (_: Exception) {

            processNextSearchPage(
                generation
            )

            return
        }

        mainHandler.postDelayed(
            searchTimeoutRunnable,
            3500L
        )
    }

    private fun finishSearch(
        generation: Int
    ) {

        if (
            destroyed ||
            generation != searchGeneration
        ) {
            return
        }

        searchRunning = false
        currentSearchPageUrl = ""

        mainHandler.removeCallbacks(
            searchTimeoutRunnable
        )

        status.text =
            if (
                songs.isEmpty()
            ) {
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
            destroyed ||
            song.url.isBlank()
        ) {
            return
        }

        if (
            !isValidHttpUrl(
                song.url
            )
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

        if (
            songs.size >= 50
        ) {
            return
        }

        songs.add(
            song
        )

        addSongView(
            song,
            songs.lastIndex
        )

        if (
            currentIndex == -1
        ) {
            currentIndex = 0
        }
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

        if (
            destroyed
        ) {
            return
        }

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

        val rowParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        rowParams.setMargins(
            0,
            0,
            0,
            8
        )

        row.layoutParams =
            rowParams

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

        loadCoverAsync(
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
                        safeLibraryContains(
                            song
                        )
                    ) {
                        "♥"
                    } else {
                        "♡"
                    }

                textSize = 25f

                setTextColor(
                    0xFFFFFFFF.toInt()
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

                    if (destroyed) {
                        return@setOnClickListener
                    }

                    try {

                        if (
                            LibraryManager.contains(
                                this@MainActivity,
                                song
                            )
                        ) {

                            LibraryManager.remove(
                                this@MainActivity,
                                song
                            )

                            text = "♡"

                            Toast.makeText(
                                this@MainActivity,
                                "از کتابخانه حذف شد",
                                Toast.LENGTH_SHORT
                            ).show()

                        } else {

                            LibraryManager.add(
                                this@MainActivity,
                                song
                            )

                            text = "♥"

                            Toast.makeText(
                                this@MainActivity,
                                "به کتابخانه اضافه شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } catch (_: Exception) {
                    }
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

            if (destroyed) {
                return@setOnClickListener
            }

            val position =
                songs.indexOfFirst {
                    it.url == song.url
                }

            if (
                position >= 0 &&
                position < songs.size
            ) {

                currentIndex =
                    position

                playSong(
                    song
                )
            }
        }

        resultsContainer.addView(
            row
        )
    }

    private fun safeLibraryContains(
        song: SongResult
    ): Boolean {

        return try {

            LibraryManager.contains(
                this,
                song
            )

        } catch (_: Exception) {

            false
        }
    }

    private fun loadCoverAsync(
        coverUrl: String,
        imageView: ImageView
    ) {

        if (
            destroyed ||
            coverUrl.isBlank() ||
            !isValidHttpUrl(coverUrl)
        ) {
            return
        }

        executor.execute {

            var connection:
                    HttpURLConnection? = null

            try {

                connection =
                    URL(
                        coverUrl
                    ).openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    4000

                connection.readTimeout =
                    5000

                connection.instanceFollowRedirects =
                    true

                connection.connect()

                if (
                    connection.responseCode !in
                    200..299
                ) {
                    return@execute
                }

                val bitmap =
                    connection.inputStream
                        .use {
                            BitmapFactory.decodeStream(
                                it
                            )
                        }

                if (
                    bitmap == null ||
                    destroyed
                ) {
                    return@execute
                }

                runOnUiThread {

                    if (
                        !destroyed &&
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        try {
                            imageView.setImageBitmap(
                                bitmap
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

            } catch (_: Exception) {

            } finally {

                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun saveCurrentSong() {

        if (
            destroyed ||
            currentIndex !in songs.indices
        ) {
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
        }
    }

    private fun playSong(
        song: SongResult
    ) {

        if (
            destroyed ||
            !isValidHttpUrl(song.url)
        ) {
            return
        }

        currentAudioUrl =
            song.url

        titleText.text =
            song.title

        artistText.text =
            "${song.artist} • ${song.site}"

        currentTimeText.text =
            "00:00"

        durationText.text =
            "00:00"

        seekBar.progress =
            0

        status.text =
            "در حال پخش..."

        try {

            if (
                song.cover.isNotBlank()
            ) {

                vinyl.setCover(
                    song.cover
                )

            } else {

                vinyl.clearCover()
            }

        } catch (_: Exception) {
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

        if (
            destroyed ||
            songs.isEmpty()
        ) {
            return
        }

        try {

            currentIndex =
                if (randomMode) {

                    if (
                        songs.size == 1
                    ) {

                        0

                    } else {

                        var next: Int

                        do {

                            next =
                                (0 until songs.size)
                                    .random()

                        } while (
                            next == currentIndex
                        )

                        next
                    }

                } else {

                    val base =
                        currentIndex
                            .coerceIn(
                                -1,
                                songs.lastIndex
                            )

                    (
                        base + 1
                    ) % songs.size
                }

            if (
                currentIndex in songs.indices
            ) {

                playSong(
                    songs[currentIndex]
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun previousSong() {

        if (
            destroyed ||
            songs.isEmpty()
        ) {
            return
        }

        try {

            currentIndex =
                if (
                    currentIndex <= 0 ||
                    currentIndex > songs.lastIndex
                ) {
                    songs.lastIndex
                } else {
                    currentIndex - 1
                }

            playSong(
                songs[currentIndex]
            )

        } catch (_: Exception) {
        }
    }

    private fun sendSeekCommand(
        percent: Int
    ) {

        if (destroyed) {
            return
        }

        val intent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                action =
                    MusicService.ACTION_SEEK_PERCENT

                putExtra(
                    MusicService.EXTRA_PERCENT,
                    percent.coerceIn(
                        0,
                        100
                    )
                )
            }

        startMusicServiceSafely(
            intent
        )
    }

    private fun sendServiceAction(
        action: String,
        url: String,
        title: String,
        artist: String = "Music Finder",
        cover: String = ""
    ) {

        if (
            destroyed ||
            url.isBlank()
        ) {
            return
        }

        if (
            !isValidHttpUrl(url)
        ) {
            status.text =
                "آدرس آهنگ معتبر نیست"
            return
        }

        val intent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                this.action =
                    action

                putExtra(
                    MusicService.EXTRA_URL,
                    url
                )

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

        startMusicServiceSafely(
            intent
        )
    }

    private fun startMusicServiceSafely(
        intent: Intent
    ) {

        if (destroyed) {
            return
        }

        try {

            if (
                Build.VERSION.SDK_INT >= 26
            ) {

                startForegroundService(
                    intent
                )

            } else {

                startService(
                    intent
                )
            }

        } catch (e: Exception) {

            if (!destroyed) {

                status.text =
                    "خطا در اجرای پخش"

                Toast.makeText(
                    this,
                    "اجرای سرویس پخش ناموفق بود",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updatePlayerUi(
        playing: Boolean,
        position: Long,
        duration: Long,
        newTitle: String,
        newArtist: String,
        error: String?
    ) {

        if (
            destroyed ||
            isFinishing
        ) {
            return
        }

        try {

            if (
                duration > 0L
            ) {

                val percent =
                    (
                        position.toDouble() /
                                duration.toDouble() *
                                100.0
                        )
                        .toInt()
                        .coerceIn(
                            0,
                            100
                        )

                seekBar.progress =
                    percent

                currentTimeText.text =
                    formatTime(
                        position
                    )

                durationText.text =
                    formatTime(
                        duration
                    )
            }

            if (
                newTitle.isNotBlank()
            ) {
                titleText.text =
                    newTitle
            }

            if (
                newArtist.isNotBlank()
            ) {
                artistText.text =
                    newArtist
            }

            playButton.text =
                if (playing) {
                    "⏸"
                } else {
                    "▶"
                }

            if (
                playing
            ) {

                vinyl.startRotation()

                status.text =
                    "در حال پخش"

            } else {

                vinyl.stopRotation()

                if (
                    !error.isNullOrBlank()
                ) {

                    status.text =
                        "خطا در پخش"

                } else if (
                    currentAudioUrl.isNotBlank()
                ) {

                    status.text =
                        "متوقف"
                }
            }

            if (
                !error.isNullOrBlank()
            ) {

                Toast.makeText(
                    this,
                    "پخش آهنگ ناموفق بود",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (_: Exception) {
        }
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

        if (
            milliseconds <= 0L
        ) {
            return "00:00"
        }

        val totalSeconds =
            milliseconds / 1000L

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    private fun parseTime(
        value: String
    ): Long {

        return try {

            val parts =
                value.split(":")

            if (
                parts.size != 2
            ) {
                0L
            } else {

                val minutes =
                    parts[0].toLong()

                val seconds =
                    parts[1].toLong()

                (
                    minutes * 60L +
                            seconds
                    ) * 1000L
            }

        } catch (_: Exception) {

            0L
        }
    }

    private fun downloadCurrentSong() {

        if (
            destroyed
        ) {
            return
        }

        val url =
            currentAudioUrl.trim()

        if (
            !isValidHttpUrl(url)
        ) {

            Toast.makeText(
                this,
                "اول یک آهنگ قابل پخش انتخاب کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            downloadThread?.isAlive == true
        ) {

            Toast.makeText(
                this,
                "یک دانلود در حال انجام است",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val name =
            makeSafeFileName(
                titleText.text.toString()
            ) + ".mp3"

        cancelRequested = false
        pauseDownloadRequested = false
        lastDownloadPercent = -1

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

        downloadThread =
            Thread {

                var success = false
                var cancelled = false

                try {

                    if (
                        Build.VERSION.SDK_INT >= 29
                    ) {

                        downloadMediaStore(
                            url,
                            name
                        )

                    } else {

                        downloadOld(
                            url,
                            name
                        )
                    }

                    success = true

                } catch (e: Exception) {

                    cancelled =
                        e.message ==
                                "CANCELLED"

                } finally {

                    if (
                        !destroyed
                    ) {

                        runOnUiThread {

                            if (
                                destroyed
                            ) {
                                return@runOnUiThread
                            }

                            when {

                                success -> {

                                    downloadProgress.progress =
                                        100

                                    downloadText.text =
                                        "100%"

                                    status.text =
                                        "دانلود کامل شد ✓"
                                }

                                cancelled -> {

                                    status.text =
                                        "دانلود لغو شد"
                                }

                                else -> {

                                    status.text =
                                        "دانلود ناموفق بود"
                                }
                            }

                            resetDownloadButtons()
                        }
                    }
                }
            }.apply {
                name =
                    "MusicFinder-Download"
                isDaemon = true
            }

        downloadThread?.start()
    }

    private fun toggleDownloadPause() {

        if (
            downloadThread?.isAlive != true
        ) {
            return
        }

        pauseDownloadRequested =
            !pauseDownloadRequested

        pauseDownloadButton.text =
            if (
                pauseDownloadRequested
            ) {
                "▶"
            } else {
                "⏸"
            }

        downloadText.text =
            if (
                pauseDownloadRequested
            ) {
                "دانلود متوقف شد"
            } else {
                "در حال دانلود..."
            }
    }

    private fun cancelDownload() {

        if (
            downloadThread?.isAlive != true
        ) {
            return
        }

        cancelRequested = true
        pauseDownloadRequested = false

        downloadText.text =
            "در حال لغو..."
    }

    private fun resetDownloadButtons() {

        if (destroyed) {
            return
        }

        downloadButton.isEnabled =
            true

        pauseDownloadButton.visibility =
            View.GONE

        cancelDownloadButton.visibility =
            View.GONE
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
            )
                ?: throw Exception(
                    "CREATE_FAILED"
                )

        var connection:
                HttpURLConnection? = null

        try {

            connection =
                URL(
                    urlString
                ).openConnection()
                        as HttpURLConnection

            connection.connectTimeout =
                15000

            connection.readTimeout =
                30000

            connection.instanceFollowRedirects =
                true

            connection.connect()

            if (
                connection.responseCode !in
                200..299
            ) {
                throw Exception(
                    "HTTP_${connection.responseCode}"
                )
            }

            val total =
                connection.contentLengthLong

            var downloaded = 0L

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                val output =
                    contentResolver
                        .openOutputStream(
                            uri
                        )
                        ?: throw Exception(
                            "OUTPUT_FAILED"
                        )

                output.use {

                    val buffer =
                        ByteArray(16 * 1024)

                    while (true) {

                        checkCancelled()

                        while (
                            pauseDownloadRequested &&
                            !cancelRequested
                        ) {

                            Thread.sleep(
                                200L
                            )
                        }

                        checkCancelled()

                        val count =
                            input.read(
                                buffer
                            )

                        if (
                            count == -1
                        ) {
                            break
                        }

                        it.write(
                            buffer,
                            0,
                            count
                        )

                        downloaded +=
                            count

                        if (
                            total > 0L
                        ) {

                            val percent =
                                (
                                    downloaded.toDouble() /
                                            total.toDouble() *
                                            100.0
                                    )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )

                            updateDownloadProgress(
                                percent
                            )
                        }
                    }
                }
            }

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
        }
    }

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
                URL(
                    urlString
                ).openConnection()
                        as HttpURLConnection

            connection.connectTimeout =
                15000

            connection.readTimeout =
                30000

            connection.instanceFollowRedirects =
                true

            connection.connect()

            if (
                connection.responseCode !in
                200..299
            ) {
                throw Exception(
                    "HTTP_${connection.responseCode}"
                )
            }

            val total =
                connection.contentLengthLong

            var downloaded = 0L

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                FileOutputStream(
                    file
                ).use { output ->

                    val buffer =
                        ByteArray(16 * 1024)

                    while (true) {

                        checkCancelled()

                        while (
                            pauseDownloadRequested &&
                            !cancelRequested
                        ) {

                            Thread.sleep(
                                200L
                            )
                        }

                        checkCancelled()

                        val count =
                            input.read(
                                buffer
                            )

                        if (
                            count == -1
                        ) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )

                        downloaded +=
                            count

                        if (
                            total > 0L
                        ) {

                            val percent =
                                (
                                    downloaded.toDouble() /
                                            total.toDouble() *
                                            100.0
                                    )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )

                            updateDownloadProgress(
                                percent
                            )
                        }
                    }
                }
            }

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
        }
    }

    private fun checkCancelled() {

        if (
            cancelRequested ||
            Thread.currentThread().isInterrupted
        ) {

            throw Exception(
                "CANCELLED"
            )
        }
    }

    private fun updateDownloadProgress(
        percent: Int
    ) {

        if (
            destroyed ||
            percent == lastDownloadPercent
        ) {
            return
        }

        /*
         * Progress فقط زمانی به UI فرستاده می‌شود
         * که واقعاً تغییر کرده باشد.
         */
        lastDownloadPercent =
            percent

        runOnUiThread {

            if (
                destroyed
            ) {
                return@runOnUiThread
            }

            try {

                downloadProgress.progress =
                    percent

                if (
                    !pauseDownloadRequested
                ) {

                    downloadText.text =
                        "$percent%"
                }

            } catch (_: Exception) {
            }
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
                value.trim()
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

    private fun makeSafeFileName(
        text: String
    ): String {

        var name =
            text.trim()

        if (
            name.isEmpty()
        ) {
            name =
                "Music_Finder"
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

    private fun isValidHttpUrl(
        value: String
    ): Boolean {

        return try {

            val uri =
                android.net.Uri.parse(
                    value.trim()
                )

            val scheme =
                uri.scheme?.lowercase()

            (
                scheme == "http" ||
                        scheme == "https"
                ) &&
                    !uri.host.isNullOrBlank()

        } catch (_: Exception) {

            false
        }
    }

    private fun saveSearchResults() {

        if (destroyed) {
            return
        }

        try {

            val prefs =
                getSharedPreferences(
                    "search_results",
                    MODE_PRIVATE
                )

            val data =
                songs
                    .take(50)
                    .joinToString(
                        separator = "\n"
                    ) {

                        listOf(
                            it.url,
                            it.title,
                            it.artist,
                            it.site,
                            it.cover
                        ).joinToString(
                            "|||"
                        )
                    }

            prefs.edit()
                .putString(
                    "songs",
                    data
                )
                .apply()

        } catch (_: Exception) {
        }
    }

    private fun restoreSearchResults() {

        if (
            destroyed
        ) {
            return
        }

        try {

            val prefs =
                getSharedPreferences(
                    "search_results",
                    MODE_PRIVATE
                )

            val data =
                prefs.getString(
                    "songs",
                    ""
                ).orEmpty()

            if (
                data.isBlank()
            ) {
                return
            }

            songs.clear()

            data.split("\n")
                .take(50)
                .forEach { line ->

                    try {

                        if (
                            line.isBlank()
                        ) {
                            return@forEach
                        }

                        val p =
                            line.split(
                                "|||"
                            )

                        if (
                            p.size < 5
                        ) {
                            return@forEach
                        }

                        val url =
                            p[0].trim()

                        if (
                            !isValidHttpUrl(url)
                        ) {
                            return@forEach
                        }

                        songs.add(
                            SongResult(
                                url = url,
                                title = p[1].trim(),
                                artist = p[2].trim(),
                                site = p[3].trim(),
                                cover = p[4].trim()
                            )
                        )

                    } catch (_: Exception) {
                    }
                }

            songs
                .distinctBy {
                    it.url
                }
                .let {

                    songs.clear()
                    songs.addAll(it)
                }

            resultsContainer.removeAllViews()

            songs.forEachIndexed {
                    index,
                    song ->

                addSongView(
                    song,
                    index
                )
            }

            if (
                songs.isNotEmpty()
            ) {

                currentIndex = 0

                status.text =
                    "${songs.size} نتیجه ذخیره شده"
            }

        } catch (_: Exception) {
        }
    }

    override fun onStart() {

        super.onStart()

        if (
            !destroyed
        ) {
            registerPlayerReceiver()
        }
    }

    override fun onStop() {

        unregisterPlayerReceiver()

        super.onStop()
    }

    override fun onDestroy() {

        destroyed = true

        searchRunning = false
        searchGeneration++

        mainHandler.removeCallbacksAndMessages(
            null
        )

        try {
            web.stopLoading()
        } catch (_: Exception) {
        }

        cancelRequested = true
        pauseDownloadRequested = false

        try {
            downloadThread?.interrupt()
        } catch (_: Exception) {
        }

        downloadThread = null

        unregisterPlayerReceiver()

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }

        try {
            web.removeJavascriptInterface(
                "MusicFinder"
            )
        } catch (_: Exception) {
        }

        try {
            web.webViewClient =
                WebViewClient()
        } catch (_: Exception) {
        }

        try {
            web.stopLoading()
            web.loadUrl(
                "about:blank"
            )
            web.clearHistory()
            web.removeAllViews()
            web.destroy()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
