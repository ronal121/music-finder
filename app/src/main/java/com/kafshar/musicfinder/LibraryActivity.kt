package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LibraryActivity : Activity() {

    private lateinit var libraryContainer:
            LinearLayout

    private val executor =
        Executors.newFixedThreadPool(2)

    private val imageCache =
        HashMap<String, Bitmap>()

    private var destroyed = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        destroyed = false

        createLayout()
        loadLibrary()
    }

    private fun createLayout() {

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    0xFF0D0D12.toInt()
                )
            }

        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    16,
                    16,
                    16,
                    16
                )
            }

        val backButton =
            TextView(this).apply {

                text = "‹"
                textSize = 36f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                gravity =
                    Gravity.CENTER

                setOnClickListener {
                    finish()
                }
            }

        header.addView(
            backButton,
            LinearLayout.LayoutParams(
                55,
                55
            )
        )

        val title =
            TextView(this).apply {

                text = "Library"
                textSize = 23f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                55,
                1f
            )
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll =
            ScrollView(this)

        libraryContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    12,
                    8,
                    12,
                    30
                )
            }

        scroll.addView(
            libraryContainer
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun loadLibrary() {

        if (destroyed) return

        libraryContainer.removeAllViews()

        val songs =
            try {
                LibraryManager.get(this)
            } catch (_: Exception) {
                mutableListOf()
            }

        if (songs.isEmpty()) {

            val empty =
                TextView(this).apply {

                    text =
                        "کتابخانه خالی است"

                    textSize = 17f

                    setTextColor(
                        0xFFAAAAAA.toInt()
                    )

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        20,
                        80,
                        20,
                        80
                    )
                }

            libraryContainer.addView(
                empty
            )

            return
        }

        songs.take(200)
            .forEachIndexed {
                    index,
                    song ->

                addSongView(
                    song,
                    index
                )
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
                    12,
                    12,
                    12
                )

                setBackgroundColor(
                    0xFF15151D.toInt()
                )
            }

        val rowParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        rowParams.setMargins(
            0,
            0,
            0,
            10
        )

        libraryContainer.addView(
            row,
            rowParams
        )

        val cover =
            ImageView(this).apply {

                scaleType =
                    ImageView.ScaleType.CENTER_CROP

                setBackgroundColor(
                    0xFF22222A.toInt()
                )
            }

        row.addView(
            cover,
            LinearLayout.LayoutParams(
                70,
                70
            )
        )

        loadCover(
            song.cover,
            cover
        )

        val textContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    14,
                    0,
                    8,
                    0
                )
            }

        val textParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        val title =
            TextView(this).apply {

                text =
                    "${index + 1}. ${song.title}"

                textSize = 16f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                maxLines = 2
            }

        val artist =
            TextView(this).apply {

                text =
                    "${song.artist} • ${song.site}"

                textSize = 12f

                setTextColor(
                    0xFFAAAAAA.toInt()
                )

                maxLines = 2
            }

        textContainer.addView(title)
        textContainer.addView(artist)

        row.addView(
            textContainer,
            textParams
        )

        val deleteButton =
            TextView(this).apply {

                text = "🗑"
                textSize = 22f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                setPadding(
                    10,
                    10,
                    8,
                    10
                )

                setOnClickListener {
                    deleteSong(song)
                }
            }

        row.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                55,
                70
            )
        )

        row.setOnClickListener {
            playSong(song)
        }
    }

    private fun loadCover(
        url: String,
        target: ImageView
    ) {

        if (
            url.isBlank() ||
            destroyed
        ) {
            return
        }

        imageCache[url]?.let {

            if (!it.isRecycled) {
                target.setImageBitmap(it)
            }

            return
        }

        executor.execute {

            var connection:
                    HttpURLConnection? = null

            try {

                connection =
                    URL(url)
                        .openConnection()
                            as? HttpURLConnection
                        ?: return@execute

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                connection.connect()

                if (
                    connection.responseCode !in
                    200..299
                ) {
                    return@execute
                }

                val bytes =
                    connection.inputStream.use {
                        it.readBytes()
                    }

                if (bytes.isEmpty()) {
                    return@execute
                }

                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig =
                            Bitmap.Config.RGB_565
                    }

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        options
                    )

                if (
                    bitmap != null &&
                    !destroyed
                ) {

                    imageCache[url] =
                        bitmap

                    runOnUiThread {

                        if (!destroyed) {
                            target.setImageBitmap(
                                bitmap
                            )
                        }
                    }
                }

            } catch (_: Exception) {
            } finally {

                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun playSong(
        song: SongResult
    ) {

        if (
            destroyed ||
            song.url.isBlank()
        ) {
            return
        }

        try {

            val intent =
                Intent(
                    this,
                    MusicService::class.java
                ).apply {

                    action =
                        MusicService.ACTION_PLAY

                    putExtra(
                        MusicService.EXTRA_URL,
                        song.url
                    )

                    putExtra(
                        MusicService.EXTRA_TITLE,
                        song.title
                    )

                    putExtra(
                        MusicService.EXTRA_ARTIST,
                        song.artist
                    )

                    putExtra(
                        MusicService.EXTRA_COVER,
                        song.cover
                    )
                }

            startForegroundService(
                intent
            )

            Toast.makeText(
                this,
                "در حال پخش: ${song.title}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "پخش آهنگ انجام نشد",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteSong(
        song: SongResult
    ) {

        try {

            LibraryManager.remove(
                this,
                song
            )

            Toast.makeText(
                this,
                "از کتابخانه حذف شد",
                Toast.LENGTH_SHORT
            ).show()

            loadLibrary()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "حذف انجام نشد",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {

        destroyed = true

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }

        imageCache.values.forEach {
            if (!it.isRecycled) {
                it.recycle()
            }
        }

        imageCache.clear()

        super.onDestroy()
    }
}
