package com.example.besu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var logView: TextView? = null

    // 1. The Receiver: Catches "Broadcasts" from the Service
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("log_message") ?: return
            appendLog(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check setup status
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("setup_complete", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Setup UI refs
        logView = findViewById(R.id.debugLogView)

        setupButtons()
        setupTtsSwitch(prefs) // New TTS Logic
        updateStatus()

        // 2. Register the Receiver
        val filter = IntentFilter("BESU_WEAR_LOG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(debugReceiver, filter)
        }

        appendLog("System Ready. Waiting for Watch...")
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnTestMenu).setOnClickListener {
            if (checkOverlayPermission()) {
                startService(Intent(this, RadialMenuService::class.java))
            } else {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTtsSwitch(prefs: android.content.SharedPreferences) {
        val ttsSwitch = findViewById<SwitchMaterial>(R.id.switchTTS)

        // Set initial state (Default to True if not set)
        val isEnabled = prefs.getBoolean("tts_enabled", true)
        ttsSwitch.isChecked = isEnabled

        // Save state on change
        ttsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tts_enabled", isChecked).apply()
            val status = if (isChecked) "Enabled" else "Disabled"
            appendLog("Voice (TTS) $status")
        }
    }

    // 3. Helper to print to screen
    private fun appendLog(text: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentText = logView?.text.toString()
        logView?.text = "[$timestamp] $text\n$currentText"
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        unregisterReceiver(debugReceiver)
        super.onDestroy()
    }

    private fun updateStatus() {
        val overlayGranted = checkOverlayPermission()
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        findViewById<TextView>(R.id.tvOverlayStatus).text =
            if (overlayGranted) "✓ Overlay permission granted"
            else "✗ Overlay permission needed"

        findViewById<TextView>(R.id.tvAccessibilityStatus).text =
            if (accessibilityEnabled) "✓ Accessibility service enabled"
            else "✗ Accessibility service needed"
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/.EmotionAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }
}