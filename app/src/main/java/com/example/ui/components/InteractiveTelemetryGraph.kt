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
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GraphMetric {
    LEVEL,   // %
    TEMP,    // °C
    POWER,   // W
    VOLTAGE  // mV
}

@Composable
fun InteractiveTelemetryGraph(
    records: List<BatteryRecord>,
    metric: GraphMetric,
    modifier: Modifier = Modifier
) {
    var scrubX by remember { mutableStateOf<Float?>(null) }
    var selectedRecord by remember { mutableStateOf<BatteryRecord?>(null) }

    val metricColor = when (metric) {
        GraphMetric.LEVEL -> NetraEmerald
        GraphMetric.TEMP -> StatusAmber
        GraphMetric.POWER -> NetraCyan
        GraphMetric.VOLTAGE -> StatusBlue
    }

    val metricUnit = when (metric) {
        GraphMetric.LEVEL -> "%"
        GraphMetric.TEMP -> "°C"
        GraphMetric.POWER -> "W"
        GraphMetric.VOLTAGE -> "mV"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_telemetry_graph")
    ) {
        // Tooltip / Touch-Scrub Info Display
        if (selectedRecord != null) {
            val r = selectedRecord!!
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.timestamp))
            val valueStr = when (metric) {
                GraphMetric.LEVEL -> "${r.level}%"
                GraphMetric.TEMP -> "${String.format("%.1f", r.temperature)}°C"
                GraphMetric.POWER -> "${String.format("%.2f", r.powerWatts)}W"
                GraphMetric.VOLTAGE -> "${r.voltageMv} mV"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(metricColor.copy(alpha = 0.15f))
                    .border(1.dp, metricColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(metricColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = timeFmt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (r.isCharging) "⚡ ${r.pluggedType}" else "🔋 Discharging",
                        fontSize = 11.sp,
                        color = if (r.isCharging) NetraCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = valueStr,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = metricColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Interactive Canvas Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .pointerInput(records, metric) {
                    detectTapGestures(
                        onPress = { offset ->
                            scrubX = offset.x
                            val idx = ((offset.x / size.width) * (records.size - 1)).toInt().coerceIn(0, records.size - 1)
                            selectedRecord = records.getOrNull(idx)
                        }
                    )
                }
                .pointerInput(records, metric) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            scrubX = offset.x
                            val idx = ((offset.x / size.width) * (records.size - 1)).toInt().coerceIn(0, records.size - 1)
                            selectedRecord = records.getOrNull(idx)
                        },
                        onDragEnd = {
                            scrubX = null
                            selectedRecord = null
                        },
                        onDragCancel = {
                            scrubX = null
                            selectedRecord = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubX = change.position.x
                            if (records.isNotEmpty()) {
                                val idx = ((change.position.x / size.width) * (records.size - 1)).toInt().coerceIn(0, records.size - 1)
                                selectedRecord = records.getOrNull(idx)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                if (records.isEmpty()) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2f
                    )
                    return@Canvas
                }

                val values = records.map {
                    when (metric) {
                        GraphMetric.LEVEL -> it.level.toFloat()
                        GraphMetric.TEMP -> it.temperature
                        GraphMetric.POWER -> it.powerWatts
                        GraphMetric.VOLTAGE -> it.voltageMv.toFloat()
                    }
                }

                val rawMin = values.minOrNull() ?: 0f
                val rawMax = values.maxOrNull() ?: 100f
                val minVal = if (metric == GraphMetric.LEVEL) 0f else if (metric == GraphMetric.TEMP) 20f else rawMin * 0.95f
                val maxVal = if (metric == GraphMetric.LEVEL) 100f else if (metric == GraphMetric.TEMP) 50f else (rawMax * 1.05f).coerceAtLeast(minVal + 1f)
                val range = (maxVal - minVal).coerceAtLeast(1f)

                val width = size.width
                val height = size.height
                val stepX = if (records.size > 1) width / (records.size - 1) else width

                // Background horizontal grid lines
                for (i in 1..4) {
                    val gridY = height * (i / 5f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(0f, gridY),
                        end = Offset(width, gridY),
                        strokeWidth = 1f
                    )
                }

                // 40°C Red Dashed Danger Line if viewing Temperature
                if (metric == GraphMetric.TEMP) {
                    val dangerY = height - ((40.0f - minVal) / range * height)
                    if (dangerY in 0f..height) {
                        drawLine(
                            color = DangerRed,
                            start = Offset(0f, dangerY),
                            end = Offset(width, dangerY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    }
                }

                // Build graph curve
                val path = Path()
                val fillPath = Path()

                values.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - ((value - minVal) / range * height).coerceIn(0f, height)
                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (index - 1) * stepX
                        val prevY = height - ((values[index - 1] - minVal) / range * height).coerceIn(0f, height)
                        val cx = (prevX + x) / 2f
                        path.cubicTo(cx, prevY, cx, y, x, y)
                        fillPath.cubicTo(cx, prevY, cx, y, x, y)
                    }
                }

                fillPath.lineTo((values.size - 1) * stepX, height)
                fillPath.close()

                // Draw gradient under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            metricColor.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )

                // Draw line path
                drawPath(
                    path = path,
                    color = metricColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw scrub vertical line indicator if active
                scrubX?.let { sx ->
                    val clampedX = sx.coerceIn(0f, width)
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(clampedX, 0f),
                        end = Offset(clampedX, height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    // Find corresponding point y
                    val idx = ((clampedX / width) * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
                    val ptY = height - ((values[idx] - minVal) / range * height).coerceIn(0f, height)

                    drawCircle(
                        color = metricColor,
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
    }
}
