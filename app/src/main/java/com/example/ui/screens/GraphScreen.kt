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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.ShowChart
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
import com.example.ui.components.GraphMetric
import com.example.ui.components.InteractiveTelemetryGraph
import com.example.ui.components.SentinelCard
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel
import com.example.viewmodel.TimeWindowFilter

@Composable
fun GraphScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(GraphMetric.LEVEL) }
    val timeWindow by viewModel.selectedTimeWindow.collectAsStateWithLifecycle()
    val records by viewModel.graphRecords.collectAsStateWithLifecycle()

    // Calculate Statistics
    val values = records.map {
        when (selectedMetric) {
            GraphMetric.LEVEL -> it.level.toFloat()
            GraphMetric.TEMP -> it.temperature
            GraphMetric.POWER -> it.powerWatts
            GraphMetric.VOLTAGE -> it.voltageMv.toFloat()
        }
    }

    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 0f
    val avgVal = if (values.isNotEmpty()) values.average().toFloat() else 0f
    val netChange = if (values.size >= 2) values.last() - values.first() else 0f

    val metricUnit = when (selectedMetric) {
        GraphMetric.LEVEL -> "%"
        GraphMetric.TEMP -> "°C"
        GraphMetric.POWER -> "W"
        GraphMetric.VOLTAGE -> "mV"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Activity Telemetry & Health Analytics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dynamic line chart analytics & telemetry from SQLite Room database",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Dynamic Battery Health % Over Time Line Chart
        item {
            SentinelCard(
                title = "Battery Health % Over Time",
                icon = Icons.Default.ShowChart,
                dotState = com.example.model.DotState.CONNECTED,
                accentColor = NetraEmerald
            ) {
                com.example.ui.components.BatteryHealthTrendLineChart(records = records)
            }
        }

        // Metric Selector Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricChip("🔋 Level (%)", selectedMetric == GraphMetric.LEVEL, NetraEmerald, { selectedMetric = GraphMetric.LEVEL }, Modifier.weight(1f))
                MetricChip("🌡️ Temp (°C)", selectedMetric == GraphMetric.TEMP, StatusAmber, { selectedMetric = GraphMetric.TEMP }, Modifier.weight(1f))
                MetricChip("⚡ Power (W)", selectedMetric == GraphMetric.POWER, NetraCyan, { selectedMetric = GraphMetric.POWER }, Modifier.weight(1f))
                MetricChip("🔌 Voltage", selectedMetric == GraphMetric.VOLTAGE, StatusBlue, { selectedMetric = GraphMetric.VOLTAGE }, Modifier.weight(1f))
            }
        }

        // Time Window Selector Chips (1 Hour, 6 Hours, 24 Hours, All Time)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeChip("1 Hour", timeWindow == TimeWindowFilter.ONE_HOUR, { viewModel.setTimeWindow(TimeWindowFilter.ONE_HOUR) }, Modifier.weight(1f))
                TimeChip("6 Hours", timeWindow == TimeWindowFilter.SIX_HOURS, { viewModel.setTimeWindow(TimeWindowFilter.SIX_HOURS) }, Modifier.weight(1f))
                TimeChip("24 Hours", timeWindow == TimeWindowFilter.TWENTY_FOUR_HOURS, { viewModel.setTimeWindow(TimeWindowFilter.TWENTY_FOUR_HOURS) }, Modifier.weight(1f))
                TimeChip("All Time", timeWindow == TimeWindowFilter.ALL_TIME, { viewModel.setTimeWindow(TimeWindowFilter.ALL_TIME) }, Modifier.weight(1f))
            }
        }

        // Interactive Telemetry Canvas Graph with touch-scrub
        item {
            InteractiveTelemetryGraph(
                records = records,
                metric = selectedMetric
            )
        }

        // Statistics Cards (Min, Max, Average, Net Change)
        item {
            Text(
                text = "Telemetry Statistics (${records.size} Samples)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("MIN", "${formatVal(minVal, selectedMetric)}$metricUnit", NetraCyan, Modifier.weight(1f))
                StatCard("MAX", "${formatVal(maxVal, selectedMetric)}$metricUnit", if (selectedMetric == GraphMetric.TEMP && maxVal >= 40f) StatusRed else StatusAmber, Modifier.weight(1f))
                StatCard("AVERAGE", "${formatVal(avgVal, selectedMetric)}$metricUnit", NetraEmerald, Modifier.weight(1f))
                StatCard("NET CHANGE", "${if (netChange >= 0) "+" else ""}${formatVal(netChange, selectedMetric)}$metricUnit", if (netChange >= 0) NetraEmerald else StatusAmber, Modifier.weight(1f))
            }
        }

        // 40°C Danger Threshold Notice for Temp
        if (selectedMetric == GraphMetric.TEMP) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(alpha = 0.1f))
                        .border(1.dp, DangerRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(DangerRed))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Red Dashed Line marks the 40°C thermal degradation threshold. Sustained temperatures above 40°C accelerate cell degradation.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

private fun formatVal(v: Float, metric: GraphMetric): String {
    return when (metric) {
        GraphMetric.LEVEL -> String.format("%.0f", v)
        GraphMetric.TEMP -> String.format("%.1f", v)
        GraphMetric.POWER -> String.format("%.2f", v)
        GraphMetric.VOLTAGE -> String.format("%.0f", v)
    }
}

@Composable
private fun MetricChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 8.dp)
            .testTag("metric_chip_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NetraEmerald.copy(alpha = 0.2f),
            selectedLabelColor = NetraEmerald
        ),
        modifier = modifier
    )
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
