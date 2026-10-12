package com.bandcompanion.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
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
import com.bandcompanion.app.ui.screen.PairingScreen
import com.bandcompanion.app.ui.screen.SettingsScreen
import com.bandcompanion.app.ui.screen.SleepDetailScreen
import com.bandcompanion.app.ui.screen.TrendsScreen
import com.bandcompanion.app.ui.theme.BandCompanionTheme
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.core.bluetooth.BleConnectionManager
import com.bandcompanion.core.bluetooth.BleScanner
import com.bandcompanion.core.bluetooth.DiscoveredBand
import com.bandcompanion.core.bluetooth.crypto.HiChain3Crypto
import com.bandcompanion.core.database.AppDatabase
import com.bandcompanion.core.database.repository.DeviceRepository
import com.bandcompanion.core.database.repository.SampleRepository
import com.bandcompanion.core.database.repository.SleepRepository
import com.bandcompanion.core.database.repository.SummaryRepository
import com.bandcompanion.core.model.DeviceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var summaryRepo: SummaryRepository
    private lateinit var sleepRepo: SleepRepository
    private lateinit var sampleRepo: SampleRepository
    private lateinit var exportManager: ExportManager
    private lateinit var bleScanner: BleScanner
    private lateinit var bleManager: BleConnectionManager

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
        bleScanner = BleScanner(this)
        bleManager = BleConnectionManager.getInstance(this)

        checkAndRequestPermissions()

        setContent {
            BandCompanionTheme {
                MainAppContent(
                    deviceRepo = deviceRepo,
                    summaryRepo = summaryRepo,
                    sleepRepo = sleepRepo,
                    sampleRepo = sampleRepo,
                    exportManager = exportManager,
                    bleScanner = bleScanner,
                    bleManager = bleManager,
                    onSyncRequested = { triggerManualSync() },
                    onStartService = { startBandService() }
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
    DASHBOARD("Health", Icons.Default.Dashboard),
    SLEEP("Sleep", Icons.Default.Bedtime),
    LIVE("Exercise", Icons.Default.Favorite),
    TRENDS("Discover", Icons.Default.ShowChart),
    SETTINGS("Me", Icons.Default.Settings)
}

@SuppressLint("MissingPermission")
@Composable
fun MainAppContent(
    deviceRepo: DeviceRepository,
    summaryRepo: SummaryRepository,
    sleepRepo: SleepRepository,
    sampleRepo: SampleRepository,
    exportManager: ExportManager,
    bleScanner: BleScanner,
    bleManager: BleConnectionManager,
    onSyncRequested: () -> Unit,
    onStartService: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var isSyncing by remember { mutableStateOf(false) }
    var isPairingOpen by remember { mutableStateOf(false) }
    var pairingStatusText by remember { mutableStateOf<String?>(null) }
    var isWaitingForWatchCheckmark by remember { mutableStateOf(false) }
    var isAwaitingWatchFaceConfirmation by remember { mutableStateOf(false) }
    var userConfirmationDeferred by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var pendingPairedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var pendingAuthKeyHex by remember { mutableStateOf<String?>(null) }
    var pendingSecretKeyHex by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val discoveredBands by bleScanner.discoveredBands.collectAsState()
    val isScanning by bleScanner.isScanning.collectAsState()
    val bondState by bleManager.bondState.collectAsState()

    LaunchedEffect(bondState) {
        if (bondState == BluetoothDevice.BOND_BONDED && isAwaitingWatchFaceConfirmation) {
            Log.i("MainActivity", "Bluetooth bonded! Triggering watch face activation...")
            bleManager.protocolHandler.sendDismissPairingScreen()
        }
    }

    val primaryDevice by deviceRepo.getPrimaryDeviceFlow().collectAsState(initial = null)
    val devId = primaryDevice?.macAddress ?: "C4:16:88:8D:04:EF"

    val todaySummary by summaryRepo.getSummaryForDate(devId, "2026-10-12").collectAsState(initial = null)
    val recentSummaries by summaryRepo.getRecentSummaries(devId, 30).collectAsState(initial = emptyList())
    val latestSleepSession by sleepRepo.getLatestSleepSession(devId).collectAsState(initial = null)

    if (isPairingOpen) {
        PairingScreen(
            discoveredBands = discoveredBands,
            isScanning = isScanning,
            pairingStatusText = pairingStatusText,
            isWaitingForWatchCheckmark = isWaitingForWatchCheckmark,
            isAwaitingWatchFaceConfirmation = isAwaitingWatchFaceConfirmation,
            bondState = bondState,
            onStartScan = { bleScanner.startScan() },
            onConfirmCheckmark = { userConfirmationDeferred?.complete(Unit) },
            onFinishPairing = {
                coroutineScope.launch {
                    val dev = pendingPairedDevice
                    if (dev != null) {
                        deviceRepo.saveDevice(dev, pendingAuthKeyHex, pendingSecretKeyHex)
                        onStartService()
                        Toast.makeText(context, "🎉 Huawei Band paired successfully!", Toast.LENGTH_LONG).show()
                    }
                    isPairingOpen = false
                    isAwaitingWatchFaceConfirmation = false
                    pairingStatusText = null
                    pendingPairedDevice = null
                }
            },
            onResendActivation = {
                coroutineScope.launch {
                    Toast.makeText(context, "Resending watch face activation...", Toast.LENGTH_SHORT).show()
                    bleManager.protocolHandler.sendDismissPairingScreen()
                }
            },
            onSelectDevice = { selectedBand ->
                coroutineScope.launch {
                    bleScanner.stopScan()
                    isAwaitingWatchFaceConfirmation = false
                    pairingStatusText = "Connecting to ${selectedBand.name}..."
                    bleManager.connect(selectedBand.macAddress)

                    // Wait for services to be discovered and CCCD written
                    var waitCount = 0
                    while (bleManager.connectionState.value != BleConnectionManager.ConnectionState.SERVICES_DISCOVERED && waitCount < 30) {
                        delay(1000)
                        waitCount++
                        if (waitCount % 4 == 0) {
                            pairingStatusText = "Connecting & discovering services... (${waitCount}s)"
                        }
                    }

                    if (bleManager.connectionState.value != BleConnectionManager.ConnectionState.SERVICES_DISCOVERED) {
                        pairingStatusText = "Could not discover services. Ensure watch displays Disconnect screen."
                        return@launch
                    }

                    pairingStatusText = "Starting pairing handshake..."
                    val success = bleManager.protocolHandler.authenticateHandshake(
                        storedAuthKeyHex = null,
                        forceFirstAuth = true,
                        onStepUpdate = { status ->
                            pairingStatusText = status
                            if (status.contains("TAP CHECKMARK", ignoreCase = true)) {
                                isWaitingForWatchCheckmark = true
                            }
                        },
                        onUserPrompt = {
                            isWaitingForWatchCheckmark = true
                            pairingStatusText = "👉 TAP CHECKMARK (✓) ON YOUR WATCH!"
                            val deferred = CompletableDeferred<Unit>()
                            userConfirmationDeferred = deferred
                            withTimeoutOrNull(90000L) {
                                deferred.await()
                            }
                            isWaitingForWatchCheckmark = false
                            pairingStatusText = "Watch confirmed! Finalizing pairing..."
                        }
                    )

                    if (success) {
                        bleManager.protocolHandler.executePostAuthBootstrap { status ->
                            pairingStatusText = status
                        }
                        bleManager.initiateBond()
                        isWaitingForWatchCheckmark = false

                        val secretKeyHex = bleManager.protocolHandler.cachedSecretKey?.let { HiChain3Crypto.bytesToHex(it) }
                        val authKeyHex = bleManager.protocolHandler.cachedAuthKey?.let { HiChain3Crypto.bytesToHex(it) }

                        val newDev = DeviceInfo(
                            macAddress = selectedBand.macAddress,
                            name = selectedBand.name,
                            isBonded = true,
                            isConnected = true,
                            batteryLevel = bleManager.protocolHandler.batteryLevel.value ?: 92,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                        pendingPairedDevice = newDev
                        pendingAuthKeyHex = authKeyHex
                        pendingSecretKeyHex = secretKeyHex

                        // Keep PairingScreen open until the watch screen shows active watch face!
                        isAwaitingWatchFaceConfirmation = true
                        pairingStatusText = null
                    } else {
                        isWaitingForWatchCheckmark = false
                        isAwaitingWatchFaceConfirmation = false
                        pairingStatusText = "Pairing timed out. Please try again."
                    }
                }
            },
            onBackClick = {
                bleScanner.stopScan()
                isPairingOpen = false
                isAwaitingWatchFaceConfirmation = false
                pairingStatusText = null
            }
        )
        return
    }

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
                    deviceInfo = primaryDevice,
                    todaySummary = todaySummary,
                    isSyncing = isSyncing,
                    onSyncClick = {
                        isSyncing = true
                        onSyncRequested()
                    },
                    onPairClick = {
                        isPairingOpen = true
                        bleScanner.startScan()
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
                    deviceInfo = primaryDevice,
                    onExportCsvClick = {
                        coroutineScope.launch {
                            val now = System.currentTimeMillis() / 1000
                            val monthAgo = now - (30 * 86400)
                            val file = exportManager.exportSamplesToCsv(devId, monthAgo, now)
                            Toast.makeText(context, "CSV exported: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onExportJsonClick = {
                        coroutineScope.launch {
                            val file = exportManager.exportFullJson(devId)
                            Toast.makeText(context, "JSON exported: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}
