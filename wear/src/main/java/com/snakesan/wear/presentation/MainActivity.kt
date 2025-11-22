package com.example.besu.wear

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.Wearable
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var vibrator: Vibrator? = null

    private lateinit var statusText: TextView
    private lateinit var debugText: TextView
    private lateinit var mainLayout: LinearLayout

    private enum class State { IDLE, LISTENING, ARM_UP, COOLDOWN }
    private var currentState = State.IDLE

    // --- GESTURE VARS ---
    private var noSweepCount = 0
    // Activation (Unlock)
    private var lastY = 0f
    private var twistCount = 0
    private var lastTwistTime = 0L

    // Arm Up State
    private var armUpStartTime = 0L
    private var maxXInSequence = 0f

    // Wave Tracking
    private var waveCount = 0
    private var lastWaveDir = 0
    private var lastWaveTime = 0L

    // Horizontal / Low Tracking
    private var stopHoldFrames = 0

    // Shared Twist Tracking (Name vs No)
    private var commandTwistCount = 0
    private var lastCmdTwistDir = 0
    private var lastCmdTwistTime = 0L

    // Nice to Meet You Tracking
    private var niceWaveStart = false
    private var niceSweepTime = 0L

    // Sensors
    private var currentGyroX = 0f
    private var currentGyroY = 0f
    private var currentGyroZ = 0f

    // Timers
    private var listeningStartTime = 0L
    private val LISTENING_WINDOW = 6000L
    private val COOLDOWN_TIME = 2000L
    private var cooldownStartTime = 0L
    private var lastLogTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 10)
            setBackgroundColor(Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "LOCKED"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.GRAY)
        }

        debugText = TextView(this).apply {
            text = "Twist to unlock"
            gravity = Gravity.CENTER
            textSize = 10f
            setTextColor(Color.LTGRAY)
        }

        val resetButton = Button(this).apply {
            text = "RESET"
            setOnClickListener { resetState() }
        }

        mainLayout.addView(statusText)
        mainLayout.addView(debugText)
        mainLayout.addView(resetButton)
        setContentView(mainLayout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentGyroX = event.values[0]
            currentGyroY = event.values[1]
            currentGyroZ = event.values[2]
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastLogTime > 200) {
                val stateStr = when(currentState) {
                    State.IDLE -> "LOCKED"
                    State.LISTENING -> "LISTENING"
                    State.ARM_UP -> "ARM UP"
                    State.COOLDOWN -> "SENT"
                }
                debugText.text = "$stateStr\nX:${"%.1f".format(x)} Y:${"%.1f".format(y)}"
                lastLogTime = currentTime
            }

            when (currentState) {
                State.IDLE -> checkUnlock(x, y, currentTime)
                State.LISTENING -> checkListening(x, y, z, currentTime)
                State.ARM_UP -> checkArmUpGestures(x, y, z, currentTime)
                State.COOLDOWN -> checkCooldown(currentTime)
            }
        }
    }

    // 1. UNLOCK (Double Twist)
    private fun checkUnlock(x: Float, y: Float, time: Long) {
        if (time - lastTwistTime > 800) twistCount = 0
        // Must be roughly horizontal or low
        if (x > 8.0f) { twistCount = 0; return }

        if (abs(y - lastY) > 7.0f) {
            twistCount++
            lastTwistTime = time
        }
        lastY = y

        if (twistCount >= 3) activateListening(time)
    }

    // 2. LISTENING (Router)
    private fun checkListening(x: Float, y: Float, z: Float, time: Long) {
        if (time - listeningStartTime > LISTENING_WINDOW) {
            resetState()
            return
        }

        // A. VERTICAL POSE (Enter Arm Up State)
        // If X is high positive, arm is Up
        if (x > 6.5f) {
            currentState = State.ARM_UP
            armUpStartTime = time
            maxXInSequence = x

            statusText.text = "ARM UP..."
            statusText.setTextColor(Color.CYAN)
            vibrate(50)
            return
        }

        // B. HORIZONTAL / LOW GESTURES
        checkHorizontalGestures(x, y, z, time)
    }

    // 3. ARM UP (Wave, Nice, Thumb)
    private fun checkArmUpGestures(x: Float, y: Float, z: Float, time: Long) {
        if (x > maxXInSequence) maxXInSequence = x

        if (time - armUpStartTime > 4000) {
            resetState()
            return
        }

        // --- CHECK 1: THUMBS UP (Pump Drop) ---
        if (x < 4.0f && waveCount == 0) {
            fireCommand("👍", time)
            return
        }

        // --- CHECK 2: WAVE & NICE ---
        val wiggle = abs(currentGyroX) + abs(currentGyroZ)
        val sweep = abs(currentGyroY)

        // Nice (Sweep)
        if (sweep > 3.0f) {
            fireCommand("NICE", time)
            return
        }

        // Wave (Wiggle)
        if (wiggle > 2.5f) {
            val dir = if (currentGyroZ > 0) 1 else -1
            if (dir != lastWaveDir) {
                waveCount++
                lastWaveDir = dir
                lastWaveTime = time
                statusText.text = "WAVING..."
                statusText.setTextColor(Color.YELLOW)
            }
        }

        if (time - lastWaveTime > 600) waveCount = 0

        if (waveCount >= 4) {
            fireCommand("👋", time)
            return
        }
    }

    // 4. HORIZONTAL & LOW GESTURES
    private fun checkHorizontalGestures(x: Float, y: Float, z: Float, time: Long) {
        // A. STOP (High Y)
        if (y > 7.0f && abs(x) < 5.0f) {
            val totalRot = abs(currentGyroX) + abs(currentGyroY) + abs(currentGyroZ)
            if (totalRot < 1.0f) {
                stopHoldFrames++
                statusText.text = "HOLDING ✋"
                statusText.setTextColor(Color.CYAN)
            } else stopHoldFrames = 0

            if (stopHoldFrames >= 10) fireCommand("✋", time)
            return
        } else {
            stopHoldFrames = 0
        }

        // B. SHARED TWIST (Name vs No)
        if (abs(currentGyroZ) > 3.0f) {
            val dir = if (currentGyroZ > 0) 1 else -1
            if (dir != lastCmdTwistDir) {
                if (time - lastCmdTwistTime < 600) commandTwistCount++
                else commandTwistCount = 1

                lastCmdTwistDir = dir
                lastCmdTwistTime = time
            }
        }
        if (time - lastCmdTwistTime > 800) commandTwistCount = 0

        // DECISION LOGIC
        if (commandTwistCount >= 3) {
            // CASE 1: NAME (Arm Level, X near 0)
            if (abs(x) < 3.5f) {
                fireCommand("NAME", time)
                return
            }

            // CASE 2: NO (Arm Down, X Negative)
            // Assuming Left Wrist: Thumb Up = +X, so Thumb Down/Pointing Floor = -X
            if (x < -2.0f) {
                fireCommand("🚫", time)
                return
            }
        }
    }

    private fun activateListening(time: Long) {
        currentState = State.LISTENING
        listeningStartTime = time
        resetCounters()

        statusText.text = "UNLOCKED!"
        statusText.setTextColor(Color.GREEN)
        mainLayout.setBackgroundColor(Color.parseColor("#222222"))
        vibrate(100)
        sendToPhone("/debug/sensor", "STATE: UNLOCKED")
    }

    private fun fireCommand(type: String, time: Long) {
        val path = when(type) {
            "NAME" -> "/gesture/name"
            "NICE" -> "/gesture/nice"
            "👋" -> "/gesture/wave"
            "✋" -> "/gesture/stop"
            "🚫" -> "/gesture/no"
            else -> "/gesture/thumbsup"
        }

        val label = if(type == "NAME") "MY NAME" else if(type == "NICE") "NICE TO MEET" else type

        sendToPhone(path, null)
        statusText.text = "$label SENT"
        mainLayout.setBackgroundColor(Color.parseColor("#004400"))
        vibrate(300)

        currentState = State.COOLDOWN
        cooldownStartTime = time
        resetCounters()
    }

    private fun checkCooldown(time: Long) {
        if (time - cooldownStartTime > COOLDOWN_TIME) resetState()
    }

    private fun resetState() {
        currentState = State.IDLE
        resetCounters()
        statusText.text = "LOCKED"
        statusText.setTextColor(Color.GRAY)
        mainLayout.setBackgroundColor(Color.BLACK)
    }

    private fun resetCounters() {
        twistCount = 0; waveCount = 0; stopHoldFrames = 0; noSweepCount = 0
        commandTwistCount = 0; niceWaveStart = false; maxXInSequence = 0f
    }

    private fun vibrate(duration: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    private fun sendToPhone(path: String, message: String?) {
        val payload = message?.toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, path, payload)
            }
        }
    }
}