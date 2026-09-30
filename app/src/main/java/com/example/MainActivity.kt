package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.StatusDot
import com.example.ui.navigation.NetraBottomNav
import com.example.ui.navigation.NetraTab
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.MonitoringScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NetraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            com.example.service.BatteryMonitorService.startService(this)
        } catch (_: Exception) {}

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHardwareState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: NetraViewModel) {
    var currentTab by remember { mutableStateOf(NetraTab.HOME) }
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val canonical by viewModel.canonicalState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Thermal & Low Battery Brightness Protection Lock
    LaunchedEffect(canonical.targetBrightnessPercent) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val target = canonical.targetBrightnessPercent
        if (window != null) {
            val lp = window.attributes
            if (target != null) {
                lp.screenBrightness = (target / 100f).coerceIn(0.01f, 1.0f)
            } else {
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = lp
        }
    }

    // Request permissions on startup gracefully
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshHardwareState()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // BackHandler: return to Home tab if on secondary tab
    BackHandler(enabled = currentTab != NetraTab.HOME) {
        currentTab = NetraTab.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NetraCyan.copy(alpha = 0.15f))
                                .border(1.dp, NetraCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Netra",
                                tint = NetraCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NETRA",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NetraEmerald.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "PRO SENTINEL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NetraEmerald
                                    )
                                }
                            }
                            Text(
                                text = "Ultra-Low Power 24/7 Engine",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Live Level & Temp Capsule
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (telemetry.temperature >= 40f) StatusRed.copy(alpha = 0.2f)
                                else NetraSurface
                            )
                            .border(
                                1.dp,
                                if (telemetry.temperature >= 40f) StatusRed
                                else NetraCyan.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(state = telemetry.serviceDotState, size = 6.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = canonical.batteryLevel?.let { "$it%" } ?: "Unavailable",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canonical.isCharging == true) NetraCyan else NetraEmerald
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = canonical.temperatureCelsius?.let { "• ${String.format("%.1f", it)}°C" } ?: "• Temp unavailable",
                                fontSize = 11.sp,
                                color = if ((canonical.temperatureCelsius ?: 0f) >= 40f) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.toggleUltraBatterySaver() },
                        modifier = Modifier.testTag("top_bar_ultra_saver_button")
                    ) {
                        val isUltraActive = settings.ultraBatterySaverActive
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Toggle Ultra Battery Saver",
                            tint = if (isUltraActive) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshHardwareState() },
                        modifier = Modifier.testTag("top_bar_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Telemetry",
                            tint = NetraCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NetraBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            com.example.ui.components.UltraBatterySaverBanner(
                isActive = settings.ultraBatterySaverActive,
                onToggle = { viewModel.toggleUltraBatterySaver() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Crossfade(
                targetState = currentTab,
                label = "tab_transition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    NetraTab.HOME -> StatusScreen(
                        viewModel = viewModel,
                        onOpenGraph = { currentTab = NetraTab.BATTERY },
                        onNavigateTab = { target ->
                            currentTab = target
                        }
                    )
                    NetraTab.BATTERY -> BatteryScreen(viewModel = viewModel)
                    NetraTab.MONITORING -> MonitoringScreen(viewModel = viewModel)
                    NetraTab.DEVICES -> DevicesScreen(viewModel = viewModel)
                    NetraTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
