package com.kafshar.musicfinder

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

        var connection: HttpURLConnection? = null
        return try {
            val a = URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.toString())
            val t = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.toString())
            val url = URL("https://api.lyrics.ovh/v1/$a/$t")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MusicFinder/1.0")

            if (connection.responseCode !in 200..299) return Result(false)

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val lyrics = JSONObject(body).optString("lyrics", "").trim()
            if (lyrics.isBlank()) Result(false) else Result(true, lyrics)
        } catch (_: Exception) {
            Result(false)
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }
}
