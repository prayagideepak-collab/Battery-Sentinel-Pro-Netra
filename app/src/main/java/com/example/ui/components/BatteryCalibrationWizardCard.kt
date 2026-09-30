package com.example.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.BatteryTelemetry
import com.example.model.CalibrationSessionState
import com.example.model.DotState
import com.example.ui.theme.NetraCyan

/** Preserve the card route without asking the user to deep-discharge or overcharge. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BatteryCalibrationWizardCard(
    calibrationState: CalibrationSessionState,
    telemetry: BatteryTelemetry,
    onStartCalibration: () -> Unit,
    onCancelCalibration: () -> Unit,
    onAdvanceStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    SentinelCard(
        title = "Battery Calibration Unavailable",
        icon = Icons.Default.Tune,
        dotState = DotState.STANDBY,
        accentColor = NetraCyan,
        modifier = modifier.testTag("battery_calibration_wizard_card")
    ) {
        Text("Capacity and accuracy measurements unavailable", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "This app cannot recalibrate Android's fuel gauge or measure remaining capacity. Do not deep-discharge or keep charging at 100% for this app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
