package com.example.besu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class TapDetectionService : Service() {
    private var windowManager: WindowManager? = null
    private var tapDetectionView: View? = null

    private var tapCount = 0
    private var lastTapTime = 0L
    private val tapTimeout = 200L
    private val requiredTaps = 3

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, createNotification())
        createTapDetectionOverlay()
    }

    private fun createTapDetectionOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create a tiny 1x1 pixel view
        tapDetectionView = object : View(this) {
            override fun onTouchEvent(event: MotionEvent?): Boolean {
                // This catches ACTION_OUTSIDE events
                if (event?.action == MotionEvent.ACTION_OUTSIDE) {
                    handleTap()
                }
                return false
            }
        }

        val params = WindowManager.LayoutParams(
            1, // 1 pixel wide
            1, // 1 pixel tall
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // KEY FLAGS: NOT_TOUCHABLE lets touches pass through
            // WATCH_OUTSIDE_TOUCH lets us observe them
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        // Position in top-left corner (invisible)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        windowManager?.addView(tapDetectionView, params)
    }

    private fun handleTap() {
        val currentTime = SystemClock.uptimeMillis()

        if (currentTime - lastTapTime <= tapTimeout) {
            tapCount++
        } else {
            tapCount = 1
        }

        lastTapTime = currentTime

        if (tapCount >= requiredTaps) {
            tapCount = 0
            triggerOverlay()
        }
    }

    private fun triggerOverlay() {
        val prefs = getSharedPreferences("gestures", Context.MODE_PRIVATE)
        val emotion = prefs.getString("triple_tap", "😊") ?: "😊"

        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emotion)
        intent.putExtra("duration", 5000L)
        startService(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tap_detection_service",
                "Tap Detection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "tap_detection_service")
            .setContentTitle("Emotional AAC Active")
            .setContentText("Screen taps (😊) • Device taps (👍)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        tapDetectionView?.let {
            windowManager?.removeView(it)
        }
        super.onDestroy()
    }
}