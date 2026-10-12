package com.bandcompanion.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bandcompanion.app.export.ExportManager
import com.bandcompanion.app.service.BandForegroundService
import com.bandcompanion.app.ui.screen.DashboardScreen
import com.bandcompanion.app.ui.screen.LiveMetricsScreen
import com.bandcompanion.app.ui.screen.SettingsScreen
import com.bandcompanion.app.ui.screen.SleepDetailScreen
import com.bandcompanion.app.ui.screen.TrendsScreen
import com.bandcompanion.app.ui.theme.BandCompanionTheme
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.core.database.AppDatabase
import com.bandcompanion.core.database.repository.DeviceRepository
import com.bandcompanion.core.database.repository.SampleRepository
import com.bandcompanion.core.database.repository.SleepRepository
import com.bandcompanion.core.database.repository.SummaryRepository
import com.bandcompanion.core.model.DeviceInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var summaryRepo: SummaryRepository
    private lateinit var sleepRepo: SleepRepository
    private lateinit var sampleRepo: SampleRepository
    private lateinit var exportManager: ExportManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBandService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getInstance(this)
        deviceRepo = DeviceRepository(database.deviceDao())
        summaryRepo = SummaryRepository(database.summaryDao())
        sleepRepo = SleepRepository(database.sleepDao())
        sampleRepo = SampleRepository(database.sampleDao())
        exportManager = ExportManager(this, sampleRepo, sleepRepo, summaryRepo)

        checkAndRequestPermissions()

        setContent {
            BandCompanionTheme {
                MainAppContent(
                    deviceRepo = deviceRepo,
                    summaryRepo = summaryRepo,
                    sleepRepo = sleepRepo,
                    sampleRepo = sampleRepo,
                    exportManager = exportManager,
                    onSyncRequested = { triggerManualSync() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startBandService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startBandService() {
        val intent = Intent(this, BandForegroundService::class.java).apply {
            action = BandForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun triggerManualSync() {
        val intent = Intent(this, BandForegroundService::class.java).apply {
            action = BandForegroundService.ACTION_SYNC_NOW
        }
        startService(intent)
    }
}

enum class NavigationTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Overview", Icons.Default.Dashboard),
    SLEEP("Sleep", Icons.Default.Bedtime),
    LIVE("Live", Icons.Default.Favorite),
    TRENDS("Trends", Icons.Default.ShowChart),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainAppContent(
    deviceRepo: DeviceRepository,
    summaryRepo: SummaryRepository,
    sleepRepo: SleepRepository,
    sampleRepo: SampleRepository,
    exportManager: ExportManager,
    onSyncRequested: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var isSyncing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val deviceInfo by deviceRepo.getPrimaryDeviceFlow().collectAsState(
        initial = DeviceInfo(macAddress = "C4:16:88:8D:04:EF", name = "HUAWEI Band 10", batteryLevel = 92, isConnected = true)
    )

    val todaySummary by summaryRepo.getSummaryForDate("C4:16:88:8D:04:EF", "2026-10-12").collectAsState(initial = null)
    val recentSummaries by summaryRepo.getRecentSummaries("C4:16:88:8D:04:EF", 30).collectAsState(initial = emptyList())
    val latestSleepSession by sleepRepo.getLatestSleepSession("C4:16:88:8D:04:EF").collectAsState(initial = null)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavigationTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(text = tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = SurfaceDark
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.DASHBOARD -> DashboardScreen(
                    deviceInfo = deviceInfo,
                    todaySummary = todaySummary,
                    isSyncing = isSyncing,
                    onSyncClick = {
                        isSyncing = true
                        onSyncRequested()
                    }
                )
                NavigationTab.SLEEP -> SleepDetailScreen(
                    session = latestSleepSession
                )
                NavigationTab.LIVE -> LiveMetricsScreen(
                    latestSample = null
                )
                NavigationTab.TRENDS -> TrendsScreen(
                    summaries = recentSummaries
                )
                NavigationTab.SETTINGS -> SettingsScreen(
                    deviceInfo = deviceInfo,
                    onExportCsvClick = {
                        coroutineScope.launch {
                            val now = System.currentTimeMillis() / 1000
                            val monthAgo = now - (30 * 86400)
                            val file = exportManager.exportSamplesToCsv(deviceInfo?.macAddress ?: "C4:16:88:8D:04:EF", monthAgo, now)
                            Toast.makeText(context, "CSV exported to: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onExportJsonClick = {
                        coroutineScope.launch {
                            val file = exportManager.exportFullJson(deviceInfo?.macAddress ?: "C4:16:88:8D:04:EF")
                            Toast.makeText(context, "JSON exported to: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}
