package com.example.besu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
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

    private var pendingSpeech: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("OverlayService", "TTS Language not supported")
            } else {
                isTtsReady = true
                Log.d("OverlayService", "TTS Ready")

                // Apply Voice Preference immediately if available
                applyVoicePreference()

                pendingSpeech?.let {
                    speakText(it)
                    pendingSpeech = null
                }
            }
        } else {
            Log.e("OverlayService", "TTS Initialization failed")
        }
    }

    // New helper to apply the specific voice object
    private fun applyVoicePreference() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedVoiceName = prefs.getString("tts_voice_name", "")

        if (!savedVoiceName.isNullOrEmpty()) {
            try {
                val targetVoice = tts?.voices?.find { it.name == savedVoiceName }
                if (targetVoice != null) {
                    tts?.voice = targetVoice
                    Log.d("OverlayService", "Applied Voice: ${targetVoice.name}")
                }
            } catch (e: Exception) {
                Log.e("OverlayService", "Error setting voice", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val emotionEmoji = intent?.getStringExtra("emotion") ?: "😊"
        val duration = intent?.getLongExtra("duration", 3000L) ?: 3000L
        val directPhrase = intent?.getStringExtra("phrase")

        showOverlay(emotionEmoji, duration)

        val textToSpeak = directPhrase ?: CommunicationData.getPhraseForEmoji(emotionEmoji)

        if (isTtsReady) {
            speakText(textToSpeak)
        } else {
            pendingSpeech = textToSpeak
        }

        return START_NOT_STICKY
    }

    private fun speakText(text: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tts_enabled", true)) return

        // Ensure voice is up to date in case it changed while service was running
        applyVoicePreference()

        val params = Bundle()
        // Request High Quality Network Synthesis
        params.putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "true")
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "TTS_ID")
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

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isSticky = prefs.getBoolean("overlay_sticky", false)

        if (!isSticky) {
            overlayView?.postDelayed({ removeOverlay() }, duration)
        }

        overlayView?.setOnClickListener { removeOverlay() }
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.isAttachedToWindow) windowManager?.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        removeOverlay()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("overlay_service", "Emotion Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "overlay_service")
            .setContentTitle("Emotional AAC")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}