package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI

/**
 * Google is the only semantic discovery engine.
 *
 * One logical user search is expanded internally into a few Google variants,
 * while every request is constrained to the MusicSitePool universe. This keeps
 * Google's semantic ranking but prevents weak variants from becoming separate
 * UI search batches.
 */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val original = SearchEngine.displayQuery(query).trim()
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original).trim()
        val clean = SearchEngine.withoutSearchNoise(original).trim()
        val variants = linkedSetOf<String>().apply {
            add(original)
            add("\"$original\"")
            add("$original آهنگ")
            add("$original \"متن آهنگ\"")
            if (corrected.isNotBlank() && !corrected.equals(original, ignoreCase = true)) {
                add(corrected)
                add("\"$corrected\"")
            }
            if (clean.isNotBlank() && !clean.equals(original, ignoreCase = true)) {
                add("\"$clean\" \"متن آهنگ\"")
            }
        }.take(6)

        val target = limit.coerceIn(10, 20)
        val merged = LinkedHashMap<String, RankedResult>()

        for ((variantIndex, variant) in variants.withIndex()) {
            val constrainedQueries = ReferenceSiteQueries.build(variant)
            for ((batchIndex, constrainedQuery) in constrainedQueries.withIndex()) {
                fetch(constrainedQuery, 40).forEachIndexed { resultIndex, result ->
                    if (!isEligibleReferenceResult(result.url)) return@forEachIndexed
                    val key = canonicalKey(result.url)
                    val candidate = RankedResult(
                        result = result,
                        variantIndex = variantIndex,
                        batchIndex = batchIndex,
                        resultIndex = resultIndex
                    )
                    val previous = merged[key]
                    if (previous == null || candidate.discoveryScore < previous.discoveryScore) {
                        merged[key] = candidate
                    }
                }

                // The first logical variant is authoritative. Later variants only
                // provide coverage if the first one did not yield enough candidates.
                if (merged.size >= target * 2) break
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
        val googleQuery = SearchEngine.displayQuery(query).trim()
        if (googleQuery.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?q=$encoded&hl=fa&num=${limit.coerceIn(20, 40)}&filter=0"

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 2200
            connection.readTimeout = 4000
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
            get() = variantIndex * 1_000_000 + batchIndex * 1_000 + resultIndex
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
