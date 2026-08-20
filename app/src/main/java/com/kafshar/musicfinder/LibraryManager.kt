package com.kafshar.musicfinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LibraryManager {

    private const val PREF = "music_finder_library"
    private const val KEY = "songs"

    fun getSongs(context: Context): MutableList<SongResult> {

        val prefs = context.getSharedPreferences(
            PREF,
            Context.MODE_PRIVATE
        )

        val raw = prefs.getString(KEY, "[]") ?: "[]"

        val result = mutableListOf<SongResult>()

        try {

            val array = JSONArray(raw)

            for (i in 0 until array.length()) {

                val obj = array.getJSONObject(i)

                result.add(
                    SongResult(
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        site = obj.optString("site"),
                        cover = obj.optString("cover")
                    )
                )
            }

        } catch (_: Exception) {
        }

        return result
    }

    fun saveSong(
        context: Context,
        song: SongResult
    ) {

        val songs = getSongs(context)

        if (
            songs.any {
                it.url == song.url
            }
        ) {
            return
        }

        songs.add(song)

        saveSongs(
            context,
            songs
        )
    }

    fun removeSong(
        context: Context,
        song: SongResult
    ) {

        val songs = getSongs(context)

        songs.removeAll {
            it.url == song.url
        }

        saveSongs(
            context,
            songs
        )
    }

    fun contains(
        context: Context,
        song: SongResult
    ): Boolean {

        return getSongs(context).any {
            it.url == song.url
        }
    }

    private fun saveSongs(
        context: Context,
        songs: List<SongResult>
    ) {

        val array = JSONArray()

        songs.forEach { song ->

            val obj = JSONObject()

            obj.put(
                "url",
                song.url
            )

            obj.put(
                "title",
                song.title
            )

            obj.put(
                "artist",
                song.artist
            )

            obj.put(
                "site",
                song.site
            )

            obj.put(
                "cover",
                song.cover
            )

            array.put(obj)
        }

        context
            .getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY,
                array.toString()
            )
            .apply()
    }
}
