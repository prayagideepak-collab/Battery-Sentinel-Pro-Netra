package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.AppUsageItem
import com.example.model.DotState
import com.example.ui.components.SentinelCard
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusRed
import com.example.util.PermissionHelper
import com.example.util.UsageStatsHelper
import com.example.viewmodel.NetraViewModel

@Composable
fun AppBatteryUsageScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(PermissionHelper.isUsageAccessGranted(context)) }
    var appUsageList by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    fun refreshAppUsage() {
        isLoading = true
        hasUsagePermission = PermissionHelper.isUsageAccessGranted(context)
        if (hasUsagePermission) {
            appUsageList = UsageStatsHelper.getAppUsageDrainList(context)
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshAppUsage()
    }

    val filteredList = remember(appUsageList, selectedCategoryFilter) {
        if (selectedCategoryFilter == "ALL") appUsageList
        else if (selectedCategoryFilter == "HIGH_DRAIN") appUsageList.filter { it.isHighDrain }
        else appUsageList.filter { it.category.contains(selectedCategoryFilter, ignoreCase = true) }
    }

    val totalEnergyMah = remember(appUsageList) {
        appUsageList.sumOf { it.estimatedEnergyMah }
    }

    val highestDrainApp = remember(appUsageList) {
        appUsageList.firstOrNull { it.isHighDrain }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: App Usage & BatteryStats Analytics
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "App Battery Consumption",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hardware energy breakdown via Android BatteryStats & UsageStats API",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { refreshAppUsage() },
                        modifier = Modifier.testTag("refresh_app_usage_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Permission Warning Card if not granted
        if (!hasUsagePermission) {
            item {
                SentinelCard(
                    title = "Usage Access Permission Required",
                    icon = Icons.Default.Lock,
                    dotState = DotState.THROTTLED,
                    accentColor = StatusAmber
                ) {
                    Text(
                        text = "Android requires Usage Access permission to read individual application foreground time and background wake lock energy consumption.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            UsageStatsHelper.openUsageAccessSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("grant_usage_permission_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.Black)
                    ) {
                        Text("Grant Usage Access in Android Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Energy KPI Summary Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppKpiBox("TOTAL 24H APP DRAIN", "$totalEnergyMah mAh", NetraCyan, Modifier.weight(1f))
                AppKpiBox("TRACKED APPS", "${appUsageList.size} Active", NetraEmerald, Modifier.weight(1f))
                AppKpiBox("HIGH DRAIN APPS", "${appUsageList.count { it.isHighDrain }} Flagged", if (appUsageList.any { it.isHighDrain }) StatusRed else NetraEmerald, Modifier.weight(1f))
            }
        }

        // Anomaly / Power-Hungry Alert Card
        highestDrainApp?.let { app ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(alpha = 0.12f))
                        .border(1.dp, DangerRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Power-Hungry App Detected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                            }
                            Text("${app.estimatedDrainPercent}% Drain", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DangerRed)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${app.appName} used ${app.foregroundTimeMinutes} mins active time consuming ~${app.estimatedEnergyMah} mAh. ${app.anomalyWarning ?: "Consider restricting background usage."}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                UsageStatsHelper.openAppDetailsSettings(context, app.packageName)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("restrict_app_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restrict Background Activity for ${app.appName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "All Apps",
                    "HIGH_DRAIN" to "⚡ High Drain",
                    "Media" to "Media",
                    "Social" to "Social",
                    "System" to "System"
                ).forEach { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NetraCyan.copy(alpha = 0.25f),
                            selectedLabelColor = NetraCyan
                        ),
                        modifier = Modifier.testTag("app_filter_$key")
                    )
                }
            }
        }

        // App List
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasUsagePermission) "No application drain recorded in this filter." else "Usage Access required to display application battery usage list.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredList, key = { it.packageName }) { app ->
                AppDrainCard(app = app, onOpenSettings = {
                    UsageStatsHelper.openAppDetailsSettings(context, app.packageName)
                })
            }
        }
    }
}

@Composable
private fun AppDrainCard(app: AppUsageItem, onOpenSettings: () -> Unit) {
    val drainColor = if (app.isHighDrain) DangerRed else if (app.estimatedDrainPercent > 8f) StatusAmber else NetraEmerald

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable { onOpenSettings() }
            .padding(14.dp)
            .testTag("app_item_${app.packageName}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // App Initial Avatar
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(drainColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = drainColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = app.appName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${app.category} • ${app.packageName.take(28)}",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${app.estimatedDrainPercent}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = drainColor
                    )
                    Text(
                        text = "~${app.estimatedEnergyMah} mAh",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Drain Progress Bar
            LinearProgressIndicator(
                progress = { (app.estimatedDrainPercent / 40f).coerceIn(0.02f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = drainColor,
                trackColor = Color.White.copy(alpha = 0.08f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats Sub-Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active: ${app.foregroundTimeMinutes}m • Dwell: ${app.backgroundTimeMinutes}m",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Rate: ${app.consumptionRateMahPerHour} mAh/h",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Manage", tint = NetraCyan, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AppKpiBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
