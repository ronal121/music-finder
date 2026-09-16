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

    init {
        backgroundTintList = ColorStateList.valueOf(accent)
        setTextColor(0xFFF3F1F7.toInt())
    }

    override fun setBackgroundTintList(tint: ColorStateList?) {
        super.setBackgroundTintList(ColorStateList.valueOf(accent))
    }
}
