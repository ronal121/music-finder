package com.kafshar.musicfinder

object SearchQueryPlanner {
    fun build(input: String): List<String> {
        val original = SearchEngine.displayQuery(input)
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original)
        val normalized = SearchEngine.normalizeQuery(original)
        val clean = SearchEngine.withoutSearchNoise(corrected)
        val variants = linkedSetOf<String>()

        // Keep the exact phrase first: this is the strongest signal for lyric-fragment searches.
        variants += "\"$original\""
        variants += original
        if (corrected.isNotBlank()) variants += "\"$corrected\""
        if (normalized.isNotBlank() && normalized != corrected) variants += normalized
        if (clean.isNotBlank() && clean != corrected) variants += clean

        // Most Persian music pages expose lyrics/text, so use that as a second discovery path.
        if (clean.isNotBlank()) variants += "\"$clean\" \"متن آهنگ\""
        if (clean.isNotBlank()) variants += "\"$clean\" lyrics"
        if (clean.isNotBlank()) variants += "$clean آهنگ"
        if (clean.isNotBlank()) variants += "$clean song"

        return variants
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .take(9)
    }
}
