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
 * search pipeline. It starts when discovery begins, advances by search stage,
 * and reaches 100% only when the search actually finishes.
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
            if (isAttachedToWindow) postDelayed(this, 80L)
        }
    }

    init {
        setWillNotDraw(false)
        visibility = GONE
        paint.color = 0xFFF44336.toInt()
        paint.style = Paint.Style.FILL
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
        val root = rootView ?: return
        val status = root.findViewById<TextView>(R.id.status)?.text?.toString().orEmpty()

        val finished = status.contains("آهنگ قابل پخش پیدا نشد") ||
            status.contains("آهنگ قابل پخش پیدا شد") ||
            status.contains("زمان جستجو تمام شد") ||
            status.contains("جستجو موقتاً در دسترس نیست") ||
            status.contains("جستجوی منابع بیشتر متوقف شد")

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
        if (active) {
            displayed = max(displayed, 0.04f)
        }
        displayed = min(displayed, 1f)

        paint.color = 0x22F44336.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.color = 0xFFF44336.toInt()
        canvas.drawRect(0f, 0f, width * displayed, height, paint)

        if (active) postInvalidateDelayed(16L)
    }
}
