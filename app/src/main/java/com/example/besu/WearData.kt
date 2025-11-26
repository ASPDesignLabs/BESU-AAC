package com.example.besu.wear // Keeping the package consistent for easy copy-paste

import kotlinx.serialization.Serializable

@Serializable
data class WatchConfig(
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
    val path: String, // Direct /gesture/ path OR raw text
    val type: String = "COMMAND" // "COMMAND" or "SENTENCE"
)