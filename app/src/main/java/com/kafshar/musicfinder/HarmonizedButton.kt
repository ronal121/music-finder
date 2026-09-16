package com.kafshar.musicfinder

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
    private val statusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusAnimator: ValueAnimator? = null

    init {
        backgroundTintList = ColorStateList.valueOf(accent)
        setTextColor(0xFFF3F1F7.toInt())
        isClickable = true
        isFocusable = true
        post {
            installSearchStatusBar()
            installClearQueryButton()
            watchSearchStatus()
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
        statusHandler.removeCallbacksAndMessages(null)
        stopStatusAnimation()
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
            cornerRadius = dp(6).toFloat()
            setColor(0xFF9E1B1B.toInt())
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(20)
        )
        params.topMargin = dp(7)
        params.bottomMargin = dp(5)
        status.layoutParams = params

        val searchParams = searchRow.layoutParams
        if (searchParams is LinearLayout.LayoutParams) {
            searchParams.topMargin = 0
            searchRow.layoutParams = searchParams
        }
    }

    private fun watchSearchStatus() {
        val activity = context as? Activity ?: return
        val status = activity.findViewById<TextView>(R.id.status) ?: return

        val check = object : Runnable {
            override fun run() {
                if (status.parent == null) return
                val text = status.text?.toString().orEmpty()
                if (isSearchActive(text)) {
                    startStatusAnimation(status)
                } else {
                    stopStatusAnimation()
                }
                statusHandler.postDelayed(this, 120L)
            }
        }
        statusHandler.post(check)
    }

    private fun isSearchActive(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return false
        if (t.contains("پیدا شد")) return false
        if (t.contains("نتیجه‌ای نداد")) return false
        if (t.contains("در دسترس نیست")) return false
        if (t.contains("خطا")) return false
        return t.contains("جستجو") ||
            t.contains("بررسی") ||
            t.contains("استخراج") ||
            t.contains("منابع") ||
            t.contains("آماده")
    }

    private fun startStatusAnimation(status: View) {
        if (statusAnimator?.isRunning == true) return
        statusAnimator = ValueAnimator.ofFloat(-dp(4).toFloat(), dp(4).toFloat()).apply {
            duration = 420L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                status.translationX = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun stopStatusAnimation() {
        statusAnimator?.cancel()
        statusAnimator = null
        (context as? Activity)?.findViewById<TextView>(R.id.status)?.translationX = 0f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
