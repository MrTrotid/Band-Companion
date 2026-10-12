package com.bandcompanion.core.bluetooth

import com.bandcompanion.core.bluetooth.crypto.HiChain3Crypto
import com.bandcompanion.core.bluetooth.protocol.Lpv2Crc16
import com.bandcompanion.core.bluetooth.protocol.Lpv2Frame
import com.bandcompanion.core.bluetooth.protocol.VarIntTlv
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class Lpv2ProtocolTest {

    @Test
    fun testCrc16HqxStandardVector() {
        // Standard test vector "123456789" -> CRC16-CCITT / HQX = 0x31C3
        val testData = "123456789".toByteArray(StandardCharsets.US_ASCII)
        val crc = Lpv2Crc16.compute(testData)
        assertEquals(0x31C3, crc)
    }

    @Test
    fun testVarIntEncodingAndDecoding() {
        // 127 -> single byte [0x7F]
        val v127 = VarIntTlv.encodeVarInt(127)
        assertEquals(1, v127.size)
        assertEquals(0x7F.toByte(), v127[0])
        val (dec127, pos127) = VarIntTlv.decodeVarInt(v127)
        assertEquals(127, dec127)
        assertEquals(1, pos127)

        // 128 -> multi-byte [0x81, 0x00]
        val v128 = VarIntTlv.encodeVarInt(128)
        assertEquals(2, v128.size)
        assertEquals(0x81.toByte(), v128[0])
        assertEquals(0x00.toByte(), v128[1])
        val (dec128, pos128) = VarIntTlv.decodeVarInt(v128)
        assertEquals(128, dec128)
        assertEquals(2, pos128)

        // 671 -> multi-byte [0x85, 0x1F]
        val v671 = VarIntTlv.encodeVarInt(671)
        assertEquals(2, v671.size)
        assertEquals(0x85.toByte(), v671[0])
        assertEquals(0x1F.toByte(), v671[1])
        val (dec671, pos671) = VarIntTlv.decodeVarInt(v671)
        assertEquals(671, dec671)
        assertEquals(2, pos671)
    }

    @Test
    fun testTlvRoundTrip() {
        val tlv1 = VarIntTlv.encode(0x01, "BAND_COMPANION")
        val tlv2 = VarIntTlv.encode(0x02, byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val combined = tlv1 + tlv2

        val decoded = VarIntTlv.decodeAll(combined)
        assertEquals(2, decoded.size)
        assertEquals("BAND_COMPANION", String(decoded[0x01]!!, StandardCharsets.UTF_8))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), decoded[0x02])
    }

    @Test
    fun testUnslicedFrameCreationAndVerification() {
        val payload = VarIntTlv.encode(0x01, "HELLO")
        val frame = Lpv2Frame.makeFrame(serviceId = 0x01, commandId = 0x01, payload = payload)

        // Verify magic byte
        assertEquals(Lpv2Frame.MAGIC_BYTE, frame[0])
        // Verify slice byte is 0x00
        assertEquals(Lpv2Frame.SLICE_NONE, frame[3])
        // Verify CRC passes
        assertTrue(Lpv2Crc16.verify(frame))

        // Feed into reassembler
        val reassembler = Lpv2Frame.Reassembler()
        val parsed = reassembler.feed(frame)
        assertNotNull(parsed)
        assertEquals(0x01, parsed!!.serviceId)
        assertEquals(0x01, parsed.commandId)
        assertEquals("HELLO", String(parsed.tlvs[0x01]!!, StandardCharsets.UTF_8))
    }

    @Test
    fun testSlicedFrameSplittingAndReassembly() {
        // Large payload exceeding single frame slice size
        val largeData = ByteArray(600) { (it % 256).toByte() }
        val tlvPayload = VarIntTlv.encode(0x01, largeData)

        val frames = Lpv2Frame.makeSlicedFrames(
            serviceId = 0x2C,
            commandId = 0x01,
            tlvPayload = tlvPayload,
            sliceSize = 128,
            seqFlag = 42
        )

        assertTrue("Should produce multiple frames for 600-byte payload with 128 slice size", frames.size >= 5)

        // First frame must be SLICE_START (0x01)
        assertEquals(Lpv2Frame.SLICE_START, frames.first()[3])
        // Middle frames must be SLICE_MIDDLE (0x02)
        assertEquals(Lpv2Frame.SLICE_MIDDLE, frames[1][3])
        // Last frame must be SLICE_END (0x03)
        assertEquals(Lpv2Frame.SLICE_END, frames.last()[3])

        // All frames must have valid CRC
        for (f in frames) {
            assertTrue(Lpv2Crc16.verify(f))
        }

        // Feed each frame into reassembler
        val reassembler = Lpv2Frame.Reassembler()
        var completedPacket: Lpv2Frame.ParsedPacket? = null
        for (f in frames) {
            val result = reassembler.feed(f)
            if (result != null) {
                completedPacket = result
            }
        }

        assertNotNull("Reassembler must emit completed packet on final frame", completedPacket)
        assertEquals(0x2C, completedPacket!!.serviceId)
        assertEquals(0x01, completedPacket.commandId)
        assertArrayEquals(largeData, completedPacket.tlvs[0x01])
    }

    @Test
    fun testHiChainCryptoDerivations() {
        val secretKey = HiChain3Crypto.hexToBytes("49796806333bec48d7b38ed167fe2cca10f319f70c073e17efb5ec8a58a40b45")
        val seed = HiChain3Crypto.hexToBytes("0102030405060708090a0b0c0d0e0f10")

        val psk = HiChain3Crypto.derivePskReconnect(secretKey, seed)
        assertEquals(32, psk.size)

        val randSelf = HiChain3Crypto.hexToBytes("1112131415161718191a1b1c1d1e1f20")
        val randPeer = HiChain3Crypto.hexToBytes("2122232425262728292a2b2c2d2e2f30")

        val sessionKey = HiChain3Crypto.deriveSessionKey(psk, randSelf, randPeer)
        assertEquals(32, sessionKey.size)

        val salt = randSelf + randPeer
        val returnKey = HiChain3Crypto.deriveReturnKey(sessionKey, salt)
        assertEquals(32, returnKey.size)
    }
}
