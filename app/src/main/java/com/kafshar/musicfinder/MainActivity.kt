package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import android.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var nowPlaying: TextView

    private var player: ExoPlayer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        query = findViewById(R.id.query)
        web = findViewById(R.id.web)
        status = findViewById(R.id.status)
        nowPlaying = findViewById(R.id.nowPlaying)

        val search: Button = findViewById(R.id.search)
        val menu: TextView = findViewById(R.id.menu)
        val filter: TextView = findViewById(R.id.filter)

        web.setBackgroundColor(Color.TRANSPARENT)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                "Chrome/128 Mobile Safari/537.36"
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
                status.text = "منبع: $url"

                detectAudioFromPage()
            }
        }

        fun doSearch() {

            val q = query.text.toString().trim()

            if (q.isEmpty()) {
                Toast.makeText(
                    this,
                    "نام آهنگ را وارد کنید",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            status.text = "در حال جستجوی موسیقی..."

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
                "پخش مستقیم موسیقی در صورت وجود لینک صوتی",
                Toast.LENGTH_SHORT
            ).show()
        }

        nowPlaying.setOnClickListener {

            player?.let {

                if (it.isPlaying) {

                    it.pause()

                    nowPlaying.text =
                        "▶  متوقف شده"

                } else {

                    it.play()

                    nowPlaying.text =
                        "❚❚  در حال پخش"

                }
            }
        }

        web.setDownloadListener { url, _, _, _, _ ->

            if (isAudioUrl(url)) {

                playAudio(
                    url,
                    "موسیقی انتخاب شده"
                )

            } else {

                Toast.makeText(
                    this,
                    "فایل صوتی قابل پخش پیدا نشد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        web.loadUrl("https://www.google.com/")
    }

    private fun detectAudioFromPage() {

        web.evaluateJavascript(
            """
            (function() {
                var media = document.querySelectorAll(
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

            if (result != null &&
                result != "null" &&
                result != "\"[]\""
            ) {

                try {

                    val clean =
                        result
                            .removePrefix("\"")
                            .removeSuffix("\"")
                            .replace("\\\"", "\"")
                            .replace("\\/", "/")

                    val urls =
                        clean
                            .removePrefix("[")
                            .removeSuffix("]")
                            .split(",")

                    for (item in urls) {

                        val url =
                            item
                                .trim()
                                .removeSurrounding("\"")

                        if (isAudioUrl(url)) {

                            playAudio(
                                url,
                                "موسیقی پیدا شد"
                            )

                            break
                        }
                    }

                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isAudioUrl(url: String): Boolean {

        val lower = url.lowercase()

        return lower.contains(".mp3") ||
                lower.contains(".m4a") ||
                lower.contains(".aac") ||
                lower.contains(".ogg") ||
                lower.contains(".wav") ||
                lower.contains(".flac") ||
                lower.contains(".m3u8")
    }

    private fun playAudio(
        url: String,
        title: String
    ) {

        try {

            player?.release()

            player = ExoPlayer.Builder(this).build()

            val mediaItem =
                MediaItem.fromUri(url)

            player?.setMediaItem(mediaItem)

            player?.prepare()

            player?.play()

            nowPlaying.text =
                "❚❚  $title"

            status.text =
                "در حال پخش موسیقی..."

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "پخش موسیقی انجام نشد",
                Toast.LENGTH_SHORT
            ).show()
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
