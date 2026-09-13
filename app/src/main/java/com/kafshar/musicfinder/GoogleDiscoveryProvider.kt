package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI

/**
 * Google is the only semantic discovery engine.
 *
 * Google searches normally so its ranking remains global and meaningful. The
 * app then applies MusicSitePool as the source boundary: only reference-site
 * pages (plus YouTube) can enter the music candidate pipeline.
 */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val variants = SearchQueryPlanner.build(query)
        if (variants.isEmpty()) return emptyList()

        val target = limit.coerceIn(10, 20)
        val merged = LinkedHashMap<String, RankedResult>()

        // Google handles semantic/phonetic correction. Start with the strongest
        // user query and only use contextual variants when necessary.
        for ((variantIndex, variant) in variants.withIndex()) {
            fetch(variant, 100).forEachIndexed { resultIndex, result ->
                if (!isEligibleReferenceResult(result.url)) return@forEachIndexed
                val key = canonicalKey(result.url)
                val candidate = RankedResult(result, variantIndex, resultIndex)
                val previous = merged[key]
                if (previous == null || candidate.discoveryScore < previous.discoveryScore) {
                    merged[key] = candidate
                }
            }

            // Keep Google's first-page relevance dominant. Context variants are
            // only a coverage mechanism, never a competing direct-site engine.
            if (merged.size >= target * 2) break
        }

        return merged.values
            .sortedBy { it.discoveryScore }
            .let { diversifyDomains(it, target) }
            .mapIndexed { index, result ->
                result.result.copy(url = addDiscoveryRank(result.result.url, index))
            }
    }

    private fun fetch(query: String, limit: Int): List<GoogleResultParser.Result> {
        val googleQuery = SearchEngine.displayQuery(query).trim()
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

    private fun isEligibleReferenceResult(url: String): Boolean {
        if (ServerConfig.isYouTubeUrl(url)) return true
        val host = hostKey(url)
        if (host.isBlank()) return false
        return MusicSitePool.domains.any { domain ->
            val normalized = domain.lowercase().removePrefix("www.")
            host == normalized || host.endsWith(".$normalized")
        }
    }

    private fun diversifyDomains(
        results: List<RankedResult>,
        limit: Int
    ): List<RankedResult> {
        val selected = ArrayList<RankedResult>(limit)
        val counts = HashMap<String, Int>()
        for (result in results) {
            if (selected.size >= limit) break
            val domain = hostKey(result.result.url)
            if (domain.isBlank()) continue
            val count = counts[domain] ?: 0
            if (count >= 2) continue
            counts[domain] = count + 1
            selected += result
        }
        return selected
    }

    private data class RankedResult(
        val result: GoogleResultParser.Result,
        val variantIndex: Int,
        val resultIndex: Int
    ) {
        val discoveryScore: Int get() = variantIndex * 1000 + resultIndex
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
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()
        host == "webcache.googleusercontent.com" ||
            path.startsWith("/search") ||
            path.startsWith("/preferences") ||
            path.startsWith("/advanced_search")
    } catch (_: Exception) {
        true
    }
}
