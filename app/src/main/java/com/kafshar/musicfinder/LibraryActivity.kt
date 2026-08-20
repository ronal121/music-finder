package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
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

                textSize = 38f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    8,
                    0,
                    18,
                    0
                )

                setOnClickListener {

                    finish()
                }
            }

        val title =
            TextView(this).apply {

                text = "کتابخانه"

                textSize = 23f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        header.addView(
            backButton
        )

        header.addView(
            title
        )

        root.addView(
            header
        )

        val scroll =
            ScrollView(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
            }

        libraryContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    10,
                    5,
                    10,
                    20
                )
            }

        scroll.addView(
            libraryContainer
        )

        root.addView(
            scroll
        )

        setContentView(root)
    }

    private fun loadLibrary() {

        libraryContainer.removeAllViews()

        val songs =
            LibraryManager.getSongs(this)

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

        songs.forEach { song ->

            addSongView(song)
        }
    }

    private fun addSongView(
        song: SongResult
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
                    0xFF15151C.toInt()
                )
            }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            4,
            5,
            4,
            5
        )

        row.layoutParams = params

        val cover =
            ImageView(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        65,
                        65
                    )

                scaleType =
                    ImageView.ScaleType.CENTER_CROP

                setImageResource(
                    android.R.drawable.ic_media_play
                )
            }

        if (song.cover.isNotBlank()) {

            executor.execute {

                try {

                    val connection =
                        URL(song.cover)
                            .openConnection()
                                as HttpURLConnection

                    connection.connectTimeout =
                        5000

                    connection.readTimeout =
                        5000

                    connection.connect()

                    val bitmap =
                        BitmapFactory.decodeStream(
                            connection.inputStream
                        )

                    connection.disconnect()

                    runOnUiThread {

                        if (
                            bitmap != null
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

        val textLayout =
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

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        val songTitle =
            TextView(this).apply {

                text =
                    song.title

                textSize = 16f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )
            }

        val info =
            TextView(this).apply {

                text =
                    "${song.artist} • ${song.site}"

                textSize = 12f

                setTextColor(
                    0xFFAAAAAA.toInt()
                )

                setPadding(
                    0,
                    5,
                    0,
                    0
                )
            }

        textLayout.addView(
            songTitle
        )

        textLayout.addView(
            info
        )

        val deleteButton =
            TextView(this).apply {

                text = "🗑"

                textSize = 23f

                setTextColor(
                    0xFFFF5555.toInt()
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    12,
                    12,
                    8,
                    12
                )

                setOnClickListener {

                    deleteSong(song)
                }
            }

        row.addView(
            cover
        )

        row.addView(
            textLayout
        )

        row.addView(
            deleteButton
        )

        row.setOnClickListener {

            playSong(song)
        }

        libraryContainer.addView(
            row
        )
    }

    private fun deleteSong(
        song: SongResult
    ) {

        LibraryManager.removeSong(
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

    private fun playSong(
        song: SongResult
    ) {

        if (
            song.url.isBlank()
        ) {
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

        startService(intent)

        Toast.makeText(
            this,
            "در حال پخش: ${song.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
