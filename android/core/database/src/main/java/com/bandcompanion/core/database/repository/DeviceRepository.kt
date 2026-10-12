package com.bandcompanion.core.database.repository

import com.bandcompanion.core.database.dao.DeviceDao
import com.bandcompanion.core.database.entity.DeviceEntity
import com.bandcompanion.core.model.DeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceRepository(private val deviceDao: DeviceDao) {
    fun getPrimaryDeviceFlow(): Flow<DeviceInfo?> {
        return deviceDao.getAllDevices().map { devices ->
            devices.firstOrNull()?.toDomainModel()
        }
    }

    suspend fun getPrimaryDevice(): DeviceInfo? {
        return deviceDao.getPrimaryDevice()?.toDomainModel()
    }

    suspend fun getDeviceCredentials(deviceId: String): Pair<String?, String?> {
        val entity = deviceDao.getDeviceSync(deviceId)
        return Pair(entity?.authKey, entity?.secretKey)
    }

    suspend fun saveDevice(device: DeviceInfo, authKey: String? = null, secretKey: String? = null) {
        val entity = DeviceEntity.fromDomainModel(device, authKey, secretKey)
        deviceDao.insertOrUpdateDevice(entity)
    }

    suspend fun updateBatteryAndSync(deviceId: String, batteryLevel: Int, timestamp: Long) {
        deviceDao.updateBatteryAndSyncTime(deviceId, batteryLevel, timestamp)
    }

    suspend fun updateAuthKeys(deviceId: String, isBonded: Boolean, authKey: String?, secretKey: String?) {
        deviceDao.updateAuthKeys(deviceId, isBonded, authKey, secretKey)
    }
}
