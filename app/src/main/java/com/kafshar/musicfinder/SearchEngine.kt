package com.kafshar.musicfinder

/** Offline query intelligence used by both Google and direct site search. */
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
        "michal jaskon" to "michael jackson",
        "jakson" to "jackson",
        "jaskon" to "jackson",
        "micheal" to "michael",
        "swft" to "swift",
        "talyor" to "taylor",
        "taylor swft" to "taylor swift",
        "bill jin" to "billie jean",
        "billie jin" to "billie jean",
        "billie jine" to "billie jean",
        "adel" to "adele",
        "adele helol" to "adele hello"
    )

    fun normalizeQuery(input: String): String = input
        .mapNotNull { ch -> persianToCanonical[ch] ?: digitMap[ch] ?: ch }
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
        val result = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            if (i + 1 < words.size) {
                val pair = "${words[i]} ${words[i + 1]}"
                commonTypos[pair]?.let {
                    result += it
                    i += 2
                    continue
                }
            }
            result += bestCorrection(words[i])
            i++
        }
        return result.joinToString(" ")
    }

    private fun bestCorrection(word: String): String {
        commonTypos[word]?.let { return it }
        if (word.length < 4) return word
        val best = commonTypos.entries
            .filter { it.key.length >= 4 && kotlin.math.abs(it.key.length - word.length) <= 2 }
            .maxByOrNull { levenshteinSimilarity(word, it.key) }
        return if (best != null && levenshteinSimilarity(word, best.key) >= 86) best.value else word
    }

    fun suggestions(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)
        return linkedSetOf(
            corrected,
            original,
            "$corrected song",
            "$corrected music",
            "$corrected آهنگ"
        ).filter { it.isNotBlank() && normalizeQuery(it) != normalized }.take(5)
    }

    /** Multiple independent search variants; callers should deduplicate results. */
    fun buildQueries(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val normalized = normalizeQuery(original)
        val corrected = correctedQuery(original)
        val words = corrected.split(' ').filter { it.isNotBlank() }
        val variants = linkedSetOf<String>()
        variants += original
        variants += corrected
        if (normalized != corrected) variants += normalized
        if (words.size > 1) variants += words.asReversed().joinToString(" ")
        variants += "$corrected song"
        return variants.filter { it.isNotBlank() }.take(6)
    }

    fun buildGoogleQuery(input: String): String = ServerConfig.searchQuery(correctedQuery(input))

    fun parseArtistTitle(input: String): Pair<String?, String?> {
        val clean = displayQuery(input)
        if (clean.isBlank()) return null to null
        val parts = clean.split(" - ", " — ", " – ")
        return if (parts.size >= 2) {
            parts[0].trim().ifBlank { null } to parts.drop(1).joinToString(" - ").trim().ifBlank { null }
        } else null to clean
    }

    fun similarity(a: String, b: String): Int {
        val left = normalizeQuery(a)
        val right = normalizeQuery(b)
        if (left.isBlank() || right.isBlank()) return 0
        if (left == right) return 100
        if (left.contains(right) || right.contains(left)) return 88

        val leftWords = left.split(' ').filter { it.isNotBlank() }
        val rightWords = right.split(' ').filter { it.isNotBlank() }
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0

        val exact = leftWords.count { it in rightWords }
        val exactCoverage = exact.toDouble() / maxOf(leftWords.size, rightWords.size)
        val fuzzy = leftWords.mapNotNull { lw -> rightWords.maxOfOrNull { rw -> levenshteinSimilarity(lw, rw) } }
            .filter { it >= 65 }
        val fuzzyCoverage = if (fuzzy.isEmpty()) 0 else fuzzy.average()
        return maxOf((exactCoverage * 100).toInt(), fuzzyCoverage.toInt()).coerceIn(0, 100)
    }

    private fun levenshteinSimilarity(a: String, b: String): Int {
        if (a == b) return 100
        if (a.isEmpty() || b.isEmpty()) return 0
        var row = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length + 1)
            next[0] = i + 1
            for (j in b.indices) {
                next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (a[i] == b[j]) 0 else 1)
            }
            row = next
        }
        val distance = row[b.length]
        return ((1.0 - distance.toDouble() / maxOf(a.length, b.length)) * 100).toInt().coerceIn(0, 100)
    }
}
