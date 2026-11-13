package com.prayagi.netraguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.prayagi.netraguardian.ui.GuardianViewModel
import com.prayagi.netraguardian.ui.theme.GuardianTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: GuardianViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.prayagi.netraguardian.work.GuardianWorkScheduler.schedule(this)
        setContent {
            GuardianTheme {
                val reading by viewModel.reading.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                GuardianHome(reading, settings.voiceEnabled, settings.nightSilentEnabled, settings.language,
                    viewModel::setVoice, viewModel::setNightSilent, viewModel::setLanguage, viewModel::clearLocalHistory)
            }
        }
    }
}

private enum class GuardianTab { Home, Battery, Heat, Apps, Storage, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardianHome(
    reading: com.prayagi.netraguardian.domain.BatteryReading?,
    voiceEnabled: Boolean, nightSilentEnabled: Boolean, language: String,
    onVoice: (Boolean) -> Unit, onSilent: (Boolean) -> Unit,
    onLanguage: (String) -> Unit, onClear: () -> Unit
) {
    var tab by remember { mutableStateOf(GuardianTab.Home) }
    val hindi = language == "hi"
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Text(if (hindi) "नेत्र बैटरी गार्जियन" else "Netra Battery Guardian", fontWeight = FontWeight.Bold)
            Text(if (hindi) "ऑफ़लाइन बैटरी और तापमान सुरक्षा" else "Offline battery & thermal protection", style = MaterialTheme.typography.labelSmall)
        } }) },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    GuardianTab.Home to Icons.Default.BatteryChargingFull,
                    GuardianTab.Battery to Icons.Default.BatteryChargingFull,
                    GuardianTab.Heat to Icons.Default.DeviceThermostat,
                    GuardianTab.Apps to Icons.Default.Apps,
                    GuardianTab.Storage to Icons.Default.Folder,
                    GuardianTab.Settings to Icons.Default.Settings
                )
                items.forEach { (item, icon) ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item },
                        icon = { Icon(icon, contentDescription = item.name) },
                        label = { Text(if (hindi) when(item) {
                            GuardianTab.Home -> "होम"; GuardianTab.Battery -> "बैटरी"; GuardianTab.Heat -> "हीट"
                            GuardianTab.Apps -> "ऐप्स"; GuardianTab.Storage -> "स्टोरेज"; GuardianTab.Settings -> "सेटिंग"
                        } else item.name) })
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                GuardianTab.Home, GuardianTab.Battery -> {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(reading?.levelPercent?.let { "$it%" } ?: "--", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                                Text(reading?.chargingLabel ?: if (hindi) "बैटरी डेटा पढ़ रहे हैं…" else "Reading battery data…")
                                Spacer(Modifier.height(8.dp))
                                Text(reading?.let { "Temperature ${it.temperatureLabel}" } ?: "Temperature unavailable")
                            }
                        }
                    }
                    item { MetricCard("Voltage", reading?.voltageMillivolts?.let { "$it mV" } ?: "Unavailable", "Device battery telemetry") }
                    item { MetricCard("Current", reading?.currentMicroamps?.let { "%.0f mA".format(it / 1000f) } ?: "Unavailable", "BatteryManager reading; sign is device-dependent") }
                    item { MetricCard("Net power", reading?.powerWatts?.let { "%.2f W".format(it) } ?: "Unavailable", "Voltage × current; device-reported battery-side estimate") }
                    item { MetricCard("Battery health", reading?.health ?: "Not exposed by this device", "No invented health percentage") }
                }
                GuardianTab.Heat -> {
                    item { MetricCard("Battery temperature", reading?.temperatureLabel ?: "Unavailable", reading?.thermalLabel ?: "Waiting for battery telemetry") }
                    item { MetricCard("Thermal status", "API-dependent", "Android thermal status is exposed only on supported versions and devices.") }
                    item { Text("The app cannot physically cool the phone. Follow system warnings and safe recommendations.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                GuardianTab.Apps -> {
                    item { MetricCard("Per-app battery attribution", "Unavailable through standard public APIs", "Android does not expose reliable per-app battery-drain ranking to ordinary third-party apps. No fabricated rankings.") }
                    item { MetricCard("Safe alternative", "Android Settings", "Use Settings → Battery to inspect system-provided app usage.") }
                }
                GuardianTab.Storage -> {
                    item { MetricCard("Cleanup policy", "App cache only", "Only this app's own cache may be cleared automatically. Other files require explicit review in Android Settings.") }
                    item { MetricCard("Storage details", "Not yet measured", "Storage summary and guided cleanup are a follow-on implementation task.") }
                }
                GuardianTab.Settings -> {
                    item { Text("Privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { Text("No account. No login. No cloud. No analytics. No ads. No tracking. Battery readings and history stay on this device. The application manifest declares no INTERNET permission.") }
                    item { HorizontalDivider() }
                    item { SettingSwitch("Voice announcements (UI preference only)", voiceEnabled, onVoice) }
                    item { SettingSwitch("Night Silent Mode (11 PM–6 AM preference)", nightSilentEnabled, onSilent) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Language")
                            FilterChip(selected = language == "en", onClick = { onLanguage("en") }, label = { Text("English") })
                            FilterChip(selected = language == "hi", onClick = { onLanguage("hi") }, label = { Text("हिन्दी") })
                        }
                    }
                    item { OutlinedButton(onClick = onClear) { Text("Delete local battery history") } }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
