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

class AccessibilityGestureService : AccessibilityService() {

    private var mAbilityButtonController: AccessibilityButtonController? = null
    private var mAccessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    companion object {
        private const val TAG = "BesuA11y"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 1. Get the Controller
        mAbilityButtonController = accessibilityButtonController

        // 2. Request the Flag via Code (Double check manifest matches, but this enforces it)
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        serviceInfo = info

        // 3. Define the Callback
        mAccessibilityButtonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                Log.d(TAG, "Accessibility button pressed! Launching Menu...")
                launchRadialMenu()
            }

            override fun onAvailabilityChanged(controller: AccessibilityButtonController, available: Boolean) {
                Log.d(TAG, "Button availability: $available")
            }
        }

        // 4. Register the Callback
        mAccessibilityButtonCallback?.let {
            mAbilityButtonController?.registerAccessibilityButtonCallback(it, Handler(Looper.getMainLooper()))
        }

        Log.d(TAG, "Service Connected & Button Callback Registered")
    }

    private fun launchRadialMenu() {
        val intent = Intent(this, RadialMenuService::class.java)
        // Ensure we use the correct start command based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, but required
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onDestroy() {
        // Unregister to prevent leaks
        mAccessibilityButtonCallback?.let {
            mAbilityButtonController?.unregisterAccessibilityButtonCallback(it)
        }
        super.onDestroy()
    }
}