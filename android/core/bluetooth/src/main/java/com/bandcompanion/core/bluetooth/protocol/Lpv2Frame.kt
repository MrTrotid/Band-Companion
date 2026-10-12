package com.bandcompanion.core.bluetooth.protocol

import java.io.ByteArrayOutputStream

/**
 * Huawei LPv2 Packet Framer and Sliced Packet Reassembler.
 *
 * Magic Byte: 0x5A
 * Sliced indicators: 0x01 (Start), 0x02 (Middle), 0x03 (End)
 */
object Lpv2Frame {
    const val MAGIC_BYTE: Byte = 0x5A
    const val SLICE_NONE: Byte = 0x00
    const val SLICE_START: Byte = 0x01
    const val SLICE_MIDDLE: Byte = 0x02
    const val SLICE_END: Byte = 0x03

    data class ParsedPacket(
        val serviceId: Int,
        val commandId: Int,
        val payload: ByteArray,
        val tlvs: Map<Int, ByteArray>
    )

    /**
     * Constructs a single unsliced LPv2 packet frame.
     */
    fun makeFrame(serviceId: Int, commandId: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val bodyLen = 2 + payload.size
        val length = bodyLen + 1 // +1 for null slice byte

        val frameWithoutCrc = ByteArray(4 + bodyLen)
        frameWithoutCrc[0] = MAGIC_BYTE
        frameWithoutCrc[1] = ((length ushr 8) and 0xFF).toByte()
        frameWithoutCrc[2] = (length and 0xFF).toByte()
        frameWithoutCrc[3] = SLICE_NONE
        frameWithoutCrc[4] = serviceId.toByte()
        frameWithoutCrc[5] = commandId.toByte()

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, frameWithoutCrc, 6, payload.size)
        }

        val crc = Lpv2Crc16.compute(frameWithoutCrc)
        val result = ByteArray(frameWithoutCrc.size + 2)
        System.arraycopy(frameWithoutCrc, 0, result, 0, frameWithoutCrc.size)
        result[result.size - 2] = ((crc ushr 8) and 0xFF).toByte()
        result[result.size - 1] = (crc and 0xFF).toByte()
        return result
    }

    /**
     * Splits a large payload into sliced LPv2 BLE frames.
     */
    fun makeSlicedFrames(
        serviceId: Int,
        commandId: Int,
        tlvPayload: ByteArray,
        sliceSize: Int = 244,
        seqFlag: Int = 0
    ): List<ByteArray> {
        val fullBody = ByteArray(2 + tlvPayload.size)
        fullBody[0] = serviceId.toByte()
        fullBody[1] = commandId.toByte()
        System.arraycopy(tlvPayload, 0, fullBody, 2, tlvPayload.size)

        val headerLen = 5 // [0x5A][len_hi][len_lo][slice][flag]
        val crcLen = 2
        val maxChunk = sliceSize - headerLen - crcLen

        val frames = mutableListOf<ByteArray>()
        var offset = 0
        val total = fullBody.size

        while (offset < total) {
            val chunkLen = (total - offset).coerceAtMost(maxChunk)
            val isFirst = (offset == 0)
            val isLast = (offset + chunkLen >= total)

            val sliceByte = when {
                isFirst -> SLICE_START
                isLast -> SLICE_END
                else -> SLICE_MIDDLE
            }

            val length = chunkLen + 2 // payload + slice_byte + flag
            val frameWithoutCrc = ByteArray(headerLen + chunkLen)
            frameWithoutCrc[0] = MAGIC_BYTE
            frameWithoutCrc[1] = ((length ushr 8) and 0xFF).toByte()
            frameWithoutCrc[2] = (length and 0xFF).toByte()
            frameWithoutCrc[3] = sliceByte
            frameWithoutCrc[4] = (seqFlag and 0xFF).toByte()
            System.arraycopy(fullBody, offset, frameWithoutCrc, headerLen, chunkLen)

            val crc = Lpv2Crc16.compute(frameWithoutCrc)
            val frame = ByteArray(frameWithoutCrc.size + 2)
            System.arraycopy(frameWithoutCrc, 0, frame, 0, frameWithoutCrc.size)
            frame[frame.size - 2] = ((crc ushr 8) and 0xFF).toByte()
            frame[frame.size - 1] = (crc and 0xFF).toByte()
            frames.add(frame)

            offset += chunkLen
        }

        return frames
    }

    /**
     * Stateful reassembler for incoming BLE notifications.
     */
    class Reassembler {
        private val buffer = ByteArrayOutputStream()
        private var activeFlag: Int = -1
        private var isAssembling = false

        /**
         * Feeds an incoming raw packet. Returns [ParsedPacket] if a complete packet is resolved.
         */
        fun feed(raw: ByteArray): ParsedPacket? {
            if (raw.size < 6 || raw[0] != MAGIC_BYTE) return null
            if (!Lpv2Crc16.verify(raw)) {
                return null
            }

            val sliceByte = raw[3]
            val payloadLen = raw.size - 4 - 2 // header (4 bytes: magic, len(2), slice) - CRC (2 bytes)

            when (sliceByte) {
                SLICE_NONE -> {
                    // Unsliced frame
                    val serviceId = raw[4].toInt() and 0xFF
                    val commandId = raw[5].toInt() and 0xFF
                    val tlvData = if (raw.size > 8) {
                        val tlvLen = raw.size - 6 - 2
                        val bytes = ByteArray(tlvLen)
                        System.arraycopy(raw, 6, bytes, 0, tlvLen)
                        bytes
                    } else ByteArray(0)

                    val tlvs = VarIntTlv.decodeAll(tlvData)
                    return ParsedPacket(serviceId, commandId, tlvData, tlvs)
                }

                SLICE_START -> {
                    buffer.reset()
                    val flag = raw[4].toInt() and 0xFF
                    activeFlag = flag
                    isAssembling = true
                    // Payload starts after slice header [0x5A][len(2)][slice][flag] = offset 5
                    val chunkLen = raw.size - 5 - 2
                    if (chunkLen > 0) {
                        buffer.write(raw, 5, chunkLen)
                    }
                    return null
                }

                SLICE_MIDDLE -> {
                    val flag = raw[4].toInt() and 0xFF
                    if (!isAssembling || flag != activeFlag) return null
                    val chunkLen = raw.size - 5 - 2
                    if (chunkLen > 0) {
                        buffer.write(raw, 5, chunkLen)
                    }
                    return null
                }

                SLICE_END -> {
                    val flag = raw[4].toInt() and 0xFF
                    if (!isAssembling || flag != activeFlag) return null
                    val chunkLen = raw.size - 5 - 2
                    if (chunkLen > 0) {
                        buffer.write(raw, 5, chunkLen)
                    }

                    isAssembling = false
                    val fullBytes = buffer.toByteArray()
                    buffer.reset()

                    if (fullBytes.size < 2) return null
                    val serviceId = fullBytes[0].toInt() and 0xFF
                    val commandId = fullBytes[1].toInt() and 0xFF
                    val tlvData = if (fullBytes.size > 2) {
                        val bytes = ByteArray(fullBytes.size - 2)
                        System.arraycopy(fullBytes, 2, bytes, 0, bytes.size)
                        bytes
                    } else ByteArray(0)

                    val tlvs = VarIntTlv.decodeAll(tlvData)
                    return ParsedPacket(serviceId, commandId, tlvData, tlvs)
                }
            }

            return null
        }
    }
}
