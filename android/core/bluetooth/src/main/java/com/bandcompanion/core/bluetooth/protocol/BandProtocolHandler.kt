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
        const val CMD_SECURITY_NEG = 0x33
        const val CMD_PIN_REQUEST = 0x2C
        const val CMD_HICHAIN_AUTH = 0x28
        const val CMD_PHONE_INFO = 0x10
        const val CMD_PRODUCT = 0x07
        const val CMD_BATTERY = 0x08
        const val CMD_TIME_SYNC = 0x02
        const val CMD_ACCEPT_AGREEMENT = 0x30
        const val CMD_CONNECT_STATUS = 0x35
        const val CMD_SETUP_DEVICE_STATUS = 0x3E
        const val CMD_REVERSE_CAPABILITIES = 0x3F

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
        }
    }

    private suspend fun handlePhoneInfoRequest(tlvs: Map<Int, ByteArray>) {
        val decrypted = decryptTransactionTlvs(tlvs)
        if (decrypted.containsKey(0x7F)) {
            Log.d(TAG, "PhoneInfo ACK received from band")
            return
        }
        val requestedTags = decrypted.keys.map { it.toByte() }.toByteArray()
        val reply = buildPhoneInfoResponse(requestedTags)
        Log.i(TAG, "handlePhoneInfoRequest: replying to CMD_PHONE_INFO (${reply.size} bytes)...")
        val encryptedReply = encryptTransactionTlv(reply)
        val frames = Lpv2Frame.makeFrames(SVC_BASE, CMD_PHONE_INFO, encryptedReply, sliceSize)
        for (f in frames) {
            sendRawBytes(f)
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

        if (!forceFirstAuth && storedAuthKeyHex != null) {
            cachedAuthKey = HiChain3Crypto.hexToBytes(storedAuthKeyHex)
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
        val key = cachedSecretKey ?: throw IllegalStateException("Cannot encrypt transaction: cachedSecretKey is null")
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
        val encryptedPayload = encryptTransactionTlv(plainPayload)
        val resp = transact(serviceId, commandId, encryptedPayload, timeoutMs) ?: return null
        return decryptTransactionTlvs(resp.tlvs)
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
            val encryptedPayload = encryptTransactionTlv(plainPayload)
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
     * 2. Time Sync (CMD_TIME_SYNC 0x01/0x02) with timezone offset
     * 3. Battery query (CMD_BATTERY 0x01/0x08)
     * 4. Mandatory legal agreement acceptance (CMD_ACCEPT_AGREEMENT 0x01/0x30)
     * 5. Reverse capabilities announcement (CMD_REVERSE_CAPABILITIES 0x01/0x3F)
     * 6. Setup device status (CMD_SETUP_DEVICE_STATUS 0x01/0x3E) via sendEncryptedNoWait (no blocking)
     * 7. Final CMD_CONNECT_STATUS (0x01/0x35) -> Activates watch face and dismisses pairing UI on band!
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

        // 2. Time Sync (svc=0x01, cmd=0x02)
        try {
            val nowSec = (System.currentTimeMillis() / 1000).toInt()
            val tzBytes = getTimezoneOffsetBytes()
            val timePayload = VarIntTlv.encode(0x01, nowSec) + VarIntTlv.encode(0x02, tzBytes)
            transactEncrypted(SVC_BASE, CMD_TIME_SYNC, timePayload, timeoutMs = 5000)
        } catch (e: Exception) {
            Log.w(TAG, "Time sync error: ${e.message}")
        }

        // 3. Battery query (svc=0x01, cmd=0x08)
        queryBattery()

        // 4. Accept mandatory agreements (svc=0x01, cmd=0x30)
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

        // 5. Send Reverse Capabilities (svc=0x01, cmd=0x3F)
        Log.i(TAG, "executePostAuthBootstrap: sending CMD_REVERSE_CAPABILITIES (cmd=0x3F)...")
        try {
            val revCapPayload = VarIntTlv.encode(0x01, byteArrayOf(0xFD.toByte(), 0xF7.toByte(), 0x73, 0x7A))
            transactEncrypted(SVC_BASE, CMD_REVERSE_CAPABILITIES, revCapPayload, timeoutMs = 8000)
        } catch (e: Exception) {
            Log.w(TAG, "CMD_REVERSE_CAPABILITIES error: ${e.message}")
        }

        // 6. Setup Device Status (svc=0x01, cmd=0x3E) via sendEncryptedNoWait (matches tools/connect.py)
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

        // 7. Send CONNECT_STATUS (svc=0x01, cmd=0x35) -> Triggers watch screen to display active watch face!
        Log.i(TAG, "executePostAuthBootstrap: sending CMD_CONNECT_STATUS (activate watch face)...")
        val connectStatusPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
        val connectResp = transactEncrypted(SVC_BASE, CMD_CONNECT_STATUS, connectStatusPayload, timeoutMs = 5000)
        Log.i(TAG, "executePostAuthBootstrap: CMD_CONNECT_STATUS response: ${connectResp?.keys}")

        _authState.value = AuthState.AUTHENTICATED
        Log.i(TAG, "executePostAuthBootstrap: bootstrap complete! Activation signal sent.")
        return true
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
     * Queries minute-level fitness samples (steps, HR, SpO2).
     */
    suspend fun queryFitnessHistory(startTime: Long, endTime: Long): List<SampleMetric> {
        val startSec = (startTime / 1000).toInt()
        val endSec = (endTime / 1000).toInt()

        val tlv1 = VarIntTlv.encode(0x01, startSec)
        val tlv2 = VarIntTlv.encode(0x02, endSec)
        val payload = tlv1 + tlv2

        val tlvs = if (cachedSecretKey != null) {
            transactEncrypted(SVC_FITNESS, CMD_FITNESS_HISTORY, payload, timeoutMs = 12000)
        } else {
            transact(SVC_FITNESS, CMD_FITNESS_HISTORY, payload, timeoutMs = 12000)?.tlvs
        } ?: return emptyList()

        val samples = mutableListOf<SampleMetric>()
        tlvs[0x03]?.let { recordBytes ->
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
