package com.kafshar.musicfinder

/**
 * Constrains Google discovery to the configured music-site universe.
 * The pool is split into manageable OR groups so Google can rank pages inside
 * the full reference bank without making one enormous query URL.
 */
object ReferenceSiteQueries {
    private const val DOMAINS_PER_QUERY = 48

    fun build(input: String): List<String> {
        val value = input.trim().replace(Regex("\\s+"), " ")
        if (value.isBlank()) return emptyList()

        val sites = MusicSitePool.domains
        if (sites.isEmpty()) return listOf(value)

        return sites.chunked(DOMAINS_PER_QUERY).map { batch ->
            val domains = batch.joinToString(" OR ") { "site:$it" }
            "$value ($domains)"
        }
    }
}
