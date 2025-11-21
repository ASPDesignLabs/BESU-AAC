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

    private enum class State { IDLE, LISTENING, COOLDOWN }
    private var currentState = State.IDLE

    // --- GESTURE VARS ---

    // 1. UNLOCK (Twist)
    private var lastY = 0f
    private var twistCount = 0
    private var lastTwistTime = 0L

    // 2. THUMBS UP (Pump)
    private var isThumbsPrimed = false

    // 3. WAVE (Wiggle)
    private var waveCount = 0
    private var lastWaveDir = 0 // 1 or -1
    private var lastWaveTime = 0L

    // 4. STOP (Hold Pose)
    private var stopHoldFrames = 0

    // 5. NO (Sweep)
    private var noSweepCount = 0
    private var lastNoDir = 0
    private var lastNoTime = 0L

    // SENSOR CACHE
    private var currentGyroX = 0f
    private var currentGyroY = 0f
    private var currentGyroZ = 0f

    // TIMERS
    private var listeningStartTime = 0L
    private val LISTENING_WINDOW = 5000L // Increased to 5s for complex gestures
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
            text = "Twist wrist to unlock"
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

        // Cache Gyro Data
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentGyroX = event.values[0]
            currentGyroY = event.values[1]
            currentGyroZ = event.values[2]
            return
        }

        // Run Logic on Accel Tick
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val currentTime = System.currentTimeMillis()

            // VISUAL DEBUG
            if (currentTime - lastLogTime > 200) {
                val stateStr = when(currentState) {
                    State.IDLE -> "LOCKED"
                    State.LISTENING -> "LISTENING..."
                    State.COOLDOWN -> "SENT"
                }
                // Show critical axes
                debugText.text = "$stateStr\nX:${"%.1f".format(x)} Y:${"%.1f".format(y)}\nG_Z:${"%.1f".format(currentGyroZ)}"
                lastLogTime = currentTime
            }

            when (currentState) {
                State.IDLE -> checkActivationGesture(x, y, currentTime)
                State.LISTENING -> checkCommandGestures(x, y, z, currentTime)
                State.COOLDOWN -> checkCooldown(currentTime)
            }
        }
    }

    // --- 1. UNLOCK ---
    private fun checkActivationGesture(x: Float, y: Float, time: Long) {
        if (time - lastTwistTime > 800) twistCount = 0

        // Arm must be raised (Not hanging down X>8)
        if (abs(x) > 8.0f) {
            twistCount = 0
            return
        }

        if (abs(y - lastY) > 7.0f) {
            twistCount++
            lastTwistTime = time
        }
        lastY = y

        if (twistCount >= 3) activateListening(time)
    }

    // --- 2. COMMAND ROUTER ---
    private fun checkCommandGestures(x: Float, y: Float, z: Float, time: Long) {
        if (time - listeningStartTime > LISTENING_WINDOW) {
            resetState()
            return
        }

        // A. STOP (✋) - "Talk to the hand"
        // Pose: Arm extended forward, Palm Forward.
        // PHYSICS:
        // 1. Arm is Horizontal -> X should be low (Gravity is perpendicular to wrist).
        // 2. Watch is Vertical (6 o'clock down) -> Y should be HIGH (Gravity pulls Y).
        if (y > 7.0f && abs(x) < 4.0f) {
            // Stability Check: Hand must be still to look like a "Stop" sign
            val totalRot = abs(currentGyroX) + abs(currentGyroY) + abs(currentGyroZ)

            if (totalRot < 0.8f) { // Very Still
                stopHoldFrames++
                if (stopHoldFrames > 2) {
                    statusText.text = "HOLDING ✋"
                    statusText.setTextColor(Color.CYAN)
                }
            } else {
                stopHoldFrames = 0
            }

            // Hold for ~0.5s (10 frames)
            if (stopHoldFrames >= 10) {
                fireCommand("✋", time)
                return
            }
        } else {
            // Only reset frames if we completely lose the pose
            if (stopHoldFrames > 0 && y < 5.0f) stopHoldFrames = 0
        }

        // B. WAVE (👋) - "Royal Wave"
        // Pose: Arm Up (Vertical-ish).
        // Motion: Side-to-side rotation.
        // PHYSICS:
        // 1. Arm Up -> X is Positive (> 5.0).
        // 2. Motion -> Gyroscope oscillation.
        if (x > 5.0f) {
            // When palm is forward and arm is up:
            // Waving left/right acts on Gyro X (Face tilt) AND Gyro Z (Face rotation).
            // Let's check BOTH.
            val wiggleEnergy = abs(currentGyroX) + abs(currentGyroZ)

            // 2.5 rad/s is a healthy wave
            if (wiggleEnergy > 2.5f) {
                // Check direction flip on the dominant axis
                val dominantGyro = if (abs(currentGyroX) > abs(currentGyroZ)) currentGyroX else currentGyroZ
                val dir = if (dominantGyro > 0) 1 else -1

                if (dir != lastWaveDir) {
                    waveCount++
                    lastWaveDir = dir
                    lastWaveTime = time
                    // Feedback
                    statusText.text = "WAVING..."
                    statusText.setTextColor(Color.MAGENTA)
                }
            }

            if (time - lastWaveTime > 600) waveCount = 0

            // Require 4 direction changes (Left-Right-Left-Right)
            if (waveCount >= 4) {
                fireCommand("👋", time)
                return
            }
        }

        // C. NO (🚫) - "Wiper"
        // Pose: Horizontal sweep. (Keep existing working logic)
        // Note: Increased threshold slightly to prevent overlap with Wave
        if (abs(currentGyroY) > 3.0f || abs(currentGyroX) > 3.0f) {
            // Combine axes energy for sweeping motion
            val sweepEnergy = currentGyroX + currentGyroY
            val dir = if (sweepEnergy > 0) 1 else -1

            if (dir != lastNoDir) {
                noSweepCount++
                lastNoDir = dir
                lastNoTime = time
            }
        }
        if (time - lastNoTime > 600) noSweepCount = 0

        if (noSweepCount >= 3) {
            fireCommand("🚫", time)
            return
        }

        // D. THUMBS UP (👍) - "Pump"
        // (Keep existing working logic)
        if (x > 7.0f) {
            if (!isThumbsPrimed) {
                isThumbsPrimed = true
                statusText.text = "PRIMED 👍"
                statusText.setTextColor(Color.YELLOW)
                vibrate(50)
            }
        }
        if (isThumbsPrimed && x < 4.0f) {
            fireCommand("👍", time)
            return
        }
    }

    private fun activateListening(time: Long) {
        currentState = State.LISTENING
        listeningStartTime = time

        // Reset all gesture counters
        twistCount = 0
        waveCount = 0
        stopHoldFrames = 0
        noSweepCount = 0
        isThumbsPrimed = false

        statusText.text = "UNLOCKED!"
        statusText.setTextColor(Color.GREEN)
        mainLayout.setBackgroundColor(Color.parseColor("#222222"))

        vibrate(100)
    }

    private fun fireCommand(emoji: String, time: Long) {
        // Send specific path based on emoji
        val path = when(emoji) {
            "👋" -> "/gesture/wave"
            "✋" -> "/gesture/stop" // You need to add this listener on Phone
            "🚫" -> "/gesture/no"   // You need to add this listener on Phone
            else -> "/gesture/thumbsup"
        }

        sendToPhone(path, null)

        statusText.text = "$emoji SENT"
        mainLayout.setBackgroundColor(Color.parseColor("#004400"))

        vibrate(300)

        currentState = State.COOLDOWN
        cooldownStartTime = time
    }

    // ... [Rest of boilerplate: checkCooldown, resetState, sendToPhone, vibrate] ...
    private fun checkCooldown(time: Long) {
        if (time - cooldownStartTime > COOLDOWN_TIME) resetState()
    }

    private fun resetState() {
        currentState = State.IDLE
        twistCount = 0
        statusText.text = "LOCKED"
        statusText.setTextColor(Color.GRAY)
        mainLayout.setBackgroundColor(Color.BLACK)
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