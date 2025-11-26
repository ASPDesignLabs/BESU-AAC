package com.example.besu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.besu.wear.WatchConfig
import com.example.besu.wear.WatchPage
import com.example.besu.wear.WatchSlot
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class WatchEditorActivity : ComponentActivity() {

    private var editingSlotIndex: Int = -1

    private var isSyncing by mutableStateOf(false)
    private var editingPageIndex: Int = 0
    private var updateConfigCallback: ((WatchSlot) -> Unit)? = null
    private val CONFIG_FILENAME = "watch_config.json"

    // Lenient JSON to prevent crashes on old data
    private val jsonHandler = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val pickerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "BESU_PICKER_RESULT" && editingSlotIndex != -1) {
                val emoji = intent.getStringExtra("emoji") ?: "❓"
                val label = intent.getStringExtra("label") ?: "Command"
                val path = intent.getStringExtra("path") ?: ""
                val type = intent.getStringExtra("type") ?: "COMMAND"

                val displayLabel = if (label.length > 10) label.take(8) + ".." else label
                val newSlot = WatchSlot(displayLabel, emoji, path, type)

                updateConfigCallback?.invoke(newSlot)
                editingSlotIndex = -1
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter("BESU_PICKER_RESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pickerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pickerReceiver, filter)
        }

        // 1. Load config safely
        val loadedConfig = loadConfig()

        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(colors = if(isDark) darkColors() else lightColors()) {
                WatchEditorScreen(
                    initialConfig = loadedConfig,
                    isDarkMode = isDark,
                    onSlotClick = { pageIdx, slotIdx, callback ->
                        editingPageIndex = pageIdx
                        editingSlotIndex = slotIdx
                        updateConfigCallback = callback

                        val intent = Intent(this, RadialMenuService::class.java)
                        intent.putExtra("IS_PICKER_MODE", true)
                        startService(intent)
                    },
                    onSync = { newConfig ->
                        saveConfigLocally(newConfig)
                        syncToWatch(newConfig)
                    }
                )
            }
        }
    }

    private fun loadConfig(): WatchConfig {
        val file = File(filesDir, CONFIG_FILENAME)
        if (!file.exists()) return createDefaultConfig()

        return try {
            jsonHandler.decodeFromString(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback so user sees pages instead of blank screen
            createDefaultConfig()
        }
    }

    private fun createDefaultConfig(): WatchConfig {
        return WatchConfig(
            pages = listOf(
                WatchPage("p1", "Page 1", List(6) { WatchSlot("Empty", "➕", "") }),
                WatchPage("p2", "Page 2", List(6) { WatchSlot("Empty", "➕", "") })
            )
        )
    }

    private fun saveConfigLocally(config: WatchConfig) {
        try {
            val json = jsonHandler.encodeToString(config)
            File(filesDir, CONFIG_FILENAME).writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncToWatch(config: WatchConfig) {
        isSyncing = true
        Toast.makeText(this, "Searching for watch...", Toast.LENGTH_SHORT).show()

        try {
            val json = jsonHandler.encodeToString(config)
            val payload = json.toByteArray(Charsets.UTF_8)
            val messageClient = Wearable.getMessageClient(this)
            val nodeClient = Wearable.getNodeClient(this)

            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                // DEBUG: Tell the user what we found
                if (nodes.isEmpty()) {
                    isSyncing = false
                    Toast.makeText(this, "❌ No Bluetooth nodes found!", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                var successCount = 0
                nodes.forEach { node ->
                    // We send to ALL nodes, assuming one is the watch
                    messageClient.sendMessage(node.id, "/besu/config_push", payload)
                        .addOnSuccessListener {
                            successCount++
                            // Only toast if this is the last one or we haven't toasted yet
                            if(successCount == 1) Toast.makeText(this, "📤 Sent to ${node.displayName}...", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "❌ Failed to send to ${node.displayName}", Toast.LENGTH_SHORT).show()
                        }
                }
            }.addOnFailureListener {
                isSyncing = false
                Toast.makeText(this, "❌ Bluetooth Error", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            isSyncing = false
            Toast.makeText(this, "Encoding Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pickerReceiver)
        super.onDestroy()
    }
}

@Composable
fun WatchEditorScreen(
    initialConfig: WatchConfig,
    isDarkMode: Boolean,
    onSlotClick: (Int, Int, (WatchSlot) -> Unit) -> Unit,
    onSync: (WatchConfig) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    var currentPageIndex by remember { mutableStateOf(0) }

    val bgColor = if (isDarkMode) Color(0xFF121212) else Color(0xF2592346)
    val textColor = if (isDarkMode) Color.White else Color.Black
    val bezelColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Watch Editor", style = MaterialTheme.typography.h5, color = textColor, fontWeight = FontWeight.Bold)

        // --- PAGE NAVIGATOR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { if (currentPageIndex > 0) currentPageIndex-- }, enabled = currentPageIndex > 0) { Text("<") }

            Text(
                "Page ${currentPageIndex + 1}/${config.pages.size}",
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Button(onClick = { if (currentPageIndex < config.pages.size - 1) currentPageIndex++ }, enabled = currentPageIndex < config.pages.size - 1) { Text(">") }

            Spacer(modifier = Modifier.width(8.dp))

            // ADD PAGE BUTTON
            Button(onClick = {
                val newPage = WatchPage("p${config.pages.size + 1}", "Page ${config.pages.size + 1}", List(6) { WatchSlot("Empty", "➕", "") })
                config = config.copy(pages = config.pages + newPage)
                currentPageIndex = config.pages.lastIndex
            }) { Text("+") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- VIRTUAL WATCH FACE ---
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.Black, CircleShape)
                .border(12.dp, bezelColor, CircleShape)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentPage = config.pages.getOrNull(currentPageIndex)
            if (currentPage != null) {
                Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                    val rows = currentPage.slots.chunked(2)
                    rows.forEachIndexed { rowIndex, rowSlots ->
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowSlots.forEachIndexed { colIndex, slot ->
                                val realSlotIndex = (rowIndex * 2) + colIndex
                                EditorSlot(slot) {
                                    onSlotClick(currentPageIndex, realSlotIndex) { updatedSlot ->
                                        // Deep State Update
                                        val newPages = config.pages.toMutableList()
                                        val newSlots = newPages[currentPageIndex].slots.toMutableList()
                                        newSlots[realSlotIndex] = updatedSlot
                                        newPages[currentPageIndex] = newPages[currentPageIndex].copy(slots = newSlots)
                                        config = config.copy(pages = newPages)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onSync(config) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
        ) {
            Text("SAVE & SYNC", color = Color.White)
        }
    }
}

@Composable
fun RowScope.EditorSlot(slot: WatchSlot, onClick: () -> Unit) {
    val isEmpty = slot.path.isEmpty()
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(4.dp)
            .background(if (isEmpty) Color(0xFF333333) else Color(0xFF222222), CircleShape)
            .clickable(onClick = onClick)
            .border(1.dp, if(isEmpty) Color.Gray else Color.Cyan, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(slot.emoji, fontSize = 24.sp)
            Text(
                slot.label,
                style = MaterialTheme.typography.caption,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}