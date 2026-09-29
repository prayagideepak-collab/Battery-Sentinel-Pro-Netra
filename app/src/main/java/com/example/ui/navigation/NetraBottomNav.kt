package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface

enum class NetraTab(val title: String, val icon: ImageVector, val tag: String) {
    STATUS("Status", Icons.Default.Home, "tab_status"),
    CHARGING("Charging", Icons.Default.Bolt, "tab_charging"),
    DISCHARGING("Discharging", Icons.Default.BatteryAlert, "tab_discharging"),
    APPS("Apps", Icons.Default.Widgets, "tab_apps"),
    MONITORING("Monitoring", Icons.Default.QueryStats, "tab_monitoring"),
    DEVICES("Devices", Icons.Default.Headset, "tab_devices"),
    GRAPH("Graph", Icons.Default.ShowChart, "tab_graph"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings"),
    LOGS("Logs", Icons.Default.ListAlt, "tab_logs")
}

@Composable
fun NetraBottomNav(
    currentTab: NetraTab,
    onTabSelected: (NetraTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NetraDarkBg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetraTab.values().forEach { tab ->
                val isSelected = tab == currentTab
                val accent = if (tab == NetraTab.CHARGING) NetraCyan else NetraEmerald

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) accent.copy(alpha = 0.18f)
                            else NetraSurface.copy(alpha = 0.4f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) accent.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(tab.tag),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
