package com.kafshar.musicfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class SongResult(
    val url: String,
    val title: String,
    val artist: String,
    val site: String,
    val cover: String = "",
    val isYouTube: Boolean = false
)

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var lyricsText: TextView
    private lateinit var playButton: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var randomButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var currentTimeText: TextView
    private lateinit var durationText: TextView
    private lateinit var downloadButton: TextView
    private lateinit var cancelDownloadButton: TextView
    private lateinit var pauseDownloadButton: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadText: TextView
    private lateinit var saveButton: TextView
    private lateinit var libraryButton: TextView
    private lateinit var historyButton: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var vinyl: VinylView

    private val turquoiseColor = 0xFF20C9C9.toInt()

    private val songs = ArrayList<SongResult>()

    private val handler = Handler(Looper.getMainLooper())

    private val io = Executors.newFixedThreadPool(4)
    private val downloadExecutor = Executors.newSingleThreadExecutor()

    private var downloadFuture: Future<*>? = null

    private var searchFuture: Future<*>? = null

    private var currentIndex = -1
    private var currentAudioUrl = ""
    private var currentSong: SongResult? = null

    private var randomMode = false
    private var destroyed = false
    private var receiverRegistered = false

    private var searchGeneration = 0

    private var googleFallbackUsed = false

    private var resultPages: List<String> = emptyList()
    private var resultPageIndex = 0
    private var resultGeneration = 0
    private var expectedPageUrl = ""

    private var searchTimeout: Runnable? = null
    private var pageTimeout: Runnable? = null

    private var cancelDownloadRequested = false
    private var pauseDownloadRequested = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    private var webRecreating = false

    private val playerReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            if (destroyed) return
            if (intent?.action != MusicService.UPDATE) return

            val playing = intent.getBooleanExtra("playing", false)
            val position = intent.getLongExtra("position", 0L)
            val duration = intent.getLongExtra("duration", 0L)

            val title = intent.getStringExtra(
                MusicService.EXTRA_TITLE
            ).orEmpty()

            val artist = intent.getStringExtra(
                MusicService.EXTRA_ARTIST
            ).orEmpty()

            val volume = intent.getIntExtra(
                MusicService.EXTRA_VOLUME,
                -1
            )

            val mediaUrl = intent.getStringExtra(
                MusicService.EXTRA_URL
            ).orEmpty()

            runOnUiThread {

                if (destroyed) return@runOnUiThread

                updatePlayerProgress(
                    playing,
                    position,
                    duration
                )

                if (title.isNotBlank()) {
                    titleText.text = title
                }

                if (artist.isNotBlank()) {
                    artistText.text = artist
                }

                if (volume in 0..100) {

                    if (volumeSeekBar.progress != volume) {
                        volumeSeekBar.progress = volume
                    }

                    volumeText.text = "$volume%"
                }

                if (mediaUrl.isNotBlank()) {
                    updateActiveResultHighlight(mediaUrl)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        destroyed = false

        setContentView(R.layout.activity_main)

        bindViews()
        setupWebView()
        setupButtons()
        setupVolumeControl()
        applyTurquoiseButtonStyle()
        restoreSearchResults()
        requestNotificationPermission()

        status.text = "نام آهنگ یا خواننده را جستجو کنید"
    }

    private fun bindViews() {

        query = findViewById(R.id.query)

        status = findViewById(R.id.status)

        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)
        lyricsText = findViewById(R.id.lyricsText)

        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        randomButton = findViewById(R.id.randomButton)

        seekBar = findViewById(R.id.seekBar)

        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)

        currentTimeText = findViewById(R.id.currentTimeText)
        durationText = findViewById(R.id.durationText)

        downloadButton = findViewById(R.id.downloadButton)
        cancelDownloadButton = findViewById(R.id.cancelDownloadButton)
        pauseDownloadButton = findViewById(R.id.pauseDownloadButton)

        downloadProgress = findViewById(R.id.downloadProgress)
        downloadText = findViewById(R.id.downloadText)

        saveButton = findViewById(R.id.saveButton)
        libraryButton = findViewById(R.id.libraryButton)
        historyButton = findViewById(R.id.historyButton)

        historyContainer = findViewById(R.id.historyContainer)
        resultsContainer = findViewById(R.id.resultsContainer)

        vinyl = findViewById(R.id.vinyl)
    }

    private fun setupButtons() {

        findViewById<TextView>(R.id.search).setOnClickListener {
            searchMusic()
        }

        query.setOnEditorActionListener { _, actionId, event ->

            val submit =
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (
                    event != null &&
                    event.keyCode == android.view.KeyEvent.KEYCODE_ENTER
                )

            if (submit) {
                searchMusic()
                true
            } else {
                false
            }
        }

        playButton.setOnClickListener {

            if (currentAudioUrl.isBlank()) {

                if (songs.isNotEmpty()) {

                    val index =
                        currentIndex.coerceIn(
                            0,
                            songs.lastIndex
                        )

                    playSong(songs[index])
                }

            } else {

                sendServiceAction(
                    MusicService.ACTION_TOGGLE,
                    currentAudioUrl,
                    titleText.text.toString(),
                    artistText.text.toString(),
                    currentSong?.cover.orEmpty()
                )
            }
        }

        previousButton.setOnClickListener {
            previousSong()
        }

        nextButton.setOnClickListener {
            nextSong()
        }

        randomButton.setOnClickListener {

            randomMode = !randomMode

            randomButton.text =
                if (randomMode) "🔀✓" else "🔀"

            if (randomMode && songs.isNotEmpty()) {
                nextSong()
            }
        }

        seekBar.max = 100

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    s: SeekBar?,
                    p: Int,
                    fromUser: Boolean
                ) = Unit

                override fun onStartTrackingTouch(
                    s: SeekBar?
                ) = Unit

                override fun onStopTrackingTouch(
                    s: SeekBar?
                ) {

                    val percent =
                        s?.progress
                            ?.coerceIn(0, 100)
                            ?: return

                    sendServiceSimpleAction(
                        MusicService.ACTION_SEEK_PERCENT
                    ) {
                        putExtra(
                            MusicService.EXTRA_PERCENT,
                            percent
                        )
                    }
                }
            }
        )

        downloadButton.setOnClickListener {
            downloadCurrentSong()
        }

        cancelDownloadButton.setOnClickListener {
            cancelDownload()
        }

        pauseDownloadButton.setOnClickListener {
            toggleDownloadPause()
        }

        saveButton.setOnClickListener {
            saveCurrentSong()
        }

        libraryButton.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        this,
                        LibraryActivity::class.java
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "کتابخانه در دسترس نیست",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        historyButton.setOnClickListener {
            toggleHistory()
        }
    }

    private fun setupVolumeControl() {

        val prefs =
            getSharedPreferences(
                "player_settings",
                MODE_PRIVATE
            )

        val saved =
            prefs.getInt(
                "volume_percent",
                80
            ).coerceIn(0, 100)

        volumeSeekBar.max = 100
        volumeSeekBar.progress = saved

        volumeText.text = "$saved%"

        volumeSeekBar.progressTintList =
            ColorStateList.valueOf(turquoiseColor)

        volumeSeekBar.thumbTintList =
            ColorStateList.valueOf(turquoiseColor)

        volumeSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    s: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    val value =
                        progress.coerceIn(0, 100)

                    volumeText.text = "$value%"

                    if (fromUser) {

                        prefs.edit()
                            .putInt(
                                "volume_percent",
                                value
                            )
                            .apply()

                        sendServiceSimpleAction(
                            MusicService.ACTION_SET_VOLUME
                        ) {
                            putExtra(
                                MusicService.EXTRA_VOLUME,
                                value
                            )
                        }
                    }
                }

                override fun onStartTrackingTouch(
                    s: SeekBar?
                ) = Unit

                override fun onStopTrackingTouch(
                    s: SeekBar?
                ) = Unit
            }
        )
    }

    private fun applyTurquoiseButtonStyle() {

        val buttons =
            listOf(
                playButton,
                previousButton,
                nextButton,
                randomButton,
                findViewById<TextView>(R.id.search),
                downloadButton,
                cancelDownloadButton,
                pauseDownloadButton,
                saveButton,
                libraryButton,
                historyButton
            )

        seekBar.progressTintList =
            ColorStateList.valueOf(turquoiseColor)

        seekBar.thumbTintList =
            ColorStateList.valueOf(turquoiseColor)

        buttons.forEach {

            try {
                it.backgroundTintList =
                    ColorStateList.valueOf(
                        turquoiseColor
                    )
            } catch (_: Exception) {
            }

            it.setTextColor(
                0xFFFFFFFF.toInt()
            )
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                500
            )
        }
    }

    private inner class Bridge {

        @JavascriptInterface
        fun results(raw: String?) {

            runOnUiThread {

                if (destroyed) return@runOnUiThread

                resultGeneration = searchGeneration
                resultPageIndex = 0

                val discovered = raw.orEmpty()
                    .split("###")
                    .map { it.trim() }
                    .mapNotNull { entry ->
                        val p = entry.split("|||", limit = 3)
                        val url = p.getOrNull(0)?.trim().orEmpty()
                        if (!url.startsWith("http", true)) return@mapNotNull null
                        val title = decode(p.getOrNull(1)?.trim().orEmpty())
                        val isYouTube = p.getOrNull(2) == "1" || ServerConfig.isYouTubeUrl(url)
                        Triple(url, title, isYouTube)
                    }
                    .distinctBy { it.first.substringBefore("#").trimEnd('/').lowercase() }
                    .take(15)

                discovered.filter { it.third }.forEach { (url, title, _) ->
                    addYouTubeView(url, title.ifBlank { "YouTube" })
                }

                resultPages = discovered
                    .filterNot { it.third }
                    .map { "${it.first}|||${it.second}" }

                if (resultPages.isEmpty()) {
                    finishSearch()
                } else {
                    status.text =
                        "نتایج پیدا شد؛ در حال بررسی..."
                    processNextResultPage()
                }
            }
        }

        @JavascriptInterface
        fun page(raw: String?) {

            runOnUiThread {

                if (
                    destroyed ||
                    resultGeneration != searchGeneration
                ) {
                    return@runOnUiThread
                }

                val parts =
                    raw.orEmpty()
                        .split(
                            "###",
                            limit = 4
                        )

                if (parts.size < 4) {
                    finishCurrentResultPage()
                    return@runOnUiThread
                }

                val title =
                    cleanTitle(
                        decode(parts[0])
                    ).ifBlank {
                        "Music"
                    }

                val artist =
                    decode(parts[1])
                        .trim()
                        .ifBlank {
                            "Unknown Artist"
                        }

                val cover =
                    decode(parts[2])
                        .trim()

                val audioCandidates = decode(parts[3])
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.startsWith("http", true) }
                    .distinct()
                    .take(30)

                validateAndAddAudioCandidates(
                    title,
                    artist,
                    cover,
                    audioCandidates,
                    expectedPageUrl
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        web = findViewById(R.id.web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true

        web.settings.mediaPlaybackRequiresUserGesture =
            false

        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

        web.addJavascriptInterface(
            Bridge(),
            "MusicFinder"
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    return !(
                        url.contains(
                            "google.com",
                            true
                        ) ||
                        ServerConfig.isAllowedPageUrl(
                            url
                        )
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    if (destroyed) return

                    if (
                        url.contains(
                            "google.com/search",
                            true
                        )
                    ) {

                        handler.postDelayed(
                            {
                                if (
                                    !destroyed &&
                                    searchGeneration > 0
                                ) {
                                    extractGoogleResults()
                                }
                            },
                            250
                        )

                    } else if (
                        resultGeneration ==
                        searchGeneration &&
                        ServerConfig.isAllowedPageUrl(
                            url
                        )
                    ) {

                        extractMusicPage(url)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )

                    if (
                        request.isForMainFrame &&
                        !destroyed &&
                        resultPages.isNotEmpty()
                    ) {
                        finishCurrentResultPage()
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    if (!destroyed) {
                        recreateWebView()
                    }

                    return true
                }
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun recreateWebView() {

        if (
            destroyed ||
            webRecreating
        ) {
            return
        }

        webRecreating = true

        try {

            val old = web

            val parent =
                old.parent as? android.view.ViewGroup

            if (parent == null) {
                webRecreating = false
                return
            }

            val index =
                parent.indexOfChild(old)

            val params =
                old.layoutParams

            parent.removeView(old)

            try {
                old.stopLoading()
                old.removeJavascriptInterface(
                    "MusicFinder"
                )
                old.destroy()
            } catch (_: Exception) {
            }

            val replacement =
                WebView(this)

            replacement.id = R.id.web
            replacement.layoutParams = params

            parent.addView(
                replacement,
                index.coerceAtMost(
                    parent.childCount
                )
            )

            web = replacement

            setupWebView()

            webRecreating = false

        } catch (_: Exception) {

            webRecreating = false

            if (!destroyed) {
                status.text =
                    "جستجو موقتاً در دسترس نیست"
            }
        }
    }

    private fun searchMusic() {

        if (destroyed) return
        val text = query.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "نام آهنگ یا خواننده را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        searchGeneration++
        val generation = searchGeneration
        cancelSearchCallbacks()
        googleFallbackUsed = false
        resultGeneration = generation
        resultPages = emptyList()
        resultPageIndex = 0
        expectedPageUrl = ""
        songs.clear()
        currentIndex = -1
        currentAudioUrl = ""
        currentSong = null
        resultsContainer.removeAllViews()
        titleText.text = text
        artistText.text = "در حال جستجو..."
        status.text = "در حال جستجوی منابع موسیقی..."
        seekBar.progress = 0
        currentTimeText.text = "00:00"
        durationText.text = "00:00"
        vinyl.clearCover()
        vinyl.stopRotation()
        clearLyrics()
        // Google is the only discovery source. No hard-coded music-site list is used.
        loadGoogleFallback(text, generation)
    }

    private fun loadGoogleFallback(text: String, generation: Int) {
        if (destroyed || generation != searchGeneration || googleFallbackUsed) return
        googleFallbackUsed = true
        resultGeneration = generation
        resultPages = emptyList()
        resultPageIndex = 0
        val encoded = try { URLEncoder.encode(SearchEngine.buildGoogleQuery(text), "UTF-8") } catch (_: Exception) {
            status.text = "خطا در آماده‌سازی جستجو"
            return
        }
        try {
            web.stopLoading()
            web.loadUrl("https://www.google.com/search?q=$encoded&num=50&hl=en&gbv=1")
        } catch (_: Exception) {
            status.text = "جستجوی جایگزین در دسترس نیست"
        }
    }

    private fun cancelSearchCallbacks() {

        try { searchFuture?.cancel(true) } catch (_: Exception) {}
        searchFuture = null

        searchTimeout?.let {
            handler.removeCallbacks(it)
        }

        pageTimeout?.let {
            handler.removeCallbacks(it)
        }

        searchTimeout = null
        pageTimeout = null
    }

    private fun extractGoogleResults() {

        if (destroyed || searchGeneration <= 0) return

        val script = """
            (function(){
              try{
                var found=[];
                function real(h){
                  try{
                    var x=new URL(h, location.href);
                    if(x.hostname.toLowerCase().indexOf('google.')>=0){
                      var q=x.searchParams.get('q') || x.searchParams.get('url');
                      if(q && q.indexOf('http')===0) return decodeURIComponent(q);
                    }
                    return x.href;
                  }catch(e){ return h; }
                }
                function youtube(u){
                  try{
                    var h=new URL(u).hostname.toLowerCase().replace(/^www\./,'');
                    return h==='youtube.com' || h.endsWith('.youtube.com') || h==='youtu.be';
                  }catch(e){ return false; }
                }
                var anchors=document.querySelectorAll('a');
                for(var i=0;i<anchors.length && found.length<15;i++){
                  var a=anchors[i];
                  var u=real(a.href||'');
                  if(!/^https?:/i.test(u)) continue;
                  try{
                    var h=new URL(u).hostname.toLowerCase();
                    if(h.indexOf('google.')>=0 || h==='webcache.googleusercontent.com') continue;
                  }catch(e){ continue; }
                  var t=(a.innerText||a.textContent||'').replace(/[\r\n\t]+/g,' ').replace(/\s+/g,' ').trim();
                  if(!t && a.querySelector('h3')) t=a.querySelector('h3').innerText||'';
                  var key=u.split('#')[0].replace(/\/$/,'').toLowerCase();
                  var dup=false;
                  for(var j=0;j<found.length;j++){ if(found[j].split('|||')[0].toLowerCase()===key){dup=true;break;} }
                  if(dup) continue;
                  found.push(u+'|||'+encodeURIComponent(t)+'|||'+(youtube(u)?'1':'0'));
                }
                MusicFinder.results(found.join('###'));
              }catch(e){ MusicFinder.results(''); }
            })();
        """.trimIndent()

        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishSearch() }
    }

    private fun extractMusicPage(pageUrl: String) {

        if (destroyed || resultGeneration != searchGeneration) return
        expectedPageUrl = pageUrl

        val script = """
            (function(){
              try{
                var title='',artist='',cover='',aud=[];
                function add(v){
                  if(!v) return;
                  try{ v=new URL(v, location.href).href; }catch(e){ return; }
                  if(!/^https?:/i.test(v)) return;
                  if(aud.indexOf(v)<0 && aud.length<40) aud.push(v);
                }
                var og=document.querySelector('meta[property="og:title"]');
                if(og) title=og.content||'';
                var h=document.querySelector('h1');
                if(!title && h) title=h.innerText||'';
                var ma=document.querySelector('meta[property="music:musician"]');
                if(ma) artist=ma.content||'';
                var im=document.querySelector('meta[property="og:image"]');
                if(im) cover=im.content||'';
                document.querySelectorAll('audio,video,source,a').forEach(function(el){
                  add(el.currentSrc||el.src||el.href||'');
                  ['data-src','data-url','data-audio','data-mp3','data-file','data-download','data-media','data-stream'].forEach(function(k){ add(el.getAttribute(k)||''); });
                });
                document.querySelectorAll('script,script[type="application/ld+json"]').forEach(function(el){
                  var text=el.textContent||'';
                  var matches=text.match(/https?:\/\/[^\s\"'<>\\]+/g)||[];
                  matches.forEach(add);
                });
                var html=document.documentElement.outerHTML||'';
                var urls=html.match(/https?:\/\/[^\s\"'<>\\]+/g)||[];
                urls.forEach(function(v){
                  if(/(?:\.mp3|\.m4a|\.aac|\.ogg|\.opus|\.wav|\.flac|\.webm|download|\/dl\/|\/api\/audio|media|stream)/i.test(v)) add(v);
                });
                MusicFinder.page(encodeURIComponent(title)+'###'+encodeURIComponent(artist)+'###'+encodeURIComponent(cover)+'###'+encodeURIComponent(aud.join('|||')));
              }catch(e){ MusicFinder.page('######'); }
            })();
        """.trimIndent()

        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }
    }

    private fun validateAndAddAudioCandidates(
        title: String,
        artist: String,
        cover: String,
        candidates: List<String>,
        pageUrl: String
    ) {
        if (candidates.isEmpty()) { finishCurrentResultPage(); return }
        val generation = searchGeneration
        io.execute {
            val accepted = candidates.mapNotNull { url ->
                if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return@mapNotNull null
                if (probeMediaUrl(url, pageUrl)) url else null
            }.distinct()
            runOnUiThread {
                if (destroyed || generation != searchGeneration) return@runOnUiThread
                accepted.forEach { audio ->
                    val song = SongResult(audio, title, artist, getSiteName(pageUrl), cover)
                    if (songs.none { it.url == song.url }) {
                        songs.add(song)
                        addSongView(song, songs.lastIndex)
                    }
                }
                if (songs.isNotEmpty()) status.text = "${songs.size} آهنگ پیدا شد"
                finishCurrentResultPage()
            }
        }
    }

    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false
        fun request(method: String): String? {
            return try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.instanceFollowRedirects = true
                c.connectTimeout = 2500
                c.readTimeout = 2500
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
                c.setRequestProperty("Referer", pageUrl)
                if (method == "GET") c.setRequestProperty("Range", "bytes=0-0")
                c.connect()
                val type = c.contentType?.lowercase()
                val code = c.responseCode
                c.disconnect()
                if (code in 200..399) type else null
            } catch (_: Exception) { null }
        }
        val type = request("HEAD") ?: request("GET")
        return type?.startsWith("audio/") == true ||
            (type?.startsWith("video/") == true && url.contains("audio", true)) ||
            ServerConfig.looksLikeAudioUrl(url)
    }

    private fun addYouTubeView(url: String, title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(0xFF15151D.toInt())
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "باز کردن YouTube ممکن نیست", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val cover = ImageView(this).apply {
            setBackgroundColor(0xFF22222A.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        row.addView(cover, LinearLayout.LayoutParams(58, 58))
        val id = youtubeVideoId(url)
        if (id.isNotBlank()) loadCover("https://i.ytimg.com/vi/$id/hqdefault.jpg", cover)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 8, 0)
        }
        val t = TextView(this).apply {
            text = if (title.isBlank()) "YouTube" else title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            maxLines = 2
        }
        val sub = TextView(this).apply {
            text = "YouTube • باز کردن"
            setTextColor(0xFFFF5555.toInt())
            textSize = 11f
        }
        box.addView(t)
        box.addView(sub)
        row.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        resultsContainer.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 8)
        })
    }

    private fun youtubeVideoId(url: String): String {
        return try {
            val u = android.net.Uri.parse(url)
            when {
                u.host?.contains("youtu.be", true) == true -> u.pathSegments.firstOrNull().orEmpty()
                u.getQueryParameter("v") != null -> u.getQueryParameter("v").orEmpty()
                else -> u.pathSegments.firstOrNull { it.length >= 8 && it.matches(Regex("[A-Za-z0-9_-]{8,}")) }.orEmpty()
            }
        } catch (_: Exception) { "" }
    }

    private fun processNextResultPage() {

        if (
            destroyed ||
            resultGeneration !=
            searchGeneration
        ) {
            return
        }

        if (
            resultPageIndex >=
            resultPages.size
        ) {

            finishSearch()
            return
        }

        val url =
            resultPages[
                resultPageIndex
            ]
                .substringBefore("|||")
                .trim()

        resultPageIndex++

        if (
            url.isBlank() ||
            !ServerConfig.isAllowedPageUrl(
                url
            )
        ) {

            processNextResultPage()
            return
        }

        expectedPageUrl = url

        pageTimeout?.let {
            handler.removeCallbacks(it)
        }

        val generation =
            searchGeneration

        val timeout =
            Runnable {

                if (
                    !destroyed &&
                    generation ==
                    searchGeneration
                ) {
                    processNextResultPage()
                }
            }

        pageTimeout = timeout

        handler.postDelayed(
            timeout,
            5000L
        )

        try {

            web.loadUrl(url)

        } catch (_: Exception) {

            finishCurrentResultPage()
        }
    }

    private fun finishCurrentResultPage() {

        pageTimeout?.let {
            handler.removeCallbacks(it)
        }

        pageTimeout = null

        if (
            !destroyed &&
            resultGeneration ==
            searchGeneration
        ) {
            processNextResultPage()
        }
    }

    private fun finishSearch() {

        if (destroyed) return

        cancelSearchCallbacks()

        if (songs.isEmpty() && !googleFallbackUsed && resultGeneration == searchGeneration) {
            status.text = "در منابع مستقیم آهنگ قابل پخش پیدا نشد؛ در حال جستجوی Google..."
            loadGoogleFallback(query.text.toString(), searchGeneration)
            return
        }

        status.text =
            if (songs.isEmpty()) {
                "آهنگ قابل پخش پیدا نشد"
            } else {
                "${songs.size} نتیجه پیدا شد"
            }

        if (
            songs.isNotEmpty() &&
            currentIndex < 0
        ) {
            currentIndex = 0
        }

        saveSearchResults()
    }

    private fun saveSearchResults() {

        if (destroyed) return

        val prefs =
            getSharedPreferences(
                "search_results",
                MODE_PRIVATE
            )

        val old =
            prefs.getString(
                "songs",
                ""
            ).orEmpty()

        val merged =
            ArrayList<SongResult>()

        old.split("\n")
            .forEach { line ->

                val p =
                    line.split(
                        "|||",
                        limit = 5
                    )

                if (
                    p.size == 5 &&
                    p[0].isNotBlank() &&
                    merged.none {
                        it.url == p[0]
                    }
                ) {

                    merged.add(
                        SongResult(
                            p[0],
                            p[1],
                            p[2],
                            p[3],
                            p[4]
                        )
                    )
                }
            }

        songs.forEach {

            if (
                merged.none {
                    x -> x.url == it.url
                }
            ) {
                merged.add(it)
            }
        }

        prefs.edit()
            .putString(
                "songs",
                merged
                    .take(200)
                    .joinToString("\n") {
                        listOf(
                            it.url,
                            it.title,
                            it.artist,
                            it.site,
                            it.cover
                        ).joinToString("|||")
                    }
            )
            .apply()
    }

    private fun restoreSearchResults() {

        val data =
            getSharedPreferences(
                "search_results",
                MODE_PRIVATE
            )
                .getString(
                    "songs",
                    ""
                )
                .orEmpty()

        if (data.isBlank()) return

        songs.clear()

        data.split("\n")
            .take(60)
            .forEach { line ->

                val p =
                    line.split(
                        "|||",
                        limit = 5
                    )

                if (
                    p.size == 5 &&
                    p[0].isNotBlank()
                ) {

                    songs.add(
                        SongResult(
                            p[0],
                            p[1],
                            p[2],
                            p[3],
                            p[4]
                        )
                    )
                }
            }

        songs.forEachIndexed {
            index,
            song ->
            addSongView(
                song,
                index
            )
        }

        if (songs.isNotEmpty()) {

            currentIndex = 0

            status.text =
                "${songs.size} نتیجه ذخیره شده"
        }
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    12,
                    10,
                    12,
                    10
                )

                setBackgroundColor(
                    0xFF15151D.toInt()
                )

                setOnClickListener {
                    playSong(song)
                }
            }

        val cover =
            ImageView(this).apply {

                setBackgroundColor(
                    0xFF22222A.toInt()
                )

                scaleType =
                    ImageView.ScaleType.CENTER_CROP
            }

        row.addView(
            cover,
            LinearLayout.LayoutParams(
                58,
                58
            )
        )

        loadCover(
            song.cover,
            cover
        )

        val box =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    12,
                    0,
                    8,
                    0
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "${index + 1}. ${song.title}"

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                textSize = 15f

                maxLines = 2
            }

        val sub =
            TextView(this).apply {

                text =
                    "${song.artist} • ${song.site}"

                setTextColor(
                    0xFFAAAAAA.toInt()
                )

                textSize = 11f

                maxLines = 2
            }

        box.addView(title)
        box.addView(sub)

        row.addView(
            box,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        resultsContainer.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    8
                )
            }
        )
    }

    private fun loadCover(
        url: String,
        target: ImageView
    ) {

        if (url.isBlank()) return

        io.execute {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                        as HttpURLConnection

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                connection.connect()

                val bytes =
                    connection.inputStream
                        .use {
                            it.readBytes()
                        }

                connection.disconnect()

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    )

                if (
                    bitmap != null &&
                    !destroyed
                ) {

                    runOnUiThread {

                        if (!destroyed) {
                            target.setImageBitmap(
                                bitmap
                            )
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun playSong(
        song: SongResult
    ) {

        if (
            song.url.isBlank() ||
            destroyed
        ) {
            return
        }

        currentSong = song

        currentAudioUrl =
            song.url

        val foundIndex =
            songs.indexOfFirst {
                it.url == song.url
            }

        if (foundIndex >= 0) {
            currentIndex = foundIndex
        }

        titleText.text =
            song.title

        artistText.text =
            song.artist

        status.text =
            "در حال پخش از ${song.site}"

        if (song.cover.isNotBlank()) {
            loadCoverToVinyl(
                song.cover
            )
        } else {
            vinyl.clearCover()
        }

        saveHistory(song)

        sendServiceAction(
            MusicService.ACTION_PLAY,
            song.url,
            song.title,
            song.artist,
            song.cover
        )
    }

    private fun sendServiceAction(
        action: String,
        url: String,
        title: String,
        artist: String,
        cover: String
    ) {

        if (destroyed) return

        safelyStartService(
            Intent(
                this,
                MusicService::class.java
            ).apply {

                this.action = action

                putExtra(
                    MusicService.EXTRA_URL,
                    url
                )

                putExtra(
                    MusicService.EXTRA_TITLE,
                    title
                )

                putExtra(
                    MusicService.EXTRA_ARTIST,
                    artist
                )

                putExtra(
                    MusicService.EXTRA_COVER,
                    cover
                )
            }
        )
    }

    private fun sendServiceSimpleAction(
        action: String,
        extras: Intent.() -> Unit = {}
    ) {

        if (destroyed) return

        val intent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                this.action = action

                extras()
            }

        safelyStartService(intent)
    }

    private fun safelyStartService(
        intent: Intent
    ) {

        if (destroyed) return

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                ContextCompat.startForegroundService(
                    this,
                    intent
                )

            } else {

                startService(intent)
            }

        } catch (
            e: SecurityException
        ) {

            Toast.makeText(
                this,
                "اجازه اجرای سرویس پخش داده نشده",
                Toast.LENGTH_SHORT
            ).show()

        } catch (
            e: IllegalStateException
        ) {

            Toast.makeText(
                this,
                "سرویس پخش فعلاً در دسترس نیست",
                Toast.LENGTH_SHORT
            ).show()

        } catch (
            e: Exception
        ) {

            Toast.makeText(
                this,
                "خطا در اجرای سرویس پخش",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun previousSong() {

        if (songs.isEmpty()) return

        currentIndex =
            if (currentIndex <= 0) {
                songs.lastIndex
            } else {
                currentIndex - 1
            }

        playSong(
            songs[currentIndex]
        )
    }

    private fun nextSong() {

        if (songs.isEmpty()) return

        currentIndex =
            if (
                randomMode &&
                songs.size > 1
            ) {

                var next: Int

                do {
                    next =
                        (0 until songs.size).random()
                } while (
                    next == currentIndex
                )

                next

            } else {

                (currentIndex + 1) %
                    songs.size
            }

        playSong(
            songs[currentIndex]
        )
    }

    private fun updatePlayerProgress(
        playing: Boolean,
        position: Long,
        duration: Long
    ) {

        val d =
            duration.coerceAtLeast(0L)

        val p =
            position.coerceAtLeast(0L)

        seekBar.progress =
            if (d > 0) {

                (
                    (
                        p.toDouble() /
                        d.toDouble()
                    ) * 100.0
                )
                    .toInt()
                    .coerceIn(
                        0,
                        100
                    )

            } else {
                0
            }

        currentTimeText.text =
            formatTime(p)

        durationText.text =
            formatTime(d)

        playButton.text =
            if (playing) {
                "Ⅱ"
            } else {
                "▶"
            }

        if (playing) {
            vinyl.startRotation()
        } else {
            vinyl.stopRotation()
        }
    }

    private fun formatTime(
        ms: Long
    ): String {

        val total =
            (ms / 1000)
                .coerceAtLeast(0)

        return String.format(
            Locale.US,
            "%02d:%02d",
            total / 60,
            total % 60
        )
    }

    private fun updateActiveResultHighlight(
        mediaUrl: String
    ) {

        if (mediaUrl.isBlank()) return

        currentAudioUrl =
            mediaUrl

        val index =
            songs.indexOfFirst {
                it.url == mediaUrl
            }

        if (index >= 0) {

            currentIndex = index

            currentSong =
                songs[index]

            if (
                titleText.text.isNullOrBlank() ||
                titleText.text.toString()
                    .equals(
                        "Music",
                        true
                    )
            ) {
                titleText.text =
                    songs[index].title
            }

            if (
                artistText.text.isNullOrBlank() ||
                artistText.text.toString()
                    .equals(
                        "Unknown Artist",
                        true
                    )
            ) {
                artistText.text =
                    songs[index].artist
            }
        }
    }

    private fun loadCoverToVinyl(
        url: String
    ) {

        if (url.isBlank()) return

        io.execute {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                        as HttpURLConnection

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                connection.connect()

                val bytes =
                    connection.inputStream
                        .use {
                            it.readBytes()
                        }

                connection.disconnect()

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    )

                if (
                    bitmap != null &&
                    !destroyed
                ) {

                    runOnUiThread {

                        if (!destroyed) {
                            vinyl.setCover(
                                bitmap
                            )
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun clearLyrics() {

        lyricsText.text = ""

        lyricsText.visibility =
            View.GONE
    }

    private fun loadLyricsFor(
        title: String,
        artist: String
    ) {

        if (
            title.isBlank() ||
            destroyed
        ) {
            return
        }

        lyricsText.text = ""

        lyricsText.visibility =
            View.GONE

        io.execute {

            try {

                val url =
                    "https://api.lyrics.ovh/v1/" +
                    "${URLEncoder.encode(artist, "UTF-8")}/" +
                    URLEncoder.encode(
                        title,
                        "UTF-8"
                    )

                val connection =
                    URL(url)
                        .openConnection()
                        as HttpURLConnection

                connection.connectTimeout = 4000
                connection.readTimeout = 5000

                connection.connect()

                if (
                    connection.responseCode !in 200..299
                ) {

                    connection.disconnect()

                    return@execute
                }

                val text =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                connection.disconnect()

                val lyrics =
                    Regex(
                        "\\\"lyrics\\\"\\s*:\\s*\\\"(.*?)\\\"",
                        RegexOption.DOT_MATCHES_ALL
                    )
                        .find(text)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.replace(
                            "\\n",
                            "\n"
                        )
                        ?.replace(
                            "\\\"",
                            "\""
                        )

                if (
                    !lyrics.isNullOrBlank() &&
                    !destroyed
                ) {

                    runOnUiThread {

                        if (!destroyed) {

                            lyricsText.text =
                                lyrics

                            lyricsText.visibility =
                                View.VISIBLE
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun getSiteName(
        url: String
    ): String =
        ServerConfig.siteName(url)

    private fun decode(
        value: String
    ): String =
        try {
            URLDecoder.decode(
                value,
                "UTF-8"
            )
        } catch (_: Exception) {
            value
        }

    private fun cleanTitle(
        value: String
    ): String =
        value
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

    private fun saveCurrentSong() {

        val song =
            currentSong
                ?: songs.getOrNull(
                    currentIndex
                )

        if (song == null) {

            Toast.makeText(
                this,
                "آهنگی انتخاب نشده",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            LibraryManager.add(
                this,
                song
            )

            Toast.makeText(
                this,
                "به کتابخانه اضافه شد",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "ذخیره آهنگ انجام نشد",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveHistory(
        song: SongResult
    ) {

        val list =
            getHistory()
                .filter {
                    it.url != song.url
                }
                .toMutableList()

        list.add(
            0,
            song
        )

        val data =
            list
                .take(100)
                .joinToString("\n") {

                    listOf(
                        it.url,
                        it.title,
                        it.artist,
                        it.site,
                        it.cover
                    ).joinToString(
                        "|||"
                    )
                }

        getSharedPreferences(
            "history",
            MODE_PRIVATE
        )
            .edit()
            .putString(
                "songs",
                data
            )
            .apply()
    }

    private fun getHistory(): List<SongResult> {

        val data =
            getSharedPreferences(
                "history",
                MODE_PRIVATE
            )
                .getString(
                    "songs",
                    ""
                )
                .orEmpty()

        if (data.isBlank()) {
            return emptyList()
        }

        return data
            .split("\n")
            .mapNotNull {

                val p =
                    it.split(
                        "|||",
                        limit = 5
                    )

                if (
                    p.size == 5 &&
                    p[0].isNotBlank()
                ) {

                    SongResult(
                        p[0],
                        p[1],
                        p[2],
                        p[3],
                        p[4]
                    )

                } else {
                    null
                }
            }
    }

    private fun toggleHistory() {

        if (
            historyContainer.visibility ==
            View.VISIBLE
        ) {

            historyContainer.visibility =
                View.GONE

            return
        }

        historyContainer.removeAllViews()

        getHistory()
            .take(30)
            .forEachIndexed {
                i,
                song ->

                val view =
                    TextView(this).apply {

                        text =
                            "${i + 1}. ${song.title} — ${song.artist}"

                        textSize = 13f

                        setTextColor(
                            0xFFFFFFFF.toInt()
                        )

                        setPadding(
                            12,
                            14,
                            12,
                            14
                        )

                        setOnClickListener {
                            playSong(song)
                        }
                    }

                historyContainer.addView(
                    view
                )
            }

        historyContainer.visibility =
            View.VISIBLE
    }

    private fun downloadCurrentSong() {

        val song =
            currentSong
                ?: songs.getOrNull(
                    currentIndex
                )

        if (
            song == null ||
            song.url.isBlank()
        ) {

            Toast.makeText(
                this,
                "آهنگی برای دانلود انتخاب نشده",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            downloadFuture?.isDone == false
        ) {

            Toast.makeText(
                this,
                "دانلود در حال انجام است",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        cancelDownloadRequested = false
        pauseDownloadRequested = false

        downloadProgress.visibility =
            View.VISIBLE

        downloadText.visibility =
            View.VISIBLE

        cancelDownloadButton.visibility =
            View.VISIBLE

        pauseDownloadButton.visibility =
            View.VISIBLE

        downloadProgress.progress = 0

        downloadText.text =
            "در حال دانلود..."

        pauseDownloadButton.text =
            "Ⅱ"

        downloadFuture =
            downloadExecutor.submit {

                downloadSong(song)
            }
    }

    private fun downloadSong(
        song: SongResult
    ) {

        var outputUri:
            android.net.Uri? = null

        var connection:
            HttpURLConnection? = null

        try {

            connection =
                URL(song.url)
                    .openConnection()
                    as HttpURLConnection

            activeConnection =
                connection

            connection.connectTimeout =
                10000

            connection.readTimeout =
                15000

            connection.instanceFollowRedirects =
                true

            connection.connect()

            val response =
                connection.responseCode

            if (
                response !in 200..299
            ) {
                throw Exception(
                    "HTTP $response"
                )
            }

            val length =
                connection.contentLengthLong

            val name =
                cleanTitle(
                    song.title
                )
                    .ifBlank {
                        "music"
                    }
                    .replace(
                        Regex(
                            "[^A-Za-z0-9_\\- ]"
                        ),
                        "_"
                    )
                    .take(60)
                    + ".mp3"

            if (
                Build.VERSION.SDK_INT >= 29
            ) {

                val values =
                    ContentValues().apply {

                        put(
                            MediaStore.Downloads.DISPLAY_NAME,
                            name
                        )

                        put(
                            MediaStore.Downloads.MIME_TYPE,
                            "audio/mpeg"
                        )

                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS +
                            "/MusicFinder"
                        )
                    }

                outputUri =
                    contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )
                        ?: throw Exception(
                            "storage"
                        )

                val outputStream =
                    contentResolver
                        .openOutputStream(
                            outputUri
                        )
                        ?: throw Exception(
                            "output"
                        )

                outputStream.use { out ->

                    connection.inputStream.use {
                        input ->

                        copyDownload(
                            input,
                            out,
                            length
                        )
                    }
                }

            } else {

                val dir =
                    Environment
                        .getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )

                val folder =
                    java.io.File(
                        dir,
                        "MusicFinder"
                    )

                if (!folder.exists()) {
                    folder.mkdirs()
                }

                val file =
                    java.io.File(
                        folder,
                        name
                    )

                FileOutputStream(file).use {
                    out ->

                    connection.inputStream.use {
                        input ->

                        copyDownload(
                            input,
                            out,
                            length
                        )
                    }
                }
            }

            if (
                !cancelDownloadRequested
            ) {

                runOnUiThread {

                    if (!destroyed) {

                        downloadText.text =
                            "دانلود کامل شد"

                        downloadProgress.progress =
                            100
                    }
                }

            } else {

                outputUri?.let {

                    try {
                        contentResolver.delete(
                            it,
                            null,
                            null
                        )
                    } catch (_: Exception) {
                    }
                }
            }

        } catch (e: Exception) {

            if (
                !cancelDownloadRequested &&
                !destroyed
            ) {

                runOnUiThread {

                    downloadText.text =
                        "خطا در دانلود"
                }
            }

        } finally {

            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }

            activeConnection = null

            runOnUiThread {

                if (!destroyed) {

                    handler.postDelayed(
                        {

                            downloadProgress.visibility =
                                View.GONE

                            cancelDownloadButton.visibility =
                                View.GONE

                            pauseDownloadButton.visibility =
                                View.GONE

                        },
                        1200L
                    )
                }
            }
        }
    }

    private fun copyDownload(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long
    ) {

        val buffer =
            ByteArray(32 * 1024)

        var done = 0L

        while (true) {

            if (
                cancelDownloadRequested
            ) {
                break
            }

            while (
                pauseDownloadRequested &&
                !cancelDownloadRequested
            ) {

                try {
                    Thread.sleep(150)
                } catch (
                    e: InterruptedException
                ) {
                    Thread.currentThread()
                        .interrupt()
                    return
                }
            }

            val count =
                try {
                    input.read(buffer)
                } catch (
                    e: Exception
                ) {
                    if (
                        cancelDownloadRequested
                    ) {
                        break
                    }

                    throw e
                }

            if (count < 0) {
                break
            }

            if (count == 0) {
                continue
            }

            out.write(
                buffer,
                0,
                count
            )

            done += count

            if (total > 0) {

                val progress =
                    (
                        done * 100L /
                        total
                    )
                        .toInt()
                        .coerceIn(
                            0,
                            100
                        )

                if (!destroyed) {

                    runOnUiThread {

                        if (!destroyed) {

                            downloadProgress.progress =
                                progress

                            if (
                                !pauseDownloadRequested
                            ) {

                                downloadText.text =
                                    "$progress%"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cancelDownload() {

        cancelDownloadRequested = true
        pauseDownloadRequested = false

        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }

        downloadText.text =
            "دانلود لغو شد"

        downloadProgress.progress = 0
    }

    private fun toggleDownloadPause() {

        if (
            downloadFuture?.isDone != false
        ) {
            return
        }

        pauseDownloadRequested =
            !pauseDownloadRequested

        pauseDownloadButton.text =
            if (pauseDownloadRequested) {
                "▶"
            } else {
                "Ⅱ"
            }

        downloadText.text =
            if (pauseDownloadRequested) {
                "دانلود متوقف شد"
            } else {
                "در حال دانلود..."
            }
    }

    override fun onStart() {

        super.onStart()

        if (receiverRegistered) {
            return
        }

        try {

            val filter =
                IntentFilter(
                    MusicService.UPDATE
                )

            if (
                Build.VERSION.SDK_INT >= 33
            ) {

                registerReceiver(
                    playerReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )

            } else {

                registerReceiver(
                    playerReceiver,
                    filter
                )
            }

            receiverRegistered = true

        } catch (_: Exception) {

            receiverRegistered = false
        }
    }

    override fun onStop() {

        if (receiverRegistered) {

            try {
                unregisterReceiver(
                    playerReceiver
                )
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }

        super.onStop()
    }

    override fun onDestroy() {

        destroyed = true

        cancelSearchCallbacks()

        cancelDownloadRequested = true
        pauseDownloadRequested = false

        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }

        activeConnection = null

        try {
            downloadFuture?.cancel(
                true
            )
        } catch (_: Exception) {
        }

        downloadFuture = null

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            web.stopLoading()
            web.removeJavascriptInterface(
                "MusicFinder"
            )
            web.destroy()
        } catch (_: Exception) {
        }

        try {
            io.shutdownNow()
            downloadExecutor.shutdownNow()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
