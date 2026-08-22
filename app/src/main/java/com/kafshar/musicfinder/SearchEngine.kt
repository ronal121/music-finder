package com.kafshar.musicfinder

/** Lightweight, offline query intelligence. No network/API dependency. */
object SearchEngine {
    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[^\\p{L}\\p{N}\\s-]")

    private val persianToCanonical = mapOf(
        'آ' to 'ا', 'أ' to 'ا', 'إ' to 'ا', 'ة' to 'ه', 'ي' to 'ی', 'ى' to 'ی',
        'ك' to 'ک', 'ۀ' to 'ه', 'ؤ' to 'و', 'ئ' to 'ی', 'ـ' to null
    )

    private val commonTypos = mapOf(
        "michal" to "michael",
        "michal jakson" to "michael jackson",
        "jakson" to "jackson",
        "swft" to "swift",
        "talyor" to "taylor",
        "taylor swft" to "taylor swift",
        "bill jin" to "billie jean",
        "billie jin" to "billie jean",
        "adel hello" to "adele hello"
    )

    fun normalizeQuery(input: String): String = input
        .mapNotNull { persianToCanonical[it] ?: it }
        .joinToString("")
        .replace(punctuation, " ")
        .replace(whitespace, " ")
        .trim()
        .lowercase()

    fun displayQuery(input: String): String = input.replace(whitespace, " ").trim()

    fun correctedQuery(input: String): String {
        val normalized = normalizeQuery(input)
        commonTypos[normalized]?.let { return it }
        return normalized.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { commonTypos[it] ?: it }
    }

    fun suggestions(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)
        return linkedSetOf(original, corrected, "$corrected song", "$corrected music")
            .filter { it.isNotBlank() && normalizeQuery(it) != normalized }
            .take(5)
    }

    fun buildQueries(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)
        return linkedSetOf(original, corrected, normalized, "$corrected official")
            .filter { it.isNotBlank() }
            .take(4)
    }

    fun buildGoogleQuery(input: String): String = ServerConfig.searchQuery(correctedQuery(input))

    fun parseArtistTitle(input: String): Pair<String?, String?> {
        val clean = displayQuery(input)
        val parts = clean.split(" - ", " — ", " – ")
        return if (parts.size >= 2) {
            parts[0].trim().ifBlank { null } to parts.drop(1).joinToString(" - ").trim().ifBlank { null }
        } else null to clean.ifBlank { null }
    }

    fun similarity(a: String, b: String): Int {
        val left = normalizeQuery(a)
        val right = normalizeQuery(b)
        if (left.isBlank() || right.isBlank()) return 0
        if (left == right) return 100
        if (left.contains(right) || right.contains(left)) return 75

        val leftWords = left.split(' ').filter { it.isNotBlank() }
        val rightWords = right.split(' ').filter { it.isNotBlank() }
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0

        val exactWords = leftWords.intersect(rightWords.toSet()).size
        val wordScore = exactWords.toDouble() / maxOf(leftWords.size, rightWords.size)
        val fuzzyScore = leftWords.maxOfOrNull { lw -> rightWords.maxOfOrNull { rw -> levenshteinSimilarity(lw, rw) } ?: 0 } ?: 0
        return maxOf((wordScore * 100).toInt(), fuzzyScore)
    }

    private fun levenshteinSimilarity(a: String, b: String): Int {
        if (a == b) return 100
        if (a.isEmpty() || b.isEmpty()) return 0
        var row = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length + 1)
            next[0] = i + 1
            for (j in b.indices) {
                next[j + 1] = minOf(
                    next[j] + 1,
                    row[j + 1] + 1,
                    row[j] + if (a[i] == b[j]) 0 else 1
                )
            }
            row = next
        }
        val distance = row[b.length]
        return ((1.0 - distance.toDouble() / maxOf(a.length, b.length)) * 100).toInt().coerceIn(0, 100)
    }
}
