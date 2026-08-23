package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Parallel, non-WebView search coordinator.
 * It fans a query out to Google and every configured music domain at the same
 * time, then normalizes HTML pages into SongResult candidates.
 */
object ParallelSearchEngine {
    private val executor = Executors.newFixedThreadPool(12)

    data class Candidate(
        val url: String,
        val title: String,
        val artist: String,
        val site: String,
        val cover: String = "",
        val score: Int = 0
    )

    fun search(query: String, generation: Int, callback: (Int, List<Candidate>) -> Unit) {
        val q = SearchEngine.correctedQuery(query).trim()
        if (q.isBlank()) {
            callback(generation, emptyList())
            return
        }

        val tasks = ArrayList<Callable<List<Candidate>>>()
        tasks += Callable { searchGoogle(q) }
        ServerConfig.MUSIC_SITES.forEach { domain ->
            tasks += Callable { searchSite(domain, q) }
        }

        Thread {
            val merged = LinkedHashMap<String, Candidate>()
            try {
                val futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS)
                futures.forEach { future ->
                    try {
                        future.get().forEach { candidate ->
                            val key = canonical(candidate.url)
                            val old = merged[key]
                            if (old == null || candidate.score > old.score) merged[key] = candidate
                        }
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }

            callback(generation, merged.values.sortedByDescending { it.score }.take(60))
        }.start()
    }

    private fun searchGoogle(q: String): List<Candidate> {
        val url = "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8") + "&num=30"
        val html = get(url) ?: return emptyList()
        return extractLinks(html, "Google", q, true)
    }

    private fun searchSite(domain: String, q: String): List<Candidate> {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val candidates = listOf(
            "https://$domain/?s=$encoded",
            "https://$domain/search?q=$encoded",
            "https://$domain/?q=$encoded"
        )
        for (url in candidates) {
            val html = get(url) ?: continue
            val results = extractLinks(html, domain, q, false)
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    private fun get(url: String): String? {
        return try {
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 4500
            c.readTimeout = 6500
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            c.connect()
            if (c.responseCode !in 200..399) return null
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(900_000) }
        } catch (_: Exception) { null }
    }

    private fun extractLinks(html: String, site: String, q: String, google: Boolean): List<Candidate> {
        val out = ArrayList<Candidate>()
        val queryTokens = normalize(q).split(' ').filter { it.length > 1 }
        val regex = Regex("(?is)<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
        for (m in regex.findAll(html).take(500)) {
            val href = android.text.Html.fromHtml(m.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            val text = android.text.Html.fromHtml(m.groupValues[2], android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                .replace(Regex("\\s+"), " ").trim()
            val absolute = absoluteUrl(href, site) ?: continue
            if (!isUsefulUrl(absolute) || text.length < 3) continue
            val lower = normalize(text + " " + absolute)
            val hits = queryTokens.count { lower.contains(it) }
            if (hits == 0) continue
            val score = hits * 20 + if (lower.contains("lyrics") || lower.contains("ترانه") || lower.contains("متن")) 8 else 0
            out += Candidate(absolute, cleanTitle(text), "", if (google) ServerConfig.siteName(absolute) else site, "", score)
        }
        return out.distinctBy { canonical(it.url) }.take(20)
    }

    private fun cleanTitle(s: String): String = s.replace(Regex("\\s+"), " ").trim().take(180)

    private fun normalize(s: String): String = s.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun canonical(url: String): String = url.substringBefore('#').trimEnd('/').lowercase()

    private fun absoluteUrl(href: String, site: String): String? {
        return try {
            when {
                href.startsWith("http://") || href.startsWith("https://") -> href
                href.startsWith("//") -> "https:$href"
                href.startsWith("/") -> "https://$site$href"
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun isUsefulUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("javascript:") || lower.contains("mailto:") || lower.contains("google.com/search")) return false
        return ServerConfig.isAllowedPageUrl(url)
    }
}
