package com.example.besu

import android.content.Context
import java.util.Calendar

object HistoryManager {
    private const val PREFS_NAME = "besu_history"
    private const val MAX_PHRASE_HISTORY = 10
    private const val MAX_ITEM_HISTORY = 50

    // --- 1. INDIVIDUAL ITEMS ---

    fun addToHistory(context: Context, item: CommItem) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = loadItemIds(context).toMutableList()

        currentList.remove(item.id)
        currentList.add(0, item.id)

        if (currentList.size > MAX_ITEM_HISTORY) {
            // FIX: Create a copy (.toList()) to avoid ConcurrentModificationException
            val trimmed = currentList.subList(0, MAX_ITEM_HISTORY).toList()
            currentList.clear()
            currentList.addAll(trimmed)
        }

        prefs.edit().putString("item_history_ids", currentList.joinToString(",")).apply()
    }

    fun getMergedHistoryAndSmart(context: Context, vocab: VocabularyRoot): List<CommItem> {
        val allItems = vocab.getAllItemsFlat()
        val smartList = getSmartSuggestions(vocab, allItems)

        val historyIds = loadItemIds(context)
        // Safely map IDs to items, filtering out nulls (deprecated items)
        val historyList = historyIds.mapNotNull { id -> allItems.find { it.id == id } }

        return (smartList + historyList).distinctBy { it.id }
    }

    private fun loadItemIds(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString("item_history_ids", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",")
    }

    // --- 2. FULL SENTENCES ---

    fun addPhraseToHistory(context: Context, emojis: String, text: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entry = "$emojis|$text"

        val currentList = loadPhrases(context).toMutableList()
        currentList.remove(entry)
        currentList.add(0, entry)

        if (currentList.size > MAX_PHRASE_HISTORY) {
            // FIX: Create a copy (.toList()) to avoid ConcurrentModificationException
            val trimmed = currentList.subList(0, MAX_PHRASE_HISTORY).toList()
            currentList.clear()
            currentList.addAll(trimmed)
        }

        prefs.edit().putString("phrase_history_list", currentList.joinToString("||")).apply()
    }

    fun getLastPhrases(context: Context): List<Pair<String, String>> {
        val rawList = loadPhrases(context)
        return rawList.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 2) Pair(parts[0], parts[1]) else null
        }
    }

    private fun loadPhrases(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString("phrase_history_list", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split("||")
    }

    // --- HELPERS ---

    private fun getSmartSuggestions(vocab: VocabularyRoot, allItems: List<CommItem>): List<CommItem> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val smartList = mutableListOf<CommItem>()

        if (hour in 5..10) {
            smartList.addAll(vocab.needs.filter { it.id == "hungry" || it.id == "toilet" })
            smartList.addAll(vocab.time.filter { it.id == "today" })
        } else if (hour >= 20 || hour < 4) {
            smartList.addAll(allItems.filter { it.id == "tired" || it.id == "sleep" })
            smartList.addAll(vocab.time.filter { it.id == "tomorrow" || it.id == "night" })
        } else if (hour in 11..14) {
            smartList.addAll(vocab.needs.filter { it.id == "hungry" || it.id == "thirsty" })
        }

        return smartList
    }
}