package com.kafshar.musicfinder

/**
 * Builds Google queries constrained to the complete reference-site universe.
 * The search intents below deliberately cover the different page categories
 * represented by the site bank: songs, lyrics, downloads, archives, local
 * music, genres, live/remix material and international/electronic music.
 */
object ReferenceSiteQueries {
    private const val DOMAINS_PER_QUERY = 32

    private val activeCategories = listOf(
        "",
        "آهنگ",
        "موزیک",
        "ترانه",
        "متن آهنگ",
        "دانلود آهنگ",
        "mp3",
        "lyrics",
        "song",
        "music",
        "آهنگ قدیمی",
        "آهنگ جدید",
        "پاپ",
        "رپ",
        "راک",
        "سنتی",
        "کلاسیک",
        "ریمیکس",
        "آکوستیک",
        "لایو",
        "کاور",
        "موسیقی بی کلام",
        "مازندرانی",
        "گیلکی",
        "کردی",
        "ترکی",
        "عربی",
        "EDM",
        "house",
        "techno",
        "trance",
        "electronic"
    )

    fun build(input: String): List<String> {
        val value = input.trim().replace(Regex("\\s+"), " ")
        if (value.isBlank()) return emptyList()

        val sites = MusicSitePool.domains
        if (sites.isEmpty()) return listOf(value)

        val queries = ArrayList<String>()
        val batches = sites.chunked(DOMAINS_PER_QUERY)

        // Keep the exact user query first. Category enrichment is a fallback
        // rather than a replacement, so Google's semantic interpretation remains
        // authoritative for ordinary searches.
        for (category in activeCategories) {
            val categoryQuery = when {
                category.isBlank() -> value
                else -> "$value $category"
            }
            for (batch in batches) {
                val domains = batch.joinToString(" OR ") { "site:$it" }
                queries += "$categoryQuery ($domains)"
            }
        }
        return queries
    }
}
