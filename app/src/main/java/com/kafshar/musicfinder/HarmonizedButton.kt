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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
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
        val progress = activity.findViewById<android.widget.ProgressBar>(R.id.searchProgress) ?: return
        val query = activity.findViewById<EditText>(R.id.query) ?: return
        val searchRow = query.parent as? ViewGroup ?: return
        val outer = searchRow.parent as? ViewGroup ?: return

        (status.parent as? ViewGroup)?.removeView(status)
        (progress.parent as? ViewGroup)?.removeView(progress)

        styleStatus(status)
        styleProgress(progress)

        val searchIndex = outer.indexOfChild(searchRow).coerceAtLeast(0)
        outer.addView(status, searchIndex)
        outer.addView(progress, searchIndex + 1)
    }

    private fun styleStatus(status: TextView) {
        status.setTextColor(0xFFAAAAAA.toInt())
        status.textSize = 10f
        status.gravity = android.view.Gravity.CENTER_VERTICAL
        status.setPadding(dp(2), 0, dp(2), 0)
        status.background = null

        status.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(16)
        ).apply {
            topMargin = dp(4)
            bottomMargin = dp(1)
        }
    }

    private fun styleProgress(progress: android.widget.ProgressBar) {
        progress.max = 100
        progress.progress = progress.progress.coerceIn(0, 100)
        progress.progressTintList =
            ColorStateList.valueOf(0xFFB51D1D.toInt())
        progress.backgroundTintList =
            ColorStateList.valueOf(0x33202020.toInt())

        progress.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(3)
        ).apply {
            bottomMargin = dp(6)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
