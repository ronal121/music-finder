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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            (parent as? android.view.ViewGroup)
                ?.findViewById<android.view.View>(R.id.clearSearch)
                ?.setOnClickListener {
                    setText("")
                    requestFocus()
                    setSelection(0)
                }
        } catch (_: Exception) {
        }
    }

    private fun hideKeyboard() {
        val imm = getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    override fun onEditorAction(actionCode: Int) {
        if (actionCode == EditorInfo.IME_ACTION_SEARCH || actionCode == EditorInfo.IME_ACTION_DONE) {
            hideKeyboard()
        }
        super.onEditorAction(actionCode)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            hideKeyboard()
        }
        return super.onKeyUp(keyCode, event)
    }
}
