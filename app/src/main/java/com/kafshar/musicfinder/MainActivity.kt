package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
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
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: TextView
    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var duration: TextView
    private lateinit var loading: ProgressBar

    private val handler = Handler(Looper.getMainLooper())

    private var pageHasAudio = false
    private var isPlaying = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            updatePlayerState()
            handler.postDelayed(this, 500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        query = findViewById(R.id.query)
        status = findViewById(R.id.status)
        nowPlaying = findViewById(R.id.nowPlaying)
        playButton = findViewById(R.id.playButton)
        progress = findViewById(R.id.progress)
        currentTime = findViewById(R.id.currentTime)
        duration = findViewById(R.id.duration)
        loading = findViewById(R.id.loading)

        val search = findViewById<Button>(R.id.search)
        val menu = findViewById<TextView>(R.id.menu)
        val filter = findViewById<TextView>(R.id.filter)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
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
                loading.visibility = ProgressBar.GONE

                if (isGooglePage(url)) {
                    status.text = "نتایج پیدا شد؛ در حال انتخاب نتیجه مناسب..."

                    handler.postDelayed({
                        selectFirstMusicResult()
                    }, 1000)
                } else {
                    status.text = "صفحه آهنگ آماده است"

                    handler.postDelayed({
                        detectAudio()
                    }, 1200)
                }
            }
        }

        search.setOnClickListener {
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
            togglePlayPause()
        }

        progress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progressValue: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        seekTo(progressValue)
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

        menu.setOnClickListener {
            Toast.makeText(
                this,
                "Music Finder • KAFSHAR",
                Toast.LENGTH_SHORT
            ).show()
        }

        filter.setOnClickListener {
            Toast.makeText(
                this,
                "RozMusic • Bia2Music • Musicdel • Musics-fa",
                Toast.LENGTH_SHORT
            ).show()
        }

        handler.post(progressUpdater)

        web.loadUrl("https://www.google.com/")
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

        pageHasAudio = false
        isPlaying = false

        playButton.text = "▶"
        nowPlaying.text = text
        currentTime.text = "0:00"
        duration.text = "0:00"
        progress.progress = 0

        loading.visibility = ProgressBar.VISIBLE
        status.text = "در حال جستجوی $text ..."

        val searchQuery =
            "\"$text\" " +
            "(site:rozmusic.com OR " +
            "site:mybia2music.com OR " +
            "site:musicdel.ir OR " +
            "site:musics-fa.com)"

        val encoded =
            URLEncoder.encode(searchQuery, "UTF-8")

        val url =
            "https://www.google.com/search?q=$encoded"

        web.loadUrl(url)
    }

    private fun selectFirstMusicResult() {

        val script = """
            (function() {
                var links = document.querySelectorAll('a');

                for (var i = 0; i < links.length; i++) {

                    var href = links[i].href || '';

                    if (
                        href.indexOf('rozmusic.com') !== -1 ||
                        href.indexOf('mybia2music.com') !== -1 ||
                        href.indexOf('musicdel.ir') !== -1 ||
                        href.indexOf('musics-fa.com') !== -1
                    ) {
                        links[i].click();
                        return;
                    }
                }

                MusicFinder.noResult();
            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    private fun detectAudio() {

        val script = """
            (function() {

                var media =
                    document.querySelectorAll(
                        'audio, video'
                    );

                if (media.length === 0) {
                    MusicFinder.noAudio();
                    return;
                }

                var item = media[0];

                item.controls = false;

                MusicFinder.audioFound(
                    item.currentSrc ||
                    item.src ||
                    ''
                );

            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    private fun togglePlayPause() {

        if (!pageHasAudio) {
            Toast.makeText(
                this,
                "فایل صوتی قابل پخش پیدا نشد",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val script = """
            (function() {

                var media =
                    document.querySelector(
                        'audio, video'
                    );

                if (!media) {
                    return;
                }

                if (media.paused) {
                    media.play();
                } else {
                    media.pause();
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    private fun seekTo(value: Int) {

        val script = """
            (function() {

                var media =
                    document.querySelector(
                        'audio, video'
                    );

                if (media && media.duration) {
                    media.currentTime =
                        media.duration * ($value / 100.0);
                }

            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    private fun updatePlayerState() {

        if (!pageHasAudio) {
            return
        }

        val script = """
            (function() {

                var media =
                    document.querySelector(
                        'audio, video'
                    );

                if (!media) {
                    return;
                }

                var current =
                    media.currentTime || 0;

                var total =
                    media.duration || 0;

                var playing =
                    !media.paused && !media.ended;

                MusicFinder.state(
                    current,
                    total,
                    playing
                );

            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    private fun isGooglePage(url: String): Boolean {
        return url.contains("google.com")
    }

    private fun formatTime(seconds: Double): String {

        if (!seconds.isFinite() || seconds < 0) {
            return "0:00"
        }

        val totalSeconds = seconds.toInt()
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60

        return String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            secs
        )
    }

    inner class MusicBridge {

        @JavascriptInterface
        fun audioFound(url: String) {

            runOnUiThread {

                pageHasAudio = true

                status.text =
                    "✓ فایل صوتی صفحه پیدا شد"

                nowPlaying.text =
                    query.text.toString()

                Toast.makeText(
                    this@MainActivity,
                    "آهنگ آماده پخش است",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun noAudio() {

            runOnUiThread {

                pageHasAudio = false

                status.text =
                    "این صفحه فایل صوتی قابل کنترل پیدا نکرد"

                playButton.text = "▶"
            }
        }

        @JavascriptInterface
        fun noResult() {

            runOnUiThread {

                loading.visibility = ProgressBar.GONE

                status.text =
                    "نتیجه مناسبی از منابع انتخاب‌شده پیدا نشد"
            }
        }

        @JavascriptInterface
        fun state(
            current: Double,
            total: Double,
            playing: Boolean
        ) {

            runOnUiThread {

                isPlaying = playing

                playButton.text =
                    if (playing) "❚❚" else "▶"

                currentTime.text =
                    formatTime(current)

                duration.text =
                    formatTime(total)

                if (total > 0) {

                    val value =
                        ((current / total) * 100)
                            .toInt()
                            .coerceIn(0, 100)

                    progress.progress = value
                }
            }
        }
    }

    override fun onDestroy() {

        handler.removeCallbacks(progressUpdater)

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
}
