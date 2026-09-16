package com.kafshar.musicfinder

/** Immutable playback contract shared by UI and media layers. */
data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val volume: Int = 80,
    val isMuted: Boolean = false,
    val title: String = "Music Finder",
    val artist: String = "KAFSHAR",
    val artwork: String = "",
    val mediaUrl: String = "",
    val error: String? = null
) {
    val isPaused: Boolean get() = !isPlaying && !isLoading
    val effectiveVolume: Int get() = if (isMuted) 0 else volume.coerceIn(0, 100)
}
