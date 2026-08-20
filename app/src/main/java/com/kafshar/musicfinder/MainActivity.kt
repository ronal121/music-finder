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
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

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

    private var currentIndex = -1
    private var currentAudioUrl = ""

    private var randomMode = false

    /**
     * هر Search یک generation مستقل دارد.
     *
     * اگر Search جدید شروع شود، تمام callbackهای Search قبلی
     * دیگر اجازه تغییر State برنامه را ندارند.
     */
    private var searchGeneration = 0L

    /**
     * Handler اصلی Activity.
     *
     * به جای ساختن Handler جدید برای هر صفحه،
     * تمام timeoutهای Search از همین Handler استفاده می‌کنند.
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Future مربوط به آخرین عملیات Search/cover loading.
     */
    private var searchFuture: Future<*>? = null

    /**
     * Executor محدود به چند Thread.
     *
     * cachedThreadPool قبلی می‌توانست با تعداد زیادی نتیجه،
     * تعداد زیادی Thread ایجاد کند.
     */
    private val executor: ExecutorService =
        Executors.newFixedThreadPool(3)

    /**
     * جلوگیری از دسترسی callbackها به Activity بعد از Destroy.
     */
    @Volatile
    private var activityDestroyed = false

    /**
     * وضعیت ثبت شدن Receiver.
     */
    private var receiverRegistered = false

    /**
     * وضعیت Search.
     */
    @Volatile
    private var searchRunning = false

    /**
     * URL صفحه‌ای که WebView در حال بررسی آن است.
     */
    private var currentPageUrl = ""

    /**
     * جلوگیری از پردازش چندباره یک صفحه.
     */
    private var currentPageGeneration = -1L

    private var currentPageHandled = false

    /**
     * وضعیت دانلود.
     */
    private var downloadThread: Thread? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var pauseDownloadRequested = false

    private val downloadRunning =
        AtomicBoolean(false)

    /**
     * برای جلوگیری از ارسال صدها update در ثانیه
     * به UI هنگام دانلود.
     */
    private var lastDownloadUiUpdate = 0L

    /**
     * BroadcastReceiver مربوط به Player.
     */
    private val playerReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (activityDestroyed) {
                    return
                }

                if (
                    intent?.action !=
                    MusicService.UPDATE
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

                if (Looper.myLooper() ==
                    Looper.getMainLooper()
                ) {

                    handlePlayerUpdate(
                        playing,
                        position,
                        duration,
                        newTitle,
                        newArtist
                    )

                } else {

                    mainHandler.post {

                        if (activityDestroyed) {
                            return@post
                        }

                        handlePlayerUpdate(
                            playing,
                            position,
                            duration,
                            newTitle,
                            newArtist
                        )
                    }
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        activityDestroyed = false

        setContentView(
            R.layout.activity_main
        )

        initializeViews()

        requestNotificationPermission()

        setupWebView()

        setupButtons()

        restoreSearchResults()

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

        if (Build.VERSION.SDK_INT < 33) {
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

        } catch (
            _: Exception
        ) {
            // Permission failure must never crash the Activity.
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        web.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = false

            mediaPlaybackRequiresUserGesture =
                false

            allowFileAccess = false

            allowContentAccess = false

            builtInZoomControls = false

            displayZoomControls = false

            setSupportZoom(false)

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/128 Mobile Safari/537.36"
        }

        web.setBackgroundColor(
            0x00000000
        )

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

                    if (activityDestroyed) {
                        return true
                    }

                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean {

                    if (activityDestroyed) {
                        return true
                    }

                    return false
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    if (activityDestroyed) {
                        return
                    }

                    currentPageUrl = url

                    currentPageGeneration =
                        searchGeneration

                    currentPageHandled = false
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    if (activityDestroyed) {
                        return
                    }

                    if (
                        currentPageGeneration !=
                        searchGeneration
                    ) {
                        return
                    }

                    if (
                        currentPageHandled
                    ) {
                        return
                    }

                    if (
                        url.contains(
                            "google.com/search",
                            ignoreCase = true
                        )
                    ) {

                        extractGoogleResults(
                            searchGeneration
                        )

                    } else if (
                        searchRunning
                    ) {

                        extractMusicPage(
                            url,
                            searchGeneration
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )

                    if (activityDestroyed) {
                        return
                    }

                    if (
                        !request.isForMainFrame
                    ) {
                        return
                    }

                    if (
                        currentPageGeneration !=
                        searchGeneration
                    ) {
                        return
                    }

                    mainHandler.post {

                        if (
                            activityDestroyed ||
                            currentPageGeneration !=
                            searchGeneration
                        ) {
                            return@post
                        }

                        status.text =
                            "خطا در اتصال به سایت"

                        continueResultProcessing(
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

            if (!activityDestroyed) {
                searchMusic()
            }
        }

        query.setOnEditorActionListener {
                _, actionId, _ ->

            if (
                actionId ==
                EditorInfo.IME_ACTION_SEARCH
            ) {

                if (!activityDestroyed) {
                    searchMusic()
                }

                true

            } else {

                false
            }
        }

        playButton.setOnClickListener {

            if (
                activityDestroyed ||
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

            if (!activityDestroyed) {
                previousSong()
            }
        }

        nextButton.setOnClickListener {

            if (!activityDestroyed) {
                nextSong()
            }
        }

        randomButton.setOnClickListener {

            if (activityDestroyed) {
                return@setOnClickListener
            }

            randomMode =
                !randomMode

            randomButton.text =
                if (randomMode)
                    "🔀"
                else
                    "🔁"
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
                        activityDestroyed
                    ) {
                        return
                    }

                    val duration =
                        parseTime(
                            durationText.text.toString()
                        )

                    if (duration > 0L) {

                        val position =
                            duration *
                                progress /
                                100L

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

                    if (activityDestroyed) {
                        return
                    }

                    val percent =
                        seekBar?.progress
                            ?.coerceIn(0, 100)
                            ?: 0

                    sendServiceSeek(
                        percent
                    )
                }
            }
        )

        downloadButton.setOnClickListener {

            if (!activityDestroyed) {
                downloadCurrentSong()
            }
        }

        pauseDownloadButton.setOnClickListener {

            if (!activityDestroyed) {
                toggleDownloadPause()
            }
        }

        cancelDownloadButton.setOnClickListener {

            if (!activityDestroyed) {
                cancelDownload()
            }
        }

        saveButton.setOnClickListener {

            if (!activityDestroyed) {
                saveCurrentSong()
            }
        }

        libraryButton.setOnClickListener {

            if (activityDestroyed) {
                return@setOnClickListener
            }

            try {

                startActivity(
                    Intent(
                        this,
                        LibraryActivity::class.java
                    )
                )

            } catch (
                _: Exception
            ) {

                Toast.makeText(
                    this,
                    "باز کردن کتابخانه ممکن نیست",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun searchMusic() {

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

        /**
         * Search قبلی کاملاً invalidate می‌شود.
         */
        searchGeneration++

        val generation =
            searchGeneration

        searchRunning = true

        currentPageHandled = false

        currentPageGeneration =
            generation

        currentPageUrl = ""

        /**
         * WebView نباید Search قبلی را ادامه دهد.
         */
        try {
            web.stopLoading()
        } catch (
            _: Exception
        ) {
        }

        /**
         * تمام timeoutهای Search قبلی حذف می‌شوند.
         */
        mainHandler.removeCallbacksAndMessages(
            SEARCH_CALLBACK_TOKEN
        )

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

        currentTimeText.text =
            "00:00"

        durationText.text =
            "00:00"

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

            } catch (
                _: Exception
            ) {

                status.text =
                    "خطا در آماده‌سازی جستجو"

                searchRunning = false

                return
            }

        val url =
            "https://www.google.com/search" +
            "?q=$encoded&num=100"

        try {

            web.loadUrl(url)

        } catch (
            _: Exception
        ) {

            searchRunning = false

            status.text =
                "خطا در شروع جستجو"
        }
    }

    private fun extractGoogleResults(
        generation: Long
    ) {

        if (
            activityDestroyed ||
            generation != searchGeneration ||
            !searchRunning
        ) {
            return
        }

        val script = """
            (function() {

                try {

                    var links =
                        document.querySelectorAll("a");

                    var found = [];

                    for (
                        var i = 0;
                        i < links.length;
                        i++
                    ) {

                        var href =
                            links[i].href || "";

                        var text =
                            links[i].innerText || "";

                        var lower =
                            href.toLowerCase();

                        if (
                            lower.indexOf("rozmusic.com") >= 0 ||
                            lower.indexOf("mybia2music.com") >= 0 ||
                            lower.indexOf("musicdel.ir") >= 0 ||
                            lower.indexOf("musics-fa.com") >= 0
                        ) {

                            if (
                                href.indexOf("google.com") < 0 &&
                                found.indexOf(href) < 0
                            ) {

                                found.push(
                                    href + "|||" +
                                    text.replace(
                                        /[\r\n]+/g,
                                        " "
                                    )
                                );
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

        } catch (
            _: Exception
        ) {

            if (
                generation ==
                searchGeneration
            ) {

                status.text =
                    "خطا در استخراج نتایج"

                searchRunning = false
            }
        }
    }

    private fun extractMusicPage(
        pageUrl: String,
        generation: Long
    ) {

        if (
            activityDestroyed ||
            generation != searchGeneration ||
            !searchRunning
        ) {
            return
        }

        val script = """
            (function() {

                try {

                    var title = "";
                    var artist = "";
                    var cover = "";

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

                    var audioLinks = [];

                    var media =
                        document.querySelectorAll(
                            "audio source, audio, video source, video, a"
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
                        "###" +
                        "###" +
                        "###"
                    );
                }

            })();
        """.trimIndent()

        try {

            web.evaluateJavascript(
                script,
                null
            )

        } catch (
            _: Exception
        ) {

            if (
                generation ==
                searchGeneration
            ) {

                continueResultProcessing(
                    generation
                )
            }
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

    /**
     * Token مشترک برای callbackهای Search.
     *
     * به این شکل فقط callbackهای Search حذف می‌شوند
     * و callbackهای دیگر Handler دستکاری نمی‌شوند.
     */
    private val SEARCH_CALLBACK_TOKEN =
        Any()

    private fun scheduleSearchCallback(
        generation: Long,
        delayMillis: Long,
        action: () -> Unit
    ) {

        mainHandler.postAtTime(
            {
                if (
                    activityDestroyed ||
                    generation != searchGeneration
                ) {
                    return@postAtTime
                }

                try {
                    action()
                } catch (
                    _: Exception
                ) {
                    if (
                        generation ==
                        searchGeneration &&
                        !activityDestroyed
                    ) {
                        status.text =
                            "خطا در پردازش نتیجه"
                    }
                }
            },
            SEARCH_CALLBACK_TOKEN,
            System.currentTimeMillis() +
                delayMillis
        )
    }

    inner class Bridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

            if (
                activityDestroyed
            ) {
                return
            }

            mainHandler.post {

                if (
                    activityDestroyed ||
                    !searchRunning
                ) {
                    return@post
                }

                val generation =
                    searchGeneration

                val items =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .distinctBy {
                            it.substringBefore("|||")
                        }

                if (items.isEmpty()) {

                    searchRunning = false

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@post
                }

                status.text =
                    "در حال بررسی نتایج..."

                processResultPages(
                    items.take(50),
                    0,
                    generation
                )
            }
        }

        @JavascriptInterface
        fun page(
            data: String
        ) {

            if (
                activityDestroyed
            ) {
                return
            }

            mainHandler.post {

                if (
                    activityDestroyed ||
                    !searchRunning
                ) {
                    return@post
                }

                val generation =
                    searchGeneration

                if (
                    currentPageGeneration !=
                    generation
                ) {
                    return@post
                }

                if (
                    currentPageHandled
                ) {
                    return@post
                }

                currentPageHandled = true

                val parts =
                    data.split("###")

                if (
                    parts.size < 4
                ) {

                    continueResultProcessing(
                        generation
                    )

                    return@post
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

                if (
                    audio.isBlank()
                ) {

                    continueResultProcessing(
                        generation
                    )

                    return@post
                }

                val song =
                    SongResult(
                        url = audio,
                        title =
                            if (
                                title.isBlank()
                            )
                                query.text
                                    .toString()
                                    .trim()
                            else
                                cleanTitle(title),

                        artist =
                            if (
                                artist.isBlank()
                            )
                                query.text
                                    .toString()
                                    .trim()
                            else
                                artist,

                        site =
                            getSiteName(
                                currentPageUrl
                            ),

                        cover = cover
                    )

                addSong(song)

                continueResultProcessing(
                    generation
                )
            }
        }
    }

    private fun processResultPages(
        items: List<String>,
        index: Int,
        generation: Long
    ) {

        if (
            activityDestroyed ||
            generation != searchGeneration ||
            !searchRunning
        ) {
            return
        }

        if (
            index >= items.size
        ) {

            finishSearch(
                generation
            )

            return
        }

        val url =
            items[index]
                .substringBefore("|||")
                .trim()

        if (
            url.isBlank()
        ) {

            processResultPages(
                items,
                index + 1,
                generation
            )

            return
        }

        currentPageUrl = url

        currentPageGeneration =
            generation

        currentPageHandled = false

        /**
         * اگر صفحه جواب نداد، بعد از timeout
         * سراغ نتیجه بعدی می‌رویم.
         */
        scheduleSearchCallback(
            generation,
            SEARCH_PAGE_TIMEOUT
        ) {

            if (
                currentPageGeneration ==
                generation &&
                !currentPageHandled
            ) {

                currentPageHandled = true

                processResultPages(
                    items,
                    index + 1,
                    generation
                )
            }
        }

        try {

            web.loadUrl(url)

        } catch (
            _: Exception
        ) {

            currentPageHandled = true

            processResultPages(
                items,
                index + 1,
                generation
            )
        }
    }

    private fun continueResultProcessing(
        generation: Long
    ) {

        if (
            activityDestroyed ||
            generation != searchGeneration ||
            !searchRunning
        ) {
            return
        }

        /**
         * این مقدار در ادامه‌ی Search نگهداری می‌شود.
         */
        val nextIndex =
            processedResultIndex + 1

        if (
            nextIndex >=
            currentResultItems.size
        ) {

            finishSearch(
                generation
            )

            return
        }

        processedResultIndex =
            nextIndex

        processResultPages(
            currentResultItems,
            processedResultIndex,
            generation
        )
    }

    private var currentResultItems =
        emptyList<String>()

    private var processedResultIndex =
        -1

    private fun finishSearch(
        generation: Long
    ) {

        if (
            activityDestroyed ||
            generation != searchGeneration
        ) {
            return
        }

        searchRunning = false

        status.text =
            if (songs.isEmpty())
                "آهنگ قابل پخش پیدا نشد"
            else
                "${songs.size} نتیجه پیدا شد"

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
            activityDestroyed
        ) {
            return
        }

        if (
            song.url.isBlank()
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
            songs.size - 1
        )
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

        if (
            activityDestroyed
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

                tag =
                    song.url
            }

        row.addView(
            cover,
            LinearLayout.LayoutParams(
                62,
                62
            )
        )

        if (
            song.cover.isNotBlank()
        ) {

            loadCoverAsync(
                song.cover,
                cover,
                song.url
            )
        }

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

                textSize =
                    15f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                maxLines =
                    2
            }

        val info =
            TextView(this).apply {

                text =
                    "${song.artist} • ${song.site}"

                textSize =
                    12f

                setTextColor(
                    0xFFAAAAAA.toInt()
                )

                maxLines =
                    2
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
                    try {

                        if (
                            LibraryManager.contains(
                                this@MainActivity,
                                song
                            )
                        )
                            "♥"
                        else
                            "♡"

                    } catch (
                        _: Exception
                    ) {

                        "♡"
                    }

                textSize =
                    25f

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

                    if (
                        activityDestroyed
                    ) {
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

                            text =
                                "♡"

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

                            text =
                                "♥"

                            Toast.makeText(
                                this@MainActivity,
                                "به کتابخانه اضافه شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } catch (
                        _: Exception
                    ) {

                        Toast.makeText(
                            this@MainActivity,
                            "خطا در کتابخانه",
                            Toast.LENGTH_SHORT
                        ).show()
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

            if (activityDestroyed) {
                return@setOnClickListener
            }

            currentIndex =
                songs.indexOf(song)

            if (
                currentIndex >= 0
            ) {

                playSong(song)
            }
        }

        resultsContainer.addView(
            row
        )
    }

    private fun loadCoverAsync(
        coverUrl: String,
        imageView: ImageView,
        songUrl: String
    ) {

        val expectedUrl =
            songUrl

        executor.execute {

            var connection:
                HttpURLConnection? = null

            try {

                connection =
                    URL(coverUrl)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    5000

                connection.readTimeout =
                    5000

                connection.instanceFollowRedirects =
                    true

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0"
                )

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
                    bitmap == null
                ) {
                    return@execute
                }

                mainHandler.post {

                    if (
                        activityDestroyed
                    ) {
                        bitmap.recycleSafely()
                        return@post
                    }

                    if (
                        imageView.tag ==
                        expectedUrl
                    ) {

                        imageView.setImageBitmap(
                            bitmap
                        )

                    } else {

                        bitmap.recycleSafely()
                    }
                }

            } catch (
                _: Exception
            ) {

            } finally {

                try {
                    connection?.disconnect()
                } catch (
                    _: Exception
                ) {
                }
            }
        }
    }

    private fun BitmapFactory.BitmapFactoryOptions() {
        // Reserved for future bitmap optimization.
    }

    private fun android.graphics.Bitmap.recycleSafely() {

        try {

            if (!isRecycled) {
                recycle()
            }

        } catch (
            _: Exception
        ) {
        }
    }

    companion object {

        private const val SEARCH_PAGE_TIMEOUT =
            3500L
    }
