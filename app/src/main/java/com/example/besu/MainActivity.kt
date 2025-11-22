package com.example.besu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var logView: TextView? = null

    // Receiver to catch logs from background services
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("log_message") ?: return
            appendLog(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial Setup Check
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("setup_complete", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.debugLogView)

        setupButtons()
        setupSwitches(prefs)
        setupNameInput(prefs) // Name saving logic
        updateStatus()

        // Register Debug Receiver
        val filter = IntentFilter("BESU_WEAR_LOG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(debugReceiver, filter)
        }

        appendLog("System Ready. Waiting for Watch...")
    }

    private fun setupNameInput(prefs: android.content.SharedPreferences) {
        val etName = findViewById<TextInputEditText>(R.id.etUserName)
        val btnSave = findViewById<Button>(R.id.btnSaveName)

        // 1. Load existing name
        val currentName = prefs.getString("user_name", "")
        etName.setText(currentName)

        // 2. Save on Button Click
        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()

            if (newName.isNotEmpty()) {
                prefs.edit().putString("user_name", newName).apply()

                // Visual Confirmation
                Toast.makeText(this, "Name saved: $newName", Toast.LENGTH_SHORT).show()
                appendLog("User name updated to: $newName")

                // Hide Keyboard
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etName.windowToken, 0)

                // Clear focus to stop cursor blinking
                etName.clearFocus()
            } else {
                Toast.makeText(this, "Please enter a name first", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun setupSwitches(prefs: android.content.SharedPreferences) {
        // 1. Voice (TTS) Switch
        val ttsSwitch = findViewById<SwitchMaterial>(R.id.switchTTS)
        ttsSwitch.isChecked = prefs.getBoolean("tts_enabled", true)
        ttsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tts_enabled", isChecked).apply()
            val status = if (isChecked) "Enabled" else "Disabled"
            appendLog("Voice (TTS) $status")
        }

        // 2. Visual Overlay Switch
        val visualSwitch = findViewById<SwitchMaterial>(R.id.switchOverlayVisuals)
        visualSwitch.isChecked = prefs.getBoolean("overlay_visuals", true)
        visualSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overlay_visuals", isChecked).apply()
            val status = if (isChecked) "Visible" else "Hidden"
            appendLog("Visual Overlay $status")
        }

        // 3. Sticky Overlay Switch
        val stickyOverlaySwitch = findViewById<SwitchMaterial>(R.id.switchOverlaySticky)
        stickyOverlaySwitch.isChecked = prefs.getBoolean("overlay_sticky", false)
        stickyOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overlay_sticky", isChecked).apply()
            val status = if (isChecked) "ON (Tap to dismiss)" else "OFF (Auto-dismiss)"
            appendLog("Sticky Overlay $status")
        }

        // 4. Sticky Menu Switch
        val stickyMenuSwitch = findViewById<SwitchMaterial>(R.id.switchMenuSticky)
        stickyMenuSwitch.isChecked = prefs.getBoolean("menu_sticky", false)
        stickyMenuSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("menu_sticky", isChecked).apply()
            val status = if (isChecked) "ON (Multi-select)" else "OFF (One-shot)"
            appendLog("Sticky Menu $status")
        }
    }

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
        try {
            unregisterReceiver(debugReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver might not have been registered if we finished early
        }
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