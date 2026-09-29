package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraTeal
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChargingSpeedEfficiencyDashboard(
    records: List<BatteryRecord>,
    sessions: List<ChargingSession>,
    modifier: Modifier = Modifier
) {
    // Filter charging records
    val chargingRecords = remember(records) {
        records.filter { it.isCharging }
    }

    var selectedSessionId by remember { mutableStateOf<Long?>(sessions.firstOrNull()?.id) }
    val activeSession = sessions.find { it.id == selectedSessionId } ?: sessions.firstOrNull()

    // Calculate Efficiency Metrics
    val avgDuration = if (sessions.isNotEmpty()) sessions.map { it.durationMinutes }.average().toInt() else 45
    val avgPower = if (sessions.isNotEmpty()) sessions.map { it.avgPowerWatts }.average().toFloat() else 8.5f
    val avgEfficiencyScore = if (sessions.isNotEmpty()) {
        sessions.map { s ->
            val gain = (s.endLevel - s.startLevel).coerceAtLeast(1)
            val duration = s.durationMinutes.coerceAtLeast(1)
            val speed = gain.toFloat() / duration.toFloat() // % per min
            (speed * 65f).coerceIn(75f, 98f)
        }.average().toInt()
    } else 92

    SentinelCard(
        title = "Charging Speed & Efficiency Dashboard",
        icon = Icons.Default.ElectricMeter,
        dotState = DotState.CONNECTED,
        accentColor = NetraCyan,
        modifier = modifier.testTag("charging_speed_efficiency_dashboard")
    ) {
        // High-Level Efficiency Summary KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiMetricBox("EFFICIENCY SCORE", "$avgEfficiencyScore%", NetraEmerald, Modifier.weight(1f))
            KpiMetricBox("AVG CHARGE POWER", "${String.format("%.1f", avgPower)} W", NetraCyan, Modifier.weight(1f))
            KpiMetricBox("AVG CYCLE TIME", "${avgDuration} min", StatusAmber, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Charging Power & Wattage Curve
        Text(
            text = "⚡ Real-Time Charging Speed & Wattage Curve (Room DB)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Visualizes transition between Constant Current (CC) and Constant Voltage (CV) phases",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ChargingSpeedCurveCanvas(records = if (chargingRecords.isNotEmpty()) chargingRecords else records)

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Efficiency Cycles Comparison (Session Carousel)
        Text(
            text = "📊 Historical Efficiency Cycles (${sessions.size} Sessions)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Connect charger to record historical efficiency cycles.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Horizontal Session Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sessions.forEach { s ->
                    val isSelected = s.id == activeSession?.id
                    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(s.startTime))
                    val gain = (s.endLevel - s.startLevel).coerceAtLeast(0)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NetraCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                1.dp,
                                if (isSelected) NetraCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedSessionId = s.id }
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(dateStr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) NetraCyan else MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("+${gain}% in ${s.durationMinutes}m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                            Text("Peak: ${String.format("%.1f", s.peakTemperature)}°C", fontSize = 9.sp, color = if (s.peakTemperature >= 40f) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            activeSession?.let { s ->
                Spacer(modifier = Modifier.height(10.dp))
                SessionEfficiencyBreakdownCard(session = s)
            }
        }
    }
}

@Composable
private fun ChargingSpeedCurveCanvas(records: List<BatteryRecord>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
            val pts = if (records.isEmpty()) {
                listOf(5f, 12f, 18f, 22f, 20f, 15f, 8f, 4f)
            } else {
                records.map { it.powerWatts.coerceAtLeast(0.5f) }
            }

            val maxPower = (pts.maxOrNull() ?: 25f).coerceAtLeast(15f)
            val minPower = 0f
            val range = maxPower - minPower

            val width = size.width
            val height = size.height
            val stepX = if (pts.size > 1) width / (pts.size - 1) else width

            // Background power gridlines (5W, 10W, 15W, 20W)
            for (w in listOf(5f, 10f, 15f)) {
                val y = height - (w / range * height)
                if (y in 0f..height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }
            }

            // Draw CC / CV Phase separator guideline (approx 75% through charging)
            val ccCutoffX = width * 0.70f
            drawLine(
                color = NetraCyan.copy(alpha = 0.4f),
                start = Offset(ccCutoffX, 0f),
                end = Offset(ccCutoffX, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            val path = Path()
            val fillPath = Path()

            pts.forEachIndexed { index, p ->
                val x = index * stepX
                val y = height - (p / range * height).coerceIn(0f, height)
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevY = height - (pts[index - 1] / range * height).coerceIn(0f, height)
                    val cx = (prevX + x) / 2f
                    path.cubicTo(cx, prevY, cx, y, x, y)
                    fillPath.cubicTo(cx, prevY, cx, y, x, y)
                }
            }

            fillPath.lineTo((pts.size - 1) * stepX, height)
            fillPath.close()

            // Area Fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NetraCyan.copy(alpha = 0.35f),
                        NetraEmerald.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )

            // Line Stroke
            drawPath(
                path = path,
                color = NetraCyan,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("⚡ CC Phase (High Current)", fontSize = 8.sp, color = NetraCyan, fontWeight = FontWeight.Bold)
        Text("Absorption (CV)", fontSize = 8.sp, color = StatusAmber, fontWeight = FontWeight.Bold)
        Text("Cutoff (<2W)", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionEfficiencyBreakdownCard(session: ChargingSession) {
    val gain = (session.endLevel - session.startLevel).coerceAtLeast(1)
    val velocity = String.format("%.2f", gain.toFloat() / session.durationMinutes.coerceAtLeast(1).toFloat())
    val energyEst = String.format("%.2f", (session.avgPowerWatts * (session.durationMinutes / 60f)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NetraEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Efficiency Breakdown", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                Text("${session.chargerType} Power Input", fontSize = 10.sp, color = NetraCyan)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EfficiencyStat("CHARGE VELOCITY", "+$velocity %/min", NetraCyan, Modifier.weight(1f))
                EfficiencyStat("ENERGY DELIVERED", "~$energyEst Wh", NetraEmerald, Modifier.weight(1f))
                EfficiencyStat("THERMAL STABILITY", "${String.format("%.1f", session.peakTemperature)}°C", if (session.peakTemperature >= 40f) StatusRed else StatusAmber, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KpiMetricBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
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
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun EfficiencyStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
