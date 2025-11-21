package com.example.besu

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityGestureService : AccessibilityService() {
    private var tapCount = 0
    private var lastTapTime = 0L
    private val tapTimeout = 500L // Max time between taps (ms)
    private val requiredTaps = 3

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service is ready
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We'll detect taps through a different mechanism
        // This is required but we won't use it for tap detection
    }

    override fun onInterrupt() {
        // Required override
    }

    // Detect taps globally
    override fun onGesture(gestureId: Int): Boolean {
        // Note: We need to use a workaround for tap detection
        // AccessibilityService doesn't directly expose tap events
        return super.onGesture(gestureId)
    }

    // Alternative: Monitor touch events through window changes
    private fun detectTap() {
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
        intent.putExtra("duration", 5000L) // 5 seconds
        startService(intent)
    }
}