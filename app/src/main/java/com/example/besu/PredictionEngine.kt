package com.example.besu

import android.content.Context
import android.os.Handler
import android.os.Looper

object PredictionEngine {
    private val buffer = mutableListOf<String>()
    private val bufferLabels = mutableListOf<String>()
    private var handler = Handler(Looper.getMainLooper())

    private val combinations = mapOf(
        "hello|my_name" to "Hello there, my name is...",
        "hello|nice" to "Hello, it is very nice to meet you.",
        "stop|nice" to "I cannot talk right now, but it was nice meeting you.",
        "stop|leave_alone" to "Please stop and leave me alone immediately.",
        "wait|need_break" to "Please wait a moment, I am overwhelmed and need a break.",
        "nice|thanks" to "Nice to meet you, and thank you.",
        "pleasure_meet|see_you" to "It was a pleasure, I hope to see you again."
    )

    fun postCommand(id: String, label: String, onExecute: (String) -> Unit) {
        handler.removeCallbacksAndMessages(null)
        buffer.add(id)

        // Clean up label (remove leading slash if present)
        val cleanLabel = if (label.startsWith("/")) label.substring(1) else label
        bufferLabels.add(cleanLabel)

        handler.postDelayed({
            executeChain(onExecute)
        }, 1500L)
    }

    private fun executeChain(onExecute: (String) -> Unit) {
        if (buffer.isEmpty()) return

        val key = buffer.joinToString("|")
        val smartResponse = combinations[key]

        if (smartResponse != null) {
            onExecute(smartResponse)
        } else {
            // FIX: Just join the phrases naturally with a period
            val chainedResponse = bufferLabels.joinToString(". ")
            onExecute(chainedResponse)
        }

        buffer.clear()
        bufferLabels.clear()
    }
}