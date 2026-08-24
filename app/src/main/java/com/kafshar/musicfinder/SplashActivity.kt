package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(openMain, 900L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openMain)
        super.onDestroy()
    }
}
