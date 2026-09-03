package com.kafshar.musicfinder

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText

class SearchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    override fun onEditorAction(actionCode: Int, event: KeyEvent?): Boolean {
        if (
            actionCode == EditorInfo.IME_ACTION_SEARCH ||
            actionCode == EditorInfo.IME_ACTION_DONE ||
            (event?.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            val imm = getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
        }
        return super.onEditorAction(actionCode, event)
    }
}
