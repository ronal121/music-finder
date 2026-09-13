package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI

/**
 * Google is the semantic discovery layer. Keep Google's result order intact;
 * local ranking must not replace Google's understanding of misspelled lyrics,
 * song names and artist intent.
 */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        // Send the user's actual query. Do not replace it with our small typo
        // dictionary: Google is much better at semantic lyric/song correction.
        val googleQuery = SearchEngine.displayQuery(query)
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

            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(2_000_000)
            }

            val parsed = GoogleResultParser.parseAnchors(
                html,
                (limit * 10).coerceAtMost(200)
            )
                .filter { !it.url.contains("google.", true) }
                .filter { !isSearchEngineUtilityUrl(it.url) }
                .distinctBy { canonicalKey(it.url) }

            diversifyDomains(parsed, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Preserve Google's order while preventing one domain from consuming the list. */
    private fun diversifyDomains(
        results: List<GoogleResultParser.Result>,
        limit: Int
    ): List<GoogleResultParser.Result> {
        val selected = ArrayList<GoogleResultParser.Result>(limit)
        val usedDomains = HashSet<String>()

        // First pass: one result per domain. This gives the app the same broad
        // coverage as the web search instead of ten pages from one music site.
        for (result in results) {
            if (selected.size >= limit) break
            val domain = hostKey(result.url)
            if (domain.isBlank() || usedDomains.add(domain)) selected += result
        }

        // If Google returned fewer unique domains, fill the remaining slots in
        // Google's original order, allowing a second/third result from a domain.
        if (selected.size < limit) {
            val selectedKeys = selected.mapTo(HashSet()) { canonicalKey(it.url) }
            for (result in results) {
                if (selected.size >= limit) break
                if (selectedKeys.add(canonicalKey(result.url))) selected += result
            }
        }
        return selected
    }

    private fun hostKey(url: String): String = try {
        URI(url).host.orEmpty().lowercase().removePrefix("www.")
    } catch (_: Exception) {
        ""
    }

    private fun canonicalKey(url: String): String =
        url.substringBefore('#').trimEnd('/').lowercase()

    private fun isSearchEngineUtilityUrl(url: String): Boolean = try {
        val host = URI(url).host.orEmpty().lowercase()
        val path = URI(url).path.orEmpty().lowercase()
        host == "webcache.googleusercontent.com" ||
            path.startsWith("/search") ||
            path.startsWith("/preferences") ||
            path.startsWith("/advanced_search")
    } catch (_: Exception) {
        true
    }
}
