package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SentinelSettings
import com.example.model.BatteryTelemetry
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.viewmodel.NetraViewModel
import com.example.widget.NetraBatteryWidgetProvider
import com.example.widget.NetraDegradationSparklineWidgetProvider

@Composable
fun WidgetCustomizationCard(
    viewModel: NetraViewModel,
    settings: SentinelSettings,
    telemetry: BatteryTelemetry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val themeColors = listOf(
        "CYAN" to ("Cyber Cyan" to NetraCyan),
        "EMERALD" to ("Emerald Green" to NetraEmerald),
        "AMBER" to ("Solar Amber" to StatusAmber),
        "RED" to ("Crimson Red" to DangerRed),
        "PURPLE" to ("Synth Purple" to Color(0xFFB388FF)),
        "MONO" to ("AMOLED White" to Color.White)
    )

    val refreshIntervals = listOf(
        0 to "⚡ Event-Driven (0s)",
        1 to "1 Min",
        5 to "5 Min",
        15 to "15 Min",
        30 to "30 Min"
    )

    val bgStyles = listOf(
        "GLASS_DARK" to "Dark Glass",
        "AMOLED_BLACK" to "AMOLED Black",
        "TRANSLUCENT" to "Translucent"
    )

    val activeColor = themeColors.find { it.first == settings.widgetThemeColor }?.second?.second ?: NetraCyan

    SentinelCard(
        title = "Home Screen Widget Customization",
        icon = Icons.Default.Widgets,
        dotState = DotState.CONNECTED,
        accentColor = activeColor,
        modifier = modifier.testTag("widget_customization_card")
    ) {
        Text(
            text = "Personalize colors, background styles, and telemetry sync intervals for both the Compact Battery Widget and the Long-Term Degradation Sparkline Widget.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Live Interactive Widget Preview
        Text(text = "LIVE WIDGET PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeColor)
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when (settings.widgetBackgroundStyle) {
                        "AMOLED_BLACK" -> Color.Black
                        "TRANSLUCENT" -> Color(0x99101926)
                        else -> Color(0xFF101926)
                    }
                )
                .border(1.5.dp, activeColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(activeColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("NETRA SENTINEL PRO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeColor)
                    }
                    Text(telemetry.healthScore?.let { "Score: $it/100" } ?: "Score: Unavailable", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (telemetry.isDataAvailable) "${telemetry.level}%" else "Unavailable",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (!telemetry.isDataAvailable) "Battery status unavailable" else if (telemetry.isCharging) "⚡ Charging (${telemetry.pluggedType})" else "🔋 Discharging",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetry.isCharging) activeColor else NetraEmerald
                        )
                        Text(
                            text = if (telemetry.isDataAvailable) "${String.format("%.1f", telemetry.temperature)}°C • ${telemetry.voltageMv}mV • Degradation Sparkline Active" else "Temperature and voltage unavailable",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Accent Color Selector
        Text(text = "ACCENT THEME COLOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themeColors.forEach { (key, pair) ->
                val (label, color) = pair
                val isSelected = settings.widgetThemeColor == key
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            viewModel.setWidgetThemeColor(key)
                            NetraBatteryWidgetProvider.updateAllWidgets(context, telemetry)
                            NetraDegradationSparklineWidgetProvider.updateAllWidgets(context)
                        }
                        .testTag("widget_color_${key.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Refresh Interval Selector
        Text(text = "DATA REFRESH FREQUENCY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            refreshIntervals.forEach { (interval, label) ->
                val isSelected = settings.widgetRefreshIntervalMinutes == interval
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.setWidgetRefreshInterval(interval)
                        NetraBatteryWidgetProvider.updateAllWidgets(context, telemetry)
                        NetraDegradationSparklineWidgetProvider.updateAllWidgets(context)
                    },
                    label = { Text(label, fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeColor.copy(alpha = 0.25f),
                        selectedLabelColor = activeColor
                    ),
                    modifier = Modifier.testTag("refresh_interval_$interval")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Background Style Selector
        Text(text = "BACKGROUND STYLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bgStyles.forEach { (styleKey, styleLabel) ->
                val isSelected = settings.widgetBackgroundStyle == styleKey
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.setWidgetBackgroundStyle(styleKey)
                        NetraBatteryWidgetProvider.updateAllWidgets(context, telemetry)
                        NetraDegradationSparklineWidgetProvider.updateAllWidgets(context)
                    },
                    label = { Text(styleLabel, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeColor.copy(alpha = 0.25f),
                        selectedLabelColor = activeColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                NetraBatteryWidgetProvider.updateAllWidgets(context, telemetry)
                NetraDegradationSparklineWidgetProvider.updateAllWidgets(context)
                Toast.makeText(context, "Widgets refreshed with new theme & data", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("refresh_widgets_button"),
            colors = ButtonDefaults.buttonColors(containerColor = activeColor, contentColor = Color.Black)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sync & Apply to Home Screen Widgets", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
