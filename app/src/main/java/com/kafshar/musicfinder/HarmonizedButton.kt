package com.kafshar.musicfinder

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Two-state button: normal surface and a translucent glass-like pressed surface. */
class HarmonizedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val normalSurface = 0xFF4B4268.toInt()
    private val focusedSurface = 0xAA6A6184.toInt()
    private val pressedGlass = 0x665E6A86

    init {
        backgroundTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_focused),
                intArrayOf()
            ),
            intArrayOf(pressedGlass, focusedSurface, normalSurface)
        )
        setTextColor(0xFFF3F1F7.toInt())
        isAllCaps = false
    }
}
