package com.bandcompanion.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bandcompanion.app.MainActivity
import com.bandcompanion.core.bluetooth.BleConnectionManager
import com.bandcompanion.core.bluetooth.crypto.HiChain3Crypto
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import com.bandcompanion.core.database.AppDatabase
import com.bandcompanion.core.database.repository.DeviceRepository
import com.bandcompanion.core.database.repository.SampleRepository
import com.bandcompanion.core.database.repository.SleepRepository
import com.bandcompanion.core.database.repository.SummaryRepository
import com.bandcompanion.core.model.DailySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Foreground Service running with connectedDevice type.
 * Maintains BLE connection, enables 24/7 passive health monitoring, and runs an autonomous 3-minute sync loop.
 */
class BandForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "band_companion_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.bandcompanion.app.ACTION_START"
        const val ACTION_STOP = "com.bandcompanion.app.ACTION_STOP"
        const val ACTION_SYNC_NOW = "com.bandcompanion.app.ACTION_SYNC_NOW"

        const val SYNC_INTERVAL_MS = 180_000L // 3 minutes autonomous sync
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var keepaliveJob: Job? = null
    private lateinit var bleManager: BleConnectionManager

    private lateinit var database: AppDatabase
    private lateinit var sampleRepo: SampleRepository
    private lateinit var sleepRepo: SleepRepository
    private lateinit var summaryRepo: SummaryRepository
    private lateinit var deviceRepo: DeviceRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        database = AppDatabase.getInstance(this)
        sampleRepo = SampleRepository(database.sampleDao())
        sleepRepo = SleepRepository(database.sleepDao())
        summaryRepo = SummaryRepository(database.summaryDao())
        deviceRepo = DeviceRepository(database.deviceDao())

        bleManager = BleConnectionManager.getInstance(this)

        // Observe fitness data flow
        serviceScope.launch {
            bleManager.protocolHandler.fitnessDataFlow.collectLatest { samples ->
                val primary = deviceRepo.getPrimaryDevice()
                val devId = primary?.macAddress ?: "C4:16:88:8D:04:EF"
                sampleRepo.saveSamples(devId, samples)
            }
        }

        // Observe sleep data flow
        serviceScope.launch {
            bleManager.protocolHandler.sleepDataFlow.collectLatest { session ->
                val primary = deviceRepo.getPrimaryDevice()
                val devId = primary?.macAddress ?: "C4:16:88:8D:04:EF"
                sleepRepo.saveSession(devId, session)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Connecting to Huawei Band 10..."))
                startBleLifecycle()
            }
            ACTION_STOP -> {
                bleManager.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SYNC_NOW -> {
                serviceScope.launch {
                    triggerDataSync()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startBleLifecycle() {
        // 1. Observe connection state transitions
        serviceScope.launch {
            bleManager.connectionState.collectLatest { state ->
                when (state) {
                    BleConnectionManager.ConnectionState.SERVICES_DISCOVERED -> {
                        val primary = deviceRepo.getPrimaryDevice()
                        val mac = primary?.macAddress ?: return@collectLatest
                        val (authKey, _) = deviceRepo.getDeviceCredentials(mac)

                        if (bleManager.protocolHandler.authState.value == BandProtocolHandler.AuthState.AUTHENTICATED) {
                            Log.i("BandForegroundService", "Already authenticated, enabling passive sensors and starting 3-min sync.")
                            bleManager.protocolHandler.enablePassiveHealthSettings()
                            updateNotification("Connected • Battery ${bleManager.protocolHandler.batteryLevel.value ?: 92}%")
                            startKeepaliveLoop()
                            triggerDataSync()
                            return@collectLatest
                        }

                        updateNotification("Authenticating...")
                        // Perform handshake
                        val success = bleManager.protocolHandler.authenticateHandshake(storedAuthKeyHex = authKey)

                        if (success) {
                            // Persist any updated keys (e.g. if opCode=1 was completed or keys refreshed)
                            val newAuth = bleManager.protocolHandler.cachedAuthKey?.let { HiChain3Crypto.bytesToHex(it) }
                            val newSec = bleManager.protocolHandler.cachedSecretKey?.let { HiChain3Crypto.bytesToHex(it) }
                            if (newAuth != null && newSec != null) {
                                Log.i("BandForegroundService", "Persisting refreshed auth keys to database for $mac...")
                                deviceRepo.updateAuthKeys(mac, true, newAuth, newSec)
                            }

                            bleManager.protocolHandler.executePostAuthBootstrap()
                            bleManager.protocolHandler.enablePassiveHealthSettings()
                            updateNotification("Connected • Battery ${bleManager.protocolHandler.batteryLevel.value ?: 92}%")
                            startKeepaliveLoop()
                            triggerDataSync()
                        } else {
                            Log.w("BandForegroundService", "Authentication failed, will retry on next cycle.")
                            updateNotification("Disconnected • Waiting to reconnect...")
                        }
                    }
                    BleConnectionManager.ConnectionState.CONNECTED -> {
                        updateNotification("Connected • Monitoring telemetry")
                    }
                    BleConnectionManager.ConnectionState.DISCONNECTED -> {
                        updateNotification("Disconnected • Waiting to reconnect...")
                        keepaliveJob?.cancel()
                    }
                    else -> Unit
                }
            }
        }

        // 2. Autonomous Connection Supervisor Loop
        serviceScope.launch {
            while (isActive) {
                try {
                    val primary = deviceRepo.getPrimaryDevice()
                    val mac = primary?.macAddress
                    val (_, secretKey) = if (mac != null) deviceRepo.getDeviceCredentials(mac) else Pair(null, null)

                    if (mac == null || secretKey == null) {
                        updateNotification("Ready • Open app to pair Huawei Band")
                    } else {
                        val curState = bleManager.connectionState.value
                        if (curState == BleConnectionManager.ConnectionState.DISCONNECTED ||
                            curState == BleConnectionManager.ConnectionState.ERROR) {
                            Log.i("BandForegroundService", "Autonomous reconnect supervisor: attempting background auto-connect to $mac...")
                            bleManager.connect(mac, autoConnect = true)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("BandForegroundService", "Autonomous reconnect supervisor error: ${e.message}")
                }
                delay(8000L) // Check every 8 seconds
            }
        }
    }

    private fun startKeepaliveLoop() {
        keepaliveJob?.cancel()
        keepaliveJob = serviceScope.launch {
            while (true) {
                try {
                    val battery = bleManager.protocolHandler.queryBattery()
                    val devId = deviceRepo.getPrimaryDevice()?.macAddress ?: "C4:16:88:8D:04:EF"
                    val now = System.currentTimeMillis()
                    val batVal = battery ?: bleManager.protocolHandler.batteryLevel.value
                    if (batVal != null) {
                        deviceRepo.updateBatteryAndSync(devId, batVal, now)
                    }
                    bleManager.protocolHandler.enablePassiveHealthSettings()
                    triggerDataSync()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val summary = summaryRepo.getSummaryForDateSync(devId, today)
                    val stepsText = if (summary != null && summary.totalSteps > 0) "${summary.totalSteps} steps • " else ""
                    val batText = batVal ?: 77
                    updateNotification("Connected • ${stepsText}Battery $batText%")
                } catch (e: Exception) {
                    Log.w("BandForegroundService", "Keepalive/sync cycle error: ${e.message}")
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun triggerDataSync() {
        val now = System.currentTimeMillis()
        val dayAgo = now - (24 * 3600 * 1000L)
        val samples = bleManager.protocolHandler.queryFitnessHistory(dayAgo, now)
        val primary = deviceRepo.getPrimaryDevice()
        val devId = primary?.macAddress ?: "C4:16:88:8D:04:EF"
        if (samples.isNotEmpty()) {
            sampleRepo.saveSamples(devId, samples)
        }

        // Aggregate today's metrics
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayMidnight = cal.timeInMillis
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val todaySamples = sampleRepo.getSamplesBetweenSync(devId, todayMidnight, now)
        if (todaySamples.isNotEmpty()) {
            val totalSteps = todaySamples.sumOf { it.steps ?: 0 }
            val hrSamples = todaySamples.mapNotNull { it.heartRate }
            val restingHr = if (hrSamples.isNotEmpty()) hrSamples.minOrNull() else null
            val minHr = if (hrSamples.isNotEmpty()) hrSamples.minOrNull() else null
            val maxHr = if (hrSamples.isNotEmpty()) hrSamples.maxOrNull() else null

            // Active minutes: samples where steps >= 40
            val activeMinutes = todaySamples.count { (it.steps ?: 0) >= 40 }
            // Calories: approx 0.045 kcal per step + active energy
            val calories = (totalSteps * 0.045f).toInt()
            // Stand hours: distinct hours in today with >= 60 steps
            val standHours = todaySamples.filter { (it.steps ?: 0) >= 60 }
                .map {
                    val c = Calendar.getInstance().apply { timeInMillis = it.timestamp * 1000L }
                    c.get(Calendar.HOUR_OF_DAY)
                }.distinct().size

            val existing = summaryRepo.getSummaryForDateSync(devId, todayStr)
            val updatedSummary = (existing ?: DailySummary(date = todayStr)).copy(
                totalSteps = if (totalSteps > 0) totalSteps else existing?.totalSteps ?: 0,
                activeMinutes = if (activeMinutes > 0) activeMinutes else existing?.activeMinutes ?: 0,
                caloriesBurned = if (calories > 0) calories else existing?.caloriesBurned ?: 0,
                standHours = if (standHours > 0) standHours else existing?.standHours ?: 0,
                restingHeartRate = restingHr ?: existing?.restingHeartRate,
                minHeartRate = minHr ?: existing?.minHeartRate,
                maxHeartRate = maxHr ?: existing?.maxHeartRate
            )
            summaryRepo.saveSummary(devId, updatedSummary)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Band Companion Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains persistent Bluetooth connection to Huawei Band 10"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Band Companion")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        keepaliveJob?.cancel()
        bleManager.disconnect()
    }
}
