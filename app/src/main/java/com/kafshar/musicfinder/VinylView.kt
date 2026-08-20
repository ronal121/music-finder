package com.kafshar.musicfinder

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class VinylView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private var coverBitmap: Bitmap? = null

    private val executor =
        Executors.newSingleThreadExecutor()

    private var rotationAnimator:
        ObjectAnimator? = null

    init {

        setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )
    }

    fun setCover(
        url: String
    ) {

        if (url.isBlank()) {

            coverBitmap = null

            invalidate()

            return
        }

        executor.execute {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                        as HttpURLConnection

                connection.connectTimeout =
                    7000

                connection.readTimeout =
                    7000

                connection.connect()

                val bitmap =
                    BitmapFactory.decodeStream(
                        connection.inputStream
                    )

                connection.disconnect()

                if (bitmap != null) {

                    post {

                        coverBitmap =
                            bitmap

                        invalidate()
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    fun startRotation() {

        if (
            rotationAnimator?.isRunning == true
        ) {
            return
        }

        rotationAnimator =
            ObjectAnimator.ofFloat(
                this,
                View.ROTATION,
                rotation,
                rotation + 360f
            ).apply {

                duration = 7000

                interpolator =
                    LinearInterpolator()

                repeatCount =
                    ObjectAnimator.INFINITE

                start()
            }
    }

    fun stopRotation() {

        rotationAnimator?.cancel()

        rotationAnimator = null
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        val cx =
            width / 2f

        val cy =
            height / 2f

        val radius =
            minOf(
                width,
                height
            ) / 2f - 8f

        paint.style =
            Paint.Style.FILL

        paint.color =
            Color.rgb(
                22,
                22,
                25
            )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth = 3f

        paint.color =
            Color.rgb(
                55,
                55,
                60
            )

        canvas.drawCircle(
            cx,
            cy,
            radius - 4,
            paint
        )

        val bitmap =
            coverBitmap

        if (bitmap != null) {

            paint.style =
                Paint.Style.FILL

            val coverRadius =
                radius * 0.56f

            val src =
                Rect(
                    0,
                    0,
                    bitmap.width,
                    bitmap.height
                )

            val dst =
                RectF(
                    cx - coverRadius,
                    cy - coverRadius,
                    cx + coverRadius,
                    cy + coverRadius
                )

            canvas.save()

            val clipPath =
                Path()

            clipPath.addCircle(
                cx,
                cy,
                coverRadius,
                Path.Direction.CW
            )

            canvas.clipPath(
                clipPath
            )

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                paint
            )

            canvas.restore()

        } else {

            paint.style =
                Paint.Style.FILL

            paint.color =
                Color.rgb(
                    45,
                    45,
                    50
                )

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.56f,
                paint
            )
        }

        paint.color =
            Color.BLACK

        canvas.drawCircle(
            cx,
            cy,
            radius * 0.09f,
            paint
        )

        paint.color =
            Color.WHITE

        canvas.drawCircle(
            cx,
            cy,
            radius * 0.025f,
            paint
        )
    }

    override fun onDetachedFromWindow() {

        stopRotation()

        executor.shutdownNow()

        super.onDetachedFromWindow()
    }
}
