package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LibraryActivity : Activity() {

    private lateinit var container: LinearLayout

    private val executor =
        Executors.newCachedThreadPool()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_library
        )

        container =
            findViewById(
                R.id.libraryContainer
            )

        findViewById<TextView>(
            R.id.backButton
        ).setOnClickListener {

            finish()
        }

        updateLibrary()
    }

    private fun updateLibrary() {

        container.removeAllViews()

        val songs =
            LibraryManager.getSongs(
                this
            )

        if (songs.isEmpty()) {

            val empty =
                TextView(this)

            empty.text =
                "کتابخانه خالی است"

            empty.textSize = 18f

            empty.gravity =
                Gravity.CENTER

            empty.setTextColor(
                0xFFAAAAAA.toInt()
            )

            empty.setPadding(
                20,
                80,
                20,
                80
            )

            container.addView(empty)

            return
        }

        songs.forEachIndexed {
                index,
                song ->

            addSongRow(
                song,
                index
            )
        }
    }

    private fun addSongRow(
        song: SongResult,
        index: Int
    ) {

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.HORIZONTAL

        row.setPadding(
            12,
            14,
            12,
            14
        )

        val cover =
            ImageView(this)

        cover.layoutParams =
            LinearLayout.LayoutParams(
                70,
                70
            ).apply {
                setMargins(
                    0,
                    0,
                    14,
                    0
                )
            }

        cover.setImageResource(
            android.R.drawable.ic_menu_gallery
        )

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

                    val bitmap =
                        BitmapFactory.decodeStream(
                            connection.inputStream
                        )

                    runOnUiThread {

                        if (bitmap != null)
                            cover.setImageBitmap(bitmap)
                    }

                    connection.disconnect()

                } catch (_: Exception) {
                }
            }
        }

        val middle =
            LinearLayout(this)

        middle.orientation =
            LinearLayout.VERTICAL

        middle.layoutParams =
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )

        val title =
            TextView(this)

        title.text =
            "${index + 1}. ${song.title}"

        title.textSize = 16f

        title.setTextColor(
            0xFFFFFFFF.toInt()
        )

        val info =
            TextView(this)

        info.text =
            "${song.artist} • ${song.site}"

        info.textSize = 12f

        info.setTextColor(
            0xFFAAAAAA.toInt()
        )

        middle.addView(title)
        middle.addView(info)

        val play =
            TextView(this)

        play.text = "▶"

        play.textSize = 22f

        play.gravity =
            Gravity.CENTER

        play.setPadding(
            14,
            0,
            14,
            0
        )

        play.setOnClickListener {

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

        val delete =
            TextView(this)

        delete.text = "🗑"

        delete.textSize = 20f

        delete.gravity =
            Gravity.CENTER

        delete.setPadding(
            14,
            0,
            8,
            0
        )

        delete.setOnClickListener {

            LibraryManager.remove(
                this,
                song
            )

            Toast.makeText(
                this,
                "آهنگ از کتابخانه حذف شد",
                Toast.LENGTH_SHORT
            ).show()

            updateLibrary()
        }

        row.addView(cover)
        row.addView(middle)
        row.addView(play)
        row.addView(delete)

        container.addView(row)
    }

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
