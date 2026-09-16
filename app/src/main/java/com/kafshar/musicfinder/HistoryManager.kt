package com.kafshar.musicfinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryManager {
    private const val PREF = "music_finder_history"
    private const val KEY = "songs"
    private const val MAX_ITEMS = 100

    @Synchronized
    fun get(context: Context): MutableList<SongResult> {
        val result = mutableListOf<SongResult>()
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
                val o = array.optJSONObject(i) ?: continue
                val url = o.optString("url").trim()
                if (url.isBlank()) continue
                result += SongResult(url, o.optString("title"), o.optString("artist"), o.optString("site"), o.optString("cover"))
            }
        } catch (_: Exception) { }
        return result
    }

    @Synchronized
    fun add(context: Context, song: SongResult) {
        if (song.url.isBlank()) return
        val list = get(context)
        list.removeAll { it.url == song.url }
        list.add(0, song)
        save(context, list.take(MAX_ITEMS))
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    private fun save(context: Context, songs: List<SongResult>) {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(JSONObject().apply {
                put("url", song.url)
                put("title", song.title)
                put("artist", song.artist)
                put("site", song.site)
                put("cover", song.cover)
            })
        }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
