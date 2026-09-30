package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BatteryRecord
import com.example.model.AppUsageItem

/** Battery samples and aggregate app foreground time have no shared per-app time series. */
@Composable
fun ThermalAppCorrelationHeatmap(
    records: List<BatteryRecord>,
    topApps: List<AppUsageItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag("thermal_app_heatmap_card")) {
        Text("App/thermal correlation unavailable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "${records.size} battery samples and ${topApps.size} app usage entries are available, but there is no timestamped app activity to link an app to a temperature sample.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
