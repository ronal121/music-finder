package com.kafshar.musicfinder

data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val volume: Int = 0,
    val isMuted: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artwork: String = "",
    val mediaUrl: String = "",
    val error: String? = null
)
