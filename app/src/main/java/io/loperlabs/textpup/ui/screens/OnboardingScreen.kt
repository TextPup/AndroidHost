package io.loperlabs.textpup.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import io.loperlabs.textpup.R
import io.loperlabs.textpup.ui.theme.*

@Composable
fun OnboardingScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var hasSmsPermissions by remember { mutableStateOf(checkSmsPermissions(context)) }
    var hasPhonePermissions by remember { mutableStateOf(checkPhonePermissions(context)) }
    var hasCameraPermission by remember { mutableStateOf(checkCameraPermission(context)) }
    var hasBatteryExemption by remember { mutableStateOf(checkBatteryExemption(context)) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasSmsPermissions = checkSmsPermissions(context)
    }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasPhonePermissions = checkPhonePermissions(context)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasCameraPermission = checkCameraPermission(context)
    }

    val allGranted = hasSmsPermissions && hasPhonePermissions && hasCameraPermission && hasBatteryExemption

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Official Mascot / Logo
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SlateSurfaceElevated)
                .border(1.dp, SlateBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_textpup_logo),
                contentDescription = "TextPup",
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "TextPup Radio Enclave",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Transform this phone into a secure, sovereign cellular station for your TextPup server.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Card 1: Cellular Messaging
        PermissionCapabilityCard(
            title = "Cellular Messaging",
            description = "Send and receive SMS and multimedia MMS across your SIM cards.",
            icon = Icons.Default.Chat,
            isGranted = hasSmsPermissions,
            onGrant = {
                smsLauncher.launch(
                    arrayOf(
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_MMS
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Dual-SIM Detection
        PermissionCapabilityCard(
            title = "Dual-SIM Detection",
            description = "Identify SIM 1 and SIM 2 carriers (e.g. Helium, Dark Star) and slot routing.",
            icon = Icons.Default.SimCard,
            isGranted = hasPhonePermissions,
            onGrant = {
                phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Camera (Pairing)
        PermissionCapabilityCard(
            title = "Camera (Pairing Only)",
            description = "Scan the secure zero-trust QR pairing code on your computer screen.",
            icon = Icons.Default.QrCodeScanner,
            isGranted = hasCameraPermission,
            onGrant = {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 4: Background Exemption
        PermissionCapabilityCard(
            title = "Uninterrupted Radio Mode",
            description = "Keep gateway awake when phone is locked so texts arrive immediately.",
            icon = Icons.Default.BatteryChargingFull,
            isGranted = hasBatteryExemption,
            onGrant = {
                requestBatteryExemption(context)
                hasBatteryExemption = checkBatteryExemption(context)
            }
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Bottom CTA Button
        Button(
            onClick = onAllPermissionsGranted,
            enabled = allGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPupIndigo,
                disabledContainerColor = SlateSurfaceElevated
            )
        ) {
            Text(
                text = if (allGranted) "Pair with TextPup Server →" else "Grant Permissions Above to Proceed",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (allGranted) Color.White else TextMuted
            )
        }
    }
}

@Composable
private fun PermissionCapabilityCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isGranted) SlateBorderSubtle else SlateBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) TextPupEmerald.copy(alpha = 0.15f) else TextPupIndigo.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (isGranted) TextPupEmerald else TextPupIndigo,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (!isGranted) {
                OutlinedButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPupIndigo),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TextPupIndigo)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TextPupEmerald.copy(alpha = 0.15f),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(
                        text = "READY",
                        color = TextPupEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun checkSmsPermissions(context: Context): Boolean {
    val send = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PermissionChecker.PERMISSION_GRANTED
    val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PermissionChecker.PERMISSION_GRANTED
    val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PermissionChecker.PERMISSION_GRANTED
    return send && receive && read
}

private fun checkPhonePermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PermissionChecker.PERMISSION_GRANTED
}

private fun checkCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED
}

private fun checkBatteryExemption(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
}

private fun requestBatteryExemption(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
