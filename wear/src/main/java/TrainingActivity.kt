package com.example.besu.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

class TrainingActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var vibrator: Vibrator? = null

    // UI State
    private var statusText by mutableStateOf("Ready")
    private var isRecording by mutableStateOf(false)

    // Data State
    private val buffer = mutableListOf<MotionSample>()
    private var mode = TrainingMode.GESTURE
    private var label = "unknown"
    private var maxEnergy = 0f

    // Receiver for remote STOP command
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isRecording) {
                stopAndProcess()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Register Stop Receiver
        val filter = IntentFilter("BESU_REMOTE_STOP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }

        if (intent.hasExtra("MODE_JSON")) {
            val json = intent.getStringExtra("MODE_JSON") ?: ""
            try {
                val req = Json.decodeFromString<TrainingRequest>(json)
                mode = req.mode
                label = req.label
                // Start immediately
                android.os.Handler(mainLooper).postDelayed({ startRecording() }, 500)
            } catch (e: Exception) { e.printStackTrace() }
        }

        setContent {
            TrainingScreenUI(
                status = statusText,
                isRecording = isRecording,
                onManualStop = { stopAndProcess() }
            )
        }
    }

    private fun startRecording() {
        buffer.clear()
        maxEnergy = 0f
        isRecording = true
        statusText = "Recording: $label..."
        vibrate(200)

        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        // No auto-stop timer anymore!
    }

    private fun stopAndProcess() {
        if (!isRecording) return

        sensorManager.unregisterListener(this)
        isRecording = false
        statusText = "Saving..."
        vibrate(50); vibrate(50)

        processData()
    }

    private fun processData() {
        val file = File(filesDir, "motion_profile.json")
        val profile = if (file.exists()) Json.decodeFromString<MotionProfile>(file.readText()) else MotionProfile()

        when (mode) {
            TrainingMode.GESTURE -> {
                val exemplar = GestureExemplar(label, buffer.toList())
                profile.exemplars.add(exemplar)
            }
            TrainingMode.NOISE -> {
                val exemplar = GestureExemplar("NOISE", buffer.toList())
                profile.exemplars.add(exemplar)
            }
            TrainingMode.GROSS_MOTOR -> {
                profile.panicThreshold = maxEnergy
            }
        }

        file.writeText(Json.encodeToString(profile))

        // Notify Phone
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, "/besu/train_result", "OK".toByteArray())
            }
        }

        sendBroadcast(Intent("BESU_PROFILE_UPDATE"))
        android.os.Handler(mainLooper).postDelayed({ finish() }, 1000)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording) return
        event ?: return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val energy = abs(ax) + abs(ay) + abs(az)
            if (energy > maxEnergy) maxEnergy = energy

            buffer.add(MotionSample(System.currentTimeMillis(), ax, ay, az, 0f, 0f, 0f))
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopReceiver) } catch(e:Exception){}
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun vibrate(ms: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

@Composable
fun TrainingScreenUI(status: String, isRecording: Boolean, onManualStop: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(if (isRecording) Color(0xFF550000) else Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isRecording) "REC" else "Saved", style = MaterialTheme.typography.title1, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(status, color = Color.LightGray)

            if (isRecording) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onManualStop, colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)) {
                    Text("STOP")
                }
            }
        }
    }
}