package com.kafshar.musicfinder

/**
 * Builds unrestricted Google discovery queries.
 *
 * The search engine is responsible for discovering the web. This layer only
 * normalizes/corrects the user's text; it must never enumerate or constrain
 * the search to a hard-coded list of music domains.
 */
object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input)
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original)
        val clean = SearchEngine.withoutSearchNoise(corrected)
        val normalized = SearchEngine.normalizeQuery(original)

        val primary = when {
            corrected.isNotBlank() && corrected != normalized && corrected != original ->
                "($original OR $corrected)"
            clean.isNotBlank() && clean != normalized ->
                "($original OR $clean)"
            else ->
                original
        }

        return linkedSetOf(
            primary.trim(),
            clean.takeIf { it.isNotBlank() && it != normalized },
            "$original lyrics".takeIf { original.split(' ').size > 1 },
            "$original آهنگ".takeIf { original.split(' ').size > 1 }
        ).filterNotNull()
            .filter { it.isNotBlank() }
            .take(3)
    }
}
