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
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class OverlayService : Service(), TextToSpeech.OnInitListener {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        // Initialize TTS Engine
        tts = TextToSpeech(this, this)
    }

    // TTS Initialization Callback
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("OverlayService", "TTS Language not supported")
            } else {
                isTtsReady = true
            }
        } else {
            Log.e("OverlayService", "TTS Initialization failed")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val emotionEmoji = intent?.getStringExtra("emotion") ?: "😊"
        val duration = intent?.getLongExtra("duration", 3000L) ?: 3000L

        showOverlay(emotionEmoji, duration)
        speakEmotion(emotionEmoji) // Trigger Voice

        return START_NOT_STICKY
    }

    private fun speakEmotion(emoji: String) {
        // 1. Check if User enabled TTS
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("tts_enabled", true)
        if (!isEnabled) return

        // 2. Check if Engine is ready
        if (!isTtsReady || tts == null) return

        // 3. Lookup Phrase
        val phrase = EmotionData.getPhraseForEmoji(emoji)
        if (phrase.isNotEmpty()) {
            // QUEUE_FLUSH interrupts whatever was saying before (Responsive!)
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        }
    }

    private fun showOverlay(emotion: String, duration: Long) {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        removeOverlay()

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_emotion, null)
        val emotionText = overlayView?.findViewById<TextView>(R.id.emotionText)
        emotionText?.text = emotion

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
        windowManager?.addView(overlayView, params)

        overlayView?.postDelayed({ removeOverlay() }, duration)
        overlayView?.setOnClickListener { removeOverlay() }
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.isAttachedToWindow) windowManager?.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        // Shut down TTS to free resources
        tts?.stop()
        tts?.shutdown()
        removeOverlay()
        super.onDestroy()
    }

    // ... [Notification Channel code remains the same] ...
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_service",
                "Emotion Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "overlay_service")
            .setContentTitle("Emotional AAC")
            .setContentText("Listening for gestures")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}