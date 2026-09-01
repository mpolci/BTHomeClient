package io.github.mpolci.bthomeclient.ui.main

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenCardOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun suppliedCardsKeepOrderAndStatusLabels() {
        val parser = BTHomeV2Parser()
        val registeredAddress = SensorAddress.require("AA:BB:CC:DD:EE:A1")
        val first = MergedSensor(registeredAddress, RegisteredSensor(registeredAddress, "Offline registered", null, null, 1, null, 0, null, 2, false, false, null, null, null, SensorSupportStatus.Supported, emptySet()), null, false, SensorSupportStatus.Supported, null)
        val secondAddress = SensorAddress.require("AA:BB:CC:DD:EE:A2")
        val second = MergedSensor(secondAddress, null, ScanObservation(secondAddress, "Online unregistered", -50, 1, parser.parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64))), true, SensorSupportStatus.Supported, null)
        val thirdAddress = SensorAddress.require("AA:BB:CC:DD:EE:A3")
        val third = MergedSensor(thirdAddress, null, ScanObservation(thirdAddress, "Unsupported unregistered", -50, 1, parser.parse(0xFCD2, byteArrayOf(0x40, 0x00, 0x01))), true, SensorSupportStatus.Unsupported, null)
        compose.setContent { MainScreen(MainScanState(sensors = listOf(first, second, third)), {}, {}, {}, {}, {}, {}, {}) }
        val firstBounds = compose.onNodeWithTag("sensor-${registeredAddress.value}").fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("sensor-${secondAddress.value}").fetchSemanticsNode().boundsInRoot
        val thirdBounds = compose.onNodeWithTag("sensor-${thirdAddress.value}").fetchSemanticsNode().boundsInRoot
        assertTrue(firstBounds.top < secondBounds.top && secondBounds.top < thirdBounds.top)
        compose.onNodeWithText("Registered · Offline").assertIsDisplayed()
        compose.onNodeWithText("Unregistered · Online").assertIsDisplayed()
        compose.onNodeWithText("Unregistered · Unsupported").assertIsDisplayed()
    }
}
