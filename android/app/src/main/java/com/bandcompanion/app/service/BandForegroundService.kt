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
import androidx.core.app.NotificationCompat
import com.bandcompanion.app.MainActivity
import com.bandcompanion.core.bluetooth.BleConnectionManager
import com.bandcompanion.core.database.AppDatabase
import com.bandcompanion.core.database.repository.DeviceRepository
import com.bandcompanion.core.database.repository.SampleRepository
import com.bandcompanion.core.database.repository.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service running with connectedDevice type.
 * Maintains BLE connection and battery keepalive loop without being killed by Android OS.
 */
class BandForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "band_companion_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.bandcompanion.app.ACTION_START"
        const val ACTION_STOP = "com.bandcompanion.app.ACTION_STOP"
        const val ACTION_SYNC_NOW = "com.bandcompanion.app.ACTION_SYNC_NOW"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var keepaliveJob: Job? = null
    private lateinit var bleManager: BleConnectionManager

    private lateinit var database: AppDatabase
    private lateinit var sampleRepo: SampleRepository
    private lateinit var sleepRepo: SleepRepository
    private lateinit var deviceRepo: DeviceRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        database = AppDatabase.getInstance(this)
        sampleRepo = SampleRepository(database.sampleDao())
        sleepRepo = SleepRepository(database.sleepDao())
        deviceRepo = DeviceRepository(database.deviceDao())

        bleManager = BleConnectionManager(this, serviceScope)

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
        serviceScope.launch {
            val primary = deviceRepo.getPrimaryDevice()
            val mac = primary?.macAddress ?: "C4:16:88:8D:04:EF"
            bleManager.connect(mac)

            bleManager.connectionState.collectLatest { state ->
                when (state) {
                    BleConnectionManager.ConnectionState.SERVICES_DISCOVERED -> {
                        updateNotification("Authenticating...")
                        // Perform handshake
                        val (authKey, secretKey) = deviceRepo.getDeviceCredentials(mac)
                        val success = if (secretKey != null) {
                            bleManager.protocolHandler.authenticateReconnect(secretKey)
                        } else {
                            bleManager.protocolHandler.exchangeLinkParams()
                        }

                        if (success) {
                            bleManager.protocolHandler.executePostAuthBootstrap()
                            updateNotification("Connected • Battery ${bleManager.protocolHandler.batteryLevel.value ?: 92}%")
                            startKeepaliveLoop()
                            triggerDataSync()
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
    }

    private fun startKeepaliveLoop() {
        keepaliveJob?.cancel()
        keepaliveJob = serviceScope.launch {
            while (true) {
                delay(35000L) // 35 seconds keepalive interval
                val battery = bleManager.protocolHandler.queryBattery()
                if (battery != null) {
                    updateNotification("Connected • Battery $battery%")
                }
            }
        }
    }

    private suspend fun triggerDataSync() {
        val now = System.currentTimeMillis()
        val dayAgo = now - (24 * 3600 * 1000L)
        bleManager.protocolHandler.queryFitnessHistory(dayAgo, now)
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
