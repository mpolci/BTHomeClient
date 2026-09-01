package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPlaceholderScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun placeholderShowsAddressDeferredCopyAndBackAction() {
        var backCalls = 0
        compose.setContent { HistoryPlaceholderScreen("AA:BB:CC:DD:EE:FF") { backCalls++ } }
        compose.onNodeWithText("Sensor: AA:BB:CC:DD:EE:FF").assertIsDisplayed()
        compose.onNodeWithText("History visualization is deferred in the first release. Readings and history APIs are prepared and stored locally.").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, backCalls)
    }
}
