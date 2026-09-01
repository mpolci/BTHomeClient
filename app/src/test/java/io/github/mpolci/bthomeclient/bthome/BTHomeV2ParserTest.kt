package io.github.mpolci.bthomeclient.bthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BTHomeV2ParserTest {
    private val parser = BTHomeV2Parser()

    @Test
    fun `decodes official temperature and humidity example`() {
        val result = decoded("40 02 C4 09 03 BF 13")

        assertEquals(25.0, result.measurements.temperatureCelsius!!, 0.0)
        assertEquals(50.55, result.measurements.humidityPercent!!, 0.0)
        assertEquals(2, result.deviceInformation.version)
        assertEquals(false, result.deviceInformation.encrypted)
        assertEquals(false, result.deviceInformation.triggerBased)
    }

    @Test
    fun `decodes negative temperature`() {
        val result = decoded("40 02 85 FF")

        assertEquals(-1.23, result.measurements.temperatureCelsius!!, 0.0)
    }

    @Test
    fun `decodes all supported measurements`() {
        val result = decoded("40 01 62 02 C4 09 03 BF 13 04 19 27 00 12 2C 01")

        assertEquals(98, result.measurements.batteryPercent)
        assertEquals(25.0, result.measurements.temperatureCelsius!!, 0.0)
        assertEquals(50.55, result.measurements.humidityPercent!!, 0.0)
        assertEquals(100.09, result.measurements.pressureHectopascals!!, 0.0)
        assertEquals(300, result.measurements.carbonDioxidePpm)
    }

    @Test
    fun `decodes packet timestamp device type and four component firmware`() {
        val result = decoded("40 00 2A 01 64 50 00 F1 53 65 F0 34 12 F1 00 01 02 04")

        assertEquals(42, result.metadata.packetId)
        assertEquals(1_700_000_000_000L, result.metadata.timestampEpochMillis)
        assertEquals(0x1234, result.metadata.deviceType)
        assertEquals("4.2.1.0", result.metadata.firmwareVersion)
    }

    @Test
    fun `decodes three component firmware`() {
        val result = decoded("40 01 64 F2 00 01 06")

        assertEquals("6.1.0", result.metadata.firmwareVersion)
    }

    @Test
    fun `reports no measurements as unsupported`() {
        val result = parser.parse(0xFCD2, bytes("40 00 2A 50 00 F1 53 65")) as BTHomeParseResult.Unsupported

        assertEquals(BTHomeUnsupportedReason.NoSupportedMeasurements, result.reason)
        assertEquals(42, result.metadata!!.packetId)
        assertEquals(1_700_000_000_000L, result.metadata.timestampEpochMillis)
        assertEquals(2, result.deviceInformation!!.version)
    }

    @Test
    fun `reports wrong UUID and empty data distinctly`() {
        val wrongUuid = parser.parse(0x180F, bytes("40 01 64")) as BTHomeParseResult.Unsupported
        val empty = parser.parse(0xFCD2, byteArrayOf()) as BTHomeParseResult.Malformed

        assertEquals(BTHomeUnsupportedReason.WrongServiceUuid, wrongUuid.reason)
        assertEquals(null, wrongUuid.deviceInformation)
        assertEquals(BTHomeMalformedReason.MissingDeviceInformation, empty.reason)
    }

    @Test
    fun `reports truncated objects`() {
        val result = parser.parse(0xFCD2, bytes("40 02 10")) as BTHomeParseResult.Malformed

        assertEquals(BTHomeMalformedReason.TruncatedObject, result.reason)
    }

    @Test
    fun `reports descending object IDs`() {
        val result = parser.parse(0xFCD2, bytes("40 03 10 00 02 C4 09")) as BTHomeParseResult.Malformed

        assertEquals(BTHomeMalformedReason.DescendingObjectIds, result.reason)
    }

    @Test
    fun `rejects encrypted and non version two advertisements`() {
        val encrypted = parser.parse(0xFCD2, bytes("41 01 64")) as BTHomeParseResult.Unsupported
        val versionOne = parser.parse(0xFCD2, bytes("20 01 64")) as BTHomeParseResult.Unsupported

        assertEquals(BTHomeUnsupportedReason.Encrypted, encrypted.reason)
        assertEquals(true, encrypted.deviceInformation!!.encrypted)
        assertEquals(BTHomeUnsupportedReason.UnsupportedVersion, versionOne.reason)
        assertEquals(1, versionOne.deviceInformation!!.version)
    }

    @Test
    fun `unknown object before measurement is unsupported`() {
        val result = parser.parse(0xFCD2, bytes("40 10 00 01 64")) as BTHomeParseResult.Unsupported

        assertEquals(BTHomeUnsupportedReason.NoSupportedMeasurements, result.reason)
        assertEquals(listOf(BTHomeDiagnostic.UnsupportedObject(0x10)), result.diagnostics)
    }

    @Test
    fun `unknown object after measurement preserves decoded value and diagnostic`() {
        val result = decoded("40 01 64 10 00")

        assertEquals(100, result.measurements.batteryPercent)
        assertEquals(listOf(BTHomeDiagnostic.UnsupportedObject(0x10)), result.diagnostics)
    }

    @Test
    fun `duplicate measurements retain first value and report diagnostic`() {
        val result = decoded("40 02 C4 09 02 10 27")

        assertEquals(25.0, result.measurements.temperatureCelsius!!, 0.0)
        assertEquals(listOf(BTHomeDiagnostic.DuplicateObject(0x02)), result.diagnostics)
    }

    @Test
    fun `decodes maximum unsigned values without overflow`() {
        val result = decoded("40 01 FF 03 FF FF 04 FF FF FF 12 FF FF 50 FF FF FF FF")

        assertEquals(255, result.measurements.batteryPercent)
        assertEquals(655.35, result.measurements.humidityPercent!!, 0.0)
        assertEquals(167772.15, result.measurements.pressureHectopascals!!, 0.0)
        assertEquals(65535, result.measurements.carbonDioxidePpm)
        assertEquals(4_294_967_295_000L, result.metadata.timestampEpochMillis)
    }

    @Test
    fun `exposes trigger based flag`() {
        val result = decoded("44 01 64")

        assertTrue(result.deviceInformation.triggerBased)
    }

    private fun decoded(hex: String): DecodedBTHomeAdvertisement =
        (parser.parse(0xFCD2, bytes(hex)) as BTHomeParseResult.Decoded).advertisement

    private fun bytes(hex: String): ByteArray = hex.split(" ")
        .filter(String::isNotBlank)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
