package com.example.besu.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlin.math.abs

class WearGestureService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastRemoteLogTime = 0L
    private var stabilityCounter = 0
    private var lastTriggerTime = 0L
    private val triggerCooldown = 2000L

    override fun onCreate() {
        super.onCreate()

        // 1. SAFETY NET: Try to start foreground, catch the crash if manifest is wrong
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Specify type in code too if possible, or fall back to manifest
                startForeground(999, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(999, notification)
            }
            sendToPhone("/debug/sensor", "Service Started & Foreground Active")
        } catch (e: Exception) {
            Log.e("BesuWear", "CRASH STARTING FOREGROUND", e)
            sendToPhone("/debug/sensor", "CRASH: ${e.message}")
        }

        // 2. Check Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            sendToPhone("/debug/sensor", "CRITICAL ERROR: No Accelerometer Found!")
        } else {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sendToPhone("/debug/sensor", "Sensor Registered. Waiting for data...")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val currentTime = System.currentTimeMillis()

        // PIPE DATA TO PHONE every 0.5 seconds
        if (currentTime - lastRemoteLogTime > 500) {
            // Send pure data so we can debug math
            val msg = "X:${"%.1f".format(x)} Y:${"%.1f".format(y)} Z:${"%.1f".format(z)}"
            sendToPhone("/debug/sensor", msg)
            lastRemoteLogTime = currentTime
        }

        // THUMB UP LOGIC (Simple Version)
        // Left Wrist Thumb Up = 9 o'clock (Negative X) down towards gravity
        // Right Wrist Thumb Up = 3 o'clock (Positive X) down towards gravity
        // We check for EITHER strong gravity pull on X axis
        if (abs(x) > 7.0f && abs(y) < 5.0f) {
            stabilityCounter++
        } else {
            stabilityCounter = 0
        }

        if (stabilityCounter > 40 && (currentTime - lastTriggerTime > triggerCooldown)) {
            sendToPhone("/gesture/thumbsup", null)
            stabilityCounter = 0
            lastTriggerTime = currentTime
        }
    }

    private fun sendToPhone(path: String, message: String?) {
        val payload = message?.toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, path, payload)
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "besu_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Gestures", NotificationManager.IMPORTANCE_LOW)
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(chan)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
        return builder.setContentTitle("BESU").setContentText("Sensors Active").setSmallIcon(android.R.drawable.btn_star).build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}