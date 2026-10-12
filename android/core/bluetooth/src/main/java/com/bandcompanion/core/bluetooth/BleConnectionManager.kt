package com.bandcompanion.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages physical Bluetooth Low Energy GATT connection, service discovery,
 * characteristic notifications, and thread-safe write dispatch.
 */
class BleConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        val CHAR_WRITE_UUID: UUID = UUID.fromString("0000fe01-0000-1000-8000-00805f9b34fb")
        val CHAR_NOTIFY_UUID: UUID = UUID.fromString("0000fe02-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        @Volatile
        private var instance: BleConnectionManager? = null

        fun getInstance(context: Context): BleConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: BleConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        SERVICES_DISCOVERED,
        AUTHENTICATING,
        CONNECTED,
        ERROR
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val writeChannel = Channel<ByteArray>(Channel.BUFFERED)
    private var isWriting = false

    lateinit var protocolHandler: BandProtocolHandler
        private set

    init {
        protocolHandler = BandProtocolHandler(
            sendRawBytes = { bytes -> sendBytes(bytes) },
            scope = scope
        )
    }

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)

        _connectionState.value = ConnectionState.CONNECTING

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    suspend fun sendBytes(bytes: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = writeChar ?: return false

        char.value = bytes
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        for (attempt in 1..5) {
            val success = gatt.writeCharacteristic(char)
            if (success) return true
            delay(60L) // Wait 60ms and retry if Android BLE buffer was busy
        }
        return false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.ERROR
                return
            }

            for (service in gatt.services) {
                val w = service.getCharacteristic(CHAR_WRITE_UUID)
                val n = service.getCharacteristic(CHAR_NOTIFY_UUID)
                if (w != null && n != null) {
                    writeChar = w
                    notifyChar = n
                    break
                }
            }

            val n = notifyChar
            if (n != null) {
                gatt.setCharacteristicNotification(n, true)
                val descriptor = n.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    // Note: SERVICES_DISCOVERED will be signaled in onDescriptorWrite once the band acknowledges CCCD!
                } else {
                    _connectionState.value = ConnectionState.SERVICES_DISCOVERED
                }
            } else {
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.SERVICES_DISCOVERED
            } else {
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                protocolHandler.onRawBytesReceived(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                characteristic.value?.let { protocolHandler.onRawBytesReceived(it) }
            }
        }
    }
}
