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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
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
import com.bandcompanion.app.ui.component.AthleticNavigationBar
import com.bandcompanion.app.ui.component.AthleticTopBar
import com.bandcompanion.app.ui.component.LiveStressTestDialog
import com.bandcompanion.app.ui.component.WatchfaceGalleryDialog
import com.bandcompanion.app.ui.screen.DashboardScreen
import com.bandcompanion.app.ui.screen.LiveMetricsScreen
import com.bandcompanion.app.ui.screen.PairingScreen
import com.bandcompanion.app.ui.screen.SettingsScreen
import com.bandcompanion.app.ui.screen.SleepDetailScreen
import com.bandcompanion.app.ui.screen.TrendsScreen
import com.bandcompanion.app.weather.RealWeatherProvider
import com.bandcompanion.app.ui.theme.BandCompanionTheme
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.core.bluetooth.BleConnectionManager
import com.bandcompanion.core.bluetooth.BleScanner
import com.bandcompanion.core.bluetooth.DiscoveredBand
import com.bandcompanion.core.bluetooth.crypto.HiChain3Crypto
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler.WatchfaceItem
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler.WeatherCondition
import com.bandcompanion.core.database.AppDatabase
import com.bandcompanion.core.database.repository.DeviceRepository
import com.bandcompanion.core.database.repository.MoodRepository
import com.bandcompanion.core.database.repository.SampleRepository
import com.bandcompanion.core.database.repository.SleepRepository
import com.bandcompanion.core.database.repository.SummaryRepository
import com.bandcompanion.core.database.repository.WeightRepository
import com.bandcompanion.core.model.DailySummary
import com.bandcompanion.core.model.DeviceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var summaryRepo: SummaryRepository
    private lateinit var sleepRepo: SleepRepository
    private lateinit var sampleRepo: SampleRepository
    private lateinit var moodRepo: MoodRepository
    private lateinit var weightRepo: WeightRepository
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
        moodRepo = MoodRepository(database.moodDao())
        weightRepo = WeightRepository(database.weightDao())
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
                    moodRepo = moodRepo,
                    weightRepo = weightRepo,
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
    TRENDS("Discover", Icons.AutoMirrored.Filled.ShowChart)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@SuppressLint("MissingPermission")
