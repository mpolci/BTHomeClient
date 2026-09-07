package io.github.mpolci.bthomeclient.ui.main

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MainFormattersTest {
    @Test
    fun `reading age keeps original time and handles units and future clocks`() {
        assertEquals("Received 5 minutes ago", formatReadingAge(1_000, 301_000))
        assertEquals("Received 1 hour ago", formatReadingAge(0, 3_600_000))
        assertEquals("Received 2 days ago", formatReadingAge(0, 172_800_000))
        assertEquals("Received 0 seconds ago", formatReadingAge(2_000, 1_000))
    }
    @Test
    fun `diagnostic label exposes typed diagnostics but never raw service data`() {
        val label = diagnosticLabel(
            io.github.mpolci.bthomeclient.bthome.BTHomeParseResult.Unsupported(
                reason = io.github.mpolci.bthomeclient.bthome.BTHomeUnsupportedReason.NoSupportedMeasurements,
                diagnostics = listOf(io.github.mpolci.bthomeclient.bthome.BTHomeDiagnostic.UnsupportedObject(0x99)),
                rawServiceDataHex = "DE AD BE EF",
            ),
        )
        assertEquals(true, label.contains("NoSupportedMeasurements"))
        assertEquals(true, label.contains("153"))
        assertEquals(false, label.contains("DE AD BE EF"))
        assertEquals(false, label.contains("rawServiceDataHex"))
        assertEquals("TruncatedObject", diagnosticLabel(io.github.mpolci.bthomeclient.bthome.BTHomeParseResult.Malformed(io.github.mpolci.bthomeclient.bthome.BTHomeMalformedReason.TruncatedObject, "AA")))
    }

    @Test
    fun `formats timestamps deterministically`() {
        assertEquals("1970-01-01 00:00:00", formatLastSeen(0, ZoneId.of("UTC")))
        assertEquals("—", formatLastSeen(null, ZoneId.of("UTC")))
    }
}
