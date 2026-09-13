package com.kafshar.musicfinder

/**
 * Builds the single unrestricted Google discovery query used by the app.
 *
 * Google is responsible for discovering and ranking the web. This class only
 * normalizes/corrects the user's text and must never enumerate music domains.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input)
        if (original.isBlank()) return emptyList()

        val googleQuery = SearchEngine.buildGoogleQuery(original).trim()
        return listOf(googleQuery.ifBlank { original })
    }
}
