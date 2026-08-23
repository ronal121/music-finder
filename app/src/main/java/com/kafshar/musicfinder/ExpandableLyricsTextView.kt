package com.kafshar.musicfinder

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView

class ExpandableLyricsTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {

    private var expanded = false

    init {
        isClickable = true
        isFocusable = true
        gravity = Gravity.START
        setOnClickListener { toggle() }
    }

    private fun toggle() {
        expanded = !expanded
        if (expanded) {
            maxLines = Int.MAX_VALUE
            layoutParams.height = dp(280)
            scrollTo(0, 0)
            movementMethod = android.text.method.ScrollingMovementMethod()
        } else {
            maxLines = 3
            layoutParams.height = dp(72)
            scrollTo(0, 0)
        }
        requestLayout()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        if (!expanded) {
            maxLines = 3
            if (layoutParams != null) layoutParams.height = dp(72)
        }
    }

    fun collapse() {
        if (expanded) toggle()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
