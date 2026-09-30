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

/** A capacity-health chart requires measured capacity history; Room battery samples do not provide it. */
@Composable
fun BatteryHealthTrendLineChart(
    records: List<BatteryRecord>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("battery_health_trend_line_chart")
    ) {
        Text(
            "Capacity health trend unavailable",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "${records.size} battery samples recorded. Level, temperature and voltage do not measure remaining cell capacity.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
