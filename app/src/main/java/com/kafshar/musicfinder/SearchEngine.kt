package com.kafshar.musicfinder

/** Lightweight, offline query intelligence. No network/API dependency. */
object SearchEngine {

    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[\\u0000-\\u001F\\u007F]")

    private val persianToLatin = mapOf(
        'آ' to 'ا', 'أ' to 'ا', 'إ' to 'ا', 'ة' to 'ه', 'ي' to 'ی', 'ى' to 'ی',
        'ك' to 'ک', 'ۀ' to 'ه', 'ؤ' to 'و', 'ئ' to 'ی'
    )

    private val commonTypos = mapOf(
        "michal" to "michael",
        "jakson" to "jackson",
        "swft" to "swift",
        "talyor" to "taylor",
        "bill jin" to "billie jean",
        "billie jin" to "billie jean"
    )

    fun normalizeQuery(input: String): String = input
        .map { persianToLatin[it] ?: it }
        .joinToString("")
        .replace(punctuation, " ")
        .replace(whitespace, " ")
        .trim()
        .lowercase()

    fun displayQuery(input: String): String = input
        .replace(whitespace, " ")
        .trim()

    fun correctedQuery(input: String): String {
        val normalized = normalizeQuery(input)
        return commonTypos[normalized] ?: normalized
    }

    fun suggestions(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)
        return linkedSetOf(original, corrected, "$corrected song", "$corrected music")
            .filter { it.isNotBlank() && it != normalized }
            .take(5)
    }

    fun buildQueries(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)
        return linkedSetOf(original, normalized, corrected)
            .filter { it.isNotBlank() }
            .take(4)
    }

    fun buildGoogleQuery(input: String): String =
        ServerConfig.searchQuery(correctedQuery(input))

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
        val leftWords = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightWords = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0
        return ((leftWords.intersect(rightWords).size.toDouble() / maxOf(leftWords.size, rightWords.size)) * 100).toInt()
    }
}
