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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BatteryTelemetry
import com.example.model.CalibrationSessionState
import com.example.model.CalibrationStep
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryCalibrationWizardCard(
    calibrationState: CalibrationSessionState,
    telemetry: BatteryTelemetry,
    onStartCalibration: () -> Unit,
    onCancelCalibration: () -> Unit,
    onAdvanceStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCalibrated = calibrationState.lastCalibratedTimestamp > 0L
    val calDateStr = if (isCalibrated) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(calibrationState.lastCalibratedTimestamp))
    } else "Not yet calibrated"

    SentinelCard(
        title = "Battery Calibration Wizard",
        icon = Icons.Default.Tune,
        dotState = if (calibrationState.isWizardActive) DotState.THROTTLED else DotState.CONNECTED,
        accentColor = if (calibrationState.isWizardActive) NetraCyan else NetraEmerald,
        trailingAction = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetraEmerald.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${calibrationState.accuracyScorePercent}% ACCURACY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraEmerald
                )
            }
        },
        modifier = modifier.testTag("battery_calibration_wizard_card")
    ) {
        // Top Metric Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalibKpiBox("LAST CALIBRATED", calDateStr, NetraCyan, Modifier.weight(1f))
            CalibKpiBox("USABLE CAPACITY", "${calibrationState.calibratedCapacityMah} mAh", NetraEmerald, Modifier.weight(1f))
            CalibKpiBox("HEALTH PRECISION", "${calibrationState.accuracyScorePercent}.8%", StatusAmber, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!calibrationState.isWizardActive && calibrationState.currentStep != CalibrationStep.COMPLETED) {
            // Idle State: Start Prompt
            Text(
                text = "Recalibrates Android's fuel gauge Coulomb counter and electrochemical capacity tracking. Guides you through a full discharge & charge cycle for maximum analytics accuracy.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStartCalibration,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_calibration_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NetraEmerald, contentColor = Color.Black)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Calibration Cycle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // Active Wizard Step Progression
            ActiveWizardContent(
                state = calibrationState,
                telemetry = telemetry,
                onCancel = onCancelCalibration,
                onAdvance = onAdvanceStep
            )
        }
    }
}

@Composable
private fun ActiveWizardContent(
    state: CalibrationSessionState,
    telemetry: BatteryTelemetry,
    onCancel: () -> Unit,
    onAdvance: () -> Unit
) {
    val step = state.currentStep

    // 4-Step Visual Progress Tracker
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(1, "Discharge", step.stepNumber >= 1, step == CalibrationStep.STEP_1_DISCHARGE)
        StepLine(step.stepNumber >= 2)
        StepDot(2, "Rest", step.stepNumber >= 2, step == CalibrationStep.STEP_2_REST)
        StepLine(step.stepNumber >= 3)
        StepDot(3, "Charge", step.stepNumber >= 3, step == CalibrationStep.STEP_3_FULL_CHARGE)
        StepLine(step.stepNumber >= 4)
        StepDot(4, "Saturate", step.stepNumber >= 4, step == CalibrationStep.STEP_4_SATURATION)
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Step Card Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, NetraCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${step.stepNumber}: ${step.title}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NetraCyan
                )

                if (step == CalibrationStep.COMPLETED) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NetraEmerald, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = step.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)

            Spacer(modifier = Modifier.height(10.dp))

            when (step) {
                CalibrationStep.STEP_1_DISCHARGE -> {
                    // Discharge Progress Bar
                    val targetProgress = ((100 - telemetry.level) / 90f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { targetProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StatusAmber,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current SoC: ${telemetry.level}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Target: ≤ 10% (Remaining: ${maxOf(0, telemetry.level - 10)}%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                    }

                    if (telemetry.isCharging) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("⚠️ Charger connected — please unplug to continue discharging.", fontSize = 10.5.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                }

                CalibrationStep.STEP_2_REST -> {
                    Text("⏳ Resting battery for 15 minutes to stabilize Open Circuit Voltage (OCV)...", fontSize = 11.sp, color = NetraCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Do not connect charger yet. Let internal electrochemical polarization dissipate.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                CalibrationStep.STEP_3_FULL_CHARGE -> {
                    val chargeProgress = (telemetry.level / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { chargeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NetraEmerald,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Charging: ${telemetry.level}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                        Text("Power: ${String.format("%.1f", telemetry.powerWatts)}W", fontSize = 10.sp, color = NetraCyan)
                    }
                }

                CalibrationStep.STEP_4_SATURATION -> {
                    Text("⚡ Top Saturation Phase Active (100% Reached)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Keep charger connected for 20-30 minutes to saturate solid-electrolyte interphase layer and calibrate fuel gauge zero-offset.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                CalibrationStep.COMPLETED -> {
                    Text("🎉 Calibration Successfully Completed!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Coulomb counter recalibrated. Usable capacity updated to ${state.calibratedCapacityMah} mAh. Accuracy index boosted to 99.8%.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                else -> {}
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Step Actions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).testTag("cancel_calibration_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cancel", fontSize = 11.sp)
        }

        Button(
            onClick = onAdvance,
            modifier = Modifier.weight(1f).testTag("advance_calibration_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NetraCyan, contentColor = Color.Black)
        ) {
            Text(if (step == CalibrationStep.COMPLETED) "Finish" else "Next Step", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepDot(number: Int, label: String, isReached: Boolean, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isActive) NetraCyan else if (isReached) NetraEmerald else Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive || isReached) Color.Black else Color.White
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 8.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) NetraCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepLine(isCompleted: Boolean) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(2.dp)
            .background(if (isCompleted) NetraEmerald else Color.White.copy(alpha = 0.2f))
    )
}

@Composable
private fun CalibKpiBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
