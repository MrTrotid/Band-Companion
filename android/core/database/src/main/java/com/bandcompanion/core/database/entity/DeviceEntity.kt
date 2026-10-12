package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bandcompanion.core.model.DeviceInfo

/**
 * Persisted wearable device state, authentication keys, and connection attributes.
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String, // MAC Address, e.g. "C4:16:88:8D:04:EF"
    @ColumnInfo(name = "device_name")
    val deviceName: String = "HUAWEI Band 10",
    @ColumnInfo(name = "firmware_version")
    val firmwareVersion: String? = null,
    @ColumnInfo(name = "battery_level")
    val batteryLevel: Int? = null,
    @ColumnInfo(name = "last_sync_timestamp")
    val lastSyncTimestamp: Long = 0L,
    @ColumnInfo(name = "auth_key")
    val authKey: String? = null,
    @ColumnInfo(name = "secret_key")
    val secretKey: String? = null,
    @ColumnInfo(name = "is_bonded")
    val isBonded: Boolean = false
) {
    fun toDomainModel(isConnected: Boolean = false): DeviceInfo = DeviceInfo(
        macAddress = deviceId,
        name = deviceName,
        firmwareVersion = firmwareVersion,
        batteryLevel = batteryLevel,
        lastSyncTimestamp = lastSyncTimestamp,
        isConnected = isConnected,
        isBonded = isBonded
    )

    companion object {
        fun fromDomainModel(model: DeviceInfo, authKey: String? = null, secretKey: String? = null): DeviceEntity =
            DeviceEntity(
                deviceId = model.macAddress,
                deviceName = model.name,
                firmwareVersion = model.firmwareVersion,
                batteryLevel = model.batteryLevel,
                lastSyncTimestamp = model.lastSyncTimestamp,
                authKey = authKey,
                secretKey = secretKey,
                isBonded = model.isBonded
            )
    }
}
