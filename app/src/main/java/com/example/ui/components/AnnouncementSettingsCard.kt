package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SentinelSettings
import com.example.model.DotState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.viewmodel.NetraViewModel

@Composable
fun AnnouncementSettingsCard(
    viewModel: NetraViewModel,
    settings: SentinelSettings,
    modifier: Modifier = Modifier
) {
    SentinelCard(
        title = "Voice Announcement Engine",
        icon = Icons.Default.VolumeUp,
        dotState = if (settings.announcementsMasterEnabled) DotState.CONNECTED else DotState.STANDBY,
        accentColor = NetraCyan,
        modifier = modifier.testTag("announcement_settings_card")
    ) {
        // Master Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Announcement System",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Real-time state-transition speech engine with priority queuing & media coordination",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.announcementsMasterEnabled,
                onCheckedChange = { viewModel.setAnnouncementsMasterEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NetraCyan,
                    checkedTrackColor = NetraCyan.copy(alpha = 0.3f)
                ),
                modifier = Modifier.testTag("announcements_master_switch")
            )
        }

        AnimatedVisibility(visible = settings.announcementsMasterEnabled) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))

                // Night Protection Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (settings.nightProtectionEnabled) StatusBlue.copy(alpha = 0.12f)
                            else NetraSurface
                        )
                        .border(
                            1.dp,
                            if (settings.nightProtectionEnabled) StatusBlue.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = StatusBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Night Protection",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "11:00 PM – 6:00 AM (Default)",
                                        fontSize = 10.5.sp,
                                        color = StatusBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Switch(
                                checked = settings.nightProtectionEnabled,
                                onCheckedChange = { viewModel.setNightProtectionEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = StatusBlue,
                                    checkedTrackColor = StatusBlue.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("night_protection_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (settings.nightProtectionEnabled) {
                                "• Normal 5% & 10% battery announcements suppressed\n• Charging speed alerts suppressed\n• CRITICAL EXCEPTIONS ALLOWED: Thermal warnings, charger connected/disconnected"
                            } else {
                                "Night Protection disabled. Voice announcements will speak 24/7."
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Media Playback Coordination Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NetraEmerald.copy(alpha = 0.08f))
                        .border(1.dp, NetraEmerald.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = NetraEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Media Playback Coordination",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Pause playing media → Speak announcement → Resume. Already-paused media stays paused.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings.mediaPlaybackHandlingEnabled,
                            onCheckedChange = { viewModel.setMediaPlaybackHandlingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NetraEmerald,
                                checkedTrackColor = NetraEmerald.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("media_handling_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "VOICE ALERT TRIGGERS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Phone Battery Announcements (5% Boundaries)
                AnnouncementToggleItem(
                    icon = Icons.Default.BatteryChargingFull,
                    iconTint = NetraCyan,
                    title = "Phone Battery Announcements",
                    subtitle = "Spoken at every 5% boundary: 'C 80 percent' (Charging) or 'D 80 percent' (Discharging)",
                    checked = settings.announcePhoneBattery,
                    onCheckedChange = { viewModel.setAnnouncePhoneBattery(it) },
                    testTag = "announce_phone_battery_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Charger Connected / Disconnected
                AnnouncementToggleItem(
                    icon = Icons.Default.Power,
                    iconTint = NetraEmerald,
                    title = "Charger Connected / Disconnected",
                    subtitle = "Speaks 'Charger connected.' and 'Charger disconnected.' on state transitions",
                    checked = settings.announceChargerConnected,
                    onCheckedChange = { viewModel.setAnnounceChargerConnected(it) },
                    testTag = "announce_charger_connected_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Charging Speed Changes
                AnnouncementToggleItem(
                    icon = Icons.Default.Bolt,
                    iconTint = StatusAmber,
                    title = "Charging Speed Transitions",
                    subtitle = "Speaks 'Slow charging.', 'Normal charging.', 'F charging.' (10-20W), or 'UF charging.' (>20W)",
                    checked = settings.announceChargingSpeed,
                    onCheckedChange = { viewModel.setAnnounceChargingSpeed(it) },
                    testTag = "announce_charging_speed_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Bluetooth Battery (10% Boundaries)
                AnnouncementToggleItem(
                    icon = Icons.Default.Bluetooth,
                    iconTint = StatusBlue,
                    title = "Bluetooth Device & Battery",
                    subtitle = "Speaks 'BT connected.', 'BT disconnected.', and 'BT 50 percent' on verified 10% boundaries",
                    checked = settings.announceBluetoothBattery,
                    onCheckedChange = { viewModel.setAnnounceBluetoothBattery(it) },
                    testTag = "announce_bt_battery_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Thermal Warning Alerts
                AnnouncementToggleItem(
                    icon = Icons.Default.Thermostat,
                    iconTint = DangerRed,
                    title = "Thermal Protection Warnings",
                    subtitle = "Speaks: 'Thermal warning. Your phone temperature is [XX.X] degrees. Please stop using the phone. Nethra is cooling down the device.'",
                    checked = settings.announceThermalWarning,
                    onCheckedChange = { viewModel.setAnnounceThermalWarning(it) },
                    testTag = "announce_thermal_warning_switch"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Verification & Test Buttons
                Text(
                    text = "SPEECH TEST & PREVIEW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.testVoiceAnnouncement("C 80 percent") },
                        modifier = Modifier.weight(1f).testTag("test_c80_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetraCyan)
                    ) {
                        Text("C 80%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.testVoiceAnnouncement("D 75 percent") },
                        modifier = Modifier.weight(1f).testTag("test_d75_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetraEmerald)
                    ) {
                        Text("D 75%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.testVoiceAnnouncement("UF charging.") },
                        modifier = Modifier.weight(1f).testTag("test_uf_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber)
                    ) {
                        Text("UF Charge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementToggleItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetraSurface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = iconTint,
                checkedTrackColor = iconTint.copy(alpha = 0.3f)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
