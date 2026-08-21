package com.kafshar.musicfinder

import android.net.Uri

object ServerConfig {
    const val GOOGLE_HOST = "google.com"

    val MUSIC_SITES = listOf(
        "pro.iraniandj.ir",
        "iranmusic.ir",
        "nicmusic.net",
        "upmusics.com",
        "rozmusic.com",
        "mybia2music.com",
        "musicdel.ir",
        "musics-fa.com"
    )

    val MUSIC_HOSTS = MUSIC_SITES.toSet()

    fun normalizeHost(host: String?): String =
        host.orEmpty().lowercase().removePrefix("www.")

    fun isMusicHost(host: String?): Boolean {
        val normalized = normalizeHost(host)
        return MUSIC_HOSTS.any { normalized == it || normalized.endsWith(".$it") }
    }

    fun isGoogleHost(host: String?): Boolean {
        val normalized = normalizeHost(host)
        return normalized == GOOGLE_HOST || normalized.endsWith(".$GOOGLE_HOST")
    }

    fun isAllowedPageUrl(url: String): Boolean = try {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in setOf("http", "https") &&
                (isGoogleHost(uri.host) || isMusicHost(uri.host))
    } catch (_: Exception) { false }

    fun isAllowedMediaUrl(url: String): Boolean = try {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in setOf("http", "https") && isMusicHost(uri.host)
    } catch (_: Exception) { false }

    private fun expandSearchTerms(input: String): LinkedHashSet<String> {
        val q = input.trim().replace(Regex("\\s+"), " ")
        val aliases = linkedSetOf<String>()
        if (q.isBlank()) return aliases

        aliases += q

        val cleaned = q
            .replace(Regex("[()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isNotBlank()) aliases += cleaned

        // Artist / compilation searches: search the distinctive part too.
        val djMatches = Regex("(?i)(?:compiled\\s+by|mixed\\s+by|by)\\s+([a-z0-9][a-z0-9 ._-]{2,})")
            .findAll(q)
            .map { it.groupValues[1].trim() }
            .toList()
        djMatches.forEach { aliases += it }

        Regex("(?i)\\b(?:dj|mc)[a-z0-9][a-z0-9_-]*\\b")
            .findAll(q)
            .forEach { aliases += it.value }

        if (q.contains("compiled", ignoreCase = true)) {
            aliases += "Compiled By"
            aliases += "Compiled"
        }
        if (q.contains("various artists", ignoreCase = true) || Regex("(?i)\\bVA\\b").containsMatchIn(q)) {
            aliases += "Various Artists"
            aliases += "VA"
        }

        val lower = q.lowercase()
        fun add(vararg values: String) { values.forEach { aliases += it } }

        if (lower.contains("hard techno") || lower.contains("هارد تکنو")) add("Hard Techno", "هارد تکنو", "HardTechno")
        if (lower.contains("techno") || lower.contains("تکنو")) add("Techno", "تکنو")
        if (lower.contains("electronic") || lower.contains("الکترونیک")) add("Electronic", "Electro", "الکترونیک")
        if (lower.contains("tech house") || lower.contains("تک هاوس")) add("Tech House", "TechHouse", "تک هاوس")
        if (lower.contains("house") || lower.contains("هاوس")) add("House", "هاوس")
        if (lower.contains("trance") || lower.contains("ترنس")) add("Trance", "ترنس")
        if (lower.contains("progressive") || lower.contains("پراگرسیو")) add("Progressive", "پراگرسیو")
        if (lower.contains("psy") || lower.contains("سای")) add("Psy", "Psytrance", "Psy Trance", "سای ترنس", "سایترنس")
        if (lower.contains("deep") || lower.contains("دیپ")) add("Deep", "دیپ")
        if (lower.contains("dark") || lower.contains("دارک")) add("Dark", "دارک")

        return aliases
    }

    fun searchQuery(input: String): String {
        val terms = expandSearchTerms(input)
        val expanded = terms.joinToString(" OR ") {
            if (it.contains(' ') || it.contains('(') || it.contains(')')) "\"$it\"" else it
        }
        val sites = MUSIC_SITES.joinToString(" OR ") { "site:$it" }
        return "($expanded) ($sites)"
    }

    fun siteName(url: String): String {
        val host = normalizeHost(runCatching { Uri.parse(url).host }.getOrNull())
        return when {
            host == "pro.iraniandj.ir" || host.endsWith(".pro.iraniandj.ir") -> "IranianDJ Pro"
            host == "rozmusic.com" || host.endsWith(".rozmusic.com") -> "RozMusic"
            host == "mybia2music.com" || host.endsWith(".mybia2music.com") -> "Bia2Music"
            host == "musicdel.ir" || host.endsWith(".musicdel.ir") -> "Musicdel"
            host == "musics-fa.com" || host.endsWith(".musics-fa.com") -> "Musics-FA"
            host == "iranmusic.ir" || host.endsWith(".iranmusic.ir") -> "IranMusic"
            host == "nicmusic.net" || host.endsWith(".nicmusic.net") -> "NicMusic"
            host == "upmusics.com" || host.endsWith(".upmusics.com") -> "UpMusics"
            else -> "سایت موسیقی"
        }
    }
}
