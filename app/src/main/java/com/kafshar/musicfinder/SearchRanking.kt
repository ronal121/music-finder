package com.kafshar.musicfinder

/** Deterministic ranking shared by native search and the WebView discovery path. */
object SearchRanking {
    fun webScore(query: String, title: String, url: String, isYouTube: Boolean): Int {
        val q = SearchEngine.withoutSearchNoise(query)
        val t = SearchEngine.withoutSearchNoise(title)
        var score = SearchEngine.similarity(q, t) * 2
        val qTokens = SearchEngine.normalizeQuery(q).split(' ').filter { it.length > 1 }.toSet()
        val tTokens = SearchEngine.normalizeQuery(t).split(' ').filter { it.length > 1 }.toSet()
        score += qTokens.count { it in tTokens } * 8
        val lower = url.lowercase()
        if (listOf("music", "song", "mp3", "download", "ahang", "tarane").any { lower.contains(it) }) score += 8
        if (listOf("/song/", "/music/", "/download", "/dl/", "/track/", ".mp3").any { lower.contains(it) }) score += 10
        if (lower.contains("/search") || lower.contains("/tag/") || lower.contains("/category/") || lower.contains("/login")) score -= 30
        if (isYouTube) score -= 8
        return score.coerceIn(0, 1000)
    }

    fun score(query: String, result: SearchResult): Int {
        val normalizedQuery = SearchEngine.normalizeQuery(query)
        val title = SearchEngine.normalizeQuery(result.title)
        val artist = SearchEngine.normalizeQuery(result.artist)
        var score = 0
        if (normalizedQuery.isNotBlank() && title == normalizedQuery) score += 40
        if (normalizedQuery.isNotBlank() && artist == normalizedQuery) score += 25
        score += SearchEngine.similarity(normalizedQuery, title) * 25 / 100
        score += SearchEngine.similarity(normalizedQuery, artist) * 15 / 100
        if (result.isPlayable || result.mediaUrl.isNotBlank()) score += 40
        if (result.title.isNotBlank() && SearchEngine.similarity(normalizedQuery, result.title) >= 50) score += 10
        if (result.quality > 0) score += result.quality.coerceIn(0, 20)
        return score.coerceIn(0, 200)
    }

    fun rank(query: String, results: List<SearchResult>): List<SearchResult> =
        results.map { it.copy(score = score(query, it)) }
            .groupBy { SearchEngine.normalizeQuery(it.title) to SearchEngine.normalizeQuery(it.artist) }
            .values.mapNotNull { it.maxByOrNull { result -> result.score } }
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.title.lowercase() })
}
