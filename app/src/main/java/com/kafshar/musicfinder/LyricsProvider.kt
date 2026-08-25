package com.kafshar.musicfinder

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LyricsProvider {
    fun find(title: String, artist: String): String? {
        return findLrclib(title, artist) ?: findLyricsOvh(title, artist)
    }

    private fun findLrclib(title: String, artist: String): String? = try {
        val url = "https://lrclib.net/api/search?track_name=" +
            URLEncoder.encode(title, "UTF-8") +
            "&artist_name=" + URLEncoder.encode(artist, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 9000
        c.setRequestProperty("User-Agent", "MusicFinder/1.0 (https://github.com/ronal121/music-finder)")
        c.setRequestProperty("Accept", "application/json")
        c.connect()
        if (c.responseCode !in 200..299) { c.disconnect(); return null }
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val results = JSONArray(body)
        var best: String? = null
        var bestScore = -1
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val lyrics = item.optString("plainLyrics").trim()
            if (lyrics.isBlank() || item.optBoolean("instrumental", false)) continue
            val rt = item.optString("trackName")
            val ra = item.optString("artistName")
            var score = 0
            if (rt.equals(title, true)) score += 3
            if (ra.equals(artist, true)) score += 3
            if (rt.contains(title, true) || title.contains(rt, true)) score++
            if (ra.contains(artist, true) || artist.contains(ra, true)) score++
            if (score > bestScore) { bestScore = score; best = lyrics }
        }
        best
    } catch (_: Exception) { null }

    private fun findLyricsOvh(title: String, artist: String): String? = try {
        val url = "https://api.lyrics.ovh/v1/" + URLEncoder.encode(artist, "UTF-8") + "/" + URLEncoder.encode(title, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 7000
        c.connect()
        if (c.responseCode !in 200..299) { c.disconnect(); return null }
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        org.json.JSONObject(body).optString("lyrics").trim().takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
}
