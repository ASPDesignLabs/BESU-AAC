package com.example.besu

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        val path = messageEvent.path

        if (path == "/debug/sensor") {
            // Extract text payload
            val debugMessage = messageEvent.data?.let { String(it, Charsets.UTF_8) } ?: "No data"
            broadcastLog(debugMessage) // Just print the raw numbers
        } else {
            // Handle normal gestures
            broadcastLog("RX: $path")

            when (path) {
                "/gesture/wave" -> triggerOverlay("👋")
                "/gesture/thumbsup" -> triggerOverlay("👍")
                // ADD THESE:
                "/gesture/stop" -> triggerOverlay("✋")
                "/gesture/no" -> triggerOverlay("🚫")

                "/gesture/test_ping" -> broadcastLog("✅ PING RECEIVED")
            }
        }
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("BESU_WEAR_LOG")
        intent.setPackage(packageName)
        intent.putExtra("log_message", msg)
        sendBroadcast(intent)
    }

    private fun triggerOverlay(emoji: String) {
        broadcastLog("TRIGGERING: $emoji")
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emoji)
        intent.putExtra("duration", 4000L)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startForegroundService(intent)
    }
}