package com.kafshar.musicfinder

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Two-state button: normal state and a clearly different pressed state. */
class HarmonizedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val defaultNormal = 0xFF20C9C9.toInt()
    private val defaultPressed = 0xFF119B9B.toInt()
    private val defaultFocused = 0xFF18B4B4.toInt()

    init {
        setButtonTint(defaultNormal)
        setTextColor(0xFFFFFFFF.toInt())
        isAllCaps = false
        isClickable = true
        isFocusable = true
    }

    /**
     * MainActivity applies a normal tint to keep the UI turquoise. Intercept it
     * and preserve the pressed/focused states instead of flattening the button
     * into one visual state.
     */
    override fun setBackgroundTintList(tint: ColorStateList?) {
        val normal = tint?.defaultColor ?: defaultNormal
        setButtonTint(normal)
    }

    private fun setButtonTint(normal: Int) {
        val pressed = darken(normal, 0.72f)
        val focused = darken(normal, 0.86f)
        super.setBackgroundTintList(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf()
                ),
                intArrayOf(pressed, focused, normal)
            )
        )
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
