package com.kafshar.musicfinder

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LyricsManager {

    data class Result(
        val found: Boolean,
        val lyrics: String = ""
    )

    fun fetch(artist: String, title: String): Result {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return Result(false)

        // First use a dedicated lyrics service when it has an exact match.
        val direct = request(
            "https://lrclib.net/api/get?artist_name=${encode(cleanArtist)}&track_name=${encode(cleanTitle)}"
        )
        if (direct.found) return direct

        val searchResult = search(cleanArtist, cleanTitle)
        if (searchResult.found) return searchResult

        // Iranian music sites often keep the lyrics only in the song page itself.
        // Search the already trusted Music Finder sources and extract the lyrics
        // from the returned HTML page as a fallback.
        return fetchFromMusicSites(cleanArtist, cleanTitle)
    }

    private fun search(artist: String, title: String): Result {
        val url =
            "https://lrclib.net/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"

        var connection: HttpURLConnection? = null
        return try {
            connection = open(url, "application/json")
            if (connection.responseCode !in 200..299) return Result(false)

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val itemArtist = item.optString("artistName").trim()
                val itemTitle = item.optString("trackName").trim()
                val plain = item.optString("plainLyrics").trim()
                val synced = item.optString("syncedLyrics").trim()

                val artistMatch =
                    itemArtist.equals(artist, true) ||
                            itemArtist.contains(artist, true) ||
                            artist.contains(itemArtist, true)

                val titleMatch =
                    itemTitle.equals(title, true) ||
                            itemTitle.contains(title, true) ||
                            title.contains(itemTitle, true)

                if (artistMatch && titleMatch) {
                    val lyrics = if (plain.isNotBlank()) plain else synced
                    if (lyrics.isNotBlank()) return Result(true, clean(lyrics))
                }
            }

            Result(false)
        } catch (_: Exception) {
            Result(false)
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun fetchFromMusicSites(artist: String, title: String): Result {
        val query = URLEncoder.encode(
            "\"$artist $title\" (${ServerConfig.MUSIC_SITES.joinToString(" OR ") { "site:$it" }})",
            StandardCharsets.UTF_8.toString()
        )
        val searchUrl = "https://www.google.com/search?q=$query&num=8"

        return try {
            val html = getText(searchUrl, "text/html") ?: return Result(false)
            val links = extractGoogleLinks(html)
                .filter { ServerConfig.isAllowedPageUrl(it) }
                .distinct()
                .take(8)

            for (pageUrl in links) {
                val pageHtml = getText(pageUrl, "text/html") ?: continue
                val lyrics = extractLyrics(pageHtml, artist, title)
                if (lyrics.isNotBlank()) return Result(true, lyrics)
            }

            Result(false)
        } catch (_: Exception) {
            Result(false)
        }
    }

    private fun extractGoogleLinks(html: String): List<String> {
        val result = LinkedHashSet<String>()
        val regex = Regex("""<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>""", RegexOption.IGNORE_CASE)

        for (match in regex.findAll(html)) {
            var href = match.groupValues[1]
                .replace("&amp;", "&")
                .trim()

            if (href.startsWith("/url?")) {
                val query = href.substringAfter('?', "")
                val encoded = query.split('&')
                    .firstOrNull { it.startsWith("q=") }
                    ?.substringAfter('=')
                href = encoded?.let {
                    try { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) } catch (_: Exception) { it }
                }.orEmpty()
            }

            if (href.startsWith("http://") || href.startsWith("https://")) {
                if (ServerConfig.isAllowedPageUrl(href)) result.add(href)
            }
        }
        return result.toList()
    }

    private fun extractLyrics(html: String, artist: String, title: String): String {
        val normalizedHtml = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")

        val candidates = mutableListOf<String>()
        val blockRegex = Regex(
            """(?is)<(div|section|article|p)[^>]*(?:id|class)\\s*=\\s*[\"'][^\"']*(?:lyrics?|songtext|lyric-text|matn|tarane|ترانه|متن)[^\"']*[\"'][^>]*>(.*?)</\\1>"""
        )
        for (match in blockRegex.findAll(normalizedHtml)) {
            val text = htmlToText(match.groupValues[2])
            if (isGoodCandidate(text)) candidates.add(text)
        }

        val broadRegex = Regex(
            """(?is)<(div|section|article)[^>]*>(.*?)</\\1>"""
        )
        for (match in broadRegex.findAll(normalizedHtml)) {
            val text = htmlToText(match.groupValues[2])
            val lower = text.lowercase()
            if ((lower.contains("متن آهنگ") || lower.contains("متن ترانه") || lower.contains("lyrics")) && isGoodCandidate(text)) {
                candidates.add(text)
            }
        }

        val best = candidates
            .distinct()
            .maxByOrNull { scoreCandidate(it, artist, title) }
            ?: return ""

        return cleanExtractedLyrics(best)
    }

    private fun scoreCandidate(text: String, artist: String, title: String): Int {
        val lower = text.lowercase()
        var score = text.length.coerceAtMost(5000) / 100
        if (lower.contains("متن آهنگ")) score += 50
        if (lower.contains("متن ترانه")) score += 45
        if (lower.contains("lyrics")) score += 35
        if (lower.contains(artist.lowercase())) score += 20
        if (lower.contains(title.lowercase())) score += 20
        if (text.count { it == '\n' } >= 3) score += 15
        return score
    }

    private fun isGoodCandidate(text: String): Boolean =
        text.length >= 80 &&
                text.length <= 30000 &&
                text.count { it == '\n' } >= 2

    private fun cleanExtractedLyrics(value: String): String {
        val lines = value
            .replace('\u00A0', ' ')
            .replace("\\r\\n", "\n")
            .replace('\\r', '\n')
            .lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val lower = line.lowercase()
                lower.contains("دانلود آهنگ") ||
                        lower.contains("download") ||
                        lower.contains("اشتراک") ||
                        lower.contains("تبلیغ")
            }

        return lines.joinToString("\n").trim()
    }

    private fun htmlToText(html: String): String =
        html
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</(p|div|section|article|li|h[1-6])>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    private fun request(url: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url, "application/json")
            if (connection.responseCode !in 200..299) return Result(false)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val plain = json.optString("plainLyrics", "").trim()
            val synced = json.optString("syncedLyrics", "").trim()
            val lyrics = if (plain.isNotBlank()) plain else synced
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
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
            )
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun clean(value: String): String =
        value.replace("\\r\\n", "\n").replace("\\r", "\n").trim()
}
