package com.bandcompanion.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.bandcompanion.core.bluetooth.crypto.HiChain3Crypto
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private const val TAG = "BleConnection"
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

    lateinit var protocolHandler: BandProtocolHandler
        private set

    init {
        protocolHandler = BandProtocolHandler(
            sendRawBytes = { bytes -> sendBytes(bytes) },
            scope = scope
        )
    }

    private var isPairingReceiverRegistered = false
    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val currentMac = bluetoothGatt?.device?.address
                if (dev != null && currentMac != null && dev.address.equals(currentMac, ignoreCase = true)) {
                    Log.i(TAG, "ACTION_PAIRING_REQUEST intercepted for ${dev.address}: auto-confirming pairing")
                    try {
                        dev.setPairingConfirmation(true)
                        abortBroadcast()
                    } catch (e: Exception) {
                        Log.w(TAG, "setPairingConfirmation failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun registerPairingReceiver() {
        if (!isPairingReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(pairingReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(pairingReceiver, filter)
            }
            isPairingReceiverRegistered = true
            Log.i(TAG, "registerPairingReceiver: registered high-priority pairing request receiver")
        }
    }

    fun unregisterPairingReceiver() {
        if (isPairingReceiverRegistered) {
            try {
                context.unregisterReceiver(pairingReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterPairingReceiver error: ${e.message}")
            }
            isPairingReceiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    fun initiateBond(): Boolean {
        val dev = bluetoothGatt?.device ?: return false
        val state = dev.bondState
        Log.i(TAG, "initiateBond: device=${dev.address}, bondState=$state")
        return if (state == BluetoothDevice.BOND_NONE) {
            dev.createBond()
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun isDeviceBonded(): Boolean {
        val dev = bluetoothGatt?.device ?: return false
        return dev.bondState == BluetoothDevice.BOND_BONDED
    }

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        if (bluetoothGatt != null && (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.SERVICES_DISCOVERED)) {
            Log.i(TAG, "connect: Already connected to $macAddress with active GATT, reusing connection.")
            return
        }

        registerPairingReceiver()

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Log.e(TAG, "BluetoothAdapter is null!")
            return
        }
        val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)

        Log.i(TAG, "Connecting to GATT at $macAddress...")
        _connectionState.value = ConnectionState.CONNECTING

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.i(TAG, "Disconnecting GATT...")
        unregisterPairingReceiver()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    suspend fun sendBytes(bytes: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: run {
            Log.e(TAG, "sendBytes: bluetoothGatt is null!")
            return false
        }
        val char = writeChar ?: run {
            Log.e(TAG, "sendBytes: writeChar is null!")
            return false
        }

        val hexPreview = if (bytes.size <= 32) HiChain3Crypto.bytesToHex(bytes) else HiChain3Crypto.bytesToHex(bytes.copyOf(32)) + "..."
        Log.i(TAG, "sendBytes: writing ${bytes.size} bytes: $hexPreview")

        for (attempt in 1..5) {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                res == BluetoothGatt.GATT_SUCCESS
            } else {
                char.value = bytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(char)
            }

            if (success) {
                Log.d(TAG, "sendBytes: writeCharacteristic succeeded on attempt $attempt")
                return true
            }
            Log.w(TAG, "sendBytes: writeCharacteristic failed on attempt $attempt, retrying in 80ms...")
            delay(80L)
        }
        Log.e(TAG, "sendBytes: all 5 attempts failed!")
        return false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT Connected! Requesting MTU 512...")
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnected.")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "onMtuChanged: mtu=$mtu, status=$status. Discovering services...")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i(TAG, "onServicesDiscovered: status=$status (GATT_SUCCESS=${BluetoothGatt.GATT_SUCCESS})")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.ERROR
                return
            }

            for (service in gatt.services) {
                val w = service.getCharacteristic(CHAR_WRITE_UUID)
                val n = service.getCharacteristic(CHAR_NOTIFY_UUID)
                if (w != null) writeChar = w
                if (n != null) notifyChar = n
            }

            Log.i(TAG, "Characteristics resolved: writeChar=${writeChar?.uuid}, notifyChar=${notifyChar?.uuid}")

            val n = notifyChar
            if (n != null) {
                val notifSet = gatt.setCharacteristicNotification(n, true)
                Log.i(TAG, "setCharacteristicNotification returned $notifSet")

                val descriptor = n.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    val descSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val res = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        res == BluetoothGatt.GATT_SUCCESS
                    } else {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.i(TAG, "writeDescriptor for CCCD returned $descSuccess")
                } else {
                    Log.w(TAG, "CCCD descriptor was null! Setting SERVICES_DISCOVERED immediately.")
                    _connectionState.value = ConnectionState.SERVICES_DISCOVERED
                }
            } else {
                Log.e(TAG, "notifyChar was not found in GATT services!")
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.i(TAG, "onDescriptorWrite: status=$status, desc=${descriptor.uuid}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Notifications enabled on band! State -> SERVICES_DISCOVERED")
                _connectionState.value = ConnectionState.SERVICES_DISCOVERED
            } else {
                Log.e(TAG, "onDescriptorWrite failed with status=$status")
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                val hexPreview = if (value.size <= 24) HiChain3Crypto.bytesToHex(value) else HiChain3Crypto.bytesToHex(value.copyOf(24)) + "..."
                Log.d(TAG, "onCharacteristicChanged (API 33+): received ${value.size} bytes: $hexPreview")
                protocolHandler.onRawBytesReceived(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // On API 33+, the overload onCharacteristicChanged(gatt, characteristic, value) handles notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return
            }
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                val value = characteristic.value ?: return
                val hexPreview = if (value.size <= 24) HiChain3Crypto.bytesToHex(value) else HiChain3Crypto.bytesToHex(value.copyOf(24)) + "..."
                Log.d(TAG, "onCharacteristicChanged (legacy): received ${value.size} bytes: $hexPreview")
                protocolHandler.onRawBytesReceived(value)
            }
        }
    }
}
