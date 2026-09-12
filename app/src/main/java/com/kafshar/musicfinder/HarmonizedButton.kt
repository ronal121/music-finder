package com.kafshar.musicfinder

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class HarmonizedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val accent = 0xFF4B4268.toInt()
    private val pressedGlass = 0x665E6A86

    init {
        applyTwoStateTint()
        setTextColor(0xFFF3F1F7.toInt())
        isAllCaps = false

        if (id == R.id.clearSearch) {
            setOnClickListener {
                rootView.findViewById<android.widget.EditText>(R.id.query)?.setText("")
            }
        }
    }

    private fun applyTwoStateTint() {
        backgroundTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_focused),
                intArrayOf()
            ),
            intArrayOf(
                pressedGlass,
                0xAA6A6184.toInt(),
                accent
            )
        )
    }

    override fun setBackgroundTintList(tint: ColorStateList?) {
        applyTwoStateTint()
    }
}
