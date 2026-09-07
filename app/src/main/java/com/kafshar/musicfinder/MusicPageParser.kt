package com.kafshar.musicfinder

import java.net.URI


data class ParsedMusicPage(
    val title: String,
    val artist: String,
    val cover: String,
    val audioCandidates: List<String>
)

/**
 * Extracts playable media from real-world music pages.
 *
 * Music sites frequently do not expose an <audio src="..."> element. They put the
 * media URL in JSON/JS player configuration, data-* attributes, OpenGraph metadata,
 * or escaped strings. This parser deliberately does not depend on a fixed site list.
 */
object MusicPageParser {
    private val mediaKeys = setOf(
        "data-src", "data-url", "data-audio", "data-mp3", "data-file",
        "data-download", "data-media", "data-stream", "src", "href",
        "file", "url", "audio", "audio_url", "audioUrl", "mp3",
        "mp3_url", "mp3Url", "download", "download_url", "downloadUrl",
        "stream", "stream_url", "streamUrl", "source", "source_url", "sourceUrl"
    )

    private val audioExtensions = Regex(
        "\\.(?:mp3|m4a|aac|ogg|opus|wav|flac|webm)(?:$|[?#&])",
        RegexOption.IGNORE_CASE
    )

    private val mediaPath = Regex(
        "(?:/download(?:/|\\?|$)|/dl/|/stream(?:/|\\?|$)|/audio(?:/|\\?|$)|/media(?:/|\\?|$)|" +
            "download\\.(?:php|aspx|asp|jsp)|getfile|mediafile|[?&](?:type|format|mime)=audio)",
        RegexOption.IGNORE_CASE
    )

    private val urlPattern = Regex(
        "https?://[^\\s\\\"'<>\\\\]+",
        RegexOption.IGNORE_CASE
    )

    fun parse(html: String, pageUrl: String): ParsedMusicPage {
        val title = firstMeta(html, "og:title").ifBlank { firstMeta(html, "twitter:title") }
            .ifBlank { firstTagText(html, "h1") }
        val artist = firstMeta(html, "music:musician").ifBlank { firstMeta(html, "author") }
            .ifBlank { firstMeta(html, "twitter:creator") }
        val cover = firstMeta(html, "og:image").ifBlank { firstMeta(html, "twitter:image") }
        val candidates = LinkedHashSet<String>()

        // Standard media/source elements.
        val mediaTag = Regex(
            "<(?:audio|video|source|a)\\b[^>]*>",
            RegexOption.IGNORE_CASE
        )
        for (m in mediaTag.findAll(html)) {
            collectAttributes(m.value, pageUrl, candidates)
        }

        // Any absolute media-looking URL in the document or script blocks.
        val escapedHtml = unescape(html)
        for (u in urlPattern.findAll(escapedHtml)) {
            val candidate = normalizeUrl(u.value, pageUrl) ?: continue
            if (looksLikeMedia(candidate)) candidates += candidate
        }

        // Player configuration is commonly JSON such as {"file":"..."},
        // {"audioUrl":"..."}, {"download_url":"..."}, etc. Accept the URL even
        // when it is extensionless; the caller will perform an HTTP media probe.
        val jsonValue = Regex(
            "[\\\"']([A-Za-z0-9_-]*(?:audio|mp3|stream|download|media|source|file|url)[A-Za-z0-9_-]*)[\\\"']\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        )
        for (m in jsonValue.findAll(escapedHtml)) {
            val key = m.groupValues[1]
            if (!isMediaKey(key)) continue
            val candidate = normalizeUrl(m.groupValues[2], pageUrl) ?: continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) candidates += candidate
        }

        // JS often stores an encoded/escaped URL in a player object. Look for a
        // media keyword near each URL instead of requiring an .mp3 suffix.
        for (m in urlPattern.findAll(escapedHtml)) {
            val start = maxOf(0, m.range.first - 180)
            val end = minOf(escapedHtml.length, m.range.last + 181)
            val context = escapedHtml.substring(start, end)
            if (!Regex("(?:audio|mp3|stream|download|media|player|source|file)", RegexOption.IGNORE_CASE)
                    .containsMatchIn(context)) continue
            val candidate = normalizeUrl(m.value, pageUrl) ?: continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) candidates += candidate
        }

        return ParsedMusicPage(
            title.trim().take(300),
            artist.trim().take(200),
            cover.trim(),
            candidates.take(80)
        )
    }

    private fun isMediaKey(key: String): Boolean {
        val k = key.lowercase()
        return mediaKeys.any { it.lowercase() == k } ||
            k.contains("audio") || k.contains("mp3") || k.contains("stream") ||
            k.contains("download") || k.contains("media") || k.contains("source") ||
            k == "file" || k == "url"
    }

    private fun looksLikeMedia(url: String): Boolean =
        audioExtensions.containsMatchIn(url) || mediaPath.containsMatchIn(url)

    private fun collectAttributes(tag: String, pageUrl: String, out: MutableSet<String>) {
        val attr = Regex(
            "([a-zA-Z0-9:_-]+)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        )
        for (m in attr.findAll(tag)) {
            val key = m.groupValues[1].lowercase()
            if (key !in mediaKeys.map { it.lowercase() }.toSet()) continue
            val candidate = normalizeUrl(unescape(m.groupValues[2]), pageUrl) ?: continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) out += candidate
        }
    }

    private fun normalizeUrl(raw: String, pageUrl: String): String? = try {
        val clean = raw.trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003F", "?")
            .replace("\\u003D", "=")
        URI(pageUrl).resolve(clean).toString().takeIf {
            it.startsWith("http://", true) || it.startsWith("https://", true)
        }
    } catch (_: Exception) {
        null
    }

    private fun firstMeta(html: String, property: String): String {
        // Support both property/name-before-content and content-before-property.
        val escaped = Regex.escape(property)
        val a = Regex(
            "<meta\\b[^>]*(?:property|name)\\s*=\\s*[\\\"']$escaped[\\\"'][^>]*content\\s*=\\s*[\\\"']([^\\\"']*)[\\\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!a.isNullOrBlank()) return unescape(a)

        return Regex(
            "<meta\\b[^>]*content\\s*=\\s*[\\\"']([^\\\"']*)[\\\"'][^>]*(?:property|name)\\s*=\\s*[\\\"']$escaped[\\\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.let(::unescape).orEmpty()
    }

    private fun firstTagText(html: String, tag: String): String = Regex(
        "<$tag\\b[^>]*>(.*?)</$tag>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)?.let { stripHtml(it) }.orEmpty()

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\/", "/")
        .replace("\\u0026", "&")
        .replace("\\u003F", "?")
        .replace("\\u003D", "=")
}
