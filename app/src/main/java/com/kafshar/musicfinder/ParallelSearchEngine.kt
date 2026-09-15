package com.kafshar.musicfinder

import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Google discovery -> parallel page inspection -> media probing.
 *
 * searchDirect() is the real native playable-search path. The compatibility
 * page-discovery path is kept separate because MainActivity still performs its
 * WebView fallback for pages whose audio is generated at runtime.
 */
object ParallelSearchEngine {
    private val executor = Executors.newFixedThreadPool(4)
    private val googleProvider = GoogleDiscoveryProvider()
    private val pageInspector = ParallelPageInspector(8)

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
            val pages = try {
                googleProvider.search(query, 15)
                    .filterNot { it.isYouTube }
                    .map { ParallelPageInspector.Page(it.url, it.title) }
            } catch (_: Exception) {
                emptyList()
            }

            if (pages.isEmpty()) {
                callback(generation, emptyList())
                return@submit
            }

            val candidates = java.util.Collections.synchronizedList(mutableListOf<Candidate>())
            val done = CountDownLatch(1)
            val currentGeneration = AtomicReference(generation)

            pageInspector.inspect(
                generation = generation,
                pages = pages,
                isGenerationCurrent = { it == currentGeneration.get() },
                onResult = { inspection ->
                    if (inspection.candidates.isEmpty()) return@onResult
                    inspection.candidates
                        .asSequence()
                        .distinct()
                        .take(8)
                        .forEach { mediaUrl ->
                            val validation = try {
                                MediaProbe.probe(mediaUrl, inspection.page.url)
                            } catch (_: Exception) {
                                null
                            }
                            if (validation?.playable != true) return@forEach
                            val finalUrl = validation.finalUrl.ifBlank { mediaUrl }
                            if (!ServerConfig.isAllowedMediaUrl(finalUrl, inspection.page.url)) return@forEach
                            candidates += Candidate(
                                url = finalUrl,
                                title = inspection.title,
                                artist = inspection.artist,
                                site = hostName(inspection.page.url),
                                cover = inspection.cover,
                                score = SearchRanking.webScore(query, inspection.title, inspection.page.url, false)
                            )
                        }
                },
                onComplete = { done.countDown() }
            )

            try {
                done.await(45L, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            val ranked = candidates
                .distinctBy { it.url.substringBefore('#').trimEnd('/').lowercase() }
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.title.lowercase() })
                .take(30)
            callback(generation, ranked)
        }
    }

    /**
     * Returns Google-discovered page URLs for the existing MainActivity/WebView
     * pipeline. This deliberately does not return media URLs.
     */
    fun discoverPagesBlocking(query: String, limit: Int = 20): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return try {
            googleProvider.search(query, limit).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Compatibility bridge for SearchProvider callers. */
    fun searchDirectBlocking(query: String, limit: Int = 20): List<GoogleResultParser.Result> =
        discoverPagesBlocking(query, limit)

    fun search(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> = searchDirect(query, generation, callback)

    fun toCandidate(result: GoogleResultParser.Result): Candidate {
        return Candidate(
            url = result.url,
            title = result.title,
            artist = "Unknown Artist",
            site = hostName(result.url),
            cover = "",
            score = SearchRanking.webScore("", result.title, result.url, result.isYouTube)
        )
    }

    private fun hostName(url: String): String = try {
        URI(url).host.orEmpty().removePrefix("www.").ifBlank { "Music" }
    } catch (_: Exception) {
        "Music"
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
