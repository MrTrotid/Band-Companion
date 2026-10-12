package com.bandcompanion.core.bluetooth.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Huawei LPv2 7-bit VarInt length codec and TLV parser.
 *
 * Single-byte length: 0x00 to 0x7F (0 to 127 bytes).
 * Multi-byte length: Continuation bit (0x80) on all preceding bytes, big-endian.
 */
object VarIntTlv {

    fun encodeVarInt(value: Int): ByteArray {
        require(value >= 0) { "VarInt cannot be negative: $value" }
        if (value < 128) {
            return byteArrayOf(value.toByte())
        }

        val chunks = mutableListOf<Int>()
        var v = value
        while (v > 0) {
            chunks.add(v and 0x7F)
            v = v ushr 7
        }
        chunks.reverse()

        val out = ByteArray(chunks.size)
        for (i in chunks.indices) {
            if (i < chunks.size - 1) {
                out[i] = (chunks[i] or 0x80).toByte()
            } else {
                out[i] = chunks[i].toByte()
            }
        }
        return out
    }

    /**
     * Decodes a VarInt from [data] starting at [offset].
     * Returns Pair(decodedValue, nextOffset).
     */
    fun decodeVarInt(data: ByteArray, offset: Int = 0): Pair<Int, Int> {
        var value = 0
        var pos = offset
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            pos++
            value = (value shl 7) or (b and 0x7F)
            if ((b and 0x80) == 0) {
                return Pair(value, pos)
            }
        }
        throw IllegalArgumentException("Truncated VarInt at offset $offset in data of size ${data.size}")
    }

    /**
     * Encodes a single TLV tag and value.
     */
    fun encode(tag: Int, value: ByteArray): ByteArray {
        val lenBytes = encodeVarInt(value.size)
        val out = ByteArray(1 + lenBytes.size + value.size)
        out[0] = tag.toByte()
        System.arraycopy(lenBytes, 0, out, 1, lenBytes.size)
        System.arraycopy(value, 0, out, 1 + lenBytes.size, value.size)
        return out
    }

    fun encode(tag: Int, value: String): ByteArray {
        return encode(tag, value.toByteArray(StandardCharsets.UTF_8))
    }

    fun encode(tag: Int, value: Byte): ByteArray {
        return encode(tag, byteArrayOf(value))
    }

    fun encode(tag: Int, value: Int): ByteArray {
        // Encode 4-byte big-endian or minimal integer
        val b = byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
        return encode(tag, b)
    }

    /**
     * Parses a byte array containing concatenated TLVs into a map of Tag -> Value.
     */
    fun decodeAll(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Map<Int, ByteArray> {
        val map = mutableMapOf<Int, ByteArray>()
        var pos = offset
        val end = (offset + length).coerceAtMost(data.size)

        while (pos < end) {
            val tag = data[pos].toInt() and 0xFF
            pos++
            if (pos >= end) break

            val (len, nextPos) = decodeVarInt(data, pos)
            pos = nextPos

            if (pos + len > end) {
                // Truncated payload, take what remains
                val actualLen = (end - pos).coerceAtLeast(0)
                val value = ByteArray(actualLen)
                System.arraycopy(data, pos, value, 0, actualLen)
                map[tag] = value
                break
            }

            val value = ByteArray(len)
            System.arraycopy(data, pos, value, 0, len)
            pos += len
            map[tag] = value
        }

        return map
    }

    /**
     * Parses a byte array containing concatenated TLVs into an ordered list of Tag -> Value pairs,
     * preserving duplicate tags (e.g. repeated 0x84 minute records or repeated 0x83 sleep records).
     */
    fun decodeItems(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<Pair<Int, ByteArray>> {
        val list = mutableListOf<Pair<Int, ByteArray>>()
        var pos = offset
        val end = (offset + length).coerceAtMost(data.size)

        while (pos < end) {
            val tag = data[pos].toInt() and 0xFF
            pos++
            if (pos >= end) break

            val (len, nextPos) = decodeVarInt(data, pos)
            pos = nextPos

            if (pos + len > end) {
                val actualLen = (end - pos).coerceAtLeast(0)
                val value = ByteArray(actualLen)
                System.arraycopy(data, pos, value, 0, actualLen)
                list.add(Pair(tag, value))
                break
            }

            val value = ByteArray(len)
            System.arraycopy(data, pos, value, 0, len)
            pos += len
            list.add(Pair(tag, value))
        }

        return list
    }
}
