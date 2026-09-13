package com.kafshar.musicfinder

import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime bridge between discovery and the real direct-site search pipeline.
 *
 * Google is the primary discovery/ranking source because it already understands
 * web-wide relevance, spelling mistakes and semantic intent. Direct-site discovery
 * runs in parallel as a fallback/coverage layer over MusicSitePool.
 */
object ParallelSearchEngine {
    private val executor = Executors.newFixedThreadPool(2)
    private val discoveryExecutor = Executors.newFixedThreadPool(2)
    private val directProvider = DirectSiteSearchProvider()
    private val googleProvider = GoogleDiscoveryProvider()

    fun searchDirect(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> {
        if (query.isBlank()) {
            callback(generation, emptyList())
            return CompletedFuture
        }

        return executor.submit {
            val candidates = try {
                searchCombined(query, 30).map(::toCandidate)
            } catch (_: Exception) {
                emptyList()
            }
            callback(generation, candidates)
        }
    }

    /** Synchronous bridge for the legacy SearchProvider interface. */
    fun searchDirectBlocking(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val result = AtomicReference<List<GoogleResultParser.Result>>(emptyList())
        val latch = CountDownLatch(1)
        val future = searchDirect(query, 0) { _, candidates ->
            result.set(candidates.map {
                GoogleResultParser.Result(it.url, it.title, ServerConfig.isYouTubeUrl(it.url))
            }.take(limit))
            latch.countDown()
        }
        return try {
            if (latch.await(45L, TimeUnit.SECONDS)) result.get() else emptyList()
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            emptyList()
        }
    }

    fun search(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> = searchDirect(query, generation, callback)

    private fun searchCombined(query: String, limit: Int): List<GoogleResultParser.Result> {
        val googleFuture = discoveryExecutor.submit<List<GoogleResultParser.Result>> {
            try { googleProvider.search(query, limit) } catch (_: Exception) { emptyList() }
        }
        val directFuture = discoveryExecutor.submit<List<GoogleResultParser.Result>> {
            try { directProvider.search(query, limit * 2) } catch (_: Exception) { emptyList() }
        }

        val google = try { googleFuture.get(7L, TimeUnit.SECONDS) } catch (_: Exception) { emptyList() }
        val direct = try { directFuture.get(8L, TimeUnit.SECONDS) } catch (_: Exception) { emptyList() }
        if (!googleFuture.isDone) googleFuture.cancel(true)
        if (!directFuture.isDone) directFuture.cancel(true)

        val merged = LinkedHashMap<String, RankedResult>()

        // Preserve Google's actual ordering. This is important for semantic/fuzzy
        // queries where our local token similarity can be very wrong.
        google.forEachIndexed { index, result ->
            merged.putIfAbsent(
                canonicalKey(result.url),
                RankedResult(result, sourceBonus = 1000, discoveryRank = index)
            )
        }

        // Direct-site results are useful coverage, but must not outrank a Google
        // result merely because a URL contains words like "music" or "download".
        direct.forEachIndexed { index, result ->
            merged.putIfAbsent(
                canonicalKey(result.url),
                RankedResult(result, sourceBonus = 0, discoveryRank = index)
            )
        }

        return merged.values
            .filter { it.result.url.startsWith("http", true) && ServerConfig.isAllowedPageUrl(it.result.url) }
            .sortedWith(
                compareByDescending<RankedResult> {
                    if (it.sourceBonus > 0) {
                        10_000 - it.discoveryRank
                    } else {
                        SearchRanking.webScore(query, it.result.title, it.result.url, it.result.isYouTube)
                    }
                }.thenBy { canonicalKey(it.result.url) }
            )
            .map { it.result }
            .take(limit)
    }

    fun toCandidate(result: GoogleResultParser.Result): Candidate {
        val site = try {
            URI(result.url).host.orEmpty().removePrefix("www.")
        } catch (_: Exception) {
            "Music"
        }
        return Candidate(
            url = result.url,
            title = result.title,
            artist = "Unknown Artist",
            site = site.ifBlank { "Music" },
            cover = "",
            score = SearchRanking.webScore("", result.title, result.url, result.isYouTube)
        )
    }

    data class Candidate(
        val url: String,
        val title: String,
        val artist: String,
        val site: String,
        val cover: String = "",
        val score: Int = 0
    )

    private data class RankedResult(
        val result: GoogleResultParser.Result,
        val sourceBonus: Int,
        val discoveryRank: Int
    )

    private fun canonicalKey(url: String): String =
        url.substringBefore('#').trimEnd('/').lowercase()

    private object CompletedFuture : Future<Any?> {
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = true
        override fun get(): Any? = null
        override fun get(timeout: Long, unit: TimeUnit): Any? = null
    }
}
