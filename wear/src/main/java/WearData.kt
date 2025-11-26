package com.example.besu.wear

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

// --- 1. LEGACY HARDCODED COMMANDS ---
data class WearCommand(
    val label: String,
    val emoji: String,
    val path: String,
    val subCommands: List<WearCommand> = emptyList()
) {
    fun toWatchSlot(): WatchSlot {
        return WatchSlot(label, emoji, path, "COMMAND")
    }
}

object WearVocabulary {
    val motionSet = listOf(
        WearCommand("Name", "😀", "/gesture/name", listOf(WearCommand("Ask", "❓", "/gesture/ask_name"))),
        WearCommand("No", "🚫", "/gesture/no", listOf(WearCommand("Hate", "😠", "/gesture/hate"))),
        WearCommand("Hello", "👋", "/gesture/wave", listOf(WearCommand("Bye", "👋", "/gesture/goodbye"))),
        WearCommand("Stop", "✋", "/gesture/stop", listOf(WearCommand("Wait", "⏳", "/gesture/wait"))),
        WearCommand("Good", "👍", "/gesture/thumbsup", listOf(WearCommand("Bad", "👎", "/gesture/thumbsdown"))),
        WearCommand("Nice", "🤝", "/gesture/nice", listOf(WearCommand("Same", "✨", "/gesture/same")))
    )
}

// --- 2. CONFIGURATION (UI PAGES) ---

@Serializable
data class WatchConfig(
    val topItems: List<WatchSlot> = emptyList(),
    val pages: List<WatchPage> = emptyList()
)

@Serializable
data class WatchPage(
    val id: String,
    val title: String,
    val slots: List<WatchSlot>
)

@Serializable
data class WatchSlot(
    val label: String,
    val emoji: String,
    val path: String,
    val type: String = "COMMAND"
)

// --- 3. MOTION MATCHING & TRAINING (RL) ---

enum class TrainingMode {
    GESTURE,     // Positive reinforcement (Learn "Wave")
    NOISE,       // Negative reinforcement (Ignore "Walking")
    GROSS_MOTOR  // Threshold calibration (Set "Panic" energy level)

}
enum class MotionProfileType {
    STANDARD, // The hardcoded "Physics Engine" (Robust, zero-setup)

    CUSTOM    // The RL "Motion Matching" (Personalized, trained)
}

@Serializable
data class TrainingRequest(
    val mode: TrainingMode,
    val label: String
)

// The Raw Data of a movement
@Serializable
data class MotionSample(
    val t: Long,
    val ax: Float, val ay: Float, val az: Float,
    val gx: Float, val gy: Float, val gz: Float
) {
    fun distanceTo(other: MotionSample): Float {
        // Simple Euclidean distance on Accelerometer only for shape matching
        val dAx = ax - other.ax
        val dAy = ay - other.ay
        val dAz = az - other.az
        return sqrt(dAx*dAx + dAy*dAy + dAz*dAz)
    }
}

// A stored "Ideal Gesture"
@Serializable
data class GestureExemplar(
    val id: String,
    val samples: List<MotionSample>
)

// The User's specific Motor Profile
@Serializable
data class MotionProfile(
    val exemplars: MutableList<GestureExemplar> = mutableListOf(),
    var sensitivity: Float = 50.0f, // DTW Distance Threshold
    var panicThreshold: Float = 40.0f // Energy Threshold for SOS
)

