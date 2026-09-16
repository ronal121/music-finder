package com.kafshar.musicfinder

import java.net.URI

data class ParsedMusicPage(val title: String, val artist: String, val cover: String, val audioCandidates: List<String>)

/** Extracts metadata and possible media URLs from static HTML. Runtime WebView may run this repeatedly. */
object MusicPageParser {
    private val mediaKeys = listOf("data-src", "data-url", "data-audio", "data-mp3", "data-file", "data-download", "data-media", "data-stream", "src", "href")
    private val mediaPattern = Regex("(?:\\.mp3|\\.m4a|\\.aac|\\.ogg|\\.opus|\\.wav|\\.flac|\\.webm|/download(?:/|\\?|$)|/dl/|/stream(?:/|\\?|$)|/audio(?:/|\\?|$)|/media(?:/|\\?|$)|[?&](?:type|format|mime)=(?:audio|audio/[^&\\s]+))", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("https?://[^\\s\\\"'<>\\\\]+", RegexOption.IGNORE_CASE)

    fun parse(html: String, pageUrl: String): ParsedMusicPage {
        val title = firstMeta(html, "og:title").ifBlank { firstTagText(html, "h1") }
        val artist = firstMeta(html, "music:musician").ifBlank { firstMeta(html, "author") }
        val cover = firstMeta(html, "og:image")
        val candidates = LinkedHashSet<String>()

        val tag = Regex("<(?:audio|video|source|a)\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (m in tag.findAll(html)) collectAttributes(m.value, pageUrl, candidates)
        for (m in Regex(
            "<script\\b[^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html)) {
            for (u in urlPattern.findAll(unescape(m.groupValues[1]))) {
                val candidate = normalizeUrl(u.value, pageUrl)
                if (candidate != null && mediaPattern.containsMatchIn(candidate)) candidates += candidate
            }
        }
        for (u in urlPattern.findAll(html)) {
            val candidate = normalizeUrl(u.value, pageUrl)
            if (candidate != null && mediaPattern.containsMatchIn(candidate)) candidates += candidate
        }
        return ParsedMusicPage(title.trim().take(300), artist.trim().take(200), cover.trim(), candidates.take(50))
    }

    private fun collectAttributes(tag: String, pageUrl: String, out: MutableSet<String>) {
        val attr = Regex("([a-zA-Z0-9:_-]+)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
        for (m in attr.findAll(tag)) {
            if (m.groupValues[1].lowercase() !in mediaKeys) continue
            val candidate = normalizeUrl(unescape(m.groupValues[2]), pageUrl) ?: continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) out += candidate
        }
    }

    private fun normalizeUrl(raw: String, pageUrl: String): String? = try {
        val clean = raw.trim().replace("\\/", "/").replace("\\u0026", "&")
        URI(pageUrl).resolve(clean).toString().takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    } catch (_: Exception) { null }

    private fun firstMeta(html: String, property: String): String {
        val r = Regex("<meta\\b[^>]*(?:property|name)\\s*=\\s*[\\\"']${Regex.escape(property)}[\\\"'][^>]*content\\s*=\\s*[\\\"']([^\\\"']*)[\\\"']", RegexOption.IGNORE_CASE)
        return r.find(html)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun firstTagText(html: String, tag: String): String = Regex(
        "<$tag\\b[^>]*>(.*?)</$tag>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)?.let { stripHtml(it) }.orEmpty()

    private fun stripHtml(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    private fun unescape(value: String): String = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
}
