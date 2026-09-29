package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.model.PowerProfileMode
import com.example.model.PowerProfileState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue

@Composable
fun PowerSavingProfileCard(
    profileState: PowerProfileState,
    onSelectProfile: (PowerProfileMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = profileState.activeEffectiveMode
    val accentColor = when (activeMode) {
        PowerProfileMode.ULTRA_SAVER -> DangerRed
        PowerProfileMode.ENDURANCE -> StatusAmber
        PowerProfileMode.PERFORMANCE -> NetraCyan
        else -> NetraEmerald
    }

    SentinelCard(
        title = "Dynamic Power-Saving Profiles",
        icon = Icons.Default.BatterySaver,
        dotState = if (profileState.dynamicSyncThrottled) DotState.THROTTLED else DotState.CONNECTED,
        accentColor = accentColor,
        trailingAction = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = activeMode.title.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        },
        modifier = modifier.testTag("power_saving_profile_card")
    ) {
        Text(
            text = "Dynamically adjusts background sync frequency, telemetry polling intervals, and display brightness limits based on battery level thresholds.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PowerProfileMode.entries.forEach { mode ->
                val isSelected = profileState.selectedMode == mode
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectProfile(mode) },
                    label = {
                        Text(
                            text = mode.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.25f),
                        selectedLabelColor = accentColor
                    ),
                    modifier = Modifier.testTag("profile_chip_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Profile Details & Active Status Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active: ${activeMode.title}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = if (profileState.selectedMode == PowerProfileMode.SMART_ADAPTIVE) "🤖 Auto Threshold" else "⚙️ Manual",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profileState.lastProfileTransitionReason,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2-Column Key Setting Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = NetraCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("BACKGROUND SYNC", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${activeMode.syncIntervalSeconds}s Interval", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetraCyan)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Brightness6, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("DISPLAY BRIGHTNESS", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Cap: ${activeMode.brightnessCappedPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}
