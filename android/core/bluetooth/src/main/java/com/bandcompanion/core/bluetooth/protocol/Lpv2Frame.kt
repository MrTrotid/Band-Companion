package com.bandcompanion.core.bluetooth.protocol

import java.io.ByteArrayOutputStream

/**
 * Huawei LPv2 Packet Framer and Stream Reassembler.
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
     * Splits a large payload into sliced LPv2 BLE frames matching tools/connect.py.
     */
    fun makeSlicedFrames(
        serviceId: Int,
        commandId: Int,
        tlvPayload: ByteArray,
        sliceSize: Int = 244
    ): List<ByteArray> {
        val headerLen = 5 // magic + len(2) + slice + flag
        val footerLen = 2 // CRC16
        val maxChunk = sliceSize - headerLen - footerLen

        val chunks = mutableListOf<Pair<ByteArray, Boolean>>()
        var pos = 0
        var first = true
        val total = tlvPayload.size

        while (pos < total || first) {
            val avail = if (first) maxChunk - 2 else maxChunk
            val chunkLen = (total - pos).coerceAtMost(avail).coerceAtLeast(0)
            val chunk = if (chunkLen > 0) tlvPayload.copyOfRange(pos, pos + chunkLen) else ByteArray(0)
            pos += chunkLen
            val last = (pos >= total)
            first = false
            chunks.add(Pair(chunk, last))
        }

        val frames = mutableListOf<ByteArray>()
        var flag = 0

        for (i in chunks.indices) {
            val (chunk, last) = chunks[i]
            val isFirst = (i == 0)
            val sliceByte = when {
                isFirst -> SLICE_START
                last -> SLICE_END
                else -> SLICE_MIDDLE
            }

            val payload = if (isFirst) {
                val p = ByteArray(2 + chunk.size)
                p[0] = serviceId.toByte()
                p[1] = commandId.toByte()
                if (chunk.isNotEmpty()) {
                    System.arraycopy(chunk, 0, p, 2, chunk.size)
                }
                p
            } else {
                chunk
            }

            val bodyLen = payload.size
            val frameWithoutCrc = ByteArray(headerLen + bodyLen)
            frameWithoutCrc[0] = MAGIC_BYTE
            val length = bodyLen + footerLen
            frameWithoutCrc[1] = ((length ushr 8) and 0xFF).toByte()
            frameWithoutCrc[2] = (length and 0xFF).toByte()
            frameWithoutCrc[3] = sliceByte
            frameWithoutCrc[4] = (flag and 0xFF).toByte()
            System.arraycopy(payload, 0, frameWithoutCrc, headerLen, bodyLen)

            val crc = Lpv2Crc16.compute(frameWithoutCrc)
            val frame = ByteArray(frameWithoutCrc.size + 2)
            System.arraycopy(frameWithoutCrc, 0, frame, 0, frameWithoutCrc.size)
            frame[frame.size - 2] = ((crc ushr 8) and 0xFF).toByte()
            frame[frame.size - 1] = (crc and 0xFF).toByte()
            frames.add(frame)

            flag = (flag + 1) and 0xFF
        }

        return frames
    }

    /**
     * Constructs appropriate BLE frames (unsliced or sliced based on sliceSize).
     */
    fun makeFrames(
        serviceId: Int,
        commandId: Int,
        payload: ByteArray = ByteArray(0),
        sliceSize: Int = 244
    ): List<ByteArray> {
        return if (payload.size + 6 <= sliceSize) {
            listOf(makeFrame(serviceId, commandId, payload))
        } else {
            val sliced = makeSlicedFrames(serviceId, commandId, payload, sliceSize)
            if (sliced.size == 1 && sliced[0][3] == SLICE_START) {
                listOf(makeFrame(serviceId, commandId, payload))
            } else {
                sliced
            }
        }
    }

    /**
     * Stream Reassembler that buffers incoming BLE chunks, recovers frames by length,
     * and assembles multi-part sliced packets.
     */
    class Reassembler {
        private var streamBuffer = ByteArray(0)
        private var sliceSvc: Int = -1
        private var sliceCmd: Int = -1
        private val slicePayloadBuffer = ByteArrayOutputStream()

        fun feed(chunk: ByteArray): List<ParsedPacket> {
            val results = mutableListOf<ParsedPacket>()
            val combined = ByteArray(streamBuffer.size + chunk.size)
            System.arraycopy(streamBuffer, 0, combined, 0, streamBuffer.size)
            System.arraycopy(chunk, 0, combined, streamBuffer.size, chunk.size)
            var buf = combined

            while (buf.isNotEmpty()) {
                if (buf.size < 4) {
                    streamBuffer = buf
                    return results
                }
                if (buf[0] != MAGIC_BYTE) {
                    // Search for magic byte 0x5A
                    var magicIdx = -1
                    for (i in 1 until buf.size) {
                        if (buf[i] == MAGIC_BYTE) {
                            magicIdx = i
                            break
                        }
                    }
                    if (magicIdx == -1) {
                        streamBuffer = ByteArray(0)
                        return results
                    }
                    buf = buf.copyOfRange(magicIdx, buf.size)
                    if (buf.size < 4) {
                        streamBuffer = buf
                        return results
                    }
                }

                val expected = ((buf[1].toInt() and 0xFF) shl 8) or (buf[2].toInt() and 0xFF)
                val frameLen = expected + 5
                if (buf.size < frameLen) {
                    streamBuffer = buf
                    return results
                }

                val frame = buf.copyOfRange(0, frameLen)
                buf = buf.copyOfRange(frameLen, buf.size)

                if (!Lpv2Crc16.verify(frame)) {
                    continue
                }

                val sliceByte = frame[3]
                when (sliceByte) {
                    SLICE_NONE -> {
                        val svc = frame[4].toInt() and 0xFF
                        val cmd = frame[5].toInt() and 0xFF
                        val tlvLen = (frame.size - 6 - 2).coerceAtLeast(0)
                        val tlvBytes = if (tlvLen > 0) frame.copyOfRange(6, 6 + tlvLen) else ByteArray(0)
                        val tlvs = VarIntTlv.decodeAll(tlvBytes)
                        results.add(ParsedPacket(svc, cmd, tlvBytes, tlvs))
                    }
                    SLICE_START -> {
                        val payloadLen = frame.size - 5 - 2
                        if (payloadLen >= 2) {
                            sliceSvc = frame[5].toInt() and 0xFF
                            sliceCmd = frame[6].toInt() and 0xFF
                            slicePayloadBuffer.reset()
                            if (payloadLen > 2) {
                                slicePayloadBuffer.write(frame, 7, payloadLen - 2)
                            }
                        }
                    }
                    SLICE_MIDDLE -> {
                        val payloadLen = frame.size - 5 - 2
                        if (payloadLen > 0) {
                            slicePayloadBuffer.write(frame, 5, payloadLen)
                        }
                    }
                    SLICE_END -> {
                        val payloadLen = frame.size - 5 - 2
                        if (payloadLen > 0) {
                            slicePayloadBuffer.write(frame, 5, payloadLen)
                        }
                        if (sliceSvc != -1 && sliceCmd != -1) {
                            val fullTlv = slicePayloadBuffer.toByteArray()
                            slicePayloadBuffer.reset()
                            val tlvs = VarIntTlv.decodeAll(fullTlv)
                            results.add(ParsedPacket(sliceSvc, sliceCmd, fullTlv, tlvs))
                            sliceSvc = -1
                            sliceCmd = -1
                        }
                    }
                }
            }

            streamBuffer = ByteArray(0)
            return results
        }
    }
}
