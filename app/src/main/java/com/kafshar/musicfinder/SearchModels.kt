package com.kafshar.musicfinder

/** Canonical result exchanged between search sources and ranking. */
data class SearchResult(
    val title: String,
    val artist: String,
    val album: String = "",
    val artwork: String = "",
    val source: String = "",
    val pageUrl: String = "",
    val mediaUrl: String = "",
    val duration: Long = 0L,
    val quality: Int = 0,
    val score: Int = 0,
    val isPlayable: Boolean = false
)

object SearchRanking {
    fun score(query: String, result: SearchResult): Int {
        val q = SearchEngine.normalizeQuery(query)
        val title = SearchEngine.normalizeQuery(result.title)
        val artist = SearchEngine.normalizeQuery(result.artist)
        var score = 0
        if (q.isNotBlank() && title == q) score += 40
        else score += SearchEngine.similarity(q, title) * 25 / 100
        if (artist.isNotBlank() && q.contains(artist)) score += 25
        if (title.isNotBlank() && q.contains(title)) score += 25
        if (ServerConfig.serverFor(UriHost.host(result.pageUrl))?.trusted == true) score += 20
        if (result.mediaUrl.isNotBlank() && result.isPlayable) score += 40
        if (result.title.isNotBlank() && SearchEngine.similarity(q, result.title) >= 50) score += 10
        return score
    }

    fun rank(query: String, results: Collection<SearchResult>): List<SearchResult> = results
        .map { it.copy(score = score(query, it)) }
        .groupBy { "${SearchEngine.normalizeQuery(it.title)}|${SearchEngine.normalizeQuery(it.artist)}" }
        .values
        .mapNotNull { group -> group.maxByOrNull { it.score } }
        .sortedByDescending { it.score }
}

private object UriHost {
    fun host(url: String): String? = try { android.net.Uri.parse(url).host } catch (_: Exception) { null }
}
