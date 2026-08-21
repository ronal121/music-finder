package com.kafshar.musicfinder

object SearchEngine {
    fun normalizeQuery(input: String): String =
        input.trim().replace(Regex("\\s+"), " ")

    fun buildGoogleQuery(input: String): String =
        ServerConfig.searchQuery(normalizeQuery(input))
}
