package com.example.besu.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

// This service lives on the WATCH background.
class WearConfigListenerService : WearableListenerService() {

    companion object {
        const val PATH_CONFIG_PUSH = "/besu/config_push"
        const val PATH_START_TRAIN = "/besu/start_train"
        const val PATH_STOP_TRAIN = "/besu/stop_train" // NEW
        const val PATH_CONFIG_ACK = "/besu/config_ack"
        const val CONFIG_FILENAME = "watch_config.json"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val nodeId = messageEvent.sourceNodeId

        when (messageEvent.path) {
            // CASE 1: UI Configuration Update
            PATH_CONFIG_PUSH -> {
                Log.d("BesuWear", "Background Config Received")
                try {
                    val json = String(messageEvent.data, Charsets.UTF_8)
                    val file = File(filesDir, CONFIG_FILENAME)
                    file.writeText(json)

                    // Broadcast to UI
                    val intent = Intent("BESU_WATCH_CONFIG_UPDATE")
                    intent.setPackage(packageName)
                    intent.putExtra("config_json", json)
                    sendBroadcast(intent)

                    // ACK
                    Wearable.getMessageClient(this).sendMessage(nodeId, PATH_CONFIG_ACK, null)

                } catch (e: Exception) {
                    Log.e("BesuWear", "Config parse error", e)
                }
            }

            // CASE 2: Remote Training Trigger (Start)
            PATH_START_TRAIN -> {
                Log.d("BesuWear", "Remote Training Trigger Received")
                try {
                    val json = String(messageEvent.data, Charsets.UTF_8)

                    val intent = Intent(this, TrainingActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra("MODE_JSON", json)
                    startActivity(intent)

                } catch (e: Exception) {
                    Log.e("BesuWear", "Training trigger error", e)
                }
            }

            // CASE 3: Remote Training Stop (NEW)
            PATH_STOP_TRAIN -> {
                Log.d("BesuWear", "Remote Stop Received")
                // Broadcast to the active TrainingActivity to stop recording
                val intent = Intent("BESU_REMOTE_STOP")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
    }
}