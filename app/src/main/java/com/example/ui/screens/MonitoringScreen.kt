package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.AppUsageItem
import com.example.model.BluetoothDeviceItem
import com.example.model.DotState
import com.example.ui.components.SentinelCard
import com.example.ui.components.StatusDot
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.UsageStatsHelper
import com.example.viewmodel.NetraViewModel

enum class MonitoringSubTab {
    APPS,
    DEVICES,
    SYSTEM
}

@Composable
fun MonitoringScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableStateOf(MonitoringSubTab.APPS) }

    val appUsageList by viewModel.appUsageDrain.collectAsStateWithLifecycle()
    val btDevices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val permissions by viewModel.systemPermissions.collectAsStateWithLifecycle()
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val totalRecords by viewModel.totalRecordCount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Sub-Tab Row (Apps, Devices, System Telemetry)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetraSurface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SubTabButton(
                title = "Apps Tab",
                icon = Icons.Default.Apps,
                isSelected = selectedSubTab == MonitoringSubTab.APPS,
                onClick = { selectedSubTab = MonitoringSubTab.APPS },
                modifier = Modifier.weight(1f)
            )
            SubTabButton(
                title = "Devices Tab",
                icon = Icons.Default.Headset,
                isSelected = selectedSubTab == MonitoringSubTab.DEVICES,
                onClick = { selectedSubTab = MonitoringSubTab.DEVICES },
                modifier = Modifier.weight(1f)
            )
            SubTabButton(
                title = "System Telemetry",
                icon = Icons.Default.Memory,
                isSelected = selectedSubTab == MonitoringSubTab.SYSTEM,
                onClick = { selectedSubTab = MonitoringSubTab.SYSTEM },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedSubTab) {
            MonitoringSubTab.APPS -> {
                AppsTabContent(
                    appUsageList = appUsageList,
                    isUsageAccessGranted = permissions.isUsageStatsGranted,
                    onOpenSettings = { UsageStatsHelper.openUsageAccessSettings(context) }
                )
            }
            MonitoringSubTab.DEVICES -> {
                DevicesTabContent(
                    btDevices = btDevices,
                    isBluetoothGranted = permissions.isBluetoothGranted,
                    onRefresh = { viewModel.refreshHardwareState() }
                )
            }
            MonitoringSubTab.SYSTEM -> {
                SystemTelemetryTabContent(
                    telemetry = telemetry,
                    totalRecords = totalRecords,
                    powerSaverEnabled = settings.powerSaverEnabled
                )
            }
        }
    }
}

@Composable
private fun SubTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NetraCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) NetraCyan.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) NetraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) NetraCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppsTabContent(
    appUsageList: List<AppUsageItem>,
    isUsageAccessGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Application Power Drain Monitor",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isUsageAccessGranted) {
                    OutlinedButton(onClick = onOpenSettings) {
                        Text("Grant Usage Access", fontSize = 11.sp)
                    }
                }
            }
        }

        if (!isUsageAccessGranted) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusAmber.copy(alpha = 0.15f))
                        .border(1.dp, StatusAmber, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Usage Access Permission Required",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To track real foreground execution and battery drain per application, grant Usage Access in Android Settings.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.Black)
                        ) {
                            Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (appUsageList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Waiting for data. App usage stats are refreshing...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(appUsageList) { app ->
                AppUsageRow(app = app)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun AppUsageRow(app: AppUsageItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (app.isHighDrain) StatusAmber.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (app.isHighDrain) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StatusAmber.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("HIGH DRAIN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${app.packageName} • Foreground: ${app.foregroundTimeMinutes} mins",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "~${app.estimatedDrainPercent}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (app.isHighDrain) StatusAmber else NetraEmerald
            )
        }
    }
}

@Composable
private fun DevicesTabContent(
    btDevices: List<BluetoothDeviceItem>,
    isBluetoothGranted: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bluetooth Hardware & Peripherals",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = NetraCyan)
                }
            }
        }

        if (btDevices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!isBluetoothGranted) "Bluetooth permission needed to query peripherals."
                        else "No paired or connected Bluetooth peripherals detected.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(btDevices) { dev ->
                BluetoothDeviceRow(dev = dev)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun BluetoothDeviceRow(dev: BluetoothDeviceItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (dev.isConnected) NetraEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (dev.isConnected) StatusGreen else StatusAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dev.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${dev.deviceType} • ${dev.profile}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (dev.batteryPercent != null) "${dev.batteryPercent}%" else "Battery: Unavailable",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dev.batteryPercent != null) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (dev.isConnected) "CONNECTED" else "PAIRED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dev.isConnected) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SystemTelemetryTabContent(
    telemetry: com.example.model.BatteryTelemetry,
    totalRecords: Int,
    powerSaverEnabled: Boolean
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SentinelCard(
                title = "Hardware Telemetry Sensors",
                icon = Icons.Default.Memory,
                dotState = DotState.CONNECTED,
                accentColor = NetraCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryRow("Battery Chemistry", telemetry.technology)
                    TelemetryRow("Android OS Health Status", telemetry.healthString)
                    TelemetryRow("Hardware Voltage", if (telemetry.isDataAvailable) "${telemetry.voltageMv} mV" else "Unavailable")
                    TelemetryRow("Instantaneous Current", if (telemetry.isDataAvailable && telemetry.currentMa != 0) "${telemetry.currentMa} mA" else "Unavailable (OEM restricted)")
                    TelemetryRow("Active Power Computation", if (telemetry.isDataAvailable) "${String.format("%.2f", telemetry.powerWatts)} W" else "Unavailable")
                    TelemetryRow("Screen State", if (telemetry.isScreenOn) "Active (Screen ON)" else "Standby (Screen OFF)")
                    TelemetryRow("Background Polling Mode", if (telemetry.isScreenOn) "Active (60-90s)" else "Ultra-Low Power (300-600s)")
                    TelemetryRow("Room Database Records", "$totalRecords Stored Records")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
