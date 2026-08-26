package com.kafshar.musicfinder

/** Lightweight, offline query intelligence. No network/API dependency. */
object SearchEngine {
    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[^\\p{L}\\p{N}\\s-]")

    private val persianToCanonical = mapOf(
        'آ' to 'ا', 'أ' to 'ا', 'إ' to 'ا', 'ٱ' to 'ا', 'ة' to 'ه',
        'ي' to 'ی', 'ى' to 'ی', 'ك' to 'ک', 'ۀ' to 'ه', 'ؤ' to 'و',
        'ئ' to 'ی', 'ـ' to null
    )

    private val digitMap = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val commonTypos = mapOf(
        "michal" to "michael",
        "michal jakson" to "michael jackson",
        "michal jackson" to "michael jackson",
        "jakson" to "jackson",
        "jaskon" to "jackson",
        "swft" to "swift",
        "talyor" to "taylor",
        "taylor swft" to "taylor swift",
        "bill jin" to "billie jean",
        "billie jin" to "billie jean",
        "adel" to "adele",
        "adel hello" to "adele hello"
    )

    /** Normalizes Arabic/Persian variants without destroying the user's words. */
    fun normalizeQuery(input: String): String = input
        .mapNotNull { ch ->
            persianToCanonical[ch] ?: digitMap[ch] ?: ch
        }
        .joinToString("")
        .replace('\u200c', ' ')
        .replace('\u200f', ' ')
        .replace('\u200e', ' ')
        .replace(punctuation, " ")
        .replace(whitespace, " ")
        .trim()
        .lowercase()

    fun displayQuery(input: String): String = input
        .replace(Regex("[\\u200c\\u200d]"), " ")
        .replace(whitespace, " ")
        .trim()

    fun correctedQuery(input: String): String {
        val normalized = normalizeQuery(input)
        if (normalized.isBlank()) return ""

        commonTypos[normalized]?.let { return it }

        val words = normalized.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        // First try multi-word corrections, then individual tokens.
        val result = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            if (i + 1 < words.size) {
                val pair = "${words[i]} ${words[i + 1]}"
                val pairCorrection = commonTypos[pair]
                if (pairCorrection != null) {
                    result += pairCorrection
                    i += 2
                    continue
                }
            }
            result += commonTypos[words[i]] ?: words[i]
            i++
        }
        return result.joinToString(" ")
    }

    fun suggestions(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()

        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)

        return linkedSetOf(
            original,
            corrected,
            "$corrected song",
            "$corrected music",
            "$corrected آهنگ"
        )
            .filter { it.isNotBlank() && normalizeQuery(it) != normalized }
            .take(5)
    }

    /** Search variants ordered from most natural to most explicit. */
    fun buildQueries(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()

        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)

        return linkedSetOf(
            original,
            corrected,
            normalized,
            "$corrected official",
            "$corrected song"
        )
            .filter { it.isNotBlank() }
            .take(5)
    }

    fun buildGoogleQuery(input: String): String =
        ServerConfig.searchQuery(correctedQuery(input))

    fun parseArtistTitle(input: String): Pair<String?, String?> {
        val clean = displayQuery(input)
        if (clean.isBlank()) return null to null

        val parts = clean.split(" - ", " — ", " – ")
        return if (parts.size >= 2) {
            parts[0].trim().ifBlank { null } to
                parts.drop(1).joinToString(" - ").trim().ifBlank { null }
        } else {
            null to clean
        }
    }

    /**
     * Token-aware similarity. This is intentionally tolerant of Persian/Arabic
     * spelling variants and small English typos.
     */
    fun similarity(a: String, b: String): Int {
        val left = normalizeQuery(a)
        val right = normalizeQuery(b)
        if (left.isBlank() || right.isBlank()) return 0
        if (left == right) return 100
        if (left.contains(right) || right.contains(left)) return 82

        val leftWords = left.split(' ').filter { it.isNotBlank() }
        val rightWords = right.split(' ').filter { it.isNotBlank() }
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0

        val rightSet = rightWords.toSet()
        val exactWords = leftWords.count { it in rightSet }
        val exactScore = exactWords.toDouble() / maxOf(leftWords.size, rightWords.size)

        var fuzzyTotal = 0
        var fuzzyMatches = 0
        for (lw in leftWords) {
            val best = rightWords.maxOfOrNull { rw -> levenshteinSimilarity(lw, rw) } ?: 0
            if (best >= 70) {
                fuzzyTotal += best
                fuzzyMatches++
            }
        }

        val fuzzyScore = if (fuzzyMatches == 0) 0 else fuzzyTotal / fuzzyMatches
        val coverageScore = ((exactScore * 100.0).toInt())
        return maxOf(coverageScore, fuzzyScore).coerceIn(0, 100)
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
        return ((1.0 - distance.toDouble() / maxOf(a.length, b.length)) * 100)
            .toInt()
            .coerceIn(0, 100)
    }
}
