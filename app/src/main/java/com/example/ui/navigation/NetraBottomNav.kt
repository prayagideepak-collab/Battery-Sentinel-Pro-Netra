package com.example.ui.navigation

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.ui.theme.NetraSurface

enum class NetraTab(val title: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Default.Home, "tab_home"),
    BATTERY("Battery", Icons.Default.Bolt, "tab_battery"),
    MONITORING("Monitoring", Icons.Default.QueryStats, "tab_monitoring"),
    DEVICES("Devices", Icons.Default.Headset, "tab_devices"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings"),
    // Compat aliases
    STATUS("Home", Icons.Default.Home, "tab_home"),
    CHARGING("Battery", Icons.Default.Bolt, "tab_charging"),
    DISCHARGING("Battery", Icons.Default.BatteryAlert, "tab_discharging"),
    APPS("Monitoring", Icons.Default.Widgets, "tab_apps"),
    GRAPH("Monitoring", Icons.Default.ShowChart, "tab_graph"),
    LOGS("Monitoring", Icons.Default.ListAlt, "tab_logs")
}

val BOTTOM_TABS = listOf(
    NetraTab.HOME,
    NetraTab.BATTERY,
    NetraTab.MONITORING,
    NetraTab.DEVICES,
    NetraTab.SETTINGS
)

@Composable
fun NetraBottomNav(
    currentTab: NetraTab,
    onTabSelected: (NetraTab) -> Unit,
    modifier: Modifier = Modifier
) {
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
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BOTTOM_TABS.forEach { tab ->
                val isSelected = tab == currentTab ||
                        (currentTab == NetraTab.STATUS && tab == NetraTab.HOME) ||
                        ((currentTab == NetraTab.CHARGING || currentTab == NetraTab.DISCHARGING) && tab == NetraTab.BATTERY) ||
                        ((currentTab == NetraTab.APPS || currentTab == NetraTab.GRAPH || currentTab == NetraTab.LOGS) && tab == NetraTab.MONITORING)

                val accent = NetraCyan

                Box(
                    modifier = Modifier
                        .weight(1f)
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
                        .padding(vertical = 10.dp)
                        .testTag(tab.tag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
