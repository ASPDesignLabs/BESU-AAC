package com.example.besu

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "BesuWearListener"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        val path = messageEvent.path

        // 1. FILTER: Ignore System channels
        if (path.startsWith("/besu/") || path.startsWith("/debug/")) {
            return
        }

        Log.d(TAG, "Received command: $path")

        // 2. Identify the content
        val result = parseMessage(path)

        if (result != null) {
            val (emoji, phrase, id) = result

            if (id != null) {
                // INTELLIGENT MODE: Buffer ID for chaining
                PredictionEngine.postCommand(id, phrase) { finalPhrase ->
                    triggerPhrase(emoji, finalPhrase)
                }
            } else {
                // Direct Text / Unknown ID -> Speak Immediately
                triggerPhrase(emoji, phrase)
            }
        }
    }

    private fun parseMessage(path: String): Triple<String, String, String?>? {
        if (path.startsWith("/gesture/")) {
            return when (path) {
                // --- SOCIAL / HANDSHAKE ---
                "/gesture/nice" -> Triple("🤝", "Nice to meet you.", "nice")
                "/gesture/meet_pleasure" -> Triple("✨", "It was a pleasure meeting you.", "pleasure_meet")
                "/gesture/meet_very_nice" -> Triple("👋", "Very nice to see you.", "see_you")
                "/gesture/sorry_wait" -> Triple("😅", "Sorry I kept you waiting.", "sorry_wait")
                "/gesture/thanks" -> Triple("🙏", "Thank you.", "thanks")
                "/gesture/same" -> Triple("✨", "Same here. Me too.", "same")

                // --- IDENTITY ---
                "/gesture/name" -> {
                    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val name = prefs.getString("user_name", "")
                    val p = if (name.isNullOrEmpty()) "My name is..." else "My name is $name."
                    Triple("😀", p, "my_name")
                }
                "/gesture/ask_name" -> Triple("❓", "What is your name?", "question_name")

                // --- UTILITY / STOP LADDER ---
                "/gesture/stop" -> Triple("✋", "Stop.", "stop")
                "/gesture/wait" -> Triple("⏳", "Please wait a moment.", "wait")
                "/gesture/break" -> Triple("⏸️", "I need a break.", "need_break")
                "/gesture/leave_alone" -> Triple("🛑", "Please leave me alone.", "leave_alone")

                // --- GREETING ---
                "/gesture/wave" -> Triple("👋", "Hello.", "hello")
                "/gesture/thumbsup" -> Triple("👍", "Good.", "yes")

                else -> Triple("❓", "Unknown Gesture", null)
            }
        }

        // Handle Direct Text
        if (path.startsWith("/")) {
            val cleanText = path.substring(1)
            return Triple("🗣️", cleanText, null)
        }

        return null
    }

    private fun triggerPhrase(emoji: String, phrase: String) {
        if (phrase.isEmpty()) return

        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emoji)
        intent.putExtra("phrase", phrase)
        intent.putExtra("duration", 4000L)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}