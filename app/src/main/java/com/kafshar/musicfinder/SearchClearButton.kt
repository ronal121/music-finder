package com.kafshar.musicfinder

import android.content.Context
import android.util.AttributeSet

class SearchClearButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : HarmonizedButton(context, attrs, defStyleAttr) {

    init {
        setOnClickListener {
            rootView.findViewById<android.widget.EditText>(R.id.query)?.setText("")
        }
    }
}
