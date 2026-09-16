package com.kafshar.musicfinder

/** Pure classification layer used by tests and by future/runtime media probing. */
object MediaUrlValidator {
    enum class Decision { ACCEPT_CANDIDATE, REJECT_YOUTUBE, REJECT_INVALID, REJECT_OBVIOUS_PAGE }

    fun classify(url: String, pageUrl: String? = null): Decision {
        if (!isHttpUrl(url)) return Decision.REJECT_INVALID
        if (ServerConfig.isYouTubeUrl(url)) return Decision.REJECT_YOUTUBE
        if (ServerConfig.isObviousNonMediaUrl(url)) return Decision.REJECT_OBVIOUS_PAGE
        if (pageUrl != null && isHttpUrl(pageUrl)) return Decision.ACCEPT_CANDIDATE
        return if (ServerConfig.looksLikeAudioUrl(url)) Decision.ACCEPT_CANDIDATE else Decision.REJECT_INVALID
    }

    fun isHttpUrl(url: String): Boolean = try {
        val uri = java.net.URI(url.trim())
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }
}
