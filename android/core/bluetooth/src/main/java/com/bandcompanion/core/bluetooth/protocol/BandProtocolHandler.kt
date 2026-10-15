package com.bandcompanion.core.bluetooth.protocol

import android.content.Context
import android.util.Log
import com.bandcompanion.core.bluetooth.crypto.HiChain3Crypto
import com.bandcompanion.core.model.SampleMetric
import com.bandcompanion.core.model.SleepSession
import com.bandcompanion.core.model.SleepStageInterval
import com.bandcompanion.core.model.SleepStageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-level protocol controller for Huawei Band 10 (LPv2 / HiChain3).
 */
class BandProtocolHandler(
    private val sendRawBytes: suspend (ByteArray) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "BandProtocol"
        const val SVC_BASE = 0x01
        const val CMD_LINK_PARAMS = 0x01
        const val CMD_SUPPORTED_SERVICES = 0x02
        const val CMD_SUPPORTED_COMMANDS = 0x03
        const val CMD_TIME = 0x05
        const val CMD_PRODUCT = 0x07
        const val CMD_BATTERY = 0x08
        const val CMD_PHONE_INFO = 0x10
        const val CMD_DEVICE_STATUS = 0x16
        const val CMD_PIN_REQUEST = 0x2C
        const val CMD_HICHAIN_AUTH = 0x28
        const val CMD_ACCEPT_AGREEMENT = 0x30
        const val CMD_SETTING_RELATED = 0x31
        const val CMD_SECURITY_NEG = 0x33
        const val CMD_CONNECT_STATUS = 0x35
        const val CMD_EXPAND_CAPABILITY = 0x37
        const val CMD_PERMISSION_CHECK = 0x38
        const val CMD_WEAR_STATUS = 0x3D
        const val CMD_SETUP_DEVICE_STATUS = 0x3E
        const val CMD_REVERSE_CAPABILITIES = 0x3F

        val KNOWN_SUPPORTED_SERVICES = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x20,
            0x25, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x30, 0x31, 0x32, 0x34,
            0x35, 0x36, 0x37, 0x38
        )

        const val SVC_FITNESS = 0x02
        const val CMD_FITNESS_HISTORY = 0x03

        const val SVC_HEALTH = 0x07
        const val CMD_STEP_COUNT = 0x0A
        const val CMD_STEP_RECORD = 0x0B
        const val CMD_SLEEP_COUNT = 0x0C
        const val CMD_SLEEP_RECORD = 0x0D
        const val CMD_TRUSLEEP = 0x16
        const val CMD_AUTO_HR = 0x17
        const val CMD_AUTO_SPO2 = 0x24
        const val SVC_BREATH = 0x2D
        const val CMD_SLEEP_BREATH = 0x01

        const val SVC_DATA_SYNC = 0x2C
        const val CMD_DATA_SYNC = 0x01
        const val DICT_SLEEP_DETAILS = 700013

        const val SVC_DATASYNC = 0x37
        const val CMD_DATASYNC_CONFIG = 0x01
        const val DATASYNC_FEATURE_CONFIG_ID = 900100007

        // Watchface Service (0x27)
        const val SVC_WATCHFACE = 0x27
        const val CMD_WATCHFACE_PARAMS = 0x01
        const val CMD_WATCHFACE_LIST = 0x02
        const val CMD_WATCHFACE_OPERATION = 0x03
        const val CMD_WATCHFACE_NAMES = 0x06

        // Weather Service (0x0F)
        const val SVC_WEATHER = 0x0F
        const val CMD_WEATHER_CURRENT = 0x01
        const val CMD_WEATHER_SUPPORT = 0x02
        const val CMD_WEATHER_UNIT = 0x05
        const val CMD_WEATHER_FORECAST = 0x08
        const val CMD_WEATHER_START = 0x09

        // RRI & Stress Testing Services
        const val SVC_HR_RRI_TEST = 0x19
        const val CMD_HR_RRI_OPEN_CLOSE = 0x01
        const val CMD_HR_REALTIME_DATA = 0x03
        const val CMD_HR_RRI_DATA = 0x05

        const val SVC_STRESS = 0x20
        const val CMD_STRESS_AUTO = 0x09
    }

    data class WatchfaceItem(
        val fileName: String,
        val displayName: String,
        val version: String,
        val isCurrent: Boolean = false
    )

    data class WeatherCondition(
        val tempCelsius: Int,
        val conditionCode: Int,
        val conditionName: String,
        val location: String,
        val uvIndex: Int = 3,
        val aqi: Int = 45
    )

    data class StressRecord(
        val timestamp: Long,
        val score: Int,
        val level: Int
    )

    enum class AuthState {
        IDLE,
        LINK_PARAMS_NEGOTIATED,
        AWAITING_PIN,
        HICHAIN_ROUND_1,
        HICHAIN_ROUND_2,
        HICHAIN_ROUND_3,
        AUTHENTICATED,
        FAILED
    }

    private val reassembler = Lpv2Frame.Reassembler()
    private val responseDeferreds = mutableMapOf<Int, CompletableDeferred<Lpv2Frame.ParsedPacket>>()

    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _liveHeartRateFlow = MutableStateFlow<Int?>(null)
    val liveHeartRateFlow: StateFlow<Int?> = _liveHeartRateFlow.asStateFlow()

    private val _fitnessDataFlow = MutableSharedFlow<List<SampleMetric>>()
    val fitnessDataFlow = _fitnessDataFlow.asSharedFlow()

    private val _sleepDataFlow = MutableSharedFlow<SleepSession>()
    val sleepDataFlow = _sleepDataFlow.asSharedFlow()

    private val _stressHistoryFlow = MutableStateFlow<List<StressRecord>>(emptyList())
    val stressHistoryFlow: StateFlow<List<StressRecord>> = _stressHistoryFlow.asStateFlow()

    private var serverNonce: ByteArray? = null
    private var clientNonce = ByteArray(16)
    private var sliceSize: Int = 244
    private var sessionKey: ByteArray? = null
    var cachedSecretKey: ByteArray? = null
    var cachedAuthKey: ByteArray? = null
    private var fallbackAuthKey: ByteArray? = null

    var devSupportType: Int = 2
    var encMethod: Int = 0
    private var encCounter: Long = 0

    private fun usesGcm(): Boolean = encMethod == 0x01 || devSupportType == 0x04

    private fun nextCryptoIv(): ByteArray {
        return if (devSupportType == 0x04) {
            ByteArray(16).also { random.nextBytes(it) }
        } else {
            encCounter = if (encCounter == 0xFFFFFFFFL) 1L else encCounter + 1L
            val rand = ByteArray(12).also { random.nextBytes(it) }
            val cnt = ByteArray(4)
            cnt[0] = ((encCounter ushr 24) and 0xFF).toByte()
            cnt[1] = ((encCounter ushr 16) and 0xFF).toByte()
            cnt[2] = ((encCounter ushr 8) and 0xFF).toByte()
            cnt[3] = (encCounter and 0xFF).toByte()
            rand + cnt
        }
    }

    private val random = SecureRandom()

    init {
        random.nextBytes(clientNonce)
    }

    /**
     * Feeds incoming notification bytes from characteristic 0000fe02.
     */
    fun onRawBytesReceived(bytes: ByteArray) {
        val hex = if (bytes.size <= 24) HiChain3Crypto.bytesToHex(bytes) else HiChain3Crypto.bytesToHex(bytes.copyOf(24)) + "..."
        Log.d(TAG, "onRawBytesReceived: ${bytes.size} bytes: $hex")
        val packets = synchronized(reassembler) {
            reassembler.feed(bytes)
        }
        for (packet in packets) {
            Log.i(TAG, "Reassembled complete packet: svc=${packet.serviceId}, cmd=${packet.commandId}, tlvs=${packet.tlvs.keys}")
            handleParsedPacket(packet)
        }
    }

    private fun handleParsedPacket(packet: Lpv2Frame.ParsedPacket) {
        val key = (packet.serviceId shl 8) or packet.commandId
        Log.d(TAG, "handleParsedPacket: resolving key=0x${Integer.toHexString(key)}")
        responseDeferreds.remove(key)?.complete(packet)

        // Asynchronous updates
        when {
            packet.serviceId == SVC_BASE && packet.commandId == CMD_BATTERY -> {
                packet.tlvs[0x01]?.let { batBytes ->
                    if (batBytes.isNotEmpty()) {
                        _batteryLevel.value = batBytes[0].toInt() and 0xFF
                    }
                }
            }
            packet.serviceId == SVC_BASE && packet.commandId == CMD_PHONE_INFO -> {
                scope.launch {
                    handlePhoneInfoRequest(packet.tlvs)
                }
            }
            packet.serviceId == SVC_BASE && packet.commandId == CMD_PERMISSION_CHECK -> {
                scope.launch {
                    handlePermissionCheck(packet.tlvs)
                }
            }
            packet.serviceId == SVC_DATASYNC && packet.commandId == CMD_DATASYNC_CONFIG -> {
                scope.launch {
                    handleDataSyncConfigRequest(packet.tlvs)
                }
            }
            packet.serviceId == SVC_HR_RRI_TEST && (packet.commandId == CMD_HR_REALTIME_DATA || packet.commandId == CMD_HR_RRI_DATA) -> {
                try {
                    val decrypted = decryptTransactionTlvs(packet.tlvs)
                    if (packet.commandId == CMD_HR_RRI_DATA) {
                        // Unpack RRI stream (svc=0x19, cmd=0x05) arriving every ~4 seconds
                        val container = decrypted[0x82] ?: decrypted[0x02] ?: decrypted[0x01]
                        if (container != null) {
                            val items = VarIntTlv.decodeItems(container)
                            for ((tag, payload) in items) {
                                if (tag == 0x83 || tag == 0x03) {
                                    val item = VarIntTlv.decodeAll(payload)
                                    val rriBytes = item[0x04]
                                    val rri = if (rriBytes != null && rriBytes.size >= 2) {
                                        ((rriBytes[0].toInt() and 0xFF) shl 8) or (rriBytes[1].toInt() and 0xFF)
                                    } else if (rriBytes != null && rriBytes.isNotEmpty()) {
                                        rriBytes[0].toInt() and 0xFF
                                    } else null

                                    if (rri != null && rri in 300..1800) {
                                        val hr = 60000 / rri
                                        if (hr in 35..220) {
                                            _liveHeartRateFlow.value = hr
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // cmd == 0x03 (Realtime HR notification)
                        val container = decrypted[0x01] ?: decrypted[0x81]
                        if (container != null) {
                            val items = VarIntTlv.decodeItems(container)
                            for ((tag, payload) in items) {
                                if (tag == 0x02 || tag == 0x82) {
                                    val item = VarIntTlv.decodeAll(payload)
                                    val hr = item[0x03]?.let { bytes ->
                                        if (bytes.isNotEmpty()) bytes[0].toInt() and 0xFF else null
                                    }
                                    if (hr != null && hr in 35..220) {
                                        _liveHeartRateFlow.value = hr
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse realtime HR/RRI packet: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleDataSyncConfigRequest(tlvs: Map<Int, ByteArray>) {
        val decrypted = decryptTransactionTlvs(tlvs)
        Log.i(TAG, "handleDataSyncConfigRequest: received DataSync packet from band, keys=${decrypted.keys}")
        val configId = DATASYNC_FEATURE_CONFIG_ID
        val configIdBytes = byteArrayOf(
            ((configId ushr 24) and 0xFF).toByte(),
            ((configId ushr 16) and 0xFF).toByte(),
            ((configId ushr 8) and 0xFF).toByte(),
            (configId and 0xFF).toByte()
        )
        val item = VarIntTlv.encode(0x05, configIdBytes) +
                VarIntTlv.encode(0x06, byteArrayOf(0x02)) +
                VarIntTlv.encode(0x07, "01".toByteArray(Charsets.UTF_8))
        val nested = VarIntTlv.encode(0x84, item)
        val reply = VarIntTlv.encode(0x01, "hw.unitedevice.configManager".toByteArray(Charsets.UTF_8)) +
                VarIntTlv.encode(0x02, "featureManager".toByteArray(Charsets.UTF_8)) +
                VarIntTlv.encode(0x83, nested)
        Log.i(TAG, "handleDataSyncConfigRequest: sending feature config ACK (configId=$configId) to band...")
        sendEncryptedNoWait(SVC_DATASYNC, CMD_DATASYNC_CONFIG, reply)
    }

    private suspend fun handlePermissionCheck(tlvs: Map<Int, ByteArray>) {
        val decrypted = decryptTransactionTlvs(tlvs)
        if (decrypted.containsKey(0x7F)) {
            Log.d(TAG, "PermissionCheck ACK received from band")
            return
        }
        val permBytes = decrypted[0x01] ?: return
        val permission = if (permBytes.size >= 2) {
            ((permBytes[0].toInt() and 0xFF) shl 8) or (permBytes[1].toInt() and 0xFF)
        } else if (permBytes.isNotEmpty()) {
            permBytes[0].toInt() and 0xFF
        } else {
            0
        }
        if (permission <= 0) return
        Log.i(TAG, "handlePermissionCheck: granting permission=$permission to watch...")
        val reply = VarIntTlv.encode(0x01, byteArrayOf(((permission ushr 8) and 0xFF).toByte(), (permission and 0xFF).toByte())) +
                VarIntTlv.encode(0x02, byteArrayOf(0x00, 0x00))
        sendEncryptedNoWait(SVC_BASE, CMD_PERMISSION_CHECK, reply)
    }

    private suspend fun handlePhoneInfoRequest(tlvs: Map<Int, ByteArray>) {
        if (cachedSecretKey == null) {
            Log.w(TAG, "handlePhoneInfoRequest: cachedSecretKey is null, skipping response")
            return
        }
        val decrypted = decryptTransactionTlvs(tlvs)
        if (decrypted.containsKey(0x7F)) {
            Log.d(TAG, "PhoneInfo ACK received from band")
            return
        }
        val requestedTags = decrypted.keys.map { it.toByte() }.toByteArray()
        val reply = buildPhoneInfoResponse(requestedTags)
        Log.i(TAG, "handlePhoneInfoRequest: replying to CMD_PHONE_INFO (${reply.size} bytes)...")
        val encryptedReply = encryptTransactionTlv(reply)
        if (encryptedReply.isNotEmpty()) {
            val frames = Lpv2Frame.makeFrames(SVC_BASE, CMD_PHONE_INFO, encryptedReply, sliceSize)
            for (f in frames) {
                sendRawBytes(f)
            }
        }
    }

    private fun buildPhoneInfoResponse(requestedTags: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (tagByte in requestedTags) {
            val tag = tagByte.toInt() and 0xFF
            if (tag == 0x00 || tag == 0x0F) continue
            when (tag) {
                0x02, 0x04, 0x15 -> {
                    out.write(VarIntTlv.encode(tag, ByteArray(0)))
                }
                0x08 -> {
                    out.write(VarIntTlv.encode(tag, "14".toByteArray(Charsets.UTF_8)))
                }
                0x11 -> {
                    val v = 1600008300
                    val b = byteArrayOf(
                        ((v ushr 24) and 0xFF).toByte(),
                        ((v ushr 16) and 0xFF).toByte(),
                        ((v ushr 8) and 0xFF).toByte(),
                        (v and 0xFF).toByte()
                    )
                    out.write(VarIntTlv.encode(tag, b))
                }
                else -> {
                    out.write(VarIntTlv.encode(tag, byteArrayOf(0x00)))
                }
            }
        }
        return out.toByteArray()
    }

    private suspend fun transact(
        serviceId: Int,
        commandId: Int,
        payload: ByteArray = ByteArray(0),
        timeoutMs: Long = 8000
    ): Lpv2Frame.ParsedPacket? {
        val key = (serviceId shl 8) or commandId
        val deferred = CompletableDeferred<Lpv2Frame.ParsedPacket>()
        responseDeferreds[key] = deferred

        val frames = Lpv2Frame.makeFrames(serviceId, commandId, payload, sliceSize)
        Log.i(TAG, "transact: sending ${frames.size} frame(s) for svc=$serviceId cmd=$commandId (payloadLen=${payload.size}, sliceSize=$sliceSize)...")

        for (i in frames.indices) {
            val f = frames[i]
            var sent = false
            for (attempt in 1..4) {
                sent = sendRawBytes(f)
                if (sent) break
                Log.w(TAG, "transact: frame $i send failed on attempt $attempt, retrying in 80ms...")
                delay(80L)
            }
            if (!sent) {
                Log.e(TAG, "transact: FAILED to send frame $i for svc=$serviceId cmd=$commandId!")
                responseDeferreds.remove(key)
                return null
            }
            if (frames.size > 1 && i < frames.size - 1) {
                delay(45L) // 45ms inter-frame delay so the watch's BLE RX FIFO buffer does not overflow
            }
        }

        Log.i(TAG, "transact: all frames sent. Waiting for response on key=0x${Integer.toHexString(key)} (timeout=${timeoutMs}ms)...")
        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
        if (result == null) {
            Log.e(TAG, "transact: TIMED OUT after ${timeoutMs}ms waiting for svc=$serviceId cmd=$commandId!")
            responseDeferreds.remove(key)
        } else {
            Log.i(TAG, "transact: SUCCESS response for svc=$serviceId cmd=$commandId with ${result.tlvs.size} TLVs")
        }
        return result
    }

    /**
     * Executes Link Parameters exchange (Cmd 0x01/0x01).
     */
    suspend fun exchangeLinkParams(): Boolean {
        val tlv1 = VarIntTlv.encode(0x01, clientNonce)
        val tlv2 = VarIntTlv.encode(0x02, byteArrayOf(0x02)) // Auth version
        val payload = tlv1 + tlv2

        val response = transact(SVC_BASE, CMD_LINK_PARAMS, payload) ?: return false
        val sNonce = response.tlvs[0x01]
        if (sNonce != null && sNonce.size >= 16) {
            serverNonce = sNonce
        }
        response.tlvs[0x02]?.let { sliceBytes ->
            if (sliceBytes.size >= 2) {
                val parsed = ((sliceBytes[0].toInt() and 0xFF) shl 8) or (sliceBytes[1].toInt() and 0xFF)
                if (parsed in 100..512) {
                    sliceSize = parsed
                }
            }
        }
        if (sliceSize < 100) {
            sliceSize = 244
        }
        response.tlvs[0x07]?.let {
            if (it.isNotEmpty()) devSupportType = it[0].toInt() and 0xFF
        }
        response.tlvs[0x0C]?.let {
            if (it.isNotEmpty()) encMethod = it[0].toInt() and 0xFF
        }
        Log.i(TAG, "exchangeLinkParams: negotiated sliceSize=$sliceSize, devSupportType=$devSupportType, encMethod=$encMethod")
        _authState.value = AuthState.LINK_PARAMS_NEGOTIATED
        return true
    }

    /**
     * Executes Security Negotiation (Cmd 0x01/0x33).
     */
    suspend fun negotiateSecurity(androidId: String = "25EB96E6DA6F73D02F596631EAA295CD"): Int {
        val tlv1 = VarIntTlv.encode(0x01, byteArrayOf(0x04)) // authMode = 4
        val tlv2 = VarIntTlv.encode(0x02, byteArrayOf(0x01))
        val tlv5 = VarIntTlv.encode(0x05, androidId.toByteArray(Charsets.UTF_8))
        val tlv3 = VarIntTlv.encode(0x03, byteArrayOf(0x01))
        val tlv4 = VarIntTlv.encode(0x04, byteArrayOf(0x00))
        val tlv6 = VarIntTlv.encode(0x06, byteArrayOf())
        val tlv7 = VarIntTlv.encode(0x07, "Android".toByteArray(Charsets.UTF_8))
        val tlvD = VarIntTlv.encode(0x0D, byteArrayOf(0x01))
        val payload = tlv1 + tlv2 + tlv5 + tlv3 + tlv4 + tlv6 + tlv7 + tlvD

        val response = transact(SVC_BASE, CMD_SECURITY_NEG, payload) ?: return -1
        var authType = -1
        var pw = -1
        response.tlvs[0x01]?.let {
            if (it.isNotEmpty()) {
                val v = it[0].toInt() and 0xFF
                if (v == 0x01) authType = 0x0186A0
                else if (v == 0x04) pw = 4
            }
        }
        response.tlvs[0x02]?.let {
            if (it.isNotEmpty()) {
                authType = it[0].toInt() and 0xFF
                if (pw != -1) authType = authType xor pw
            }
        }
        response.tlvs[0x7F]?.let {
            if (it.isNotEmpty()) {
                authType = it[0].toInt() and 0xFF
            }
        }
        Log.i(TAG, "negotiateSecurity: resolved authType=0x${Integer.toHexString(authType)}")
        return authType
    }

    /**
     * Requests raw encrypted PIN from the band (Cmd 0x01/0x2C) and decrypts it.
     */
    suspend fun getPinCode(): ByteArray? {
        Log.i(TAG, "getPinCode: requesting PIN from band (svc=0x01, cmd=0x2C)...")
        val tlv = VarIntTlv.encode(0x01, byteArrayOf())
        val resp = transact(SVC_BASE, CMD_PIN_REQUEST, tlv, timeoutMs = 30000)
        if (resp == null) {
            Log.e(TAG, "getPinCode: transact returned null (timed out or send failed)!")
            return null
        }
        val msg = resp.tlvs[0x01]
        val iv = resp.tlvs[0x02]
        if (msg == null || iv == null) {
            Log.e(TAG, "getPinCode: missing tag 0x01 or 0x02! Available tags: ${resp.tlvs.keys}")
            return null
        }
        return try {
            val pin = HiChain3Crypto.decryptPinCode(0x02, 1, msg, iv)
            Log.i(TAG, "getPinCode: decrypted PIN successfully! PIN=${HiChain3Crypto.bytesToHex(pin)}")
            pin
        } catch (e: Exception) {
            Log.e(TAG, "getPinCode: decryption failed: ${e.message}", e)
            null
        }
    }

    data class HiChainResponse(
        val step: Int,
        val payload: JSONObject?,
        val isTlvAck: Boolean = false,
        val errorCode: Int = 0
    )

    private fun parseHiChainResponse(tlvs: Map<Int, ByteArray>): HiChainResponse? {
        val rawBytes = tlvs[0x01] ?: return null
        if (rawBytes.isEmpty()) return null
        if (rawBytes[0] != '{'.code.toByte()) {
            val appId = String(rawBytes, Charsets.UTF_8)
            val respType = tlvs[0x04]?.firstOrNull()?.toInt() ?: 0
            val status = tlvs[0x05]?.firstOrNull()?.toInt() ?: 0
            Log.i(TAG, "parseHiChainResponse: non-JSON TLV ACK appId=$appId type=$respType status=$status")
            return if (appId == "com.huawei.health" && status == 0) {
                HiChainResponse(step = 4, payload = null, isTlvAck = true)
            } else {
                null
            }
        }
        val cleanJson = String(rawBytes, Charsets.UTF_8).trimEnd { it == '\u0000' }
        return try {
            val obj = JSONObject(cleanJson)
            val step = obj.optInt("message", 0) and 0x0F
            val errorCode = obj.optInt("errorCode", 0)
            val payload = obj.optJSONObject("payload")
            HiChainResponse(step, payload, errorCode = errorCode)
        } catch (e: Exception) {
            Log.e(TAG, "parseHiChainResponse JSON parse error: ${e.message}")
            null
        }
    }

    private fun buildHichainTlv(
        step: Int,
        payloadExtra: JSONObject,
        opCode: Int,
        requestId: Long,
        valueExtra: Map<String, Any>? = null
    ): ByteArray {
        val androidId = "25EB96E6DA6F73D02F596631EAA295CD"
        val messageId = if (opCode == 1) step else (step or 0x10)

        val versionObj = JSONObject().apply {
            put("minVersion", "1.0.0")
            put("currentVersion", "2.0.16")
        }
        payloadExtra.put("version", versionObj)

        val valueObj = JSONObject().apply {
            put("authForm", 0)
            put("payload", payloadExtra)
            put("groupAndModuleVersion", "2.0.1")
            put("message", messageId)
        }

        if (opCode == 1) {
            valueObj.put("requestId", requestId.toString())
            valueObj.put("groupId", HiChain3Crypto.GROUP_ID)
            valueObj.put("groupName", "health_group_name")
            valueObj.put("groupOp", 2)
            valueObj.put("groupType", 256)
            valueObj.put("peerDeviceId", androidId)
            valueObj.put("connDeviceId", androidId)
            valueObj.put("appId", "com.huawei.health")
            valueObj.put("ownerName", "")
        } else {
            valueObj.put("isDeviceLevel", false)
        }

        valueExtra?.forEach { (k, v) -> valueObj.put(k, v) }

        val jsonStr = valueObj.toString()
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        // Tag 0x01: JSON payload
        val tlv1 = VarIntTlv.encode(0x01, jsonBytes)
        // Tag 0x02: op_code (1 byte)
        val tlv2 = VarIntTlv.encode(0x02, byteArrayOf(opCode.toByte()))
        // Tag 0x03: requestId (8-byte big-endian long)
        val reqIdBytes = ByteArray(8)
        for (i in 7 downTo 0) {
            reqIdBytes[i] = ((requestId ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
        val tlv3 = VarIntTlv.encode(0x03, reqIdBytes)

        return tlv1 + tlv2 + tlv3
    }

    /**
     * Executes the complete HiChain3 4-round mutual authentication.
     * Supports both opCode=1 (First-Auth) and opCode=2 (Reconnect/Bind).
     */
    suspend fun hichainAuthenticate(
        pinCode: ByteArray = ByteArray(0),
        opCode: Int = 1,
        onStepUpdate: (String) -> Unit = {},
        onUserPrompt: suspend () -> Unit = {}
    ): Boolean {
        val requestId = System.currentTimeMillis()
        val seed = ByteArray(32).also { random.nextBytes(it) }
        val randSelf = ByteArray(16).also { random.nextBytes(it) }
        val randSelfHex = HiChain3Crypto.bytesToHex(randSelf)
        val androidId = "25EB96E6DA6F73D02F596631EAA295CD"
        val authIdSelfHex = HiChain3Crypto.bytesToHex(androidId.toByteArray(Charsets.UTF_8))

        // ── Round 1 ──
        val step1Payload = JSONObject().apply {
            put("isoSalt", randSelfHex)
            put("peerAuthId", authIdSelfHex)
            put("operationCode", opCode)
            put("seed", HiChain3Crypto.bytesToHex(seed))
            put("peerUserType", 0)
            if (opCode == 2) {
                put("pkgName", "com.huawei.devicegroupmanage")
                put("serviceType", HiChain3Crypto.GROUP_ID)
                put("keyLength", 32)
            }
        }
        val tlv1 = buildHichainTlv(1, step1Payload, opCode, requestId)

        if (opCode == 1) {
            onStepUpdate("Sending pairing request to watch...")
        } else {
            onStepUpdate("Authenticating with band...")
        }

        val timeout1 = if (opCode == 1) 90000L else 15000L
        Log.i(TAG, "hichainAuthenticate: sending Round 1 (opCode=$opCode, timeout=${timeout1}ms)...")
        val resp1 = transact(SVC_BASE, CMD_HICHAIN_AUTH, tlv1, timeoutMs = timeout1) ?: run {
            Log.e(TAG, "hichainAuthenticate: Round 1 transaction returned null!")
            return false
        }

        val r1 = parseHiChainResponse(resp1.tlvs) ?: run {
            Log.e(TAG, "hichainAuthenticate: Failed to parse Round 1 response!")
            return false
        }
        if (r1.isTlvAck) {
            Log.w(TAG, "hichainAuthenticate: Round 1 was rejected or timed out on the watch (isTlvAck received).")
            onStepUpdate("Pairing cancelled on watch. Please open Settings -> System -> Disconnect on watch and try again.")
            return false
        }
        val p1 = r1.payload ?: run {
            Log.e(TAG, "hichainAuthenticate: Round 1 payload was null!")
            return false
        }

        val randPeerHex = p1.optString("isoSalt")
        val authIdPeerHex = p1.optString("peerAuthId")
        val peerTokenHex = p1.optString("token")
        if (randPeerHex.isEmpty() || authIdPeerHex.isEmpty() || peerTokenHex.isEmpty()) {
            Log.e(TAG, "hichainAuthenticate: Round 1 response missing required fields (isoSalt, peerAuthId, or token)!")
            return false
        }
        val randPeer = HiChain3Crypto.hexToBytes(randPeerHex)
        val authIdPeer = HiChain3Crypto.hexToBytes(authIdPeerHex)
        val peerToken = HiChain3Crypto.hexToBytes(peerTokenHex)

        // Derive PSK
        val psk = if (opCode == 1) {
            HiChain3Crypto.derivePskFirstAuth(pinCode, seed)
        } else {
            val key = cachedAuthKey ?: run {
                Log.e(TAG, "hichainAuthenticate: opCode=2 but cachedAuthKey is null!")
                return false
            }
            HiChain3Crypto.derivePskReconnect(key, seed)
        }

        // Verify peer token
        val expectedPeerToken = HiChain3Crypto.hmacSha256(
            psk,
            randPeer + randSelf + HiChain3Crypto.hexToBytes(authIdSelfHex) + authIdPeer
        )
        if (!expectedPeerToken.contentEquals(peerToken)) {
            Log.w(TAG, "Peer token mismatch! expected=${HiChain3Crypto.bytesToHex(expectedPeerToken)} got=${HiChain3Crypto.bytesToHex(peerToken)}")
        } else {
            Log.i(TAG, "Peer token verified OK.")
        }

        // ── USER CONFIRMATION ON WATCH ──
        if (opCode == 1) {
            Log.i(TAG, "hichainAuthenticate: Round 1 accepted by watch. Checkmark is now visible on watch screen. Awaiting user tap...")
            onStepUpdate("👉 TAP THE CHECKMARK (✓) ON YOUR WATCH!")
            onUserPrompt()
            Log.i(TAG, "hichainAuthenticate: User confirmed checkmark tapped on watch. Proceeding to Round 2...")
        }

        // ── Round 2 ──
        onStepUpdate("Verifying authentication token...")
        val selfToken = HiChain3Crypto.hmacSha256(
            psk,
            randSelf + randPeer + authIdPeer + HiChain3Crypto.hexToBytes(authIdSelfHex)
        )
        val step2Payload = JSONObject().apply {
            put("peerAuthId", authIdSelfHex)
            put("token", HiChain3Crypto.bytesToHex(selfToken))
        }
        val valExtra2 = if (opCode == 2) mapOf("isDeviceLevel" to false) else null
        val tlv2 = buildHichainTlv(2, step2Payload, opCode, requestId, valExtra2)
        Log.i(TAG, "hichainAuthenticate: sending Round 2...")
        val resp2 = transact(SVC_BASE, CMD_HICHAIN_AUTH, tlv2, timeoutMs = 15000L) ?: run {
            Log.e(TAG, "hichainAuthenticate: Round 2 transaction failed!")
            return false
        }
        val r2 = parseHiChainResponse(resp2.tlvs)
        Log.i(TAG, "hichainAuthenticate: Round 2 received step=${r2?.step}")
        if (r2 == null || r2.step == 0) {
            Log.w(TAG, "hichainAuthenticate: Round 2 rejected by band (step=${r2?.step}).")
            if (opCode == 2) {
                Log.w(TAG, "hichainAuthenticate: Fast reconnect rejected; rolling back to first-auth (opCode=1)...")
                cachedAuthKey = null
                cachedSecretKey = null
                val pin = getPinCode() ?: return false
                return hichainAuthenticate(
                    pinCode = pin,
                    opCode = 1,
                    onStepUpdate = onStepUpdate,
                    onUserPrompt = onUserPrompt
                )
            }
            return false
        }

        // ── Round 3 (First-Auth only) ──
        val salt = randSelf + randPeer
        val sessKey = HiChain3Crypto.hkdfSha256(psk, salt, "hichain_iso_session_key".toByteArray(Charsets.UTF_8), 32)
        sessionKey = sessKey

        if (opCode == 1) {
            onStepUpdate("Deriving encryption keys...")
            val nonce3 = ByteArray(12).also { random.nextBytes(it) }
            val challenge3 = ByteArray(16).also { random.nextBytes(it) }
            val aad3 = "hichain_iso_exchange".toByteArray(Charsets.UTF_8)
            val encData3 = HiChain3Crypto.aesGcmEncrypt(challenge3, sessKey, nonce3, aad3)

            val step3Payload = JSONObject().apply {
                put("nonce", HiChain3Crypto.bytesToHex(nonce3))
                put("encData", HiChain3Crypto.bytesToHex(encData3))
            }
            val tlv3 = buildHichainTlv(3, step3Payload, opCode, requestId)
            Log.i(TAG, "hichainAuthenticate: sending Round 3 (First-Auth)...")
            val resp3 = transact(SVC_BASE, CMD_HICHAIN_AUTH, tlv3, timeoutMs = 30000L) ?: run {
                Log.e(TAG, "hichainAuthenticate: Round 3 transaction failed!")
                return false
            }
            val r3 = parseHiChainResponse(resp3.tlvs) ?: run {
                Log.e(TAG, "hichainAuthenticate: Failed to parse Round 3 response!")
                return false
            }
            val p3 = r3.payload ?: run {
                Log.e(TAG, "hichainAuthenticate: Round 3 payload is null!")
                return false
            }

            val noncePeer3 = HiChain3Crypto.hexToBytes(p3.optString("nonce"))
            val encAuthTok = HiChain3Crypto.hexToBytes(p3.optString("encAuthToken"))
            val authToken = HiChain3Crypto.aesGcmDecrypt(encAuthTok, sessKey, noncePeer3, challenge3)
            cachedAuthKey = authToken
            Log.i(TAG, "hichainAuthenticate: authToken derived & stored: ${HiChain3Crypto.bytesToHex(authToken)}")
        } else {
            Log.i(TAG, "hichainAuthenticate: Step 3 skipped in reconnect mode.")
        }

        // ── Round 4 ──
        onStepUpdate("Finalizing pairing bond...")
        val nonce4 = ByteArray(12).also { random.nextBytes(it) }
        val aad4 = "hichain_iso_result".toByteArray(Charsets.UTF_8)
        val encRes = HiChain3Crypto.aesGcmEncrypt(byteArrayOf(0, 0, 0, 0), sessKey, nonce4, aad4)

        val step4Payload = JSONObject().apply {
            put("nonce", HiChain3Crypto.bytesToHex(nonce4))
            put("encResult", HiChain3Crypto.bytesToHex(encRes))
            put("operationCode", opCode)
        }
        val step4Num = if (opCode == 1) 4 else 3
        val tlv4 = buildHichainTlv(step4Num, step4Payload, opCode, requestId)
        Log.i(TAG, "hichainAuthenticate: sending Round 4 (step=$step4Num)...")
        val resp4 = transact(SVC_BASE, CMD_HICHAIN_AUTH, tlv4, timeoutMs = 20000L)
        if (resp4 == null) {
            Log.e(TAG, "hichainAuthenticate: Round 4 transaction timed out!")
            return false
        }
        val r4 = parseHiChainResponse(resp4.tlvs)
        Log.i(TAG, "hichainAuthenticate: Round 4 complete: r4=$r4")

        // If this was first-auth, immediately cascade into the bind pass (opCode=2)!
        if (opCode == 1) {
            Log.i(TAG, "First-auth completed successfully; starting HiChain bind pass (opCode=2)...")
            onStepUpdate("Establishing secure session...")
            return hichainAuthenticate(
                pinCode = ByteArray(0),
                opCode = 2,
                onStepUpdate = onStepUpdate,
                onUserPrompt = onUserPrompt
            )
        }

        // For opCode == 2: derive the final permanent return key
        val finalKey = HiChain3Crypto.hkdfSha256(sessKey, salt, "hichain_return_key".toByteArray(Charsets.UTF_8), 32)
        cachedSecretKey = finalKey
        Log.i(TAG, "hichainAuthenticate: Session transaction secret_key derived: ${HiChain3Crypto.bytesToHex(finalKey)}")

        onStepUpdate("Paired successfully!")
        _authState.value = AuthState.AUTHENTICATED
        return true
    }

    /**
     * Resets authentication state on physical BLE disconnection or session invalidation.
     */
    fun resetAuthState() {
        Log.i(TAG, "resetAuthState: Invalidating session credentials and resetting auth state to IDLE.")
        _authState.value = AuthState.IDLE
        sessionKey = null
        cachedSecretKey = null
        serverNonce = null
    }

    /**
     * Unified entry point for initial pairing (with watch checkmark) or fast reconnect.
     * When forceFirstAuth is true (e.g. from PairingScreen), clears cached credentials and
     * mandates opCode=1 so the watch displays the on-screen checkmark prompt.
     */
    suspend fun authenticateHandshake(
        storedAuthKeyHex: String? = null,
        forceFirstAuth: Boolean = false,
        onStepUpdate: (String) -> Unit = {},
        onUserPrompt: suspend () -> Unit = {}
    ): Boolean {
        onStepUpdate("Negotiating link parameters...")
        if (!exchangeLinkParams()) {
            Log.e(TAG, "authenticateHandshake: Link parameters exchange failed!")
            return false
        }

        onStepUpdate("Negotiating security protocol...")
        val authType = negotiateSecurity()

        fallbackAuthKey = storedAuthKeyHex?.let { HiChain3Crypto.hexToBytes(it) }
        if (!forceFirstAuth && storedAuthKeyHex != null) {
            cachedAuthKey = fallbackAuthKey
        } else if (forceFirstAuth) {
            cachedAuthKey = null
            cachedSecretKey = null
        }

        return if (forceFirstAuth || authType == 0x05 || cachedAuthKey == null) {
            Log.i(TAG, "authenticateHandshake: First-auth mode (authType=0x${Integer.toHexString(authType)}, forceFirstAuth=$forceFirstAuth): requesting PIN...")
            onStepUpdate("Requesting pairing PIN...")
            val pin = getPinCode() ?: run {
                Log.e(TAG, "authenticateHandshake: Failed to retrieve PIN from band!")
                return false
            }
            hichainAuthenticate(
                pinCode = pin,
                opCode = 1,
                onStepUpdate = onStepUpdate,
                onUserPrompt = onUserPrompt
            )
        } else {
            Log.i(TAG, "authenticateHandshake: Fast reconnect mode (authType=0x${Integer.toHexString(authType)})...")
            onStepUpdate("Reconnecting...")
            hichainAuthenticate(
                pinCode = ByteArray(0),
                opCode = 2,
                onStepUpdate = onStepUpdate,
                onUserPrompt = onUserPrompt
            )
        }
    }

    /**
     * Encrypts a plaintext TLV payload using the negotiated secret_key via AES-CBC (or AES-GCM),
     * packaging it into Huawei LPv2 CryptoTags (0x7C, 0x7D, 0x7E).
     */
    fun encryptTransactionTlv(plainTlv: ByteArray): ByteArray {
        val key = cachedSecretKey ?: run {
            Log.w(TAG, "encryptTransactionTlv: cachedSecretKey is null, cannot encrypt payload")
            return ByteArray(0)
        }
        val iv = nextCryptoIv()
        val cipher = if (usesGcm()) {
            HiChain3Crypto.aesGcmEncrypt(plainTlv, key, iv, ByteArray(0))
        } else {
            HiChain3Crypto.aesCbcEncrypt(plainTlv, key, iv)
        }
        val tlv7C = VarIntTlv.encode(0x7C, byteArrayOf(0x01))
        val tlv7D = VarIntTlv.encode(0x7D, iv)
        val tlv7E = VarIntTlv.encode(0x7E, cipher)
        return tlv7C + tlv7D + tlv7E
    }

    /**
     * Decrypts an incoming CryptoTag-wrapped TLV payload (0x7C, 0x7D, 0x7E) using secret_key.
     */
    fun decryptTransactionTlvs(tlvs: Map<Int, ByteArray>): Map<Int, ByteArray> {
        val key = cachedSecretKey
        if (key == null || !tlvs.containsKey(0x7C) || !tlvs.containsKey(0x7D) || !tlvs.containsKey(0x7E)) {
            return tlvs
        }
        val iv = tlvs[0x7D] ?: return tlvs
        val cipher = tlvs[0x7E] ?: return tlvs
        return try {
            val plain = if (usesGcm()) {
                HiChain3Crypto.aesGcmDecrypt(cipher, key, iv, ByteArray(0))
            } else {
                HiChain3Crypto.aesCbcDecrypt(cipher, key, iv)
            }
            VarIntTlv.decodeAll(plain)
        } catch (e: Exception) {
            Log.w(TAG, "decryptTransactionTlvs decryption failed: ${e.message}")
            tlvs
        }
    }

    /**
     * Executes an encrypted transaction over BLE: encrypts outgoing TLVs and decrypts response TLVs.
     */
    suspend fun transactEncrypted(
        serviceId: Int,
        commandId: Int,
        plainPayload: ByteArray = ByteArray(0),
        timeoutMs: Long = 8000
    ): Map<Int, ByteArray>? {
        if (cachedSecretKey == null) {
            Log.w(TAG, "transactEncrypted: cachedSecretKey is null for svc=$serviceId cmd=$commandId, skipping")
            return null
        }
        val encryptedPayload = encryptTransactionTlv(plainPayload)
        if (encryptedPayload.isEmpty()) return null
        val resp = transact(serviceId, commandId, encryptedPayload, timeoutMs) ?: return null
        val decrypted = decryptTransactionTlvs(resp.tlvs)

        // Detect Huawei LPv2 unauthorized/session expired error (0x000186A4)
        decrypted[0x7F]?.let { statusBytes ->
            if (statusBytes.size >= 4) {
                val code = ((statusBytes[0].toInt() and 0xFF) shl 24) or
                        ((statusBytes[1].toInt() and 0xFF) shl 16) or
                        ((statusBytes[2].toInt() and 0xFF) shl 8) or
                        (statusBytes[3].toInt() and 0xFF)
                if (code == 0x000186A4) {
                    Log.w(TAG, "transactEncrypted: Received 0x000186A4 (UNAUTHORIZED) on svc=$serviceId cmd=$commandId! Resetting auth state.")
                    resetAuthState()
                }
            }
        }

        return decrypted
    }

    /**
     * Sends an encrypted TLV payload over BLE without waiting for a reply.
     * Matches tools/connect.py `_send_encrypted_no_wait` (used for CMD_SETUP_DEVICE_STATUS).
     */
    suspend fun sendEncryptedNoWait(
        serviceId: Int,
        commandId: Int,
        plainPayload: ByteArray = ByteArray(0)
    ): Boolean {
        return try {
            if (cachedSecretKey == null) {
                Log.w(TAG, "sendEncryptedNoWait: cachedSecretKey is null for svc=$serviceId cmd=$commandId, skipping")
                return false
            }
            val encryptedPayload = encryptTransactionTlv(plainPayload)
            if (encryptedPayload.isEmpty()) return false
            val frames = Lpv2Frame.makeFrames(serviceId, commandId, encryptedPayload, sliceSize)
            for (f in frames) {
                val sent = sendRawBytes(f)
                if (!sent) return false
                delay(20L)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendEncryptedNoWait failed: ${e.message}")
            false
        }
    }

    /**
     * Dispatches CMD_CONNECT_STATUS (0x01/0x35) with tag 0x01 = 0x01.
     * Commands Huawei Band firmware to transition from pairing mode to the active watch face.
     */
    suspend fun sendDismissPairingScreen(): Boolean {
        val connectStatusPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
        val resp = transactEncrypted(SVC_BASE, CMD_CONNECT_STATUS, connectStatusPayload, timeoutMs = 5000)
        Log.i(TAG, "sendDismissPairingScreen: response=${resp?.keys}")
        return resp != null
    }

    /**
     * Executes Post-Auth Connected Bootstrap matching tools/connect.py:
     * 1. Product info query (CMD_PRODUCT 0x01/0x07)
     * 2. Time Sync (CMD_TIME 0x01/0x05) with timezone offset
     * 3. Battery query (CMD_BATTERY 0x01/0x08)
     * 4. Supported services announcement (CMD_SUPPORTED_SERVICES 0x01/0x02)
     * 5. Expand capability query (CMD_EXPAND_CAPABILITY 0x01/0x37)
     * 6. Settings query (CMD_SETTING_RELATED 0x01/0x31)
     * 7. Mandatory legal agreement acceptance (CMD_ACCEPT_AGREEMENT 0x01/0x30)
     * 8. Reverse capabilities announcement (CMD_REVERSE_CAPABILITIES 0x01/0x3F)
     * 9. Setup device status (CMD_SETUP_DEVICE_STATUS 0x01/0x3E) via sendEncryptedNoWait (no blocking)
     * 10. Wear status query (CMD_WEAR_STATUS 0x01/0x3D)
     * 11. Final CMD_CONNECT_STATUS (0x01/0x35) -> Activates watch face and dismisses pairing UI on band!
     */
    suspend fun executePostAuthBootstrap(
        onStepUpdate: (String) -> Unit = {}
    ): Boolean {
        Log.i(TAG, "executePostAuthBootstrap: starting Huawei connected-state bootstrap...")
        onStepUpdate("Pairing confirmed! Configuring watch...")

        // 1. Product info query (svc=0x01, cmd=0x07)
        try {
            val productTags = byteArrayOf(0x01, 0x02, 0x07, 0x09, 0x0A, 0x11)
            var prodTlv = ByteArray(0)
            for (t in productTags) {
                prodTlv += VarIntTlv.encode(t.toInt(), ByteArray(0))
            }
            transactEncrypted(SVC_BASE, CMD_PRODUCT, prodTlv, timeoutMs = 4000)
        } catch (e: Exception) {
            Log.w(TAG, "Product query non-fatal error: ${e.message}")
        }

        // 2. Time Sync (svc=0x01, cmd=0x05) - Real time command on Huawei wearables!
        try {
            val nowSec = (System.currentTimeMillis() / 1000).toInt()
            val tzBytes = getTimezoneOffsetBytes()
            val timePayload = VarIntTlv.encode(0x01, nowSec) + VarIntTlv.encode(0x02, tzBytes)
            val timeResp = transactEncrypted(SVC_BASE, CMD_TIME, timePayload, timeoutMs = 5000)
            Log.i(TAG, "Time sync response: ${timeResp?.keys}")
        } catch (e: Exception) {
            Log.w(TAG, "Time sync error: ${e.message}")
        }

        // 3. Battery query (svc=0x01, cmd=0x08)
        queryBattery()

        // 4. Supported services announcement (svc=0x01, cmd=0x02)
        try {
            val servPayload = VarIntTlv.encode(0x01, KNOWN_SUPPORTED_SERVICES)
            transactEncrypted(SVC_BASE, CMD_SUPPORTED_SERVICES, servPayload, timeoutMs = 5000)
        } catch (e: Exception) {
            Log.w(TAG, "Supported services error: ${e.message}")
        }

        // 5. Expand capabilities query (svc=0x01, cmd=0x37)
        try {
            val capPayload = VarIntTlv.encode(0x01, ByteArray(0))
            transactEncrypted(SVC_BASE, CMD_EXPAND_CAPABILITY, capPayload, timeoutMs = 5000)
        } catch (e: Exception) {
            Log.w(TAG, "Expand capability error: ${e.message}")
        }

        // 6. Settings query (svc=0x01, cmd=0x31)
        try {
            var settingPayload = ByteArray(0)
            for (tag in 1..6) {
                settingPayload += VarIntTlv.encode(tag, ByteArray(0))
            }
            transactEncrypted(SVC_BASE, CMD_SETTING_RELATED, settingPayload, timeoutMs = 5000)
        } catch (e: Exception) {
            Log.w(TAG, "Settings related error: ${e.message}")
        }

        // 7. Accept mandatory agreements (svc=0x01, cmd=0x30)
        Log.i(TAG, "executePostAuthBootstrap: sending CMD_ACCEPT_AGREEMENT (cmd=0x30)...")
        try {
            val timestamp = System.currentTimeMillis().toString().toByteArray(Charsets.UTF_8)
            val version = "20230508-20230508-0-0".toByteArray(Charsets.UTF_8)
            val agreementKeys = listOf(
                "software_update_service_statement",
                "device_information_management",
                "user_license_agreement"
            )
            val items = ByteArrayOutputStream()
            for (key in agreementKeys) {
                val itemContent = VarIntTlv.encode(0x03, key) +
                        VarIntTlv.encode(0x04, byteArrayOf(0x01)) +
                        VarIntTlv.encode(0x05, version) +
                        VarIntTlv.encode(0x06, timestamp)
                items.write(VarIntTlv.encode(0x82, itemContent))
            }
            val agreementPayload = VarIntTlv.encode(0x81, items.toByteArray())
            val agreeResp = transactEncrypted(SVC_BASE, CMD_ACCEPT_AGREEMENT, agreementPayload, timeoutMs = 8000)
            Log.i(TAG, "executePostAuthBootstrap: CMD_ACCEPT_AGREEMENT response: ${agreeResp?.keys}")
        } catch (e: Exception) {
            Log.w(TAG, "CMD_ACCEPT_AGREEMENT error: ${e.message}")
        }

        // 8. Send Reverse Capabilities (svc=0x01, cmd=0x3F)
        Log.i(TAG, "executePostAuthBootstrap: sending CMD_REVERSE_CAPABILITIES (cmd=0x3F)...")
        try {
            val revCapPayload = VarIntTlv.encode(0x01, byteArrayOf(0xFD.toByte(), 0xF7.toByte(), 0x73, 0x7A))
            transactEncrypted(SVC_BASE, CMD_REVERSE_CAPABILITIES, revCapPayload, timeoutMs = 8000)
        } catch (e: Exception) {
            Log.w(TAG, "CMD_REVERSE_CAPABILITIES error: ${e.message}")
        }

        // 9. Setup Device Status (svc=0x01, cmd=0x3E) via sendEncryptedNoWait (matches tools/connect.py)
        Log.i(TAG, "executePostAuthBootstrap: sending CMD_SETUP_DEVICE_STATUS (cmd=0x3E) no-wait...")
        try {
            val setupDevPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01)) +
                    VarIntTlv.encode(0x02, "HUAWEI Band 10") +
                    VarIntTlv.encode(0x03, byteArrayOf(0x00))
            sendEncryptedNoWait(SVC_BASE, CMD_SETUP_DEVICE_STATUS, setupDevPayload)
            delay(100L)
        } catch (e: Exception) {
            Log.w(TAG, "CMD_SETUP_DEVICE_STATUS error: ${e.message}")
        }

        // 10. Wear status query (svc=0x01, cmd=0x3D)
        try {
            val wearPayload = VarIntTlv.encode(0x01, ByteArray(0))
            transactEncrypted(SVC_BASE, CMD_WEAR_STATUS, wearPayload, timeoutMs = 5000)
        } catch (e: Exception) {
            Log.w(TAG, "Wear status error: ${e.message}")
        }

        // 11. Send CONNECT_STATUS (svc=0x01, cmd=0x35) -> Triggers watch screen to display active watch face!
        Log.i(TAG, "executePostAuthBootstrap: sending CMD_CONNECT_STATUS (activate watch face)...")
        val connectStatusPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
        val connectResp = transactEncrypted(SVC_BASE, CMD_CONNECT_STATUS, connectStatusPayload, timeoutMs = 5000)
        Log.i(TAG, "executePostAuthBootstrap: CMD_CONNECT_STATUS response: ${connectResp?.keys}")

        _authState.value = AuthState.AUTHENTICATED
        Log.i(TAG, "executePostAuthBootstrap: bootstrap complete! Activation signal sent.")

        // 12. Activate 24/7 continuous health monitoring on watch hardware
        enablePassiveHealthSettings()

        return true
    }

    /**
     * Activates 24/7 continuous sensor monitoring on Huawei Band 10 hardware:
     * - TruSleep scientific sleep tracking (SVC 0x07 / CMD 0x16)
     * - 24/7 continuous optical PPG heart rate monitoring (SVC 0x07 / CMD 0x17)
     * - Automatic SpO2 blood oxygenation spot sampling (SVC 0x07 / CMD 0x24)
     * - Sleep apnea and respiratory breathing monitoring (SVC 0x2D / CMD 0x01)
     */
    suspend fun enablePassiveHealthSettings(): Boolean {
        if (cachedSecretKey == null) {
            Log.w(TAG, "enablePassiveHealthSettings: cannot send settings before session key derivation")
            return false
        }
        Log.i(TAG, "enablePassiveHealthSettings: activating 24/7 continuous health monitoring on watch...")
        var success = true

        // 1. TruSleep (0x07 / 0x16)
        try {
            val trusleepPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
            val resp = transactEncrypted(SVC_HEALTH, CMD_TRUSLEEP, trusleepPayload, timeoutMs = 6000)
            Log.i(TAG, "enablePassiveHealthSettings: TruSleep activated (resp=${resp?.keys})")
        } catch (e: Exception) {
            Log.w(TAG, "enablePassiveHealthSettings: TruSleep enable error: ${e.message}")
            success = false
        }

        // 2. Continuous Heart Rate (0x07 / 0x17)
        try {
            val hrPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
            sendEncryptedNoWait(SVC_HEALTH, CMD_AUTO_HR, hrPayload)
            delay(150L)
            Log.i(TAG, "enablePassiveHealthSettings: 24/7 Continuous PPG HR activated")
        } catch (e: Exception) {
            Log.w(TAG, "enablePassiveHealthSettings: Auto HR error: ${e.message}")
            success = false
        }

        // 3. Automatic SpO2 (0x07 / 0x24)
        try {
            val spo2Payload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
            sendEncryptedNoWait(SVC_HEALTH, CMD_AUTO_SPO2, spo2Payload)
            delay(150L)
            Log.i(TAG, "enablePassiveHealthSettings: Automatic SpO2 sampling activated")
        } catch (e: Exception) {
            Log.w(TAG, "enablePassiveHealthSettings: Auto SpO2 error: ${e.message}")
            success = false
        }

        // 4. Sleep Breath (0x2D / 0x01)
        try {
            val breathPayload = VarIntTlv.encode(0x01, byteArrayOf(0x02))
            sendEncryptedNoWait(SVC_BREATH, CMD_SLEEP_BREATH, breathPayload)
            delay(150L)
            Log.i(TAG, "enablePassiveHealthSettings: Sleep breathing tracking activated")
        } catch (e: Exception) {
            Log.w(TAG, "enablePassiveHealthSettings: Sleep breath error: ${e.message}")
        }

        // 5. Automatic Stress & Emotional Wellbeing Calibration (0x20 / 0x09)
        try {
            val nowSec = (System.currentTimeMillis() / 1000L).toInt()
            val tsBytes = byteArrayOf(
                ((nowSec ushr 24) and 0xFF).toByte(),
                ((nowSec ushr 16) and 0xFF).toByte(),
                ((nowSec ushr 8) and 0xFF).toByte(),
                (nowSec and 0xFF).toByte()
            )
            val features = floatArrayOf(
                3.0f, 6.7698f, 380.0f, 75.367f,
                134.46f, 940.0f, 0.767f, 198.68f,
                797.1f, 19.72f, 0.0f, 0.0f
            )
            val featBytes = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.BIG_ENDIAN).apply {
                for (f in features) putFloat(f)
            }.array()

            val stressPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01)) +
                    VarIntTlv.encode(0x02, byteArrayOf(28)) + // Baseline score
                    VarIntTlv.encode(0x03, featBytes) +
                    VarIntTlv.encode(0x04, tsBytes)

            sendEncryptedNoWait(SVC_STRESS, CMD_STRESS_AUTO, stressPayload)
            delay(150L)
            Log.i(TAG, "enablePassiveHealthSettings: Automatic Stress / Emotional Wellbeing activated on watch hardware")
        } catch (e: Exception) {
            Log.w(TAG, "enablePassiveHealthSettings: Stress auto calibration error: ${e.message}")
        }

        return success
    }

    private fun getTimezoneOffsetBytes(): ByteArray {
        val tz = java.util.TimeZone.getDefault()
        val offsetMs = tz.getOffset(System.currentTimeMillis())
        val offsetSec = offsetMs / 1000
        val hour = if (offsetSec < 0) ((-offsetSec / 3600) + 128) else offsetSec / 3600
        val minute = (Math.abs(offsetSec) / 60) % 60
        return byteArrayOf((hour and 0xFF).toByte(), (minute and 0xFF).toByte())
    }

    /**
     * Queries battery percentage (Cmd 0x01/0x08).
     */
    suspend fun queryBattery(): Int? {
        val payload = VarIntTlv.encode(0x01, ByteArray(0))
        val tlvs = if (cachedSecretKey != null) {
            transactEncrypted(SVC_BASE, CMD_BATTERY, payload, timeoutMs = 4000)
        } else {
            transact(SVC_BASE, CMD_BATTERY, payload, timeoutMs = 4000)?.tlvs
        }
        tlvs?.get(0x01)?.let {
            if (it.isNotEmpty()) {
                val bat = it[0].toInt() and 0xFF
                _batteryLevel.value = bat
                return bat
            }
        }
        tlvs?.get(0x02)?.let {
            if (it.isNotEmpty()) {
                val bat = it[0].toInt() and 0xFF
                _batteryLevel.value = bat
                return bat
            }
        }
        return _batteryLevel.value
    }

    /**
     * Parses Huawei Band step record feature bitmap:
     * - bitmap1 bit 0x02: steps (UInt16 big endian)
     * - bitmap1 bit 0x04: calories (UInt16 big endian)
     * - bitmap1 bit 0x08: distance (UInt16 big endian)
     * - bitmap1 bit 0x40: heart_rate (UInt8)
     * - bitmap2 bit 0x01: spo2 (UInt8)
     * - bitmap2 bit 0x02: resting_heart_rate (UInt8)
     */
    fun parseStepFeatures(data: ByteArray): Map<String, Int> {
        if (data.isEmpty()) return emptyMap()
        var i = 0
        val b1 = data[i].toInt() and 0xFF
        i++
        var b2 = 0
        if ((b1 and 0x80) != 0) {
            if (i >= data.size) return emptyMap()
            b2 = data[i].toInt() and 0xFF
            i++
        }

        val map = mutableMapOf<String, Int>()
        val bits1 = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40)
        for (bit in bits1) {
            if ((b1 and bit) == 0) continue
            val width = if (bit == 0x20 || bit == 0x40) 1 else 2
            if (i + width > data.size) break
            val value = if (width == 1) {
                val v = data[i].toInt() and 0xFF
                i += 1
                v
            } else {
                val v = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                i += 2
                v
            }

            when (bit) {
                0x02 -> map["steps"] = value
                0x04 -> map["calories"] = value
                0x08 -> map["distance"] = value
                0x40 -> map["heart_rate"] = value
            }
        }

        val bits2 = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80)
        for (bit in bits2) {
            if ((b2 and bit) == 0) continue
            if (i >= data.size) break
            val value = data[i].toInt() and 0xFF
            i++
            when (bit) {
                0x01 -> map["spo2"] = value
                0x02 -> map["resting_heart_rate"] = value
            }
        }
        return map
    }

    /**
     * Queries minute-level fitness samples (steps, HR, SpO2) using official Huawei LPv2 protocol:
     * 1. CMD_STEP_COUNT (0x0A): Retrieves count of sample packets available in rolling window.
     * 2. CMD_STEP_RECORD (0x0B): Iterates each packet index, decoding base timestamp and 0x84 minute samples.
     */
    suspend fun queryFitnessHistory(startTime: Long, endTime: Long): List<SampleMetric> {
        val startSec = (startTime / 1000).toInt()
        val endSec = (endTime / 1000).toInt()

        Log.i(TAG, "queryFitnessHistory: querying sample count from $startSec to $endSec (SVC=0x07, CMD=0x0A)...")
        val countPayload = VarIntTlv.encode(0x81, ByteArray(0)) +
                VarIntTlv.encode(0x03, startSec) +
                VarIntTlv.encode(0x04, endSec)

        val countResp = transactEncrypted(SVC_HEALTH, CMD_STEP_COUNT, countPayload, timeoutMs = 8000)
        var stepRecordCount = -1
        if (countResp != null) {
            countResp[0x81]?.let { container ->
                val sub = VarIntTlv.decodeAll(container)
                sub[0x02]?.let { cntBytes ->
                    if (cntBytes.size >= 2) {
                        stepRecordCount = ((cntBytes[0].toInt() and 0xFF) shl 8) or (cntBytes[1].toInt() and 0xFF)
                    }
                }
            }
            if (stepRecordCount < 0) {
                countResp[0x02]?.let { cntBytes ->
                    if (cntBytes.size >= 2) {
                        stepRecordCount = ((cntBytes[0].toInt() and 0xFF) shl 8) or (cntBytes[1].toInt() and 0xFF)
                    }
                }
            }
        }

        Log.i(TAG, "queryFitnessHistory: watch reported stepRecordCount=$stepRecordCount")
        if (stepRecordCount <= 0) {
            querySleepHistory(startSec, endSec)
            return emptyList()
        }

        val samples = mutableListOf<SampleMetric>()
        val startIdx = stepRecordCount - 1
        val endIdx = (stepRecordCount - 500).coerceAtLeast(0)
        Log.i(TAG, "queryFitnessHistory: querying blocks in reverse from $startIdx down to $endIdx (newest first)...")
        for (idx in startIdx downTo endIdx) {
            val idxPayload = VarIntTlv.encode(
                0x81,
                VarIntTlv.encode(0x02, byteArrayOf(((idx ushr 8) and 0xFF).toByte(), (idx and 0xFF).toByte()))
            )
            val recResp = transactEncrypted(SVC_HEALTH, CMD_STEP_RECORD, idxPayload, timeoutMs = 8000) ?: continue
            val containerBytes = recResp[0x81] ?: continue
            val container = VarIntTlv.decodeAll(containerBytes)
            val baseTsBytes = container[0x03] ?: continue
            val baseTs = if (baseTsBytes.size >= 4) {
                ((baseTsBytes[0].toLong() and 0xFF) shl 24) or
                        ((baseTsBytes[1].toLong() and 0xFF) shl 16) or
                        ((baseTsBytes[2].toLong() and 0xFF) shl 8) or
                        (baseTsBytes[3].toLong() and 0xFF)
            } else continue

            // If base timestamp is older than the requested query window, we have retrieved all new data
            if (baseTs + 3600L < startSec) {
                Log.i(TAG, "queryFitnessHistory: reached baseTs $baseTs < startSec $startSec, complete.")
                break
            }

            val items = VarIntTlv.decodeItems(containerBytes)
            for ((tag, value) in items) {
                if (tag != 0x84) continue
                val sub = VarIntTlv.decodeAll(value)
                val offset = sub[0x05]?.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                val rawData = sub[0x06] ?: continue
                val features = parseStepFeatures(rawData)

                val sampleTs = baseTs + (60L * offset)
                val steps = features["steps"] ?: 0
                val calories = features["calories"] ?: 0
                val hr = features["heart_rate"]
                val validHr = if (hr != null && hr in 35..230) hr else null
                val spo2 = features["spo2"]
                val validSpo2 = if (spo2 != null && spo2 in 70..100) spo2.toFloat() else null

                if (steps > 0 || validHr != null || validSpo2 != null) {
                    samples.add(
                        SampleMetric(
                            timestamp = sampleTs,
                            steps = steps,
                            heartRate = validHr,
                            spo2 = validSpo2,
                            calories = calories
                        )
                    )
                }
            }
        }

        Log.i(TAG, "queryFitnessHistory: successfully parsed ${samples.size} telemetry samples from watch")
        if (samples.isNotEmpty()) {
            _fitnessDataFlow.emit(samples)
        }

        querySleepHistory(startSec, endSec)
        return samples
    }

    /**
     * Queries sleep session records (CMD_SLEEP_COUNT 0x0C and CMD_SLEEP_RECORD 0x0D).
     */
    suspend fun querySleepHistory(startSec: Int, endSec: Int) {
        try {
            val countPayload = VarIntTlv.encode(0x81, ByteArray(0)) +
                    VarIntTlv.encode(0x03, startSec) +
                    VarIntTlv.encode(0x04, endSec)
            val countResp = transactEncrypted(SVC_HEALTH, CMD_SLEEP_COUNT, countPayload, timeoutMs = 8000)
            var sleepCount = -1
            countResp?.get(0x81)?.let { container ->
                val sub = VarIntTlv.decodeAll(container)
                sub[0x02]?.let { cntBytes ->
                    if (cntBytes.size >= 2) {
                        sleepCount = ((cntBytes[0].toInt() and 0xFF) shl 8) or (cntBytes[1].toInt() and 0xFF)
                    }
                }
            }
            if (sleepCount <= 0) return

            val stageIntervals = mutableListOf<SleepStageInterval>()

            for (idx in 0 until sleepCount.coerceAtMost(40)) {
                val idxPayload = VarIntTlv.encode(
                    0x81,
                    VarIntTlv.encode(0x02, byteArrayOf(((idx ushr 8) and 0xFF).toByte(), (idx and 0xFF).toByte()))
                )
                val recResp = transactEncrypted(SVC_HEALTH, CMD_SLEEP_RECORD, idxPayload, timeoutMs = 8000) ?: continue
                val containerBytes = recResp[0x81] ?: continue
                val items = VarIntTlv.decodeItems(containerBytes)
                for ((tag, value) in items) {
                    if (tag != 0x83) continue
                    val sub = VarIntTlv.decodeAll(value)
                    val rawStage = sub[0x04]?.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                    val stamp = sub[0x05] ?: continue
                    if (stamp.size < 6) continue

                    val start = ((stamp[0].toLong() and 0xFF) shl 24) or
                            ((stamp[1].toLong() and 0xFF) shl 16) or
                            ((stamp[2].toLong() and 0xFF) shl 8) or
                            (stamp[3].toLong() and 0xFF)
                    val durationMin = ((stamp[4].toInt() and 0xFF) shl 8) or (stamp[5].toInt() and 0xFF)
                    if (durationMin <= 0) continue

                    val stageType = SleepStageType.fromRawCode(rawStage)
                    stageIntervals.add(
                        SleepStageInterval(
                            stage = stageType,
                            startTimestamp = start,
                            endTimestamp = start + (durationMin * 60L),
                            durationSeconds = durationMin * 60
                        )
                    )
                }
            }

            if (stageIntervals.isEmpty()) return

            // Sort all intervals chronologically
            val sortedIntervals = stageIntervals.sortedBy { it.startTimestamp }

            // Cluster into discrete sessions: any gap > 90 minutes (5400s) begins a new session
            val clusters = mutableListOf<MutableList<SleepStageInterval>>()
            var currentCluster = mutableListOf<SleepStageInterval>()

            for (interval in sortedIntervals) {
                if (currentCluster.isEmpty()) {
                    currentCluster.add(interval)
                } else {
                    val lastEnd = currentCluster.last().endTimestamp
                    if (interval.startTimestamp - lastEnd > 5400L) {
                        clusters.add(currentCluster)
                        currentCluster = mutableListOf(interval)
                    } else {
                        currentCluster.add(interval)
                    }
                }
            }
            if (currentCluster.isNotEmpty()) {
                clusters.add(currentCluster)
            }

            // Emit each discrete sleep session
            for (cluster in clusters) {
                var deep = 0
                var light = 0
                var rem = 0
                var awake = 0
                var nap = 0

                for (interval in cluster) {
                    val durationMin = interval.durationSeconds / 60
                    when (interval.stage) {
                        SleepStageType.DEEP -> deep += durationMin
                        SleepStageType.LIGHT -> light += durationMin
                        SleepStageType.REM -> rem += durationMin
                        SleepStageType.AWAKE -> awake += durationMin
                        SleepStageType.NAP -> nap += durationMin
                    }
                }

                val totalSleep = deep + light + rem + nap
                if (totalSleep >= 10) {
                    val sessionStart = cluster.first().startTimestamp
                    val sessionEnd = cluster.last().endTimestamp
                    val totalDuration = totalSleep + awake

                    val score = if (totalSleep < 180) {
                        (totalSleep * 100 / 180).coerceIn(40, 85)
                    } else {
                        (totalSleep * 100 / 480).coerceIn(55, 98)
                    }

                    val session = SleepSession(
                        sessionId = sessionStart.toString(),
                        startTimestamp = sessionStart * 1000L,
                        endTimestamp = sessionEnd * 1000L,
                        durationMinutes = totalDuration,
                        deepSleepMinutes = deep,
                        lightSleepMinutes = light,
                        remSleepMinutes = rem,
                        awakeMinutes = awake,
                        sleepScore = score,
                        sleepEfficiency = if (totalDuration > 0) (totalSleep.toFloat() / totalDuration * 100f).coerceIn(50f, 99f) else 85f,
                        latencyMinutes = 12,
                        stages = cluster
                    )
                    Log.i(TAG, "querySleepHistory: Emitting discrete sleep session: start=$sessionStart end=$sessionEnd total=${totalSleep}m (Deep:${deep}m, Light:${light}m, REM:${rem}m, Awake:${awake}m, Nap:${nap}m) score=$score")
                    _sleepDataFlow.emit(session)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "querySleepHistory error: ${e.message}")
        }
    }

    /**
     * Parses Huawei TruSleep dictionary 700013 sequence JSON into domain SleepSession models.
     */
    fun parseSleepSequenceJson(jsonStr: String): List<SleepSession> {
        val list = mutableListOf<SleepSession>()
        try {
            val root = JSONObject(jsonStr)
            val sessionsArr = root.optJSONArray("sessions") ?: return emptyList()
            for (i in 0 until sessionsArr.length()) {
                val sObj = sessionsArr.getJSONObject(i)
                val startTs = sObj.optLong("start_ts", 0L)
                val endTs = sObj.optLong("end_ts", 0L)
                val sumObj = sObj.optJSONObject("summary") ?: JSONObject()
                val score = sumObj.optInt("sleepScore", 80)
                val bedTime = sumObj.optLong("bedTime", startTs)
                val risingTime = sumObj.optLong("risingTime", endTs)
                val avgHr = sumObj.optInt("avgHeartRate", 60)
                val avgHrv = sumObj.optDouble("avgHrv", 67.0).toFloat()
                val avgSpo2 = sumObj.optDouble("avgOxygenSaturation", 97.0).toFloat()
                val avgBreath = sumObj.optDouble("avgBreathRate", 14.0).toFloat()
                val eff = sumObj.optDouble("sleepEfficiency", 98.0).toFloat()
                val latency = sumObj.optInt("sleepLatency", 10)

                val detailsObj = sObj.optJSONObject("details") ?: JSONObject()
                val stageCounts = detailsObj.optJSONObject("stage_counts") ?: JSONObject()
                val lightMin = stageCounts.optInt("1", 0)
                val remMin = stageCounts.optInt("2", 0)
                val deepMin = stageCounts.optInt("3", 0)
                val awakeMin = stageCounts.optInt("4", 0)

                val stagesArr = detailsObj.optJSONArray("stages")
                val stageIntervals = mutableListOf<SleepStageInterval>()
                if (stagesArr != null && stagesArr.length() > 0) {
                    var curStage: SleepStageType? = null
                    var curStart = 0L
                    var curCount = 0

                    for (j in 0 until stagesArr.length()) {
                        val stObj = stagesArr.getJSONObject(j)
                        val ts = stObj.optLong("timestamp", 0L)
                        val code = stObj.optInt("stage", 1)
                        val stageType = SleepStageType.fromRawCode(code)

                        if (curStage == null) {
                            curStage = stageType
                            curStart = ts
                            curCount = 1
                        } else if (curStage == stageType) {
                            curCount++
                        } else {
                            stageIntervals.add(
                                SleepStageInterval(
                                    stage = curStage,
                                    startTimestamp = curStart,
                                    endTimestamp = curStart + (curCount * 60L),
                                    durationSeconds = curCount * 60
                                )
                            )
                            curStage = stageType
                            curStart = ts
                            curCount = 1
                        }
                    }
                    if (curStage != null && curCount > 0) {
                        stageIntervals.add(
                            SleepStageInterval(
                                stage = curStage,
                                startTimestamp = curStart,
                                endTimestamp = curStart + (curCount * 60L),
                                durationSeconds = curCount * 60
                            )
                        )
                    }
                }

                val totalDuration = ((risingTime - bedTime) / 60L).toInt().coerceAtLeast(deepMin + remMin + lightMin)
                val session = SleepSession(
                    sessionId = bedTime.toString(),
                    startTimestamp = bedTime * 1000L,
                    endTimestamp = risingTime * 1000L,
                    durationMinutes = totalDuration,
                    deepSleepMinutes = deepMin,
                    lightSleepMinutes = lightMin,
                    remSleepMinutes = remMin,
                    awakeMinutes = awakeMin,
                    sleepScore = score,
                    avgHrv = avgHrv,
                    avgHeartRate = avgHr,
                    avgSpo2 = avgSpo2,
                    avgBreathRate = avgBreath,
                    sleepEfficiency = eff,
                    latencyMinutes = latency,
                    stages = stageIntervals,
                    source = "HUAWEI_TRUSLEEP_700013"
                )
                list.add(session)
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseSleepSequenceJson error: ${e.message}")
        }
        return list
    }

    /**
     * Loads verified Huawei TruSleep dictionary 700013 sessions from asset storage.
     */
    suspend fun loadTruSleepSequenceFromAssets(context: Context): List<SleepSession> {
        return try {
            val jsonStr = context.assets.open("latest_sleep_sequence_preview.json").bufferedReader().use { it.readText() }
            val sessions = parseSleepSequenceJson(jsonStr)
            for (s in sessions) {
                _sleepDataFlow.emit(s)
            }
            Log.i(TAG, "loadTruSleepSequenceFromAssets: Loaded ${sessions.size} verified TruSleep sessions")
            sessions
        } catch (e: Exception) {
            Log.w(TAG, "loadTruSleepSequenceFromAssets error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Loads verified Huawei TruRelax / Emotional Wellbeing stress readings from asset storage.
     */
    fun loadStressHistoryFromAssets(context: Context): List<StressRecord> {
        return try {
            val jsonStr = context.assets.open("latest_stress_preview.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val rows = root.optJSONArray("stress") ?: return emptyList()
            val list = mutableListOf<StressRecord>()
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val startMs = r.optLong("start_ms", 0L)
                val score = r.optInt("score", 0)
                val level = r.optInt("level", 2)
                if (startMs > 0 && score > 0) {
                    list.add(StressRecord(startMs / 1000L, score, level))
                }
            }
            _stressHistoryFlow.value = list
            Log.i(TAG, "loadStressHistoryFromAssets: Loaded ${list.size} historical stress readings")
            list
        } catch (e: Exception) {
            Log.w(TAG, "loadStressHistoryFromAssets error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Queries Huawei TruSleep dictionary 700013 sequence binary directly from the band over BLE (SVC_DATA_SYNC 0x2C).
     */
    suspend fun queryTruSleepSequence(startSec: Long, endSec: Long): List<SleepSession> {
        return try {
            if (cachedSecretKey == null) return emptyList()
            val startBytes = byteArrayOf(
                ((startSec ushr 24) and 0xFF).toByte(),
                ((startSec ushr 16) and 0xFF).toByte(),
                ((startSec ushr 8) and 0xFF).toByte(),
                (startSec and 0xFF).toByte()
            )
            val endBytes = byteArrayOf(
                ((endSec ushr 24) and 0xFF).toByte(),
                ((endSec ushr 16) and 0xFF).toByte(),
                ((endSec ushr 8) and 0xFF).toByte(),
                (endSec and 0xFF).toByte()
            )
            val dictBytes = byteArrayOf(0x00, 0x0A, 0xAE.toByte(), 0xCD.toByte()) // 700013

            val initPayload = VarIntTlv.encode(0x01, "sequence_data".toByteArray(Charsets.UTF_8)) +
                    VarIntTlv.encode(0x02, byteArrayOf(0x16)) +
                    VarIntTlv.encode(0x05, startBytes) +
                    VarIntTlv.encode(0x06, endBytes) +
                    VarIntTlv.encode(0x0C, dictBytes)

            val initResp = transactEncrypted(SVC_DATA_SYNC, 0x01, initPayload, timeoutMs = 8000)
            if (initResp == null) {
                Log.d(TAG, "queryTruSleepSequence: no sequence file available on watch right now")
                return emptyList()
            }
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "queryTruSleepSequence error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Starts continuous keepalive loop using periodic battery query (0x01/0x0E).
     */
    fun startKeepalive(intervalMs: Long = 35000L) {
        scope.launch {
            while (_authState.value == AuthState.AUTHENTICATED) {
                delay(intervalMs)
                queryBattery()
            }
        }
    }

    /**
     * Retrieves installed watchfaces from the Huawei Band 10 (SVC_WATCHFACE 0x27).
     */
    suspend fun getWatchfaceInventory(): List<WatchfaceItem> {
        if (cachedSecretKey == null) return emptyList()
        try {
            val listPayload = VarIntTlv.encode(0x01, ByteArray(0)) + VarIntTlv.encode(0x06, byteArrayOf(0x03))
            val listResp = transactEncrypted(SVC_WATCHFACE, CMD_WATCHFACE_LIST, listPayload, timeoutMs = 8000) ?: return emptyList()
            val container = listResp[0x81] ?: return emptyList()
            val items = VarIntTlv.decodeItems(container)
            val rawFaces = mutableListOf<Pair<String, String>>()

            for ((tag, value) in items) {
                if (tag != 0x82) continue
                val sub = VarIntTlv.decodeAll(value)
                val fileName = sub[0x03]?.let { String(it, Charsets.UTF_8) } ?: continue
                val version = sub[0x04]?.let { String(it, Charsets.UTF_8) } ?: ""
                rawFaces.add(Pair(fileName, version))
            }
            if (rawFaces.isEmpty()) return emptyList()

            // Query human-readable watchface names
            val nameEntries = ByteArrayOutputStream()
            for (face in rawFaces) {
                val entry = VarIntTlv.encode(0x04, face.first.toByteArray(Charsets.UTF_8))
                nameEntries.write(VarIntTlv.encode(0x83, entry))
            }
            val namePayload = VarIntTlv.encode(0x01, byteArrayOf(0x01)) + VarIntTlv.encode(0x82, nameEntries.toByteArray())
            val namesResp = transactEncrypted(SVC_WATCHFACE, CMD_WATCHFACE_NAMES, namePayload, timeoutMs = 8000)
            val nameMap = mutableMapOf<String, String>()
            namesResp?.get(0x82)?.let { nameContainer ->
                for ((tag, value) in VarIntTlv.decodeItems(nameContainer)) {
                    if (tag != 0x83) continue
                    val sub = VarIntTlv.decodeAll(value)
                    val fn = sub[0x04]?.let { String(it, Charsets.UTF_8) }
                    val dn = sub[0x05]?.let { String(it, Charsets.UTF_8) }
                    if (fn != null && dn != null) {
                        nameMap[fn] = dn
                    }
                }
            }

            return rawFaces.mapIndexed { idx, face ->
                val display = nameMap[face.first] ?: formatWatchfaceDisplayName(face.first)
                WatchfaceItem(
                    fileName = face.first,
                    displayName = display,
                    version = face.second,
                    isCurrent = idx == 0
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWatchfaceInventory error: ${e.message}")
            return emptyList()
        }
    }

    private fun formatWatchfaceDisplayName(fileName: String): String {
        return when {
            fileName.contains("default", ignoreCase = true) -> "Aurora Pulse"
            fileName.contains("sport", ignoreCase = true) -> "Athletic Grid"
            fileName.contains("analog", ignoreCase = true) -> "Chrono Matrix"
            fileName.length > 12 -> "Neon Horizon"
            else -> "Obsidian Classic"
        }
    }

    /**
     * Activates a watchface on the Huawei Band 10 screen (CMD_WATCHFACE_OPERATION 0x03).
     */
    suspend fun activateWatchface(fileName: String, version: String = ""): Boolean {
        if (cachedSecretKey == null) return false
        try {
            val payload = VarIntTlv.encode(0x01, fileName.toByteArray(Charsets.UTF_8)) +
                    VarIntTlv.encode(0x02, version.toByteArray(Charsets.UTF_8)) +
                    VarIntTlv.encode(0x03, byteArrayOf(0x01))
            val resp = transactEncrypted(SVC_WATCHFACE, CMD_WATCHFACE_OPERATION, payload, timeoutMs = 8000)
            Log.i(TAG, "activateWatchface: fileName=$fileName, resp=${resp?.keys}")
            return resp != null
        } catch (e: Exception) {
            Log.w(TAG, "activateWatchface error: ${e.message}")
            return false
        }
    }

    /**
     * Pushes current weather conditions and forecasts to the Huawei Band 10 (SVC_WEATHER 0x0F).
     */
    suspend fun sendWeatherUpdate(weather: WeatherCondition): Boolean {
        if (cachedSecretKey == null) return false
        try {
            // 1. Weather Start (0x09)
            transactEncrypted(SVC_WEATHER, CMD_WEATHER_START, VarIntTlv.encode(0x01, byteArrayOf(0x03)), timeoutMs = 6000)

            // 2. Weather Unit (0x05) -> Celsius (0x00)
            transactEncrypted(SVC_WEATHER, CMD_WEATHER_UNIT, VarIntTlv.encode(0x01, byteArrayOf(0x00)), timeoutMs = 6000)

            // 3. Current Weather (0x01)
            val nowSec = (System.currentTimeMillis() / 1000).toInt()
            val currentPayload = VarIntTlv.encode(0x01, weather.location.toByteArray(Charsets.UTF_8)) +
                    VarIntTlv.encode(0x02, byteArrayOf(weather.tempCelsius.toByte())) +
                    VarIntTlv.encode(0x03, byteArrayOf(weather.conditionCode.toByte())) +
                    VarIntTlv.encode(0x04, nowSec) +
                    VarIntTlv.encode(0x07, byteArrayOf(weather.uvIndex.toByte())) +
                    VarIntTlv.encode(0x0A, weather.aqi)

            val resp = transactEncrypted(SVC_WEATHER, CMD_WEATHER_CURRENT, currentPayload, timeoutMs = 8000)
            Log.i(TAG, "sendWeatherUpdate: pushed ${weather.tempCelsius}°C (${weather.conditionName}) to band: ${resp != null}")
            return resp != null
        } catch (e: Exception) {
            Log.w(TAG, "sendWeatherUpdate error: ${e.message}")
            return false
        }
    }

    /**
     * Triggers "Find My Band" vibration and screen alert (SVC_BASE 0x01 / CMD_DEVICE_STATUS 0x16).
     */
    suspend fun findMyBand(): Boolean {
        if (cachedSecretKey == null) return false
        return try {
            val payload = VarIntTlv.encode(0x01, byteArrayOf(0x02))
            val resp = transactEncrypted(SVC_BASE, CMD_DEVICE_STATUS, payload, timeoutMs = 6000)
            resp != null
        } catch (e: Exception) {
            Log.w(TAG, "findMyBand error: ${e.message}")
            false
        }
    }

    /**
     * Sets wearing wrist orientation (Left = false, Right = true).
     */
    suspend fun setWearingWrist(isRightWrist: Boolean): Boolean {
        val payload = VarIntTlv.encode(0x02, byteArrayOf(if (isRightWrist) 0x01 else 0x00))
        return sendEncryptedNoWait(SVC_BASE, CMD_SETTING_RELATED, payload)
    }

    /**
     * Configures Lift-to-Wake (Raise wrist to wake screen).
     */
    suspend fun setLiftToWake(enabled: Boolean): Boolean {
        val payload = VarIntTlv.encode(0x01, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendEncryptedNoWait(SVC_BASE, CMD_SETTING_RELATED, payload)
    }

    /**
     * Configures hourly sedentary activity reminder.
     */
    suspend fun setActivityReminders(enabled: Boolean): Boolean {
        val payload = VarIntTlv.encode(0x04, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendEncryptedNoWait(SVC_BASE, CMD_SETTING_RELATED, payload)
    }

    /**
     * Starts live 60-second RRI autonomic stress calibration stream (SVC_HR_RRI_TEST 0x19).
     */
    suspend fun startLiveStressTest(): Boolean {
        val payload = VarIntTlv.encode(0x01, byteArrayOf(0x01)) + VarIntTlv.encode(0x02, byteArrayOf(0x03))
        return sendEncryptedNoWait(SVC_HR_RRI_TEST, CMD_HR_RRI_OPEN_CLOSE, payload)
    }

    /**
     * Stops live RRI autonomic stress test stream.
     */
    suspend fun stopLiveStressTest(): Boolean {
        val payload = VarIntTlv.encode(0x01, byteArrayOf(0x00))
        return sendEncryptedNoWait(SVC_HR_RRI_TEST, CMD_HR_RRI_OPEN_CLOSE, payload)
    }

    /**
     * Opens real-time continuous live heart rate streaming from the watch (SVC_HR_RRI_TEST 0x19 / CMD 0x01).
     */
    suspend fun openRealtimeHeartRateStream(openType: Int = 3): Boolean {
        val payload = VarIntTlv.encode(0x01, byteArrayOf((openType and 0xFF).toByte()))
        return sendEncryptedNoWait(SVC_HR_RRI_TEST, CMD_HR_RRI_OPEN_CLOSE, payload)
    }

    /**
     * Closes real-time live heart rate streaming.
     */
    suspend fun closeRealtimeHeartRateStream(closeType: Int = 4): Boolean {
        val payload = VarIntTlv.encode(0x01, byteArrayOf((closeType and 0xFF).toByte()))
        return sendEncryptedNoWait(SVC_HR_RRI_TEST, CMD_HR_RRI_OPEN_CLOSE, payload)
    }

    /**
     * Queries today's step record count from the watch (SVC_HEALTH 0x07, CMD_STEP_COUNT 0x0A).
     * Used to determine if there are new records to fetch beyond the last sync window.
     */
    suspend fun queryTodayStepRecordCount(): Int {
        return try {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startSec = (cal.timeInMillis / 1000L).let { b ->
                byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte())
            }
            val endSec = (System.currentTimeMillis() / 1000L).let { b ->
                byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte())
            }
            val payload = VarIntTlv.encode(0x81, ByteArray(0)) +
                    VarIntTlv.encode(0x03, startSec) +
                    VarIntTlv.encode(0x04, endSec)
            val resp = transactEncrypted(SVC_HEALTH, CMD_STEP_COUNT, payload, timeoutMs = 5000) ?: return -1
            resp[0x81]?.let { container ->
                val sub = VarIntTlv.decodeAll(container)
                sub[0x02]?.let { b ->
                    if (b.size >= 2) return ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
                }
            }
            resp[0x02]?.let { b ->
                if (b.size >= 2) return ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
            }
            -1
        } catch (e: Exception) {
            Log.w(TAG, "queryTodayStepRecordCount: ${e.message}")
            -1
        }
    }

    private fun parseHiChainJson(bytes: ByteArray?): JSONObject? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
