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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import com.example.data.local.ChargingSession
import com.example.model.CanonicalChargingSpeed
import com.example.model.DotState
import com.example.ui.components.CircularBatteryGauge
import com.example.ui.components.SentinelCard
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BatteryScreenSubTab {
    LIVE_TELEMETRY,
    SESSIONS_AND_GRAPH
}

@Composable
fun BatteryScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val canonical by viewModel.canonicalState.collectAsStateWithLifecycle()
    val sessions by viewModel.recentChargingSessions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableStateOf(BatteryScreenSubTab.LIVE_TELEMETRY) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Sub-Tab Switcher (Live Telemetry vs Sessions & Graph)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battery_subtab_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSubTab == BatteryScreenSubTab.LIVE_TELEMETRY,
                onClick = { selectedSubTab = BatteryScreenSubTab.LIVE_TELEMETRY },
                label = { Text("LIVE TELEMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NetraEmerald.copy(alpha = 0.2f),
                    selectedLabelColor = NetraEmerald
                ),
                modifier = Modifier.weight(1f).testTag("tab_live_telemetry")
            )
            FilterChip(
                selected = selectedSubTab == BatteryScreenSubTab.SESSIONS_AND_GRAPH,
                onClick = { selectedSubTab = BatteryScreenSubTab.SESSIONS_AND_GRAPH },
                label = { Text("SESSIONS & GRAPH", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NetraCyan.copy(alpha = 0.2f),
                    selectedLabelColor = NetraCyan
                ),
                modifier = Modifier.weight(1f).testTag("tab_sessions_graph")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedSubTab == BatteryScreenSubTab.LIVE_TELEMETRY) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2. Circular Status Meter (Reused, surfaces live canonical information & ETA)
                item {
                    SentinelCard(
                        title = "Battery & Power Sentinel",
                        icon = Icons.Default.ElectricMeter,
                        dotState = if (canonical.isCharging == true) DotState.CONNECTED else DotState.STANDBY,
                        accentColor = if (canonical.isCharging == true) NetraCyan else NetraEmerald,
                        trailingAction = {
                            val stateBadge = when {
                                canonical.isCharging == true -> "⚡ CHARGING"
                                canonical.isChargerConnected == true -> "🔌 IDLE / FULL"
                                else -> "🔋 DISCHARGING"
                            }
                            Text(
                                text = stateBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (canonical.isCharging == true) NetraCyan else NetraEmerald
                            )
                        }
                    ) {
                        CircularBatteryGauge(canonical = canonical)
                    }
                }

                // 3. Electrical Telemetry & Raw Incoming Power Card
                item {
                    SentinelCard(
                        title = "Raw Incoming Power & Electrical Telemetry",
                        icon = Icons.Default.Bolt,
                        dotState = if (canonical.isCharging == true) DotState.CONNECTED else DotState.STANDBY,
                        accentColor = NetraCyan
                    ) {
                        val rawPowerStr = canonical.powerWatts?.let { "${String.format(Locale.US, "%.2f", it)} W" } ?: "Unavailable"
                        val speedCategoryStr = when (canonical.chargingSpeed) {
                            CanonicalChargingSpeed.SLOW -> "Slow (<5W)"
                            CanonicalChargingSpeed.NORMAL -> "Normal (5W–<10W)"
                            CanonicalChargingSpeed.FAST -> "Fast (10W–<20W)"
                            CanonicalChargingSpeed.SUPER_FAST -> "Super Fast (20W–<40W)"
                            CanonicalChargingSpeed.ULTRA_FAST -> "Ultra Fast (≥40W)"
                            CanonicalChargingSpeed.UNAVAILABLE -> "Unavailable"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (canonical.isCharging == true) "Raw Incoming Charging Power" else "Active Power Draw",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = rawPowerStr,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (canonical.isCharging == true) NetraEmerald else NetraCyan
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Charging Speed Tier",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = speedCategoryStr,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canonical.isCharging == true) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ElectricBadge(
                                label = "VOLTAGE",
                                value = canonical.voltageMv?.let { "$it mV" } ?: "Unavailable",
                                accent = NetraCyan,
                                modifier = Modifier.weight(1f)
                            )
                            ElectricBadge(
                                label = "CURRENT",
                                value = canonical.currentMa?.let { "$it mA" } ?: "Unavailable",
                                accent = if ((canonical.currentMa ?: 0) >= 0) NetraEmerald else StatusAmber,
                                modifier = Modifier.weight(1f)
                            )
                            ElectricBadge(
                                label = "PHONE DRAIN",
                                value = canonical.consumptionPowerWatts?.let { "${String.format(Locale.US, "%.2f", it)} W" } ?: "Unavailable",
                                accent = StatusAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4. Canonical ETA & Charger Session Timings
                item {
                    SentinelCard(
                        title = "Session Timing & Intelligence ETA",
                        icon = Icons.Default.Schedule,
                        dotState = DotState.CONNECTED,
                        accentColor = NetraEmerald
                    ) {
                        val etaText = when (canonical.isCharging) {
                            true -> canonical.chargingEtaMinutes?.let { "~$it minutes until 100% full" } ?: "ETA calculating from progression..."
                            false -> canonical.dischargingEtaMinutes?.let { "~$it minutes of runtime remaining" } ?: "ETA calculating from progression..."
                            null -> "ETA unavailable"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Canonical ETA",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = etaText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NetraEmerald
                                )
                            }

                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val sessionStart = if (canonical.isCharging == true) canonical.chargingStartedAt else canonical.dischargingStartedAt
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Session Started",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sessionStart?.let { timeFormat.format(Date(it)) } ?: "Ongoing",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 5. Thermal Health & Safe Margin
                item {
                    SentinelCard(
                        title = "Thermal Health & Safety Margin",
                        icon = Icons.Default.DeviceThermostat,
                        dotState = if ((canonical.temperatureCelsius ?: 0f) >= 40f) DotState.CRITICAL else DotState.CONNECTED,
                        accentColor = if ((canonical.temperatureCelsius ?: 0f) >= 40f) StatusRed else NetraCyan
                    ) {
                        val temp = canonical.temperatureCelsius
                        val tempStr = temp?.let { "${String.format(Locale.US, "%.1f", it)} °C" } ?: "Unavailable"
                        val margin = temp?.let { 40.0f - it }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Battery Temperature", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = tempStr,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if ((temp ?: 0f) >= 40f) StatusRed else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Safety Margin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = when {
                                        margin == null -> "Unavailable"
                                        margin > 0 -> "${String.format(Locale.US, "%.1f", margin)}°C to 40°C threshold"
                                        else -> "⚠️ OVER 40°C LIMIT"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        margin == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        margin > 0 -> NetraEmerald
                                        else -> DangerRed
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // SESSIONS & GRAPH VIEW
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    GraphScreen(viewModel = viewModel)
                }

                item {
                    SentinelCard(
                        title = "Recent Charging Sessions",
                        icon = Icons.Default.History,
                        dotState = DotState.CONNECTED,
                        accentColor = NetraCyan
                    ) {
                        if (sessions.isEmpty()) {
                            Text(
                                text = "No charging sessions recorded yet. Plug in your charger to start recording session metrics.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sessions.take(5).forEach { session ->
                                    ChargingSessionItemRow(session = session)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ElectricBadge(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun ChargingSessionItemRow(session: ChargingSession) {
    val durationMin = session.durationMinutes
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(session.startTime))
    val levelGained = session.endLevel - session.startLevel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${session.startLevel}% → ${session.endLevel}% (+$levelGained%)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraEmerald
                )
                Text(
                    text = "$dateStr • ${session.chargerType}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (durationMin > 0) "$durationMin min" else "<1 min",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", session.peakTemperature)}°C peak",
                    fontSize = 11.sp,
                    color = if (session.peakTemperature >= 40f) StatusRed else NetraCyan
                )
            }
        }
    }
}
