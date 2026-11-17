package io.loperlabs.textpup.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.loperlabs.textpup.MainActivity
import io.loperlabs.textpup.R
import io.loperlabs.textpup.network.GatewayMessageListener
import io.loperlabs.textpup.network.GatewayWebSocketClient
import io.loperlabs.textpup.telephony.HistoryCrawler
import io.loperlabs.textpup.telephony.MmsPartExtractor
import io.loperlabs.textpup.telephony.SimManager
import io.loperlabs.textpup.telephony.SmsDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ActivityLog(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val time: String,
    val isIncoming: Boolean
)

class RadioGatewayService : Service(), GatewayMessageListener {

    companion object {
        const val ACTION_START = "io.loperlabs.textpup.action.START"
        const val ACTION_STOP = "io.loperlabs.textpup.action.STOP"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_TOKEN = "pairing_token"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "textpup_radio_enclave"

        var isRunning: Boolean = false
            private set
        var instance: RadioGatewayService? = null
            private set

        private val _activityLogs = MutableStateFlow<List<ActivityLog>>(
            listOf(
                ActivityLog(
                    title = "Radio Enclave Initialized",
                    time = formatTime(System.currentTimeMillis()),
                    isIncoming = true
                )
            )
        )
        val activityLogs: StateFlow<List<ActivityLog>> = _activityLogs.asStateFlow()

        fun logActivity(title: String, isIncoming: Boolean) {
            val log = ActivityLog(
                title = title,
                time = formatTime(System.currentTimeMillis()),
                isIncoming = isIncoming
            )
            _activityLogs.update { (listOf(log) + it).take(50) }
        }

        private fun formatTime(millis: Long): String {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    lateinit var simManager: SimManager
        private set
    lateinit var smsDispatcher: SmsDispatcher
        private set
    lateinit var mmsExtractor: MmsPartExtractor
        private set
    lateinit var wsClient: GatewayWebSocketClient
        private set

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        simManager = SimManager(this)
        smsDispatcher = SmsDispatcher(this)
        mmsExtractor = MmsPartExtractor(this)
        wsClient = GatewayWebSocketClient(this, this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TextPupHost:RadioWakeLock").apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundGateway()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL)
                val token = intent?.getStringExtra(EXTRA_TOKEN)

                startForegroundGateway()

                if (!serverUrl.isNullOrBlank() && !token.isNullOrBlank()) {
                    wsClient.connect(serverUrl, token)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundGateway() {
        isRunning = true
        try {
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24hr safeguard limit
        } catch (e: Exception) {
            android.util.Log.w("TextPupHost", "WakeLock acquire notice: ${e.message}")
        }

        val notification = buildNotification("TextPup Radio Enclave Active", "Connecting to server...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (secEx: Exception) {
            android.util.Log.e("TextPupHost", "Typed startForeground failed: ${secEx.message}, attempting fallback", secEx)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fallbackEx: Exception) {
                android.util.Log.e("TextPupHost", "Fallback startForeground failed", fallbackEx)
            }
        }
    }

    private fun stopForegroundGateway() {
        isRunning = false
        wsClient.disconnect()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onConnected() {
        val sims = simManager.getActiveSimCards()
        val simSummary = if (sims.isNotEmpty()) {
            sims.joinToString(" • ") { "SIM ${it.simNumber}: ${it.carrierName}" }
        } else {
            "Cellular radios ready"
        }

        updateNotification("Connected to TextPup", simSummary)
        wsClient.reportDeviceStatus(sims)
        logActivity("Connected to TextPup Server", isIncoming = true)
    }

    override fun onDisconnected() {
        updateNotification("Disconnected", "Reconnecting to TextPup...")
        logActivity("Disconnected (Reconnecting to Server...)", isIncoming = false)
    }

    override fun onSendSmsRequested(messageId: String, recipient: String, body: String, simNumber: Int) {
        serviceScope.launch {
            val subId = simManager.getSubIdForSimNumber(simNumber)
            val result = smsDispatcher.sendSms(subId, recipient, body, messageId)
            wsClient.reportSendResult(messageId, result.isSuccess, result.exceptionOrNull()?.message)
            if (result.isSuccess) {
                logActivity("Sent SMS to $recipient (SIM $simNumber)", isIncoming = false)
            } else {
                logActivity("Failed to send SMS to $recipient (SIM $simNumber)", isIncoming = false)
            }
        }
    }

    override fun onSyncHistoryRequested() {
        serviceScope.launch {
            logActivity("Starting Wi-Fi History Sync...", isIncoming = true)
            val crawler = HistoryCrawler(this@RadioGatewayService, simManager)
            val msgs = crawler.crawlSms(limit = 1000)
            // Process oldest to newest so threads end on the latest message state
            val chronological = msgs.reversed()
            for (msg in chronological) {
                wsClient.forwardSms(
                    address = msg.address,
                    body = msg.body ?: "",
                    simNumber = msg.simSlot,
                    timestamp = msg.timestamp,
                    direction = msg.direction,
                    isSync = true,
                    gatewayMsgId = "android_${msg.id}"
                )
            }
            logActivity("Synced ${msgs.size} messages over Wi-Fi", isIncoming = true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Fallback system vector
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        stopForegroundGateway()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
