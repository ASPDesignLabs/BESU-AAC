package com.example.besu

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class EmotionAccessibilityService : AccessibilityService() {

    private var mAbilityButtonController: AccessibilityButtonController? = null
    private var mAccessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    companion object {
        private const val TAG = "EmotionA11yService"
        var instance: EmotionAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        mAbilityButtonController = accessibilityButtonController

        // Check if the accessibility button is available
        if (mAbilityButtonController?.isAccessibilityButtonAvailable == false) {
            Log.d(TAG, "Accessibility button not available")
            // You might want to return here or handle it differently
        }

        val info = serviceInfo ?: AccessibilityServiceInfo() // Use existing info if available
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        // You may not need to set these again unless you are changing them
        // info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
        // info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

        serviceInfo = info
        Log.d(TAG, "Accessibility service connected")

        mAccessibilityButtonCallback =
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    Log.d(TAG, "Accessibility button pressed!")
                    showRadialMenu()
                }

                override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
                ) {
                    if (controller == mAbilityButtonController) {
                        Log.d(TAG, "Accessibility button availability changed to $available")
                    }
                }
            }

        mAccessibilityButtonCallback?.let {
            // FIX: Provide a Handler.
            // Callbacks will run on the main thread.
            mAbilityButtonController?.registerAccessibilityButtonCallback(
                it,
                Handler(Looper.getMainLooper())
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle regular accessibility events
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    private fun showRadialMenu() {
        val intent = Intent(this, RadialMenuService::class.java)
        startService(intent)
    }

    override fun onDestroy() {
        mAccessibilityButtonCallback?.let {
            mAbilityButtonController?.unregisterAccessibilityButtonCallback(it)
        }
        instance = null
        super.onDestroy()
    }
}
