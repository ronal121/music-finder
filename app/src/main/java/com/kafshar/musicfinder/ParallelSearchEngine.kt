package com.kafshar.musicfinder

import java.net.URI
import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runtime bridge between the app search pipeline and the real direct-site provider.
 *
 * This is deliberately not a compatibility stub: callers receive the provider's
 * actual results and can feed them into the existing page-inspection pipeline.
 */
object ParallelSearchEngine {
    private val executor = Executors.newFixedThreadPool(2)
    private val provider = DirectSiteSearchProvider()

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
                provider.search(query, 20).map(::toCandidate)
            } catch (_: Exception) {
                emptyList()
            }
            callback(generation, candidates)
        }
    }

    /** Synchronous provider bridge used by SearchProvider compatibility callers. */
    fun searchDirectBlocking(query: String, limit: Int = 20): List<GoogleResultParser.Result> =
        if (query.isBlank() || limit <= 0) emptyList()
        else try { provider.search(query, limit) } catch (_: Exception) { emptyList() }

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
