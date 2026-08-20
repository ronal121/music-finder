package com.kafshar.musicfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.*
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread

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
    private lateinit var saveButton: TextView
    private lateinit var libraryButton: TextView
    private lateinit var downloadButton: TextView
    private lateinit var pauseDownloadButton: TextView
    private lateinit var cancelDownloadButton: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadText: TextView
    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var vinyl: VinylView

    private val songs = ArrayList<SongResult>()

    private val executor =
        Executors.newFixedThreadPool(4)

    private val handler =
        Handler(Looper.getMainLooper())

    private var currentIndex = -1

    private var currentAudioUrl = ""

    private var currentSong: SongResult? = null

    private var randomMode = false

    private var searchGeneration = 0

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var pauseDownloadRequested = false

    private var downloadThread: Thread? = null

    private var vinylAngle = 0f

    private val receiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

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

                val pos =
                    intent.getLongExtra(
                        "position",
                        0L
                    )

                val dur =
                    intent.getLongExtra(
                        "duration",
                        0L
                    )

                playButton.text =
                    if (playing)
                        "Ⅱ"
                    else
                        "▶"

                if (dur > 0) {

                    progress.progress =
                        (
                            pos.toDouble() /
                                    dur.toDouble() *
                                    100
                            )
                            .toInt()
                            .coerceIn(
                                0,
                                100
                            )

                    currentTime.text =
                        formatTime(pos)

                    durationText.text =
                        formatTime(dur)
                }

                if (playing) {

                    vinylAngle =
                        (vinylAngle + 2f) % 360f

                    vinyl.setRotationAngle(
                        vinylAngle
                    )

                    handler.postDelayed(
                        {
                            receiver.onReceive(
                                this@MainActivity,
                                Intent(
                                    MusicService.UPDATE
                                ).apply {

                                    putExtra(
                                        "playing",
                                        true
                                    )

                                    putExtra(
                                        "position",
                                        pos
                                    )

                                    putExtra(
                                        "duration",
                                        dur
                                    )
                                }
                            )
                        },
                        1000
                    )
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        query =
            findViewById(
                R.id.query
            )

        status =
            findViewById(
                R.id.status
            )

        titleText =
            findViewById(
                R.id.titleText
            )

        artistText =
            findViewById(
                R.id.artistText
            )

        playButton =
            findViewById(
                R.id.playButton
            )

        previousButton =
            findViewById(
                R.id.previousButton
            )

        nextButton =
            findViewById(
                R.id.nextButton
            )

        randomButton =
            findViewById(
                R.id.randomButton
            )

        saveButton =
            findViewById(
                R.id.saveButton
            )

        libraryButton =
            findViewById(
                R.id.libraryButton
            )

        downloadButton =
            findViewById(
                R.id.downloadButton
            )

        pauseDownloadButton =
            findViewById(
                R.id.pauseDownloadButton
            )

        cancelDownloadButton =
            findViewById(
                R.id.cancelDownloadButton
            )

        downloadProgress =
            findViewById(
                R.id.downloadProgress
            )

        downloadText =
            findViewById(
                R.id.downloadText
            )

        progress =
            findViewById(
                R.id.progress
            )

        currentTime =
            findViewById(
                R.id.currentTime
            )

        durationText =
            findViewById(
                R.id.duration
            )

        resultsContainer =
            findViewById(
                R.id.resultsContainer
            )

        vinyl =
            findViewById(
                R.id.vinyl
            )

        web =
            findViewById(
                R.id.web
            )

        registerReceiverCompat()

        requestNotificationPermission()

        setupWebView()

        setupButtons()

        restoreResults()

        status.text =
            if (songs.isEmpty())
                "نام آهنگ یا خواننده را جستجو کنید"
            else
                "${songs.size} نتیجه آماده است"
    }

    private fun registerReceiverCompat() {

        val f =
            IntentFilter(
                MusicService.UPDATE
            )

        if (
            Build.VERSION.SDK_INT >= 33
        ) {

            registerReceiver(
                receiver,
                f,
                RECEIVER_NOT_EXPORTED
            )

        } else {

            registerReceiver(
                receiver,
                f
            )
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
            PackageManager.PERMISSION_GRANTED
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
    private fun setupWebView() {

        web.settings.javaScriptEnabled =
            true

        web.settings.domStorageEnabled =
            true

        web.settings.databaseEnabled =
            true

        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 12) " +
                    "AppleWebKit/537.36 " +
                    "Chrome/128 Mobile Safari/537.36"

        web.addJavascriptInterface(
            Bridge(),
            "MusicFinder"
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    if (
                        url.contains(
                            "google.com/search"
                        )
                    ) {

                        extractGoogleResults()

                    } else if (
                        url.contains(
                            "rozmusic.com"
                        ) ||
                        url.contains(
                            "mybia2music.com"
                        ) ||
                        url.contains(
                            "musicdel.ir"
                        ) ||
                        url.contains(
                            "musics-fa.com"
                        )
                    ) {

                        extractMusicPage(
                            url
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
                _,
                action,
                _ ->

            if (
                action ==
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
                currentAudioUrl.isNotBlank()
            ) {

                sendService(
                    MusicService.ACTION_TOGGLE,
                    currentSong
                )
            }
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
                if (randomMode)
                    "🔀"
                else
                    "↕"
        }

        saveButton.setOnClickListener {

            currentSong?.let {

                LibraryManager.add(
                    this,
                    it
                )

                saveButton.text =
                    "✓ ذخیره شد"

                Toast.makeText(
                    this,
                    "به کتابخانه اضافه شد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        libraryButton.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    LibraryActivity::class.java
                )
            )
        }

        downloadButton.setOnClickListener {

            if (
                downloadThread?.isAlive != true
            ) {

                downloadCurrentSong()
            }
        }

        pauseDownloadButton.setOnClickListener {

            toggleDownloadPause()
        }

        cancelDownloadButton.setOnClickListener {

            cancelDownload()
        }

        progress.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    s: SeekBar?,
                    p: Int,
                    fromUser: Boolean
                ) {

                    if (fromUser) {

                        sendSeek(p)
                    }
                }

                override fun onStartTrackingTouch(
                    s: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    s: SeekBar?
                ) {
                }
            }
        )
    }

    private fun searchMusic() {

        val text =
            query.text
                .toString()
                .trim()

        if (
            text.isEmpty()
        ) {

            Toast.makeText(
                this,
                "نام آهنگ یا خواننده را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        searchGeneration++

        songs.clear()

        currentIndex = -1

        resultsContainer.removeAllViews()

        status.text =
            "در حال جستجو..."

        titleText.text =
            "نتایج برای: $text"

        artistText.text =
            "جستجوی گسترده در سایت‌ها"

        val q =
            URLEncoder.encode(
                "$text " +
                        "(site:rozmusic.com OR " +
                        "site:mybia2music.com OR " +
                        "site:musicdel.ir OR " +
                        "site:musics-fa.com)",
                "UTF-8"
            )

        web.loadUrl(
            "https://www.google.com/search?q=$q&num=50"
        )
    }

    private fun extractGoogleResults() {

        web.evaluateJavascript(
            """
            (function(){
                var a =
                    document.querySelectorAll("a");

                var out = [];

                for(
                    var i = 0;
                    i < a.length;
                    i++
                ){

                    var h =
                        a[i].href || "";

                    var l =
                        h.toLowerCase();

                    if(
                        (
                            l.indexOf(
                                "rozmusic.com"
                            ) >= 0 ||

                            l.indexOf(
                                "mybia2music.com"
                            ) >= 0 ||

                            l.indexOf(
                                "musicdel.ir"
                            ) >= 0 ||

                            l.indexOf(
                                "musics-fa.com"
                            ) >= 0
                        ) &&

                        h.indexOf(
                            "google.com"
                        ) < 0 &&

                        out.indexOf(h) < 0
                    ){

                        out.push(h);
                    }
                }

                MusicFinder.results(
                    out.join("###")
                );

            })();
            """.trimIndent(),
            null
        )
    }

    private fun extractMusicPage(
        pageUrl: String
    ) {

        web.evaluateJavascript(
            """
            (function(){

                var t = "";
                var a = "";
                var c = "";

                var m =
                    document.querySelector(
                        'meta[property="og:title"]'
                    );

                if(m)
                    t =
                        m.content || "";

                var h =
                    document.querySelector("h1");

                if(
                    !t &&
                    h
                )
                    t =
                        h.innerText || "";

                var im =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if(im)
                    c =
                        im.content || "";

                var audioLinks = [];

                var media =
                    document.querySelectorAll(
                        "audio source, audio, video source, video, a"
                    );

                for(
                    var i = 0;
                    i < media.length;
                    i++
                ){

                    var el =
                        media[i];

                    var src =
                        el.src ||
                        el.href ||
                        "";

                    var lower =
                        src.toLowerCase();

                    if(
                        lower.indexOf(
                            ".mp3"
                        ) >= 0 ||

                        lower.indexOf(
                            ".m4a"
                        ) >= 0 ||

                        lower.indexOf(
                            ".aac"
                        ) >= 0 ||

                        lower.indexOf(
                            ".ogg"
                        ) >= 0 ||

                        lower.indexOf(
                            ".wav"
                        ) >= 0 ||

                        lower.indexOf(
                            ".flac"
                        ) >= 0 ||

                        lower.indexOf(
                            "dl."
                        ) >= 0
                    ){

                        if(
                            audioLinks.indexOf(
                                src
                            ) < 0
                        ){

                            audioLinks.push(src);
                        }
                    }
                }

                MusicFinder.page(
                    encodeURIComponent(t) +
                    "###" +
                    encodeURIComponent(a) +
                    "###" +
                    encodeURIComponent(c) +
                    "###" +
                    encodeURIComponent(
                        audioLinks.join("|||")
                    )
                );

            })();
            """.trimIndent(),
            null
        )
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

            runOnUiThread {

                val items =
                    data.split(
                        "###"
                    )
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }

                if (
                    items.isEmpty()
                ) {

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                status.text =
                    "در حال بررسی ${items.size} نتیجه..."

                val limited =
                    items.take(50)

                processResultPages(
                    limited,
                    0,
                    searchGeneration
                )
            }
        }

        @JavascriptInterface
        fun page(
            data: String
        ) {

            runOnUiThread {

                val parts =
                    data.split(
                        "###"
                    )

                if (
                    parts.size < 4
                ) {
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
                            it.isNotBlank()
                        }
                        ?: ""

                if (
                    audio.isBlank()
                ) {
                    return@runOnUiThread
                }

                val pageUrl =
                    web.url ?: ""

                val song =
                    SongResult(
                        url = audio,

                        title =
                            if (
                                title.isBlank()
                            )
                                query.text
                                    .toString()
                            else
                                cleanTitle(
                                    title
                                ),

                        artist =
                            if (
                                artist.isBlank()
                            )
                                "Music Finder"
                            else
                                artist,

                        site =
                            getSiteName(
                                pageUrl
                            ),

                        cover = cover
                    )

                addSong(song)
            }
        }
    }

    private fun processResultPages(
        items: List<String>,
        index: Int,
        generation: Int
    ) {

        if (
            generation !=
            searchGeneration
        ) {
            return
        }

        if (
            index >= items.size
        ) {

            status.text =
                if (
                    songs.isEmpty()
                )
                    "آهنگ قابل پخش پیدا نشد"
                else
                    "${songs.size} نتیجه پیدا شد"

            if (
                songs.isNotEmpty() &&
                currentIndex == -1
            ) {

                currentIndex = 0

                playSong(
                    songs[0]
                )
            }

            saveResults()

            return
        }

        val item =
            items[index]

        val url =
            item.substringBefore(
                "|||"
            )

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

        val handler =
            Handler(
                mainLooper
            )

        var finished =
            false

        val timeout =
            Runnable {

                if (!finished) {

                    finished = true

                    processResultPages(
                        items,
                        index + 1,
                        generation
                    )
                }
            }

        handler.postDelayed(
            timeout,
            5000
        )

        web.loadUrl(
            url
        )

        handler.postDelayed(
            {

                if (!finished) {

                    finished = true

                    processResultPages(
                        items,
                        index + 1,
                        generation
                    )
                }

            },
            1200
        )
    }

    private fun addSong(
        song: SongResult
    ) {

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

        saveResults()
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.VERTICAL

        row.setPadding(
            18,
            14,
            18,
            14
        )

        row.setBackgroundColor(
            Color.rgb(
                24,
                24,
                31
            )
        )

        val title =
            TextView(this)

        title.text =
            "${index + 1}. ${song.title}"

        title.textSize =
            15f

        title.setTextColor(
            Color.WHITE
        )

        val info =
            TextView(this)

        info.text =
            "${song.artist}  •  ${song.site}"

        info.textSize =
            12f

        info.setTextColor(
            Color.rgb(
                170,
                170,
                170
            )
        )

        row.addView(
            title
        )

        row.addView(
            info
        )

        row.setOnClickListener {

            currentIndex =
                songs.indexOf(
                    song
                )

            playSong(
                song
            )
        }

        resultsContainer.addView(
            row
        )
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
    }

    private fun decode(
        value: String
    ): String {

        return try {

            java.net.URLDecoder.decode(
                value,
                "UTF-8"
            )

        } catch (
            _: Exception
        ) {

            value
        }
    }

    private fun playSong(
        song: SongResult
    ) {

        currentSong =
            song

        currentAudioUrl =
            song.url

        titleText.text =
            song.title

        artistText.text =
            "${song.artist}  •  ${song.site}"

        status.text =
            "در حال پخش..."

        saveButton.text =
            if (
                LibraryManager.contains(
                    this,
                    song
                )
            )
                "✓ ذخیره شد"
            else
                "♡ ذخیره"

        if (
            song.cover.isNotBlank()
        ) {

            thread {

                try {

                    val connection =
                        URL(
                            song.cover
                        )
                            .openConnection()
                            as HttpURLConnection

                    connection.connectTimeout =
                        10000

                    connection.readTimeout =
                        10000

                    connection.connect()

                    val bitmap =
                        BitmapFactory.decodeStream(
                            connection.inputStream
                        )

                    connection.disconnect()

                    if (
                        bitmap != null
                    ) {

                        runOnUiThread {

                            vinyl.setCover(
                                bitmap
                            )
                        }
                    }

                } catch (
                    _: Exception
                ) {
                }
            }
        }

        sendService(
            MusicService.ACTION_PLAY,
            song
        )
    }

    private fun nextSong() {

        if (
            songs.isEmpty()
        ) {
            return
        }

        currentIndex =
            if (
                randomMode
            ) {

                if (
                    songs.size == 1
                )
                    0
                else {

                    var next: Int

                    do {

                        next =
                            (
                                0 until
                                        songs.size
                                )
                                .random()

                    } while (
                        next ==
                        currentIndex
                    )

                    next
                }

            } else {

                (
                    currentIndex + 1
                ) %
                        songs.size
            }

        playSong(
            songs[currentIndex]
        )
    }

    private fun previousSong() {

        if (
            songs.isEmpty()
        ) {
            return
        }

        currentIndex =
            if (
                currentIndex <= 0
            )
                songs.size - 1
            else
                currentIndex - 1

        playSong(
            songs[currentIndex]
        )
    }

    private fun sendService(
        action: String,
        song: SongResult?
    ) {

        if (
            song == null
        ) {
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
                    song.url
                )

                putExtra(
                    MusicService.EXTRA_TITLE,
                    song.title
                )

                putExtra(
                    MusicService.EXTRA_ARTIST,
                    song.artist
                )

                putExtra(
                    MusicService.EXTRA_COVER,
                    song.cover
                )
            }

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
    }

    private fun sendSeek(
        percent: Int
    ) {

        val intent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                action =
                    MusicService.ACTION_SEEK_PERCENT

                putExtra(
                    MusicService.EXTRA_PERCENT,
                    percent
                )
            }

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
    }

    private fun formatTime(
        millis: Long
    ): String {

        if (
            millis <= 0
        ) {
            return "00:00"
        }

        val totalSeconds =
            millis / 1000

        val minutes =
            totalSeconds / 60

        val seconds =
            totalSeconds % 60

        return String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    private fun downloadCurrentSong() {

        val song =
            currentSong

        if (
            song == null ||
            song.url.isBlank()
        ) {

            Toast.makeText(
                this,
                "اول یک آهنگ پخش کنید",
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

        cancelRequested =
            false

        pauseDownloadRequested =
            false

        val name =
            makeSafeFileName(
                song.title
            ) + ".mp3"

        downloadProgress.progress =
            0

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
            "Ⅱ"

        downloadText.text =
            "0%"

        status.text =
            "در حال دانلود..."

        downloadThread =
            thread {

                try {

                    if (
                        Build.VERSION.SDK_INT >= 29
                    ) {

                        downloadMediaStore(
                            song.url,
                            name
                        )

                    } else {

                        downloadOld(
                            song.url,
                            name
                        )
                    }

                    runOnUiThread {

                        downloadProgress.progress =
                            100

                        downloadText.text =
                            "100%"

                        status.text =
                            "دانلود کامل شد ✓"

                        downloadButton.isEnabled =
                            true

                        pauseDownloadButton.visibility =
                            View.GONE

                        cancelDownloadButton.visibility =
                            View.GONE
                    }

                } catch (
                    e: Exception
                ) {

                    runOnUiThread {

                        if (
                            e.message ==
                            "CANCELLED"
                        ) {

                            status.text =
                                "دانلود لغو شد"

                        } else {

                            status.text =
                                "دانلود ناموفق بود"
                        }

                        downloadButton.isEnabled =
                            true

                        pauseDownloadButton.visibility =
                            View.GONE

                        cancelDownloadButton.visibility =
                            View.GONE
                    }
                }
            }
    }

    private fun toggleDownloadPause() {

        pauseDownloadRequested =
            !pauseDownloadRequested

        pauseDownloadButton.text =
            if (
                pauseDownloadRequested
            )
                "▶"
            else
                "Ⅱ"

        status.text =
            if (
                pauseDownloadRequested
            )
                "دانلود متوقف شده"
            else
                "در حال دانلود..."
    }

    private fun cancelDownload() {

        cancelRequested =
            true

        pauseDownloadRequested =
            false

        downloadText.text =
            "در حال لغو..."

        status.text =
            "در حال لغو دانلود..."
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

        try {

            val connection =
                URL(urlString)
                    .openConnection()
                    as HttpURLConnection

            connection.connectTimeout =
                15000

            connection.readTimeout =
                30000

            connection.connect()

            val total =
                connection.contentLengthLong

            var downloaded =
                0L

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->

                        val buffer =
                            ByteArray(
                                8192
                            )

                        while (true) {

                            if (
                                cancelRequested
                            ) {

                                throw Exception(
                                    "CANCELLED"
                                )
                            }

                            while (
                                pauseDownloadRequested &&
                                !cancelRequested
                            ) {

                                Thread.sleep(
                                    200
                                )
                            }

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
                                total > 0
                            ) {

                                val percent =
                                    (
                                        downloaded.toDouble() /
                                                total.toDouble() *
                                                100
                                        )
                                        .toInt()

                                runOnUiThread {

                                    downloadProgress.progress =
                                        percent

                                    downloadText.text =
                                        "$percent%"
                                }
                            }
                        }
                    }
            }

            connection.disconnect()

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

        } catch (
            e: Exception
        ) {

            contentResolver.delete(
                uri,
                null,
                null
            )

            throw e
        }
    }

    private fun downloadOld(
        urlString: String,
        fileName: String
    ) {

        if (
            checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            runOnUiThread {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    501
                )
            }

            throw Exception(
                "PERMISSION"
            )
        }

        val directory =
            Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC
                )

        if (
            !directory.exists()
        ) {

            directory.mkdirs()
        }

        val file =
            File(
                directory,
                fileName
            )

        val connection =
            URL(urlString)
                .openConnection()
                as HttpURLConnection

        connection.connect()

        val total =
            connection.contentLengthLong

        var downloaded =
            0L

        BufferedInputStream(
            connection.inputStream
        ).use { input ->

            FileOutputStream(
                file
            ).use { output ->

                val buffer =
                    ByteArray(
                        8192
                    )

                while (true) {

                    if (
                        cancelRequested
                    ) {

                        file.delete()

                        throw Exception(
                            "CANCELLED"
                        )
                    }

                    while (
                        pauseDownloadRequested &&
                        !cancelRequested
                    ) {

                        Thread.sleep(
                            200
                        )
                    }

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
                        total > 0
                    ) {

                        val percent =
                            (
                                downloaded.toDouble() /
                                        total.toDouble() *
                                        100
                                )
                                .toInt()

                        runOnUiThread {

                            downloadProgress.progress =
                                percent

                            downloadText.text =
                                "$percent%"
                        }
                    }
                }
            }
        }

        connection.disconnect()
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

        return name.take(
            100
        )
    }

    private fun saveResults() {

        val prefs =
            getSharedPreferences(
                "music_finder_results",
                MODE_PRIVATE
            )

        val data =
            songs.joinToString(
                "|||SONG|||"
            ) {

                listOf(
                    it.url,
                    it.title,
                    it.artist,
                    it.site,
                    it.cover
                )
                    .joinToString(
                        "|||FIELD|||"
                    )
            }

        prefs.edit()
            .putString(
                "songs",
                data
            )
            .apply()
    }

    private fun restoreResults() {

        val prefs =
            getSharedPreferences(
                "music_finder_results",
                MODE_PRIVATE
            )

        val data =
            prefs.getString(
                "songs",
                ""
            )
                ?: ""

        if (
            data.isBlank()
        ) {
            return
        }

        data.split(
            "|||SONG|||"
        )
            .forEach { item ->

                val p =
                    item.split(
                        "|||FIELD|||"
                    )

                if (
                    p.size >= 5
                ) {

                    val song =
                        SongResult(
                            url = p[0],
                            title = p[1],
                            artist = p[2],
                            site = p[3],
                            cover = p[4]
                        )

                    if (
                        songs.none {
                            it.url ==
                            song.url
                        }
                    ) {

                        songs.add(
                            song
                        )
                    }
                }
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

            currentIndex =
                0
        }
    }

    override fun onResume() {

        super.onResume()

        sendServiceGetPosition()
    }

    private fun sendServiceGetPosition() {

        try {

            val intent =
                Intent(
                    this,
                    MusicService::class.java
                ).apply {

                    action =
                        MusicService.ACTION_GET_POSITION
                }

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

        } catch (
            _: Exception
        ) {
        }
    }

    override fun onDestroy() {

        try {

            unregisterReceiver(
                receiver
            )

        } catch (
            _: Exception
        ) {
        }

        web.destroy()

        executor.shutdownNow()

        super.onDestroy()
    }
}
