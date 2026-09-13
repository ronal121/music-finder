package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI

/**
 * Google is the semantic discovery engine. The configured reference pool is a
 * source boundary, not a replacement for Google's ranking.
 */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val original = SearchEngine.displayQuery(query).trim()
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original).trim()
        val clean = SearchEngine.withoutSearchNoise(original).trim()
        val semanticVariants = linkedSetOf<String>().apply {
            add(original)
            if (corrected.isNotBlank()) add(corrected)
            if (clean.isNotBlank()) add(clean)
            add("$original آهنگ")
            add("$original متن آهنگ")
        }.take(5)

        val target = limit.coerceIn(10, 20)
        val merged = LinkedHashMap<String, RankedResult>()

        for ((variantIndex, variant) in semanticVariants.withIndex()) {
            fetch(variant, 40).forEachIndexed { resultIndex, result ->
                if (!isEligibleReferenceResult(result.url)) return@forEachIndexed
                putBest(merged, result, variantIndex, -1, resultIndex)
            }
            if (merged.size >= target * 2) break
        }

        if (merged.size < target) {
            val constrainedQueries = ReferenceSiteQueries.build(original).take(18)
            for ((index, constrainedQuery) in constrainedQueries.withIndex()) {
                fetch(constrainedQuery, 40).forEachIndexed { resultIndex, result ->
                    if (!isEligibleReferenceResult(result.url)) return@forEachIndexed
                    putBest(merged, result, 10 + index, index, resultIndex)
                }
                if (merged.size >= target * 2) break
            }
        }

        return merged.values
            .sortedBy { it.discoveryScore }
            .let { diversifyDomains(it, target) }
            .mapIndexed { index, result ->
                result.result.copy(url = addDiscoveryRank(result.result.url, index))
            }
    }

    private fun putBest(
        merged: LinkedHashMap<String, RankedResult>,
        result: GoogleResultParser.Result,
        variantIndex: Int,
        batchIndex: Int,
        resultIndex: Int
    ) {
        val key = canonicalKey(result.url)
        val candidate = RankedResult(result, variantIndex, batchIndex, resultIndex)
        val previous = merged[key]
        if (previous == null || candidate.discoveryScore < previous.discoveryScore) {
            merged[key] = candidate
        }
    }

    private fun fetch(query: String, limit: Int): List<GoogleResultParser.Result> {
        val googleQuery = SearchEngine.displayQuery(query).trim()
        if (googleQuery.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8.toString())
        val urls = listOf(
            "https://www.google.com/search?gbv=1&q=$encoded&hl=fa&num=${limit.coerceIn(20, 40)}&filter=0",
            "https://www.google.com/search?q=$encoded&hl=fa&num=${limit.coerceIn(20, 40)}&filter=0"
        )

        for (requestUrl in urls) {
            val parsed = fetchUrl(requestUrl)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun fetchUrl(requestUrl: String): List<GoogleResultParser.Result> {
        return try {
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 3500
            connection.readTimeout = 5500
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.connect()
            if (connection.responseCode !in 200..399) return emptyList()

            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(2_500_000)
            }

            GoogleResultParser.parseAnchors(html, 200)
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
        val batchIndex: Int,
        val resultIndex: Int
    ) {
        val discoveryScore: Int
            get() = variantIndex * 1_000_000 + (batchIndex + 1).coerceAtLeast(0) * 1_000 + resultIndex
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
