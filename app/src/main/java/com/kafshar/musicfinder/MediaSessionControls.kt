package com.kafshar.musicfinder

/**
 * Central place for media-session control constants.
 *
 * The current player implementation is handled by MusicService through
 * Media3 MediaSessionService. This object intentionally contains no
 * Android callback overrides, so it remains compatible with the Media3
 * version used by this project and does not introduce duplicate controls.
 */
object MediaSessionControls {

    const val ACTION_PLAY = MusicService.ACTION_PLAY
    const val ACTION_PAUSE = MusicService.ACTION_PAUSE
    const val ACTION_TOGGLE = MusicService.ACTION_TOGGLE
    const val ACTION_NEXT = MusicService.ACTION_NEXT
    const val ACTION_PREVIOUS = MusicService.ACTION_PREVIOUS
    const val ACTION_STOP = MusicService.ACTION_STOP

    fun isPlaybackAction(action: String?): Boolean {
        return when (action) {
            ACTION_PLAY,
            ACTION_PAUSE,
            ACTION_TOGGLE,
            ACTION_NEXT,
            ACTION_PREVIOUS,
            ACTION_STOP -> true
            else -> false
        }
    }
}
