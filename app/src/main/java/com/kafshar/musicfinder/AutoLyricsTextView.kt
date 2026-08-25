package com.kafshar.musicfinder

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView

class AutoLyricsTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {
    private var expanded = false
    private var lastKey = ""
    private var titleView: TextView? = null
    private var artistView: TextView? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = requestLyrics()
        override fun afterTextChanged(s: Editable?) = Unit
    }

    init {
        isClickable = true
        isFocusable = true
        gravity = Gravity.START
        setOnClickListener { toggle() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val root = rootView
        titleView = root.findViewById(resources.getIdentifier("titleText", "id", context.packageName))
        artistView = root.findViewById(resources.getIdentifier("artistText", "id", context.packageName))
        titleView?.addTextChangedListener(watcher)
        artistView?.addTextChangedListener(watcher)
        postDelayed({ requestLyrics() }, 300L)
    }

    override fun onDetachedFromWindow() {
        titleView?.removeTextChangedListener(watcher)
        artistView?.removeTextChangedListener(watcher)
        super.onDetachedFromWindow()
    }

    private fun requestLyrics() {
        val title = titleView?.text?.toString()?.trim().orEmpty()
        val artist = artistView?.text?.toString()?.trim().orEmpty()
        if (title.isBlank() || artist.isBlank()) return
        if (title.equals("Music Finder", true) || title.equals("در حال جستجو...", true)) return
        if (artist.equals("Ready to search", true) || artist.equals("در حال جستجو...", true)) return
        val key = "$title\u0000$artist"
        if (key == lastKey) return
        lastKey = key
        visibility = View.VISIBLE
        text = "در حال دریافت متن..."
        Thread {
            val lyrics = LyricsProvider.find(title, artist)
            post {
                if (!isAttachedToWindow) return@post
                text = lyrics ?: "متن این آهنگ پیدا نشد"
            }
        }.start()
    }

    private fun toggle() {
        expanded = !expanded
        if (expanded) {
            maxLines = Int.MAX_VALUE
            layoutParams.height = dp(280)
            movementMethod = android.text.method.ScrollingMovementMethod()
        } else {
            maxLines = 3
            layoutParams.height = dp(72)
        }
        requestLayout()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        if (!expanded && layoutParams != null) layoutParams.height = dp(72)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
