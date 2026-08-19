package com.kafshar.musicfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.BitmapFactory
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
import android.widget.ImageView
import android.widget.LinearLayout
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
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var web: WebView

    private lateinit var query: EditText
    private lateinit var searchButton: TextView

    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var status: TextView

    private lateinit var coverImage: ImageView

    private lateinit var playButton: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var downloadButton: TextView

    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationText: TextView

    private lateinit var loading: ProgressBar
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadPercent: TextView

    private lateinit var playlistContainer: LinearLayout

    private lateinit var player: ExoPlayer

    data class Song(
        val title: String,
        val artist: String,
        val audioUrl: String,
        val coverUrl: String,
        val pageUrl: String
    )

    private val playlist = ArrayList<Song>()

    private val searchPages = ArrayList<String>()

    private var searchPageIndex = 0

    private var currentSongIndex = -1

    private var shuffleMode = false

    private var searchingPlaylist = false

    private var currentAudioUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        query = findViewById(R.id.query)
        searchButton = findViewById(R.id.search)

        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)

        status = findViewById(R.id.status)

        coverImage = findViewById(R.id.coverImage)

        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        downloadButton = findViewById(R.id.downloadButton)

        progress = findViewById(R.id.progress)
        currentTime = findViewById(R.id.currentTime)
        durationText = findViewById(R.id.duration)

        loading = findViewById(R.id.loading)

        downloadProgress = findViewById(R.id.downloadProgress)
        downloadPercent = findViewById(R.id.downloadPercent)

        playlistContainer =
            findViewById(R.id.playlistContainer)

        web = findViewById(R.id.web)

        setupPlayer()
        setupWebView()
        setupControls()
    }

    private fun setupPlayer() {

        player =
            ExoPlayer.Builder(this).build()

        player.addListener(
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
                    state: Int
                ) {

                    when (state) {

                        Player.STATE_BUFFERING -> {

                            loading.visibility =
                                View.VISIBLE

                            status.text =
                                "در حال بارگذاری آهنگ..."
                        }

                        Player.STATE_READY -> {

                            loading.visibility =
                                View.GONE

                            status.text =
                                "در حال پخش"

                            updateProgress()
                        }

                        Player.STATE_ENDED -> {

                            loading.visibility =
                                View.GONE

                            playNextSong()
                        }
                    }
                }

                override fun onPlayerError(
                    error: androidx.media3.common.PlaybackException
                ) {

                    loading.visibility =
                        View.GONE

                    status.text =
                        "این آهنگ قابل پخش نیست"

                    playNextSong()
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        web.visibility =
            View.INVISIBLE

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            cacheMode =
                WebSettings.LOAD_DEFAULT

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

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    if (
                        url.contains(
                            "google.com/search"
                        )
                    ) {

                        extractSearchResults()

                    } else {

                        extractSongPage()
                    }
                }
            }
    }

    private fun setupControls() {

        searchButton.setOnClickListener {

            searchMusic()
        }

        query.setOnEditorActionListener {
                _, actionId, _ ->

            if (
                actionId ==
                EditorInfo.IME_ACTION_SEARCH
            ) {

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

                if (
                    currentSongIndex >= 0
                ) {

                    player.play()

                } else if (
                    playlist.isNotEmpty()
                ) {

                    playSong(0)
                }
            }
        }

        previousButton.setOnClickListener {

            playPreviousSong()
        }

        nextButton.setOnClickListener {

            playNextSong()
        }

        shuffleButton.setOnClickListener {

            shuffleMode =
                !shuffleMode

            shuffleButton.text =
                if (shuffleMode) {
                    "🔀"
                } else {
                    "↕"
                }

            status.text =
                if (shuffleMode) {
                    "پخش تصادفی فعال شد"
                } else {
                    "پخش ترتیبی فعال شد"
                }
        }

        downloadButton.setOnClickListener {

            downloadCurrentSong()
        }

        progress.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    bar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {

                    if (!fromUser) return

                    val duration =
                        player.duration

                    if (duration > 0) {

                        player.seekTo(
                            duration *
                                    value /
                                    100L
                        )
                    }
                }

                override fun onStartTrackingTouch(
                    bar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    bar: SeekBar?
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
                "نام خواننده یا آهنگ را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        player.stop()

        playlist.clear()
        searchPages.clear()

        currentSongIndex = -1
        searchPageIndex = 0

        currentAudioUrl = ""

        searchingPlaylist = true

        playlistContainer.removeAllViews()

        titleText.text =
            "در حال جستجو..."

        artistText.text =
            text

        coverImage.setImageResource(
            android.R.drawable.ic_media_play
        )

        status.text =
            "در حال پیدا کردن آهنگ‌ها..."

        loading.visibility =
            View.VISIBLE

        val q =
            "\"$text\" " +
                    "(site:rozmusic.com OR " +
                    "site:mybia2music.com OR " +
                    "site:musicdel.ir OR " +
                    "site:musics-fa.com)"

        val encoded =
            URLEncoder.encode(
                q,
                "UTF-8"
            )

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
                            href.indexOf("google.com") < 0 &&
                            result.indexOf(href) < 0
                        ) {

                            result.push(href);
                        }
                    }
                }

                if (
                    result.length > 0
                ) {

                    MusicFinder.results(
                        result.join("###")
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();

        """.trimIndent()

        web.evaluateJavascript(
            js,
            null
        )
    }

    private fun loadNextSearchPage() {

        if (
            searchPageIndex >=
            searchPages.size
        ) {

            searchingPlaylist = false

            loading.visibility =
                View.GONE

            if (playlist.isEmpty()) {

                status.text =
                    "آهنگ قابل پخش پیدا نشد"

            } else {

                status.text =
                    "${playlist.size} آهنگ پیدا شد"

                showPlaylist()

                playSong(0)
            }

            return
        }

        val url =
            searchPages[
                searchPageIndex
            ]

        searchPageIndex++

        status.text =
            "در حال بررسی ${searchPageIndex} از ${searchPages.size}..."

        web.loadUrl(url)
    }

    private fun extractSongPage() {

        val js = """

            (function() {

                var title = "";

                var artist = "";

                var cover = "";

                var audio = [];

                var ogTitle =
                    document.querySelector(
                        'meta[property="og:title"]'
                    );

                var description =
                    document.querySelector(
                        'meta[name="description"]'
                    );

                var ogImage =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if (ogTitle) {

                    title =
                        ogTitle.content || "";
                }

                if (!title) {

                    title =
                        document.title || "";
                }

                if (ogImage) {

                    cover =
                        ogImage.content || "";
                }

                var artistMeta =
                    document.querySelector(
                        'meta[name="author"]'
                    );

                if (artistMeta) {

                    artist =
                        artistMeta.content || "";
                }

                var media =
                    document.querySelectorAll(
                        "audio, audio source, video source"
                    );

                for (
                    var i = 0;
                    i < media.length;
                    i++
                ) {

                    var src =
                        media[i].src ||
                        media[i].getAttribute("src") ||
                        "";

                    if (src) {

                        audio.push(src);
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

                    var low =
                        href.toLowerCase();

                    if (
                        low.indexOf(".mp3") >= 0 ||
                        low.indexOf(".m4a") >= 0 ||
                        low.indexOf(".aac") >= 0 ||
                        low.indexOf(".ogg") >= 0 ||
                        low.indexOf(".wav") >= 0
                    ) {

                        audio.push(href);
                    }
                }

                var unique = [];

                for (
                    var k = 0;
                    k < audio.length;
                    k++
                ) {

                    if (
                        audio[k] &&
                        unique.indexOf(audio[k]) < 0
                    ) {

                        unique.push(audio[k]);
                    }
                }

                if (
                    unique.length > 0
                ) {

                    MusicFinder.song(
                        encodeURIComponent(title) +
                        "|||" +
                        encodeURIComponent(artist) +
                        "|||" +
                        encodeURIComponent(cover) +
                        "|||" +
                        encodeURIComponent(unique[0])
                    );

                } else {

                    MusicFinder.notFound();
                }

            })();

        """.trimIndent()

        web.evaluateJavascript(
            js,
            null
        )
    }

    private fun addSong(
        title: String,
        artist: String,
        cover: String,
        audio: String,
        page: String
    ) {

        if (audio.isBlank()) {

            loadNextSearchPage()

            return
        }

        val cleanTitle =
            if (
                title.isBlank()
            ) {
                query.text.toString()
            } else {
                title
            }

        val cleanArtist =
            if (
                artist.isBlank()
            ) {
                "Music Finder"
            } else {
                artist
            }

        val song =
            Song(
                cleanTitle,
                cleanArtist,
                audio,
                cover,
                page
            )

        if (
            playlist.none {
                it.audioUrl == audio
            }
        ) {

            playlist.add(song)
        }

        loadNextSearchPage()
    }

    private fun showPlaylist() {

        playlistContainer.removeAllViews()

        for (
            index in playlist.indices
        ) {

            val song =
                playlist[index]

            val row =
                TextView(this)

            row.text =
                "${index + 1}. ${song.title}"

            row.textSize = 14f

            row.setTextColor(
                resources.getColor(
                    R.color.white
                )
            )

            row.setPadding(
                18,
                18,
                18,
                18
            )

            row.setOnClickListener {

                playSong(index)
            }

            playlistContainer.addView(
                row
            )
        }
    }

    private fun playSong(index: Int) {

        if (
            index < 0 ||
            index >= playlist.size
        ) {
            return
        }

        currentSongIndex =
            index

        val song =
            playlist[index]

        currentAudioUrl =
            song.audioUrl

        titleText.text =
            cleanTitle(song.title)

        artistText.text =
            song.artist

        status.text =
            "در حال پخش..."

        loadCover(
            song.coverUrl
        )

        player.stop()

        player.setMediaItem(
            MediaItem.fromUri(
                song.audioUrl
            )
        )

        player.prepare()

        player.play()

        highlightCurrentSong()
    }

    private fun cleanTitle(
        text: String
    ): String {

        return text
            .replace(
                Regex(
                    "\\s+"
                ),
                " "
            )
            .trim()
    }

    private fun highlightCurrentSong() {

        for (
            i in 0 until
                    playlistContainer.childCount
        ) {

            val view =
                playlistContainer.getChildAt(i)

            if (
                view is TextView
            ) {

                view.alpha =
                    if (
                        i ==
                        currentSongIndex
                    ) {
                        1f
                    } else {
                        0.55f
                    }
            }
        }
    }

    private fun playNextSong() {

        if (playlist.isEmpty()) {

            return
        }

        val nextIndex: Int

        if (shuffleMode) {

            if (playlist.size == 1) {

                nextIndex = 0

            } else {

                var randomIndex =
                    (0 until playlist.size)
                        .random()

                while (
                    randomIndex ==
                    currentSongIndex
                ) {

                    randomIndex =
                        (0 until playlist.size)
                            .random()
                }

                nextIndex =
                    randomIndex
            }

        } else {

            nextIndex =
                if (
                    currentSongIndex + 1 <
                    playlist.size
                ) {
                    currentSongIndex + 1
                } else {
                    0
                }
        }

        playSong(nextIndex)
    }

    private fun playPreviousSong() {

        if (
            playlist.isEmpty()
        ) {
            return
        }

        val index =
            if (
                currentSongIndex > 0
            ) {
                currentSongIndex - 1
            } else {
                playlist.size - 1
            }

        playSong(index)
    }

    private fun loadCover(
        url: String
    ) {

        if (url.isBlank()) {

            coverImage.setImageResource(
                android.R.drawable.ic_media_play
            )

            return
        }

        thread {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    15000

                connection.connect()

                val bitmap =
                    BitmapFactory.decodeStream(
                        connection.inputStream
                    )

                connection.disconnect()

                if (
                    bitmap != null
                ) {

                    runOnUiThread {

                        coverImage.setImageBitmap(
                            bitmap
                        )
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun updateProgress() {

        if (
            isFinishing
        ) {
            return
        }

        val duration =
            player.duration

        if (
            duration <= 0
        ) {
            return
        }

        val position =
            player.currentPosition

        val value =
            (
                position.toDouble() /
                        duration.toDouble() *
                        100
                ).toInt()

        progress.progress =
            value

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

    private fun formatTime(
        ms: Long
    ): String {

        if (
            ms <= 0
        ) {
            return "0:00"
        }

        val seconds =
            ms / 1000

        val minutes =
            seconds / 60

        val sec =
            seconds % 60

        return String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            sec
        )
    }

    private fun downloadCurrentSong() {

        val url =
            currentAudioUrl

        if (
            url.isBlank()
        ) {

            Toast.makeText(
                this,
                "اول یک آهنگ پخش کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val fileName =
            safeFileName(
                if (
                    titleText.text
                        .toString()
                        .isNotBlank()
                ) {
                    titleText.text
                        .toString()
                } else {
                    query.text.toString()
                }
            ) + ".mp3"

        downloadButton.text =
            "..."

        downloadProgress.visibility =
            View.VISIBLE

        downloadPercent.visibility =
            View.VISIBLE

        downloadProgress.progress =
            0

        downloadPercent.text =
            "0%"

        status.text =
            "در حال دانلود..."

        thread {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    30000

                connection.requestMethod =
                    "GET"

                connection.connect()

                if (
                    connection.responseCode !in
                    200..299
                ) {

                    throw Exception()
                }

                val total =
                    connection.contentLengthLong

                val input =
                    BufferedInputStream(
                        connection.inputStream
                    )

                if (
                    Build.VERSION.SDK_INT >= 29
                ) {

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

                        val output =
                            contentResolver
                                .openOutputStream(uri)
                                ?: throw Exception()

                        input.use { inputStream ->

                            output.use { outputStream ->

                                copyWithProgress(
                                    inputStream,
                                    outputStream,
                                    total
                                )
                            }
                        }

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

                    val directory =
                        Environment
                            .getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MUSIC
                            )

                    if (
                        !directory.exists()
                    ) {

                        directory.mkdirs()
                    }

                    val file =
                        File(
                            directory,
                            fileName
                        )

                    val output =
                        FileOutputStream(file)

                    input.use { inputStream ->

                        output.use { outputStream ->

                            copyWithProgress(
                                inputStream,
                                outputStream,
                                total
                            )
                        }
                    }
                }

                connection.disconnect()

                runOnUiThread {

                    downloadButton.text =
                        "⬇"

                    downloadProgress.progress =
                        100

                    downloadPercent.text =
                        "100%"

                    status.text =
                        "دانلود کامل شد ✓"

                    Toast.makeText(
                        this,
                        "آهنگ در پوشه Music ذخیره شد",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (_: Exception) {

                runOnUiThread {

                    downloadButton.text =
                        "⬇"

                    downloadProgress.visibility =
                        View.GONE

                    downloadPercent.visibility =
                        View.GONE

                    status.text =
                        "دانلود انجام نشد"
                }
            }
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        total: Long
    ) {

        val buffer =
            ByteArray(8192)

        var downloaded = 0L

        var count: Int

        while (
            input.read(buffer)
                .also {
                    count = it
                } != -1
        ) {

            output.write(
                buffer,
                0,
                count
            )

            downloaded +=
                count

            if (
                total > 0
            ) {

                val percent =
                    (
                        downloaded.toDouble() /
                                total.toDouble() *
                                100
                        ).toInt()
                            .coerceIn(
                                0,
                                100
                            )

                runOnUiThread {

                    downloadProgress.progress =
                        percent

                    downloadPercent.text =
                        "$percent%"
                }
            }
        }
    }

    private fun safeFileName(
        text: String
    ): String {

        val name =
            text.replace(
                Regex(
                    "[\\\\/:*?\"<>|]"
                ),
                "_"
            )
                .trim()

        return if (
            name.isEmpty()
        ) {
            "Music_Finder"
        } else {
            name.take(80)
        }
    }

    inner class Bridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

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

                searchPages.clear()

                searchPages.addAll(
                    list.take(10)
                )

                searchPageIndex =
                    0

                if (
                    searchPages.isEmpty()
                ) {

                    notFound()

                } else {

                    loadNextSearchPage()
                }
            }
        }

        @JavascriptInterface
        fun song(
            data: String
        ) {

            runOnUiThread {

                try {

                    val parts =
                        data.split("|||")

                    if (
                        parts.size < 4
                    ) {

                        loadNextSearchPage()

                        return@runOnUiThread
                    }

                    val title =
                        java.net.URLDecoder
                            .decode(
                                parts[0],
                                "UTF-8"
                            )

                    val artist =
                        java.net.URLDecoder
                            .decode(
                                parts[1],
                                "UTF-8"
                            )

                    val cover =
                        java.net.URLDecoder
                            .decode(
                                parts[2],
                                "UTF-8"
                            )

                    val audio =
                        java.net.URLDecoder
                            .decode(
                                parts[3],
                                "UTF-8"
                            )

                    addSong(
                        title,
                        artist,
                        cover,
                        audio,
                        web.url ?: ""
                    )

                } catch (_: Exception) {

                    loadNextSearchPage()
                }
            }
        }

        @JavascriptInterface
        fun notFound() {

            runOnUiThread {

                loadNextSearchPage()
            }
        }
    }

    override fun onDestroy() {

        if (
            ::player.isInitialized
        ) {

            player.release()
        }

        if (
            ::web.isInitialized
        ) {

            web.destroy()
        }

        super.onDestroy()
    }
}
