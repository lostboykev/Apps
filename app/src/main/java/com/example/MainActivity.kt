package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ShizukuStatusDialog
import com.example.ui.components.StatusHeader
import com.example.ui.components.TerminalOutputCard
import com.example.ui.screens.BatteryBloatScreen
import com.example.ui.screens.CompilationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.ThermalWakelockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SysOptViewModel

data class NavTabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: SysOptViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var selectedTabIndex by remember { mutableIntStateOf(0) }
                var showShizukuDialog by remember { mutableStateOf(false) }

                val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
                val activeJob by viewModel.activeJob.collectAsStateWithLifecycle()
                val lastOutput by viewModel.lastOutput.collectAsStateWithLifecycle()

                val navItems = listOf(
                    NavTabItem("Doze & OS", Icons.Filled.BatterySaver, Icons.Outlined.BatterySaver, "nav_doze"),
                    NavTabItem("AOT Speed", Icons.Filled.Bolt, Icons.Outlined.Bolt, "nav_aot"),
                    NavTabItem("Drainers", Icons.Filled.HourglassBottom, Icons.Outlined.HourglassBottom, "nav_drainers"),
                    NavTabItem("Thermals", Icons.Filled.Thermostat, Icons.Outlined.Thermostat, "nav_thermals"),
                    NavTabItem("Logs", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_logs")
                )

                BackHandler(enabled = selectedTabIndex != 0) {
                    selectedTabIndex = 0
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "SysOpt",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "Shell & Battery Optimizer",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showShizukuDialog = true },
                                    modifier = Modifier.testTag("app_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings & Shizuku Status",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.navigationBarsPadding(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            navItems.forEachIndexed { index, item ->
                                val selected = selectedTabIndex == index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTabIndex = index },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Global Status Header & Cancelable Active Job Banner
                        StatusHeader(
                            shizukuStatus = shizukuStatus,
                            activeJob = activeJob,
                            onOpenShizukuGuide = { showShizukuDialog = true },
                            onCancelActiveJob = { viewModel.cancelActiveJob() }
                        )

                        // Console Terminal Output Card (Shows on command completion)
                        TerminalOutputCard(
                            output = lastOutput,
                            onDismiss = { viewModel.clearLastOutput() }
                        )

                        // Main Navigation Content
                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTabIndex) {
                                0 -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToThermals = { selectedTabIndex = 3 }
                                )
                                1 -> CompilationScreen(viewModel = viewModel)
                                2 -> BatteryBloatScreen(viewModel = viewModel)
                                3 -> ThermalWakelockScreen(viewModel = viewModel)
                                4 -> LogsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }

                if (showShizukuDialog) {
                    ShizukuStatusDialog(
                        status = shizukuStatus,
                        isSimulationMode = viewModel.repository.isSimulationMode(),
                        onDismiss = { showShizukuDialog = false },
                        onRequestPermission = { viewModel.requestShizukuPermission() },
                        onRefreshStatus = { viewModel.refreshShizukuStatus() },
                        onToggleSimulation = { viewModel.toggleSimulationMode(it) }
                    )
                }
            }
        }
    }
}
