package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
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

    private lateinit var libraryContainer: LinearLayout

    private val executor =
        Executors.newCachedThreadPool()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        createLayout()
        loadLibrary()
    }

    private fun createLayout() {

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(
                    0xFF0D0D12.toInt()
                )
            }

        val header =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
                gravity = Gravity.CENTER

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
                gravity = Gravity.CENTER_VERTICAL
            }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                55,
                1f
            )
        )

        root.addView(header)

        val scroll =
            ScrollView(this)

        libraryContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    12,
                    8,
                    12,
                    30
                )
            }

        scroll.addView(
            libraryContainer,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
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

        libraryContainer.removeAllViews()

        val songs =
            LibraryManager.get(this)

        if (songs.isEmpty()) {

            val empty =
                TextView(this).apply {

                    text = "کتابخانه خالی است"
                    textSize = 17f

                    setTextColor(
                        0xFFAAAAAA.toInt()
                    )

                    gravity = Gravity.CENTER

                    setPadding(
                        20,
                        80,
                        20,
                        80
                    )
                }

            libraryContainer.addView(empty)

            return
        }

        songs.forEachIndexed { index, song ->

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

        row.layoutParams = rowParams

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

        if (song.cover.isNotBlank()) {

            executor.execute {

                try {

                    val connection =
                        URL(song.cover)
                            .openConnection()
                                as HttpURLConnection

                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()

                    val bitmap =
                        BitmapFactory.decodeStream(
                            connection.inputStream
                        )

                    connection.disconnect()

                    runOnUiThread {

                        if (
                            bitmap != null &&
                            !isFinishing
                        ) {
                            cover.setImageBitmap(
                                bitmap
                            )
                        }
                    }

                } catch (_: Exception) {
                }
            }
        }

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

                gravity = Gravity.CENTER

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

        libraryContainer.addView(row)
    }

    private fun playSong(
        song: SongResult
    ) {

        if (song.url.isBlank()) {
            return
        }

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

        if (Build.VERSION.SDK_INT >= 26) {

            startForegroundService(intent)

        } else {

            startService(intent)
        }

        Toast.makeText(
            this,
            "در حال پخش: ${song.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteSong(
        song: SongResult
    ) {

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
    }

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
