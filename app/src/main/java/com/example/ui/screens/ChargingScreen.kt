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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
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
import com.example.data.local.ChargingSession
import com.example.model.DotState
import com.example.ui.components.SentinelCard
import com.example.ui.components.StatusDot
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChargingScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val sessions by viewModel.recentChargingSessions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Prominent Temperature Display (Large Header Banner)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (telemetry.temperature >= 40f) StatusRed.copy(alpha = 0.15f)
                        else NetraCyan.copy(alpha = 0.1f)
                    )
                    .border(
                        1.dp,
                        if (telemetry.temperature >= 40f) StatusRed else NetraCyan.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
                    .testTag("charging_temp_header")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeviceThermostat,
                            contentDescription = "Temperature",
                            tint = if (telemetry.temperature >= 40f) StatusRed else NetraCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Battery Temperature: ${String.format("%.1f", telemetry.temperature)} °C",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (telemetry.temperature >= 40f) StatusRed else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (telemetry.distanceTo40C > 0)
                            "${String.format("%.1f", telemetry.distanceTo40C)}°C safe margin from 40°C threshold"
                        else
                            "⚠️ ${String.format("%.1f", kotlin.math.abs(telemetry.distanceTo40C))}°C OVER 40°C safety limit!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (telemetry.distanceTo40C > 0) NetraEmerald else DangerRed
                    )
                }
            }
        }

        // Real-Time Speed, Wattage & Electrical Metrics
        item {
            SentinelCard(
                title = "Charging Speed & Electrical Telemetry",
                icon = Icons.Default.Bolt,
                dotState = if (telemetry.isCharging) DotState.CONNECTED else DotState.STANDBY,
                accentColor = NetraCyan
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Charger Protocol", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (telemetry.isCharging) "${telemetry.pluggedType} (${telemetry.chargingSpeedLabel})" else "Disconnected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetry.isCharging) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Target Cutoff", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${settings.chargeTargetPercent}% SoC",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetraCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElectricCard("ACTIVE POWER", "${String.format("%.2f", telemetry.powerWatts)} W", NetraEmerald, Modifier.weight(1f))
                    ElectricCard("VOLTAGE", "${telemetry.voltageMv} mV", NetraCyan, Modifier.weight(1f))
                    ElectricCard("CURRENT", if (telemetry.currentMa != 0) "${telemetry.currentMa} mA" else "Unavailable", StatusAmber, Modifier.weight(1f))
                }
            }
        }

        // Time to Full (ETA)
        item {
            SentinelCard(
                title = "Time to Full (ETA)",
                icon = Icons.Default.Schedule,
                dotState = if (telemetry.isCharging) DotState.CONNECTED else DotState.STANDBY,
                accentColor = NetraEmerald
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (telemetry.isCharging) "Estimated remaining to 100%" else "Device not connected to charger",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (telemetry.isCharging) {
                                telemetry.timeToFullMinutes?.let { "~$it minutes" } ?: "Estimating..."
                            } else "N/A",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (telemetry.isCharging) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetraEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Target 80%: ${if (telemetry.level >= 80) "REACHED" else "ARMED"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetraEmerald
                        )
                    }
                }
            }
        }

        // Charging Speed & Efficiency Dashboard (Room DB cycles & power curve)
        item {
            val records by viewModel.graphRecords.collectAsStateWithLifecycle()
            com.example.ui.components.ChargingSpeedEfficiencyDashboard(
                records = records,
                sessions = sessions
            )
        }

        // Charging Sessions History (from Room Database)
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
                        text = "Charging Sessions History (${sessions.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
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
                        text = "Waiting for data. Plug in and charge device to record session telemetry.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(sessions) { session ->
                ChargingSessionItem(session = session)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ElectricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ChargingSessionItem(session: ChargingSession) {
    val dateFmt = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.startTime))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NetraCyan))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = dateFmt, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = "${session.durationMinutes} mins",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NetraEmerald
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Level: ${session.startLevel}% → ${session.endLevel}% (+${session.endLevel - session.startLevel}%)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Peak: ${String.format("%.1f", session.peakTemperature)}°C",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (session.peakTemperature >= 40f) StatusRed else StatusAmber
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Source: ${session.chargerType}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Avg Power: ${String.format("%.2f", session.avgPowerWatts)}W",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
