package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

/** Lightweight Google HTML discovery. Failure is non-fatal; direct site search remains authoritative fallback. */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val googleQuery = SearchEngine.buildGoogleQuery(query)
        val encoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?q=$encoded&hl=fa&num=${limit.coerceIn(10, 20)}"
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 1800
            connection.readTimeout = 3000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.connect()
            if (connection.responseCode !in 200..399) return emptyList()
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(2_000_000) }
            GoogleResultParser.parseAnchors(html, limit * 3)
                .filter { !it.url.contains("google.", true) }
                .distinctBy { it.url.substringBefore('#').trimEnd('/').lowercase() }
                .sortedByDescending { SearchRanking.webScore(query, it.title, it.url, it.isYouTube) }
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
