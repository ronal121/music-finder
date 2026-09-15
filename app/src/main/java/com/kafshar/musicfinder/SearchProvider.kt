package com.kafshar.musicfinder

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

/** Primary discovery path: Google-ranked organic results. */
class DirectMusicSiteSearchProvider : SearchProvider {
    override val name: String = "Google"

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> =
        ParallelSearchEngine.searchDirectBlocking(query, limit)
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    /**
     * Google is always tried first. The adaptive site pool is only a fallback when
     * Google returns no organic pages, so a temporary Google block does not leave
     * the app with an empty search screen.
     */
    val providers: List<SearchProvider> = listOf(
        DirectMusicSiteSearchProvider(),
        DirectSiteSearchProvider()
    )
}
