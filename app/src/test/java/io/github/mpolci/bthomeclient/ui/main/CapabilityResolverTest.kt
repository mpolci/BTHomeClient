package io.github.mpolci.bthomeclient.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityResolverTest {
    @Test
    fun resolvesCapabilityFactsInPriorityOrder() {
        assertEquals(ScanCapability.BleUnsupported, resolveCapability(CapabilityFacts(false, emptyList(), false, true)))
        assertEquals(ScanCapability.PermissionMissing, resolveCapability(CapabilityFacts(true, listOf(false, false), false, true)))
        assertEquals(ScanCapability.PermissionMissing, resolveCapability(CapabilityFacts(true, listOf(true), true, true)))
        assertEquals(ScanCapability.PermissionPermanentlyDenied, resolveCapability(CapabilityFacts(true, listOf(false), true, true)))
        assertEquals(ScanCapability.PermissionPermanentlyDenied, resolveCapability(CapabilityFacts(true, listOf(true, false), true, true)))
        assertEquals(ScanCapability.BluetoothDisabled, resolveCapability(CapabilityFacts(true, emptyList(), false, false)))
        assertEquals(ScanCapability.Ready, resolveCapability(CapabilityFacts(true, emptyList(), false, true)))
    }
}
