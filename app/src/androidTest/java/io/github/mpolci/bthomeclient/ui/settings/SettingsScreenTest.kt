package io.github.mpolci.bthomeclient.ui.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun settingsScreenSelectsPendingValuesAndReflectsSaveStates() {
        lateinit var state: MutableState<SettingsState>
        var saves = 0
        compose.setContent {
            state = mutableStateOf(SettingsState(current = AppSettings(), pending = AppSettings()))
            SettingsScreen(state.value, SettingsViewModel.intervals, SettingsViewModel.retentions, {}, { state.value = state.value.copy(pending = state.value.pending.copy(minimumPersistenceIntervalMillis = it), edited = true) }, { state.value = state.value.copy(pending = state.value.pending.copy(retentionDurationMillis = it), edited = true) }, { saves++ })
        }
        compose.onNodeWithText("These global settings affect persistence for registered sensors only.").assertIsDisplayed()
        listOf("1 minutes", "5 minutes", "15 minutes", "30 minutes", "60 minutes", "Unlimited", "7 days", "30 days", "90 days", "365 days").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("settings-interval-900000").assertIsSelected()
        compose.onNodeWithTag("settings-retention-unlimited").assertIsSelected()
        compose.onNodeWithTag("settings-interval-300000").performClick()
        compose.onNodeWithTag("settings-retention-2592000000").performClick()
        compose.onNodeWithTag("settings-interval-300000").assertIsSelected()
        compose.onNodeWithTag("settings-retention-2592000000").assertIsSelected()
        compose.onNodeWithTag("settings-save").performClick(); assertEquals(1, saves)
        compose.runOnIdle { state.value = state.value.copy(error = "Unable to save settings.", isSaving = true) }
        compose.onNodeWithText("Unable to save settings.").assertIsDisplayed()
        compose.onNodeWithTag("settings-save").assertIsNotEnabled()
    }
}
