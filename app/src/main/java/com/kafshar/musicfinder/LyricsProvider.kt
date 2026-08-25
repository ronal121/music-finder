package com.kafshar.musicfinder

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LyricsProvider {

    fun find(title: String, artist: String): String? {
        return findLrclib(title, artist) ?: findLyricsOvh(title, artist)
    }

    private fun findLrclib(title: String, artist: String): String? {
        return try {
            val url = "https://lrclib.net/api/search?track_name=" +
                URLEncoder.encode(title, "UTF-8") +
                "&artist_name=" + URLEncoder.encode(artist, "UTF-8")

            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 7000
                connection.readTimeout = 9000
                connection.setRequestProperty(
                    "User-Agent",
                    "MusicFinder/1.0 (https://github.com/ronal121/music-finder)"
                )
                connection.setRequestProperty("Accept", "application/json")
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    null
                } else {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val results = JSONArray(body)
                    var best: String? = null
                    var bestScore = -1

                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val lyrics = item.optString("plainLyrics").trim()

                        if (lyrics.isBlank() || item.optBoolean("instrumental", false)) {
                            continue
                        }

                        val resultTitle = item.optString("trackName").trim()
                        val resultArtist = item.optString("artistName").trim()
                        var score = 0

                        if (resultTitle.equals(title.trim(), true)) score += 3
                        if (resultArtist.equals(artist.trim(), true)) score += 3
                        if (resultTitle.contains(title.trim(), true) ||
                            title.trim().contains(resultTitle, true)
                        ) score++
                        if (resultArtist.contains(artist.trim(), true) ||
                            artist.trim().contains(resultArtist, true)
                        ) score++

                        if (score > bestScore) {
                            bestScore = score
                            best = lyrics
                        }
                    }

                    best
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findLyricsOvh(title: String, artist: String): String? {
        return try {
            val url = "https://api.lyrics.ovh/v1/" +
                URLEncoder.encode(artist, "UTF-8") + "/" +
                URLEncoder.encode(title, "UTF-8")

            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 7000
                connection.setRequestProperty("Accept", "application/json")
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    null
                } else {
                    JSONObject(
                        connection.inputStream.bufferedReader().use { it.readText() }
                    ).optString("lyrics").trim().takeIf { it.isNotBlank() }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
