package io.github.mpolci.bthomeclient.ble

import io.github.mpolci.bthomeclient.bthome.BTHomeParseResult
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdvertisementMapperTest {
    @Test
    fun `rejects malformed address but preserves wrong UUID diagnostic for valid address`() {
        val mapper = BleAdvertisementMapper(BTHomeV2Parser())
        assertEquals(null, mapper.map("not-an-address", null, -1, 1, 0xFCD2, bytes("40 01 64")))

        val event = mapper.map("AA:BB:CC:DD:EE:01", null, -1, 1, 0x180F, bytes("40 01 64")) as ScanEvent.Advertisement
        val result = event.observation.parseResult as BTHomeParseResult.Unsupported
        assertEquals(io.github.mpolci.bthomeclient.bthome.BTHomeUnsupportedReason.WrongServiceUuid, result.reason)
    }

    @Test
    fun `maps and normalizes official BTHome temperature humidity advertisement`() {
        val event = BleAdvertisementMapper(BTHomeV2Parser()).map(
            address = "aa-bb-cc-dd-ee-ff",
            name = "Environment",
            rssi = -47,
            receptionEpochMillis = 1_234L,
            serviceUuid = 0xFCD2,
            serviceData = bytes("40 02 C4 09 03 BF 13"),
        ) as ScanEvent.Advertisement

        val observation = event.observation
        val decoded = observation.parseResult as BTHomeParseResult.Decoded
        assertEquals("AA:BB:CC:DD:EE:FF", observation.address.value)
        assertEquals("Environment", observation.advertisedName)
        assertEquals(-47, observation.rssi)
        assertEquals(1_234L, observation.receptionEpochMillis)
        assertEquals(2, decoded.advertisement.deviceInformation.version)
        assertTrue(!decoded.advertisement.deviceInformation.encrypted)
        assertEquals(25.0, decoded.advertisement.measurements.temperatureCelsius!!, 0.0)
        assertEquals(50.55, decoded.advertisement.measurements.humidityPercent!!, 0.0)
    }

    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
