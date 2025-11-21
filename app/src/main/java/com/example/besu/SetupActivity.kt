// Notice the semicolon at the end of this line
package com.example.besu;

import android.content.Context;
import android.content.Intent; // You were missing this import
import android.net.Uri;
import android.os.Build;
import android.os.Bundle; // You were missing this import
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity; // You were missing this import

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        updatePermissionStatus()

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnComplete).setOnClickListener {
            completeSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val overlayGranted = checkOverlayPermission()

        findViewById<TextView>(R.id.tvOverlayStatus).text =
            if (overlayGranted) "✓ Overlay permission granted"
            else "✗ Overlay permission needed"

        findViewById<Button>(R.id.btnGrantOverlay).isEnabled = !overlayGranted
        findViewById<Button>(R.id.btnComplete).isEnabled = overlayGranted
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun completeSetup() {
        // Mark setup as complete
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("setup_complete", true).apply()

        // Initialize default gestures
        val gesturePref = getSharedPreferences("gestures", Context.MODE_PRIVATE)
        gesturePref.edit().apply {
            putString("triple_tap", "😊")
            putString("device_triple_tap", "👍")
            apply()
        }

        // Start both detection services
        // Note: You need to create these service files (TapDetectionService.kt, AccelerometerTapService.kt)
        // startService(Intent(this, TapDetectionService::class.java))
        // startService(Intent(this, AccelerometerTapService::class.java))

        // Mark services as running
        prefs.edit().putBoolean("service_running", true).apply()

        // Go to main activity
        // Note: You need to create MainActivity and its layout file
        // startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
