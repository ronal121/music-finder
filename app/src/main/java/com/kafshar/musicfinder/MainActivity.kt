package com.kafshar.musicfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
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
import java.util.Locale
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

    private lateinit var downloadButton: TextView
    private lateinit var pauseDownloadButton: TextView
    private lateinit var cancelDownloadButton: TextView

    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadText: TextView

    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationText: TextView

    private lateinit var resultsContainer: LinearLayout

    private val songs =
        ArrayList<SongResult>()

    private var currentIndex = -1

    private var currentAudioUrl = ""

    private var randomMode = false

    private var searchGeneration = 0

    private var cancelRequested = false

    @Volatile
    private var pauseDownloadRequested = false

    private var downloadThread: Thread? = null

    private val prefs by lazy {
        getSharedPreferences(
            "music_finder",
            MODE_PRIVATE
        )
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private val playerReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action !=
                    "com.kafshar.musicfinder.PLAYER_UPDATE"
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
                        0
                    )

                val duration =
                    intent.getLongExtra(
                        "duration",
                        0
                    )

                playButton.text =
                    if (playing)
                        "Ⅱ"
                    else
                        "▶"

                if (duration > 0) {

                    progress.progress =
                        (
                            position.toDouble() /
                                    duration.toDouble() *
                                    100
                            ).toInt()

                    currentTime.text =
                        formatTime(position)

                    durationText.text =
                        formatTime(duration)
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
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

        downloadButton =
            findViewById(R.id.downloadButton)

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

        web =
            findViewById(R.id.web)

        registerPlayerReceiver()

        requestNotificationPermission()

        setupWebView()

        setupButtons()

        loadSavedResults()

        status.text =
            if (songs.isEmpty())
                "نام آهنگ یا خواننده را جستجو کنید"
            else
                "${songs.size} نتیجه ذخیره شده"
    }

    private fun registerPlayerReceiver() {

        val filter =
            IntentFilter(
                "com.kafshar.musicfinder.PLAYER_UPDATE"
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
                titleText.text.toString()
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
                    "↕"

        }

        downloadButton.setOnClickListener {

            if (
                downloadThread?.isAlive == true
            ) {
                return@setOnClickListener
            }

            downloadCurrentSong()
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
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {

                    if (!fromUser) {
                        return
                    }

                    sendSeek(
                        value
                    )
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

    private fun searchMusic() {

        val text =
            query.text.toString().trim()

        if (text.isEmpty()) {

            Toast.makeText(
                this,
                "نام آهنگ را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        searchGeneration++

        songs.clear()

        currentIndex = -1

        resultsContainer.removeAllViews()

        titleText.text =
            "در حال جستجو..."

        artistText.text =
            text

        status.text =
            "در حال جستجوی سایت‌ها..."

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
            "https://www.google.com/search?q=$encoded&num=30"
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
                                href
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

                if (!title && h1) {

                    title =
                        h1.innerText || "";
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
                "Music Site"
        }
    }

    inner class Bridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

            runOnUiThread {

                val items =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .distinct()
                        .take(30)

                if (
                    items.isEmpty()
                ) {

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                status.text =
                    "در حال بررسی ${items.size} نتیجه..."

                processResultPages(
                    items,
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
                            cleanTitle(
                                if (
                                    title.isBlank()
                                )
                                    query.text.toString()
                                else
                                    title
                            ),
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

                playSong(
                    songs[0]
                )
            }

            return
        }

        val url =
            items[index]

        web.loadUrl(url)

        /*
         * منتظر سایت قبلی نمی‌مانیم.
         * بعد از حدود ۱.۲ ثانیه نتیجه بعدی بررسی می‌شود.
         */

        handler.postDelayed(
            {

                if (
                    generation ==
                    searchGeneration
                ) {

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

        saveResults()

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
            LinearLayout(this)

        row.orientation =
            LinearLayout.HORIZONTAL

        row.gravity =
            Gravity.CENTER_VERTICAL

        row.setPadding(
            14,
            12,
            14,
            12
        )

        val number =
            TextView(this)

        number.text =
            "${index + 1}"

        number.textSize =
            13f

        number.gravity =
            Gravity.CENTER

        number.setTextColor(
            Color.WHITE
        )

        val numberParams =
            LinearLayout.LayoutParams(
                40,
                40
            )

        row.addView(
            number,
            numberParams
        )

        val textBox =
            LinearLayout(this)

        textBox.orientation =
            LinearLayout.VERTICAL

        val title =
            TextView(this)

        title.text =
            song.title

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
            0xFFAAAAAA.toInt()
        )

        textBox.addView(title)

        textBox.addView(info)

        row.addView(
            textBox,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
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

    private fun playSong(
        song: SongResult
    ) {

        currentAudioUrl =
            song.url

        titleText.text =
            song.title

        artistText.text =
            "${song.artist}  •  ${song.site}"

        status.text =
            "در حال پخش..."

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

                if (songs.size == 1) {

                    0

                } else {

                    var nextIndex: Int

                    do {

                        nextIndex =
                            songs.indices.random()

                    } while (
                        nextIndex ==
                        currentIndex
                    )

                    nextIndex
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

            startService(intent)
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
            return
        }

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
            "Ⅱ"

        downloadText.text =
            "0%"

        status.text =
            "در حال دانلود..."

        val fileName =
            makeSafeFileName(
                titleText.text.toString()
            ) + ".mp3"

        downloadThread =
            thread {

                try {

                    if (
                        Build.VERSION.SDK_INT >= 29
                    ) {

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
                            when (
                                e.message
                            ) {

                                "CANCELLED" ->
                                    "دانلود لغو شد"

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
            downloadThread?.isAlive != true
        ) {
            return
        }

        pauseDownloadRequested =
            !pauseDownloadRequested

        if (
            pauseDownloadRequested
        ) {

            pauseDownloadButton.text =
                "▶"

            status.text =
                "دانلود متوقف شد"

        } else {

            pauseDownloadButton.text =
                "Ⅱ"

            status.text =
                "ادامه دانلود..."
        }
    }

    private fun cancelDownload() {

        cancelRequested =
            true

        pauseDownloadRequested =
            false

        status.text =
            "در حال لغو دانلود..."

        cancelDownloadButton.isEnabled =
            false
    }

    private fun waitIfPaused() {

        while (
            pauseDownloadRequested &&
            !cancelRequested
        ) {

            Thread.sleep(150)
        }

        if (
            cancelRequested
        ) {

            throw Exception(
                "CANCELLED"
            )
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

            if (
                connection.responseCode !in
                200..299
            ) {

                throw Exception(
                    "HTTP_ERROR"
                )
            }

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
                            ByteArray(16384)

                        while (true) {

                            waitIfPaused()

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
                                        ).toInt()

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

        try {

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                FileOutputStream(
                    file
                ).use { output ->

                    val buffer =
                        ByteArray(16384)

                    while (true) {

                        waitIfPaused()

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
                                    ).toInt()

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

        } catch (
            e: Exception
        ) {

            file.delete()

            throw e
        } finally {

            connection.disconnect()
        }
    }

    private fun resetDownloadButtons() {

        downloadButton.isEnabled =
            true

        pauseDownloadButton.visibility =
            View.GONE

        cancelDownloadButton.visibility =
            View.GONE

        downloadProgress.visibility =
            View.GONE

        downloadText.visibility =
            View.GONE

        downloadThread =
            null
    }

    private fun saveResults() {

        val data =
            songs.joinToString("\n") {

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

    private fun loadSavedResults() {

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

        data.split("\n")
            .forEachIndexed {
                    index,
                    line ->

                val p =
                    line.split("|||")

                if (
                    p.size >= 5
                ) {

                    val song =
                        SongResult(
                            p[0],
                            p[1],
                            p[2],
                            p[3],
                            p[4]
                        )

                    songs.add(
                        song
                    )

                    addSongView(
                        song,
                        index
                    )
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

    private fun formatTime(
        millis: Long
    ): String {

        if (
            millis <= 0
        ) {
            return "0:00"
        }

        val totalSeconds =
            millis / 1000

        val minutes =
            totalSeconds / 60

        val seconds =
            totalSeconds % 60

        return String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            seconds
        )
    }

    override fun onDestroy() {

        try {
            unregisterReceiver(
                playerReceiver
            )
        } catch (_: Exception) {
        }

        /*
         * سرویس موسیقی را متوقف نمی‌کنیم.
         */

        web.destroy()

        super.onDestroy()
    }
}
