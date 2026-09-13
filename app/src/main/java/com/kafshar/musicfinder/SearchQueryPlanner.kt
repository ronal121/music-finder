package com.kafshar.musicfinder

/**
 * Builds a small set of Google queries for lyric fragments, misspellings and
 * ordinary song/artist searches. Google remains responsible for semantic intent;
 * these variants only give it the useful context that the old search pipeline had.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input).trim()
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original).trim()
        val normalized = SearchEngine.normalizeQuery(original).trim()
        val clean = SearchEngine.withoutSearchNoise(corrected).trim()
        val variants = linkedSetOf<String>()

        // Exact lyric phrase first: strongest signal when the user remembers lyrics.
        variants += "\"$original\""
        variants += original
        if (corrected.isNotBlank() && !corrected.equals(original, ignoreCase = true)) {
            variants += "\"$corrected\""
            variants += corrected
        }
        if (normalized.isNotBlank() && !normalized.equals(original, ignoreCase = true)) {
            variants += normalized
        }
        if (clean.isNotBlank() && !clean.equals(corrected, ignoreCase = true)) {
            variants += clean
        }

        // Music-context variants help Google connect lyric fragments to a song page.
        if (clean.isNotBlank()) {
            variants += "\"$clean\" \"متن آهنگ\""
            variants += "\"$clean\" lyrics"
            variants += "$clean آهنگ"
            variants += "$clean song"
        }

        return variants
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(9)
    }
}
