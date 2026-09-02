package io.github.mpolci.bthomeclient.ui.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenExpansionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun expansionIsChevronOnlyAndBodiesRouteToCorrectCallbacks() {
        val parser = BTHomeV2Parser()
        val unregisteredAddress = SensorAddress.require("AA:BB:CC:DD:EE:B1")
        val registeredAddress = SensorAddress.require("AA:BB:CC:DD:EE:B2")
        val unregistered = MergedSensor(unregisteredAddress, null, ScanObservation(unregisteredAddress, "Guest", -50, 1, parser.parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64))), true, SensorSupportStatus.Supported)
        val registered = MergedSensor(registeredAddress, RegisteredSensor(registeredAddress, "Known", null, null, 1, null, 1, -50, 2, false, false, null, null, null, SensorSupportStatus.Supported, emptySet()), null, true, SensorSupportStatus.Supported)
        var registeredClicks = 0
        var detailsClicks = 0
        compose.setContent { MainScreen(MainScanState(sensors = listOf(unregistered, registered)), {}, {}, {}, {}, {}, { registeredClicks++ }, { detailsClicks++ }) }
        compose.onNodeWithContentDescription("Expand Guest").performClick()
        compose.onNodeWithText("Address: ${unregisteredAddress.value}", substring = true).assertIsDisplayed()
        assertEquals(0, registeredClicks)
        assertEquals(0, detailsClicks)
        compose.onNodeWithTag("sensor-body-${unregisteredAddress.value}").performClick()
        assertEquals(1, registeredClicks)
        assertEquals(0, detailsClicks)
        compose.onNodeWithTag("sensor-body-${registeredAddress.value}").performClick()
        assertEquals(1, detailsClicks)
        compose.onNodeWithContentDescription("Collapse Guest").performClick()
        compose.onAllNodesWithText("Address: ${unregisteredAddress.value}", substring = true).assertCountEquals(0)
    }
}
