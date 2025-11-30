package com.example.besu.wear

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max
import java.io.File

// --- DEBUG DATA STRUCTURES ---
@Serializable
data class DebugSample(
    val t: Long,
    val ax: Float, val ay: Float, val az: Float,
    val gx: Float, val gy: Float, val gz: Float
)
@Serializable
data class DebugBatch(val samples: List<DebugSample>)

// UI State
data class SensorUiState(
    val ax: Float = 0f, val ay: Float = 0f, val az: Float = 0f,
    val gx: Float = 0f, val gy: Float = 0f, val gz: Float = 0f,
    val pose: String = "NONE"
)

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioManager: AudioManager? = null

    // UI State
    private var isMotionEnabled by mutableStateOf(true)
    private var isConversationMode by mutableStateOf(false) // NEW: Dedicated Chaining Mode
    private var isDebugStreaming by mutableStateOf(false)
    private var watchConfig by mutableStateOf(WatchConfig())
    private var sensorUiState by mutableStateOf(SensorUiState())

    // Debug Buffering
    private val debugBuffer = mutableListOf<DebugSample>()

    // Persistence
    private val CONFIG_FILENAME = "watch_config.json"
    private val jsonHandler = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // --- PHYSICS VARS ---
    private var currentGyroX = 0f
    private var currentGyroY = 0f
    private var currentGyroZ = 0f

    // Unlock / Modifier Logic
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastTwistTime = 0L
    private var twistCount = 0
    private var lastModifierTime = 0L

    // THRESHOLDS
    private val UNLOCK_TIMEOUT = 1200L
    private val ACCEL_TWIST_THRESHOLD = 6.0f

    // Polling Rates
    private val IDLE_POLLING_RATE = SensorManager.SENSOR_DELAY_UI
    private val ACTIVE_POLLING_RATE = SensorManager.SENSOR_DELAY_GAME

    // STATE MACHINE
    private enum class State {
        IDLE,
        GATE_UNLOCK,
        LISTENING,
        HANDSHAKE_HOLD,
        GATE_POSE,
        EVALUATING,
        CHAINING_WAIT, // Waits for arm reset between chained commands
        COOLDOWN
    }
    private var currentState = State.IDLE

    // Timings
    private var stateStartTime = 0L
    private val GATE_UNLOCK_DURATION = 600L
    private val GATE_POSE_DURATION = 400L
    private val LISTENING_WINDOW = 6000L
    private val EVAL_WINDOW = 6000L
    private val COOLDOWN_TIME = 2000L

    // HOLD TIMERS
    private val POSE_HOLD_TIME = 750L
    private val MODIFIER_GATE_TIME = 500L
    private val HANDSHAKE_AUTO_FIRE_TIME = 1200L

    // Synthesis Engine Vars
    private var commandTwistCount = 0
    private var maxXInSequence = 0f

    // Pose Identification
    private enum class DetectedPose { NONE, ARM_UP, STOP, HANDSHAKE }
    private var currentPose = DetectedPose.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        loadConfigFromDisk()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        } catch (e: Exception) { e.printStackTrace() }

        setContent {
            BesuWatchFace(
                isMotionEnabled = isMotionEnabled,
                isConversationMode = isConversationMode, // Pass state
                customConfig = watchConfig,
                sensorState = sensorUiState,
                onToggleMotion = { enabled ->
                    isMotionEnabled = enabled
                    if (!enabled) {
                        resetState()
                        updateSensorRate(false)
                    } else {
                        updateSensorRate(true)
                    }
                },
                // Double Tap to toggle Conversation Mode
                onDoubleTap = {
                    isConversationMode = !isConversationMode
                    val type = if(isConversationMode) FeedbackType.ACTION else FeedbackType.UNLOCK
                    feedback(type)
                },
                onToggleDebug = {
                    isDebugStreaming = !isDebugStreaming
                    feedback(if(isDebugStreaming) FeedbackType.MODIFIER else FeedbackType.UNLOCK)
                },
                onSendCommand = { path -> sendToPhone(path, null); feedback(FeedbackType.ACTION) }
            )
        }
    }

    override fun onDestroy() {
        toneGenerator?.release()
        super.onDestroy()
    }

    private fun updateSensorRate(enable: Boolean) {
        sensorManager.unregisterListener(this)
        if (!enable) return
        // Force Active Rate if in Conversation Mode to catch rapid chains
        val rate = if (isDebugStreaming || isConversationMode || currentState != State.IDLE) ACTIVE_POLLING_RATE else IDLE_POLLING_RATE
        accelerometer?.let { sensorManager.registerListener(this, it, rate) }
        gyroscope?.let { sensorManager.registerListener(this, it, rate) }
    }

    private fun transitionState(newState: State) {
        val oldState = currentState
        currentState = newState
        stateStartTime = System.currentTimeMillis()
        // If entering Idle while Convo mode is ON, we might want to lower rate,
        // but let's keep it responsive for now.
        if ((oldState == State.IDLE && newState != State.IDLE) || (oldState != State.IDLE && newState == State.IDLE)) {
            updateSensorRate(true)
        }
    }

    private fun loadConfigFromDisk() {
        try {
            val file = File(filesDir, CONFIG_FILENAME)
            if (file.exists()) watchConfig = jsonHandler.decodeFromString(file.readText())
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMotionEnabled) return
        event ?: return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentGyroX = event.values[0]
            currentGyroY = event.values[1]
            currentGyroZ = event.values[2]
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val time = System.currentTimeMillis()

            // Update UI State
            sensorUiState = SensorUiState(
                x, y, z,
                currentGyroX, currentGyroY, currentGyroZ,
                "${currentState.name}\n${currentPose.name}\nC: $isConversationMode"
            )

            if (isDebugStreaming) {
                debugBuffer.add(DebugSample(time, x, y, z, currentGyroX, currentGyroY, currentGyroZ))
                if (debugBuffer.size >= 10) sendDebugBatch()
            }

            // OMNI-DIRECTIONAL TWIST CHECKER
            val deltaX = abs(x - lastX)
            val deltaY = abs(y - lastY)
            val deltaZ = abs(z - lastZ)
            val isTwist = max(deltaX, max(deltaY, deltaZ)) > ACCEL_TWIST_THRESHOLD

            lastX = x
            lastY = y
            lastZ = z

            when (currentState) {
                State.IDLE -> checkUnlock(isTwist, time)
                State.GATE_UNLOCK -> checkGateUnlock(time)
                State.LISTENING -> checkPoseEntry(x, y, z, time)
                State.HANDSHAKE_HOLD -> checkHandshakeHold(x, y, z, time)
                State.GATE_POSE -> checkGatePose(time)
                State.EVALUATING -> {
                    checkModifiers(isTwist, time)
                    checkGestures(x, y, z, time)
                }
                State.CHAINING_WAIT -> checkChainingWait(x, y, z, time)
                State.COOLDOWN -> checkCooldown(time)
            }
        }
    }

    private fun sendDebugBatch() {
        try {
            val batch = DebugBatch(debugBuffer.toList())
            val json = jsonHandler.encodeToString(batch)
            sendToPhone("/besu/sensor_debug", json)
            debugBuffer.clear()
        } catch(e: Exception) { e.printStackTrace() }
    }

    // --- PHYSICS LOGIC ---

    // 1. IDLE
    private fun checkUnlock(isTwist: Boolean, time: Long) {
        if (time - lastTwistTime > UNLOCK_TIMEOUT) twistCount = 0
        if (isTwist) {
            twistCount++
            lastTwistTime = time
            if(twistCount < 3) vibrate(10)
        }
        if (twistCount >= 3) {
            feedback(FeedbackType.UNLOCK)
            transitionState(State.GATE_UNLOCK)
        }
    }

    // 2. GATE UNLOCK
    private fun checkGateUnlock(time: Long) {
        if (time - stateStartTime > GATE_UNLOCK_DURATION) {
            commandTwistCount = 0
            maxXInSequence = 0f
            currentPose = DetectedPose.NONE
            transitionState(State.LISTENING)
        }
    }

    // 3. LISTENING
    private fun checkPoseEntry(x: Float, y: Float, z: Float, time: Long) {
        if (time - stateStartTime > LISTENING_WINDOW) { resetState(); return }

        // Pose A: Arm Up
        if (x > 6.0f) {
            currentPose = DetectedPose.ARM_UP
            maxXInSequence = x
            feedback(FeedbackType.POSE_ENTRY)
            transitionState(State.GATE_POSE)
            return
        }

        // Pose B: STOP (Face Up)
        if (z > 8.5f && y > 2.0f && y < 8.0f && abs(x) < 5.0f) {
            currentPose = DetectedPose.STOP
            feedback(FeedbackType.POSE_ENTRY)
            transitionState(State.GATE_POSE)
            return
        }

        // Pose C: HANDSHAKE (Face Sideways)
        if (y < -5.0f && abs(z) < 2.5f) {
            currentPose = DetectedPose.HANDSHAKE
            feedback(FeedbackType.POSE_ENTRY)
            transitionState(State.HANDSHAKE_HOLD)
            return
        }
    }

    // 3b. HANDSHAKE HOLD
    private fun checkHandshakeHold(x: Float, y: Float, z: Float, time: Long) {
        // 1. AUTO-FIRE
        if (time - stateStartTime > HANDSHAKE_AUTO_FIRE_TIME) {
            fireCommand("NICE", time)
            return
        }

        // 2. INTERRUPT (Return to Neutral)
        if (z > 8.5f) {
            feedback(FeedbackType.POSE_ENTRY)
            transitionState(State.GATE_POSE)
            return
        }
    }

    // 4. GATE POSE
    private fun checkGatePose(time: Long) {
        if (time - stateStartTime > GATE_POSE_DURATION) {
            commandTwistCount = 0
            transitionState(State.EVALUATING)
        }
    }

    // MODIFIER
    private fun checkModifiers(isTwist: Boolean, time: Long) {
        if (isTwist) {
            if (time - lastModifierTime > 400) {
                commandTwistCount++
                lastModifierTime = time
                feedback(FeedbackType.MODIFIER)
                stateStartTime = time
            }
        }
    }

    // 5. EVALUATING
    private fun checkGestures(x: Float, y: Float, z: Float, time: Long) {
        val holdDuration = time - stateStartTime

        if (holdDuration > EVAL_WINDOW) { resetState(); return }
        if (x > maxXInSequence) maxXInSequence = x

        // Safety disabled here to allow flexible movement

        val isSettled = (time - lastModifierTime > MODIFIER_GATE_TIME)

        if (holdDuration > POSE_HOLD_TIME && isSettled) {
            when (currentPose) {
                DetectedPose.ARM_UP -> evaluateArmUp(z, time)
                DetectedPose.STOP -> evaluateStop(time)
                DetectedPose.HANDSHAKE -> evaluateHandshake(time)
                else -> resetState()
            }
        }
    }

    private fun evaluateArmUp(z: Float, time: Long) {
        if (commandTwistCount > 3) {
            fireCommand("👋", time)
            return
        }
        when (commandTwistCount) {
            0 -> { if (z > 2.0f) fireCommand("👍", time) }
            1 -> fireCommand("👋", time)
            2 -> fireCommand("ASK_NAME", time)
            3 -> fireCommand("NAME", time)
        }
    }

    private fun evaluateStop(time: Long) {
        when (commandTwistCount) {
            0 -> fireCommand("✋", time)
            1 -> fireCommand("WAIT", time)
            2 -> fireCommand("BREAK", time)
            3 -> fireCommand("LEAVE", time)
        }
    }

    private fun evaluateHandshake(time: Long) {
        when (commandTwistCount) {
            0 -> { /* No Action */ }
            1 -> fireCommand("VERY_NICE", time)
            2 -> fireCommand("SORRY_WAIT", time)
            3 -> fireCommand("PLEASURE", time)
        }
    }

    // 6. CHAINING WAIT (Conversation Mode Logic)
    private fun checkChainingWait(x: Float, y: Float, z: Float, time: Long) {
        // We wait here until the arm is "Reset" (Dropped/Neutral)
        // This prevents the system from re-detecting the same pose immediately

        val isArmReset = when (currentPose) {
            DetectedPose.ARM_UP -> x < 2.0f
            DetectedPose.STOP -> z < 5.0f
            DetectedPose.HANDSHAKE -> y > 0.0f
            else -> true
        }

        if (isArmReset) {
            // Arm is reset! Go back to listening for the next phrase.
            commandTwistCount = 0
            maxXInSequence = 0f
            currentPose = DetectedPose.NONE
            transitionState(State.LISTENING)
        }

        // Timeout if they take too long to chain (5 seconds)
        if (time - stateStartTime > 5000L) {
            resetState()
        }
    }

    private fun checkCooldown(time: Long) { if (time - stateStartTime > COOLDOWN_TIME) resetState() }

    private fun resetState() {
        twistCount = 0; commandTwistCount = 0; currentPose = DetectedPose.NONE
        transitionState(State.IDLE)
    }

    private fun fireCommand(type: String, time: Long) {
        val path = when(type) {
            "👋" -> "/gesture/wave"
            "👍" -> "/gesture/thumbsup"
            "NAME" -> "/gesture/name"
            "ASK_NAME" -> "/gesture/ask_name"
            "✋" -> "/gesture/stop"
            "WAIT" -> "/gesture/wait"
            "BREAK" -> "/gesture/break"
            "LEAVE" -> "/gesture/leave_alone"
            "NICE" -> "/gesture/nice"
            "VERY_NICE" -> "/gesture/meet_very_nice"
            "SORRY_WAIT" -> "/gesture/sorry_wait"
            "PLEASURE" -> "/gesture/meet_pleasure"
            "THANKS" -> "/gesture/thanks"
            "SAME" -> "/gesture/same"
            else -> "/gesture/thumbsup"
        }
        sendToPhone(path, null)
        feedback(FeedbackType.ACTION)

        if (isConversationMode) {
            // Wait for reset before listening again
            transitionState(State.CHAINING_WAIT)
        } else {
            // One-shot mode
            transitionState(State.COOLDOWN)
        }
    }

    // --- FEEDBACK ---
    enum class FeedbackType { UNLOCK, POSE_ENTRY, MODIFIER, ACTION }

    private fun feedback(type: FeedbackType) {
        val buzzTime = when(type) {
            FeedbackType.UNLOCK -> 80L
            FeedbackType.POSE_ENTRY -> 40L
            FeedbackType.MODIFIER -> 20L
            FeedbackType.ACTION -> 300L
        }
        vibrate(buzzTime)

        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val targetVol = (maxVol * 0.9).toInt()
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

        val tone = when(type) {
            FeedbackType.UNLOCK -> ToneGenerator.TONE_PROP_BEEP
            FeedbackType.POSE_ENTRY -> ToneGenerator.TONE_PROP_ACK
            FeedbackType.MODIFIER -> ToneGenerator.TONE_PROP_BEEP
            FeedbackType.ACTION -> ToneGenerator.TONE_PROP_PROMPT
        }
        toneGenerator?.startTone(tone, 150)
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

    // --- SYNC ---
    private val localConfigReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra("config_json")
            if (json != null) {
                try {
                    watchConfig = jsonHandler.decodeFromString(json)
                    feedback(FeedbackType.UNLOCK)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSensorRate(isMotionEnabled)
        Wearable.getMessageClient(this).addListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(localConfigReceiver, android.content.IntentFilter("BESU_WATCH_CONFIG_UPDATE"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(localConfigReceiver, android.content.IntentFilter("BESU_WATCH_CONFIG_UPDATE"))
        }
        loadConfigFromDisk()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        unregisterReceiver(localConfigReceiver)
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/besu/config_push") {
            try {
                val json = String(event.data, Charsets.UTF_8)
                File(filesDir, CONFIG_FILENAME).writeText(json)
                watchConfig = jsonHandler.decodeFromString(json)
                feedback(FeedbackType.UNLOCK)
                Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, "/besu/config_ack", null)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}