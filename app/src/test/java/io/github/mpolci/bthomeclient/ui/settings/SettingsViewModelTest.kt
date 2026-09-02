package io.github.mpolci.bthomeclient.ui.settings

import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `invalid selection and save failure keep the pending value for retry`() = runTest(dispatcher) {
        val manager = Manager(AppSettings())
        val viewModel = SettingsViewModel(manager)
        runCurrent()

        viewModel.edit(1)
        assertEquals("Invalid settings selection.", viewModel.state.value.error)
        val pending = AppSettings(604_800_000)
        viewModel.edit(pending.retentionDurationMillis)
        manager.failSave = true
        viewModel.save()
        runCurrent()

        assertEquals(1, manager.saveCalls)
        assertEquals(false, viewModel.state.value.isSaving)
        assertEquals("Unable to save settings.", viewModel.state.value.error)
        assertEquals(pending, viewModel.state.value.pending)
    }

    @Test
    fun `save guard and success settle the state`() = runTest(dispatcher) {
        val manager = Manager(AppSettings())
        val viewModel = SettingsViewModel(manager)
        runCurrent()
        val saved = AppSettings(2_592_000_000)
        viewModel.edit(saved.retentionDurationMillis)
        val event = async { viewModel.events.first() }
        val gate = CompletableDeferred<Unit>()
        manager.saveGate = gate

        viewModel.save()
        viewModel.save()
        runCurrent()
        assertEquals(1, manager.saveCalls)
        gate.complete(Unit)
        runCurrent()

        assertEquals(io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Saved, event.await())
        assertEquals(saved, viewModel.state.value.current)
        assertEquals(false, viewModel.state.value.edited)
    }

    @Test
    fun `retention options and pending selection survive external updates`() = runTest(dispatcher) {
        val manager = Manager(AppSettings())
        val viewModel = SettingsViewModel(manager)
        runCurrent()

        assertEquals(listOf<Long?>(null, 604_800_000L, 2_592_000_000L, 7_776_000_000L, 31_536_000_000L), SettingsViewModel.retentions)
        viewModel.edit(604_800_000)
        manager.settingsState.value = AppSettings(2_592_000_000)
        runCurrent()

        assertEquals(AppSettings(2_592_000_000), viewModel.state.value.current)
        assertEquals(AppSettings(604_800_000), viewModel.state.value.pending)
        assertEquals(true, viewModel.state.value.edited)
    }

    private class Manager(initial: AppSettings) : SensorManager {
        val settingsState = MutableStateFlow(initial)
        var saveCalls = 0
        var saveGate: CompletableDeferred<Unit>? = null
        var failSave = false

        override val sensors: StateFlow<List<MergedSensor>> = MutableStateFlow(emptyList())
        override val settings: Flow<AppSettings> = settingsState
        override fun sensor(address: SensorAddress): Flow<MergedSensor?> = MutableStateFlow(null)
        override suspend fun observe(advertisement: ScanObservation) = Unit
        override suspend fun register(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun deleteRegistration(address: SensorAddress) = Unit
        override suspend fun saveSettings(settings: AppSettings) {
            saveCalls += 1
            saveGate?.await()
            if (failSave) error("failure")
            settingsState.value = settings
        }
    }
}
