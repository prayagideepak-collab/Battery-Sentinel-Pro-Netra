package com.example.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.NightChargingThrottler
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.BatteryTelemetry
import com.example.model.DotState
import com.example.ui.theme.StatusAmber

/** Keep the existing card route but never offer a switch for unsupported charger control. */
@Composable
@Suppress("UNUSED_PARAMETER")
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
    val status = NightChargingThrottler.evaluateNightThrottle(telemetry, isFeatureEnabled, targetWakeHour, records, sessions)
    SentinelCard(
        title = "Night Charging",
        icon = Icons.Default.Bedtime,
        dotState = DotState.STANDBY,
        accentColor = StatusAmber,
        modifier = modifier.testTag("night_charging_throttle_card")
    ) {
        Text(status.statusHeadline, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(status.detailedAdvice, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
