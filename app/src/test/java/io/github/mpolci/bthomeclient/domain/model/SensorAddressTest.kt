package io.github.mpolci.bthomeclient.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorAddressTest {
    @Test
    fun `normalizes lowercase and hyphen separated addresses`() {
        assertEquals("AA:BB:CC:DD:EE:FF", SensorAddress.require("aa-bb-cc-dd-ee-ff").value)
    }

    @Test
    fun `rejects malformed addresses`() {
        assertNull(SensorAddress.from("AA:BB:CC"))
        assertNull(SensorAddress.from("GG:BB:CC:DD:EE:FF"))
    }
}
