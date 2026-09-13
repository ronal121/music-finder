package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI

/**
 * Google is the only discovery engine.
 *
 * Google decides semantic relevance and spelling/lyric intent. MusicSitePool is
 * the result universe: Google may search the web, but only URLs belonging to our
 * configured music-source bank are allowed into the app's candidate pipeline.
 */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val queries = SearchQueryPlanner.build(query)
        if (queries.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, GoogleResultParser.Result>()
        val target = limit.coerceIn(10, 20)

        // Try the strongest query first. Only use weaker context variants when the
        // allowed music-source universe did not produce enough candidates.
        for (searchQuery in queries) {
            fetch(searchQuery, 100).forEach { result ->
                if (!ServerConfig.isAllowedPageUrl(result.url)) return@forEach
                merged.putIfAbsent(canonicalKey(result.url), result)
            }
            if (merged.size >= target * 3) break
        }

        return diversifyDomains(merged.values.toList(), target)
            .mapIndexed { index, result ->
                result.copy(url = addDiscoveryRank(result.url, index))
            }
    }

    private fun fetch(query: String, limit: Int): List<GoogleResultParser.Result> {
        val googleQuery = SearchEngine.displayQuery(query)
        if (googleQuery.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?q=$encoded&hl=fa&num=${limit.coerceIn(20, 100)}&filter=0"

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 1800
            connection.readTimeout = 3500
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.connect()
            if (connection.responseCode !in 200..399) return emptyList()

            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(5_000_000)
            }

            GoogleResultParser.parseAnchors(html, 500)
                .filter { !it.url.contains("google.", true) }
                .filter { !isSearchEngineUtilityUrl(it.url) }
                .distinctBy { canonicalKey(it.url) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Keep Google's order, but stop one source from consuming the whole result set. */
    private fun diversifyDomains(
        results: List<GoogleResultParser.Result>,
        limit: Int
    ): List<GoogleResultParser.Result> {
        val selected = ArrayList<GoogleResultParser.Result>(limit)
        val domainCounts = HashMap<String, Int>()

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

    private fun addDiscoveryRank(url: String, rank: Int): String =
        url.substringBefore("#") + "#mf-google-rank=$rank"

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
