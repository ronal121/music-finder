package com.kafshar.musicfinder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * Thin search progress indicator driven by the real status text emitted by the
 * search pipeline. The view is already placed directly above the search row in
 * activity_main.xml, so it must not be translated or participate in scrolling
 * through a second coordinate system.
 */
class SearchProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var displayed = 0f
    private var target = 0f
    private var active = false

    private val poll = object : Runnable {
        override fun run() {
            updateFromStatus()
            if (isAttachedToWindow) postDelayed(this, 120L)
        }
    }

    init {
        setWillNotDraw(false)
        visibility = GONE
        paint.style = Paint.Style.FILL
        // Do not use translationY here. The progress view is a normal child of
        // the same scrolling column as the search bar; translating it caused
        // occasional scroll/layout jitter while the ScrollView was moving.
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(poll)
        post(poll)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(poll)
        super.onDetachedFromWindow()
    }

    private fun updateFromStatus() {
        val status = rootView.findViewById<TextView>(R.id.status)?.text?.toString().orEmpty()

        val finished = status.contains("آهنگ قابل پخش پیدا نشد") ||
            status.contains("آهنگ قابل پخش پیدا شد") ||
            status.contains("زمان جستجو تمام شد") ||
            status.contains("جستجوی منابع بیشتر متوقف شد") ||
            status.contains("جستجو موقتاً در دسترس نیست")

        if (finished) {
            active = false
            target = 1f
            visibility = VISIBLE
            invalidate()
            postDelayed({
                if (!active) visibility = GONE
            }, 220L)
            return
        }

        val searching = status.contains("در حال جستجو") ||
            status.contains("در حال بررسی") ||
            status.contains("صفحه پیدا شد") ||
            status.contains("منبع")

        if (!searching) return

        if (!active && displayed >= 0.98f) displayed = 0f
        active = true
        visibility = VISIBLE
        target = when {
            status.contains("در حال بررسی") -> 0.72f
            status.contains("صفحه پیدا شد") -> 0.48f
            else -> 0.20f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        displayed += (target - displayed) * 0.18f
        if (active) displayed = max(displayed, 0.04f)
        displayed = min(displayed, 1f)

        paint.color = 0x22F44336.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.color = 0xFFF44336.toInt()
        canvas.drawRect(0f, 0f, width * displayed, height, paint)

        if (active) postInvalidateDelayed(32L)
    }
}
