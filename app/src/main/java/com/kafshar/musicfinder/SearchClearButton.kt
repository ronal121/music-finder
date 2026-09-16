package com.kafshar.musicfinder

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class SearchClearButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        setOnClickListener {
            rootView.findViewById<android.widget.EditText>(R.id.query)?.setText("")
        }
    }
}
