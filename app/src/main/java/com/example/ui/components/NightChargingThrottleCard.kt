package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.NightChargingThrottler
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.BatteryTelemetry
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue

@Composable
fun NightChargingThrottleCard(
    telemetry: BatteryTelemetry,
    isFeatureEnabled: Boolean,
    targetWakeHour: Int,
    records: List<BatteryRecord>,
    sessions: List<ChargingSession>,
    onToggleFeature: (Boolean) -> Unit,
    onSelectWakeHour: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = remember(telemetry, isFeatureEnabled, targetWakeHour, records, sessions) {
        NightChargingThrottler.evaluateNightThrottle(telemetry, isFeatureEnabled, targetWakeHour, records, sessions)
    }

    val accentColor = if (status.isThrottleActive) NetraCyan else if (isFeatureEnabled) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant

    SentinelCard(
        title = "Night Thermal Throttling",
        icon = Icons.Default.Bedtime,
        dotState = if (status.isThrottleActive) DotState.CONNECTED else if (isFeatureEnabled) DotState.STANDBY else DotState.THROTTLED,
        accentColor = accentColor,
        trailingAction = {
            Switch(
                checked = isFeatureEnabled,
                onCheckedChange = onToggleFeature,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NetraEmerald,
                    checkedTrackColor = NetraEmerald.copy(alpha = 0.3f)
                ),
                modifier = Modifier.testTag("night_throttle_switch")
            )
        },
        modifier = modifier.testTag("night_charging_throttle_card")
    ) {
        Text(
            text = "Learns your sleep patterns and discharge curves to slow down charging wattage at night, preventing prolonged high-temperature exposure and cathode phase degradation.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status Card Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (status.isThrottleActive) NetraCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status.statusHeadline,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    if (status.isThrottleActive) {
                        Text(
                            text = "⚡ Limit: ${status.targetPowerLimitWatts}W",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NetraCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.detailedAdvice,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target Wake Time Configuration
        Text(
            text = "Target Wake-Up Hour (Full 100% Saturation by):",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(6 to "06:00 AM", 7 to "07:00 AM", 8 to "08:00 AM", 9 to "09:00 AM").forEach { (hour, label) ->
                val isSelected = targetWakeHour == hour
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectWakeHour(hour) },
                    label = { Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NetraEmerald.copy(alpha = 0.25f),
                        selectedLabelColor = NetraEmerald
                    ),
                    modifier = Modifier.testTag("wake_hour_chip_$hour")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2-Column KPI Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeviceThermostat, contentDescription = null, tint = NetraEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("TARGET TEMPERATURE", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("< 28.5°C (Cool)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = NetraCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("THERMAL STRESS REDUCTION", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-74% SEI Stress", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraCyan)
                    }
                }
            }
        }
    }
}
