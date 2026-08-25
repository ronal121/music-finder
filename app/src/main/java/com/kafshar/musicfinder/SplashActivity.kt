package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Lightweight launcher splash. Keeps the real MainActivity unchanged and gives WebView/player
 * initialization a clean hand-off instead of showing a blank first frame. */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private val openMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(10, 10, 15)
        window.navigationBarColor = Color.rgb(10, 10, 15)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(10, 10, 15))
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_music_finder)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0f
            animate().alpha(1f).setDuration(450L).start()
        }

        root.addView(icon, LinearLayout.LayoutParams(112, 112).apply {
            bottomMargin = 18
        })

        val title = TextView(this).apply {
            text = "Music Finder"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val subtitle = TextView(this).apply {
            text = "KAFSHAR"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(32, 201, 201))
            gravity = Gravity.CENTER
        }
        root.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 7 })

        setContentView(root)
        handler.postDelayed(openMain, 950L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openMain)
        super.onDestroy()
    }
}
