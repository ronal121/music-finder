package com.kafshar.musicfinder

/**
 * Offline search intelligence used before the Google discovery layer.
 * Keeps the app dependency-free while making Persian/English music queries
 * tolerant to spelling, spacing, script and common intent words.
 */
object SearchEngine {
    enum class Intent { UNKNOWN, ARTIST, SONG, ALBUM, ARTIST_AND_SONG, GENERAL }

    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[^\\p{L}\\p{N}\\s-]")
    private val arabicDiacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val zeroWidth = Regex("[\\u200C\\u200D\\u200E\\u200F]")

    private val persianToCanonical = mapOf(
        'آ' to 'ا', 'أ' to 'ا', 'إ' to 'ا', 'ٱ' to 'ا', 'ة' to 'ه',
        'ي' to 'ی', 'ى' to 'ی', 'ك' to 'ک', 'ۀ' to 'ه', 'ؤ' to 'و', 'ئ' to 'ی', 'ـ' to null
    )

    private val commonTypos = mapOf(
        "michal" to "michael", "michal jakson" to "michael jackson", "jakson" to "jackson",
        "swft" to "swift", "talyor" to "taylor", "taylor swft" to "taylor swift",
        "bill jin" to "billie jean", "billie jin" to "billie jean", "adel hello" to "adele hello",
        "aref" to "aref", "araf" to "aref", "areff" to "aref"
    )

    private val albumWords = setOf("آلبوم", "البوم", "album")
    private val artistHints = setOf("خواننده", "خوانندگان", "singer", "artist", "آهنگ های", "آهنگهای", "موزیک های", "موزیکهای", "music of")
    private val songHints = setOf("آهنگ", "اهنگ", "ترانه", "song", "track", "mp3")

    fun normalizeQuery(input: String): String = input
        .replace(zeroWidth, "")
        .replace(arabicDiacritics, "")
        .mapNotNull { persianToCanonical[it] ?: it }
        .joinToString("")
        .replace(punctuation, " ")
        .replace(whitespace, " ")
        .trim()
        .lowercase()

    fun displayQuery(input: String): String = input.replace(zeroWidth, "").replace(whitespace, " ").trim()

    fun correctedQuery(input: String): String {
        val normalized = normalizeQuery(input)
        commonTypos[normalized]?.let { return it }
        return normalized.split(' ').filter { it.isNotBlank() }.joinToString(" ") { commonTypos[it] ?: it }
    }

    fun detectIntent(input: String): Intent {
        val clean = correctedQuery(input)
        if (clean.isBlank()) return Intent.UNKNOWN
        if (albumWords.any { clean.contains(it) }) return Intent.ALBUM
        val hasArtistHint = artistHints.any { clean.contains(it) }
        val hasSongHint = songHints.any { clean.contains(it) }
        if (hasArtistHint && hasSongHint) return Intent.ARTIST_AND_SONG
        if (hasArtistHint) return Intent.ARTIST
        if (hasSongHint) return Intent.SONG
        val tokens = clean.split(' ').filter { it.isNotBlank() }
        return when { tokens.size == 1 -> Intent.ARTIST; tokens.size >= 2 -> Intent.GENERAL; else -> Intent.UNKNOWN }
    }

    fun suggestions(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val corrected = correctedQuery(original)
        val intent = detectIntent(original)
        val candidates = linkedSetOf<String>().apply {
            add(original)
            if (corrected != normalizeQuery(original)) add(corrected)
            when (intent) {
                Intent.ARTIST -> { add("$corrected آهنگ"); add("$corrected خواننده"); add("$corrected music") }
                Intent.SONG, Intent.ARTIST_AND_SONG -> { add("$corrected آهنگ"); add("$corrected music") }
                else -> { add("$corrected آهنگ"); add("$corrected music") }
            }
        }
        return candidates.filter { it.isNotBlank() }.take(6)
    }

    fun buildQueries(input: String): List<String> {
        val original = displayQuery(input)
        if (original.isBlank()) return emptyList()
        val corrected = correctedQuery(original)
        val normalized = normalizeQuery(corrected)
        val intent = detectIntent(original)
        return linkedSetOf<String>().apply {
            add(original)
            if (corrected != normalized) add(corrected)
            when (intent) {
                Intent.ARTIST -> { add("$corrected آهنگ"); add("$corrected music"); add("$corrected خواننده") }
                Intent.SONG -> { add("$corrected آهنگ"); add("$corrected music"); add("$corrected mp3") }
                Intent.ALBUM -> { add(corrected); add("$corrected album") }
                else -> { add("$corrected آهنگ"); add("$corrected music") }
            }
        }.filter { it.isNotBlank() }.take(6)
    }

    fun buildGoogleQuery(input: String): String {
        val corrected = correctedQuery(input)
        if (corrected.isBlank()) return "music"
        return when (detectIntent(corrected)) {
            Intent.ARTIST -> "$corrected (آهنگ OR خواننده OR music)"
            Intent.SONG, Intent.ARTIST_AND_SONG -> "$corrected (آهنگ OR music OR mp3)"
            Intent.ALBUM -> "$corrected (آلبوم OR album OR music)"
            else -> "$corrected (آهنگ OR music)"
        }
    }

    fun parseArtistTitle(input: String): Pair<String?, String?> {
        val clean = displayQuery(input)
        val parts = clean.split(" - ", " — ", " – ")
        return if (parts.size >= 2) parts[0].trim().ifBlank { null } to parts.drop(1).joinToString(" - ").trim().ifBlank { null } else null to clean.ifBlank { null }
    }

    fun similarity(a: String, b: String): Int {
        val left = normalizeQuery(a); val right = normalizeQuery(b)
        if (left.isBlank() || right.isBlank()) return 0
        if (left == right) return 100
        if (left.contains(right) || right.contains(left)) return 75
        val leftWords = left.split(' ').filter { it.isNotBlank() }; val rightWords = right.split(' ').filter { it.isNotBlank() }
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
            val next = IntArray(b.length + 1); next[0] = i + 1
            for (j in b.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (a[i] == b[j]) 0 else 1)
            row = next
        }
        val distance = row[b.length]
        return ((1.0 - distance.toDouble() / maxOf(a.length, b.length)) * 100).toInt().coerceIn(0, 100)
    }
}
