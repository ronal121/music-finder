package com.kafshar.musicfinder

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Makes search behave like a music-first search UI:
 * as soon as the first validated playable SongResult is added by MainActivity,
 * start it immediately. Remaining results continue to populate the list and
 * can be selected manually by the user.
 *
 * MainActivity's search pipeline remains responsible for discovery, page parsing,
 * media probing, caching and playback. This controller only changes the timing
 * of the first play and suppresses progress/count messages in the status label.
 */
internal class SearchAutoPlayController(
    private val activity: Activity
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastGeneration = -1
    private var autoPlayedGeneration = -1
    private var running = false

    private val generationField by lazy {
        MainActivity::class.java.getDeclaredField("searchGeneration").apply {
            isAccessible = true
        }
    }

    private val songsField by lazy {
        MainActivity::class.java.getDeclaredField("songs").apply {
            isAccessible = true
        }
    }

    private val playSongMethod by lazy {
        MainActivity::class.java.getDeclaredMethod(
            "playSong",
            SongResult::class.java
        ).apply {
            isAccessible = true
        }
    }

    private val statusField by lazy {
        MainActivity::class.java.getDeclaredField("status").apply {
            isAccessible = true
        }
    }

    fun start() {
        if (running) return
        running = true
        poll()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun poll() {
        if (!running || activity.isFinishing || activity.isDestroyedCompat()) return

        try {
            val generation = generationField.getInt(activity)
            val songs = songsField.get(activity) as? ArrayList<*>

            // The status field is intentionally hidden: the search screen should
            // show the music list, not messages such as "20 results found".
            (statusField.get(activity) as? TextView)?.visibility = View.GONE

            if (generation > 0 && generation != lastGeneration) {
                lastGeneration = generation
                autoPlayedGeneration = -1
            }

            if (
                generation > 0 &&
                generation == lastGeneration &&
                autoPlayedGeneration != generation &&
                !songs.isNullOrEmpty()
            ) {
                val first = songs.firstOrNull() as? SongResult
                if (first != null && first.url.isNotBlank()) {
                    autoPlayedGeneration = generation
                    playSongMethod.invoke(activity, first)
                }
            }
        } catch (_: Throwable) {
            // Search must never be broken by the optional auto-play controller.
        }

        handler.postDelayed({ poll() }, 100L)
    }

    private fun Activity.isDestroyedCompat(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed
}
