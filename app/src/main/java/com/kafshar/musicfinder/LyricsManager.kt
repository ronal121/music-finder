package com.kafshar.musicfinder

import org.json.JSONArray
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

        val direct = request(
            "https://lrclib.net/api/get?artist_name=${encode(cleanArtist)}&track_name=${encode(cleanTitle)}"
        )
        if (direct.found) return direct

        return search(cleanArtist, cleanTitle)
    }

    private fun search(artist: String, title: String): Result {
        val url =
            "https://lrclib.net/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"

        var connection: HttpURLConnection? = null
        return try {
            connection = open(url)
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

    private fun request(url: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url)
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

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "MusicFinder/1.0 (https://github.com/ronal121/music-finder)"
            )
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun clean(value: String): String =
        value.replace("\\r\\n", "\n").replace("\\r", "\n").trim()
}
