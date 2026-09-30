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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BluetoothDeviceItem
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.DangerRed
import com.example.viewmodel.NetraViewModel

@Composable
fun DevicesScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val btDevices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val btHistory by viewModel.bluetoothHistory.collectAsStateWithLifecycle()
    val permissions by viewModel.systemPermissions.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("LIVE") }

    val filteredDevices = if (activeTab == "LIVE") {
        btDevices.filter { it.isConnected }
    } else {
        btHistory.filter { !it.isConnected }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bluetooth Device Sentinel",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Peripherals battery telemetry & power drain tracker",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshHardwareState() },
                    modifier = Modifier.testTag("refresh_devices_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = NetraCyan)
                }
            }
        }

        // Live vs History tab switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == "LIVE",
                    onClick = { activeTab = "LIVE" },
                    label = { Text("LIVE CONNECTED (${btDevices.filter { it.isConnected }.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NetraEmerald.copy(alpha = 0.25f),
                        selectedLabelColor = NetraEmerald
                    ),
                    modifier = Modifier.weight(1f).testTag("tab_live_bt")
                )
                FilterChip(
                    selected = activeTab == "HISTORY",
                    onClick = { activeTab = "HISTORY" },
                    label = { Text("HISTORY (${btHistory.filter { !it.isConnected }.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NetraCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NetraCyan
                    ),
                    modifier = Modifier.weight(1f).testTag("tab_history_bt")
                )
            }
        }

        if (filteredDevices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Headset, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (!permissions.isBluetoothGranted) {
                                "Bluetooth permission needed to query device batteries."
                            } else if (activeTab == "LIVE") {
                                "No connected Bluetooth devices. Connect a peripheral to trace power telemetry."
                            } else {
                                "No registered historical Bluetooth devices."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredDevices) { dev ->
                DeviceSentinelCard(device = dev, isLive = activeTab == "LIVE")
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceSentinelCard(device: BluetoothDeviceItem, isLive: Boolean) {
    // Battery Color Visual States Mapping
    val batteryColor = when {
        device.batteryPercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        device.batteryPercent!! >= 75 -> NetraEmerald
        device.batteryPercent!! >= 50 -> StatusGreen
        device.batteryPercent!! >= 20 -> StatusAmber
        else -> DangerRed
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isLive) NetraEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLive) NetraEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (device.deviceType.contains("Watch")) Icons.Default.Watch else Icons.Default.Headset,
                            contentDescription = null,
                            tint = if (isLive) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = device.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = device.deviceType,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Battery Badge (Battery-dependent color mapping)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(batteryColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (device.batteryPercent != null) {
                            if (isLive) "🔋 ${device.batteryPercent}%" else "🔋 ${device.batteryPercent}% (Last known)"
                        } else {
                            "Battery: Unavailable"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isLive) StatusGreen else StatusAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLive) "Status: Connected & Streaming" else "Status: Previously Connected (Offline)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isLive) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Profile: ${device.profile}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
