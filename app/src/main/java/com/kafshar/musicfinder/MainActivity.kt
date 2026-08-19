package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView

    private lateinit var loading: ProgressBar

    private val results = ArrayList<SearchResult>()

    private var currentIndex = -1
    private var currentTitle = ""
    private var currentArtist = ""

    private var searching = false
    private var pageLoaded = false

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

        seekBar = findViewById(R.id.progress)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.duration)

        loading = findViewById(R.id.loading)

        configureWebView()
        configureButtons()

        web.loadUrl("https://www.google.com/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {

        web.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            mediaPlaybackRequiresUserGesture = false

            builtInZoomControls = false

            displayZoomControls = false

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

        web.webViewClient = object : WebViewClient() {

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

                pageLoaded = true

                if (searching) {

                    searching = false

                    status.text =
                        "در حال بررسی نتیجه..."

                    inspectMusicPage()
                }
            }
        }
    }

    private fun configureButtons() {

        findViewById<TextView>(R.id.search).setOnClickListener {
            searchMusic()
        }

        query.setOnEditorActionListener { _, _, _ ->
            searchMusic()
            true
        }

        playButton.setOnClickListener {
            togglePlay()
        }

        previousButton.setOnClickListener {
            previousSong()
        }

        nextButton.setOnClickListener {
            nextSong()
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (fromUser) {

                        val script = """
                            (function() {
                                var media =
                                    document.querySelector('audio,video');

                                if (media && media.duration) {
                                    media.currentTime =
                                        media.duration * ($progress / 100);
                                }
                            })();
                        """.trimIndent()

                        web.evaluateJavascript(
                            script,
                            null
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

        results.clear()

        currentIndex = -1

        titleText.text = text

        artistText.text =
            "در حال جستجو..."

        currentTime.text = "0:00"

        totalTime.text = "0:00"

        seekBar.progress = 0

        playButton.text = "▶"

        loading.visibility =
            View.VISIBLE

        status.text =
            "در حال جستجوی منابع موسیقی..."

        searching = true

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

        web.loadUrl(url)
    }

    private fun inspectMusicPage() {

        val script = """
            (function() {

                var audio =
                    document.querySelector('audio,video');

                var title =
                    document.title || '';

                var text =
                    document.body.innerText || '';

                if (audio) {

                    MusicFinder.found(
                        audio.currentSrc ||
                        audio.src ||
                        '',
                        title,
                        text.substring(0, 1000)
                    );

                    return;
                }

                var links =
                    document.querySelectorAll('a');

                for (
                    var i = 0;
                    i < links.length;
                    i++
                ) {

                    var href =
                        links[i].href || '';

                    if (
                        href.indexOf('.mp3') !== -1 ||
                        href.indexOf('.m4a') !== -1 ||
                        href.indexOf('.aac') !== -1 ||
                        href.indexOf('.ogg') !== -1 ||
                        href.indexOf('.wav') !== -1
                    ) {

                        MusicFinder.found(
                            href,
                            title,
                            text.substring(0, 1000)
                        );

                        return;
                    }
                }

                MusicFinder.notFound();

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun startSong(
        result: SearchResult
    ) {

        currentTitle =
            result.title

        currentArtist =
            result.artist

        titleText.text =
            currentTitle

        artistText.text =
            currentArtist

        status.text =
            "در حال آماده‌سازی..."

        val script = """
            (function() {

                var media =
                    document.querySelector('audio,video');

                if (!media) {
                    return;
                }

                media.currentTime = 0;

                media.play();

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )

        playButton.text =
            "❚❚"

        status.text =
            "در حال پخش"
    }

    private fun togglePlay() {

        val script = """
            (function() {

                var media =
                    document.querySelector('audio,video');

                if (!media) {
                    MusicFinder.noPlayer();
                    return;
                }

                if (media.paused) {
                    media.play();
                } else {
                    media.pause();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun previousSong() {

        if (results.isEmpty()) {
            return
        }

        if (currentIndex > 0) {

            currentIndex--

            openResult(
                results[currentIndex]
            )
        }
    }

    private fun nextSong() {

        if (results.isEmpty()) {
            return
        }

        if (currentIndex < results.size - 1) {

            currentIndex++

            openResult(
                results[currentIndex]
            )
        }
    }

    private fun openResult(
        result: SearchResult
    ) {

        loading.visibility =
            View.VISIBLE

        status.text =
            "در حال باز کردن آهنگ..."

        searching = false

        web.loadUrl(
            result.url
        )
    }

    private fun addResult(
        result: SearchResult
    ) {

        results.add(result)

        currentIndex =
            results.size - 1

        loading.visibility =
            View.GONE

        startSong(result)
    }

    private fun formatTime(
        seconds: Double
    ): String {

        if (
            seconds.isNaN() ||
            seconds.isInfinite() ||
            seconds < 0
        ) {
            return "0:00"
        }

        val total =
            seconds.toInt()

        val minutes =
            total / 60

        val secondsPart =
            total % 60

        return String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            secondsPart
        )
    }

    inner class MusicBridge {

        @JavascriptInterface
        fun found(
            audioUrl: String,
            pageTitle: String,
            pageText: String
        ) {

            runOnUiThread {

                if (audioUrl.isBlank()) {

                    notFound()

                    return@runOnUiThread
                }

                val artist =
                    extractArtist(
                        pageTitle,
                        pageText
                    )

                val title =
                    extractTitle(
                        pageTitle
                    )

                val result =
                    SearchResult(
                        title = title,
                        artist = artist,
                        url = web.url ?: ""
                    )

                addResult(result)
            }
        }

        @JavascriptInterface
        fun notFound() {

            runOnUiThread {

                loading.visibility =
                    View.GONE

                status.text =
                    "نتیجه قابل پخش پیدا نشد"
            }
        }

        @JavascriptInterface
        fun noPlayer() {

            runOnUiThread {

                playButton.text =
                    "▶"

                status.text =
                    "پلیر این صفحه در دسترس نیست"
            }
        }

        @JavascriptInterface
        fun state(
            current: Double,
            total: Double,
            playing: Boolean
        ) {

            runOnUiThread {

                currentTime.text =
                    formatTime(current)

                totalTime.text =
                    formatTime(total)

                if (total > 0) {

                    seekBar.progress =
                        ((current / total) * 100)
                            .toInt()
                            .coerceIn(
                                0,
                                100
                            )
                }

                playButton.text =
                    if (playing) {
                        "❚❚"
                    } else {
                        "▶"
                    }

                if (
                    total > 0 &&
                    current >= total - 0.5
                ) {

                    nextSong()
                }
            }
        }
    }

    private fun extractTitle(
        title: String
    ): String {

        if (title.isBlank()) {
            return query.text.toString()
        }

        return title
            .replace(
                Regex(
                    "\\s*[-|].*$"
                ),
                ""
            )
            .trim()
    }

    private fun extractArtist(
        title: String,
        text: String
    ): String {

        val source =
            "$title $text"

        val names =
            listOf(
                "محسن چاوشی",
                "چاوشی"
            )

        for (name in names) {

            if (
                source.contains(
                    name,
                    ignoreCase = true
                )
            ) {
                return name
            }
        }

        return "Music Finder"
    }

    override fun onDestroy() {

        web.destroy()

        super.onDestroy()
    }

    override fun onBackPressed() {

        if (web.canGoBack()) {

            web.goBack()

        } else {

            super.onBackPressed()
        }
    }

    data class SearchResult(
        val title: String,
        val artist: String,
        val url: String
    )
}
