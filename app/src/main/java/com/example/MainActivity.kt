package com.example

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DotState
import com.example.ui.components.StatusDot
import com.example.ui.navigation.NetraBottomNav
import com.example.ui.navigation.NetraTab
import com.example.ui.screens.ChargingScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.DischargingScreen
import com.example.ui.screens.GraphScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.MonitoringScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NetraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    var currentTab by remember { mutableStateOf(NetraTab.STATUS) }
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()

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

    // BackHandler: return to Status tab if on secondary tab
    BackHandler(enabled = currentTab != NetraTab.STATUS) {
        currentTab = NetraTab.STATUS
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
                                text = "${telemetry.level}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (telemetry.isCharging) NetraCyan else NetraEmerald
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• ${String.format("%.1f", telemetry.temperature)}°C",
                                fontSize = 11.sp,
                                color = if (telemetry.temperature >= 40f) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    containerColor = NetraDarkBg
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NetraBottomNav(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        },
        containerColor = NetraDarkBg
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            label = "tab_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                NetraTab.STATUS -> StatusScreen(viewModel = viewModel, onNavigateTab = { currentTab = it })
                NetraTab.CHARGING -> ChargingScreen(viewModel = viewModel)
                NetraTab.DISCHARGING -> DischargingScreen(viewModel = viewModel)
                NetraTab.MONITORING -> MonitoringScreen(viewModel = viewModel)
                NetraTab.DEVICES -> DevicesScreen(viewModel = viewModel)
                NetraTab.GRAPH -> GraphScreen(viewModel = viewModel)
                NetraTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                NetraTab.LOGS -> LogsScreen(viewModel = viewModel)
            }
        }
    }
}
