package com.kafshar.musicfinder

/**
 * Builds Google queries that search ONLY the configured reference music sites.
 * Google is used as the ranking/index engine; it is not used as an unrestricted web search.
 */
object ReferenceSiteQueries {
    private const val DOMAINS_PER_QUERY = 12

    fun build(text: String): List<String> {
        val value = text.trim().replace(Regex("\\s+"), " ")
        if (value.isBlank()) return emptyList()

        val normalized = SearchEngine.normalizeQuery(value)
        val clean = SearchEngine.withoutSearchNoise(SearchEngine.correctedQuery(value))
        val terms = linkedSetOf<String>().apply {
            add("\"$value\"")
            if (normalized.isNotBlank()) add("\"$normalized\"")
            if (clean.isNotBlank()) add("\"$clean\"")
            if (clean.isNotBlank()) add("\"$clean\" \"متن آهنگ\"")
            if (clean.isNotBlank()) add("\"$clean\" lyrics")
            if (clean.isNotBlank()) add("$clean آهنگ")
        }

        val termPart = "(${terms.joinToString(" OR ")})"
        return MusicSitePool.domains
            .chunked(DOMAINS_PER_QUERY)
            .map { batch ->
                val sites = batch.joinToString(" OR ") { "site:$it" }
                "$termPart ($sites)"
            }
    }
}
