package io.github.mpolci.bthomeclient.ui.detail

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

    @Test fun unregisteredSensorShowsIdentityReadingsAndRegistrationWithoutEditing() {
        var registrations = 0
        val registered = contentState()
        val state = registered.copy(sensor = registered.sensor!!.copy(registeredSensor = null), displayName = "Broadcast", room = "", imageUri = null)
        compose.setContent { DetailScreen(state, {}, {}, {}, {}, {}, { registrations++ }, {}, {}, nowEpochMillis = 121_000L) }
        compose.onNodeWithText("Not registered").assertIsDisplayed()
        compose.onNodeWithText("Humidity").assertIsDisplayed()
        compose.onNodeWithText("Received 21 seconds ago").assertIsDisplayed()
        compose.onNodeWithText("Sensor battery: 86%").assertIsDisplayed()
        compose.onNodeWithText("AA:BB:CC:DD:EE:99").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not checked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-edit-name").assertDoesNotExist()
        compose.onNodeWithTag("detail-delete").assertDoesNotExist()
        compose.onNodeWithTag("detail-history").assertDoesNotExist()
        compose.onNodeWithTag("detail-register").performScrollTo().performClick()
        assertEquals(1, registrations)
    }

    @Test fun fieldDialogCancelDoesNotSaveAndSuccessClosesOnlyThatDialog() {
        val state = mutableStateOf(contentState())
        var savedName: String? = null
        var savedRoom: String? = null
        compose.setContent { DetailScreen(state.value, {}, { savedName = it }, { savedRoom = it }, {}, {}, {}, {}, {}, nowEpochMillis = 121_000L) }
        compose.onNodeWithContentDescription("Edit local name").performClick()
        compose.onNodeWithTag("detail-field-input").performTextReplacement("Changed")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, savedName)
        compose.onNodeWithText("Stored").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit local name").performClick()
        compose.onNodeWithTag("detail-field-input").performTextReplacement(" ")
        compose.onNodeWithTag("detail-field-save").assertIsNotEnabled()
        compose.onNodeWithTag("detail-field-input").performTextReplacement("Changed")
        compose.onNodeWithTag("detail-field-save").performClick()
        assertEquals("Changed", savedName)
        assertEquals(null, savedRoom)
        compose.runOnIdle { state.value = state.value.copy(isSaving = false, error = "Unable to save sensor. Try again.") }
        compose.onNodeWithTag("detail-field-input").assertTextContains("Changed")
        compose.runOnIdle { state.value = state.value.copy(displayName = "Changed", saveVersion = 1, error = null) }
        compose.onNodeWithTag("detail-field-input").assertDoesNotExist()
        compose.onNodeWithText("Changed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit placement").performClick()
        compose.onNodeWithTag("detail-field-input").performTextClearance()
        compose.onNodeWithTag("detail-field-save").performClick()
        assertEquals("", savedRoom)
    }

    @Test fun photoActionsAndRemovalConfirmationAreExplicit() {
        val state = mutableStateOf(contentState())
        var chosen = 0
        var removedPhoto = 0
        var removedSensor = 0
        compose.setContent { DetailScreen(state.value, {}, {}, {}, { chosen++ }, { removedPhoto++ }, {}, { removedSensor++ }, {}, nowEpochMillis = 121_000L) }
        compose.onNodeWithTag("detail-photo").performClick()
        assertEquals(1, chosen)
        compose.runOnIdle { state.value = state.value.copy(imageUri = "content://invalid") }
        compose.onNodeWithTag("detail-remove-photo").performScrollTo().performClick()
        assertEquals(1, removedPhoto)
        compose.onNodeWithTag("detail-delete").performScrollTo().performClick()
        compose.onNodeWithText("Remove Stored?").assertIsDisplayed()
        compose.onNodeWithText("History stored on the sensor will not be erased.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, removedSensor)
        compose.onNodeWithTag("detail-delete").performClick()
        compose.onNodeWithTag("detail-confirm-delete").performClick()
        assertEquals(1, removedSensor)
    }

    @Test fun historyCapabilityIsIndependentOfAdvertisementParsingAndSavedReadingIsVisible() {
        val initial = contentState()
        val state = mutableStateOf(initial.copy(sensor = initial.sensor!!.copy(observation = null, supportStatus = SensorSupportStatus.Unsupported)))
        var history = 0
        compose.setContent { DetailScreen(state.value, {}, {}, {}, {}, {}, {}, {}, { history++ }, nowEpochMillis = 400_000L) }
        compose.onNodeWithText("Humidity").assertIsDisplayed()
        compose.onNodeWithText("Received 5 minutes ago").assertIsDisplayed()
        compose.onNodeWithText("Check history").performScrollTo().performClick()
        assertEquals(1, history)
        compose.runOnIdle {
            val sensor = state.value.sensor!!
            state.value = state.value.copy(sensor = sensor.copy(registeredSensor = sensor.registeredSensor!!.copy(historySupport = HistorySupport.Unsupported)))
        }
        compose.onNodeWithTag("detail-history").assertDoesNotExist()
        compose.onNodeWithText("Not supported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-edit-name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-delete").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingBatteryIsOmittedAndAbsentSensorHasBackAction() {
        val state = mutableStateOf(contentState())
        val sensor = state.value.sensor!!
        val observation = sensor.observation!!.copy(parseResult = BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x02, 0x2E, 0x09)))
        state.value = state.value.copy(sensor = sensor.copy(observation = observation))
        var back = 0
        compose.setContent { DetailScreen(state.value, { back++ }, {}, {}, {}, {}, {}, {}, {}, nowEpochMillis = 121_000L) }
        compose.onNodeWithText("Sensor battery:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Temperature").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(sensor = null, notFound = true) }
        compose.onNodeWithText("Sensor is no longer nearby.").assertIsDisplayed()
        compose.onNodeWithText("Back to Live").performClick()
        assertEquals(1, back)
    }

    private fun contentState(): DetailState {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:99")
        val observation = ScanObservation(address, "Broadcast", -50, 100_000, BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x56, 0x03, 0x7C, 0x15)))
        val reading = LatestReading(observation.measurements!!, observation.receptionEpochMillis)
        val registered = RegisteredSensor(address, "Stored", null, null, 1, "Broadcast", 100_000, -50, 2, false, false, null, null, null, SensorSupportStatus.Supported, emptySet(), latestReading = reading)
        return DetailState(sensor = MergedSensor(address, registered, observation, true, SensorSupportStatus.Supported), loading = false, displayName = "Stored")
    }
}
