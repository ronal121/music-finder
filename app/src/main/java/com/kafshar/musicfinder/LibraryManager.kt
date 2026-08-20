package com.kafshar.musicfinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LibraryManager {

    private const val PREF =
        "music_finder_library"

    private const val KEY =
        "songs"

    fun getSongs(
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
                            obj.optString(
                                "url"
                            ),

                        title =
                            obj.optString(
                                "title"
                            ),

                        artist =
                            obj.optString(
                                "artist"
                            ),

                        site =
                            obj.optString(
                                "site"
                            ),

                        cover =
                            obj.optString(
                                "cover"
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {
        }

        return result
    }

    fun saveSong(
        context: Context,
        song: SongResult
    ) {

        val list =
            getSongs(context)

        if (
            list.any {
                it.url == song.url
            }
        ) {
            return
        }

        list.add(song)

        saveSongs(
            context,
            list
        )
    }

    fun removeSong(
        context: Context,
        song: SongResult
    ) {

        val list =
            getSongs(context)

        list.removeAll {
            it.url == song.url
        }

        saveSongs(
            context,
            list
        )
    }

    fun contains(
        context: Context,
        song: SongResult
    ): Boolean {

        return getSongs(context)
            .any {
                it.url == song.url
            }
    }

    private fun saveSongs(
        context: Context,
        list: List<SongResult>
    ) {

        val array =
            JSONArray()

        list.forEach { song ->

            val obj =
                JSONObject()

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

            array.put(
                obj
            )
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

    // سازگاری با نسخه قبلی
    fun get(
        context: Context
    ): MutableList<SongResult> {

        return getSongs(context)
    }

    // سازگاری با نسخه قبلی
    fun add(
        context: Context,
        song: SongResult
    ) {

        saveSong(
            context,
            song
        )
    }

    // سازگاری با نسخه قبلی
    fun remove(
        context: Context,
        song: SongResult
    ) {

        removeSong(
            context,
            song
        )
    }
}
