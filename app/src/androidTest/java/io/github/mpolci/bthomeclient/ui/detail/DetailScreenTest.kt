package io.github.mpolci.bthomeclient.ui.detail

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.bthome.*
import io.github.mpolci.bthomeclient.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun detailScreenCyclesStatesAndRoutesCallbacks() {
        lateinit var state: MutableState<DetailState>
        var save = 0; var history = 0; var choose = 0; var remove = 0
        compose.setContent { state = mutableStateOf(DetailState()); DetailScreen(state.value, {}, { state.value = state.value.copy(displayName = it) }, { state.value = state.value.copy(room = it) }, { choose++ }, { remove++; state.value = state.value.copy(imageUri = null) }, { save++ }, { history++ }) }
        compose.onNodeWithText("Loading sensor…").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(loading = false, notFound = true) }
        compose.onNodeWithText("Registered sensor not found.").assertIsDisplayed()
        compose.runOnIdle { state.value = contentState() }
        compose.onNodeWithText("Offline · Unsupported").assertIsDisplayed()
        compose.onNodeWithText("Temperature: 21.50 °C").assertIsDisplayed()
        compose.onNodeWithText("Humidity: 55.00 %").assertIsDisplayed()
        compose.onNodeWithText("Battery: 80%").assertIsDisplayed()
        compose.onNodeWithText("Address: AA:BB:CC:DD:EE:99", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Diagnostic: None", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("detail-name").performTextInput(" edited")
        compose.onNodeWithTag("detail-room").performTextInput(" lab")
        compose.onNodeWithText("Choose image").performClick(); assertEquals(1, choose)
        compose.runOnIdle { state.value = state.value.copy(imageUri = "content://invalid") }
        compose.onNodeWithText("Remove image").performClick(); assertEquals(1, remove)
        compose.onNodeWithTag("detail-save").performClick(); compose.onNodeWithTag("detail-history").performClick()
        assertEquals(1, save); assertEquals(1, history)
        compose.runOnIdle { state.value = state.value.copy(error = "Unable to save sensor.", isSaving = true) }
        compose.onNodeWithText("Unable to save sensor.").assertIsDisplayed()
        compose.onNodeWithTag("detail-save").assertIsNotEnabled()
    }
    private fun contentState(): DetailState {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:99")
        val registered = RegisteredSensor(address, "Stored", null, null, 1, null, 0, null, 2, false, false, null, null, "NoSupportedMeasurements", SensorSupportStatus.Unsupported, emptySet())
        val observation = ScanObservation(address, "Live", -50, 1, BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x03, 0x7C, 0x15)))
        val latest = SensorReading(address, 0, null, BTHomeMeasurements(temperatureCelsius = 21.5, batteryPercent = 80), null)
        return DetailState(sensor = MergedSensor(address, registered, observation, false, SensorSupportStatus.Unsupported, latest), displayName = "Stored")
    }
}
