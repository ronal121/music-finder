package com.kafshar.musicfinder

object SearchEngine {

    fun normalizeQuery(input: String): String {
        return input
            .trim()
            .replace('\u200c', ' ')
            .replace('\u200f', ' ')
            .replace('\u200e', ' ')
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun buildGoogleQuery(input: String): String {
        val query = normalizeQuery(input)
        return ServerConfig.searchQuery(query)
    }
}
