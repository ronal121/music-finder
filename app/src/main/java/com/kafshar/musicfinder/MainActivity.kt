package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var searchButton: TextView

    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var status: TextView

    private lateinit var playButton: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var downloadButton: TextView

    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationText: TextView
    private lateinit var loading: ProgressBar

    private lateinit var player: ExoPlayer

    private val siteResults = ArrayList<String>()

    private var siteIndex = 0
    private var currentAudioUrl = ""

    private var searching = false
    private var tryingNextSite = false

    private val sites = arrayOf(
        "rozmusic.com",
        "mybia2music.com",
        "musicdel.ir",
        "musics-fa.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        query = findViewById(R.id.query)
        searchButton = findViewById(R.id.search)

        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)
        status = findViewById(R.id.status)

        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        downloadButton = findViewById(R.id.downloadButton)

        progress = findViewById(R.id.progress)
        currentTime = findViewById(R.id.currentTime)
        durationText = findViewById(R.id.duration)
        loading = findViewById(R.id.loading)

        web = findViewById(R.id.web)

        setupPlayer()
        setupWebView()
        setupControls()
    }

    private fun setupPlayer() {

        player = ExoPlayer.Builder(this).build()

        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {

                playButton.text =
                    if (isPlaying) "❚❚" else "▶"
            }

            override fun onPlaybackStateChanged(state: Int) {

                when (state) {

                    Player.STATE_BUFFERING -> {
                        loading.visibility = View.VISIBLE
                        status.text = "در حال بارگذاری..."
                    }

                    Player.STATE_READY -> {
                        loading.visibility = View.GONE
                        status.text = "در حال پخش"
                        updateProgress()
                    }

                    Player.STATE_ENDED -> {

                        loading.visibility = View.GONE

                        playNextResult()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {

                loading.visibility = View.GONE

                status.text = "این لینک قابل پخش نیست؛ در حال امتحان گزینه بعدی..."

                playNextResult()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        web.visibility = View.INVISIBLE

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            cacheMode = WebSettings.LOAD_DEFAULT

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

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {

                if (url.contains("google.com/search")) {

                    extractSearchResults()

                } else {

                    extractAudioLinks()
                }
            }
        }
    }

    private fun setupControls() {

        searchButton.setOnClickListener {
            searchMusic()
        }

        query.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEARCH) {

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

            if (siteResults.isNotEmpty() && siteIndex > 0) {

                siteIndex--

                loadResult(siteResults[siteIndex])
            }
        }

        nextButton.setOnClickListener {

            playNextResult()
        }

        downloadButton.setOnClickListener {

            downloadCurrentSong()
        }

        progress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    bar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {

                    if (!fromUser) return

                    val duration = player.duration

                    if (duration > 0) {

                        player.seekTo(
                            duration * value / 100L
                        )
                    }
                }

                override fun onStartTrackingTouch(
                    bar: SeekBar?
                ) {}

                override fun onStopTrackingTouch(
                    bar: SeekBar?
                ) {}
            }
        )
    }

    private fun searchMusic() {

        val text = query.text.toString().trim()

        if (text.isEmpty()) {

            Toast.makeText(
                this,
                "نام آهنگ را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        player.stop()

        siteResults.clear()
        siteIndex = 0
        currentAudioUrl = ""

        searching = true
        tryingNextSite = false

        titleText.text = text
        artistText.text = "Music Finder"

        status.text = "در حال جستجوی آهنگ..."

        loading.visibility = View.VISIBLE

        val q =
            "\"$text\" " +
                    "(site:rozmusic.com OR " +
                    "site:mybia2music.com OR " +
                    "site:musicdel.ir OR " +
                    "site:musics-fa.com)"

        val encoded =
            URLEncoder.encode(q, "UTF-8")

        web.loadUrl(
            "https://www.google.com/search?q=$encoded"
        )
    }

    private fun extractSearchResults() {

        val js = """
            (function() {

                var links =
                    document.querySelectorAll("a");

                var result = [];

                for (
                    var i = 0;
                    i < links.length;
                    i++
                ) {

                    var href =
                        links[i].href || "";

                    if (
                        href.indexOf("rozmusic.com") >= 0 ||
                        href.indexOf("mybia2music.com") >= 0 ||
                        href.indexOf("musicdel.ir") >= 0 ||
                        href.indexOf("musics-fa.com") >= 0
                    ) {

                        if (
                            href.indexOf("/search") < 0 &&
                            result.indexOf(href) < 0
                        ) {

                            result.push(href);
                        }
                    }
                }

                if (result.length > 0) {

                    MusicFinder.results(
                        result.join("###")
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(js, null)
    }

    private fun loadResult(url: String) {

        tryingNextSite = true

        loading.visibility = View.VISIBLE

        status.text =
            "در حال پیدا کردن فایل آهنگ..."

        web.loadUrl(url)
    }

    private fun extractAudioLinks() {

        val js = """
            (function() {

                var found = [];

                var audio =
                    document.querySelectorAll(
                        "audio, audio source, video source"
                    );

                for (
                    var i = 0;
                    i < audio.length;
                    i++
                ) {

                    var src =
                        audio[i].src ||
                        audio[i].getAttribute("src") ||
                        "";

                    if (src) {
                        found.push(src);
                    }
                }

                var links =
                    document.querySelectorAll("a");

                for (
                    var j = 0;
                    j < links.length;
                    j++
                ) {

                    var href =
                        links[j].href || "";

                    var x =
                        href.toLowerCase();

                    if (
                        x.indexOf(".mp3") >= 0 ||
                        x.indexOf(".m4a") >= 0 ||
                        x.indexOf(".aac") >= 0 ||
                        x.indexOf(".ogg") >= 0 ||
                        x.indexOf(".wav") >= 0
                    ) {

                        found.push(href);
                    }
                }

                var unique =
                    [];

                for (
                    var k = 0;
                    k < found.length;
                    k++
                ) {

                    if (
                        found[k] &&
                        unique.indexOf(found[k]) < 0
                    ) {

                        unique.push(found[k]);
                    }
                }

                if (unique.length > 0) {

                    MusicFinder.audio(
                        unique.join("###")
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(js, null)
    }

    private fun playNextResult() {

        if (siteResults.isEmpty()) {

            searchMusic()

            return
        }

        if (siteIndex + 1 < siteResults.size) {

            siteIndex++

            loadResult(
                siteResults[siteIndex]
            )

        } else {

            loading.visibility = View.GONE

            status.text =
                "آهنگ قابل پخش دیگری پیدا نشد"
        }
    }

    private fun playAudio(url: String) {

        if (url.isBlank()) {

            playNextResult()

            return
        }

        currentAudioUrl = url

        loading.visibility = View.VISIBLE

        status.text = "در حال پخش..."

        try {

            player.stop()

            player.setMediaItem(
                MediaItem.fromUri(url)
            )

            player.prepare()
            player.play()

        } catch (e: Exception) {

            playNextResult()
        }
    }

    private fun updateProgress() {

        if (isFinishing) return

        val duration = player.duration

        if (duration <= 0) return

        val position =
            player.currentPosition

        val value =
            (
                    position.toDouble() /
                            duration.toDouble() *
                            100
                    ).toInt()

        progress.progress = value

        currentTime.text =
            formatTime(position)

        durationText.text =
            formatTime(duration)

        progress.postDelayed(
            {
                updateProgress()
            },
            500
        )
    }

    private fun formatTime(ms: Long): String {

        if (ms <= 0) return "0:00"

        val seconds = ms / 1000

        val minutes = seconds / 60

        val sec = seconds % 60

        return String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            sec
        )
    }

    private fun downloadCurrentSong() {

        val url = currentAudioUrl

        if (url.isBlank()) {

            Toast.makeText(
                this,
                "اول آهنگ را پخش کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        downloadButton.text = "..."

        Thread {

            try {

                val fileName =
                    safeName(
                        query.text.toString()
                    ) + ".mp3"

                if (Build.VERSION.SDK_INT >= 29) {

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
                            ?: throw Exception()

                    try {

                        val connection =
                            URL(url)
                                .openConnection()
                                    as HttpURLConnection

                        connection.connectTimeout = 15000
                        connection.readTimeout = 30000
                        connection.connect()

                        val input =
                            BufferedInputStream(
                                connection.inputStream
                            )

                        val output =
                            contentResolver.openOutputStream(uri)
                                ?: throw Exception()

                        input.use { inputStream ->

                            output.use { outputStream ->

                                val buffer =
                                    ByteArray(8192)

                                var count: Int

                                while (
                                    inputStream.read(buffer)
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

                        val done =
                            ContentValues().apply {

                                put(
                                    MediaStore.Audio.Media.IS_PENDING,
                                    0
                                )
                            }

                        contentResolver.update(
                            uri,
                            done,
                            null,
                            null
                        )

                    } catch (e: Exception) {

                        contentResolver.delete(
                            uri,
                            null,
                            null
                        )

                        throw e
                    }

                } else {

                    val dir =
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC
                        )

                    if (!dir.exists()) {
                        dir.mkdirs()
                    }

                    val file =
                        File(dir, fileName)

                    val connection =
                        URL(url)
                            .openConnection()
                                as HttpURLConnection

                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()

                    val input =
                        BufferedInputStream(
                            connection.inputStream
                        )

                    val output =
                        FileOutputStream(file)

                    input.use { i ->

                        output.use { o ->

                            val buffer =
                                ByteArray(8192)

                            var count: Int

                            while (
                                i.read(buffer)
                                    .also {
                                        count = it
                                    } != -1
                            ) {

                                o.write(
                                    buffer,
                                    0,
                                    count
                                )
                            }
                        }
                    }

                    connection.disconnect()
                }

                runOnUiThread {

                    downloadButton.text = "⬇"

                    status.text =
                        "آهنگ در Music ذخیره شد"

                    Toast.makeText(
                        this,
                        "دانلود شد ✓",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    downloadButton.text = "⬇"

                    status.text =
                        "دانلود ناموفق بود"

                    Toast.makeText(
                        this,
                        "دانلود انجام نشد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }.start()
    }

    private fun safeName(text: String): String {

        val clean =
            text.replace(
                Regex("[\\\\/:*?\"<>|]"),
                "_"
            ).trim()

        return if (clean.isEmpty()) {
            "Music_Finder"
        } else {
            clean.take(80)
        }
    }

    inner class Bridge {

        @JavascriptInterface
        fun results(data: String) {

            runOnUiThread {

                val list =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .distinct()

                siteResults.clear()
                siteResults.addAll(list)

                if (siteResults.isEmpty()) {

                    notFound()

                    return@runOnUiThread
                }

                siteIndex = 0

                status.text =
                    "نتیجه پیدا شد؛ در حال آماده‌سازی پخش..."

                loadResult(
                    siteResults[0]
                )
            }
        }

        @JavascriptInterface
        fun audio(data: String) {

            runOnUiThread {

                val list =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .distinct()

                if (list.isEmpty()) {

                    playNextResult()

                    return@runOnUiThread
                }

                titleText.text =
                    query.text.toString()

                artistText.text =
                    "Music Finder"

                playAudio(list[0])
            }
        }

        @JavascriptInterface
        fun notFound() {

            runOnUiThread {

                loading.visibility = View.GONE

                if (
                    siteIndex + 1 <
                    siteResults.size
                ) {

                    siteIndex++

                    loadResult(
                        siteResults[siteIndex]
                    )

                } else {

                    status.text =
                        "برای این آهنگ فایل قابل پخش پیدا نشد"
                }
            }
        }
    }

    override fun onDestroy() {

        if (::player.isInitialized) {
            player.release()
        }

        if (::web.isInitialized) {
            web.destroy()
        }

        super.onDestroy()
    }
}
