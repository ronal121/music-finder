package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
    private lateinit var durationText: TextView
    private lateinit var loading: ProgressBar

    private val results = ArrayList<SearchResult>()
    private var currentIndex = -1

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
        durationText = findViewById(R.id.duration)
        loading = findViewById(R.id.loading)

        setupWebView()
        setupControls()

        web.loadUrl("https://www.google.com/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

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

                if (url.contains("google.com/search")) {
                    inspectGoogleResults()
                } else if (!url.contains("google.com")) {
                    inspectMusicPage()
                }
            }
        }
    }

    private fun setupControls() {

        findViewById<TextView>(R.id.search).setOnClickListener {
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

                    if (!fromUser) return

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

        val text = query.text.toString().trim()

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
        artistText.text = "در حال جستجو..."

        currentTime.text = "0:00"
        durationText.text = "0:00"
        seekBar.progress = 0
        playButton.text = "▶"

        loading.visibility = View.VISIBLE

        status.text =
            "در حال جستجوی منابع موسیقی..."

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

    private fun inspectGoogleResults() {

        val script = """
            (function() {

                var links =
                    document.querySelectorAll('a');

                var found = [];

                for (var i = 0; i < links.length; i++) {

                    var href =
                        links[i].href || '';

                    var text =
                        links[i].innerText || '';

                    if (
                        href.indexOf('rozmusic.com') !== -1 ||
                        href.indexOf('mybia2music.com') !== -1 ||
                        href.indexOf('musicdel.ir') !== -1 ||
                        href.indexOf('musics-fa.com') !== -1
                    ) {

                        found.push(
                            href + '|||' + text
                        );
                    }
                }

                if (found.length > 0) {

                    MusicFinder.results(
                        found.join('###')
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

    private fun inspectMusicPage() {

        val script = """
            (function() {

                var media =
                    document.querySelector('audio,video');

                var title =
                    document.title || '';

                var text =
                    document.body.innerText || '';

                if (media) {

                    MusicFinder.found(
                        media.currentSrc ||
                        media.src ||
                        '',
                        title,
                        text.substring(0, 1500)
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
                            text.substring(0, 1500)
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

    private fun openResult(
        result: SearchResult
    ) {

        loading.visibility = View.VISIBLE

        status.text =
            "در حال آماده‌سازی آهنگ..."

        web.loadUrl(result.url)
    }

    private fun startSong(
        result: SearchResult
    ) {

        titleText.text =
            result.title

        artistText.text =
            result.artist

        status.text =
            "در حال پخش"

        playButton.text =
            "❚❚"

        loading.visibility =
            View.GONE

        val script = """
            (function() {

                var media =
                    document.querySelector('audio,video');

                if (!media) {
                    MusicFinder.noPlayer();
                    return;
                }

                media.play();

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
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

        if (results.isEmpty()) return

        if (currentIndex > 0) {

            currentIndex--

            openResult(
                results[currentIndex]
            )
        }
    }

    private fun nextSong() {

        if (results.isEmpty()) return

        if (currentIndex < results.size - 1) {

            currentIndex++

            openResult(
                results[currentIndex]
            )

        } else {

            status.text =
                "آهنگ بعدی پیدا نشد"
        }
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

        val secs =
            total % 60

        return String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            secs
        )
    }

    private fun extractTitle(
        title: String
    ): String {

        if (title.isBlank()) {
            return query.text.toString()
        }

        return title
            .replace(
                Regex("\\s*[-|].*$"),
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

        val knownArtists =
            listOf(
                "محسن چاوشی",
                "چاوشی"
            )

        for (artist in knownArtists) {

            if (
                source.contains(
                    artist,
                    ignoreCase = true
                )
            ) {
                return artist
            }
        }

        return "Music Finder"
    }

    inner class MusicBridge {

        @JavascriptInterface
        fun results(data: String) {

            runOnUiThread {

                val items =
                    data.split("###")

                if (items.isEmpty()) {
                    notFound()
                    return@runOnUiThread
                }

                val first =
                    items.firstOrNull()

                if (first.isNullOrBlank()) {
                    notFound()
                    return@runOnUiThread
                }

                val parts =
                    first.split("|||")

                val url =
                    parts.getOrNull(0)
                        ?.trim()
                        ?: ""

                if (url.isBlank()) {
                    notFound()
                    return@runOnUiThread
                }

                val result =
                    SearchResult(
                        title =
                            query.text.toString(),
                        artist =
                            "در حال پخش...",
                        url = url
                    )

                results.add(result)

                currentIndex = 0

                openResult(result)
            }
        }

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

                val result =
                    SearchResult(
                        title =
                            extractTitle(pageTitle),
                        artist =
                            extractArtist(
                                pageTitle,
                                pageText
                            ),
                        url =
                            web.url ?: ""
                    )

                if (results.isEmpty()) {
                    results.add(result)
                    currentIndex = 0
                }

                startSong(result)
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
                    "پلیر این صفحه پیدا نشد"
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

                durationText.text =
                    formatTime(total)

                if (total > 0) {

                    seekBar.progress =
                        (
                            current /
                                total *
                                100
                            )
                            .toInt()
                            .coerceIn(0, 100)
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

    private fun notFound() {

        loading.visibility =
            View.GONE

        status.text =
            "نتیجه‌ای پیدا نشد"
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
