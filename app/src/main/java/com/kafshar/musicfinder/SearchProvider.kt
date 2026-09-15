package com.kafshar.musicfinder

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

/**
 * Searches the configured music sources directly.
 *
 * This remains the secondary discovery path. Google is used first because it
 * already ranks the web-wide song pages far better than a fixed list of site
 * search URL conventions. The normal page/audio pipeline then verifies whether
 * each discovered page actually exposes playable media.
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
     * Discovery order is intentional:
     * 1) Google finds the most relevant song pages across the web.
     * 2) The configured music-site pool is the fallback when Google gives us
     *    too few usable pages or a site is not indexed.
     *
     * Both providers feed the same page-inspection/media-probing pipeline in
     * MainActivity, so a search result is never considered playable merely
     * because a search engine returned it.
     */
    val providers: List<SearchProvider> = listOf(
        object : SearchProvider {
            override val name: String = "Google"
            private val delegate = GoogleDiscoveryProvider()

            override fun search(query: String, limit: Int): List<GoogleResultParser.Result> =
                delegate.search(query, limit)
        },
        DirectMusicSiteSearchProvider()
    )
}
