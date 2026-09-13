package com.kafshar.musicfinder

/**
 * Keeps discovery close to a Google-style search: one clean user query is sent
 * to the combined Google + direct-site discovery engine. GoogleDiscoveryProvider
 * itself adds the typo-corrected OR form, so issuing the same search twice here
 * only adds latency and dilutes ranking.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input).trim()
        if (original.isBlank()) return emptyList()
        return listOf(original)
    }
}
