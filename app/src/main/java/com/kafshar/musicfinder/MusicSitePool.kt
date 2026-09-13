package com.kafshar.musicfinder

/**
 * The only search sources used by Music Finder.
 *
 * Google is intentionally not part of the search path. Queries are sent
 * directly to these music sites and the returned pages are inspected for
 * playable audio.
 */
object MusicSitePool {
    val domains: List<String> = listOf(
        "rozmusic.com",
        "mybia2music.com",
        "musicdel.ir",
        "musics-fa.com",
        "nex1music.com",
        "upmusics.com",
        "sahand-music.ir",
        "radio3da.com"
    ).distinct()
}
