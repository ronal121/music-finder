package com.kafshar.musicfinder

object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input)
        if (original.isBlank()) return emptyList()
        val corrected = SearchEngine.correctedQuery(original)
        val normalized = SearchEngine.normalizeQuery(original)
        val clean = SearchEngine.withoutSearchNoise(corrected)
        val variants = linkedSetOf<String>()
        variants += original
        if (corrected.isNotBlank()) variants += corrected
        if (normalized.isNotBlank() && normalized != corrected) variants += normalized
        if (clean.isNotBlank() && clean != corrected) variants += clean
        if (clean.isNotBlank()) variants += "$clean آهنگ"
        if (clean.isNotBlank()) variants += "$clean song"
        return variants.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }.take(6)
    }
}
