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

        for ((variantIndex, variant) in variants.withIndex()) {
            fetch(variant, 40).forEachIndexed { resultIndex, result ->
                if (!isEligibleResult(result.url)) return@forEachIndexed
                putBest(merged, result, variantIndex, resultIndex, original)
            }
            if (merged.size >= target * 3) break
        }

        return merged.values
            .sortedWith(
                compareBy<RankedResult> { it.variantIndex }
                    .thenByDescending { it.relevanceScore }
                    .thenBy { it.resultIndex }
            )
            .let { diversifyDomains(it, target) }
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
        val n = limit.coerceIn(20, 40)

        // Google serves different HTML to different clients. The Android app must
        // not depend on one particular result-page variant or on the consent page.
        val urls = listOf(
            "https://www.google.com/search?client=firefox-b-d&gbv=1&igu=1&q=$encoded&hl=fa&num=$n&filter=0",
            "https://www.google.com/search?gbv=1&igu=1&q=$encoded&hl=fa&num=$n&filter=0",
            "https://www.google.com/search?client=android&gbv=1&igu=1&q=$encoded&hl=fa&num=$n&filter=0",
            "https://www.google.com/search?q=$encoded&hl=fa&num=$n&filter=0"
        )
        for (requestUrl in urls) {
            val parsed = fetchUrl(requestUrl)
            if (parsed.size >= 3) return parsed
        }
        return emptyList()
    }

    private fun fetchUrl(requestUrl: String): List<GoogleResultParser.Result> {
        return try {
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Referer", "https://www.google.com/")
            connection.connect()
            if (connection.responseCode !in 200..399) return emptyList()
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(3_000_000)
            }
            GoogleResultParser.parseAnchors(html, 300)
                .filter { !it.url.contains("google.", true) }
                .filter { !isSearchEngineUtilityUrl(it.url) }
                .filter { ServerConfig.isPublicWebUrl(it.url) }
                .distinctBy { canonicalKey(it.url) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isEligibleResult(url: String): Boolean = ServerConfig.isPublicWebUrl(url)

    private fun diversifyDomains(results: List<RankedResult>, limit: Int): List<GoogleResultParser.Result> {
        val selected = ArrayList<GoogleResultParser.Result>(limit)
        val counts = HashMap<String, Int>()
        for (result in results) {
            if (selected.size >= limit) break
            val domain = hostKey(result.result.url)
            if (domain.isBlank()) continue
            val count = counts[domain] ?: 0
            if (count >= 3) continue
            counts[domain] = count + 1
            selected += result.result
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
