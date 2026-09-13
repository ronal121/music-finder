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
 * Google discovery is complementary. Direct search always runs against the full
 * MusicSitePool, and both result sets are normalized, deduplicated and ranked.
 */
object ParallelSearchEngine {
    private val executor = Executors.newFixedThreadPool(2)
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
                searchCombined(query, 20).map(::toCandidate)
            } catch (_: Exception) {
                emptyList()
            }
            callback(generation, candidates)
        }
    }

    /** Synchronous bridge for the legacy SearchProvider interface. It still executes searchDirect(). */
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
        val merged = LinkedHashMap<String, GoogleResultParser.Result>()

        try {
            googleProvider.search(query, limit).forEach { merged.putIfAbsent(canonicalKey(it.url), it) }
        } catch (_: Exception) {
        }

        try {
            directProvider.search(query, limit * 2).forEach { merged.putIfAbsent(canonicalKey(it.url), it) }
        } catch (_: Exception) {
        }

        return merged.values
            .filter { it.url.startsWith("http", true) && ServerConfig.isAllowedPageUrl(it.url) }
            .sortedWith(
                compareByDescending<GoogleResultParser.Result> {
                    SearchRanking.webScore(query, it.title, it.url, it.isYouTube)
                }.thenBy { canonicalKey(it.url) }
            )
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
