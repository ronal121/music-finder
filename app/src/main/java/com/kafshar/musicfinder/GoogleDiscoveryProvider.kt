package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Google is the only discovery engine. Every Google request is constrained to
 * MusicSitePool, so Google's semantic ranking is used without unrestricted-web noise.
 */
class GoogleDiscoveryProvider {
    private val executor = Executors.newFixedThreadPool(4)

    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val variants = SearchQueryPlanner.build(query)
        if (variants.isEmpty()) return emptyList()

        val target = limit.coerceIn(10, 20)
        val merged = LinkedHashMap<String, RankedResult>()

        // The original query has priority. We only spend time on weaker variants
        // when the reference-site universe has not yielded enough candidates.
        for ((variantIndex, variant) in variants.withIndex()) {
            val constrainedQueries = ReferenceSiteQueries.build(variant)
            val jobs = constrainedQueries.map { constrained ->
                Callable {
                    fetch(constrained, target).mapIndexed { resultIndex, result ->
                        RankedResult(result, variantIndex, resultIndex)
                    }
                }
            }

            val futures = jobs.map { executor.submit(it) }
            futures.forEach { future ->
                try {
                    future.get(6L, TimeUnit.SECONDS).forEach { candidate ->
                        if (!ServerConfig.isAllowedPageUrl(candidate.result.url)) return@forEach
                        val key = canonicalKey(candidate.result.url)
                        val previous = merged[key]
                        if (previous == null || candidate.discoveryScore < previous.discoveryScore) {
                            merged[key] = candidate
                        }
                    }
                } catch (_: Exception) {
                    // A failed domain batch must not cancel the other reference sites.
                }
            }

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
        val googleQuery = SearchEngine.displayQuery(query)
        if (googleQuery.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?q=$encoded&hl=fa&num=${limit.coerceIn(10, 20)}&filter=0"

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
