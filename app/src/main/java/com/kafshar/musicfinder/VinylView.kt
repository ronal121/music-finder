package com.kafshar.musicfinder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class VinylView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {

    private val vinylPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val groovePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val centerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private var rotationAngle = 0f

    private val rotationRunnable =
        object : Runnable {

            override fun run() {

                rotationAngle += 1.5f

                if (rotationAngle >= 360f) {
                    rotationAngle -= 360f
                }

                invalidate()

                postDelayed(
                    this,
                    16L
                )
            }
        }

    init {

        setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        post(
            rotationRunnable
        )
    }

    override fun onDetachedFromWindow() {

        removeCallbacks(
            rotationRunnable
        )

        super.onDetachedFromWindow()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        val size =
            min(
                width,
                height
            )

        val centerX =
            width / 2f

        val centerY =
            height / 2f

        val radius =
            size / 2f - 8f

        canvas.save()

        canvas.rotate(
            rotationAngle,
            centerX,
            centerY
        )

        // سایه صفحه
        vinylPaint.style =
            Paint.Style.FILL

        vinylPaint.color =
            0x55000000

        vinylPaint.setShadowLayer(
            18f,
            0f,
            8f,
            0x88000000.toInt()
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            vinylPaint
        )

        vinylPaint.clearShadowLayer()

        // خود صفحه
        vinylPaint.color =
            0xFF111116.toInt()

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            vinylPaint
        )

        // شیارهای صفحه
        groovePaint.style =
            Paint.Style.STROKE

        groovePaint.strokeWidth =
            1.2f

        groovePaint.color =
            0xFF292930.toInt()

        var grooveRadius =
            radius - 12f

        while (
            grooveRadius > 35f
        ) {

            canvas.drawCircle(
                centerX,
                centerY,
                grooveRadius,
                groovePaint
            )

            grooveRadius -= 8f
        }

        // انعکاس روی صفحه
        highlightPaint.style =
            Paint.Style.STROKE

        highlightPaint.strokeWidth =
            2f

        highlightPaint.color =
            0x44555560

        val highlightRect =
            RectF(
                centerX - radius + 15f,
                centerY - radius + 15f,
                centerX + radius - 15f,
                centerY + radius - 15f
            )

        canvas.drawArc(
            highlightRect,
            210f,
            80f,
            false,
            highlightPaint
        )

        // لیبل وسط صفحه
        centerPaint.style =
            Paint.Style.FILL

        centerPaint.color =
            0xFF24242D.toInt()

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.27f,
            centerPaint
        )

        // مرکز لیبل
        labelPaint.style =
            Paint.Style.FILL

        labelPaint.color =
            0xFF8B8B96.toInt()

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.075f,
            labelPaint
        )

        // سوراخ وسط
        centerPaint.color =
            0xFF050509.toInt()

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.028f,
            centerPaint
        )

        canvas.restore()
    }
}
