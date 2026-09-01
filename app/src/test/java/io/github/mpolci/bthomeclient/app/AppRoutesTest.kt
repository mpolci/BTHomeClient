package io.github.mpolci.bthomeclient.app

import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRoutesTest {
    @Test
    fun routesRoundTripAddressAndRejectInvalidTokens() {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:FF")
        listOf(AppRoutes.registration(address), AppRoutes.detail(address), AppRoutes.history(address)).forEach { route ->
            assertEquals(address, AppRoutes.address(route.substringAfter('/')))
        }
        listOf<String?>(null, "AABB", "AABBCCDDEEFG", "AABBCCDDEEFF00", "aaBBCCDDEEFF", "AABBCCDDEEFF/extra").forEach {
            assertNull(AppRoutes.address(it))
        }
    }
}
