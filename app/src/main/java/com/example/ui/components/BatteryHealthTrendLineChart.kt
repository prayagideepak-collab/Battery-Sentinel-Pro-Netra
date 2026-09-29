package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BatteryRecord
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HealthPoint(
    val timestamp: Long,
    val healthPercent: Int,
    val temperature: Float,
    val level: Int,
    val isCharging: Boolean
)

@Composable
fun BatteryHealthTrendLineChart(
    records: List<BatteryRecord>,
    modifier: Modifier = Modifier
) {
    var scrubX by remember { mutableStateOf<Float?>(null) }
    var selectedPoint by remember { mutableStateOf<HealthPoint?>(null) }

    // Map Room records into computed Health trajectory points
    val healthPoints = remember(records) {
        if (records.isEmpty()) {
            listOf(
                HealthPoint(System.currentTimeMillis() - 3600_000L, 95, 32f, 80, false),
                HealthPoint(System.currentTimeMillis(), 94, 33f, 82, true)
            )
        } else {
            records.map { r ->
                var score = 96
                if (r.temperature > 42f) score -= 8
                else if (r.temperature > 38f) score -= 3
                if (r.voltageMv > 4350) score -= 4
                if (r.level <= 10 && !r.isCharging) score -= 4
                HealthPoint(
                    timestamp = r.timestamp,
                    healthPercent = score.coerceIn(70, 99),
                    temperature = r.temperature,
                    level = r.level,
                    isCharging = r.isCharging
                )
            }
        }
    }

    val currentHealth = healthPoints.lastOrNull()?.healthPercent ?: 95
    val minHealth = healthPoints.minOfOrNull { it.healthPercent } ?: 90
    val maxHealth = healthPoints.maxOfOrNull { it.healthPercent } ?: 99
    val avgHealth = if (healthPoints.isNotEmpty()) healthPoints.map { it.healthPercent }.average().toInt() else 95

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_health_trend_line_chart")
    ) {
        // Top Health Summary Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HealthSummaryBox("CURRENT HEALTH", "$currentHealth%", NetraEmerald, Modifier.weight(1f))
            HealthSummaryBox("AVG HEALTH", "$avgHealth%", NetraCyan, Modifier.weight(1f))
            HealthSummaryBox("MIN / MAX", "$minHealth% / $maxHealth%", StatusAmber, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tooltip Banner when touch scrubbing
        if (selectedPoint != null) {
            val pt = selectedPoint!!
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pt.timestamp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetraEmerald.copy(alpha = 0.15f))
                    .border(1.dp, NetraEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NetraEmerald))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = timeFmt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SoC: ${pt.level}% • ${pt.temperature}°C", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = "Health: ${pt.healthPercent}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraEmerald
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Dynamic Line Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .pointerInput(healthPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            scrubX = offset.x
                            val idx = ((offset.x / size.width) * (healthPoints.size - 1)).toInt().coerceIn(0, healthPoints.size - 1)
                            selectedPoint = healthPoints.getOrNull(idx)
                        }
                    )
                }
                .pointerInput(healthPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            scrubX = offset.x
                            val idx = ((offset.x / size.width) * (healthPoints.size - 1)).toInt().coerceIn(0, healthPoints.size - 1)
                            selectedPoint = healthPoints.getOrNull(idx)
                        },
                        onDragEnd = {
                            scrubX = null
                            selectedPoint = null
                        },
                        onDragCancel = {
                            scrubX = null
                            selectedPoint = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubX = change.position.x
                            if (healthPoints.isNotEmpty()) {
                                val idx = ((change.position.x / size.width) * (healthPoints.size - 1)).toInt().coerceIn(0, healthPoints.size - 1)
                                selectedPoint = healthPoints.getOrNull(idx)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp)) {
                val minScale = 60f
                val maxScale = 100f
                val range = maxScale - minScale

                val width = size.width
                val height = size.height
                val stepX = if (healthPoints.size > 1) width / (healthPoints.size - 1) else width

                // Horizontal Grid lines (70%, 80%, 90%, 100%)
                for (pct in listOf(70f, 80f, 90f)) {
                    val y = height - ((pct - minScale) / range * height)
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // 80% Healthy threshold guideline
                val threshold80Y = height - ((80f - minScale) / range * height)
                drawLine(
                    color = StatusAmber.copy(alpha = 0.5f),
                    start = Offset(0f, threshold80Y),
                    end = Offset(width, threshold80Y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                // Build smooth spline curve
                val path = Path()
                val fillPath = Path()

                healthPoints.forEachIndexed { index, pt ->
                    val x = index * stepX
                    val y = height - ((pt.healthPercent - minScale) / range * height).coerceIn(0f, height)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (index - 1) * stepX
                        val prevY = height - ((healthPoints[index - 1].healthPercent - minScale) / range * height).coerceIn(0f, height)
                        val cx = (prevX + x) / 2f
                        path.cubicTo(cx, prevY, cx, y, x, y)
                        fillPath.cubicTo(cx, prevY, cx, y, x, y)
                    }
                }

                fillPath.lineTo((healthPoints.size - 1) * stepX, height)
                fillPath.close()

                // Draw Gradient Fill Area
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NetraEmerald.copy(alpha = 0.35f),
                            NetraCyan.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )

                // Draw Smooth Line
                drawPath(
                    path = path,
                    color = NetraEmerald,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Data Point Circles
                if (healthPoints.size <= 25) {
                    healthPoints.forEachIndexed { index, pt ->
                        val x = index * stepX
                        val y = height - ((pt.healthPercent - minScale) / range * height).coerceIn(0f, height)
                        drawCircle(
                            color = NetraDarkBg,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = NetraEmerald,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                // Scrub Cursor
                scrubX?.let { sx ->
                    val clampedX = sx.coerceIn(0f, width)
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(clampedX, 0f),
                        end = Offset(clampedX, height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val idx = ((clampedX / width) * (healthPoints.size - 1)).toInt().coerceIn(0, healthPoints.size - 1)
                    val ptY = height - ((healthPoints[idx].healthPercent - minScale) / range * height).coerceIn(0f, height)

                    drawCircle(
                        color = NetraCyan,
                        radius = 6.dp.toPx(),
                        center = Offset(clampedX, ptY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(clampedX, ptY)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Oldest Sample", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("80% Retention Threshold", fontSize = 9.sp, color = StatusAmber)
            Text("Latest Sample", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthSummaryBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
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
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
