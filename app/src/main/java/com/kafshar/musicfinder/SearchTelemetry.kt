package com.kafshar.musicfinder

import android.content.Context

/** Persistent per-source quality counters. Search terms and URLs are never stored. */
object SearchTelemetry {
    private const val PREFS = "music_source_quality"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(site: String, metric: String) = "${site.lowercase().replace(Regex("[^a-z0-9.-]"), "_")}.$metric"

    @Synchronized
    fun recordSearch(context: Context?, site: String, resultCount: Int) {
        if (context == null || site.isBlank()) return
        val p = prefs(context)
        val s = site.lowercase()
        p.edit()
            .putInt(key(s, "attempts"), p.getInt(key(s, "attempts"), 0) + 1)
            .putInt(key(s, "pages"), p.getInt(key(s, "pages"), 0) + if (resultCount > 0) 1 else 0)
            .putInt(key(s, "page_count"), p.getInt(key(s, "page_count"), 0) + resultCount.coerceAtLeast(0))
            .putInt(key(s, "empty"), p.getInt(key(s, "empty"), 0) + if (resultCount == 0) 1 else 0)
            .apply()
    }

    @Synchronized
    fun recordPlayable(context: Context?, site: String) {
        if (context == null || site.isBlank()) return
        val p = prefs(context)
        val k = key(site.lowercase(), "playable")
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
    }

    /** Every configured source remains eligible; historic results adapt the order. */
    fun rankSites(context: Context?, sites: List<String>): List<String> {
        if (context == null) return sites
        val p = prefs(context)
        return sites.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { item ->
                val site = item.value.lowercase()
                val attempts = p.getInt(key(site, "attempts"), 0)
                val pages = p.getInt(key(site, "pages"), 0)
                val playable = p.getInt(key(site, "playable"), 0)
                val empty = p.getInt(key(site, "empty"), 0)
                val pageRate = (pages + 1.0) / (attempts + 2.0)
                val playableRate = (playable + 0.5) / (pages + 2.0)
                val emptyRate = empty.toDouble() / (attempts + 1.0)
                val configured = (sites.size - item.index).toDouble() / sites.size.coerceAtLeast(1) * 12.0
                val exploration = if (attempts == 0) 8.0 else 0.0
                pageRate * 18.0 + playableRate * 48.0 - emptyRate * 8.0 + configured + exploration
            }.thenBy { item -> item.index }
        ).map { it.value }
    }
}
