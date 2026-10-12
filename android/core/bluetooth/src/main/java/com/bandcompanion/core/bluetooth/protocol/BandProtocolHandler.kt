package com.bandcompanion.core.bluetooth.protocol

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
import org.json.JSONObject
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
        const val SVC_BASE = 0x01
        const val CMD_LINK_PARAMS = 0x01
        const val CMD_SECURITY_NEG = 0x33
        const val CMD_PIN_REQUEST = 0x2C
        const val CMD_HICHAIN_AUTH = 0x28
        const val CMD_PHONE_INFO = 0x04
        const val CMD_BATTERY = 0x0E
        const val CMD_TIME_SYNC = 0x02

        const val SVC_FITNESS = 0x02
        const val CMD_FITNESS_HISTORY = 0x03

        const val SVC_DATA_SYNC = 0x2C
        const val CMD_DATA_SYNC = 0x01
        const val DICT_SLEEP_DETAILS = 700013
    }

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

    private val _fitnessDataFlow = MutableSharedFlow<List<SampleMetric>>()
    val fitnessDataFlow = _fitnessDataFlow.asSharedFlow()

    private val _sleepDataFlow = MutableSharedFlow<SleepSession>()
    val sleepDataFlow = _sleepDataFlow.asSharedFlow()

    private var serverNonce: ByteArray? = null
    private var clientNonce = ByteArray(16)
    private var sliceSize: Int = 244
    private var sessionKey: ByteArray? = null
    var cachedSecretKey: ByteArray? = null
    var cachedAuthKey: ByteArray? = null

    private val random = SecureRandom()

    init {
        random.nextBytes(clientNonce)
    }

    /**
     * Feeds incoming notification bytes from characteristic 0000fe02.
     */
    fun onRawBytesReceived(bytes: ByteArray) {
        val packet = reassembler.feed(bytes) ?: return
        handleParsedPacket(packet)
    }

    private fun handleParsedPacket(packet: Lpv2Frame.ParsedPacket) {
        val key = (packet.serviceId shl 8) or packet.commandId
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
        }
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

        val frame = Lpv2Frame.makeFrame(serviceId, commandId, payload)
        val sent = sendRawBytes(frame)
        if (!sent) {
            responseDeferreds.remove(key)
            return null
        }

        return withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
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
        response.tlvs[0x04]?.let { sliceBytes ->
            if (sliceBytes.size >= 2) {
                sliceSize = ((sliceBytes[0].toInt() and 0xFF) shl 8) or (sliceBytes[1].toInt() and 0xFF)
            }
        }
        _authState.value = AuthState.LINK_PARAMS_NEGOTIATED
        return true
    }

    /**
     * Executes Security Negotiation (Cmd 0x01/0x33).
     */
    suspend fun negotiateSecurity(androidId: String = "25EB96E6DA6F73D02F596631EAA295CD"): Int? {
        val tlv1 = VarIntTlv.encode(0x01, byteArrayOf(0x04)) // authMode = 4
        val tlv2 = VarIntTlv.encode(0x02, androidId)
        val payload = tlv1 + tlv2

        val response = transact(SVC_BASE, CMD_SECURITY_NEG, payload) ?: return null
        return response.tlvs[0x01]?.let {
            if (it.isNotEmpty()) it[0].toInt() and 0xFF else null
        }
    }

    /**
     * Performs fast reconnect with previously persisted secret_key.
     */
    suspend fun authenticateReconnect(secretKeyHex: String): Boolean {
        val secretKey = HiChain3Crypto.hexToBytes(secretKeyHex)
        val seed = ByteArray(16).also { random.nextBytes(it) }
        val psk = HiChain3Crypto.derivePskReconnect(secretKey, seed)

        val randSelf = ByteArray(16).also { random.nextBytes(it) }
        val randSelfHex = HiChain3Crypto.bytesToHex(randSelf)

        // Step 1: Start HiChain3 with operationCode = 0x02
        val step1Json = JSONObject().apply {
            put("message", 1)
            put("payload", JSONObject().apply {
                put("operationCode", 2)
                put("challenge", randSelfHex)
                put("groupAuthType", 1)
                put("peerAuthId", "BAND_COMPANION")
            })
        }

        val step1Tlv = VarIntTlv.encode(0x01, step1Json.toString().toByteArray())
        val resp1 = transact(SVC_BASE, CMD_HICHAIN_AUTH, step1Tlv) ?: return false

        val resp1Json = parseHiChainJson(resp1.tlvs[0x01]) ?: return false
        val payload1 = resp1Json.optJSONObject("payload") ?: return false
        val randPeerHex = payload1.optString("challenge")
        val randPeer = HiChain3Crypto.hexToBytes(randPeerHex)

        // Derive session key
        val derivedSessionKey = HiChain3Crypto.deriveSessionKey(psk, randSelf, randPeer)
        sessionKey = derivedSessionKey

        // Step 3: Send Auth Token HMAC
        val hmacToken = HiChain3Crypto.computeHmacHex(derivedSessionKey, randPeer)
        val step3Json = JSONObject().apply {
            put("message", 3)
            put("payload", JSONObject().apply {
                put("authData", hmacToken)
            })
        }
        val step3Tlv = VarIntTlv.encode(0x01, step3Json.toString().toByteArray())
        val resp3 = transact(SVC_BASE, CMD_HICHAIN_AUTH, step3Tlv) ?: return false

        // Derive final return key
        val salt = ByteArray(randSelf.size + randPeer.size)
        System.arraycopy(randSelf, 0, salt, 0, randSelf.size)
        System.arraycopy(randPeer, 0, salt, randSelf.size, randPeer.size)
        val finalKey = HiChain3Crypto.deriveReturnKey(derivedSessionKey, salt)
        cachedSecretKey = finalKey

        _authState.value = AuthState.AUTHENTICATED
        return true
    }

    /**
     * Executes Post-Auth Bootstrap:
     * - Dispatches Phone Info (0x01/0x04)
     * - Dispatches Time Sync (0x01/0x02)
     * - Queries Battery Percentage (0x01/0x0E)
     */
    suspend fun executePostAuthBootstrap(): Boolean {
        // 1. Time Sync
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        val timePayload = VarIntTlv.encode(0x01, nowSec)
        transact(SVC_BASE, CMD_TIME_SYNC, timePayload, timeoutMs = 3000)

        // 2. Battery query
        queryBattery()
        return true
    }

    /**
     * Queries battery percentage (Cmd 0x01/0x0E).
     */
    suspend fun queryBattery(): Int? {
        val resp = transact(SVC_BASE, CMD_BATTERY, ByteArray(0), timeoutMs = 3000) ?: return _batteryLevel.value
        resp.tlvs[0x01]?.let {
            if (it.isNotEmpty()) {
                val bat = it[0].toInt() and 0xFF
                _batteryLevel.value = bat
                return bat
            }
        }
        return _batteryLevel.value
    }

    /**
     * Queries minute-level fitness samples (steps, HR, SpO2).
     */
    suspend fun queryFitnessHistory(startTime: Long, endTime: Long): List<SampleMetric> {
        val startSec = (startTime / 1000).toInt()
        val endSec = (endTime / 1000).toInt()

        val tlv1 = VarIntTlv.encode(0x01, startSec)
        val tlv2 = VarIntTlv.encode(0x02, endSec)
        val payload = tlv1 + tlv2

        val resp = transact(SVC_FITNESS, CMD_FITNESS_HISTORY, payload, timeoutMs = 12000) ?: return emptyList()

        val samples = mutableListOf<SampleMetric>()
        resp.tlvs[0x03]?.let { recordBytes ->
            // Parse sample records
            var offset = 0
            while (offset + 6 <= recordBytes.size) {
                val ts = ((recordBytes[offset].toLong() and 0xFF) shl 24) or
                        ((recordBytes[offset + 1].toLong() and 0xFF) shl 16) or
                        ((recordBytes[offset + 2].toLong() and 0xFF) shl 8) or
                        (recordBytes[offset + 3].toLong() and 0xFF)
                val hr = recordBytes[offset + 4].toInt() and 0xFF
                val steps = recordBytes[offset + 5].toInt() and 0xFF

                val validHr = if (hr in 35..230) hr else null
                samples.add(SampleMetric(timestamp = ts, steps = steps, heartRate = validHr))
                offset += 6
            }
        }

        if (samples.isNotEmpty()) {
            _fitnessDataFlow.emit(samples)
        }
        return samples
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

    private fun parseHiChainJson(bytes: ByteArray?): JSONObject? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
