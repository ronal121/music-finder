package com.kafshar.musicfinder

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

/**
 * Searches the configured music sources directly.
 *
 * This is the same discovery model used by the earlier working version:
 * query each configured music site itself, collect song-page URLs, then let
 * the normal page/audio pipeline inspect those pages for a playable file.
 * No Google/Bing/web-search-engine discovery is used here.
 */
class DirectMusicSiteSearchProvider : SearchProvider {
    override val name: String = "Music sites"

    private val delegate = DirectSiteSearchProvider()

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> =
        delegate.search(query, limit)
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    /**
     * The search path is deliberately limited to the configured music-source
     * pool. All configured sources are queried in parallel by DirectSiteSearchProvider.
     */
    val providers: List<SearchProvider> = listOf(
        DirectMusicSiteSearchProvider()
    )
}
