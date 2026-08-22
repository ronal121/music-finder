package com.kafshar.musicfinder

/** Deterministic, offline ranking for search results. */
object SearchRanking {
    fun score(query: String, result: SearchResult, trusted: Boolean = false): Int {
        val normalizedQuery = SearchEngine.normalizeQuery(query)
        val title = SearchEngine.normalizeQuery(result.title)
        val artist = SearchEngine.normalizeQuery(result.artist)
        var score = 0

        if (normalizedQuery.isNotBlank() && title == normalizedQuery) score += 40
        if (normalizedQuery.isNotBlank() && artist == normalizedQuery) score += 25
        score += (SearchEngine.similarity(normalizedQuery, title) * 25 / 100)
        score += (SearchEngine.similarity(normalizedQuery, artist) * 15 / 100)
        if (trusted) score += 20
        if (result.isPlayable || result.mediaUrl.isNotBlank()) score += 40
        if (result.title.isNotBlank() && SearchEngine.similarity(normalizedQuery, result.title) >= 50) score += 10
        if (result.quality > 0) score += result.quality.coerceIn(0, 20)
        return score.coerceIn(0, 200)
    }

    fun rank(query: String, results: List<SearchResult>): List<SearchResult> =
        results
            .map { it.copy(score = score(query, it, trusted = ServerConfig.serverForUrl(it.pageUrl)?.trusted == true)) }
            .groupBy { SearchEngine.normalizeQuery(it.title) to SearchEngine.normalizeQuery(it.artist) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.score } }
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.title.lowercase() })
}
