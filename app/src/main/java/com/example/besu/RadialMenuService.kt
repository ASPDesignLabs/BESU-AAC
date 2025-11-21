package com.example.besu

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

class RadialMenuService : Service() {
    private var windowManager: WindowManager? = null
    private var radialMenuView: View? = null

    companion object {
        private const val TAG = "RadialMenuService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check for null intent to prevent crashes on system restart
        if (intent == null) {
            Log.w(TAG, "Service restarted with null intent. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Showing radial menu")
        showRadialMenu()
        return START_NOT_STICKY
    }

    private fun showRadialMenu() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        removeRadialMenu()

        // Inflate custom radial menu view
        radialMenuView = LayoutInflater.from(this).inflate(R.layout.radial_menu, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER

        windowManager?.addView(radialMenuView, params)

        setupRadialMenu(radialMenuView)
    }

    private fun setupRadialMenu(view: View?) {
        view ?: return

        val container = view.findViewById<RadialMenuLayout>(R.id.radialContainer)

        // Load the new, expanded emotion list
        container.setEmotions(EmotionData.primaryEmotions)

        // 1. User Tapped a Primary Emotion (e.g., "Happy")
        // The UI expands to show sub-options. We don't trigger action yet.
        container.onEmotionSelected = { emotion ->
            Log.d(TAG, "Expanded: ${emotion.label}")
        }

        // 2. User Tapped the Center Button AGAIN (Confirm Base Emotion)
        // e.g., "I am feeling Happy today"
        container.onPrimaryConfirmed = { emotion ->
            removeRadialMenu()
            showFullScreenEmotion(emotion)
        }

        // 3. User Tapped a Sub-Emotion (e.g., "Proud")
        // e.g., "I am proud of myself"
        container.onSubEmotionSelected = { emotion ->
            removeRadialMenu()
            showFullScreenEmotion(emotion)
        }

        // Dismiss menu on background tap (outside the buttons)
        view.setOnClickListener {
            removeRadialMenu()
        }
    }

    private fun showFullScreenEmotion(emotion: Emotion) {
        // Launch the OverlayService which handles the Display AND the Voice
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emotion.emoji)
        intent.putExtra("duration", 4000L)

        // Since we are launching from another service/overlay context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun removeRadialMenu() {
        radialMenuView?.let {
            if (it.isAttachedToWindow) {
                windowManager?.removeView(it)
            }
            radialMenuView = null
        }
    }

    override fun onDestroy() {
        removeRadialMenu()
        super.onDestroy()
    }
}