package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

/** Lightweight Google HTML discovery. Google order is treated as the primary relevance signal. */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val googleQuery = SearchEngine.buildGoogleQuery(query)
        val encoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?q=$encoded&hl=fa&num=${limit.coerceIn(10, 20)}&filter=0"
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 2500
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.connect()
            if (connection.responseCode !in 200..399) return emptyList()
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(2_000_000) }

            // Do NOT re-rank Google's results with our small local similarity model.
            // Google can understand misspellings/semantic intent such as
            // "وجودم اشو لاشه" -> "چنگیز چاوشی" much better than token matching.
            // Keep Google's result order and only remove noise/duplicates.
            val results = GoogleResultParser.parseAnchors(html, (limit * 8).coerceAtMost(160))
                .filter { !it.url.contains("google.", true) }
                .filter { !isSearchEngineUtilityUrl(it.url) }
                .distinctBy { it.url.substringBefore('#').trimEnd('/').lowercase() }

            // Avoid filling the app with near-identical results from one domain while
            // retaining enough coverage for sites that have several useful qualities.
            val perHost = HashMap<String, Int>()
            val diversified = ArrayList<GoogleResultParser.Result>(limit)
            for (result in results) {
                val host = try {
                    java.net.URI(result.url).host.orEmpty().removePrefix("www.").lowercase()
                } catch (_: Exception) {
                    ""
                }
                val count = perHost[host] ?: 0
                if (host.isNotBlank() && count >= 2) continue
                if (host.isNotBlank()) perHost[host] = count + 1
                diversified += result
                if (diversified.size >= limit) break
            }
            diversified
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isSearchEngineUtilityUrl(url: String): Boolean = try {
        val host = java.net.URI(url).host.orEmpty().lowercase()
        val path = java.net.URI(url).path.orEmpty().lowercase()
        host == "webcache.googleusercontent.com" ||
            path.startsWith("/search") ||
            path.startsWith("/preferences") ||
            path.startsWith("/advanced_search")
    } catch (_: Exception) { true }
}
