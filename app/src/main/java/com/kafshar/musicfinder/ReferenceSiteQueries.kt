package com.kafshar.musicfinder

/**
 * Constrains Google discovery to the configured music-site universe.
 * The site pool is deliberately split into manageable OR groups so Google can
 * still rank the pages while the app never falls back to unrestricted web noise.
 */
object ReferenceSiteQueries {
    private const val DOMAINS_PER_QUERY = 24

    fun build(input: String): List<String> {
        val value = input.trim().replace(Regex("\\s+"), " ")
        if (value.isBlank()) return emptyList()

        val sites = MusicSitePool.domains
        if (sites.isEmpty()) return listOf(value)

        val siteQueries = sites.chunked(DOMAINS_PER_QUERY).map { batch ->
            batch.joinToString(" OR ") { "site:$it" }
        }
        return siteQueries.map { domains -> "$value ($domains)" }
    }
}
