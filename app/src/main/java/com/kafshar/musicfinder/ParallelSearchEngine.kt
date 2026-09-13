package com.kafshar.musicfinder

import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Google-only discovery bridge.
 *
 * Google is the only engine allowed to decide which pages are relevant. The
 * reference-site pool is applied inside GoogleDiscoveryProvider as a boundary;
 * direct-site search is deliberately not merged into the candidate list because
 * it can flood the UI with low-relevance pages from one domain.
 */
object ParallelSearchEngine {
    private val executor = Executors.newFixedThreadPool(2)
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
                googleProvider.search(query, 30).map(::toCandidate)
            } catch (_: Exception) {
                emptyList()
            }
            callback(generation, candidates)
        }
    }

    /** Compatibility bridge for the existing SearchProvider interface. */
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

    private object CompletedFuture : Future<Any?> {
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = true
        override fun get(): Any? = null
        override fun get(timeout: Long, unit: TimeUnit): Any? = null
    }
}
