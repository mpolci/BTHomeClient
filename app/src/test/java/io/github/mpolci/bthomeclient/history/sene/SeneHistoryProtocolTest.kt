package io.github.mpolci.bthomeclient.history.sene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeneHistoryProtocolTest {
    @Test
    fun `encodes commands with exact lengths and little endian UTC`() {
        val cursor = cursor(generation = 0x11223344, slot = 255)

        assertArrayEquals(
            bytes("01 01 04 03 02 01"),
            SeneHistoryProtocol.encodeOpenFull(0x01020304),
        )
        assertArrayEquals(
            bytes("02 01 04 03 02 01") + cursor,
            SeneHistoryProtocol.encodeOpenAfter(0x01020304, cursor),
        )
        assertArrayEquals(bytes("03 01 FE"), SeneHistoryProtocol.encodeCancel(0xFE))
    }

    @Test
    fun `rejects values that cannot be represented by the wire protocol`() {
        assertThrows(IllegalArgumentException::class.java) { SeneHistoryProtocol.encodeOpenFull(-1) }
        assertThrows(IllegalArgumentException::class.java) { SeneHistoryProtocol.encodeOpenFull(0x1_0000_0000) }
        assertThrows(IllegalArgumentException::class.java) { SeneHistoryProtocol.encodeCancel(0) }
        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.encodeOpenAfter(1, ByteArray(SeneHistoryProtocol.cursorSize))
        }
    }

    @Test
    fun `validates cursor geometry and CRC16 CCITT FALSE`() {
        assertEquals(0x29B1, SeneHistoryProtocol.crc16CcittFalse("123456789".encodeToByteArray()))

        val valid = SeneHistoryProtocol.decodeCursor(cursor(generation = 0xFFFF_FFFFL, slot = 255))
        assertEquals(1, valid.formatVersion)
        assertEquals(0xFFFF_FFFFL, valid.generation)
        assertEquals(255, valid.nextSlot)

        val badCrc = cursor(generation = 2, slot = 3).also { it[8] = 0 }
        val badSlot = cursor(generation = 2, slot = 256)
        assertThrows(IllegalArgumentException::class.java) { SeneHistoryProtocol.decodeCursor(badCrc) }
        assertThrows(IllegalArgumentException::class.java) { SeneHistoryProtocol.decodeCursor(badSlot) }
    }

    @Test
    fun `decodes strict ready and empty responses`() {
        val finalCursor = cursor(generation = 42, slot = 17)
        val ready = SeneHistoryProtocol.decodeControlResponse(
            bytes("81 01 09 03 01 00 02 00 00 00") + finalCursor,
        ) as SeneHistoryControlResponse.Ready
        val empty = SeneHistoryProtocol.decodeControlResponse(
            bytes("82 01 02 00") + finalCursor,
        ) as SeneHistoryControlResponse.Empty

        assertEquals(9, ready.transferId)
        assertEquals(2, ready.positionCount)
        assertTrue(ready.flags.syncPending)
        assertTrue(ready.flags.historyTruncated)
        assertEquals(42, ready.finalCursor.generation)
        assertFalse(empty.flags.syncPending)
        assertTrue(empty.flags.historyTruncated)

        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeControlResponse(bytes("81 01 09 04 01 00 01 00 00 00") + finalCursor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeControlResponse(bytes("82 01 00 01") + finalCursor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeControlResponse(bytes("81 01 09 00 01 00 00 00 00 00") + finalCursor)
        }
    }

    @Test
    fun `decodes all terminal control responses with exact transfer ID rules`() {
        assertEquals(
            SeneHistoryControlResponse.StaleCursor,
            SeneHistoryProtocol.decodeControlResponse(bytes("83 01")),
        )
        assertEquals(
            SeneHistoryControlResponse.Busy(0),
            SeneHistoryProtocol.decodeControlResponse(bytes("84 01 00")),
        )
        assertEquals(
            SeneHistoryControlResponse.StorageError(0),
            SeneHistoryProtocol.decodeControlResponse(bytes("87 01 00")),
        )
        assertEquals(
            SeneHistoryControlResponse.Canceled(5),
            SeneHistoryProtocol.decodeControlResponse(bytes("88 01 05")),
        )
        assertEquals(
            SeneHistoryControlResponse.Done(255),
            SeneHistoryProtocol.decodeControlResponse(bytes("89 01 FF")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeControlResponse(bytes("88 01 00"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeControlResponse(bytes("89 01 01 00"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeControlResponse(bytes("83 02"))
        }
    }

    @Test
    fun `decodes data packets and rejects partial positions`() {
        val firstPosition = 0x00A1B2
        val data = position(type = SeneHistoryProtocol.sampleUtcType, timestamp = 100)
        val packet = SeneHistoryProtocol.decodeDataPacket(bytes("07 B2 A1 00") + data)

        assertEquals(7, packet.transferId)
        assertEquals(firstPosition, packet.firstPositionIndex)
        assertEquals(1, packet.positionCount)
        assertArrayEquals(data, packet.rawPositions)

        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeDataPacket(bytes("07 00 00 00") + ByteArray(15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeneHistoryProtocol.decodeDataPacket(bytes("00 00 00 00") + data)
        }
    }

    @Test
    fun `decodes direct UTC and reconstructs unsynced records from the closest session anchor`() {
        val raw = position(
            type = SeneHistoryProtocol.sampleUnsyncedType,
            timestamp = 110,
            pressureDecaPa = 10013,
            temperatureDeciC = -25,
            humidity = 50,
            battery = 90,
        ) + position(
            type = SeneHistoryProtocol.timeSyncType,
            timestamp = 1_700_000_000,
            payloadSecond = 100,
        ) + position(
            type = SeneHistoryProtocol.sampleUtcType,
            timestamp = 1_700_000_020,
            pressureDecaPa = 10020,
            temperatureDeciC = 215,
            humidity = 60,
            battery = 80,
        )

        val result = SeneHistoryProtocol.decodeRawPositions(raw)

        assertEquals(2, result.samples.size)
        assertEquals(1_700_000_010_000L, result.samples[0].recordedEpochMillis)
        assertEquals(SeneHistoryTimeQuality.RECONSTRUCTED, result.samples[0].timeQuality)
        assertEquals(-2.5, result.samples[0].temperatureCelsius, 0.0)
        assertEquals(1001.3, result.samples[0].pressureHectopascals, 0.0)
        assertEquals(1_700_000_020_000L, result.samples[1].recordedEpochMillis)
        assertEquals(SeneHistoryTimeQuality.DIRECT_UTC, result.samples[1].timeQuality)
    }

    @Test
    fun `does not reconstruct across boot sessions and preserves undated samples`() {
        val raw = position(
            type = SeneHistoryProtocol.timeSyncType,
            timestamp = 1_700_000_000,
            payloadSecond = 100,
        ) + position(
            type = SeneHistoryProtocol.sampleUnsyncedType,
            timestamp = 110,
        ) + position(type = SeneHistoryProtocol.bootType) + position(
            type = SeneHistoryProtocol.sampleUnsyncedType,
            timestamp = 120,
        )

        val result = SeneHistoryProtocol.decodeRawPositions(raw)

        assertEquals(2, result.samples.size)
        assertEquals(1_700_000_010_000L, result.samples[0].recordedEpochMillis)
        assertNull(result.samples[1].recordedEpochMillis)
        assertEquals(SeneHistoryTimeQuality.UNAVAILABLE, result.samples[1].timeQuality)
        assertEquals(1, result.undatedSampleCount)
    }

    @Test
    fun `counts erased invalid unsupported and undated positions without failing the transfer`() {
        val erased = ByteArray(SeneHistoryProtocol.rawPositionSize) { 0xFF.toByte() }
        val invalidCrc = position(type = SeneHistoryProtocol.sampleUtcType, timestamp = 1).also { it[11] = 0 }
        val badPadding = position(type = SeneHistoryProtocol.sampleUtcType, timestamp = 2).also { it[13] = 0 }
        val unknown = position(type = 0x42)
        val badHumidity = position(type = SeneHistoryProtocol.sampleUtcType, timestamp = 3, humidity = 101)
        val undated = position(type = SeneHistoryProtocol.sampleUnsyncedType, timestamp = 4)

        val result = SeneHistoryProtocol.decodeRawPositions(erased + invalidCrc + badPadding + unknown + badHumidity + undated)

        assertEquals(1, result.erasedPositionCount)
        assertEquals(3, result.invalidPositionCount)
        assertEquals(1, result.unsupportedPositionCount)
        assertEquals(1, result.undatedSampleCount)
        assertEquals(listOf(5), result.samples.map { it.positionIndex })
    }

    private fun cursor(generation: Long, slot: Int): ByteArray = ByteArray(SeneHistoryProtocol.cursorSize).also { cursor ->
        writeUInt16(cursor, 0, SeneHistoryProtocol.formatVersion)
        writeUInt32(cursor, 2, generation)
        writeUInt16(cursor, 6, slot)
        writeUInt16(cursor, 8, SeneHistoryProtocol.crc16CcittFalse(cursor, length = 8))
    }

    private fun position(
        type: Int,
        timestamp: Long = 0,
        payloadSecond: Long = 0,
        pressureDecaPa: Int = 10_000,
        temperatureDeciC: Int = 200,
        humidity: Int = 40,
        battery: Int = 70,
    ): ByteArray = ByteArray(SeneHistoryProtocol.rawPositionSize) { 0xFF.toByte() }.also { position ->
        position[0] = type.toByte()
        writeUInt32(position, 1, timestamp)
        writeUInt16(position, 5, pressureDecaPa)
        writeUInt16(position, 7, temperatureDeciC)
        position[9] = humidity.toByte()
        position[10] = battery.toByte()
        if (type == SeneHistoryProtocol.timeSyncType) writeUInt32(position, 5, payloadSecond)
        writeUInt16(position, 11, SeneHistoryProtocol.crc16CcittFalse(position, length = 11))
    }

    private fun writeUInt16(output: ByteArray, offset: Int, value: Int) {
        output[offset] = value.toByte()
        output[offset + 1] = (value shr 8).toByte()
    }

    private fun writeUInt32(output: ByteArray, offset: Int, value: Long) {
        output[offset] = value.toByte()
        output[offset + 1] = (value shr 8).toByte()
        output[offset + 2] = (value shr 16).toByte()
        output[offset + 3] = (value shr 24).toByte()
    }

    private fun bytes(hex: String): ByteArray = hex.split(" ")
        .filter(String::isNotBlank)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
