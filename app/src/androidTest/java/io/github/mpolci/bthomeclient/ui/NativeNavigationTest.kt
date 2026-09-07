package io.github.mpolci.bthomeclient.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.BTHomeApplication
import io.github.mpolci.bthomeclient.MainActivity
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.data.repository.HistoryUpdateState
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses an isolated synthetic address through the real application routes; no real sensor is modified. */
@RunWith(AndroidJUnit4::class)
class NativeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun liveSensorEditingAndHistoryRestoreTheirRoutesAndOrientation() {
        val container = (compose.activity.application as BTHomeApplication).appContainer
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10).uppercase().chunked(2).joinToString(":")
        val address = SensorAddress.require("FE:$suffix")
        val name = "Navigation fixture"
        val changed = "Edited navigation fixture"
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        try {
            runBlocking {
                container.sensorCoordinator.observe(ScanObservation(address, "Fixture broadcast", -50, System.currentTimeMillis(), BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x50, 0x02, 0xC4.toByte(), 0x09))))
                container.sensorCoordinator.register(address, name)
                withTimeout(5_000) { container.sensorCoordinator.sensor(address).first { it?.isRegistered == true } }
            }
            compose.onNodeWithTag("sensor-body-${address.value}").performScrollTo().performClick()
            compose.onNodeWithTag("detail-edit-name").performClick()
            compose.onNodeWithTag("detail-field-input").performTextReplacement(changed)
            compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
            compose.waitUntil(5_000) { container.sensorCoordinator.sensors.value.any { it.address == address && it.registeredSensor?.displayName == changed } }
            compose.onNodeWithTag("detail-history").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.waitUntil(5_000) { container.historySession.updates.value.containsKey(address) }
            compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithTag("detail-edit-name").assertExists()
            compose.waitUntil(5_000) {
                val update = container.historySession.updates.value[address]
                update == HistoryUpdateState.Idle || update is HistoryUpdateState.Failed
            }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText(changed).assertExists()
            runBlocking {
                assertEquals("Fixture broadcast", container.sensorCoordinator.sensor(address).first()!!.registeredSensor!!.advertisedName)
            }
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithText("History", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithTag("history-overview").assertExists()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
            val overviewSession = container.historySession.sessionId.value
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("history-overview").assertExists()
            assertEquals(overviewSession, container.historySession.sessionId.value)
            compose.onNodeWithTag("history-sensor-${address.value}").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("history-detail").assertExists()
            assertEquals(overviewSession, container.historySession.sessionId.value)
            compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithTag("history-overview").assertExists()
            assertEquals(overviewSession, container.historySession.sessionId.value)
            compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
            compose.onNodeWithTag("sensor-body-${address.value}").assertExists()
        } finally {
            container.historySession.end()
            runBlocking { container.sensorCoordinator.deleteRegistration(address) }
        }
    }
}
