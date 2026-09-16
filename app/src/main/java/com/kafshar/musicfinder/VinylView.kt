package com.kafshar.musicfinder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Static album-art view. The old implementation rendered a rotating vinyl;
 * playback controls now use a simple, non-animated cover instead.
 */
class VinylView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val executor = Executors.newSingleThreadExecutor()
    private var coverBitmap: Bitmap? = null
    private var destroyed = false
    private var requestedCoverUrl = ""

    fun setCover(url: String) {
        requestedCoverUrl = url
        if (url.isBlank() || destroyed) {
            clearCover()
            return
        }
        executor.execute {
            try {
                val connection = URL(url).openConnection() as? HttpURLConnection ?: return@execute
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return@execute
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null && !destroyed) {
                    post {
                        if (!destroyed && requestedCoverUrl == url) replaceBitmap(bitmap)
                        else if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun setCover(bitmap: Bitmap) {
        if (destroyed || bitmap.isRecycled) return
        requestedCoverUrl = "bitmap"
        post { if (!destroyed) replaceBitmap(bitmap) }
    }

    private fun replaceBitmap(bitmap: Bitmap) {
        val old = coverBitmap
        coverBitmap = bitmap
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
        invalidate()
    }

    fun clearCover() {
        requestedCoverUrl = ""
        val old = coverBitmap
        coverBitmap = null
        if (old != null && !old.isRecycled) old.recycle()
        invalidate()
    }

    // Kept for source compatibility with MainActivity. They intentionally do nothing.
    fun startRotation() = Unit
    fun stopRotation() = Unit

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = (8 * resources.displayMetrics.density)
        val rect = RectF(pad, pad, width - pad, height - pad)

        paint.style = Paint.Style.FILL
        paint.color = 0xFF16161E.toInt()
        canvas.drawRoundRect(rect, 18f * resources.displayMetrics.density, 18f * resources.displayMetrics.density, paint)

        val bitmap = coverBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            canvas.save()
            val clip = RectF(rect)
            canvas.clipRect(clip)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        } else {
            paint.color = 0xFF202029.toInt()
            canvas.drawRoundRect(rect, 18f * resources.displayMetrics.density, 18f * resources.displayMetrics.density, paint)
            paint.color = 0xFF777783.toInt()
            paint.textSize = 42f * resources.displayMetrics.density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("♪", width / 2f, height / 2f + paint.textSize / 3f, paint)
        }
    }

    override fun onDetachedFromWindow() {
        destroyed = true
        try { executor.shutdownNow() } catch (_: Exception) { }
        clearCover()
        super.onDetachedFromWindow()
    }
}
