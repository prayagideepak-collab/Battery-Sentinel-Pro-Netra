package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BatteryRecord
import com.example.model.DotState
import com.example.model.hasCompleteLegacyReading
import com.example.ui.components.SentinelCard
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DischargingScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val canonicalReading by viewModel.canonicalState.collectAsStateWithLifecycle()
    val checkpoints by viewModel.recentDischargeCheckpoints.collectAsStateWithLifecycle()

    // Compute observed discharge rate (%/hr) from checkpoints if available
    val observedRate = if (checkpoints.size >= 2) {
        val oldest = checkpoints.last()
        val newest = checkpoints.first()
        val timeDiffHours = (newest.timestamp - oldest.timestamp) / 3600_000f
        val dropLevel = oldest.level - newest.level
        if (timeDiffHours > 0.05f && dropLevel > 0) dropLevel / timeDiffHours else null
    } else null

    if (canonicalReading.batteryLevel == null && !telemetry.isDataAvailable) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("Connecting to Central Sentinel...", color = MaterialTheme.colorScheme.onSurface)
            Text("Waiting for battery sensor data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Runtime & SoC Overview
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
                    .testTag("discharging_overview_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Remaining Runtime",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NetraEmerald.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "SoC: ${telemetry.level}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (!telemetry.isCharging) {
                                telemetry.estimatedDischargeHours?.let { "~${String.format("%.1f", it)}" } ?: "Unavailable"
                            } else "Charging",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (telemetry.level <= 15) StatusAmber else NetraEmerald
                        )
                        if (!telemetry.isCharging && telemetry.estimatedDischargeHours != null) {
                            Text(
                                text = "hours",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp, start = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Remaining runtime unavailable without measured capacity and a reliable drain model.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Electrical Drain Metrics & Observed Rate
        item {
            SentinelCard(
                title = "Electrical Drain Metrics",
                icon = Icons.Default.ElectricMeter,
                dotState = if (!telemetry.isCharging) DotState.CONNECTED else DotState.STANDBY,
                accentColor = StatusAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DrainBox(
                        label = "DISCHARGE CURRENT",
                        value = if (telemetry.currentMa != 0) "${telemetry.currentMa} mA" else "Unavailable",
                        accent = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                    DrainBox(
                        label = "REAL POWER DRAIN",
                        value = telemetry.powerWatts?.let { "${String.format("%.2f", kotlin.math.abs(it))} W" } ?: "Unavailable",
                        accent = NetraCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Observed Discharge Rate from Room DB
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NetraEmerald.copy(alpha = 0.1f))
                        .border(1.dp, NetraEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Observed Discharge Rate", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                            Text(text = "Historical gradient across battery samples", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = observedRate?.let { "${String.format("%.2f", it)} % / hour" } ?: "Unavailable",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NetraEmerald
                        )
                    }
                }
            }
        }

        // Checkpoints History Log (Recent battery drops with timestamp and temperature)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = NetraCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Discharge Checkpoints Log (${checkpoints.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (checkpoints.isEmpty()) {
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
                        text = "Waiting for data. Discharging telemetry will be recorded automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(checkpoints) { checkpoint ->
                DischargeCheckpointItem(record = checkpoint)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrainBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun DischargeCheckpointItem(record: BatteryRecord) {
    val dateFmt = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusAmber))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "${record.level}% Battery Level", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = dateFmt, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%.1f", record.temperature)} °C",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (record.temperature >= 40f) StatusRed else NetraCyan
                )
                Text(
                    text = "${record.voltageMv} mV • ${String.format("%.2f", record.powerWatts)}W",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
