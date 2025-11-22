package com.example.besu

import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        val path = messageEvent.path

        if (path == "/debug/sensor") {
            val debugMessage = messageEvent.data?.let { String(it, Charsets.UTF_8) } ?: "No data"
            broadcastLog(debugMessage)
        } else {
            broadcastLog("RX: $path")

            when (path) {
                // Core Emotions
                "/gesture/wave" -> triggerOverlay("👋")
                "/gesture/thumbsup" -> triggerOverlay("👍")
                "/gesture/stop" -> triggerOverlay("✋")
                "/gesture/no" -> triggerOverlay("🚫")

                // Social / Name
                "/gesture/nice" -> triggerPhrase("🤝", "Nice to meet you.")
                "/gesture/name" -> triggerNameIntro()

                "/gesture/test_ping" -> broadcastLog("✅ PING RECEIVED")
            }
        }
    }

    private fun triggerNameIntro() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // Fetch the name saved in MainActivity
        val name = prefs.getString("user_name", "")

        val phrase = if (name.isNullOrEmpty()) {
            "My name is..."
        } else {
            "My name is $name."
        }

        triggerPhrase("😀", phrase)
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("BESU_WEAR_LOG")
        intent.setPackage(packageName)
        intent.putExtra("log_message", msg)
        sendBroadcast(intent)
    }

    private fun triggerOverlay(emoji: String) {
        // Lookup phrase from data file if not explicitly provided
        val phrase = CommunicationData.getPhraseForEmoji(emoji)
        triggerPhrase(emoji, phrase)
    }

    private fun triggerPhrase(emoji: String, phrase: String) {
        broadcastLog("TRIGGERING: $phrase")

        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emoji)
        intent.putExtra("phrase", phrase)
        intent.putExtra("duration", 4000L)

        // Required because we are starting from a background service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startForegroundService(intent)
    }
}