package com.example.besu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class AccelerometerTapService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var tapCount = 0
    private var lastTapTime = 0L
    private val tapTimeout = 800L // Slightly longer window for deliberate taps
    private val requiredTaps = 3

    // Detection thresholds - much more sensitive!
    private val tapThreshold = 6f // Changed from 15f
    private var lastZ = 0f
    private var eventCount = 0

    companion object {
        private const val TAG = "AccelTapService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== Service onCreate called ===")
        createNotificationChannel()
        startForeground(3, createNotification())
        setupAccelerometer()
    }

    private fun setupAccelerometer() {
        Log.d(TAG, "Setting up accelerometer...")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.e(TAG, "ERROR: Accelerometer not available!")
            return
        }

        Log.d(TAG, "Accelerometer found: ${accelerometer?.name}")

        val registered = sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        ) ?: false

        if (registered) {
            Log.d(TAG, "SUCCESS: Accelerometer listener registered")
        } else {
            Log.e(TAG, "ERROR: Failed to register listener")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        eventCount++

        if (eventCount % 100 == 0) {
            Log.d(TAG, "Sensor events: $eventCount")
        }

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            detectPhysicalTap(event)
        }
    }

    private fun detectPhysicalTap(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Focus on Z-axis (perpendicular to screen)
        // This is most sensitive to tapping the back/bottom of device
        val zDelta = abs(z - lastZ)

        // Also track overall movement for context
        val xDelta = abs(x)
        val yDelta = abs(y)

        // Log movements for fine-tuning
        if (zDelta > 3f) {
            Log.d(TAG, "Z-movement: ${"%.2f".format(zDelta)}, " +
                    "X: ${"%.2f".format(xDelta)}, " +
                    "Y: ${"%.2f".format(yDelta)}, " +
                    "Z: ${"%.2f".format(z)}")
        }

        // Detect tap: significant Z-axis spike with relatively stable X/Y
        // This distinguishes taps from general movement/shaking
        if (zDelta > tapThreshold) {
            Log.d(TAG, "*** TAP DETECTED! Z-Delta: ${"%.2f".format(zDelta)} ***")
            handleTap()
        }

        lastZ = z
    }

    private fun handleTap() {
        val currentTime = SystemClock.uptimeMillis()

        if (currentTime - lastTapTime <= tapTimeout) {
            tapCount++
            Log.d(TAG, ">>> Tap $tapCount of $requiredTaps <<<")
        } else {
            tapCount = 1
            Log.d(TAG, ">>> New tap sequence started <<<")
        }

        lastTapTime = currentTime

        if (tapCount >= requiredTaps) {
            Log.d(TAG, "!!! TRIPLE TAP COMPLETE - SHOWING THUMBS UP !!!")
            tapCount = 0
            triggerOverlay()
        }
    }

    private fun triggerOverlay() {
        val prefs = getSharedPreferences("gestures", Context.MODE_PRIVATE)
        val emotion = prefs.getString("device_triple_tap", "👍") ?: "👍"

        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emotion)
        intent.putExtra("duration", 5000L)
        startService(intent)

        Log.d(TAG, "Overlay triggered!")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Optional logging
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "accelerometer_service",
                "Device Tap Detection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "accelerometer_service")
            .setContentTitle("Physical Tap Detection Active")
            .setContentText("Triple-tap device back/bottom for 👍")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "=== Service stopped ===")
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }
}