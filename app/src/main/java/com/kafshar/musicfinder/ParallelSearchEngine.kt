package com.kafshar.musicfinder

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Google-only discovery marker.
 *
 * Search discovery is intentionally owned by MainActivity's Google WebView.
 * This object remains only as a compatibility shim for older callers.
 */
object ParallelSearchEngine {
    fun searchDirect(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> {
        callback(generation, emptyList())
        return CompletedFuture
    }

    fun search(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> = searchDirect(query, generation, callback)

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
