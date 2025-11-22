package com.example.besu

import android.content.Context
import android.graphics.Color
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CommItem(
    val id: String,
    val emoji: String,
    val label: String,
    val phrase: String,
    val color: String = "#2196F3",
    val type: String = "NONE",
    val subItems: List<CommItem> = emptyList()
) {
    fun getColorInt(): Int {
        return try {
            Color.parseColor(color)
        } catch (e: IllegalArgumentException) {
            Color.GRAY
        }
    }
}

@Serializable
data class VocabularyRoot(
    val emotions: List<CommItem> = emptyList(),
    val who: List<CommItem> = emptyList(),
    val where: List<CommItem> = emptyList(),
    val what: List<CommItem> = emptyList(),
    val needs: List<CommItem> = emptyList(),
    @SerialName("when") val time: List<CommItem> = emptyList(),
    val linkers: List<CommItem> = emptyList(),
    val descriptions: List<CommItem> = emptyList(),
    val questions: List<CommItem> = emptyList(),
    val grammar: List<CommItem> = emptyList() // <--- THIS MUST EXIST
) {
    fun getAllItemsFlat(): List<CommItem> {
        val all = mutableListOf<CommItem>()
        all.addAll(emotions); all.addAll(who); all.addAll(where)
        all.addAll(what); all.addAll(needs); all.addAll(time)
        all.addAll(linkers); all.addAll(descriptions); all.addAll(questions)
        all.addAll(grammar)

        val deepList = mutableListOf<CommItem>()
        deepList.addAll(all)
        all.forEach { collectSubItems(it, deepList) }
        return deepList
    }

    private fun collectSubItems(parent: CommItem, targetList: MutableList<CommItem>) {
        if (parent.subItems.isNotEmpty()) {
            targetList.addAll(parent.subItems)
            parent.subItems.forEach { collectSubItems(it, targetList) }
        }
    }
}

object CommunicationData {
    private var cache: VocabularyRoot? = null

    // Lenient parsing helps avoid crashes on minor JSON errors
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(context: Context): VocabularyRoot {
        if (cache != null) return cache!!

        return try {
            val jsonString = context.assets.open("communication.json")
                .bufferedReader()
                .use { it.readText() }

            val data = jsonConfig.decodeFromString<VocabularyRoot>(jsonString)

            // DEBUG LOG: Check if data actually loaded
            Log.d("BesuData", "Loaded ${data.grammar.size} grammar items")
            Log.d("BesuData", "Loaded ${data.what.size} 'what' items")

            cache = data
            data
        } catch (e: Exception) {
            // LOG THE ERROR so you can see it in Logcat!
            Log.e("BesuData", "CRITICAL: Failed to parse JSON", e)
            VocabularyRoot() // Returns empty lists, causing your blank bars
        }
    }

    fun getPhraseForEmoji(emoji: String): String = when(emoji) {
        "👋" -> "Hello."
        "✋" -> "Stop."
        "🚫" -> "No."
        "👍" -> "Good."
        else -> ""
    }
}