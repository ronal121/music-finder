package com.kafshar.musicfinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LibraryManager {

    private const val PREF =
        "music_finder_library"

    private const val KEY =
        "songs"

    private const val MAX_ITEMS = 200

    private fun prefs(
        context: Context
    ) =
        context.applicationContext
            .getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )

    fun get(
        context: Context
    ): MutableList<SongResult> {

        val result =
            mutableListOf<SongResult>()

        val raw =
            prefs(context)
                .getString(
                    KEY,
                    "[]"
                )
                ?: "[]"

        try {

            val array =
                JSONArray(raw)

            for (
                i in 0 until
                        minOf(
                            array.length(),
                            MAX_ITEMS
                        )
            ) {

                val obj =
                    array.optJSONObject(i)
                        ?: continue

                val url =
                    obj.optString("url")
                        .trim()

                if (url.isBlank()) {
                    continue
                }

                result.add(
                    SongResult(
                        url = url,
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

        } catch (_: Exception) {
        }

        return result
    }

    @Synchronized
    fun add(
        context: Context,
        song: SongResult
    ) {

        if (
            song.url.isBlank()
        ) {
            return
        }

        val list =
            get(context)

        if (
            list.any {
                it.url == song.url
            }
        ) {
            return
        }

        list.add(0, song)

        while (
            list.size > MAX_ITEMS
        ) {
            list.removeAt(
                list.lastIndex
            )
        }

        save(
            context,
            list
        )
    }

    @Synchronized
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

        if (
            song.url.isBlank()
        ) {
            return false
        }

        return get(context).any {
            it.url == song.url
        }
    }

    private fun save(
        context: Context,
        list: List<SongResult>
    ) {

        try {

            val array =
                JSONArray()

            list.take(MAX_ITEMS)
                .forEach {

                    val obj =
                        JSONObject().apply {

                            put(
                                "url",
                                it.url
                            )

                            put(
                                "title",
                                it.title
                            )

                            put(
                                "artist",
                                it.artist
                            )

                            put(
                                "site",
                                it.site
                            )

                            put(
                                "cover",
                                it.cover
                            )
                        }

                    array.put(obj)
                }

            prefs(context)
                .edit()
                .putString(
                    KEY,
                    array.toString()
                )
                .apply()

        } catch (_: Exception) {
        }
    }
}
