package com.bandcompanion.core.bluetooth.protocol

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
        private const val TAG = "BandProtocol"
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
        val hex = if (bytes.size <= 24) HiChain3Crypto.bytesToHex(bytes) else HiChain3Crypto.bytesToHex(bytes.copyOf(24)) + "..."
        Log.d(TAG, "onRawBytesReceived: ${bytes.size} bytes: $hex")
        val packet = reassembler.feed(bytes) ?: return
        Log.i(TAG, "Reassembled complete packet: svc=${packet.serviceId}, cmd=${packet.commandId}, tlvs=${packet.tlvs.keys}")
        handleParsedPacket(packet)
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
        var sent = false
        for (attempt in 1..4) {
            sent = sendRawBytes(frame)
            if (sent) break
            Log.w(TAG, "transact: sendRawBytes failed on attempt $attempt for svc=$serviceId cmd=$commandId, retrying in 100ms...")
            delay(100L)
        }

        if (!sent) {
            Log.e(TAG, "transact: FAILED to send frame for svc=$serviceId cmd=$commandId after 4 attempts!")
            responseDeferreds.remove(key)
            return null
        }

        Log.i(TAG, "transact: sent frame for svc=$serviceId cmd=$commandId (${frame.size}B). Waiting for reply (timeout=${timeoutMs}ms)...")
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

    /**
     * Executes the complete 4-round HiChain3 First-Authentication flow.
     * Triggers the confirmation checkmark on the watch and invokes [onUserPrompt]
     * so the UI can prompt the user to tap the checkmark.
     */
    suspend fun authenticateFirstAuth(
        onStepUpdate: (String) -> Unit = {},
        onUserPrompt: suspend () -> Unit
    ): Boolean {
        onStepUpdate("Negotiating link parameters...")
        if (!exchangeLinkParams()) {
            return false
        }

        onStepUpdate("Negotiating security protocol...")
        negotiateSecurity()

        onStepUpdate("Requesting pairing PIN...")
        val pinBytes = getPinCode() ?: return false
        val requestId = System.currentTimeMillis()
        val seed = ByteArray(32).also { random.nextBytes(it) }
        val randSelf = ByteArray(16).also { random.nextBytes(it) }
        val randSelfHex = HiChain3Crypto.bytesToHex(randSelf)
        val androidIdHex = "25EB96E6DA6F73D02F596631EAA295CD"
        val authIdSelfHex = HiChain3Crypto.bytesToHex(androidIdHex.toByteArray(Charsets.UTF_8))

        // ── Round 1 ──
        val p1 = JSONObject().apply {
            put("isoSalt", randSelfHex)
            put("peerAuthId", authIdSelfHex)
            put("operationCode", 1)
            put("seed", HiChain3Crypto.bytesToHex(seed))
            put("peerUserType", 0)
            put("version", JSONObject().apply {
                put("minVersion", "1.0.0")
                put("currentVersion", "2.0.16")
            })
        }
        val step1Json = JSONObject().apply {
            put("authForm", 0)
            put("payload", p1)
            put("groupAndModuleVersion", "2.0.1")
            put("message", 1)
            put("requestId", requestId.toString())
            put("groupId", HiChain3Crypto.GROUP_ID)
            put("groupName", "health_group_name")
            put("groupOp", 2)
            put("groupType", 256)
            put("peerDeviceId", androidIdHex)
            put("connDeviceId", androidIdHex)
            put("appId", "com.huawei.health")
            put("ownerName", "")
        }

        val step1Tlv = VarIntTlv.encode(0x01, step1Json.toString().toByteArray()) +
                VarIntTlv.encode(0x02, byteArrayOf(0x01))

        // Prompt user immediately when sending Round 1: the watch screen shows the checkmark right now!
        onStepUpdate("👉 TAP CHECKMARK ON YOUR BAND!")
        onUserPrompt()

        // Band waits up to 90 seconds for user to tap the checkmark on the watch screen
        val resp1 = transact(SVC_BASE, CMD_HICHAIN_AUTH, step1Tlv, timeoutMs = 90000) ?: return false

        val resp1Json = parseHiChainJson(resp1.tlvs[0x01]) ?: return false
        val p1Resp = resp1Json.optJSONObject("payload") ?: return false

        val randPeerHex = p1Resp.optString("isoSalt")
        val randPeer = HiChain3Crypto.hexToBytes(randPeerHex)
        val authIdPeer = HiChain3Crypto.hexToBytes(p1Resp.optString("peerAuthId"))

        // Derive PSK
        val psk = HiChain3Crypto.derivePskFirstAuth(pinBytes, seed)

        // ── Round 2 ──
        onStepUpdate("Verifying authentication token...")
        val selfToken = HiChain3Crypto.hmacSha256(
            psk,
            randSelf + randPeer + authIdPeer + HiChain3Crypto.hexToBytes(authIdSelfHex)
        )
        val p2 = JSONObject().apply {
            put("peerAuthId", authIdSelfHex)
            put("token", HiChain3Crypto.bytesToHex(selfToken))
            put("version", JSONObject().apply {
                put("minVersion", "1.0.0")
                put("currentVersion", "2.0.16")
            })
        }
        val step2Json = JSONObject().apply {
            put("authForm", 0)
            put("payload", p2)
            put("groupAndModuleVersion", "2.0.1")
            put("message", 2)
            put("requestId", requestId.toString())
            put("groupId", HiChain3Crypto.GROUP_ID)
            put("groupName", "health_group_name")
            put("groupOp", 2)
            put("groupType", 256)
            put("peerDeviceId", androidIdHex)
            put("connDeviceId", androidIdHex)
            put("appId", "com.huawei.health")
            put("ownerName", "")
        }
        val step2Tlv = VarIntTlv.encode(0x01, step2Json.toString().toByteArray()) +
                VarIntTlv.encode(0x02, byteArrayOf(0x01))
        val resp2 = transact(SVC_BASE, CMD_HICHAIN_AUTH, step2Tlv, timeoutMs = 20000) ?: return false

        // ── Round 3 ──
        onStepUpdate("Deriving encryption keys...")
        val salt = randSelf + randPeer
        val sessKey = HiChain3Crypto.hkdfSha256(psk, salt, "hichain_iso_session_key".toByteArray(), 32)
        sessionKey = sessKey

        val nonce3 = ByteArray(12).also { random.nextBytes(it) }
        val challenge3 = ByteArray(16).also { random.nextBytes(it) }
        val encData3 = HiChain3Crypto.aesGcmEncrypt(challenge3, sessKey, nonce3, "hichain_iso_exchange".toByteArray())

        val p3 = JSONObject().apply {
            put("nonce", HiChain3Crypto.bytesToHex(nonce3))
            put("encData", HiChain3Crypto.bytesToHex(encData3))
            put("version", JSONObject().apply {
                put("minVersion", "1.0.0")
                put("currentVersion", "2.0.16")
            })
        }
        val step3Json = JSONObject().apply {
            put("authForm", 0)
            put("payload", p3)
            put("groupAndModuleVersion", "2.0.1")
            put("message", 3)
            put("requestId", requestId.toString())
            put("groupId", HiChain3Crypto.GROUP_ID)
            put("groupName", "health_group_name")
            put("groupOp", 2)
            put("groupType", 256)
            put("peerDeviceId", androidIdHex)
            put("connDeviceId", androidIdHex)
            put("appId", "com.huawei.health")
            put("ownerName", "")
        }
        val step3Tlv = VarIntTlv.encode(0x01, step3Json.toString().toByteArray()) +
                VarIntTlv.encode(0x02, byteArrayOf(0x01))
        val resp3 = transact(SVC_BASE, CMD_HICHAIN_AUTH, step3Tlv, timeoutMs = 30000) ?: return false
        val p3Resp = parseHiChainJson(resp3.tlvs[0x01])?.optJSONObject("payload") ?: return false

        val noncePeer3 = HiChain3Crypto.hexToBytes(p3Resp.optString("nonce"))
        val encAuthTok = HiChain3Crypto.hexToBytes(p3Resp.optString("encAuthToken"))
        val authTok = HiChain3Crypto.aesGcmDecrypt(encAuthTok, sessKey, noncePeer3, challenge3)
        cachedAuthKey = authTok

        // ── Round 4 ──
        onStepUpdate("Finalizing pairing bond...")
        val nonce4 = ByteArray(12).also { random.nextBytes(it) }
        val encAuthTokSelf = HiChain3Crypto.aesGcmEncrypt(challenge3, sessKey, nonce4, challenge3)
        val p4 = JSONObject().apply {
            put("nonce", HiChain3Crypto.bytesToHex(nonce4))
            put("encAuthToken", HiChain3Crypto.bytesToHex(encAuthTokSelf))
            put("version", JSONObject().apply {
                put("minVersion", "1.0.0")
                put("currentVersion", "2.0.16")
            })
        }
        val step4Json = JSONObject().apply {
            put("authForm", 0)
            put("payload", p4)
            put("groupAndModuleVersion", "2.0.1")
            put("message", 4)
            put("requestId", requestId.toString())
            put("groupId", HiChain3Crypto.GROUP_ID)
            put("groupName", "health_group_name")
            put("groupOp", 2)
            put("groupType", 256)
            put("peerDeviceId", androidIdHex)
            put("connDeviceId", androidIdHex)
            put("appId", "com.huawei.health")
            put("ownerName", "")
        }
        val step4Tlv = VarIntTlv.encode(0x01, step4Json.toString().toByteArray()) +
                VarIntTlv.encode(0x02, byteArrayOf(0x01))
        transact(SVC_BASE, CMD_HICHAIN_AUTH, step4Tlv, timeoutMs = 20000)

        // Derive permanent session return key
        val returnKey = HiChain3Crypto.deriveReturnKey(sessKey, salt)
        cachedSecretKey = returnKey

        onStepUpdate("Paired successfully!")
        _authState.value = AuthState.AUTHENTICATED
        return true
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
