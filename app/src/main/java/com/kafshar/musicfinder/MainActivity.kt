package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import android.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import java.net.URLDecoder

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: TextView

    private var player: ExoPlayer? = null

    private var currentAudioUrl: String? = null
    private var currentTitle: String = ""

    private var searchStarted = false
    private var candidateIndex = 0

    private val candidateSites = mutableListOf<String>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        query = findViewById(R.id.query)
        web = findViewById(R.id.web)
        status = findViewById(R.id.status)
        nowPlaying = findViewById(R.id.nowPlaying)
        playButton = findViewById(R.id.playButton)

        val search: Button = findViewById(R.id.search)
        val menu: TextView = findViewById(R.id.menu)
        val filter: TextView = findViewById(R.id.filter)

        web.setBackgroundColor(Color.TRANSPARENT)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            databaseEnabled = true
            builtInZoomControls = false
            displayZoomControls = false

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128 Mobile Safari/537.36"
        }

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

                status.text = "در حال بررسی صفحه..."

                if (searchStarted && isGoogleUrl(url)) {

                    findFirstMusicResults()
                    return
                }

                if (searchStarted && !isGoogleUrl(url)) {

                    status.text = "در حال پیدا کردن فایل موسیقی..."

                    detectAudioFromPage()
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {

                val url = request.url.toString()

                if (isAudioUrl(url)) {

                    runOnUiThread {

                        if (currentAudioUrl == null) {

                            currentAudioUrl = url

                            status.text =
                                "فایل موسیقی پیدا شد؛ در حال پخش..."

                            playAudio(
                                url,
                                currentTitle.ifEmpty {
                                    query.text.toString()
                                }
                            )
                        }
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        search.setOnClickListener {

            doSearch()
        }

        query.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEARCH) {

                doSearch()

                true

            } else {

                false
            }
        }

        playButton.setOnClickListener {

            togglePlayback()
        }

        nowPlaying.setOnClickListener {

            togglePlayback()
        }

        menu.setOnClickListener {

            Toast.makeText(
                this,
                "Music Finder • طراحی: KAFSHAR",
                Toast.LENGTH_SHORT
            ).show()
        }

        filter.setOnClickListener {

            Toast.makeText(
                this,
                "انتخاب خودکار بهترین نتیجه موسیقی",
                Toast.LENGTH_SHORT
            ).show()
        }

        web.loadUrl("https://www.google.com/")
    }

    private fun doSearch() {

        val q = query.text.toString().trim()

        if (q.isEmpty()) {

            Toast.makeText(
                this,
                "نام آهنگ را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        player?.stop()

        currentAudioUrl = null
        currentTitle = ""

        searchStarted = true
        candidateIndex = 0
        candidateSites.clear()

        playButton.text = "▶"
        nowPlaying.text = "در حال جستجو..."

        status.text =
            "در حال پیدا کردن بهترین نتیجه موسیقی..."

        val smartQuery =
            "\"$q\" (آهنگ OR موزیک OR music OR song OR mp3)"

        val url =
            "https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(
                        smartQuery,
                        "UTF-8"
                    )

        web.loadUrl(url)
    }

    private fun findFirstMusicResults() {

        web.evaluateJavascript(
            """
            (function() {

                var links = document.querySelectorAll('a');
                var result = [];

                for (var i = 0; i < links.length; i++) {

                    var a = links[i];
                    var href = a.href || "";
                    var text = (a.innerText || "").trim();

                    if (!href || !text) continue;

                    if (!href.startsWith("http")) continue;

                    var bad =
                        href.indexOf("google.com") !== -1 ||
                        href.indexOf("googleusercontent.com") !== -1 ||
                        href.indexOf("gstatic.com") !== -1 ||
                        href.indexOf("youtube.com") !== -1;

                    if (bad) continue;

                    if (
                        text.length > 2 &&
                        (
                            text.toLowerCase().indexOf("mp3") !== -1 ||
                            text.toLowerCase().indexOf("آهنگ") !== -1 ||
                            text.toLowerCase().indexOf("موزیک") !== -1 ||
                            text.toLowerCase().indexOf("چنگیز") !== -1 ||
                            text.toLowerCase().indexOf("music") !== -1 ||
                            text.toLowerCase().indexOf("song") !== -1
                        )
                    ) {

                        result.push(
                            JSON.stringify({
                                url: href,
                                title: text
                            })
                        );
                    }
                }

                return "[" + result.join(",") + "]";

            })();
            """.trimIndent()
        ) { result ->

            try {

                val decoded =
                    URLDecoder.decode(
                        result
                            .removePrefix("\"")
                            .removeSuffix("\"")
                            .replace("\\\"", "\"")
                            .replace("\\/", "/"),
                        "UTF-8"
                    )

                parseGoogleResults(decoded)

            } catch (e: Exception) {

                status.text =
                    "نتیجه موسیقی پیدا نشد"
            }
        }
    }

    private fun parseGoogleResults(json: String) {

        candidateSites.clear()

        val urlRegex =
            Regex("\"url\"\\s*:\\s*\"(.*?)\"")

        val matches =
            urlRegex.findAll(json)

        for (match in matches) {

            val url =
                match.groupValues[1]
                    .replace("\\/", "/")

            if (
                url.startsWith("http") &&
                !url.contains("google.com")
            ) {

                candidateSites.add(url)
            }
        }

        if (candidateSites.isEmpty()) {

            status.text =
                "نتیجه موسیقی مناسبی پیدا نشد"

            return
        }

        candidateIndex = 0

        openNextCandidate()
    }

    private fun openNextCandidate() {

        if (candidateIndex >= candidateSites.size) {

            status.text =
                "فایل صوتی قابل پخش پیدا نشد"

            return
        }

        val url =
            candidateSites[candidateIndex]

        candidateIndex++

        currentAudioUrl = null

        status.text =
            "در حال بررسی نتیجه ${candidateIndex}..."

        web.loadUrl(url)
    }

    private fun detectAudioFromPage() {

        web.evaluateJavascript(
            """
            (function() {

                var media =
                    document.querySelectorAll(
                        'audio, video, source'
                    );

                var result = [];

                for (var i = 0; i < media.length; i++) {

                    var src =
                        media[i].src ||
                        media[i].currentSrc ||
                        media[i].getAttribute('src');

                    if (src) {
                        result.push(src);
                    }
                }

                return JSON.stringify(result);

            })();
            """.trimIndent()
        ) { result ->

            try {

                val clean =
                    result
                        .removePrefix("\"")
                        .removeSuffix("\"")
                        .replace("\\\"", "\"")
                        .replace("\\/", "/")

                val urlRegex =
                    Regex("https?://[^\"\\s,]+")

                val urls =
                    urlRegex.findAll(clean)

                for (match in urls) {

                    val url =
                        match.value

                    if (isAudioUrl(url)) {

                        currentAudioUrl = url

                        playAudio(
                            url,
                            query.text.toString()
                        )

                        return@evaluateJavascript
                    }
                }

                status.text =
                    "این نتیجه فایل صوتی قابل پخش نداشت"

                openNextCandidate()

            } catch (e: Exception) {

                openNextCandidate()
            }
        }
    }

    private fun isGoogleUrl(url: String): Boolean {

        return url.contains("google.com") ||
                url.contains("googleusercontent.com")
    }

    private fun isAudioUrl(url: String): Boolean {

        val lower =
            url.lowercase()

        return lower.contains(".mp3") ||
                lower.contains(".m4a") ||
                lower.contains(".aac") ||
                lower.contains(".ogg") ||
                lower.contains(".wav") ||
                lower.contains(".flac") ||
                lower.contains(".m3u8") ||
                lower.contains("audio/")
    }

    private fun playAudio(
        url: String,
        title: String
    ) {

        try {

            player?.release()

            player =
                ExoPlayer.Builder(this).build()

            val mediaItem =
                MediaItem.fromUri(url)

            player?.setMediaItem(mediaItem)

            player?.addListener(
                object : androidx.media3.common.Player.Listener {

                    override fun onIsPlayingChanged(
                        isPlaying: Boolean
                    ) {

                        if (isPlaying) {

                            playButton.text = "❚❚"

                            nowPlaying.text =
                                "در حال پخش: $title"

                        } else {

                            playButton.text = "▶"
                        }
                    }

                    override fun onPlayerError(
                        error: PlaybackException
                    ) {

                        status.text =
                            "پخش این نتیجه ممکن نبود؛ نتیجه بعدی..."

                        openNextCandidate()
                    }
                }
            )

            player?.prepare()

            player?.play()

            currentTitle = title

            nowPlaying.text =
                "در حال پخش: $title"

            playButton.text = "❚❚"

            status.text =
                "✓ موسیقی در حال پخش است"

        } catch (e: Exception) {

            openNextCandidate()
        }
    }

    private fun togglePlayback() {

        val p = player

        if (p == null) {

            Toast.makeText(
                this,
                "هنوز آهنگی برای پخش انتخاب نشده",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (p.isPlaying) {

            p.pause()

            playButton.text = "▶"

        } else {

            p.play()

            playButton.text = "❚❚"
        }
    }

    override fun onDestroy() {

        player?.release()

        player = null

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
