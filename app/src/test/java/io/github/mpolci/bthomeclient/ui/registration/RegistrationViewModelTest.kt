package io.github.mpolci.bthomeclient.ui.registration

import androidx.lifecycle.ViewModel
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.ui.editor.EditorEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    @Test
    fun registrationValidationAndDoubleSaveGuardKeepUserFieldsOnFailure() = runTest(dispatcher) {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:F0")
        val manager = FakeManager()
        val viewModel = RegistrationViewModel(address, manager)
        runCurrent()
        viewModel.edit(displayName = "", room = "Office")
        viewModel.save()
        runCurrent()
        assertEquals("A display name is required.", viewModel.state.value.error)
        assertEquals(0, manager.calls)
        val gate = CompletableDeferred<Unit>()
        manager.gate = gate
        manager.failRegister = true
        viewModel.edit(displayName = "Saved name", room = "Office")
        viewModel.save()
        viewModel.save()
        runCurrent()
        assertEquals(1, manager.calls)
        assertEquals(true, viewModel.state.value.isSaving)
        gate.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.state.value.isSaving)
        assertEquals("Unable to save sensor.", viewModel.state.value.error)
        assertEquals("Saved name", viewModel.state.value.displayName)
        assertEquals("Office", viewModel.state.value.room)
        assertEquals(emptyList<EditorEvent>(), viewModel.events.replayCache)
    }

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun registrationInitializesWithoutOverwritingEditsAndSaves() = runTest(dispatcher) {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:FF")
        val manager = FakeManager()
        val viewModel = RegistrationViewModel(address, manager)
        runCurrent()
        assertEquals("Sensor EE:FF", viewModel.state.value.displayName)
        assertEquals(false, viewModel.state.value.loading)
        viewModel.edit(room = "Kitchen")
        manager.sensorState.value = sensor(address, "Advertised")
        runCurrent()
        assertEquals("Advertised", viewModel.state.value.displayName)
        assertEquals("Kitchen", viewModel.state.value.room)
        viewModel.edit(displayName = "User name")
        manager.sensorState.value = sensor(address, "New advertised")
        runCurrent()
        assertEquals("User name", viewModel.state.value.displayName)
        assertEquals("New advertised", viewModel.state.value.sensor!!.observation!!.advertisedName)
        val event = async { viewModel.events.first() }
        viewModel.save()
        runCurrent()
        assertEquals(listOf("AA:BB:CC:DD:EE:FF", "User name", "Kitchen", null), manager.registered)
        assertEquals(EditorEvent.Saved, event.await())
        assertEquals(false, viewModel.state.value.isSaving)
    }

    private fun sensor(address: SensorAddress, name: String) = MergedSensor(address, null, ScanObservation(address, name, -50, 1, BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64))), true, SensorSupportStatus.Supported, null)

    private class FakeManager : SensorManager {
        val sensorState = MutableStateFlow<MergedSensor?>(null)
        var registered: List<Any?> = emptyList()
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var failRegister = false
        override val sensors: StateFlow<List<MergedSensor>> = MutableStateFlow(emptyList())
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override fun sensor(address: SensorAddress): Flow<MergedSensor?> = sensorState
        override suspend fun observe(advertisement: ScanObservation) = Unit
        override suspend fun register(address: SensorAddress, displayName: String, room: String?, imageUri: String?) {
            calls += 1
            gate?.await()
            if (failRegister) error("failure")
            registered = listOf(address.value, displayName, room, imageUri)
        }
        override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun deleteRegistration(address: SensorAddress) = Unit
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }
}
