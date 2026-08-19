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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.net.URLEncoder

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: TextView

    private var player: ExoPlayer? = null
    private var currentUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        query = findViewById(R.id.query)
        status = findViewById(R.id.status)
        nowPlaying = findViewById(R.id.nowPlaying)
        playButton = findViewById(R.id.playButton)

        val search = findViewById<Button>(R.id.search)
        val menu = findViewById<TextView>(R.id.menu)
        val filter = findViewById<TextView>(R.id.filter)

        web.setBackgroundColor(Color.TRANSPARENT)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
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
                status.text = "صفحه آماده است"

                if (!isGoogle(url)) {
                    findPublicAudio()
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
            togglePlayer()
        }

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
                "جستجو در منابع موسیقی",
                Toast.LENGTH_SHORT
            ).show()
        }

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

        player?.stop()
        currentUrl = null

        playButton.text = "▶"
        nowPlaying.text = "در حال جستجو..."
        status.text = "در حال پیدا کردن بهترین نتیجه..."

        val searchQuery =
            "$text site:rozmusic.com OR site:mybia2music.com " +
            "OR site:musicdel.ir OR site:musics-fa.com"

        val url =
            "https://www.google.com/search?q=" +
                    URLEncoder.encode(searchQuery, "UTF-8")

        web.loadUrl(url)
    }

    private fun findPublicAudio() {

        web.evaluateJavascript(
            """
            (function() {

                var list = [];

                var elements =
                    document.querySelectorAll(
                        'audio, audio source, video, video source'
                    );

                for (var i = 0; i < elements.length; i++) {

                    var u =
                        elements[i].currentSrc ||
                        elements[i].src ||
                        elements[i].getAttribute('src');

                    if (u) {
                        list.push(u);
                    }
                }

                return JSON.stringify(list);

            })();
            """.trimIndent()
        ) { result ->

            val urls = extractUrls(result)

            val audio =
                urls.firstOrNull {
                    isSupportedAudio(it)
                }

            if (audio != null) {

                currentUrl = audio

                playAudio(
                    audio,
                    query.text.toString()
                )

            } else {

                status.text =
                    "این صفحه فایل صوتی قابل پخش داخلی ارائه نکرده است"
            }
        }
    }

    private fun extractUrls(data: String): List<String> {

        return Regex(
            """https?://[^"\\\s,\]]+"""
        )
            .findAll(data)
            .map {
                it.value
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
            }
            .toList()
    }

    private fun isSupportedAudio(url: String): Boolean {

        val u = url.lowercase()

        return u.contains(".mp3") ||
                u.contains(".m4a") ||
                u.contains(".aac") ||
                u.contains(".ogg") ||
                u.contains(".wav") ||
                u.contains(".flac") ||
                u.contains(".m3u8")
    }

    private fun playAudio(
        url: String,
        title: String
    ) {

        try {

            player?.release()

            player =
                ExoPlayer.Builder(this).build()

            player?.setMediaItem(
                MediaItem.fromUri(url)
            )

            player?.addListener(
                object : Player.Listener {

                    override fun onIsPlayingChanged(
                        isPlaying: Boolean
                    ) {

                        playButton.text =
                            if (isPlaying) "❚❚"
                            else "▶"
                    }

                    override fun onPlayerError(
                        error: PlaybackException
                    ) {

                        status.text =
                            "پخش این فایل ممکن نیست"

                        playButton.text = "▶"
                    }
                }
            )

            player?.prepare()
            player?.play()

            nowPlaying.text =
                "در حال پخش: $title"

            status.text =
                "✓ پخش شروع شد"

        } catch (e: Exception) {

            status.text =
                "خطا در پخش فایل"
        }
    }

    private fun togglePlayer() {

        val p = player

        if (p == null) {

            Toast.makeText(
                this,
                "ابتدا یک آهنگ جستجو کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    private fun isGoogle(url: String): Boolean {
        return url.contains("google.com")
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
