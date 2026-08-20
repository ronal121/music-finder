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
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
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

    private val songs =
        ArrayList<SongResult>()

    private var currentIndex = -1
    private var currentAudioUrl = ""

    private var randomMode = false

    private var searchGeneration = 0

    private var downloadThread: Thread? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var pauseDownloadRequested = false

    private val executor =
        Executors.newCachedThreadPool()

    private val playerReceiver =
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

                runOnUiThread {

                    updatePlayerProgress(
                        playing,
                        position,
                        duration
                    )

                    val newTitle =
                        intent.getStringExtra(
                            "title"
                        ) ?: ""

                    val newArtist =
                        intent.getStringExtra(
                            "artist"
                        ) ?: ""

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
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

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

        requestNotificationPermission()

        setupWebView()

        setupButtons()

        restoreSearchResults()

        status.text =
            "نام آهنگ یا خواننده را جستجو کنید"
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
    private fun setupWebView() {

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            mediaPlaybackRequiresUserGesture =
                false

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/128 Mobile Safari/537.36"
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

                    return false
                }

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

                    } else {

                        extractMusicPage(url)
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
                        fromUser
                    ) {

                        val duration =
                            parseTime(
                                durationText.text.toString()
                            )

                        if (
                            duration > 0
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

                    if (
                        Build.VERSION.SDK_INT >= 26
                    ) {

                        startForegroundService(
                            intent
                        )

                    } else {

                        startService(intent)
                    }
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

            startActivity(
                Intent(
                    this,
                    LibraryActivity::class.java
                )
            )
        }
    }

    private fun searchMusic() {

        val text =
            query.text.toString().trim()

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

        titleText.text =
            text

        artistText.text =
            "در حال جستجو..."

        status.text =
            "در حال جستجوی سایت‌ها..."

        seekBar.progress =
            0

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
            URLEncoder.encode(
                searchQuery,
                "UTF-8"
            )

        web.loadUrl(
            "https://www.google.com/search?q=$encoded&num=100"
        )
    }

    private fun extractGoogleResults() {

        val script = """
            (function() {

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

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun extractMusicPage(
        pageUrl: String
    ) {

        val script = """
            (function() {

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

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
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

                if (
                    items.isEmpty()
                ) {

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                status.text =
                    "در حال بررسی نتایج..."

                processResultPages(
                    items.take(100),
                    0,
                    generation
                )
            }
        }

        @JavascriptInterface
        fun page(
            data: String
        ) {

            runOnUiThread {

                val parts =
                    data.split("###")

                if (
                    parts.size < 4
                ) {
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
                                query.text.toString()
                            else
                                cleanTitle(title),

                        artist =
                            if (
                                artist.isBlank()
                            )
                                query.text.toString()
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
            generation != searchGeneration
        ) {
            return
        }

        if (
            index >= items.size
        ) {

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

            return
        }

        val url =
            items[index]
                .substringBefore("|||")

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
            android.os.Handler(
                mainLooper
            )

        var finished = false

        val next =
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
            next,
            2500
        )

        web.loadUrl(url)
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
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

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

        if (
            song.cover.isNotBlank()
        ) {

            executor.execute {

                try {

                    val connection =
                        URL(song.cover)
                            .openConnection()
                                as HttpURLConnection

                    connection.connectTimeout =
                        5000

                    connection.readTimeout =
                        5000

                    connection.connect()

                    val bitmap =
                        BitmapFactory.decodeStream(
                            connection.inputStream
                        )

                    connection.disconnect()

                    runOnUiThread {

                        if (
                            bitmap != null &&
                            !isFinishing
                        ) {

                            cover.setImageBitmap(
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
                    if (
                        LibraryManager.contains(
                            this@MainActivity,
                            song
                        )
                    )
                        "♥"
                    else
                        "♡"

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

            currentIndex =
                songs.indexOf(song)

            playSong(song)
        }

        resultsContainer.addView(
            row
        )
    }

    private fun saveCurrentSong() {

        if (
            currentIndex < 0 ||
            currentIndex >= songs.size
        ) {
            return
        }

        val song =
            songs[currentIndex]

        LibraryManager.add(
            this,
            song
        )

        Toast.makeText(
            this,
            "در کتابخانه ذخیره شد ♥",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun playSong(
        song: SongResult
    ) {

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

        if (
            song.cover.isNotBlank()
        ) {

            vinyl.setCover(
                song.cover
            )

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

        if (
            songs.isEmpty()
        ) {
            return
        }

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

                (
                    currentIndex + 1
                ) % songs.size
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

    private fun sendServiceAction(
        action: String,
        url: String,
        title: String,
        artist: String = "Music Finder",
        cover: String = ""
    ) {

        if (
            url.isBlank()
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

        if (
            Build.VERSION.SDK_INT >= 26
        ) {

            startForegroundService(
                intent
            )

        } else {

            startService(intent)
        }
    }

    private fun updatePlayerProgress(
        playing: Boolean,
        position: Long,
        duration: Long
    ) {

        if (
            duration > 0
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
                formatTime(position)

            durationText.text =
                formatTime(duration)
        }

        playButton.text =
            if (playing)
                "⏸"
            else
                "▶"

        if (playing) {

            vinyl.startRotation()

            status.text =
                "در حال پخش"

        } else {

            vinyl.stopRotation()
        }
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

        if (
            milliseconds <= 0
        ) {
            return "00:00"
        }

        val totalSeconds =
            milliseconds / 1000

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
                    minutes * 60 +
                    seconds
                ) * 1000
            }

        } catch (
            _: Exception
        ) {

            0L
        }
    }

    private fun downloadCurrentSong() {

        val url =
            currentAudioUrl

        if (
            url.isBlank()
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

        val name =
            makeSafeFileName(
                titleText.text.toString()
            ) + ".mp3"

        cancelRequested =
            false

        pauseDownloadRequested =
            false

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
            "⏸"

        downloadText.text =
            "0%"

        status.text =
            "در حال دانلود..."

        downloadThread =
            Thread {

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

                    runOnUiThread {

                        downloadProgress.progress =
                            100

                        downloadText.text =
                            "100%"

                        status.text =
                            "دانلود کامل شد ✓"

                        resetDownloadButtons()
                    }

                } catch (
                    e: Exception
                ) {

                    runOnUiThread {

                        status.text =
                            if (
                                e.message ==
                                "CANCELLED"
                            )
                                "دانلود لغو شد"
                            else
                                "دانلود ناموفق بود"

                        resetDownloadButtons()
                    }
                }
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
            )
                "▶"
            else
                "⏸"

        downloadText.text =
            if (
                pauseDownloadRequested
            )
                "دانلود متوقف شد"
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
    }

    private fun resetDownloadButtons() {

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
                        ByteArray(8192)

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

                            Thread.sleep(200)
                        }

                        val count =
                            input.read(buffer)

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
                                ).toInt()

                            runOnUiThread {

                                downloadProgress.progress =
                                    percent

                                if (
                                    !pauseDownloadRequested
                                ) {

                                    downloadText.text =
                                        "$percent%"
                                }
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
                    ByteArray(8192)

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

                        Thread.sleep(200)
                    }

                    val count =
                        input.read(buffer)

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
                            ).toInt()

                        runOnUiThread {

                            downloadProgress.progress =
                                percent

                            if (
                                !pauseDownloadRequested
                            ) {

                                downloadText.text =
                                    "$percent%"
                            }
                        }
                    }
                }
            }
        }

        connection.disconnect()
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

            URLDecoder.decode(
                value,
                "UTF-8"
            )

        } catch (
            _: Exception
        ) {

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

        return name.take(100)
    }

    private fun saveSearchResults() {

        val prefs =
            getSharedPreferences(
                "search_results",
                MODE_PRIVATE
            )

        val data =
            songs.joinToString(
                separator = "\n"
            ) {

                listOf(
                    it.url,
                    it.title,
                    it.artist,
                    it.site,
                    it.cover
                ).joinToString("|||")
            }

        prefs.edit()
            .putString(
                "songs",
                data
            )
            .apply()
    }

    private fun restoreSearchResults() {

        val prefs =
            getSharedPreferences(
                "search_results",
                MODE_PRIVATE
            )

        val data =
            prefs.getString(
                "songs",
                ""
            ) ?: ""

        if (
            data.isBlank()
        ) {
            return
        }

        songs.clear()

        data.split("\n")
            .forEach {

                val p =
                    it.split("|||")

                if (
                    p.size >= 5
                ) {

                    songs.add(
                        SongResult(
                            url = p[0],
                            title = p[1],
                            artist = p[2],
                            site = p[3],
                            cover = p[4]
                        )
                    )
                }
            }

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

            status.text =
                "${songs.size} نتیجه ذخیره شده"
        }
    }

    override fun onResume() {

        super.onResume()

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

            startService(intent)
        }
    }

    override fun onPause() {

        try {

            unregisterReceiver(
                playerReceiver
            )

        } catch (
            _: Exception
        ) {
        }

        super.onPause()
    }

    override fun onDestroy() {

        web.destroy()

        executor.shutdownNow()

        super.onDestroy()
    }
}
