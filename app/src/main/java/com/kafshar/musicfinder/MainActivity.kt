package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
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
                url: String
            ): Boolean {
                return false
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                status.text = "صفحه آماده است"
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
            togglePlayback()
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
                "منابع موسیقی فعال هستند",
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

        playButton.text = "▶"
        nowPlaying.text = "در حال جستجو..."
        status.text = "در حال پیدا کردن نتیجه..."

        val searchQuery =
            "$text site:rozmusic.com OR " +
            "site:mybia2music.com OR " +
            "site:musicdel.ir OR " +
            "site:musics-fa.com"

        val url =
            "https://www.google.com/search?q=" +
                    URLEncoder.encode(searchQuery, "UTF-8")

        web.loadUrl(url)
    }

    private fun togglePlayback() {

        val currentPlayer = player

        if (currentPlayer == null) {
            Toast.makeText(
                this,
                "ابتدا یک آهنگ انتخاب کنید",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            playButton.text = "▶"
        } else {
            currentPlayer.play()
            playButton.text = "❚❚"
        }
    }

    private fun playAudio(
        audioUrl: String,
        title: String
    ) {

        player?.release()

        player = ExoPlayer.Builder(this).build()

        val mediaItem =
            MediaItem.fromUri(audioUrl)

        player?.setMediaItem(mediaItem)

        player?.addListener(
            object : Player.Listener {

                override fun onIsPlayingChanged(
                    isPlaying: Boolean
                ) {

                    playButton.text =
                        if (isPlaying) {
                            "❚❚"
                        } else {
                            "▶"
                        }
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {

                    if (
                        playbackState ==
                        Player.STATE_ENDED
                    ) {
                        playButton.text = "▶"
                    }
                }
            }
        )

        player?.prepare()
        player?.play()

        nowPlaying.text =
            "در حال پخش: $title"

        status.text =
            "✓ پخش شروع شد"

        playButton.text = "❚❚"
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
