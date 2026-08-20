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
import java.util.concurrent.ExecutorService
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

    private val executor:
            ExecutorService =
        Executors.newSingleThreadExecutor()

    private var coverBitmap: Bitmap? = null
    private var rotation = 0f
    private var rotating = false
    private var destroyed = false
    private var requestedCoverUrl = ""

    private val rotationRunnable =
        object : Runnable {

            override fun run() {

                if (
                    !rotating ||
                    destroyed
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
                    16L
                )
            }
        }

    fun setCover(
        url: String
    ) {

        requestedCoverUrl = url

        coverBitmap = null
        invalidate()

        if (
            url.isBlank() ||
            destroyed
        ) {
            return
        }

        executor.execute {

            var connection:
                    HttpURLConnection? = null

            try {

                connection =
                    URL(url)
                        .openConnection()
                            as? HttpURLConnection
                        ?: return@execute

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                connection.connect()

                if (
                    connection.responseCode !in
                    200..299
                ) {
                    return@execute
                }

                val bytes =
                    connection.inputStream.use {
                        it.readBytes()
                    }

                if (
                    bytes.isEmpty()
                ) {
                    return@execute
                }

                val bounds =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }

                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    bounds
                )

                val maxSize = 700

                var sample = 1

                while (
                    bounds.outWidth / sample >
                    maxSize ||
                    bounds.outHeight / sample >
                    maxSize
                ) {
                    sample *= 2
                }

                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig =
                            Bitmap.Config.RGB_565
                    }

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        options
                    )

                if (
                    bitmap == null ||
                    destroyed
                ) {
                    return@execute
                }

                post {

                    if (
                        destroyed ||
                        requestedCoverUrl != url
                    ) {
                        bitmap.recycle()
                        return@post
                    }

                    coverBitmap?.let {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }

                    coverBitmap = bitmap

                    invalidate()
                }

            } catch (_: Exception) {

            } finally {

                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clearCover() {

        requestedCoverUrl = ""

        val old =
            coverBitmap

        coverBitmap = null

        if (
            old != null &&
            !old.isRecycled
        ) {
            old.recycle()
        }

        invalidate()
    }

    fun startRotation() {

        if (
            destroyed ||
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

        if (size <= 0) {
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
            bitmap != null &&
            !bitmap.isRecycled
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

        destroyed = true

        stopRotation()

        removeCallbacks(
            rotationRunnable
        )

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }

        coverBitmap?.let {

            if (!it.isRecycled) {
                it.recycle()
            }
        }

        coverBitmap = null

        super.onDetachedFromWindow()
    }
}
