package com.bandcompanion.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredBand(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

class BleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val _discoveredBands = MutableStateFlow<List<DiscoveredBand>>(emptyList())
    val discoveredBands: StateFlow<List<DiscoveredBand>> = _discoveredBands.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val rawName = device.name ?: result.scanRecord?.deviceName

            val isTargetMac = device.address.equals("C4:16:88:8D:04:EF", ignoreCase = true)
            val name = when {
                isTargetMac -> rawName ?: "HUAWEI Band 10-4EF"
                rawName != null && (rawName.contains("Band", ignoreCase = true) || rawName.contains("HUAWEI", ignoreCase = true)) -> rawName
                else -> return
            }

            val current = _discoveredBands.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.macAddress.equals(device.address, ignoreCase = true) }
            val item = DiscoveredBand(name = name, macAddress = device.address, rssi = result.rssi)

            if (existingIdx >= 0) {
                current[existingIdx] = item
            } else {
                current.add(item)
            }
            _discoveredBands.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (_isScanning.value) return

        _discoveredBands.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (!_isScanning.value) return

        _isScanning.value = false
        scanner.stopScan(scanCallback)
    }
}
