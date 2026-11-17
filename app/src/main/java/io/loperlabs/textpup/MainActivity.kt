package io.loperlabs.textpup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import io.loperlabs.textpup.service.RadioGatewayService
import io.loperlabs.textpup.telephony.SimCardInfo
import io.loperlabs.textpup.telephony.SimManager
import io.loperlabs.textpup.ui.screens.DashboardScreen
import io.loperlabs.textpup.ui.screens.OnboardingScreen
import io.loperlabs.textpup.ui.screens.QrScannerScreen
import io.loperlabs.textpup.ui.theme.TextPupHostTheme

enum class AppNavState {
    ONBOARDING,
    QR_SCANNER,
    DASHBOARD
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var simManager: SimManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("textpup_host_prefs", Context.MODE_PRIVATE)
        simManager = SimManager(this)

        val savedServerUrl = prefs.getString("server_url", null)
        val savedToken = prefs.getString("pairing_token", null)

        setContent {
            TextPupHostTheme {
                var navState by remember {
                    mutableStateOf(
                        if (!savedServerUrl.isNullOrBlank() && !savedToken.isNullOrBlank()) {
                            AppNavState.DASHBOARD
                        } else {
                            AppNavState.ONBOARDING
                        }
                    )
                }

                var currentServerUrl by remember { mutableStateOf(savedServerUrl ?: "") }
                var currentToken by remember { mutableStateOf(savedToken ?: "") }
                var activeSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }

                LaunchedEffect(navState) {
                    if (navState == AppNavState.DASHBOARD) {
                        activeSims = simManager.getActiveSimCards()
                    }
                }

                when (navState) {
                    AppNavState.ONBOARDING -> {
                        OnboardingScreen(
                            onAllPermissionsGranted = {
                                navState = AppNavState.QR_SCANNER
                            }
                        )
                    }
                    AppNavState.QR_SCANNER -> {
                        QrScannerScreen(
                            onPairingSuccess = { serverUrl, token ->
                                currentServerUrl = serverUrl
                                currentToken = token

                                prefs.edit()
                                    .putString("server_url", serverUrl)
                                    .putString("pairing_token", token)
                                    .apply()

                                startGatewayService(serverUrl, token)
                                navState = AppNavState.DASHBOARD
                            },
                            onCancel = {
                                navState = AppNavState.ONBOARDING
                            }
                        )
                    }
                    AppNavState.DASHBOARD -> {
                        val activityLogs by RadioGatewayService.activityLogs.collectAsState()
                        DashboardScreen(
                            isConnected = RadioGatewayService.isRunning,
                            serverUrl = currentServerUrl,
                            sims = activeSims,
                            batteryPercent = getBatteryPercentage(),
                            logs = activityLogs,
                            onSyncHistory = {
                                RadioGatewayService.instance?.onSyncHistoryRequested()
                            },
                            onUnpair = {
                                stopGatewayService()
                                prefs.edit().clear().apply()
                                navState = AppNavState.ONBOARDING
                            }
                        )
                    }
                }
            }
        }

        // If already paired on app launch, ensure service is active
        if (!savedServerUrl.isNullOrBlank() && !savedToken.isNullOrBlank()) {
            startGatewayService(savedServerUrl, savedToken)
        }
    }

    private fun startGatewayService(serverUrl: String, token: String) {
        try {
            val intent = Intent(this, RadioGatewayService::class.java).apply {
                action = RadioGatewayService.ACTION_START
                putExtra(RadioGatewayService.EXTRA_SERVER_URL, serverUrl)
                putExtra(RadioGatewayService.EXTRA_TOKEN, token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("TextPupHost", "startGatewayService error: ${e.message}", e)
        }
    }

    private fun stopGatewayService() {
        try {
            val intent = Intent(this, RadioGatewayService::class.java).apply {
                action = RadioGatewayService.ACTION_STOP
            }
            startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("TextPupHost", "stopGatewayService error: ${e.message}", e)
        }
    }

    private fun getBatteryPercentage(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
    }
}
