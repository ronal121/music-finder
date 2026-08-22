package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

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
    private lateinit var seekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var saveButton: TextView
    private lateinit var libraryButton: TextView

    private var currentSong: SongResult? = null
    private var isPlaying = false
    private var destroyed = false
    private var searchGeneration = 0

    private val playerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (destroyed || intent?.action != MusicService.UPDATE) return
            isPlaying = intent.getBooleanExtra("playing", false)
            val title = intent.getStringExtra("title").orEmpty()
            val artist = intent.getStringExtra("artist").orEmpty()
            val volume = intent.getIntExtra("volume", -1)
            if (title.isNotBlank()) titleText.text = title
            if (artist.isNotBlank()) artistText.text = artist
            if (volume in 0..100) {
                volumeSeekBar.progress = volume
                volumeText.text = "$volume%"
            }
            playButton.text = if (isPlaying) "Ⅱ" else "▶"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destroyed = false
        setContentView(R.layout.activity_main)
        bindViews()
        setupWebView()
        setupControls()
        registerPlayerReceiver()
        status.text = "نام آهنگ یا خواننده را جستجو کنید"
    }

    private fun bindViews() {
        query = findViewById(R.id.query)
        status = findViewById(R.id.status)
        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)
        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        randomButton = findViewById(R.id.randomButton)
        seekBar = findViewById(R.id.seekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)
        resultsContainer = findViewById(R.id.resultsContainer)
        saveButton = findViewById(R.id.saveButton)
        libraryButton = findViewById(R.id.libraryButton)
        web = findViewById(R.id.web)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128 Mobile Safari/537.36"
        web.addJavascriptInterface(Bridge(), "MusicFinder")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return !ServerConfig.isAllowedPageUrl(request.url.toString())
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (destroyed) return
                if (url.contains("google.com/search", ignoreCase = true)) {
                    extractGoogleResults()
                } else if (ServerConfig.isAllowedPageUrl(url)) {
                    extractMediaFromPage(url)
                }
            }
        }
    }

    private fun setupControls() {
        findViewById<TextView>(R.id.search).setOnClickListener { searchMusic() }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchMusic()
                true
            } else false
        }

        playButton.setOnClickListener {
            val song = currentSong ?: return@setOnClickListener
            if (song.url.isBlank()) return@setOnClickListener
            sendPlay(song)
        }

        previousButton.setOnClickListener { sendSimpleAction(MusicService.ACTION_PREVIOUS) }
        nextButton.setOnClickListener { sendSimpleAction(MusicService.ACTION_NEXT) }
        randomButton.setOnClickListener { nextSongFromResults() }

        saveButton.setOnClickListener {
            currentSong?.let {
                LibraryManager.add(this, it)
                Toast.makeText(this, "به کتابخانه اضافه شد", Toast.LENGTH_SHORT).show()
            }
        }

        libraryButton.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        volumeSeekBar.max = 100
        val prefs = getSharedPreferences("player_settings", MODE_PRIVATE)
        val savedVolume = prefs.getInt("volume_percent", 80).coerceIn(0, 100)
        volumeSeekBar.progress = savedVolume
        volumeText.text = "$savedVolume%"
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val volume = value.coerceIn(0, 100)
                prefs.edit().putInt("volume_percent", volume).apply()
                volumeText.text = "$volume%"
                sendVolume(volume)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        seekBar.max = 100
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val intent = Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_SEEK_PERCENT
                    putExtra(MusicService.EXTRA_PERCENT, value)
                }
                startMusicService(intent)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }

    private fun searchMusic() {
        val text = SearchEngine.normalizeQuery(query.text.toString())
        if (text.isBlank()) {
            status.text = "عبارت جستجو را وارد کنید"
            return
        }

        searchGeneration++
        resultsContainer.removeAllViews()
        status.text = "در حال جستجو برای «$text»..."

        val encoded = try {
            URLEncoder.encode(SearchEngine.buildGoogleQuery(text), "UTF-8")
        } catch (_: Exception) {
            status.text = "خطا در ساخت جستجو"
            return
        }

        web.loadUrl("https://www.google.com/search?q=$encoded&num=40")
    }

    private fun extractGoogleResults() {
        val hosts = ServerConfig.MUSIC_SITES.joinToString(
            prefix = "[",
            postfix = "]"
        ) { "\"$it\"" }

        val script = """
            (function() {
                var links = document.querySelectorAll('a');
                var found = [];
                var hosts = $hosts;
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].href || '';
                    var text = links[i].innerText || '';
                    var lower = href.toLowerCase();
                    var allowed = false;
                    for (var h = 0; h < hosts.length; h++) {
                        if (lower.indexOf(hosts[h]) >= 0) { allowed = true; break; }
                    }
                    if (allowed && lower.indexOf('google.com') < 0 && found.indexOf(href) < 0) {
                        found.push(href + '|||' + text.replace(/[\r\n]+/g, ' '));
                    }
                }
                MusicFinder.results(found.join('###'));
            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    private fun extractMediaFromPage(pageUrl: String) {
        val script = """
            (function() {
                var urls = [];
                var audio = document.querySelectorAll('audio, source');
                for (var i = 0; i < audio.length; i++) {
                    var u = audio[i].src || audio[i].getAttribute('src') || '';
                    if (u) urls.push(u);
                }
                var meta = document.querySelectorAll('meta[property="og:audio"], meta[property="og:audio:url"]');
                for (var j = 0; j < meta.length; j++) {
                    var m = meta[j].content || '';
                    if (m) urls.push(m);
                }
                var links = document.querySelectorAll('a[href]');
                for (var k = 0; k < links.length; k++) {
                    var h = links[k].href || '';
                    if (/\.(mp3|m4a|aac|ogg|wav)(\?|$)/i.test(h)) urls.push(h);
                }
                MusicFinder.media('$pageUrl', urls.join('###'));
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun renderResults(raw: String) {
        resultsContainer.removeAllViews()
        val items = raw.split("###")
            .mapNotNull { item ->
                val parts = item.split("|||", limit = 2)
                if (parts.isEmpty() || parts[0].isBlank()) null
                else parts[0] to parts.getOrNull(1).orEmpty()
            }
            .distinctBy { it.first }
            .take(30)

        if (items.isEmpty()) {
            status.text = "نتیجه‌ای از سایت‌های موسیقی پیدا نشد"
            return
        }

        status.text = "${items.size} نتیجه پیدا شد"
        items.forEachIndexed { index, item ->
            val button = TextView(this).apply {
                text = "${index + 1}. ${item.second.ifBlank { ServerConfig.siteName(item.first) }}"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(18, 16, 18, 16)
                setBackgroundColor(0xFF15151D.toInt())
                setOnClickListener {
                    status.text = "در حال بررسی لینک..."
                    web.loadUrl(item.first)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            resultsContainer.addView(button, params)
        }
    }

    private fun sendPlay(song: SongResult) {
        currentSong = song
        titleText.text = song.title
        artistText.text = "${song.artist} • ${song.site}"
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra(MusicService.EXTRA_URL, song.url)
            putExtra(MusicService.EXTRA_TITLE, song.title)
            putExtra(MusicService.EXTRA_ARTIST, song.artist)
            putExtra(MusicService.EXTRA_COVER, song.cover)
        }
        startMusicService(intent)
        isPlaying = true
        playButton.text = "Ⅱ"
    }

    private fun nextSongFromResults() {
        val song = currentSong ?: return
        sendPlay(song.copy(title = song.title))
    }

    private fun sendVolume(value: Int) {
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_SET_VOLUME
            putExtra(MusicService.EXTRA_VOLUME, value)
        }
        startMusicService(intent)
    }

    private fun sendSimpleAction(action: String) {
        startMusicService(Intent(this, MusicService::class.java).apply { this.action = action })
    }

    private fun startMusicService(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "سرویس پخش در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerPlayerReceiver() {
        val filter = android.content.IntentFilter(MusicService.UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(playerReceiver, filter)
        }
    }

    override fun onDestroy() {
        destroyed = true
        try { unregisterReceiver(playerReceiver) } catch (_: Exception) { }
        try { web.stopLoading(); web.removeJavascriptInterface("MusicFinder"); web.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun results(raw: String?) {
            if (destroyed) return
            runOnUiThread { if (!destroyed) renderResults(raw.orEmpty()) }
        }

        @JavascriptInterface
        fun media(pageUrl: String?, rawUrls: String?) {
            if (destroyed) return
            val urls = rawUrls.orEmpty().split("###")
                .map { it.trim() }
                .filter { ServerConfig.isAllowedMediaUrl(it) }
                .distinct()

            runOnUiThread {
                if (destroyed) return@runOnUiThread
                if (urls.isEmpty()) {
                    status.text = "لینک مستقیم پخش در این صفحه پیدا نشد"
                    return@runOnUiThread
                }
                val url = urls.first()
                val title = query.text.toString().ifBlank { "Music" }
                val song = SongResult(
                    url = url,
                    title = title,
                    artist = ServerConfig.siteName(pageUrl.orEmpty()),
                    site = ServerConfig.siteName(pageUrl.orEmpty())
                )
                currentSong = song
                titleText.text = song.title
                artistText.text = song.site
                status.text = "لینک پخش آماده است"
                sendPlay(song)
            }
        }
    }
}
