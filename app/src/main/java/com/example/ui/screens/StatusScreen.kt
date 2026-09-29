package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DotState
import com.example.ui.components.CircularBatteryGauge
import com.example.ui.components.SentinelCard
import com.example.ui.components.SparklineChart
import com.example.ui.components.StatusDot
import com.example.ui.navigation.NetraTab
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraTeal
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.UsageStatsHelper
import com.example.viewmodel.NetraViewModel

@Composable
fun StatusScreen(
    viewModel: NetraViewModel,
    onNavigateTab: (NetraTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sparklineRecords by viewModel.sparkline1HourRecords.collectAsStateWithLifecycle()
    val totalRecords by viewModel.totalRecordCount.collectAsStateWithLifecycle()
    val overheatCount by viewModel.overheatCount.collectAsStateWithLifecycle()
    val deepDischargeCount by viewModel.deepDischargeCount.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentChargingSessions.collectAsStateWithLifecycle()
    val appUsageList by viewModel.appUsageDrain.collectAsStateWithLifecycle()
    val btDevices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val permissions by viewModel.systemPermissions.collectAsStateWithLifecycle()
    val recentLogs by viewModel.activityLogs.collectAsStateWithLifecycle()

    val quickAi by viewModel.quickAiDiagnostic.collectAsStateWithLifecycle()
    val isQuickAiLoading by viewModel.isQuickAiLoading.collectAsStateWithLifecycle()
    val deepAi by viewModel.deepAiDiagnostic.collectAsStateWithLifecycle()
    val isDeepAiLoading by viewModel.isDeepAiLoading.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 🔥 Critical Overheat Banner (>45°C)
        AnimatedVisibility(visible = telemetry.isCriticalOverheat) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DangerRed.copy(alpha = 0.2f))
                    .border(2.dp, DangerRed, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("critical_overheat_banner")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DangerRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Overheat",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔥 CRITICAL OVERHEAT: ${String.format("%.1f", telemetry.temperature)}°C",
                            color = DangerRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Unplug charger immediately and let device cool down to prevent permanent cell damage.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // A. Live Battery Card (Circular Gauge + Service Status)
        SentinelCard(
            title = "Live Battery Sentinel",
            icon = Icons.Default.Dashboard,
            dotState = telemetry.serviceDotState,
            accentColor = NetraEmerald,
            trailingAction = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Service: Stream Connected",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetraEmerald
                    )
                }
            }
        ) {
            CircularBatteryGauge(telemetry = telemetry)
        }

        // Gemini AI Battery & Thermal Diagnostic Intelligence
        SentinelCard(
            title = "Netra AI Battery Intelligence",
            icon = Icons.Default.AutoAwesome,
            dotState = DotState.CONNECTED,
            accentColor = NetraCyan
        ) {
            Text(
                text = "Live electrochemical triage & thermal degradation forecasting backed by Gemini AI.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.runQuickAiTriage() },
                    modifier = Modifier.weight(1f).testTag("quick_ai_triage_button"),
                    enabled = !isQuickAiLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = NetraCyan.copy(alpha = 0.2f), contentColor = NetraCyan)
                ) {
                    if (isQuickAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NetraCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quick Triage", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { viewModel.runDeepThinkingAnalysis() },
                    modifier = Modifier.weight(1f).testTag("deep_ai_thinking_button"),
                    enabled = !isDeepAiLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = NetraEmerald.copy(alpha = 0.2f), contentColor = NetraEmerald)
                ) {
                    if (isDeepAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NetraEmerald, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deep Analysis", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick AI Result
            quickAi?.let { res ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NetraCyan.copy(alpha = 0.1f))
                        .border(1.dp, NetraCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = NetraCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quick Diagnostic (gemini-3.1-flash-lite)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraCyan)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(res.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("💡 ${res.optimalChargingAdvice}", fontSize = 11.sp, color = NetraEmerald, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Deep Thinking AI Result
            deepAi?.let { res ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NetraEmerald.copy(alpha = 0.1f))
                        .border(1.dp, NetraEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = NetraEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deep Sentinel Thinking (gemini-3.1-pro - HIGH)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Risk: ${res.degradationRisk}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StatusAmber)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Thermal: ${res.thermalAnalysis}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        res.recommendedActions.forEach { action ->
                            Text("• $action", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Gemini Health Insights (Firebase AI SDK & ML Degradation/Failure Predictor)
        com.example.ui.components.GeminiHealthInsightsCard(viewModel = viewModel)

        // B. Battery Saving Engine
        SentinelCard(
            title = "Battery Saving Engine",
            icon = Icons.Default.BatterySaver,
            dotState = if (settings.powerSaverEnabled) DotState.CONNECTED else DotState.STANDBY,
            accentColor = NetraEmerald
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ultra-Low Power Background Sentinel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Throttles background poll interval to 300s when screen off",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.powerSaverEnabled,
                    onCheckedChange = { viewModel.setPowerSaverEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NetraEmerald, checkedTrackColor = NetraEmerald.copy(alpha = 0.3f)),
                    modifier = Modifier.testTag("power_saver_toggle")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Brightness Optimization Guard",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Prevents display backlight battery drain spikes",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.brightnessOptimization,
                    onCheckedChange = { viewModel.setBrightnessOptimization(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NetraEmerald, checkedTrackColor = NetraEmerald.copy(alpha = 0.3f)),
                    modifier = Modifier.testTag("brightness_opt_toggle")
                )
            }
        }

        // C. Thermal Sentinel
        SentinelCard(
            title = "Thermal Sentinel",
            icon = Icons.Default.DeviceThermostat,
            dotState = if (telemetry.temperature >= 40f) DotState.CRITICAL else DotState.CONNECTED,
            accentColor = if (telemetry.temperature >= 40f) StatusRed else StatusAmber
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current: ${String.format("%.1f", telemetry.temperature)}°C",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (telemetry.temperature >= 40f) StatusRed else NetraCyan
                    )
                    Text(
                        text = if (telemetry.distanceTo40C > 0)
                            "${String.format("%.1f", telemetry.distanceTo40C)}°C below 40°C threshold"
                        else
                            "${String.format("%.1f", kotlin.math.abs(telemetry.distanceTo40C))}°C ABOVE safe limit",
                        fontSize = 11.sp,
                        color = if (telemetry.distanceTo40C > 0) NetraEmerald else DangerRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Velocity: ${String.format("%+.2f", telemetry.thermalVelocity)} °C/min",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    telemetry.predictedThrottlingMinutes?.let { mins ->
                        Text(
                            text = "Throttling ETA: ~$mins min",
                            fontSize = 11.sp,
                            color = StatusAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "1-Hour Thermal Trend (40°C Red Safety Limit)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            SparklineChart(records = sparklineRecords, dangerThreshold = 40.0f)
        }

        // D. Charging Intelligence
        SentinelCard(
            title = "Charging Intelligence",
            icon = Icons.Default.Bolt,
            dotState = if (telemetry.isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = NetraCyan,
            trailingAction = {
                Text(
                    text = "Target: ${settings.chargeTargetPercent}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraCyan
                )
            }
        ) {
            if (telemetry.isCharging) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Active Charging", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${telemetry.pluggedType} (${telemetry.chargingSpeedLabel})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Estimated Time to 100%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = telemetry.timeToFullMinutes?.let { "~$it mins" } ?: "Calculating...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetraCyan
                        )
                    }
                }
            } else {
                Text(
                    text = "Charger disconnected. Target 80% unplug alert is armed.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            recentSessions.firstOrNull()?.let { session ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Last Session: ${session.startLevel}% → ${session.endLevel}% in ${session.durationMinutes}m (Peak: ${session.peakTemperature}°C)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // E. Battery Health Advisor
        SentinelCard(
            title = "Battery Health Advisor",
            icon = Icons.Default.HealthAndSafety,
            dotState = DotState.CONNECTED,
            accentColor = NetraEmerald
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Health Score: ${telemetry.healthScore}/100",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NetraEmerald
                    )
                    Text(text = "Grade: ${telemetry.healthGrade}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Overheats: $overheatCount", fontSize = 11.sp, color = if (overheatCount > 0) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Deep Discharges: $deepDischargeCount", fontSize = 11.sp, color = if (deepDischargeCount > 0) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "80% Target Unplug Alarm", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = settings.unplugAlarmEnabled,
                    onCheckedChange = { viewModel.setUnplugAlarmEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NetraCyan, checkedTrackColor = NetraCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.testTag("unplug_alarm_switch")
                )
            }
        }

        // F. Application Monitor Preview
        SentinelCard(
            title = "Application Monitor Preview",
            icon = Icons.Default.Security,
            dotState = if (permissions.isUsageStatsGranted) DotState.CONNECTED else DotState.THROTTLED,
            accentColor = NetraTeal,
            trailingAction = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Monitoring",
                    tint = NetraCyan,
                    modifier = Modifier.clickable { onNavigateTab(NetraTab.MONITORING) }
                )
            }
        ) {
            if (permissions.isUsageStatsGranted && appUsageList.isNotEmpty()) {
                val topApp = appUsageList.first()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Top Active: ${topApp.appName}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Foreground: ${topApp.foregroundTimeMinutes}m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "~${topApp.estimatedDrainPercent}% drain",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (topApp.isHighDrain) StatusAmber else NetraEmerald
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Usage Access needed for app drain stats", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { UsageStatsHelper.openUsageAccessSettings(context) }) {
                        Text("Grant", fontSize = 11.sp)
                    }
                }
            }
        }

        // G. Device Monitor Preview
        SentinelCard(
            title = "Device Monitor Preview",
            icon = Icons.Default.Headset,
            dotState = if (permissions.isBluetoothGranted) DotState.CONNECTED else DotState.THROTTLED,
            accentColor = StatusBlue,
            trailingAction = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Devices",
                    tint = StatusBlue,
                    modifier = Modifier.clickable { onNavigateTab(NetraTab.DEVICES) }
                )
            }
        ) {
            val connectedCount = btDevices.count { it.isConnected }
            Text(
                text = "Connected Bluetooth Devices: $connectedCount / ${btDevices.size} Paired",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (btDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                btDevices.take(2).forEach { dev ->
                    Text(
                        text = "• ${dev.name} (${dev.batteryPercent?.let { "$it%" } ?: "Battery: Unavailable"})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // H. System Access & Permissions
        SentinelCard(
            title = "System Access & Permissions",
            icon = Icons.Default.Shield,
            dotState = DotState.CONNECTED,
            accentColor = NetraEmerald
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PermissionStatusRow("Notifications (Alerts)", permissions.isNotificationGranted)
                PermissionStatusRow("Bluetooth Telemetry", permissions.isBluetoothGranted)
                PermissionStatusRow("App Usage Access", permissions.isUsageStatsGranted)
                PermissionStatusRow("Battery Optimization Exemption", permissions.isIgnoringBatteryOptimizations)
            }
        }

        // I. Local History Preview (Room DB)
        SentinelCard(
            title = "Local History Preview (Room DB)",
            icon = Icons.Default.History,
            dotState = DotState.CONNECTED,
            accentColor = NetraEmerald,
            trailingAction = {
                Text(
                    text = "$totalRecords Records",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraEmerald
                )
            }
        ) {
            Text(
                text = "SQLite Room Database maintains debounced telemetry without wake-lock power waste.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onNavigateTab(NetraTab.GRAPH) },
                modifier = Modifier.fillMaxWidth().testTag("view_full_graph_button")
            ) {
                Text("Open Interactive Telemetry Graph", fontSize = 12.sp)
            }
        }

        // J. Activity & Updates
        SentinelCard(
            title = "Activity & Updates",
            icon = Icons.Default.Storage,
            dotState = DotState.CONNECTED,
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            val lastLog = recentLogs.firstOrNull()
            Text(
                text = "Latest Event: ${lastLog?.title ?: "Sentinel Initialized"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = lastLog?.message ?: "24/7 Autonomous Low-Power Engine Active",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "App: Battery Sentinel Pro Netra v1.0 • Autonomous Engine", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionStatusRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) StatusGreen else StatusAmber)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = if (isGranted) "GRANTED" else "REQUIRED",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) StatusGreen else StatusAmber
        )
    }
}
