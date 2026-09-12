package com.kafshar.musicfinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SongResultStore {
    private const val PREFS = "search_results"
    private const val KEY = "songs_json"

    fun save(context: Context, songs: List<SongResult>) {
        val array = JSONArray()
        songs.take(200).forEach { song ->
            array.put(JSONObject().apply {
                put("url", song.url)
                put("title", song.title)
                put("artist", song.artist)
                put("site", song.site)
                put("cover", song.cover)
                put("isYouTube", song.isYouTube)
                put("referer", song.referer)
                put("timestamp", System.currentTimeMillis())
                put("playable", !song.isYouTube && song.url.isNotBlank())
                put("sourceType", if (song.isYouTube) "YOUTUBE" else "DIRECT_AUDIO")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun restore(context: Context): List<SongResult> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                return buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val url = item.optString("url")
                        if (url.isBlank()) continue
                        add(SongResult(url, item.optString("title"), item.optString("artist"), item.optString("site"), item.optString("cover"), item.optBoolean("isYouTube", false), item.optString("referer")))
                    }
                }
            } catch (_: Exception) { }
        }
        return restoreLegacy(context)
    }

    private fun restoreLegacy(context: Context): List<SongResult> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("songs", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val p = line.split("|||", limit = 5)
            if (p.size != 5 || p[0].isBlank()) null else SongResult(p[0], p[1], p[2], p[3], p[4])
        }.take(200)
    }
}
