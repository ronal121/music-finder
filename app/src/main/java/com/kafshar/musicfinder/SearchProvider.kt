package com.kafshar.musicfinder

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

/** Compatibility name retained for existing callers; implementation is adaptive direct search. */
class DirectMusicSiteSearchProvider : SearchProvider {
    private val delegate = DirectSiteSearchProvider()

    override val name: String = delegate.name

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> =
        delegate.search(query, limit)
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    /** Direct-search provider over the complete configured reference-site pool. */
    val providers: List<SearchProvider> = listOf(
        DirectMusicSiteSearchProvider()
    )
}
