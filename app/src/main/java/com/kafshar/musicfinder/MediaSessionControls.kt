package com.kafshar.musicfinder

/** Single source of truth for playback actions exposed to the UI/service. */
object MediaSessionControls {
    const val ACTION_PLAY = MusicService.ACTION_PLAY
    const val ACTION_PAUSE = MusicService.ACTION_PAUSE
    const val ACTION_TOGGLE = MusicService.ACTION_TOGGLE
    const val ACTION_NEXT = MusicService.ACTION_NEXT
    const val ACTION_PREVIOUS = MusicService.ACTION_PREVIOUS
    const val ACTION_STOP = MusicService.ACTION_STOP
    const val ACTION_MUTE = MusicService.ACTION_MUTE
    const val ACTION_UNMUTE = MusicService.ACTION_UNMUTE

    fun isPlaybackAction(action: String?): Boolean = when (action) {
        ACTION_PLAY, ACTION_PAUSE, ACTION_TOGGLE, ACTION_NEXT, ACTION_PREVIOUS,
        ACTION_STOP, ACTION_MUTE, ACTION_UNMUTE -> true
        else -> false
    }
}
