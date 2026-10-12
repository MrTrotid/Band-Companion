package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bandcompanion.core.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE device_id = :deviceId LIMIT 1")
    fun getDevice(deviceId: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE device_id = :deviceId LIMIT 1")
    suspend fun getDeviceSync(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY last_sync_timestamp DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY last_sync_timestamp DESC LIMIT 1")
    suspend fun getPrimaryDevice(): DeviceEntity?

    @Query("UPDATE devices SET battery_level = :battery, last_sync_timestamp = :timestamp WHERE device_id = :deviceId")
    suspend fun updateBatteryAndSyncTime(deviceId: String, battery: Int, timestamp: Long)

    @Query("UPDATE devices SET is_bonded = :isBonded, auth_key = :authKey, secret_key = :secretKey WHERE device_id = :deviceId")
    suspend fun updateAuthKeys(deviceId: String, isBonded: Boolean, authKey: String?, secretKey: String?)

    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    suspend fun deleteDevice(deviceId: String)
}
