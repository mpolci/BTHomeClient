package io.github.mpolci.bthomeclient.ui.registration

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun registrationScreenEditsAndReflectsImageErrorAndSavingState() {
        lateinit var state: MutableState<RegistrationState>
        var saveCalls = 0
        var chooseCalls = 0
        var removeCalls = 0
        compose.setContent {
            state = mutableStateOf(RegistrationState(address = SensorAddress.require("AA:BB:CC:DD:EE:FF")))
            RegistrationScreen(
                state = state.value,
                onBack = {},
                onNameChange = { state.value = state.value.copy(displayName = it) },
                onRoomChange = { state.value = state.value.copy(room = it) },
                onChooseImage = { chooseCalls++ },
                onRemoveImage = { removeCalls++; state.value = state.value.copy(imageUri = null) },
                onSave = { saveCalls++ },
            )
        }
        compose.onNodeWithText("Address: AA:BB:CC:DD:EE:FF").assertIsDisplayed()
        compose.onNodeWithText("Not currently visible").assertIsDisplayed()
        compose.onNodeWithTag("registration-save").assertIsNotEnabled()
        compose.onNodeWithTag("registration-name").performTextInput("Kitchen")
        compose.onNodeWithTag("registration-room").performTextInput("Lab")
        compose.onNodeWithTag("registration-save").assertIsEnabled().performClick()
        assertEquals(1, saveCalls)
        compose.runOnIdle { state.value = state.value.copy(imageUri = "content://invalid/image") }
        compose.onNodeWithText("Replace image").performClick()
        assertEquals(1, chooseCalls)
        compose.onNodeWithText("Remove image").performClick()
        assertEquals(1, removeCalls)
        compose.onNodeWithTag("sensor-image-placeholder").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Unable to save sensor.") }
        compose.onNodeWithText("Unable to save sensor.").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(isSaving = true) }
        compose.onNodeWithTag("registration-save").assertIsNotEnabled()
    }
}
