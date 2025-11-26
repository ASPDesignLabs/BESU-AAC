package com.example.besu.wear

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import java.io.File

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var vibrator: Vibrator? = null

    // UI State
    private var isMotionEnabled by mutableStateOf(true)
    private var activeProfile by mutableStateOf(MotionProfileType.STANDARD)
    private var watchConfig by mutableStateOf(WatchConfig())

    // Persistence
    private val CONFIG_FILENAME = "watch_config.json"
    private val PROFILE_FILENAME = "motion_profile.json"
    private val PREFS_NAME = "besu_prefs"
    private val jsonHandler = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // --- MOTION ENGINES ---
    private var motionProfile = MotionProfile()
    private val liveBuffer = mutableListOf<MotionSample>()

    // --- PHYSICS VARS ---
    private var currentGyroX = 0f
    private var currentGyroY = 0f
    private var currentGyroZ = 0f
    private var lastY = 0f
    private var lastTwistTime = 0L
    private var twistCount = 0
    private var currentState = State.IDLE

    private enum class State { IDLE, LISTENING, ARM_UP, COOLDOWN }
    private var listeningStartTime = 0L
    private var cooldownStartTime = 0L
    private var commandTwistCount = 0
    private var lastCmdTwistDir = 0
    private var lastCmdTwistTime = 0L
    private var maxXInSequence = 0f
    private var stopEntryTime = 0L
    private var armUpStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Load Preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val profileName = prefs.getString("profile_type", MotionProfileType.STANDARD.name)
        try {
            activeProfile = MotionProfileType.valueOf(profileName ?: MotionProfileType.STANDARD.name)
        } catch(e:Exception) { activeProfile = MotionProfileType.STANDARD }

        loadConfigFromDisk()
        loadMotionProfile()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setContent {
            BesuWatchFace(
                isMotionEnabled = isMotionEnabled,
                activeProfile = activeProfile,
                customConfig = watchConfig,
                onToggleMotion = { enabled ->
                    isMotionEnabled = enabled
                    if (!enabled) resetState()
                },
                onSwitchProfile = {
                    toggleProfile()
                },
                onSendCommand = { path ->
                    sendToPhone(path, null)
                    vibrate(50)
                }
            )
        }
    }

    private fun toggleProfile() {
        activeProfile = if (activeProfile == MotionProfileType.STANDARD) {
            MotionProfileType.CUSTOM
        } else {
            MotionProfileType.STANDARD
        }

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("profile_type", activeProfile.name)
            .apply()

        val msg = if (activeProfile == MotionProfileType.CUSTOM) "Custom Profile Active" else "Standard Profile Active"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        vibrate(50); vibrate(50)

        if(activeProfile == MotionProfileType.CUSTOM) loadMotionProfile()
    }

    private fun loadConfigFromDisk() {
        try {
            val file = File(filesDir, CONFIG_FILENAME)
            if (file.exists()) watchConfig = jsonHandler.decodeFromString(file.readText())
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadMotionProfile() {
        try {
            val file = File(filesDir, PROFILE_FILENAME)
            if (file.exists()) motionProfile = jsonHandler.decodeFromString(file.readText())
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- SENSOR LOGIC ---
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMotionEnabled) return
        event ?: return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentGyroX = event.values[0]
            currentGyroY = event.values[1]
            currentGyroZ = event.values[2]

            // Standard Engine Helper
            if (activeProfile == MotionProfileType.STANDARD) {
                if (abs(currentGyroZ) > 3.0f && (currentState == State.LISTENING || currentState == State.ARM_UP)) {
                    val dir = if (currentGyroZ > 0) 1 else -1
                    val time = System.currentTimeMillis()
                    if (dir != lastCmdTwistDir) {
                        if (time - lastCmdTwistTime < 600) commandTwistCount++
                        else commandTwistCount = 1
                        lastCmdTwistDir = dir
                        lastCmdTwistTime = time
                    }
                }
            }
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val time = System.currentTimeMillis()

            if (activeProfile == MotionProfileType.CUSTOM) {
                // --- CUSTOM ENGINE ---
                if (currentState == State.LISTENING) {
                    liveBuffer.add(MotionSample(time, ax, ay, az, currentGyroX, currentGyroY, currentGyroZ))
                    if (time - listeningStartTime > 1500) {
                        attemptMotionMatch()
                        resetState()
                    }
                } else {
                    checkUnlock(ay, time)
                }
            } else {
                // --- STANDARD ENGINE ---
                runStandardEngine(ax, ay, time)
            }
        }
    }

    private fun checkUnlock(y: Float, time: Long) {
        if (time - lastTwistTime > 800) twistCount = 0
        if (abs(y - lastY) > 7.0f) {
            twistCount++
            lastTwistTime = time
            vibrate(20)
        }
        lastY = y
        if (twistCount >= 3) {
            currentState = State.LISTENING
            listeningStartTime = time
            twistCount = 0
            liveBuffer.clear()
            vibrate(100)
        }
    }

    private fun runStandardEngine(ax: Float, ay: Float, time: Long) {
        when (currentState) {
            State.IDLE -> checkUnlock(ay, time)
            State.LISTENING -> {
                if (time - listeningStartTime > 6000) resetState()
                if (ax > 6.5f) {
                    currentState = State.ARM_UP
                    armUpStartTime = time
                    maxXInSequence = ax
                    vibrate(50)
                }
            }
            State.ARM_UP -> {
                if (time - armUpStartTime > 4000) resetState()
                if (commandTwistCount >= 3) fireCommand("👋", time)
            }
            State.COOLDOWN -> {
                if (time - cooldownStartTime > 2000) resetState()
            }
        }
    }

    private fun attemptMotionMatch() {
        if (motionProfile.exemplars.isEmpty()) return
        val matchId = MotionMatchingEngine.match(liveBuffer, motionProfile)

        if (matchId != null && matchId != "NOISE") {
            fireCommand(matchId, System.currentTimeMillis())
        }
    }

    private fun fireCommand(type: String, time: Long) {
        val path = when(type) {
            "👋" -> "/gesture/wave"
            "wave" -> "/gesture/wave"
            "thumbs_up" -> "/gesture/thumbsup"
            "no" -> "/gesture/no"
            else -> "/gesture/thumbsup"
        }
        sendToPhone(path, null)
        vibrate(300)
        currentState = State.COOLDOWN
        cooldownStartTime = time
        commandTwistCount = 0
    }

    private fun resetState() {
        currentState = State.IDLE
        twistCount = 0
        commandTwistCount = 0
        liveBuffer.clear()
    }

    private fun vibrate(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
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

    // BROADCAST RECEIVERS
    private val localConfigReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra("config_json")
            if (json != null) {
                try {
                    watchConfig = jsonHandler.decodeFromString(json)
                    vibrate(200)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private val localProfileReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadMotionProfile()
            vibrate(50)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        Wearable.getMessageClient(this).addListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(localConfigReceiver, android.content.IntentFilter("BESU_WATCH_CONFIG_UPDATE"), RECEIVER_NOT_EXPORTED)
            registerReceiver(localProfileReceiver, android.content.IntentFilter("BESU_PROFILE_UPDATE"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(localConfigReceiver, android.content.IntentFilter("BESU_WATCH_CONFIG_UPDATE"))
            registerReceiver(localProfileReceiver, android.content.IntentFilter("BESU_PROFILE_UPDATE"))
        }

        loadConfigFromDisk()
        loadMotionProfile()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        unregisterReceiver(localConfigReceiver)
        unregisterReceiver(localProfileReceiver)
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/besu/config_push") {
            try {
                val json = String(event.data, Charsets.UTF_8)
                File(filesDir, CONFIG_FILENAME).writeText(json)
                watchConfig = jsonHandler.decodeFromString(json)
                vibrate(100)
                Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, "/besu/config_ack", null)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}