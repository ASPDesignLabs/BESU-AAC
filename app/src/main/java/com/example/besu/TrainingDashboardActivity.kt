package com.example.besu

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.besu.wear.TrainingMode
import com.example.besu.wear.TrainingRequest
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

class TrainingDashboardActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    // Track which item is currently recording
    private var activeRecordingLabel by mutableStateOf<String?>(null)
    private var connectionStatus by mutableStateOf("Ready")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Wearable.getMessageClient(this).addListener(this)

        setContent {
            TrainingDashboardScreen(
                status = connectionStatus,
                activeRecordingLabel = activeRecordingLabel,
                onTriggerStart = { mode, label -> startRemoteTraining(mode, label) },
                onTriggerStop = { stopRemoteTraining() }
            )
        }
    }

    private fun startRemoteTraining(mode: TrainingMode, label: String) {
        val request = TrainingRequest(mode, label)
        val json = Json.encodeToString(request)

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                connectionStatus = "No Watch Found"
                return@addOnSuccessListener
            }
            activeRecordingLabel = label // Set UI to recording state
            connectionStatus = "Recording on Watch..."

            nodes.forEach { node ->
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, "/besu/start_train", json.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }

    private fun stopRemoteTraining() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                // Send STOP signal
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, "/besu/stop_train", null)
            }
            connectionStatus = "Saving..."
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/besu/train_result") {
            val result = String(event.data, StandardCharsets.UTF_8)
            runOnUiThread {
                activeRecordingLabel = null // Reset UI
                connectionStatus = if(result == "OK") "Saved Successfully!" else "Save Failed"
                Toast.makeText(this, "Motion Captured!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(this)
        super.onDestroy()
    }
}

@Composable
fun TrainingDashboardScreen(
    status: String,
    activeRecordingLabel: String?,
    onTriggerStart: (TrainingMode, String) -> Unit,
    onTriggerStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Motion Tuner", style = MaterialTheme.typography.h4, color = Color.White)
        Text(status, color = if(activeRecordingLabel != null) Color.Red else Color.Green, modifier = Modifier.padding(bottom = 24.dp))

        // If recording, show HUGE Stop button
        if (activeRecordingLabel != null) {
            Card(
                backgroundColor = Color(0xFFB71C1C),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable(onClick = onTriggerStop)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RECORDING: $activeRecordingLabel", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        // using text for safety if icons missing
                        // or standard material icons
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_pause),
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("TAP TO STOP & SAVE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Normal Menu
            SectionHeader("Gesture Training")
            TrainCard("Train 'Hello' Wave", "👋") { onTriggerStart(TrainingMode.GESTURE, "wave") }
            TrainCard("Train 'Yes' Twist", "👍") { onTriggerStart(TrainingMode.GESTURE, "thumbs_up") }
            TrainCard("Train 'No' Shake", "🚫") { onTriggerStart(TrainingMode.GESTURE, "no") }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Anti-Noise (Filters)")
            TrainCard("Walking Motion", "🚶") { onTriggerStart(TrainingMode.NOISE, "walking") }
            TrainCard("Tremors / Spasms", "〰️") { onTriggerStart(TrainingMode.NOISE, "tremor") }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Emergencies")
            TrainCard("Train Panic Flail", "🚨") { onTriggerStart(TrainingMode.GROSS_MOTOR, "panic") }
        }
    }
}

@Composable
fun TrainCard(label: String, icon: String, onClick: () -> Unit) {
    Card(
        backgroundColor = Color(0xFF263238),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}