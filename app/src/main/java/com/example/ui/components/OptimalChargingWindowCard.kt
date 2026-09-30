package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.OptimalChargingWindowAdvisor
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession

@Composable
fun OptimalChargingWindowCard(
    records: List<BatteryRecord>,
    sessions: List<ChargingSession>,
    modifier: Modifier = Modifier
) {
    val suggestion = remember(records, sessions) {
        OptimalChargingWindowAdvisor.analyzeChargingHabitsAndSuggestWindows(records, sessions)
    }
    Column(modifier = modifier.fillMaxWidth().testTag("optimal_charging_window_card")) {
        Text("Optimal charging time unavailable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        Text(suggestion.keyScientificRationale, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
