package io.loperlabs.textpup.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.loperlabs.textpup.R
import io.loperlabs.textpup.service.ActivityLog
import io.loperlabs.textpup.telephony.SimCardInfo
import io.loperlabs.textpup.ui.theme.*

@Composable
fun DashboardScreen(
    isConnected: Boolean,
    serverUrl: String,
    sims: List<SimCardInfo>,
    batteryPercent: Int,
    logs: List<ActivityLog> = emptyList(),
    onSyncHistory: () -> Unit,
    onUnpair: () -> Unit
) {
    var isSyncing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_textpup_logo),
                    contentDescription = "TextPup",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "TextPup Host",
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Cellular Radio Enclave",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Connection Status Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isConnected) TextPupEmerald.copy(alpha = 0.12f) else TextPupRose.copy(alpha = 0.12f),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isConnected) TextPupEmerald.copy(alpha = 0.3f) else TextPupRose.copy(alpha = 0.3f)
                    )
                )
            ) {
                Text(
                    text = if (isConnected) "ONLINE" else "CONNECTING",
                    color = if (isConnected) TextPupEmerald else TextPupRose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Active Dual-SIM Radios
            item {
                Text(
                    text = "ACTIVE CELLULAR RADIOS",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (sims.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SimCardAlert, contentDescription = null, tint = TextPupAmber)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("No active SIM cards detected in device slots.", color = TextSecondary, fontSize = 13.sp)
                            }
                        } else {
                            sims.forEachIndexed { index, sim ->
                                if (index > 0) {
                                    Divider(
                                        color = SlateBorderSubtle,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                                SimSlotRow(sim)
                            }
                        }
                    }
                }
            }

            // Section 2: Host Phone Hardware Telemetry
            item {
                Text(
                    text = "HOST TELEMETRY",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Battery Level", color = TextMuted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BatteryStd, contentDescription = null, tint = TextPupEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (batteryPercent >= 0) "$batteryPercent%" else "100%",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Column {
                            Text("Connected Server", color = TextMuted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lan, contentDescription = null, tint = TextPupIndigoLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = serverUrl.replace("http://", "").replace("https://", "").take(18),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: One-Tap History Sync
            item {
                Button(
                    onClick = {
                        isSyncing = true
                        onSyncHistory()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TextPupIndigo)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSyncing) "Syncing SMS & MMS Over Wi-Fi..." else "Sync Phone History Over Wi-Fi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Section 4: Live Activity Console (Bounded Scrollable Log Window)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RADIO ACTIVITY LOG",
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SlateSurfaceElevated
                            ) {
                                Text(
                                    text = "${logs.size} events",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (logs.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Radio, contentDescription = null, tint = TextPupIndigoLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Standing by for cellular radio activity...", color = TextSecondary, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(logs, key = { it.id }) { log ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SlateSurfaceElevated, RoundedCornerShape(10.dp))
                                            .border(1.dp, SlateBorderSubtle, RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(if (log.isIncoming) TextPupEmerald.copy(alpha = 0.12f) else TextPupIndigo.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (log.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                                                contentDescription = null,
                                                tint = if (log.isIncoming) TextPupEmerald else TextPupIndigoLight,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(log.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text(log.time, color = TextMuted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onUnpair,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPupRose),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect & Unpair Gateway", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SimSlotRow(sim: SimCardInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (sim.simNumber == 1) TextPupIndigo.copy(alpha = 0.2f) else TextPupAmber.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "SIM ${sim.simNumber}",
                    color = if (sim.simNumber == 1) TextPupIndigoLight else TextPupAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = sim.carrierName,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = sim.phoneNumber ?: "Active Carrier Sub ${sim.subscriptionId}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = TextPupEmerald, modifier = Modifier.size(20.dp))
    }
}
