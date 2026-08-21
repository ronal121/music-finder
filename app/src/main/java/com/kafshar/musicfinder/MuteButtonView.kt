package com.kafshar.musicfinder

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.TextView

class MuteButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val prefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
    private var muted = prefs.getBoolean("muted", false)

    init {
        isClickable = true
        isFocusable = true
        updateIcon()
        setOnClickListener { toggleMute() }
    }

    private fun toggleMute() {
        val current = prefs.getInt("volume_percent", 80).coerceIn(0, 100)
        if (!muted && current > 0) {
            prefs.edit()
                .putInt("volume_before_mute", current)
                .putBoolean("muted", true)
                .putInt("volume_percent", 0)
                .apply()
            sendVolume(0)
            muted = true
        } else {
            val restored = prefs.getInt("volume_before_mute", 80).coerceIn(1, 100)
            prefs.edit()
                .putBoolean("muted", false)
                .putInt("volume_percent", restored)
                .apply()
            sendVolume(restored)
            muted = false
        }
        updateIcon()
    }

    private fun sendVolume(value: Int) {
        try {
            context.startService(
                Intent(context, MusicService::class.java).apply {
                    action = MusicService.ACTION_SET_VOLUME
                    putExtra(MusicService.EXTRA_VOLUME, value)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun updateIcon() {
        text = if (muted) "🔇" else "🔊"
        contentDescription = if (muted) "Unmute" else "Mute"
    }
}
