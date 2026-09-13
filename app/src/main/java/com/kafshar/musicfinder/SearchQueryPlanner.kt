package com.kafshar.musicfinder

/**
 * The UI submits one logical search request. GoogleDiscoveryProvider expands it
 * internally so fallback variants cannot flood the UI as separate site batches.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input).trim()
        return if (original.isBlank()) emptyList() else listOf(original)
    }
}
