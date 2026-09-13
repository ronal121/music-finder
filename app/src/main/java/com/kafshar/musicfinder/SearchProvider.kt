package com.kafshar.musicfinder

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

/** Compatibility name retained for existing callers; now routed through the real search engine. */
class DirectMusicSiteSearchProvider : SearchProvider {
    override val name: String = "Music sites"

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> =
        ParallelSearchEngine.searchDirectBlocking(query, limit)
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    /** The provider uses the complete MusicSitePool through ParallelSearchEngine. */
    val providers: List<SearchProvider> = listOf(
        DirectMusicSiteSearchProvider()
    )
}
