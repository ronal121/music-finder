package com.kafshar.musicfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

data class SongResult(
    val url: String,
    val title: String,
    val artist: String,
    val site: String,
    val cover: String = ""
)

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

    private lateinit var downloadButton: TextView
    private lateinit var cancelDownloadButton: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadText: TextView

    private lateinit var resultsContainer: LinearLayout

    private val songs =
        ArrayList<SongResult>()

    private var currentIndex = -1

    private var currentAudioUrl = ""

    private var randomMode = false

    private var searching = false

    private var searchGeneration = 0

    private var downloadThread: Thread? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        query =
            findViewById(R.id.query)

        status =
            findViewById(R.id.status)

        titleText =
            findViewById(R.id.titleText)

        artistText =
            findViewById(R.id.artistText)

        playButton =
            findViewById(R.id.playButton)

        previousButton =
            findViewById(R.id.previousButton)

        nextButton =
            findViewById(R.id.nextButton)

        randomButton =
            findViewById(R.id.randomButton)

        downloadButton =
            findViewById(R.id.downloadButton)

        cancelDownloadButton =
            findViewById(R.id.cancelDownloadButton)

        downloadProgress =
            findViewById(R.id.downloadProgress)

        downloadText =
            findViewById(R.id.downloadText)

        resultsContainer =
            findViewById(R.id.resultsContainer)

        web =
            findViewById(R.id.web)

        requestNotificationPermission()

        setupWebView()

        setupButtons()

        status.text =
            "نام آهنگ یا خواننده را جستجو کنید"
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            mediaPlaybackRequiresUserGesture = false

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

                    if (
                        url.contains(
                            "google.com/search"
                        )
                    ) {

                        extractGoogleResults()

                    } else {

                        extractMusicPage(url)
                    }
                }
            }
    }

    private fun setupButtons() {

        findViewById<TextView>(
            R.id.search
        ).setOnClickListener {

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

            sendServiceAction(
                MusicService.ACTION_PLAY,
                currentAudioUrl,
                titleText.text.toString()
            )
        }

        previousButton.setOnClickListener {

            previousSong()
        }

        nextButton.setOnClickListener {

            nextSong()
        }

        randomButton.setOnClickListener {

            randomMode =
                !randomMode

            randomButton.text =
                if (randomMode)
                    "🔀 روشن"
                else
                    "🔀"

            Toast.makeText(
                this,
                if (randomMode)
                    "پخش تصادفی فعال شد"
                else
                    "پخش ترتیبی فعال شد",
                Toast.LENGTH_SHORT
            ).show()
        }

        downloadButton.setOnClickListener {

            downloadCurrentSong()
        }

        cancelDownloadButton.setOnClickListener {

            cancelDownload()
        }
    }

    private fun searchMusic() {

        val text =
            query.text.toString().trim()

        if (text.isEmpty()) {

            Toast.makeText(
                this,
                "نام آهنگ یا خواننده را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        searchGeneration++

        searching = true

        songs.clear()

        currentIndex = -1

        resultsContainer.removeAllViews()

        titleText.text = text

        artistText.text =
            "در حال جستجو..."

        status.text =
            "در حال جستجوی سایت‌ها..."

        web.visibility = View.GONE

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

        web.loadUrl(
            "https://www.google.com/search?q=$encoded"
        )
    }

    private fun extractGoogleResults() {

        val generation =
            searchGeneration

        val script = """
            (function() {

                var links =
                    document.querySelectorAll("a");

                var found = [];

                for (
                    var i = 0;
                    i < links.length;
                    i++
                ) {

                    var href =
                        links[i].href || "";

                    var text =
                        links[i].innerText || "";

                    var lower =
                        href.toLowerCase();

                    if (
                        lower.indexOf("rozmusic.com") >= 0 ||
                        lower.indexOf("mybia2music.com") >= 0 ||
                        lower.indexOf("musicdel.ir") >= 0 ||
                        lower.indexOf("musics-fa.com") >= 0
                    ) {

                        if (
                            href.indexOf("google.com") < 0 &&
                            found.indexOf(href) < 0
                        ) {

                            found.push(
                                href + "|||" +
                                text.replace(
                                    /[\r\n]+/g,
                                    " "
                                )
                            );
                        }
                    }
                }

                MusicFinder.results(
                    found.join("###")
                );

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun extractMusicPage(
        pageUrl: String
    ) {

        val site =
            getSiteName(pageUrl)

        val script = """
            (function() {

                var title = "";

                var artist = "";

                var cover = "";

                var metaTitle =
                    document.querySelector(
                        'meta[property="og:title"]'
                    );

                if (metaTitle) {
                    title =
                        metaTitle.content || "";
                }

                var h1 =
                    document.querySelector("h1");

                if (
                    !title &&
                    h1
                ) {
                    title =
                        h1.innerText || "";
                }

                var image =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if (image) {
                    cover =
                        image.content || "";
                }

                var audioLinks = [];

                var media =
                    document.querySelectorAll(
                        "audio source, audio, video source, video, a"
                    );

                for (
                    var i = 0;
                    i < media.length;
                    i++
                ) {

                    var el =
                        media[i];

                    var src =
                        el.src ||
                        el.href ||
                        "";

                    var lower =
                        src.toLowerCase();

                    if (
                        lower.indexOf(".mp3") >= 0 ||
                        lower.indexOf(".m4a") >= 0 ||
                        lower.indexOf(".aac") >= 0 ||
                        lower.indexOf(".ogg") >= 0 ||
                        lower.indexOf(".wav") >= 0 ||
                        lower.indexOf(".flac") >= 0 ||
                        lower.indexOf("dl.") >= 0
                    ) {

                        if (
                            audioLinks.indexOf(src) < 0
                        ) {

                            audioLinks.push(src);
                        }
                    }
                }

                var text =
                    document.body.innerText || "";

                MusicFinder.page(
                    encodeURIComponent(title) +
                    "###" +
                    encodeURIComponent(artist) +
                    "###" +
                    encodeURIComponent(cover) +
                    "###" +
                    encodeURIComponent(
                        audioLinks.join("|||")
                    )
                );

            })();
        """.trimIndent()

        web.evaluateJavascript(
            script,
            null
        )
    }

    private fun getSiteName(
        url: String
    ): String {

        val lower =
            url.lowercase()

        return when {

            lower.contains(
                "rozmusic.com"
            ) ->
                "RozMusic"

            lower.contains(
                "mybia2music.com"
            ) ->
                "Bia2Music"

            lower.contains(
                "musicdel.ir"
            ) ->
                "Musicdel"

            lower.contains(
                "musics-fa.com"
            ) ->
                "Musics-FA"

            else ->
                "سایت موسیقی"
        }
    }

    inner class Bridge {

        @JavascriptInterface
        fun results(
            data: String
        ) {

            runOnUiThread {

                val generation =
                    searchGeneration

                val items =
                    data.split("###")
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotEmpty()
                        }

                if (
                    items.isEmpty()
                ) {

                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                status.text =
                    "در حال بررسی ${items.size} نتیجه..."

                /*
                 * حداکثر ۳۰ نتیجه را بررسی می‌کنیم.
                 * اگر تعداد بیشتری توسط Google آمد،
                 * می‌توان بعداً این عدد را افزایش داد.
                 */
                val limited =
                    items.take(30)

                processResultPages(
                    limited,
                    0,
                    generation
                )
            }
        }

        @JavascriptInterface
        fun page(
            data: String
        ) {

            runOnUiThread {

                val parts =
                    data.split("###")

                if (parts.size < 4) {
                    return@runOnUiThread
                }

                val title =
                    decode(parts[0])

                val artist =
                    decode(parts[1])

                val cover =
                    decode(parts[2])

                val audioString =
                    decode(parts[3])

                val audio =
                    audioString
                        .split("|||")
                        .firstOrNull {
                            it.isNotBlank()
                        }
                        ?: ""

                if (audio.isBlank()) {
                    return@runOnUiThread
                }

                val pageUrl =
                    web.url ?: ""

                val song =
                    SongResult(
                        url = audio,
                        title =
                            if (title.isBlank())
                                query.text.toString()
                            else
                                cleanTitle(title),
                        artist =
                            if (artist.isBlank())
                                "Music Finder"
                            else
                                artist,
                        site =
                            getSiteName(pageUrl),
                        cover = cover
                    )

                addSong(song)
            }
        }
    }

    private fun processResultPages(
        items: List<String>,
        index: Int,
        generation: Int
    ) {

        if (
            generation != searchGeneration
        ) {
            return
        }

        if (
            index >= items.size
        ) {

            searching = false

            status.text =
                if (songs.isEmpty())
                    "آهنگ قابل پخش پیدا نشد"
                else
                    "${songs.size} نتیجه پیدا شد"

            if (
                songs.isNotEmpty() &&
                currentIndex == -1
            ) {

                currentIndex = 0

                playSong(
                    songs[0]
                )
            }

            return
        }

        val item =
            items[index]

        val url =
            item.substringBefore("|||")

        if (url.isBlank()) {

            processResultPages(
                items,
                index + 1,
                generation
            )

            return
        }

        /*
         * هر سایت فقط ۵ ثانیه فرصت دارد.
         * اگر گیر کرد سراغ نتیجه بعدی می‌رویم.
         */
        val handler =
            android.os.Handler(
                mainLooper
            )

        var finished = false

        val timeout =
            Runnable {

                if (!finished) {

                    finished = true

                    processResultPages(
                        items,
                        index + 1,
                        generation
                    )
                }
            }

        handler.postDelayed(
            timeout,
            5000
        )

        web.loadUrl(url)

        /*
         * بعد از page() نتیجه به لیست اضافه می‌شود.
         * برای اینکه زنجیره جستجو جلو برود،
         * کمی بعد نتیجه بعدی را هم بررسی می‌کنیم.
         */
        handler.postDelayed(
            {

                if (!finished) {

                    finished = true

                    processResultPages(
                        items,
                        index + 1,
                        generation
                    )
                }

            },
            1200
        )
    }

    private fun addSong(
        song: SongResult
    ) {

        if (
            songs.any {
                it.url == song.url
            }
        ) {
            return
        }

        songs.add(song)

        addSongView(
            song,
            songs.size - 1
        )
    }

    private fun addSongView(
        song: SongResult,
        index: Int
    ) {

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.VERTICAL

        row.setPadding(
            18,
            14,
            18,
            14
        )

        row.setBackgroundColor(
            0xFF18181F.toInt()
        )

        val title =
            TextView(this)

        title.text =
            "${index + 1}. ${song.title}"

        title.textSize =
            15f

        title.setTextColor(
            0xFFFFFFFF.toInt()
        )

        val info =
            TextView(this)

        info.text =
            "${song.artist}  •  ${song.site}"

        info.textSize =
            12f

        info.setTextColor(
            0xFFAAAAAA.toInt()
        )

        row.addView(title)
        row.addView(info)

        row.setOnClickListener {

            currentIndex =
                songs.indexOf(song)

            playSong(song)
        }

        resultsContainer.addView(
            row
        )
    }

    private fun cleanTitle(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    "دانلود|آهنگ|موزیک|\\|.*"
                ),
                ""
            )
            .trim()
    }

    private fun decode(
        value: String
    ): String {

        return try {

            java.net.URLDecoder.decode(
                value,
                "UTF-8"
            )

        } catch (
            _: Exception
        ) {

            value
        }
    }

    private fun playSong(
        song: SongResult
    ) {

        currentAudioUrl =
            song.url

        titleText.text =
            song.title

        artistText.text =
            "${song.artist}  •  ${song.site}"

        status.text =
            "در حال پخش..."

        sendServiceAction(
            MusicService.ACTION_PLAY,
            song.url,
            song.title
        )
    }

    private fun nextSong() {

        if (
            songs.isEmpty()
        ) {
            return
        }

        currentIndex =
            if (randomMode) {

                if (songs.size == 1)
                    0
                else {

                    var next

                    do {
                        next =
                            (0 until songs.size)
                                .random()
                    } while (
                        next == currentIndex
                    )

                    next
                }

            } else {

                (currentIndex + 1) %
                        songs.size
            }

        playSong(
            songs[currentIndex]
        )
    }

    private fun previousSong() {

        if (
            songs.isEmpty()
        ) {
            return
        }

        currentIndex =
            if (
                currentIndex <= 0
            )
                songs.size - 1
            else
                currentIndex - 1

        playSong(
            songs[currentIndex]
        )
    }

    private fun sendServiceAction(
        action: String,
        url: String,
        title: String
    ) {

        if (url.isBlank()) {
            return
        }

        val intent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                this.action =
                    action

                putExtra(
                    MusicService.EXTRA_URL,
                    url
                )

                putExtra(
                    MusicService.EXTRA_TITLE,
                    title
                )
            }

        if (
            Build.VERSION.SDK_INT >= 26
        ) {

            startForegroundService(
                intent
            )

        } else {

            startService(
                intent
            )
        }
    }

    private fun downloadCurrentSong() {

        val url =
            currentAudioUrl

        if (url.isBlank()) {

            Toast.makeText(
                this,
                "اول یک آهنگ پخش کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            downloadThread?.isAlive == true
        ) {

            Toast.makeText(
                this,
                "یک دانلود در حال انجام است",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val name =
            makeSafeFileName(
                titleText.text.toString()
            ) + ".mp3"

        downloadProgress.progress =
            0

        downloadProgress.visibility =
            View.VISIBLE

        downloadText.visibility =
            View.VISIBLE

        cancelDownloadButton.visibility =
            View.VISIBLE

        downloadButton.isEnabled =
            false

        downloadText.text =
            "0%"

        status.text =
            "در حال دانلود..."

        downloadThread =
            Thread {

                try {

                    if (
                        Build.VERSION.SDK_INT >= 29
                    ) {

                        downloadMediaStore(
                            url,
                            name
                        )

                    } else {

                        downloadOld(
                            url,
                            name
                        )
                    }

                    runOnUiThread {

                        downloadProgress.progress =
                            100

                        downloadText.text =
                            "100%"

                        status.text =
                            "دانلود کامل شد ✓"

                        downloadButton.isEnabled =
                            true

                        cancelDownloadButton.visibility =
                            View.GONE
                    }

                } catch (
                    e: Exception
                ) {

                    runOnUiThread {

                        if (
                            e.message ==
                            "CANCELLED"
                        ) {

                            status.text =
                                "دانلود لغو شد"

                        } else {

                            status.text =
                                "دانلود ناموفق بود"
                        }

                        downloadButton.isEnabled =
                            true

                        cancelDownloadButton.visibility =
                            View.GONE
                    }
                }
            }

        downloadThread?.start()
    }

    @Volatile
    private var cancelRequested =
        false

    private fun cancelDownload() {

        cancelRequested =
            true

        downloadText.text =
            "در حال لغو..."

        downloadThread =
            null
    }

    private fun downloadMediaStore(
        urlString: String,
        fileName: String
    ) {

        cancelRequested =
            false

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
                ?: throw Exception(
                    "CREATE_FAILED"
                )

        try {

            val connection =
                URL(urlString)
                    .openConnection()
                    as HttpURLConnection

            connection.connectTimeout =
                15000

            connection.readTimeout =
                30000

            connection.connect()

            val total =
                connection.contentLengthLong

            var downloaded =
                0L

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->

                    val buffer =
                        ByteArray(8192)

                    while (true) {

                        if (
                            cancelRequested
                        ) {

                            throw Exception(
                                "CANCELLED"
                            )
                        }

                        val count =
                            input.read(buffer)

                        if (
                            count == -1
                        ) {
                            break
                        }

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
                                    downloaded.toDouble()
                                        /
                                        total.toDouble()
                                        *
                                        100
                                    ).toInt()

                            runOnUiThread {

                                downloadProgress.progress =
                                    percent

                                downloadText.text =
                                    "$percent%"
                            }
                        }
                    }
                }
            }

            connection.disconnect()

            contentResolver.update(
                uri,
                ContentValues().apply {

                    put(
                        MediaStore.Audio.Media.IS_PENDING,
                        0
                    )
                },
                null,
                null
            )

        } catch (
            e: Exception
        ) {

            contentResolver.delete(
                uri,
                null,
                null
            )

            throw e
        }
    }

    private fun downloadOld(
        urlString: String,
        fileName: String
    ) {

        cancelRequested =
            false

        if (
            checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            runOnUiThread {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    501
                )
            }

            throw Exception(
                "PERMISSION"
            )
        }

        val directory =
            Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC
                )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file =
            File(
                directory,
                fileName
            )

        val connection =
            URL(urlString)
                .openConnection()
                as HttpURLConnection

        connection.connect()

        val total =
            connection.contentLengthLong

        var downloaded =
            0L

        BufferedInputStream(
            connection.inputStream
        ).use { input ->

            FileOutputStream(
                file
            ).use { output ->

                val buffer =
                    ByteArray(8192)

                while (true) {

                    if (
                        cancelRequested
                    ) {

                        file.delete()

                        throw Exception(
                            "CANCELLED"
                        )
                    }

                    val count =
                        input.read(buffer)

                    if (
                        count == -1
                    ) {
                        break
                    }

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
                                downloaded.toDouble()
                                    /
                                    total.toDouble()
                                    *
                                    100
                                ).toInt()

                        runOnUiThread {

                            downloadProgress.progress =
                                percent

                            downloadText.text =
                                "$percent%"
                        }
                    }
                }
            }
        }

        connection.disconnect()
    }

    private fun makeSafeFileName(
        text: String
    ): String {

        var name =
            text.trim()

        if (name.isEmpty()) {

            name =
                "Music_Finder"
        }

        name =
            name.replace(
                Regex(
                    "[\\\\/:*?\"<>|]"
                ),
                "_"
            )

        return name.take(100)
    }

    override fun onDestroy() {

        /*
         * سرویس موسیقی را متوقف نمی‌کنیم.
         * بنابراین با بسته شدن Activity،
         * موزیک می‌تواند ادامه داشته باشد.
         */

        web.destroy()

        super.onDestroy()
    }
}
