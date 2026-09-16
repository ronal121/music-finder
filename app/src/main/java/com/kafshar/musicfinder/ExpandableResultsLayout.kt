package com.kafshar.musicfinder

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Search-results container.
 * Shows only the first three results by default and expands when the user
 * taps the More/Show less control next to SEARCH RESULTS.
 *
 * MainActivity can continue to use it exactly like a LinearLayout, so no
 * search/playback logic has to change.
 */
class ExpandableResultsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val COLLAPSED_COUNT = 3
    }

    private var expanded = false
    private var toggleView: TextView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindToggle()
        refreshChildren()
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        refreshChildren()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (childCount <= COLLAPSED_COUNT) {
            expanded = false
        }
        refreshChildren()
    }

    private fun bindToggle() {
        val root = rootView ?: return
        toggleView = root.findViewById(R.id.resultsToggle)
        toggleView?.setOnClickListener {
            if (childCount <= COLLAPSED_COUNT) return@setOnClickListener
            expanded = !expanded
            refreshChildren()
        }
    }

    private fun refreshChildren() {
        val count = childCount

        for (i in 0 until count) {
            getChildAt(i).visibility = when {
                expanded -> View.VISIBLE
                i < COLLAPSED_COUNT -> View.VISIBLE
                else -> View.GONE
            }
        }

        val toggle = toggleView ?: rootView?.findViewById<TextView>(R.id.resultsToggle)
        toggleView = toggle

        if (count > COLLAPSED_COUNT) {
            toggle?.visibility = View.VISIBLE
            toggle?.text = if (expanded) "Less  ↑" else "More  ›"
            toggle?.contentDescription = if (expanded) {
                "Show fewer search results"
            } else {
                "Show all search results"
            }
        } else {
            toggle?.visibility = View.GONE
        }
    }
}
