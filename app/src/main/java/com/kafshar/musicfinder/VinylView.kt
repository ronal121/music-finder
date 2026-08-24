package com.kafshar.musicfinder

import android.content.Context
import android.graphics.Bitmap
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

class VinylView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val executor = Executors.newSingleThreadExecutor()
    private var coverBitmap: Bitmap? = null
    private var rotation = 0f
    private var rotating = false
    private var destroyed = false
    private var requestedCoverUrl = ""

    private val rotationRunnable = object : Runnable {
        override fun run() {
            if (!rotating || destroyed) return
            rotation = (rotation + 1.2f) % 360f
            invalidate()
            postDelayed(this, 16L)
        }
    }

    fun setCover(url: String) {
        requestedCoverUrl = url
        if (url.isBlank() || destroyed) { clearCover(); return }
        executor.execute {
            try {
                val connection = URL(url).openConnection() as? HttpURLConnection ?: return@execute
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true
                connection.connect()
                if (connection.responseCode !in 200..299) { connection.disconnect(); return@execute }
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()
                val bitmap = BitmapFactoryCompat.decode(bytes)
                if (bitmap != null && !destroyed) {
                    post {
                        if (destroyed || requestedCoverUrl != url) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        } else {
                            replaceBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {}
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

    fun startRotation() {
        if (destroyed || rotating) return
        rotating = true
        removeCallbacks(rotationRunnable)
        post(rotationRunnable)
    }

    fun stopRotation() {
        rotating = false
        removeCallbacks(rotationRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height)
        if (size <= 0) return
        val rect = RectF((width-size)/2f, (height-size)/2f, (width+size)/2f, (height+size)/2f)
        canvas.save()
        canvas.rotate(rotation, rect.centerX(), rect.centerY())
        paint.style = Paint.Style.FILL
        paint.color = 0xFF080808.toInt()
        canvas.drawCircle(rect.centerX(), rect.centerY(), size/2f, paint)
        paint.color = 0xFF151515.toInt()
        canvas.drawCircle(rect.centerX(), rect.centerY(), size*.43f, paint)
        paint.color = 0xFF202020.toInt()
        canvas.drawCircle(rect.centerX(), rect.centerY(), size*.37f, paint)
        val bitmap = coverBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            val coverSize = size*.68f
            val coverRect = RectF(rect.centerX()-coverSize/2f, rect.centerY()-coverSize/2f, rect.centerX()+coverSize/2f, rect.centerY()+coverSize/2f)
            val path = Path().apply { addCircle(rect.centerX(), rect.centerY(), coverSize/2f, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(path); canvas.drawBitmap(bitmap, null, coverRect, paint); canvas.restore()
        } else {
            paint.color = 0xFF292929.toInt()
            canvas.drawCircle(rect.centerX(), rect.centerY(), size*.34f, paint)
        }
        paint.color = 0xFF111111.toInt()
        canvas.drawCircle(rect.centerX(), rect.centerY(), size*.045f, paint)
        paint.color = 0xFFCCCCCC.toInt()
        canvas.drawCircle(rect.centerX(), rect.centerY(), size*.012f, paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        destroyed = true
        stopRotation()
        try { executor.shutdownNow() } catch (_: Exception) {}
        clearCover()
        super.onDetachedFromWindow()
    }
}

private object BitmapFactoryCompat {
    fun decode(bytes: ByteArray): Bitmap? = try {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }
}
