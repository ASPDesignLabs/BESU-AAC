package com.example.besu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    // We hold the voices here to pass to the UI
    private val availableVoices = mutableStateListOf<Voice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS to get the list of voices
        tts = TextToSpeech(this, this)

        // Ensure setup is marked complete
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("setup_complete", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContent {
            BesuDashboard(
                availableVoices = availableVoices,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenOverlaySettings = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                // Populate voices list
                try {
                    val voices = tts?.voices
                    if (voices != null) {
                        availableVoices.clear()
                        availableVoices.addAll(voices.sortedBy { it.name })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun BesuDashboard(
    availableVoices: List<Voice>,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    // State for Name
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var showNameDialog by remember { mutableStateOf(false) }

    // State for TTS
    var currentVoiceName by remember { mutableStateOf(prefs.getString("tts_voice_name", "Default") ?: "Default") }
    var showVoiceDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Theme Colors
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // HEADER
        Text(
            "Besu AI",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Welcome back, ${if(userName.isEmpty()) "Friend" else userName}.",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // --- QUICK ACTIONS ---
        SectionHeader("Quick Access")

        DashboardCard(
            title = "Speak",
            icon = "💬",
            desc = "Open Radial Menu",
            color = Color(0xFF4CAF50)
        ) {
            context.startService(Intent(context, RadialMenuService::class.java))
        }

        DashboardCard(
            title = "Watch Config",
            icon = "⌚",
            desc = "Edit Watch Face & Gestures",
            color = Color(0xFF2196F3)
        ) {
            context.startActivity(Intent(context, WatchEditorActivity::class.java))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- CONFIGURATION ---
        SectionHeader("Personalization")

        // 1. Name Editor
        SettingCard(
            title = "My Name",
            value = if (userName.isEmpty()) "Tap to set..." else userName,
            icon = "👤"
        ) {
            showNameDialog = true
        }

        SettingCard(title = "Motion Tuning", value = "Train & Filter Noise", icon = "🧠") {
            context.startActivity(Intent(context, TrainingDashboardActivity::class.java))
        }

        // 2. TTS Selector
        SettingCard(
            title = "AI Voice",
            value = currentVoiceName.take(20) + if(currentVoiceName.length > 20) "..." else "",
            icon = "🔊"
        ) {
            if (availableVoices.isEmpty()) {
                Toast.makeText(context, "Loading voices...", Toast.LENGTH_SHORT).show()
            } else {
                showVoiceDialog = true
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SYSTEM PERMISSIONS ---
        SectionHeader("System Permissions")

        SettingCard(title = "Accessibility Menu", value = "Gesture Permissions", icon = "🖐️") {
            onOpenAccessibility()
        }

        SettingCard(title = "Overlay Permission", value = "Display Over Apps", icon = "📱") {
            onOpenOverlaySettings()
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SYSTEM STATUS ---
        SectionHeader("System Status")
        StatusRow("Brain Engine", "Active (Learning)")
        StatusRow("Overlay Service", "Ready")
    }

    // --- DIALOGS ---

    // Name Dialog
    if (showNameDialog) {
        var tempName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("What is your name?") },
            text = {
                TextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    userName = tempName
                    prefs.edit().putString("user_name", tempName).apply()
                    showNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Voice Dialog
    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text("Select Voice") },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        items(availableVoices) { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentVoiceName = voice.name
                                        prefs.edit().putString("tts_voice_name", voice.name).apply()
                                        showVoiceDialog = false
                                        Toast.makeText(context, "Voice saved!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    voice.name,
                                    fontWeight = if(voice.name == currentVoiceName) FontWeight.Bold else FontWeight.Normal,
                                    color = if(voice.name == currentVoiceName) MaterialTheme.colors.primary else Color.Black
                                )
                            }
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showVoiceDialog = false }) { Text("Close") }
            }
        )
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Color(0xFF90A4AE),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun DashboardCard(title: String, icon: String, desc: String, color: Color, onClick: () -> Unit) {
    Card(
        backgroundColor = Color(0xFF263238),
        shape = RoundedCornerShape(16.dp),
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SettingCard(title: String, value: String, icon: String, onClick: () -> Unit) {
    Card(
        backgroundColor = Color(0xFF37474F),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(value, color = Color(0xFF81C784), fontSize = 12.sp)
        }
    }
}

@Composable
fun StatusRow(label: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray)
        Text(status, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
    }
}