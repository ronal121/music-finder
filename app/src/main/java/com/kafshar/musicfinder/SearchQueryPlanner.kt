package com.kafshar.musicfinder

/**
 * Keeps discovery close to a Google-style search: send one clean user query
 * to the combined Google + direct-site discovery engine. Google itself handles
 * spelling, lyric fragments and semantic intent better than our small local
 * correction dictionary, so the original query must remain intact.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input).trim()
        if (original.isBlank()) return emptyList()
        return listOf(original)
    }
}
