package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.local.BatteryRecord
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber

@Composable
fun SparklineChart(
    records: List<BatteryRecord>,
    modifier: Modifier = Modifier,
    dangerThreshold: Float = 40.0f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (records.isEmpty()) {
                // Draw a flat baseline
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2f
                )
                return@Canvas
            }

            val temps = records.map { it.temperature }
            val minTemp = (temps.minOrNull() ?: 25f).coerceAtMost(30f)
            val maxTemp = (temps.maxOrNull() ?: 42f).coerceAtLeast(42f)
            val range = (maxTemp - minTemp).coerceAtLeast(5f)

            val width = size.width
            val height = size.height
            val stepX = if (records.size > 1) width / (records.size - 1) else width

            // 1. Draw 40°C Red Dashed Danger Line
            val dangerY = height - ((dangerThreshold - minTemp) / range * height)
            if (dangerY in 0f..height) {
                drawLine(
                    color = DangerRed,
                    start = Offset(0f, dangerY),
                    end = Offset(width, dangerY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // 2. Build Smooth Spline Curve for Temperatures
            val path = Path()
            val fillPath = Path()

            records.forEachIndexed { index, item ->
                val x = index * stepX
                val y = height - ((item.temperature - minTemp) / range * height).coerceIn(0f, height)
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevY = height - ((records[index - 1].temperature - minTemp) / range * height).coerceIn(0f, height)
                    val cx = (prevX + x) / 2f
                    path.cubicTo(cx, prevY, cx, y, x, y)
                    fillPath.cubicTo(cx, prevY, cx, y, x, y)
                }
            }

            fillPath.lineTo((records.size - 1) * stepX, height)
            fillPath.close()

            // Draw Gradient Fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NetraCyan.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
            )

            // Draw Line Curve
            val isHot = (temps.maxOrNull() ?: 0f) >= 40f
            drawPath(
                path = path,
                color = if (isHot) StatusAmber else NetraCyan,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
