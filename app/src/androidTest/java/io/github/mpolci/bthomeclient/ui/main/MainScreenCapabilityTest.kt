package io.github.mpolci.bthomeclient.ui.main

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.ble.ScanFailure
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenCapabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capabilityStatesShowFriendlyActionsAndRouteCallbacks() {
        var allow = 0
        var settings = 0
        var bluetooth = 0
        var retry = 0
        lateinit var state: MutableState<MainScanState>
        compose.setContent {
            state = mutableStateOf(MainScanState())
            MainScreen(state.value, { allow++ }, { settings++ }, { bluetooth++ }, { retry++ }, { settings++ }, {}, {})
        }
        fun show(next: MainScanState) { compose.runOnIdle { state.value = next }; compose.waitForIdle() }
        show(MainScanState(capability = ScanCapability.Checking))
        compose.onNodeWithText("Checking Bluetooth capability…").assertExists()
        show(MainScanState(capability = ScanCapability.PermissionMissing))
        compose.onNodeWithText("Allow").performClick(); assertEquals(1, allow)
        show(MainScanState(capability = ScanCapability.PermissionPermanentlyDenied))
        compose.onNodeWithText("Open settings").performClick(); assertEquals(1, settings)
        show(MainScanState(capability = ScanCapability.BluetoothDisabled))
        compose.onNodeWithText("Enable Bluetooth").performClick(); assertEquals(1, bluetooth)
        show(MainScanState(capability = ScanCapability.BleUnsupported))
        compose.onNodeWithText("Bluetooth Low Energy is not supported on this device.").assertExists()
        show(MainScanState(capability = ScanCapability.Ready, failure = ScanFailure.Platform(3)))
        compose.onNodeWithText("Retry").performClick(); assertEquals(1, retry)
        show(MainScanState(capability = ScanCapability.Ready, failure = ScanFailure.PermissionDenied))
        compose.onNodeWithText("Allow").performClick(); assertEquals(2, allow)
        show(MainScanState(capability = ScanCapability.Ready, failure = ScanFailure.BluetoothDisabled))
        compose.onNodeWithText("Enable Bluetooth").performClick(); assertEquals(2, bluetooth)
        compose.onNodeWithText("Settings").performClick(); assertEquals(2, settings)
    }
}
