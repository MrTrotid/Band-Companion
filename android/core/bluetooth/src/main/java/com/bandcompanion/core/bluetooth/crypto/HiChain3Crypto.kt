package com.bandcompanion.core.bluetooth.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic operations and key derivations for Huawei HiChain3 authentication.
 */
object HiChain3Crypto {

    val DIGEST_SECRET_1: ByteArray = hexToBytes("70FB6C24035FDB552F38898AEEDE3F69")
    val DIGEST_SECRET_2: ByteArray = hexToBytes("93ACDEF76ACB09857DBFE5261AABCD78")
    val DIGEST_SECRET_3: ByteArray = hexToBytes("9C2763A9CCE134766DE3FF6118200550")

    const val GROUP_ID: String = "7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989"

    fun hexToBytes(hex: String): ByteArray {
        val s = hex.replace(" ", "")
        val result = ByteArray(s.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = s.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /**
     * Decrypts the raw pairing PIN code sent by the band on svc=0x01, cmd=0x2C.
     */
    fun decryptPinCode(encMethod: Int, authVersion: Int, msg: ByteArray, iv: ByteArray): ByteArray {
        val key = when (authVersion) {
            2 -> DIGEST_SECRET_2
            3 -> DIGEST_SECRET_3
            else -> DIGEST_SECRET_1
        }

        if (encMethod == 0x01) {
            val nonce = ByteArray(12)
            System.arraycopy(iv, 0, nonce, 0, 12.coerceAtMost(iv.size))
            return aesGcmDecrypt(msg, key, nonce, ByteArray(0))
        }

        val iv16 = ByteArray(16)
        System.arraycopy(iv, 0, iv16, 0, 16.coerceAtMost(iv.size))

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC")
        val secretKeySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv16)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)
        return cipher.doFinal(msg)
    }

    /**
     * Derives Pre-Shared Key (PSK) for first authentication.
     * PSK = HMAC-SHA256(SHA256(hex(pin).upper()), seed)
     */
    fun derivePskFirstAuth(pinBytes: ByteArray, seed: ByteArray): ByteArray {
        val pinHex = bytesToHex(pinBytes).uppercase().toByteArray(StandardCharsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val pinHash = md.digest(pinHex)
        return hmacSha256(pinHash, seed)
    }

    /**
     * Derives Pre-Shared Key (PSK) for subsequent reconnects.
     */
    fun derivePskReconnect(secretKey: ByteArray, seed: ByteArray): ByteArray {
        return hmacSha256(secretKey, seed)
    }

    /**
     * Derives the 32-byte session key using HKDF-SHA256.
     */
    fun deriveSessionKey(psk: ByteArray, randSelf: ByteArray, randPeer: ByteArray): ByteArray {
        val salt = ByteArray(randSelf.size + randPeer.size)
        System.arraycopy(randSelf, 0, salt, 0, randSelf.size)
        System.arraycopy(randPeer, 0, salt, randSelf.size, randPeer.size)
        val info = "hichain_iso_session_key".toByteArray(StandardCharsets.UTF_8)
        return hkdfSha256(psk, salt, info, 32)
    }

    /**
     * Derives the final transaction secret_key using HKDF-SHA256.
     */
    fun deriveReturnKey(sessionKey: ByteArray, salt: ByteArray): ByteArray {
        val info = "hichain_return_key".toByteArray(StandardCharsets.UTF_8)
        return hkdfSha256(sessionKey, salt, info, 32)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data)
    }

    fun computeHmacHex(key: ByteArray, data: ByteArray): String {
        return bytesToHex(hmacSha256(key, data)).uppercase()
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        val params = HKDFParameters(ikm, salt, info)
        generator.init(params)
        val out = ByteArray(outLen)
        generator.generateBytes(out, 0, outLen)
        return out
    }

    fun aesGcmEncrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }

    fun aesGcmDecrypt(dataAndTag: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(dataAndTag)
    }
}
