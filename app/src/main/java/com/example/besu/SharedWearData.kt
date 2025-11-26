package com.example.besu.wear

import kotlinx.serialization.Serializable

// These duplicate the definitions in the :wear module
// so the Phone knows how to structure the JSON payload.

enum class TrainingMode {
    GESTURE,
    NOISE,
    GROSS_MOTOR
}

@Serializable
data class TrainingRequest(
    val mode: TrainingMode,
    val label: String
)