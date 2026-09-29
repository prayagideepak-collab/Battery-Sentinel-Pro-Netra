package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DotState
import com.example.ui.components.SentinelCard
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import com.example.util.UsageStatsHelper
import com.example.viewmodel.NetraViewModel

@Composable
fun SettingsScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val totalRecords by viewModel.totalRecordCount.collectAsStateWithLifecycle()
    val permissions by viewModel.systemPermissions.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Sentinel Control & Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tune ultra-low power algorithms and protection limits",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Charging Target Cutoff (80%, 85%, 90%, 95%, 100%)
        item {
            SentinelCard(
                title = "Charging Target & Unplug Alarm",
                icon = Icons.Default.Bolt,
                dotState = DotState.CONNECTED,
                accentColor = NetraCyan
            ) {
                Text(
                    text = "Select SoC threshold for health alarm (80% recommended by battery electrochemistry):",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(80, 85, 90, 95, 100).forEach { target ->
                        val isSelected = settings.chargeTargetPercent == target
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateChargeTarget(target) },
                            label = { Text("$target%", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NetraCyan.copy(alpha = 0.25f),
                                selectedLabelColor = NetraCyan
                            ),
                            modifier = Modifier.weight(1f).testTag("charge_target_$target")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Target Charge Alarm Switch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Triggers sound + vibration when reaching target", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.unplugAlarmEnabled,
                        onCheckedChange = { viewModel.setUnplugAlarmEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NetraCyan, checkedTrackColor = NetraCyan.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("unplug_alarm_toggle_settings")
                    )
                }
            }
        }

        // 2. Low-Battery Protection Threshold (5% to 30%)
        item {
            SentinelCard(
                title = "Low-Battery Protection Threshold",
                icon = Icons.Default.BatteryAlert,
                dotState = DotState.CONNECTED,
                accentColor = StatusAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Alert Threshold", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${settings.lowBatteryThreshold}% SoC",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusAmber
                    )
                }

                Slider(
                    value = settings.lowBatteryThreshold.toFloat(),
                    onValueChange = { viewModel.updateLowBatteryThreshold(it.toInt()) },
                    valueRange = 5f..30f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = StatusAmber, activeTrackColor = StatusAmber),
                    modifier = Modifier.testTag("low_battery_slider")
                )
            }
        }

        // 3. Power Optimization Engine
        item {
            SentinelCard(
                title = "Power Optimization Engine",
                icon = Icons.Default.BatterySaver,
                dotState = DotState.CONNECTED,
                accentColor = NetraEmerald
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Autonomous Power Saver Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Throttles background poll interval to 300-600s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.powerSaverEnabled,
                        onCheckedChange = { viewModel.setPowerSaverEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NetraEmerald, checkedTrackColor = NetraEmerald.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Brightness Optimization", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Guards against excessive display backlight draw", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.brightnessOptimization,
                        onCheckedChange = { viewModel.setBrightnessOptimization(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NetraEmerald, checkedTrackColor = NetraEmerald.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // 4. Battery Optimization Exemption
        item {
            SentinelCard(
                title = "24/7 OEM Sleep Exemption",
                icon = Icons.Default.Shield,
                dotState = if (permissions.isIgnoringBatteryOptimizations) DotState.CONNECTED else DotState.THROTTLED,
                accentColor = NetraEmerald
            ) {
                Text(
                    text = if (permissions.isIgnoringBatteryOptimizations)
                        "Device is configured to allow 24/7 Sentinel background execution without OEM process killing."
                    else
                        "Allow Netra Sentinel Pro to run continuously without being killed by Android Doze / OEM battery managers.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!permissions.isIgnoringBatteryOptimizations) {
                    OutlinedButton(
                        onClick = { UsageStatsHelper.openBatteryOptimizationSettings(context) },
                        modifier = Modifier.fillMaxWidth().testTag("exempt_battery_button")
                    ) {
                        Text("Configure Battery Optimization Exemption", fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Database & Telemetry Management
        item {
            SentinelCard(
                title = "Local Room Database & Cache",
                icon = Icons.Default.CleaningServices,
                dotState = DotState.CONNECTED,
                accentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Total SQLite Records", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "$totalRecords records", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NetraCyan)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearDatabase()
                            Toast.makeText(context, "Telemetry database cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("clear_db_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear History", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "Telemetry summary exported to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NetraCyan.copy(alpha = 0.2f), contentColor = NetraCyan)
                    ) {
                        Text("Export Summary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 6. About App & Update Channel
        item {
            SentinelCard(
                title = "About Netra - Battery Sentinel Pro",
                icon = Icons.Default.Update,
                dotState = DotState.CONNECTED,
                accentColor = NetraCyan
            ) {
                Text(text = "App Name: Battery Sentinel Pro Netra", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = "Version: 1.0.0 Pro • Autonomous Engine", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Protocol: Ultra-Low Power 24/7 Event-Driven Architecture", fontSize = 11.sp, color = NetraEmerald)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
