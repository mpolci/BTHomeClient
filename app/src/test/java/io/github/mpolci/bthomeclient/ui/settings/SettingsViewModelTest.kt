package io.github.mpolci.bthomeclient.ui.settings

import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
    @Test fun invalidSelectionAndSaveFailureKeepPendingForRetry() = runTest(dispatcher) {
        val manager = Manager(AppSettings())
        val viewModel = SettingsViewModel(manager)
        runCurrent()
        val before = viewModel.state.value.pending
        viewModel.edit(2, 3)
        assertEquals("Invalid settings selection.", viewModel.state.value.error); assertEquals(before, viewModel.state.value.pending); assertEquals(0, manager.saveCalls)
        val pending = AppSettings(60_000, 604_800_000)
        viewModel.edit(pending.minimumPersistenceIntervalMillis, pending.retentionDurationMillis)
        manager.failSave = true
        viewModel.save(); runCurrent()
        assertEquals(1, manager.saveCalls); assertEquals(false, viewModel.state.value.isSaving); assertEquals("Unable to save settings.", viewModel.state.value.error); assertEquals(pending, viewModel.state.value.pending); assertEquals(true, viewModel.state.value.edited); assertEquals(emptyList<io.github.mpolci.bthomeclient.ui.editor.EditorEvent>(), viewModel.events.replayCache)
    }
    @Test fun settingsSaveGuardAndSuccessSettleState() = runTest(dispatcher) {
        val manager = Manager(AppSettings())
        val viewModel = SettingsViewModel(manager)
        runCurrent()
        val saved = AppSettings(60_000, 604_800_000)
        viewModel.edit(saved.minimumPersistenceIntervalMillis, saved.retentionDurationMillis)
        val gate = CompletableDeferred<Unit>(); manager.saveGate = gate
        val event = async { viewModel.events.first() }
        viewModel.save(); viewModel.save(); runCurrent()
        assertEquals(1, manager.saveCalls); assertEquals(saved, manager.saved); assertEquals(true, viewModel.state.value.isSaving)
        gate.complete(Unit); runCurrent()
        assertEquals(io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Saved, event.await())
        assertEquals(saved, viewModel.state.value.current); assertEquals(saved, viewModel.state.value.pending); assertEquals(false, viewModel.state.value.edited); assertEquals(false, viewModel.state.value.isSaving); assertEquals(null, viewModel.state.value.error)
        manager.settingsState.value = saved; runCurrent()
        assertEquals(saved, viewModel.state.value.current); assertEquals(false, viewModel.state.value.edited)
    }
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()
    @Test fun optionsAndPendingSelectionSurviveExternalSettingsUpdate() = runTest(dispatcher) {
        val manager = Manager(AppSettings(300_000, null))
        val viewModel = SettingsViewModel(manager)
        runCurrent()
        assertEquals(listOf(60_000L, 300_000L, 900_000L, 1_800_000L, 3_600_000L), SettingsViewModel.intervals)
        assertEquals(listOf<Long?>(null, 604_800_000L, 2_592_000_000L, 7_776_000_000L, 31_536_000_000L), SettingsViewModel.retentions)
        assertEquals(manager.settingsState.value, viewModel.state.value.current)
        assertEquals(manager.settingsState.value, viewModel.state.value.pending)
        viewModel.edit(60_000, 604_800_000)
        manager.settingsState.value = AppSettings(1_800_000, 2_592_000_000)
        runCurrent()
        assertEquals(AppSettings(1_800_000, 2_592_000_000), viewModel.state.value.current)
        assertEquals(AppSettings(60_000, 604_800_000), viewModel.state.value.pending)
        assertEquals(true, viewModel.state.value.edited)
    }
    private class Manager(initial: AppSettings) : SensorManager {
        val settingsState = MutableStateFlow(initial)
        var saveCalls = 0
        var saved: AppSettings? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var failSave = false
        override val sensors: StateFlow<List<MergedSensor>> = MutableStateFlow(emptyList())
        override val settings: Flow<AppSettings> = settingsState
        override fun sensor(address: SensorAddress): Flow<MergedSensor?> = MutableStateFlow(null)
        override suspend fun observe(advertisement: ScanObservation) = Unit
        override suspend fun register(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun deleteRegistration(address: SensorAddress) = Unit
        override suspend fun saveSettings(settings: AppSettings) { saveCalls += 1; saved = settings; saveGate?.await(); if (failSave) error("failure") }
    }
}
