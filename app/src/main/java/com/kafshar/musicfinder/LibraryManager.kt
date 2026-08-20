package com.kafshar.musicfinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LibraryManager {

    private const val PREF =
        "music_finder_library"

    private const val KEY =
        "songs"

    fun get(
        context: Context
    ): MutableList<SongResult> {

        val prefs =
            context.getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )

        val raw =
            prefs.getString(
                KEY,
                "[]"
            ) ?: "[]"

        val result =
            mutableListOf<SongResult>()

        try {

            val array =
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

                result.add(
                    SongResult(
                        url =
                            obj.optString("url"),

                        title =
                            obj.optString("title"),

                        artist =
                            obj.optString("artist"),

                        site =
                            obj.optString("site"),

                        cover =
                            obj.optString("cover")
                    )
                )
            }

        } catch (
            _: Exception
        ) {
        }

        return result
    }

    fun add(
        context: Context,
        song: SongResult
    ) {

        val list =
            get(context)

        if (
            list.any {
                it.url == song.url
            }
        ) {
            return
        }

        list.add(song)

        save(
            context,
            list
        )
    }

    fun remove(
        context: Context,
        song: SongResult
    ) {

        val list =
            get(context)

        list.removeAll {
            it.url == song.url
        }

        save(
            context,
            list
        )
    }

    fun contains(
        context: Context,
        song: SongResult
    ): Boolean {

        return get(context).any {
            it.url == song.url
        }
    }

    private fun save(
        context: Context,
        list: List<SongResult>
    ) {

        val array =
            JSONArray()

        list.forEach {

            val obj =
                JSONObject()

            obj.put(
                "url",
                it.url
            )

            obj.put(
                "title",
                it.title
            )

            obj.put(
                "artist",
                it.artist
            )

            obj.put(
                "site",
                it.site
            )

            obj.put(
                "cover",
                it.cover
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
