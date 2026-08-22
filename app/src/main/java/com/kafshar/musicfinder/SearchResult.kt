package com.kafshar.musicfinder

/** Canonical result exchanged between search, ranking and playback layers. */
data class SearchResult(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artwork: String = "",
    val source: String = "",
    val pageUrl: String = "",
    val mediaUrl: String = "",
    val durationMs: Long = 0L,
    val quality: Int = 0,
    val score: Int = 0,
    val isPlayable: Boolean = false
)
