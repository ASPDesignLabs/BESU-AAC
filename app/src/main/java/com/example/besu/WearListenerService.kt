package com.example.besu

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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

        // 1. Debug Channel
        if (path == "/debug/sensor") {
            val debugMessage = messageEvent.data?.let { String(it, Charsets.UTF_8) } ?: "No data"
            broadcastLog(debugMessage)
            return
        }

        broadcastLog("RX CMD: $path")
        Log.d(TAG, "Received command: $path")

        // 2. Identify the content and the ID for the Brain
        val (emoji, phrase, id) = parseMessage(path, messageEvent.data)

        // 3. Trigger Action
        triggerPhrase(emoji, phrase)

        // 4. Teach the Brain
        // If we identified a valid ID, tell the engine we used it.
        if (id != null) {
            // We run this on the main thread or a background thread?
            // RecommendationEngine writes to disk, so let's be safe,
            // but for a simple service this is usually fine on the listener thread
            // as it's quick.
            try {
                RecommendationEngine.learnSentence(listOf(id))
                RecommendationEngine.persist(this)
                Log.d(TAG, "Brain updated with ID: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update brain", e)
            }
        }
    }

    private fun parseMessage(path: String, data: ByteArray?): Triple<String, String, String?> {
        // Returns Triple(Emoji, Phrase, ID?)

        // A. Handle System Gestures (Mapped to specific IDs in your JSON)
        if (path.startsWith("/gesture/")) {
            return when (path) {
                // Social
                "/gesture/name" -> {
                    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val name = prefs.getString("user_name", "")
                    val p = if (name.isNullOrEmpty()) "My name is..." else "My name is $name."
                    Triple("😀", p, "my_name")
                }
                "/gesture/ask_name" -> Triple("❓", "What is your name?", "question_name")

                // Refusal / Negation
                "/gesture/no" -> Triple("🚫", "No.", "no")
                "/gesture/hate" -> Triple("😠", "I hate this. Go away.", "hate")

                // Greeting
                "/gesture/wave" -> Triple("👋", "Hello.", "hello")
                "/gesture/goodbye" -> Triple("👋", "Goodbye. See you later.", "goodbye")

                // Command
                "/gesture/stop" -> Triple("✋", "Stop.", "stop")
                "/gesture/wait" -> Triple("⏳", "Please wait a moment.", "wait")

                // Affirmation
                "/gesture/thumbsup" -> Triple("👍", "Good.", "yes") // Mapping 'good' to 'yes' or 'good' ID
                "/gesture/thumbsdown" -> Triple("👎", "That is bad.", "bad")

                // Connection
                "/gesture/nice" -> Triple("🤝", "Nice to meet you.", "nice")
                "/gesture/same" -> Triple("✨", "Same here. Me too.", "same")

                else -> Triple("❓", "Unknown Gesture", null)
            }
        }

        // B. Handle Raw Text / Custom Sentences sent from Watch Grid
        // The path itself is the phrase usually, or we can send ID in the data payload if we get fancy later.
        // For now, if the watch sends a custom phrase, we don't have a strict ID,
        // unless we want to do a reverse lookup. We will return null for ID to be safe.
        else {
            return Triple("🗣️", path, null)
        }
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("BESU_WEAR_LOG")
        intent.setPackage(packageName)
        intent.putExtra("log_message", msg)
        sendBroadcast(intent)
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