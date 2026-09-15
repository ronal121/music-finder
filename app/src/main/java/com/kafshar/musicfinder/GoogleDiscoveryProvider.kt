package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.net.URI

/** Google-only discovery. Google decides relevance; the app only removes utility URLs. */
class GoogleDiscoveryProvider {
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val original = SearchEngine.displayQuery(query).trim()
        if (original.isBlank()) return emptyList()

        val corrected = SearchEngine.correctedQuery(original).trim()
        val clean = SearchEngine.withoutSearchNoise(corrected).trim()
        val variants = linkedSetOf<String>().apply {
            add(original)
            if (corrected.isNotBlank()) add(corrected)
            if (clean.isNotBlank()) add(clean)
            if (clean.isNotBlank()) add("$clean آهنگ")
            if (clean.isNotBlank()) add("$clean song")
        }.take(5)

        val target = limit.coerceIn(10, 20)
        val merged = LinkedHashMap<String, RankedResult>()

        // The first query is authoritative. Variants are fallback discovery and
        // can only contribute URLs not already found by an earlier query.
        for ((variantIndex, variant) in variants.withIndex()) {
            fetch(variant, 40).forEachIndexed { resultIndex, result ->
                if (!isEligibleResult(result.url)) return@forEachIndexed
                putBest(merged, result, variantIndex, resultIndex, original)
            }
            if (merged.size >= target * 3) break
        }

        return merged.values
            .sortedBy { it.discoveryScore }
            .let { diversifyDomains(it, target) }
            .mapIndexed { index, ranked ->
                ranked.result.copy(url = addDiscoveryRank(ranked.result.url, index))
            }
    }

    private fun putBest(
        merged: LinkedHashMap<String, RankedResult>,
        result: GoogleResultParser.Result,
        variantIndex: Int,
        resultIndex: Int,
        query: String
    ) {
        val candidate = RankedResult(
            result = result,
            variantIndex = variantIndex,
            resultIndex = resultIndex,
            relevanceScore = SearchEngine.similarity(query, result.title)
        )
        val key = canonicalKey(result.url)
        val previous = merged[key]
        if (previous == null || candidate.discoveryScore < previous.discoveryScore ||
            (candidate.discoveryScore == previous.discoveryScore && candidate.relevanceScore > previous.relevanceScore)) {
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
                .filter { ServerConfig.isPublicWebUrl(it.url) }
                .distinctBy { canonicalKey(it.url) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Any public result from Google is eligible for page inspection. */
    private fun isEligibleResult(url: String): Boolean = ServerConfig.isPublicWebUrl(url)

    private fun diversifyDomains(results: List<RankedResult>, limit: Int): List<RankedResult> {
        val selected = ArrayList<RankedResult>(limit)
        val counts = HashMap<String, Int>()
        for (result in results) {
            if (selected.size >= limit) break
            val domain = hostKey(result.result.url)
            if (domain.isBlank()) continue
            val count = counts[domain] ?: 0
            if (count >= 3) continue
            counts[domain] = count + 1
            selected += result
        }
        return selected
    }

    private data class RankedResult(
        val result: GoogleResultParser.Result,
        val variantIndex: Int,
        val resultIndex: Int,
        val relevanceScore: Int
    ) {
        val discoveryScore: Int get() = variantIndex * 1_000_000 + resultIndex
    }

    private fun addDiscoveryRank(url: String, rank: Int): String =
        url.substringBefore("#") + "#mf-google-rank=$rank"

    private fun hostKey(url: String): String = try {
        URI(url).host.orEmpty().lowercase().removePrefix("www.")
    } catch (_: Exception) { "" }

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
    } catch (_: Exception) { true }
}
