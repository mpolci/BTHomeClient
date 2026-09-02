package io.github.mpolci.bthomeclient.ui.detail

import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
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
class DetailViewModelTest {
    @Test fun detailDeleteGuardEmitsDeletedAndReportsFailure() = runTest(dispatcher) {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:D4")
        val manager = Manager()
        val viewModel = DetailViewModel(address, manager)
        manager.flow.value = item(address, "Stored", "Live")
        runCurrent()

        val gate = CompletableDeferred<Unit>()
        manager.deleteGate = gate
        val event = async { viewModel.events.first() }
        viewModel.delete()
        viewModel.delete()
        runCurrent()

        assertEquals(1, manager.deleteCalls)
        assertEquals(true, viewModel.state.value.isDeleting)
        gate.complete(Unit)
        runCurrent()
        assertEquals(io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Deleted, event.await())
        assertEquals(false, viewModel.state.value.isDeleting)

        val failingManager = Manager().apply {
            flow.value = item(address, "Stored", "Live")
            failDelete = true
        }
        val failingViewModel = DetailViewModel(address, failingManager)
        runCurrent()
        failingViewModel.delete()
        runCurrent()
        assertEquals("Unable to remove sensor.", failingViewModel.state.value.error)
        assertEquals(false, failingViewModel.state.value.isDeleting)
    }

    @Test fun detailSaveFailureKeepsEditableAndLiveState() = runTest(dispatcher) {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:D3")
        val manager = Manager()
        val viewModel = DetailViewModel(address, manager)
        manager.flow.value = item(address, "Stored", "Live")
        runCurrent()
        viewModel.edit("User", "User room", "user-image")
        manager.failUpdate = true
        viewModel.save()
        runCurrent()
        assertEquals(false, viewModel.state.value.isSaving)
        assertEquals("Unable to save sensor.", viewModel.state.value.error)
        assertEquals("User", viewModel.state.value.displayName)
        assertEquals("User room", viewModel.state.value.room)
        assertEquals("user-image", viewModel.state.value.imageUri)
        assertEquals("Live", viewModel.state.value.sensor!!.observation!!.advertisedName)
        assertEquals(emptyList<io.github.mpolci.bthomeclient.ui.editor.EditorEvent>(), viewModel.events.replayCache)
    }
    @Test fun detailValidationAndSaveGuardUseNarrowUpdate() = runTest(dispatcher) {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:D2")
        val manager = Manager()
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        viewModel.save(); runCurrent()
        assertEquals("A display name is required.", viewModel.state.value.error); assertEquals(0, manager.updateCalls)
        manager.flow.value = item(address, "Stored", "Live")
        runCurrent()
        viewModel.edit("Edited", "Room", "image")
        val gate = CompletableDeferred<Unit>(); manager.updateGate = gate
        val event = async { viewModel.events.first() }
        viewModel.save(); viewModel.save(); runCurrent()
        assertEquals(1, manager.updateCalls); assertEquals(true, viewModel.state.value.isSaving)
        assertEquals(listOf(address.value, "Edited", "Room", "image"), manager.updated)
        gate.complete(Unit); runCurrent()
        assertEquals(io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Saved, event.await())
        assertEquals(false, viewModel.state.value.isSaving); assertEquals(null, viewModel.state.value.error); assertEquals("Edited", viewModel.state.value.displayName)
    }
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()
    @Test fun detailLoadsCleanFieldsPreservesDirtyFieldsAndUpdatesLiveSnapshot() = runTest(dispatcher) {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:D1")
        val manager = Manager()
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        assertEquals(false, viewModel.state.value.loading); assertEquals(true, viewModel.state.value.notFound)
        viewModel.edit(room = "User room", imageUri = "user-image")
        manager.flow.value = item(address, "Stored", "Live one")
        runCurrent()
        assertEquals(false, viewModel.state.value.notFound); assertEquals("Stored", viewModel.state.value.displayName); assertEquals("User room", viewModel.state.value.room); assertEquals("user-image", viewModel.state.value.imageUri)
        manager.flow.value = item(address, "Changed stored", "Live two")
        runCurrent()
        assertEquals("Stored", viewModel.state.value.displayName); assertEquals("User room", viewModel.state.value.room); assertEquals("user-image", viewModel.state.value.imageUri); assertEquals("Live two", viewModel.state.value.sensor!!.observation!!.advertisedName)
    }
    private fun item(address: SensorAddress, name: String, live: String): MergedSensor {
        val registered = RegisteredSensor(address, name, "Stored room", "stored-image", 1, null, 1, -1, 2, false, false, 1, "1.0", null, SensorSupportStatus.Supported, emptySet())
        return MergedSensor(address, registered, ScanObservation(address, live, -1, 2, BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64))), true, SensorSupportStatus.Supported)
    }
    private class Manager : SensorManager {
        val flow = MutableStateFlow<MergedSensor?>(null)
        var updateCalls = 0
        var updated: List<Any?> = emptyList()
        var updateGate: CompletableDeferred<Unit>? = null
        var failUpdate = false
        var deleteCalls = 0
        var deleteGate: CompletableDeferred<Unit>? = null
        var failDelete = false
        override val sensors: StateFlow<List<MergedSensor>> = MutableStateFlow(emptyList())
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override fun sensor(address: SensorAddress): Flow<MergedSensor?> = flow
        override suspend fun observe(advertisement: ScanObservation) = Unit
        override suspend fun register(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) { updateCalls += 1; updated = listOf(address.value, displayName, room, imageUri); updateGate?.await(); if (failUpdate) error("failure") }
        override suspend fun deleteRegistration(address: SensorAddress) {
            deleteCalls += 1
            deleteGate?.await()
            if (failDelete) error("failure")
        }
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }
}
