package com.example.ui.components

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ai.OptimalChargingWindowAdvisor
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusRed

@Composable
fun OptimalChargingWindowCard(
    records: List<BatteryRecord>,
    sessions: List<ChargingSession>,
    modifier: Modifier = Modifier
) {
    val suggestion = remember(records, sessions) {
        OptimalChargingWindowAdvisor.analyzeChargingHabitsAndSuggestWindows(records, sessions)
    }

    SentinelCard(
        title = "Optimal Charging Window Advisor",
        icon = Icons.Default.Schedule,
        dotState = DotState.CONNECTED,
        accentColor = NetraEmerald,
        trailingAction = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetraEmerald.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = suggestion.predictedLifespanExtension,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetraEmerald
                )
            }
        },
        modifier = modifier.testTag("optimal_charging_window_card")
    ) {
        // Primary & Secondary Recommended Windows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WindowBox(
                label = "🌟 PRIMARY OPTIMAL SLOT",
                window = suggestion.primaryOptimalWindow,
                sub = "Lowest thermal load & zero overnight dwell",
                accent = NetraEmerald,
                modifier = Modifier.weight(1f)
            )
            WindowBox(
                label = "⚡ SECONDARY OPTIMAL SLOT",
                window = suggestion.secondaryOptimalWindow,
                sub = "Pre-sleep 80% charge with cutoff alarm",
                accent = NetraCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Discouraged Window Warning
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DangerRed.copy(alpha = 0.1f))
                .border(1.dp, DangerRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.DoNotDisturb, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "DISCOURAGED: ${suggestion.discouragedWindow}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed
                    )
                    Text(
                        text = "6+ hours at 100% SoC accelerates transition-metal dissolution and SEI growth.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 24-Hour Diurnal Timeline Schedule Bar
        Text(
            text = "24-Hour Habit & Thermal Timeline Schedule",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Time-series suitability scores calculated from historical Room temperature & dwell metrics",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Visual 24-Hour Slot Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            suggestion.hourlyScores.forEach { slot ->
                val slotColor = when (slot.statusLabel) {
                    "OPTIMAL" -> NetraEmerald
                    "ACCEPTABLE" -> NetraCyan.copy(alpha = 0.7f)
                    "WARM" -> StatusAmber
                    else -> DangerRed.copy(alpha = 0.7f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .background(slotColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (slot.hourOfDay % 6 == 0) {
                        Text(
                            text = "${slot.hourOfDay}h",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Timeline Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem("Optimal (Cool)", NetraEmerald)
            LegendItem("Acceptable", NetraCyan)
            LegendItem("Warm Heat", StatusAmber)
            LegendItem("Avoid (Dwell)", DangerRed)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "🔬 Scientific Principle: ${suggestion.keyScientificRationale}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WindowBox(label: String, window: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(text = label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = window, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
