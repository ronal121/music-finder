package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView

    private lateinit var titleText: TextView
    private lateinit var artistText: TextView

    private lateinit var playButton: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var downloadButton: TextView

    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationText: TextView
    private lateinit var loading: ProgressBar

    private lateinit var player: ExoPlayer

    private val results = ArrayList<String>()

    private var currentIndex = -1

    // لینک مستقیم آهنگ در حال پخش
    private var currentAudioUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        query = findViewById(R.id.query)
        status = findViewById(R.id.status)

        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)

        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        downloadButton = findViewById(R.id.downloadButton)

        seekBar = findViewById(R.id.progress)
        currentTime = findViewById(R.id.currentTime)
        durationText = findViewById(R.id.duration)
        loading = findViewById(R.id.loading)

        // WebView فقط پشت صحنه استفاده می‌شود
        web.visibility = View.GONE

        setupPlayer()
        setupWebView()
        setupControls()
    }

    private fun setupPlayer() {

        player = ExoPlayer.Builder(this).build()

        player.addListener(
            object : Player.Listener {

                override fun onIsPlayingChanged(
                    isPlaying: Boolean
                ) {

                    playButton.text =
                        if (isPlaying) "❚❚" else "▶"

                    if (isPlaying) {
                        status.text = "در حال پخش"
                    }
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {

                    when (playbackState) {

                        Player.STATE_BUFFERING -> {

                            loading.visibility = View.VISIBLE
                            status.text = "در حال بارگذاری آهنگ..."
                        }

                        Player.STATE_READY -> {

                            loading.visibility = View.GONE
                            updateDuration()
                        }

                        Player.STATE_ENDED -> {

                            loading.visibility = View.GONE

                            // آهنگ بعدی خودکار
                            nextSong()
                        }
                    }
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) " +
                        "AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) " +
                        "Chrome/128 Mobile Safari/537.36"
        }

        web.addJavascriptInterface(
            MusicBridge(),
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

                    if (url.contains("google.com/search")) {

                        extractGoogleResults()

                    } else if (
                        url.contains("rozmusic.com")
                    ) {

                        extractRozMusic()

                    } else if (
                        url.contains("mybia2music.com") ||
                        url.contains("musicdel.ir") ||
                        url.contains("musics-fa.com")
                    ) {

                        extractGenericMusicSite()
                    }
                }
            }
    }

    private fun setupControls() {

        findViewById<TextView>(
            R.id.search
        ).setOnClickListener {

            searchMusic()
        }

        query.setOnEditorActionListener {
                _, actionId, _ ->

            if (
                actionId == EditorInfo.IME_ACTION_SEARCH
            ) {

                searchMusic()
                true

            } else {

                false
            }
        }

        playButton.setOnClickListener {

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        previousButton.setOnClickListener {

            previousSong()
        }

        nextButton.setOnClickListener {

            nextSong()
        }

        // دکمه دانلود
        downloadButton.setOnClickListener {

            downloadCurrentSong()
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (!fromUser) return

                    val duration = player.duration

                    if (duration > 0) {

                        val position =
                            duration * progress / 100

                        player.seekTo(position)
                    }
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

        player.stop()

        results.clear()
        currentIndex = -1
        currentAudioUrl = ""

        titleText.text = text
        artistText.text = "در حال جستجو..."

        status.text =
            "در حال پیدا کردن آهنگ..."

        loading.visibility =
            View.VISIBLE

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

        val url =
            "https://www.google.com/search?q=$encoded"

        // Google برای کاربر نمایش داده نمی‌شود
        web.loadUrl(url)
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

                    if (
                        href.indexOf("rozmusic.com") !== -1 ||
                        href.indexOf("mybia2music.com") !== -1 ||
                        href.indexOf("musicdel.ir") !== -1 ||
                        href.indexOf("musics-fa.com") !== -1
                    ) {

                        if (
                            found.indexOf(href) === -1
                        ) {

                            found.push(href);
                        }
                    }
                }

                if (found.length > 0) {

                    MusicFinder.results(
                        found.join("###")
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun extractRozMusic() {

        status.text =
            "لینک مستقیم آهنگ پیدا شد..."

        val script = """
            (function() {

                var links =
                    document.querySelectorAll("a");

                var audioLinks = [];

                for (
                    var i = 0;
                    i < links.length;
                    i++
                ) {

                    var href =
                        links[i].href || "";

                    var lower =
                        href.toLowerCase();

                    if (
                        lower.indexOf(".mp3") !== -1 ||
                        lower.indexOf(".m4a") !== -1 ||
                        lower.indexOf(".aac") !== -1 ||
                        lower.indexOf(".ogg") !== -1 ||
                        lower.indexOf(".wav") !== -1 ||
                        lower.indexOf("dl.rozmusic.com") !== -1
                    ) {

                        if (
                            audioLinks.indexOf(href) === -1
                        ) {

                            audioLinks.push(href);
                        }
                    }
                }

                var sources =
                    document.querySelectorAll(
                        "audio source, video source"
                    );

                for (
                    var j = 0;
                    j < sources.length;
                    j++
                ) {

                    var src =
                        sources[j].src || "";

                    if (
                        src.indexOf(".mp3") !== -1 ||
                        src.indexOf(".m4a") !== -1
                    ) {

                        audioLinks.push(src);
                    }
                }

                if (
                    audioLinks.length > 0
                ) {

                    MusicFinder.audio(
                        audioLinks.join("###")
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun extractGenericMusicSite() {

        val script = """
            (function() {

                var audioLinks = [];

                var links =
                    document.querySelectorAll("a");

                for (
                    var i = 0;
                    i < links.length;
                    i++
                ) {

                    var href =
                        links[i].href || "";

                    var lower =
                        href.toLowerCase();

                    if (
                        lower.indexOf(".mp3") !== -1 ||
                        lower.indexOf(".m4a") !== -1 ||
                        lower.indexOf(".aac") !== -1 ||
                        lower.indexOf(".ogg") !== -1 ||
                        lower.indexOf(".wav") !== -1
                    ) {

                        if (
                            audioLinks.indexOf(href) === -1
                        ) {

                            audioLinks.push(href);
                        }
                    }
                }

                var sources =
                    document.querySelectorAll(
                        "audio source, video source"
                    );

                for (
                    var j = 0;
                    j < sources.length;
                    j++
                ) {

                    var src =
                        sources[j].src || "";

                    if (src) {
                        audioLinks.push(src);
                    }
                }

                if (
                    audioLinks.length > 0
                ) {

                    MusicFinder.audio(
                        audioLinks.join("###")
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun playAudio(
        url: String
    ) {

        if (url.isBlank()) {

            loading.visibility =
                View.GONE

            status.text =
                "لینک آهنگ پیدا نشد"

            return
        }

        currentAudioUrl = url

        loading.visibility =
            View.VISIBLE

        status.text =
            "در حال پخش..."

        val mediaItem =
            MediaItem.fromUri(url)

        player.setMediaItem(
            mediaItem
        )

        player.prepare()
        player.play()
    }

    private fun nextSong() {

        if (results.isEmpty()) {
            return
        }

        if (
            currentIndex <
            results.size - 1
        ) {

            currentIndex++

            loadSongPage(
                results[currentIndex]
            )

        } else {

            searchRelatedSong()
        }
    }

    private fun previousSong() {

        if (results.isEmpty()) {
            return
        }

        if (currentIndex > 0) {

            currentIndex--

            loadSongPage(
                results[currentIndex]
            )
        }
    }

    private fun loadSongPage(
        url: String
    ) {

        loading.visibility =
            View.VISIBLE

        status.text =
            "در حال پیدا کردن آهنگ..."

        web.loadUrl(url)
    }

    private fun searchRelatedSong() {

        val current =
            query.text.toString().trim()

        if (current.isEmpty()) {
            return
        }

        val relatedQuery =
            "$current آهنگ جدید"

        val encoded =
            URLEncoder.encode(
                relatedQuery,
                "UTF-8"
            )

        web.loadUrl(
            "https://www.google.com/search?q=$encoded"
        )
    }

    /*
     * دانلود آهنگ فعلی
     */
    private fun downloadCurrentSong() {

        val url =
            currentAudioUrl

        if (url.isBlank()) {

            Toast.makeText(
                this,
                "اول یک آهنگ را پخش کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val fileName =
            makeSafeFileName(
                query.text.toString()
            ) + ".mp3"

        downloadButton.text =
            "..."

        status.text =
            "در حال دانلود..."

        Thread {

            try {

                if (Build.VERSION.SDK_INT >= 29) {

                    downloadWithMediaStore(
                        url,
                        fileName
                    )

                } else {

                    downloadOldAndroid(
                        url,
                        fileName
                    )
                }

                runOnUiThread {

                    downloadButton.text =
                        "✓"

                    status.text =
                        "آهنگ در پوشه Music ذخیره شد"

                    Toast.makeText(
                        this,
                        "دانلود شد ✓",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    downloadButton.text =
                        "⬇"

                    status.text =
                        "دانلود ناموفق بود"

                    Toast.makeText(
                        this,
                        "خطا در دانلود: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }

    /*
     * Android 10 و بالاتر
     *
     * فایل مستقیماً داخل Music ذخیره می‌شود.
     */
    private fun downloadWithMediaStore(
        urlString: String,
        fileName: String
    ) {

        val resolver =
            contentResolver

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
            resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
            )
                ?: throw Exception(
                    "امکان ساخت فایل وجود ندارد"
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

            connection.requestMethod =
                "GET"

            connection.connect()

            if (
                connection.responseCode !in 200..299
            ) {

                throw Exception(
                    "سرور فایل را ارسال نکرد"
                )
            }

            val input =
                BufferedInputStream(
                    connection.inputStream
                )

            val output =
                resolver.openOutputStream(
                    uri
                )
                    ?: throw Exception(
                        "امکان نوشتن فایل وجود ندارد"
                    )

            input.use { inputStream ->
                output.use { outputStream ->

                    val buffer =
                        ByteArray(8192)

                    var count: Int

                    while (
                        inputStream.read(
                            buffer
                        )
                            .also {
                                count = it
                            } != -1
                    ) {

                        outputStream.write(
                            buffer,
                            0,
                            count
                        )
                    }
                }
            }

            connection.disconnect()

            val update =
                ContentValues().apply {

                    put(
                        MediaStore.Audio.Media.IS_PENDING,
                        0
                    )
                }

            resolver.update(
                uri,
                update,
                null,
                null
            )

        } catch (e: Exception) {

            resolver.delete(
                uri,
                null,
                null
            )

            throw e
        }
    }

    /*
     * Android 9 و پایین‌تر
     */
    private fun downloadOldAndroid(
        urlString: String,
        fileName: String
    ) {

        if (
            checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {

            runOnUiThread {

                requestPermissions(
                    arrayOf(
                        android.Manifest.permission
                            .WRITE_EXTERNAL_STORAGE
                    ),
                    100
                )
            }

            throw Exception(
                "اجازه ذخیره‌سازی لازم است"
            )
        }

        val musicDirectory =
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC
            )

        if (!musicDirectory.exists()) {
            musicDirectory.mkdirs()
        }

        val file =
            File(
                musicDirectory,
                fileName
            )

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
            connection.responseCode !in 200..299
        ) {

            throw Exception(
                "دانلود از سرور ناموفق بود"
            )
        }

        val input =
            BufferedInputStream(
                connection.inputStream
            )

        val output =
            FileOutputStream(file)

        input.use { inputStream ->
            output.use { outputStream ->

                val buffer =
                    ByteArray(8192)

                var count: Int

                while (
                    inputStream.read(
                        buffer
                    )
                        .also {
                            count = it
                        } != -1
                ) {

                    outputStream.write(
                        buffer,
                        0,
                        count
                    )
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

        if (name.isEmpty()) {
            name = "Music_Finder_Song"
        }

        name =
            name.replace(
                Regex("[\\\\/:*?\"<>|]"),
                "_"
            )

        return name.take(100)
    }

    private fun updateDuration() {

        val duration =
            player.duration

        if (duration <= 0) {
            return
        }

        durationText.text =
            formatTime(duration)

        seekBar.progress = 0

        updateProgress()
    }

    private fun updateProgress() {

        if (
            player.duration <= 0
        ) {
            return
        }

        val position =
            player.currentPosition

        val duration =
            player.duration

        val progress =
            (
                position.toDouble() /
                        duration.toDouble() *
                        100.0
                ).toInt()

        seekBar.progress =
            progress

        currentTime.text =
            formatTime(position)

        durationText.text =
            formatTime(duration)

        seekBar.postDelayed(
            {
                if (!isFinishing) {
                    updateProgress()
                }
            },
            500
        )
    }

    private fun formatTime(
        millis: Long
    ): String {

        if (millis <= 0) {
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

    inner class MusicBridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

            runOnUiThread {

                results.clear()

                val urls =
                    data
                        .split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .distinct()

                results.addAll(urls)

                if (
                    results.isEmpty()
                ) {

                    notFound()

                    return@runOnUiThread
                }

                currentIndex = 0

                loadSongPage(
                    results[0]
                )
            }
        }

        @JavascriptInterface
        fun audio(
            data: String
        ) {

            runOnUiThread {

                val urls =
                    data
                        .split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .distinct()

                if (
                    urls.isEmpty()
                ) {

                    notFound()

                    return@runOnUiThread
                }

                /*
                 * اولین لینک مستقیم
                 */
                val audioUrl =
                    urls.first()

                currentAudioUrl =
                    audioUrl

                titleText.text =
                    query.text.toString()

                artistText.text =
                    "Music Finder"

                playAudio(
                    audioUrl
                )
            }
        }

        @JavascriptInterface
        fun notFound() {

            runOnUiThread {

                loading.visibility =
                    View.GONE

                status.text =
                    "آهنگ قابل پخش پیدا نشد"
            }
        }
    }

    override fun onDestroy() {

        player.release()
        web.destroy()

        super.onDestroy()
    }

    override fun onBackPressed() {

        super.onBackPressed()
    }
}
