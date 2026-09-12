package com.kafshar.musicfinder

object SearchQueryPlanner {
    /**
     * Google is the only discovery engine. Every generated query is constrained to
     * MusicSitePool, so Google ranks pages inside our reference-site universe rather
     * than searching the unrestricted web.
     */
    fun build(input: String): List<String> = ReferenceSiteQueries.build(input)
}
