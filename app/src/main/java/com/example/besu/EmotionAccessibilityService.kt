package com.example.besu

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
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

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
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
            mAbilityButtonController?.registerAccessibilityButtonCallback(
                it,
                Handler(Looper.getMainLooper())
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    private fun showRadialMenu() {
        val intent = Intent(this, RadialMenuService::class.java)
        // CRITICAL FIX FOR ANDROID 8+: Start as Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        mAccessibilityButtonCallback?.let {
            mAbilityButtonController?.unregisterAccessibilityButtonCallback(it)
        }
        instance = null
        super.onDestroy()
    }
}