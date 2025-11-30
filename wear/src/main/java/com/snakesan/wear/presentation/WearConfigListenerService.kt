package com.example.besu.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

// This service lives ONLY on the WATCH.
// It listens for configuration commands coming FROM the Phone.
class WearConfigListenerService : WearableListenerService() {

    companion object {
        // UI Layout Configuration (Keep)
        const val PATH_CONFIG_PUSH = "/besu/config_push"
        const val PATH_CONFIG_ACK = "/besu/config_ack"

        const val CONFIG_FILENAME = "watch_config.json"

        // DELETED: PATH_START_TRAIN, PATH_STOP_TRAIN, etc.
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val nodeId = messageEvent.sourceNodeId

        when (messageEvent.path) {
            // 1. UPDATE WATCH FACE UI
            PATH_CONFIG_PUSH -> {
                Log.d("BesuWear", "Config Received")
                try {
                    val json = String(messageEvent.data, Charsets.UTF_8)
                    File(filesDir, CONFIG_FILENAME).writeText(json)

                    // Notify active UI to refresh
                    val intent = Intent("BESU_WATCH_CONFIG_UPDATE")
                    intent.setPackage(packageName)
                    intent.putExtra("config_json", json)
                    sendBroadcast(intent)

                    // Send ACK
                    Wearable.getMessageClient(this).sendMessage(nodeId, PATH_CONFIG_ACK, byteArrayOf())
                } catch (e: Exception) { e.printStackTrace() }
            }
            // DELETED: The cases for START_TRAIN, STOP_TRAIN, PROFILE_PUSH are gone.
        }
    }
}