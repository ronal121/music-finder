package com.kafshar.musicfinder

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LyricsManager {
    data class Result(val found: Boolean, val lyrics: String = "")

    fun fetch(artist: String, title: String): Result {
        val a = normalize(artist)
        val t = normalize(title)
        if (a.isBlank() || t.isBlank()) return Result(false)

        // 1) Fast public lyrics database. This is only a fallback; site text is preferred below.
        request("https://lrclib.net/api/get?artist_name=${encode(a)}&track_name=${encode(t)}").let {
            if (it.found) return it
        }
        searchLrcLib(a, t).let {
            if (it.found) return it
        }

        // 2) Search the music sites already registered in ServerConfig and extract
        // the actual lyrics block from the returned HTML.
        fetchFromMusicSites(a, t).let {
            if (it.found) return it
        }

        return Result(false)
    }

    private fun searchLrcLib(artist: String, title: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            val url = "https://lrclib.net/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
            connection = open(url, "application/json")
            if (connection.responseCode !in 200..299) return Result(false)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            var best: Result? = null
            var bestScore = 0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val ia = normalize(item.optString("artistName"))
                val it = normalize(item.optString("trackName"))
                val lyrics = item.optString("plainLyrics").ifBlank { item.optString("syncedLyrics") }
                if (lyrics.isBlank()) continue
                val score = matchScore(artist, title, ia, it)
                if (score > bestScore) {
                    bestScore = score
                    best = Result(true, clean(lyrics))
                }
            }
            if (bestScore >= 45) best ?: Result(false) else Result(false)
        } catch (_: Exception) {
            Result(false)
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun fetchFromMusicSites(artist: String, title: String): Result {
        val sites = ServerConfig.MUSIC_SITES.take(18)
        val siteQuery = sites.joinToString(" OR ") { "site:$it" }
        val query = URLEncoder.encode(
            "\"$title\" \"$artist\" ($siteQuery)",
            StandardCharsets.UTF_8.toString()
        )

        val searchUrls = listOf(
            "https://www.google.com/search?q=$query&num=20",
            "https://html.duckduckgo.com/html/?q=$query"
        )

        for (searchUrl in searchUrls) {
            val html = getText(searchUrl, "text/html") ?: continue
            val links = extractSearchLinks(html)
                .filter { ServerConfig.isAllowedPageUrl(it) }
                .distinct()
                .take(12)

            for (pageUrl in links) {
                val pageHtml = getText(pageUrl, "text/html") ?: continue
                val lyrics = extractLyrics(pageHtml, artist, title)
                if (lyrics.isNotBlank()) return Result(true, lyrics)
            }
        }
        return Result(false)
    }

    private fun extractSearchLinks(html: String): List<String> {
        val result = LinkedHashSet<String>()
        val regex = Regex("""<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>""", RegexOption.IGNORE_CASE)
        for (m in regex.findAll(html)) {
            var href = decodeHtml(m.groupValues[1]).trim()
            if (href.startsWith("/url?")) {
                val q = href.substringAfter('?', "")
                    .split('&')
                    .firstOrNull { it.startsWith("q=") }
                    ?.substringAfter('=')
                href = q?.let { decodeUrl(it) }.orEmpty()
            }
            if (href.startsWith("http://") || href.startsWith("https://")) {
                if (ServerConfig.isAllowedPageUrl(href)) result.add(href)
            }
        }
        return result.toList()
    }

    private fun extractLyrics(html: String, artist: String, title: String): String {
        val source = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<noscript[^>]*>.*?</noscript>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")

        val candidates = ArrayList<String>()

        // Explicit lyric containers. Do not require the whole page to match one nested div.
        val explicit = Regex(
            """(?is)<[^>]+(?:id|class)\s*=\s*[\"'][^\"']*(?:lyrics?|lyric-text|songtext|song-text|matn|tarane|ترانه|متن)[^\"']*[\"'][^>]*>(.*?)</[^>]+>"""
        )
        for (m in explicit.findAll(source)) {
            val text = htmlToText(m.groupValues[1])
            if (isCandidate(text)) candidates.add(text)
        }

        // JSON-LD / metadata can contain lyrics on some modern pages.
        val jsonLyrics = Regex("""(?is)[\"'](?:lyrics|lyric)[\"']\s*:\s*[\"'](.*?)[\"']""")
        for (m in jsonLyrics.findAll(source)) {
            val text = decodeJsonText(m.groupValues[1])
            if (isCandidate(text)) candidates.add(text)
        }

        // Fall back to meaningful text blocks. This catches sites whose lyrics
        // container uses generic classes.
        val blocks = Regex("""(?is)<(?:div|section|article|main|p)[^>]*>(.*?)</(?:div|section|article|main|p)>""")
        for (m in blocks.findAll(source)) {
            val text = htmlToText(m.groupValues[1])
            val lower = text.lowercase()
            if (isCandidate(text) && (
                lower.contains("متن آهنگ") ||
                lower.contains("متن ترانه") ||
                lower.contains("lyrics") ||
                lower.contains(title.lowercase()) ||
                lower.contains(artist.lowercase())
            )) candidates.add(text)
        }

        return candidates
            .distinct()
            .map { cleanExtractedLyrics(it) }
            .filter { it.length >= 80 }
            .maxByOrNull { scoreCandidate(it, artist, title) }
            ?: ""
    }

    private fun scoreCandidate(text: String, artist: String, title: String): Int {
        val lower = text.lowercase()
        var score = (text.length / 120).coerceAtMost(35)
        if (lower.contains("متن آهنگ")) score += 60
        if (lower.contains("متن ترانه")) score += 55
        if (lower.contains("lyrics")) score += 40
        if (lower.contains(normalize(artist).lowercase())) score += 30
        if (lower.contains(normalize(title).lowercase())) score += 30
        score += (text.count { it == '\n' } * 2).coerceAtMost(30)
        return score
    }

    private fun isCandidate(text: String): Boolean =
        text.length in 80..40000 && text.count { it == '\n' } >= 2

    private fun cleanExtractedLyrics(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .lines()
            .map { it.replace(Regex("[ \t]+"), " ").trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val l = line.lowercase()
                l.contains("دانلود آهنگ") ||
                    l.contains("download music") ||
                    l == "download" ||
                    l.contains("اشتراک") ||
                    l.contains("عضویت در")
            }
            .joinToString("\n")
            .trim()
    }

    private fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<br\\s*/?>"), "\n")
        .replace(Regex("(?is)</(?:p|div|section|article|main|li|h[1-6])>"), "\n")
        .replace(Regex("(?is)<[^>]+>"), " ")
        .let(::decodeHtml)
        .replace('\u00A0', ' ')
        .trim()

    private fun request(url: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url, "application/json")
            if (connection.responseCode !in 200..299) return Result(false)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val lyrics = json.optString("plainLyrics").ifBlank { json.optString("syncedLyrics") }
            if (lyrics.isBlank()) Result(false) else Result(true, clean(lyrics))
        } catch (_: Exception) {
            Result(false)
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun getText(url: String, accept: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url, accept)
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 9000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
            )
        }

    private fun matchScore(a: String, t: String, ia: String, it: String): Int {
        var score = 0
        if (ia == a) score += 50 else if (ia.contains(a) || a.contains(ia)) score += 30
        if (it == t) score += 50 else if (it.contains(t) || t.contains(it)) score += 30
        return score
    }

    private fun normalize(value: String): String = value
        .replace(Regex("[|•·–—]"), " ")
        .replace(Regex("(?i)دانلود|download|آهنگ|song|music"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeHtml(value: String): String = value
        .replace("&nbsp;", " ", true)
        .replace("&amp;", "&", true)
        .replace("&quot;", "\"", true)
        .replace("&#39;", "'", true)
        .replace("&lt;", "<", true)
        .replace("&gt;", ">", true)

    private fun decodeUrl(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
    } catch (_: Exception) { value }

    private fun decodeJsonText(value: String): String =
        value.replace("\\/", "/").replace("\\\"", "\"").replace("\\n", "\n")

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun clean(value: String): String =
        value.replace("\\r\\n", "\n").replace("\\r", "\n").trim()
}
