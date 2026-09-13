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

        val original = SearchEngine.displayQuery(query).trim()
        if (original.isBlank()) return emptyList()

        // Google remains the authority for semantic correction. For lyric fragments,
        // adding music-context variants helps when the raw fragment is ambiguous.
        val queries = linkedSetOf<String>().apply {
            add(original)
            add("$original آهنگ")
            add("$original متن آهنگ")
            val normalized = SearchEngine.normalizeQuery(original)
            if (normalized.isNotBlank() && !normalized.equals(original, ignoreCase = true)) {
                add(normalized)
            }
        }

        val perQuery = limit.coerceIn(10, 20)
        val merged = LinkedHashMap<String, GoogleResultParser.Result>()
        for (searchQuery in queries) {
            fetch(searchQuery, perQuery).forEach { result ->
                merged.putIfAbsent(canonicalKey(result.url), result)
            }
            if (merged.size >= limit * 5) break
        }

        return diversifyDomains(merged.values.toList(), limit)
    }

    private fun fetch(query: String, limit: Int): List<GoogleResultParser.Result> {
        // Do not pass a locally "corrected" query here. The point of this layer is
        // to let Google resolve typo/phonetic/semantic intent itself.
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

            GoogleResultParser.parseAnchors(html, (limit * 10).coerceAtMost(200))
                .filter { !it.url.contains("google.", true) }
                .filter { !isSearchEngineUtilityUrl(it.url) }
                .distinctBy { canonicalKey(it.url) }
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
        val domainCounts = HashMap<String, Int>()

        // Hard cap: at most two pages from one domain in the discovery result set.
        for (result in results) {
            if (selected.size >= limit) break
            val domain = hostKey(result.url)
            if (domain.isBlank()) continue
            val count = domainCounts[domain] ?: 0
            if (count >= 2) continue
            domainCounts[domain] = count + 1
            selected += result
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
