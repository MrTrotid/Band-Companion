package com.bandcompanion.core.bluetooth.protocol

/**
 * CRC16-HQXQ checksum calculation (Polynomial 0x1021, Initial value 0x0000).
 * Matches Python `binascii.crc_hqx(data, 0)` used by Huawei LPv2 protocol frames.
 */
object Lpv2Crc16 {
    private const val POLYNOMIAL = 0x1021

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0x0000
        val end = (offset + length).coerceAtMost(data.size)
        for (i in offset until end) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor POLYNOMIAL) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    fun verify(frameWithCrc: ByteArray): Boolean {
        if (frameWithCrc.size < 4) return false
        val expectedCrc = ((frameWithCrc[frameWithCrc.size - 2].toInt() and 0xFF) shl 8) or
                (frameWithCrc[frameWithCrc.size - 1].toInt() and 0xFF)
        val calculatedCrc = compute(frameWithCrc, 0, frameWithCrc.size - 2)
        return expectedCrc == calculatedCrc
    }
}
