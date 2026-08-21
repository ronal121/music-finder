package com.kafshar.musicfinder

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.SeekBar

/**
 * Keeps the Activity position display alive while music is playing.
 * The existing MainActivity receiver receives ACTION_GET_POSITION updates
 * from MusicService, so the visible clock/seekbar updates continuously
 * instead of only after the user touches the seekbar.
 */
class PlayerProgressSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : SeekBar(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            try {
                context.startService(
                    Intent(context, MusicService::class.java).apply {
                        action = MusicService.ACTION_GET_POSITION
                    }
                )
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }
}
