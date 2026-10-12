package com.bandcompanion.core.model

/**
 * Connected wearable device metadata and connection status.
 */
data class DeviceInfo(
    val macAddress: String,
    val name: String = "HUAWEI Band 10",
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null,
    val lastSyncTimestamp: Long = 0L,
    val isConnected: Boolean = false,
    val isBonded: Boolean = false
)