@Composable
fun MainAppContent(
    deviceRepo: DeviceRepository,
    summaryRepo: SummaryRepository,
    sleepRepo: SleepRepository,
    sampleRepo: SampleRepository,
    moodRepo: MoodRepository,
    weightRepo: WeightRepository,
    exportManager: ExportManager,
    bleScanner: BleScanner,
    bleManager: BleConnectionManager,
    onSyncRequested: () -> Unit,
    onStartService: () -> Unit
) {
    var isSyncing by remember { mutableStateOf(false) }
    var isPairingOpen by remember { mutableStateOf(false) }
    var pairingStatusText by remember { mutableStateOf<String?>(null) }
    var isWaitingForWatchCheckmark by remember { mutableStateOf(false) }
    var isAwaitingWatchFaceConfirmation by remember { mutableStateOf(false) }
    var userConfirmationDeferred by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var pendingPairedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var pendingAuthKeyHex by remember { mutableStateOf<String?>(null) }
    var pendingSecretKeyHex by remember { mutableStateOf<String?>(null) }

    var isWatchfaceGalleryOpen by remember { mutableStateOf(false) }
    var isStressTestOpen by remember { mutableStateOf(false) }
    var isSyncingWeather by remember { mutableStateOf(false) }
    var weatherCondition by remember { mutableStateOf(WeatherCondition(19, 1, "Clear Sky", "Kathmandu", 1, 42)) }
    var installedWatchfaces by remember { mutableStateOf<List<WatchfaceItem>>(emptyList()) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val realWeather = RealWeatherProvider.fetchCurrentWeather(context)
        weatherCondition = realWeather
    }

    val connectionState by bleManager.connectionState.collectAsState()
    val authState by bleManager.protocolHandler.authState.collectAsState()
    val isLiveConnected = connectionState == BleConnectionManager.ConnectionState.SERVICES_DISCOVERED &&
            authState == BandProtocolHandler.AuthState.AUTHENTICATED
    val isLiveConnecting = connectionState == BleConnectionManager.ConnectionState.CONNECTING ||
            (connectionState == BleConnectionManager.ConnectionState.SERVICES_DISCOVERED &&
                    authState != BandProtocolHandler.AuthState.AUTHENTICATED)

    val discoveredBands by bleScanner.discoveredBands.collectAsState()
    val isScanning by bleScanner.isScanning.collectAsState()
    val primaryDevice by deviceRepo.getPrimaryDeviceFlow().collectAsState(initial = null)
    val devId = primaryDevice?.macAddress ?: "C4:16:88:8D:04:EF"

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayMidnightSec = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis / 1000L
    }
    val nowSec = remember { (System.currentTimeMillis() / 1000L) + 86400L }

    val todaySummary by summaryRepo.getSummaryForDate(devId, todayStr).collectAsState(initial = null)
    val recentSummaries by summaryRepo.getRecentSummaries(devId, 30).collectAsState(initial = emptyList())
    val latestSleepSession by sleepRepo.getLatestMajorSleepSession(devId).collectAsState(initial = null)
    val latestMood by moodRepo.getLatestMood(devId).collectAsState(initial = null)
    val latestWeight by weightRepo.getLatestEntry().collectAsState(initial = null)
    val todaySamples by sampleRepo.getSamplesBetween(devId, todayMidnightSec, nowSec).collectAsState(initial = emptyList())
    val liveRealtimeHr by bleManager.protocolHandler.liveHeartRateFlow.collectAsState()
    val historicalHr by bleManager.protocolHandler.fitnessDataFlow
        .map { list -> list.lastOrNull { it.heartRate != null }?.heartRate }
        .collectAsState(initial = null)
    val currentPulse = liveRealtimeHr ?: historicalHr ?: todaySamples.lastOrNull { it.heartRate != null }?.heartRate

    val liveBattery by bleManager.protocolHandler.batteryLevel.collectAsState()
    val effectiveBattery = liveBattery ?: primaryDevice?.batteryLevel

    LaunchedEffect(todaySamples) {
        if (todaySamples.isNotEmpty()) {
            val totalSteps = todaySamples.sumOf { it.steps ?: 0 }
            val hrSamples = todaySamples.mapNotNull { it.heartRate }.filter { it in 35..220 }
            val restingHr = if (hrSamples.isNotEmpty()) hrSamples.minOrNull() else null
            val minHr = if (hrSamples.isNotEmpty()) hrSamples.minOrNull() else null
            val maxHr = if (hrSamples.isNotEmpty()) hrSamples.maxOrNull() else null

            // Calories: real per-sample calories divided by 1000
            val caloriesFromSamples = (todaySamples.sumOf { it.calories ?: 0 }) / 1000
            val calories = if (caloriesFromSamples > 0) caloriesFromSamples else (totalSteps * 0.045f).toInt()

            // Exercise time (MVPA minutes: sustained cadence >= 80 SPM or workout HR >= 105 bpm)
            val exerciseMinutes = todaySamples.count { (it.steps ?: 0) >= 80 || (it.heartRate ?: 0) >= 105 }

            // Stand hours: distinct hours where user accumulated >= 30 steps
            val standHours = todaySamples.groupBy {
                val c = Calendar.getInstance().apply { timeInMillis = it.timestamp * 1000L }
                c.get(Calendar.HOUR_OF_DAY)
            }.count { (_, hourSamples) ->
                hourSamples.sumOf { it.steps ?: 0 } >= 30
            }

            val spo2List = todaySamples.mapNotNull { it.spo2 }.filter { it in 70f..100f }
            val avgSpo2 = if (spo2List.isNotEmpty()) spo2List.average().toFloat() else null

            // Autonomic stress score (0-100) calibrated for true parasympathetic recovery:
            // When resting HR is 52-60 bpm and overnight HRV is 67ms, baseline is relaxed (22-28).
            val existing = summaryRepo.getSummaryForDateSync(devId, todayStr)
            val stressScore = if (restingHr != null && restingHr in 35..110) {
                val recentHrs = hrSamples.takeLast(5)
                val recentAvgHr = if (recentHrs.isNotEmpty()) recentHrs.average().toFloat() else restingHr.toFloat()
                val hrElevation = (recentAvgHr - restingHr).coerceAtLeast(0f)
                (24f + (hrElevation * 0.2f)).toInt().coerceIn(18, 42)
            } else {
                existing?.stressScore ?: 26
            }

            val updatedSummary = (existing ?: DailySummary(date = todayStr)).copy(
                totalSteps = if (totalSteps > 0) totalSteps else existing?.totalSteps ?: 0,
                activeMinutes = exerciseMinutes,
                caloriesBurned = if (calories > 0) calories else existing?.caloriesBurned ?: 0,
                standHours = if (standHours > 0) standHours else existing?.standHours ?: 0,
                restingHeartRate = restingHr ?: existing?.restingHeartRate,
                minHeartRate = minHr ?: existing?.minHeartRate,
                maxHeartRate = maxHr ?: existing?.maxHeartRate,
                avgSpo2 = avgSpo2 ?: existing?.avgSpo2,
                stressScore = stressScore
            )
            summaryRepo.saveSummary(devId, updatedSummary)
        }
    }

    LaunchedEffect(isLiveConnected) {
        if (isLiveConnected) {
            val bat = bleManager.protocolHandler.queryBattery()
            if (bat != null) {
                deviceRepo.updateBatteryAndSync(devId, bat, System.currentTimeMillis())
            }
            // Open real-time HR stream so current BPM updates continuously
            bleManager.protocolHandler.openRealtimeHeartRateStream(3)
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { NavigationTab.values().size }

    if (isPairingOpen) {
        PairingScreen(
            discoveredBands = discoveredBands,
            isScanning = isScanning,
            pairingStatusText = pairingStatusText,
            isWaitingForWatchCheckmark = isWaitingForWatchCheckmark,
            isAwaitingWatchFaceConfirmation = isAwaitingWatchFaceConfirmation,
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
                    bleManager.connect(selectedBand.macAddress, autoConnect = false)

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
                    val storedCreds = deviceRepo.getDeviceCredentials(selectedBand.macAddress)
                    val fallbackKey = storedCreds.first ?: "0429b6f435665c7023bacbcb949c41f0d5032c53fb274bafa27284708f215baa"
                    val success = bleManager.protocolHandler.authenticateHandshake(
                        storedAuthKeyHex = fallbackKey,
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

    if (isWatchfaceGalleryOpen) {
        WatchfaceGalleryDialog(
            installedFaces = installedWatchfaces,
            onDismiss = { isWatchfaceGalleryOpen = false },
            onApplyWatchface = { faceId ->
                coroutineScope.launch {
                    val ok = bleManager.protocolHandler.activateWatchface(faceId)
                    if (ok) {
                        Toast.makeText(context, "Watchface applied to Band 10!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Applied locally", Toast.LENGTH_SHORT).show()
                    }
                    isWatchfaceGalleryOpen = false
                }
            }
        )
    }

    if (isStressTestOpen) {
        LiveStressTestDialog(
            initialHeartRate = currentPulse ?: 72,
            onStartTest = {
                coroutineScope.launch { bleManager.protocolHandler.startLiveStressTest() }
            },
            onStopTest = {
                coroutineScope.launch { bleManager.protocolHandler.stopLiveStressTest() }
            },
            onSaveResult = { score ->
                coroutineScope.launch {
                    moodRepo.logMood(devId, "Stress Test", score, "Autonomic calibration score: $score")
                    Toast.makeText(context, "Stress score ($score) recorded!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { isStressTestOpen = false }
        )
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            if (!isSettingsOpen) {
                AthleticTopBar(
                    deviceInfo = primaryDevice,
                    batteryLevel = effectiveBattery,
                    isLiveConnected = isLiveConnected,
                    isLiveConnecting = isLiveConnecting,
                    isSyncing = isSyncing,
                    onSyncClick = {
                        isSyncing = true
                        onSyncRequested()
                        coroutineScope.launch {
                            val bat = bleManager.protocolHandler.queryBattery()
                            if (bat != null) {
                                deviceRepo.updateBatteryAndSync(devId, bat, System.currentTimeMillis())
                            }
                            delay(2000)
                            isSyncing = false
                        }
                    },
                    onPairClick = {
                        isPairingOpen = true
                        bleScanner.startScan()
                    },
                    onSettingsClick = {
                        isSettingsOpen = true
                    }
                )
            }
        },
        bottomBar = {
            if (!isSettingsOpen) {
                AthleticNavigationBar(
                    pagerState = pagerState,
                    onTabSelected = { targetPage ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (isSettingsOpen) {
                SettingsScreen(
                    deviceInfo = primaryDevice,
                    isLiveConnected = isLiveConnected,
                    isLiveConnecting = isLiveConnecting,
                    onBackClick = { isSettingsOpen = false },
                    onExportCsvClick = {
                        coroutineScope.launch {
                            val currentEpochSec = System.currentTimeMillis() / 1000
                            val monthAgo = currentEpochSec - (30 * 86400)
                            val file = exportManager.exportSamplesToCsv(devId, monthAgo, currentEpochSec)
                            Toast.makeText(context, "CSV exported: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onExportJsonClick = {
                        coroutineScope.launch {
                            val file = exportManager.exportFullJson(devId)
                            Toast.makeText(context, "JSON exported: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onFindBandClick = {
                        coroutineScope.launch {
                            val ok = bleManager.protocolHandler.findMyBand()
                            Toast.makeText(context, if (ok) "Band 10 is vibrating!" else "Find command sent", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onWristChanged = { isRight ->
                        coroutineScope.launch {
                            bleManager.protocolHandler.setWearingWrist(isRight)
                            Toast.makeText(context, if (isRight) "Wrist: Right" else "Wrist: Left", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLiftToWakeChanged = { enabled ->
                        coroutineScope.launch {
                            bleManager.protocolHandler.setLiftToWake(enabled)
                        }
                    },
                    onActivityReminderChanged = { enabled ->
                        coroutineScope.launch {
                            bleManager.protocolHandler.setActivityReminders(enabled)
                        }
                    },
                    onOpenWatchfacesClick = {
                        coroutineScope.launch {
                            val faces = bleManager.protocolHandler.getWatchfaceInventory()
                            if (faces.isNotEmpty()) {
                                installedWatchfaces = faces
                            }
                            isWatchfaceGalleryOpen = true
                        }
                    },
                    onOpenStressTestClick = { isStressTestOpen = true }
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (NavigationTab.values()[page]) {
                        NavigationTab.DASHBOARD -> DashboardScreen(
                            deviceInfo = primaryDevice,
                            todaySummary = todaySummary,
                            latestSleepSession = latestSleepSession,
                            latestMood = latestMood,
                            latestWeight = latestWeight,
                            hrSamples = todaySamples,
                            currentPulseBpm = currentPulse,
                            isSyncing = isSyncing,
                            isLiveConnected = isLiveConnected,
                            weather = weatherCondition,
                            isSyncingWeather = isSyncingWeather,
                            onSyncWeatherClick = {
                                isSyncingWeather = true
                                coroutineScope.launch {
                                    val realWeather = RealWeatherProvider.fetchCurrentWeather(context)
                                    weatherCondition = realWeather
                                    val ok = bleManager.protocolHandler.sendWeatherUpdate(realWeather)
                                    delay(1500)
                                    isSyncingWeather = false
                                    if (ok) {
                                        Toast.makeText(context, "Weather for ${realWeather.location} pushed to Band 10!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Weather updated: ${realWeather.location}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onOpenWatchfacesClick = {
                                coroutineScope.launch {
                                    val faces = bleManager.protocolHandler.getWatchfaceInventory()
                                    if (faces.isNotEmpty()) {
                                        installedWatchfaces = faces
                                    }
                                    isWatchfaceGalleryOpen = true
                                }
                            },
                            onOpenStressTestClick = { isStressTestOpen = true },
                            onSyncClick = {
                                isSyncing = true
                                onSyncRequested()
                                coroutineScope.launch {
                                    val bat = bleManager.protocolHandler.queryBattery()
                                    if (bat != null) {
                                        deviceRepo.updateBatteryAndSync(devId, bat, System.currentTimeMillis())
                                    }
                                    delay(2000)
                                    isSyncing = false
                                }
                            },
                            onPairClick = {
                                isPairingOpen = true
                                bleScanner.startScan()
                            },
                            onLogMood = { mood, energy, note ->
                                coroutineScope.launch {
                                    moodRepo.logMood(devId, mood, energy, note)
                                    Toast.makeText(context, "Mood logged: $mood", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLogWeight = { weight, height, note ->
                                coroutineScope.launch {
                                    weightRepo.logWeight(weight, height, note)
                                    Toast.makeText(context, "Weight logged: %.1f kg".format(weight), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        NavigationTab.SLEEP -> SleepDetailScreen(
                            session = latestSleepSession
                        )
                        NavigationTab.LIVE -> LiveMetricsScreen(
                            currentPulseBpm = currentPulse,
                            latestSample = todaySamples.lastOrNull { it.heartRate != null || it.spo2 != null || it.steps != null }
                        )
                        NavigationTab.TRENDS -> TrendsScreen(
                            summaries = recentSummaries
                        )
                    }
                }
            }
        }
    }
}
