package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ParallelPageInspector(private val concurrency: Int = 4) {
    data class Page(val url: String, val titleHint: String = "")
    data class Inspection(val page: Page, val title: String, val artist: String, val cover: String, val candidates: List<String>, val needsWebView: Boolean)

    private val executor = Executors.newFixedThreadPool(concurrency.coerceIn(2, 6))
    private val active = java.util.Collections.synchronizedSet(mutableSetOf<Future<*>>())

    fun inspect(
        generation: Int,
        pages: List<Page>,
        isGenerationCurrent: (Int) -> Boolean,
        onResult: (Inspection) -> Unit,
        onComplete: () -> Unit
    ) {
        if (pages.isEmpty()) return onComplete()
        val remaining = java.util.concurrent.atomic.AtomicInteger(pages.size)
        pages.forEach { page ->
            val future = executor.submit {
                try {
                    if (!isGenerationCurrent(generation)) return@submit
                    val html = fetch(page.url)
                    if (html.isBlank()) return@submit
                    val parsed = MusicPageParser.parse(html, page.url)
                    val candidates = parsed.audioCandidates
                        .filter { ServerConfig.isAllowedMediaUrl(it, page.url) }
                        .distinct()
                        .take(80)
                    if (isGenerationCurrent(generation)) {
                        onResult(Inspection(page, parsed.title.ifBlank { page.titleHint.ifBlank { "Music" } }, parsed.artist.ifBlank { "Unknown Artist" }, parsed.cover, candidates, candidates.isEmpty()))
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) onComplete()
                }
            }
            active += future
        }
    }

    private fun fetch(url: String): String {
        if (!ServerConfig.isAllowedPageUrl(url)) return ""
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 4500
            connection.readTimeout = 6500
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            if (connection.responseCode !in 200..399) return ""
            if (!ServerConfig.isAllowedPageUrl(connection.url.toString())) return ""
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(1_500_000) }
        } catch (_: Exception) { "" } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    fun cancel() {
        synchronized(active) {
            active.forEach { try { it.cancel(true) } catch (_: Exception) { } }
            active.clear()
        }
    }

    fun shutdown() { cancel(); executor.shutdownNow() }
}
