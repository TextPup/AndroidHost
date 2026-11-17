package io.loperlabs.textpup.network

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import io.loperlabs.textpup.telephony.ExtractedMmsPart
import io.loperlabs.textpup.telephony.SimCardInfo
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface GatewayMessageListener {
    fun onConnected()
    fun onDisconnected()
    fun onSendSmsRequested(messageId: String, recipient: String, body: String, simNumber: Int)
    fun onSyncHistoryRequested()
}

class GatewayWebSocketClient(
    private val context: Context,
    private val listener: GatewayMessageListener
) {
    private val TAG = "TextPupGatewayWS"
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var isIntentionalClose = false
    private var currentUrl: String = ""
    private var currentToken: String = ""

    fun connect(serverUrl: String, pairingToken: String) {
        currentUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
        if (!currentUrl.endsWith("/api/gateway/connect")) {
            currentUrl = currentUrl.trimEnd('/') + "/api/gateway/connect"
        }
        currentToken = pairingToken
        isIntentionalClose = false

        startConnection()
    }

    private fun startConnection() {
        val request = Request.Builder()
            .url(currentUrl)
            .addHeader("Authorization", "Bearer $currentToken")
            .addHeader("X-Client", "TextPupHost-Android/1.0.0")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to TextPup Server: $currentUrl")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingCommand(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket connection failed: ${t.message}")
                listener.onDisconnected()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                listener.onDisconnected()
                if (!isIntentionalClose && code != 4001) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (isIntentionalClose) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            Log.d(TAG, "Attempting auto-reconnect to TextPup Server...")
            startConnection()
        }
    }

    private fun handleIncomingCommand(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val action = json.optString("action")

            when (action) {
                "SEND_SMS" -> {
                    val messageId = json.getString("messageId")
                    val recipient = json.getString("recipient")
                    val body = json.getString("body")
                    val simNumber = json.optInt("simNumber", 1)
                    listener.onSendSmsRequested(messageId, recipient, body, simNumber)
                }
                "SYNC_HISTORY" -> {
                    listener.onSyncHistoryRequested()
                }
                "PING" -> {
                    sendPong()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming gateway command", e)
        }
    }

    fun reportDeviceStatus(sims: List<SimCardInfo>) {
        val batteryStatus = getBatteryPercentage()
        val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: android.os.Build.MODEL
        val payload = JSONObject().apply {
            put("event", "status:update")
            put("batteryPercent", batteryStatus)
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("simCount", sims.size)
            val simsArray = JSONArray()
            for (sim in sims) {
                val simObj = JSONObject().apply {
                    put("slotIndex", sim.slotIndex)
                    put("simNumber", sim.simNumber)
                    put("carrierName", sim.carrierName)
                    put("phoneNumber", sim.phoneNumber ?: "")
                    put("isDefault", sim.isDefault)
                }
                simsArray.put(simObj)
            }
            put("sims", simsArray)
        }
        sendJson(payload)
    }

    fun forwardSms(
        address: String,
        body: String,
        simNumber: Int,
        timestamp: Long,
        direction: String = "inbound",
        isSync: Boolean = false,
        gatewayMsgId: String? = null
    ) {
        val payload = JSONObject().apply {
            put("event", "sms:received")
            put("sender", address)
            put("body", body)
            put("simNumber", simNumber)
            put("timestamp", timestamp)
            put("direction", direction)
            put("isSync", isSync)
            if (gatewayMsgId != null) {
                put("gatewayMsgId", gatewayMsgId)
            }
        }
        sendJson(payload)
    }

    fun forwardInboundSms(sender: String, body: String, simNumber: Int, timestamp: Long, isSync: Boolean = false) {
        forwardSms(
            address = sender,
            body = body,
            simNumber = simNumber,
            timestamp = timestamp,
            direction = "inbound",
            isSync = isSync
        )
    }

    fun forwardInboundMms(
        sender: String,
        subject: String?,
        simNumber: Int,
        timestamp: Long,
        parts: List<ExtractedMmsPart>
    ) {
        val payload = JSONObject().apply {
            put("event", "mms:received")
            put("sender", sender)
            put("subject", subject ?: "")
            put("simNumber", simNumber)
            put("timestamp", timestamp)

            val partsArray = JSONArray()
            for (part in parts) {
                val p = JSONObject().apply {
                    put("partId", part.partId)
                    put("mimeType", part.mimeType)
                    put("name", part.name)
                    put("data", android.util.Base64.encodeToString(part.dataBytes, android.util.Base64.NO_WRAP))
                    put("size", part.sizeBytes)
                }
                partsArray.put(p)
            }
            put("attachments", partsArray)
        }
        sendJson(payload)
    }

    fun reportSendResult(messageId: String, success: Boolean, error: String? = null) {
        val payload = JSONObject().apply {
            put("event", "message:sent_status")
            put("messageId", messageId)
            put("status", if (success) "delivered" else "failed")
            if (error != null) put("error", error)
        }
        sendJson(payload)
    }

    private fun sendPong() {
        val payload = JSONObject().apply {
            put("event", "PONG")
            put("timestamp", System.currentTimeMillis())
        }
        sendJson(payload)
    }

    private fun sendJson(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    private fun getBatteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    fun disconnect() {
        isIntentionalClose = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
