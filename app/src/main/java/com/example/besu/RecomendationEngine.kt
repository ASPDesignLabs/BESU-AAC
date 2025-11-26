package com.example.besu

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

// The structure of our "Brain"
@Serializable
data class BrainData(
    // Maps WordID -> NextWordID -> Weight
    val transitions: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    // Maps Hour(0-23) -> WordID -> Weight
    val timeMap: MutableMap<Int, MutableMap<String, Int>> = mutableMapOf()
)

object RecommendationEngine {
    private const val FILE_NAME = "besu_brain.json"
    private var brain = BrainData()
    private var vocabCache: VocabularyRoot? = null

    // Weights
    private const val WEIGHT_SEQUENCE = 5  // Importance of "Word A follows Word B"
    private const val WEIGHT_TIME = 2      // Importance of Time of Day
    private const val DECAY_FACTOR = 0.95  // Not implemented yet, but useful for forgetting old habits

    fun init(context: Context) {
        vocabCache = CommunicationData.load(context)
        loadBrain(context)
    }

    // --- THE LEARNING LOOP ---
    // Call this when the user hits "PLAY" on a sentence
    fun learnSentence(sentenceIds: List<String>) {
        if (sentenceIds.isEmpty()) return

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 1. Learn Time Context (First word is usually the intent trigger)
        val firstWord = sentenceIds[0]
        val hourMap = brain.timeMap.getOrPut(currentHour) { mutableMapOf() }
        hourMap[firstWord] = (hourMap[firstWord] ?: 0) + WEIGHT_TIME

        // 2. Learn Sequences (Markov Chain)
        for (i in 0 until sentenceIds.size - 1) {
            val current = sentenceIds[i]
            val next = sentenceIds[i+1]

            val nextMap = brain.transitions.getOrPut(current) { mutableMapOf() }
            nextMap[next] = (nextMap[next] ?: 0) + WEIGHT_SEQUENCE
        }

        // 3. Learn Endings (Optional: Map last word to a "STOP" token if needed)
    }

    fun persist(context: Context) {
        try {
            val json = Json.encodeToString(brain)
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e("BesuAI", "Failed to save brain", e)
        }
    }

    // --- THE PREDICTION LOOP ---
    // Call this every time the sentence buffer changes
    fun getSuggestions(context: Context, lastWordId: String?): List<CommItem> {
        if (vocabCache == null) init(context)
        val allItems = vocabCache?.getAllItemsFlat() ?: return emptyList()

        val scores = mutableMapOf<String, Int>()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 1. BASELINE: Check Sequence Probability
        if (lastWordId != null) {
            val transitions = brain.transitions[lastWordId]
            transitions?.forEach { (nextId, weight) ->
                scores[nextId] = (scores[nextId] ?: 0) + weight
            }
        } else {
            // Cold Start: Recommend based on Time of Day + Global Popularity
            // Boost words popular in this hour
            brain.timeMap[currentHour]?.forEach { (wordId, weight) ->
                scores[wordId] = (scores[wordId] ?: 0) + (weight * 2)
            }

            // Adjacent hours (fuzzy time)
            brain.timeMap[currentHour - 1]?.forEach { (id, w) -> scores[id] = (scores[id] ?: 0) + w }
            brain.timeMap[currentHour + 1]?.forEach { (id, w) -> scores[id] = (scores[id] ?: 0) + w }
        }

        // 2. FILLER LOGIC (If we don't have enough data yet)
        // If scores are empty, provide sensible defaults based on grammar categories
        if (scores.isEmpty()) {
            if (lastWordId == null) {
                // Sentence Start: Suggest Starters (I, You, Want, Help)
                return listOfNotNull(
                    findItem("i", allItems),
                    findItem("want", allItems),
                    findItem("help", allItems),
                    findItem("no", allItems),
                    findItem("yes", allItems)
                )
            } else {
                // Fallback: If we have "I", suggest "Want/Am/Need"
                // Simple hardcoded heuristics to seed the AI
                val lastItem = findItem(lastWordId, allItems)
                if (lastItem != null) {
                    // Very basic grammar heuristics
                    if (isInGroup(lastItem, "who", context)) return getGroup("what", context) + getGroup("needs", context)
                    if (isInGroup(lastItem, "what", context)) return getGroup("where", context) + getGroup("grammar", context)
                }
            }
        }

        // 3. CONVERT SCORES TO OBJECTS
        // Sort by score descending, take top 10
        val topIds = scores.entries.sortedByDescending { it.value }.map { it.key }.take(10)

        val results = topIds.mapNotNull { id -> findItem(id, allItems) }.toMutableList()

        // 4. Backfill if we have few results (ensure the bar isn't empty)
        if (results.size < 5) {
            // Add some generic high-frequency items that aren't already there
            val fillers = listOf("to", "it", "and", "the").mapNotNull { findItem(it, allItems) }
            results.addAll(fillers.filter { !results.contains(it) })
        }

        return results.distinctBy { it.id }
    }

    private fun loadBrain(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                val json = file.readText()
                brain = Json.decodeFromString(json)
            }
        } catch (e: Exception) {
            Log.e("BesuAI", "Failed to load brain", e)
        }
    }

    // Helpers
    private fun findItem(id: String, all: List<CommItem>): CommItem? = all.find { it.id == id }

    // Quick hacks to identify categories for heuristics (fallback)
    private fun isInGroup(item: CommItem, groupName: String, context: Context): Boolean {
        val root = CommunicationData.load(context)
        val list = when(groupName) {
            "who" -> root.who
            "what" -> root.what
            else -> emptyList()
        }
        return list.any { it.id == item.id }
    }

    private fun getGroup(groupName: String, context: Context): List<CommItem> {
        val root = CommunicationData.load(context)
        return when(groupName) {
            "what" -> root.what.take(4)
            "needs" -> root.needs.take(4)
            "where" -> root.where.take(3)
            "grammar" -> root.grammar.take(3)
            else -> emptyList()
        }
    }
}