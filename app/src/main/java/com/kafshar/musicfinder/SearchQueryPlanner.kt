package com.kafshar.musicfinder

/**
 * Builds search text for the configured music sources.
 *
 * There are no Google/site: operators here. SearchProvider sends this text
 * directly to MusicSitePool.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input).trim()
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original).trim()
        return linkedSetOf(
            original,
            corrected
        ).filter { it.isNotBlank() }
    }
}
