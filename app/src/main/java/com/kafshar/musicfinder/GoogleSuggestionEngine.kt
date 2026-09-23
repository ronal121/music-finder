package com.kafshar.musicfinder

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.Future

class GoogleSuggestionEngine(
    private val onSuggestions: (generation: Int, suggestions: List<String>) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var destroyed = false

    private var requestGeneration = 0
    private var pending: Future<*>? = null
    private var debounce: Runnable? = null

    fun suggest(query: String, generation: Int) {
        if (destroyed) return

        val text = query.trim()
        requestGeneration = generation

        debounce?.let { handler.removeCallbacks(it) }
        pending?.cancel(true)
        pending = null

        if (text.length < 2) {
            onSuggestions(generation, emptyList())
            return
        }

        val task = Runnable {
            if (destroyed || generation != requestGeneration) return@Runnable

            pending = executor.submit {
                val suggestions = fetch(text)

                if (
                    destroyed ||
                    generation != requestGeneration
                ) {
                    return@submit
                }

                handler.post {
                    if (
                        !destroyed &&
                        generation == requestGeneration
                    ) {
                        onSuggestions(generation, suggestions)
                    }
                }
            }
        }

        debounce = task
        handler.postDelayed(task, 280L)
    }

    fun clear(generation: Int) {
        requestGeneration = generation
        debounce?.let { handler.removeCallbacks(it) }
        pending?.cancel(true)
        pending = null
        if (!destroyed) {
            handler.post {
                if (!destroyed) onSuggestions(generation, emptyList())
            }
        }
    }

    fun destroy() {
        destroyed = true
        debounce?.let { handler.removeCallbacks(it) }
        debounce = null
        pending?.cancel(true)
        pending = null
        executor.shutdownNow()
    }

    private fun fetch(query: String): List<String> {
        var connection: HttpURLConnection? = null

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url =
                "https://suggestqueries.google.com/complete/search" +
                "?client=firefox&hl=fa&gl=ir&q=$encoded"

            connection =
                URL(url)
                    .openConnection() as HttpURLConnection

            connection.connectTimeout = 3500
            connection.readTimeout = 4500
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json,text/plain,*/*"
            )

            if (connection.responseCode !in 200..299) {
                return emptyList()
            }

            val body =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            parseSuggestions(body)
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseSuggestions(body: String): List<String> {
        val start = body.indexOf('[')
        if (start < 0) return emptyList()

        return try {
            val root = org.json.JSONArray(body.substring(start))
            val values = root.optJSONArray(1) ?: return emptyList()

            buildList {
                for (i in 0 until values.length()) {
                    val value = values.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
                .distinct()
                .take(8)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
