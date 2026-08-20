package com.kafshar.musicfinder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.min

class VinylView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
        )

    private var coverBitmap: Bitmap? = null

    private var rotation = 0f

    private var rotating = false

    private val executor =
        Executors.newSingleThreadExecutor()

    private val rotationRunnable =
        object : Runnable {

            override fun run() {

                if (
                    !rotating
                ) {
                    return
                }

                rotation += 1.2f

                if (
                    rotation >= 360f
                ) {
                    rotation -= 360f
                }

                invalidate()

                postDelayed(
                    this,
                    16
                )
            }
        }

    fun setCover(
        url: String
    ) {

        coverBitmap = null

        invalidate()

        if (
            url.isBlank()
        ) {
            return
        }

        executor.execute {

            try {

                val connection =
                    URL(url)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    8000

                connection.readTimeout =
                    8000

                connection.connect()

                val bitmap =
                    BitmapFactory.decodeStream(
                        connection.inputStream
                    )

                connection.disconnect()

                post {

                    coverBitmap =
                        bitmap

                    invalidate()
                }

            } catch (
                _: Exception
            ) {
            }
        }
    }

    fun clearCover() {

        coverBitmap = null

        invalidate()
    }

    fun startRotation() {

        if (
            rotating
        ) {
            return
        }

        rotating = true

        removeCallbacks(
            rotationRunnable
        )

        post(
            rotationRunnable
        )
    }

    fun stopRotation() {

        rotating = false

        removeCallbacks(
            rotationRunnable
        )
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

        if (
            size <= 0
        ) {
            return
        }

        val left =
            (width - size) / 2f

        val top =
            (height - size) / 2f

        val rect =
            RectF(
                left,
                top,
                left + size,
                top + size
            )

        canvas.save()

        canvas.rotate(
            rotation,
            rect.centerX(),
            rect.centerY()
        )

        paint.style =
            Paint.Style.FILL

        paint.color =
            0xFF080808.toInt()

        canvas.drawCircle(
            rect.centerX(),
            rect.centerY(),
            size / 2f,
            paint
        )

        paint.color =
            0xFF151515.toInt()

        canvas.drawCircle(
            rect.centerX(),
            rect.centerY(),
            size * 0.43f,
            paint
        )

        paint.color =
            0xFF202020.toInt()

        canvas.drawCircle(
            rect.centerX(),
            rect.centerY(),
            size * 0.37f,
            paint
        )

        val bitmap =
            coverBitmap

        if (
            bitmap != null
        ) {

            val coverSize =
                size * 0.68f

            val coverRect =
                RectF(
                    rect.centerX() -
                            coverSize / 2f,

                    rect.centerY() -
                            coverSize / 2f,

                    rect.centerX() +
                            coverSize / 2f,

                    rect.centerY() +
                            coverSize / 2f
                )

            val path =
                Path()

            path.addCircle(
                rect.centerX(),
                rect.centerY(),
                coverSize / 2f,
                Path.Direction.CW
            )

            canvas.save()

            canvas.clipPath(path)

            canvas.drawBitmap(
                bitmap,
                null,
                coverRect,
                paint
            )

            canvas.restore()

        } else {

            paint.color =
                0xFF292929.toInt()

            canvas.drawCircle(
                rect.centerX(),
                rect.centerY(),
                size * 0.34f,
                paint
            )
        }

        paint.color =
            0xFF111111.toInt()

        canvas.drawCircle(
            rect.centerX(),
            rect.centerY(),
            size * 0.045f,
            paint
        )

        paint.color =
            0xFFCCCCCC.toInt()

        canvas.drawCircle(
            rect.centerX(),
            rect.centerY(),
            size * 0.012f,
            paint
        )

        canvas.restore()
    }

    override fun onDetachedFromWindow() {

        stopRotation()

        executor.shutdownNow()

        super.onDetachedFromWindow()
    }
}
