package io.github.mpolci.bthomeclient.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenExpansionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allMeasurementsAreVisibleWithoutExpansionAndCardsKeepTheirCallbacks() {
        val registeredAddress = SensorAddress.require("AA:BB:CC:DD:EE:B2")
        val registered = registered(registeredAddress, 1_000)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:B1")
        val guest = MergedSensor(address, null, ScanObservation(address, "Guest", -50, 1_000, BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64))), true, SensorSupportStatus.Supported)
        var registrationClicks = 0
        var detailsClicks = 0
        compose.setContent { MainScreen(MainScanState(sensors = listOf(registered, guest), capability = ScanCapability.Ready), {}, {}, {}, {}, {}, { registrationClicks++ }, { detailsClicks++ }, nowEpochMillis = 2_000) }
        compose.onNodeWithText("Temperature").assertIsDisplayed()
        compose.onNodeWithText("Humidity").assertIsDisplayed()
        compose.onNodeWithText("Pressure").assertIsDisplayed()
        compose.onNodeWithText("CO₂").assertIsDisplayed()
        compose.onNodeWithText("Above the wardrobe").assertIsDisplayed()
        compose.onNodeWithText("%.1f °C".format(25.0)).assertIsDisplayed()
        compose.onAllNodesWithText("86%", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Received", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Expand Cabin").assertDoesNotExist()
        compose.onNodeWithTag("sensor-body-${registeredAddress.value}").performClick()
        compose.onNodeWithTag("sensor-body-${address.value}").performScrollTo().performClick()
        assertEquals(1, detailsClicks)
        assertEquals(1, registrationClicks)
    }

    @Test
    fun savedReadingsRemainVisibleWithTheirAgeWhileBluetoothIsOff() {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:B3")
        val sensor = registered(address, 1_000).copy(isOnline = false)
        compose.setContent { MainScreen(MainScanState(sensors = listOf(sensor), capability = ScanCapability.BluetoothDisabled), {}, {}, {}, {}, {}, {}, {}, nowEpochMillis = 301_000) }
        compose.onNodeWithText("Enable Bluetooth").assertIsDisplayed()
        compose.onNodeWithText("%.1f °C".format(25.0)).assertIsDisplayed()
        compose.onNodeWithText("Received 5 minutes ago").assertIsDisplayed()
    }

    private fun registered(address: SensorAddress, time: Long): MergedSensor {
        val record = RegisteredSensor(address, "Cabin", "Above the wardrobe", null, 1, null, time, -50, 2, false, false, null, null, null, SensorSupportStatus.Supported, emptySet(),
            LatestReading(BTHomeMeasurements(86, 25.0, 50.0, 1013.0, 750), time))
        return MergedSensor(address, record, null, true, SensorSupportStatus.Supported)
    }
}
