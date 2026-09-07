package com.kafshar.musicfinder

import java.net.URI


data class ParsedMusicPage(
    val title: String,
    val artist: String,
    val cover: String,
    val audioCandidates: List<String>
)

/** Site-agnostic extraction of playable media from music pages. */
object MusicPageParser {
    private val mediaKeys = setOf(
        "data-src", "data-url", "data-audio", "data-mp3", "data-file",
        "data-download", "data-media", "data-stream", "src", "href",
        "file", "url", "audio", "audio_url", "audioUrl", "mp3",
        "mp3_url", "mp3Url", "download", "download_url", "downloadUrl",
        "stream", "stream_url", "streamUrl", "source", "source_url", "sourceUrl",
        "content", "data-content"
    )

    private val audioExtensions = Regex(
        "\\.(?:mp3|m4a|aac|ogg|oga|opus|wav|flac|webm)(?:$|[?#&])",
        RegexOption.IGNORE_CASE
    )

    private val mediaPath = Regex(
        "(?:/download(?:/|\\?|$)|/dl(?:/|\\?|$)|/stream(?:/|\\?|$)|/audio(?:/|\\?|$)|/media(?:/|\\?|$)|" +
            "download\\.(?:php|aspx|asp|jsp)|getfile|mediafile|[?&](?:type|format|mime)=audio)",
        RegexOption.IGNORE_CASE
    )

    private val urlPattern = Regex(
        "https?://[^\\s\\\"'<>\\\\]+|//[^\\s\\\"'<>\\\\]+",
        RegexOption.IGNORE_CASE
    )

    fun parse(html: String, pageUrl: String): ParsedMusicPage {
        val normalizedHtml = unescape(html)
        val title = firstMeta(normalizedHtml, "og:title").ifBlank { firstMeta(normalizedHtml, "twitter:title") }
            .ifBlank { firstTagText(normalizedHtml, "h1") }
        val artist = firstMeta(normalizedHtml, "music:musician").ifBlank { firstMeta(normalizedHtml, "author") }
            .ifBlank { firstMeta(normalizedHtml, "twitter:creator") }
        val cover = firstMeta(normalizedHtml, "og:image").ifBlank { firstMeta(normalizedHtml, "twitter:image") }
        val candidates = LinkedHashSet<String>()

        // Standard HTML5 media plus preload/link hints.
        val mediaTag = Regex(
            "<(?:audio|video|source|a|link)\\b[^>]*>",
            RegexOption.IGNORE_CASE
        )
        for (m in mediaTag.findAll(normalizedHtml)) {
            collectAttributes(m.value, pageUrl, candidates)
        }

        // OpenGraph/Twitter audio metadata and other common meta names.
        val meta = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (m in meta.findAll(normalizedHtml)) {
            val tag = m.value
            val key = attrValue(tag, "property").ifBlank { attrValue(tag, "name") }.lowercase()
            val value = attrValue(tag, "content")
            if (value.isBlank()) continue
            if (key.contains("audio") || key.contains("stream") || key.contains("media") ||
                key.contains("mp3") || key.contains("download")) {
                normalizeUrl(value, pageUrl)?.let { if (!ServerConfig.isObviousNonMediaUrl(it)) candidates += it }
            }
        }

        // Absolute and protocol-relative media-looking URLs anywhere in HTML/JS.
        for (u in urlPattern.findAll(normalizedHtml)) {
            val candidate = normalizeUrl(u.value, pageUrl) ?: continue
            if (looksLikeMedia(candidate)) candidates += candidate
        }

        // JSON/player configuration. Do not require an audio extension: many CDNs use
        // extensionless endpoints and the caller validates the response MIME type.
        val jsonValue = Regex(
            "[\\\"']([A-Za-z0-9:_-]*(?:audio|mp3|stream|download|media|source|file|url)[A-Za-z0-9:_-]*)[\\\"']\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        )
        for (m in jsonValue.findAll(normalizedHtml)) {
            val candidate = normalizeUrl(m.groupValues[2], pageUrl) ?: continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) candidates += candidate
        }

        // Player URLs may be encoded as JSON unicode/hex strings or surrounded by a
        // media keyword. Decode first, then use a wider contextual pass.
        val decoded = decodeJsEscapes(normalizedHtml)
        for (u in urlPattern.findAll(decoded)) {
            val start = maxOf(0, u.range.first - 220)
            val end = minOf(decoded.length, u.range.last + 221)
            val context = decoded.substring(start, end)
            if (!Regex("(?:audio|mp3|stream|download|media|player|source|file|playlist|sound)", RegexOption.IGNORE_CASE)
                    .containsMatchIn(context)) continue
            val candidate = normalizeUrl(u.value, pageUrl) ?: continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) candidates += candidate
        }

        return ParsedMusicPage(
            title.trim().take(300),
            artist.trim().take(200),
            cover.trim(),
            candidates.take(80)
        )
    }

    private fun collectAttributes(tag: String, pageUrl: String, out: MutableSet<String>) {
        val attr = Regex(
            "([a-zA-Z0-9:_-]+)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        )
        val tagName = Regex("^<([a-zA-Z0-9]+)").find(tag)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
        for (m in attr.findAll(tag)) {
            val key = m.groupValues[1].lowercase()
            if (key !in mediaKeys.map { it.lowercase() }.toSet()) continue
            val value = m.groupValues[2]
            val candidate = normalizeUrl(value, pageUrl) ?: continue
            // href on ordinary navigation links is not a media candidate unless the
            // URL itself looks like media or the element is an actual media element.
            if (key == "href" && tagName == "a" && !looksLikeMedia(candidate)) continue
            if (!ServerConfig.isObviousNonMediaUrl(candidate)) out += candidate
        }
    }

    private fun attrValue(tag: String, name: String): String = Regex(
        "\\b${Regex.escape(name)}\\s*=\\s*[\\\"']([^\\\"']*)[\\\"']",
        RegexOption.IGNORE_CASE
    ).find(tag)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    private fun normalizeUrl(raw: String, pageUrl: String): String? = try {
        val clean = decodeJsEscapes(raw.trim())
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
        URI(pageUrl).resolve(clean).toString().takeIf {
            it.startsWith("http://", true) || it.startsWith("https://", true)
        }
    } catch (_: Exception) {
        null
    }

    private fun looksLikeMedia(url: String): Boolean =
        audioExtensions.containsMatchIn(url) || mediaPath.containsMatchIn(url)

    private fun firstMeta(html: String, property: String): String {
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
        value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun decodeJsEscapes(value: String): String {
        var result = value
        result = result.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        result = result.replace(Regex("\\\\x([0-9a-fA-F]{2})")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return result
    }

    private fun unescape(value: String): String = decodeJsEscapes(value)
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\/", "/")
}
