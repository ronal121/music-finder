package com.kafshar.musicfinder

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
        isClickable = true
        isFocusable = true
        post {
            installSearchStatusBar()
            installClearQueryButton()
        }
    }

    override fun setBackgroundTintList(tint: ColorStateList?) {
        super.setBackgroundTintList(ColorStateList.valueOf(accent))
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val pressed = isPressed
        animate()
            .scaleX(if (pressed) 0.96f else 1f)
            .scaleY(if (pressed) 0.96f else 1f)
            .alpha(if (pressed) 0.72f else 1f)
            .setDuration(70L)
            .start()
    }

    private fun installClearQueryButton() {
        if (id != R.id.clearQuery) return
        setOnClickListener {
            (context as? Activity)
                ?.findViewById<EditText>(R.id.query)
                ?.text
                ?.clear()
        }
    }

    private fun installSearchStatusBar() {
        val activity = context as? Activity ?: return
        val status = activity.findViewById<TextView>(R.id.status) ?: return
        val query = activity.findViewById<EditText>(R.id.query) ?: return
        val searchRow = query.parent as? ViewGroup ?: return
        val outer = searchRow.parent as? ViewGroup ?: return

        if (status.parent === outer) {
            val currentIndex = outer.indexOfChild(status)
            val searchIndex = outer.indexOfChild(searchRow)
            if (currentIndex >= 0 && searchIndex >= 0 && currentIndex < searchIndex) {
                styleStatus(status, searchRow)
                return
            }
        }

        val oldParent = status.parent as? ViewGroup
        oldParent?.removeView(status)

        styleStatus(status, searchRow)
        val searchIndex = outer.indexOfChild(searchRow)
        outer.addView(status, searchIndex.coerceAtLeast(0))
    }

    private fun styleStatus(status: TextView, searchRow: View) {
        status.setTextColor(Color.WHITE)
        status.textSize = 11f
        status.gravity = android.view.Gravity.CENTER_VERTICAL
        status.setPadding(dp(10), 0, dp(10), 0)
        status.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(7).toFloat()
            setColor(0xFF9E1B1B.toInt())
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(30)
        )
        params.topMargin = dp(10)
        params.bottomMargin = dp(8)
        status.layoutParams = params

        val searchParams = searchRow.layoutParams
        if (searchParams is LinearLayout.LayoutParams) {
            searchParams.topMargin = 0
            searchRow.layoutParams = searchParams
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
