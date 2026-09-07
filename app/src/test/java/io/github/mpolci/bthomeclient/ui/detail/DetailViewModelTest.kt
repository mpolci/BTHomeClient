package io.github.mpolci.bthomeclient.ui.detail

import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.*
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val address = SensorAddress.require("AA:BB:CC:DD:EE:D1")
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun unregisteredPageSurvivesExpiryAndRegistersOriginalSnapshot() = runTest(dispatcher) {
        val initial = item(registered = false)
        val manager = Manager(initial)
        val viewModel = DetailViewModel(address, manager, Clock { 600_000L })
        runCurrent()
        assertFalse(viewModel.state.value.notFound)
        assertEquals("Broadcast", viewModel.state.value.displayName)
        manager.flow.value = null
        runCurrent()
        assertFalse(viewModel.state.value.notFound)
        assertFalse(viewModel.state.value.sensor!!.isOnline)
        viewModel.register()
        runCurrent()
        assertEquals("Broadcast", manager.registeredName)
        assertEquals(initial.observation, manager.observed)
        assertEquals(100_000L, viewModel.state.value.sensor!!.latestReading!!.receptionEpochMillis)
        assertTrue(viewModel.state.value.sensor!!.isRegistered)
        assertFalse(viewModel.state.value.isRegistering)
    }

    @Test fun missingBroadcastNameFallsBackToAddress() = runTest(dispatcher) {
        val original = item(registered = false)
        val manager = Manager(original.copy(observation = original.observation!!.copy(advertisedName = "  ")))
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        viewModel.register()
        runCurrent()
        assertEquals(address.value, manager.registeredName)
    }

    @Test fun fieldSavePreservesConcurrentMetadataAndBroadcastIdentity() = runTest(dispatcher) {
        val manager = Manager(item())
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        manager.updateGate = gate
        viewModel.saveName("  New local name  ")
        runCurrent()
        val current = manager.flow.value!!
        manager.flow.value = current.copy(registeredSensor = current.registeredSensor!!.copy(room = "Changed elsewhere", imageUri = "new-photo"))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("New local name", viewModel.state.value.displayName)
        assertEquals("Changed elsewhere", viewModel.state.value.room)
        assertEquals("new-photo", viewModel.state.value.imageUri)
        assertEquals("Broadcast", viewModel.state.value.sensor!!.observation!!.advertisedName)
        assertEquals(1, viewModel.state.value.saveVersion)
        assertEquals(0, manager.fullMetadataUpdates)
    }

    @Test fun optionalFieldsSaveIndividuallyAndCanBeRemoved() = runTest(dispatcher) {
        val manager = Manager(item())
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        viewModel.saveRoom("   ")
        runCurrent()
        assertEquals("", viewModel.state.value.room)
        assertEquals("photo", viewModel.state.value.imageUri)
        viewModel.saveImage(null)
        runCurrent()
        assertNull(viewModel.state.value.imageUri)
        assertEquals("Stored", viewModel.state.value.displayName)
        assertEquals(2, viewModel.state.value.saveVersion)
    }

    @Test fun blankNameFailsValidationAndDuplicateSaveIsIgnored() = runTest(dispatcher) {
        val manager = Manager(item())
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        viewModel.saveName(" ")
        runCurrent()
        assertEquals("A local name is required.", viewModel.state.value.error)
        assertEquals(0, manager.updateCalls)
        val gate = CompletableDeferred<Unit>()
        manager.updateGate = gate
        viewModel.saveName("New")
        viewModel.saveRoom("Ignored while saving")
        runCurrent()
        assertEquals(1, manager.updateCalls)
        assertTrue(viewModel.state.value.isSaving)
        gate.complete(Unit)
        runCurrent()
        assertEquals("Placement", viewModel.state.value.room)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test fun saveFailureKeepsSavedMetadataAndAllowsRetry() = runTest(dispatcher) {
        val manager = Manager(item()).apply { failUpdate = true }
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        viewModel.saveName("New")
        runCurrent()
        assertEquals("Stored", viewModel.state.value.displayName)
        assertEquals(0, viewModel.state.value.saveVersion)
        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSaving)
        manager.failUpdate = false
        viewModel.saveName("New")
        runCurrent()
        assertEquals("New", viewModel.state.value.displayName)
        assertNull(viewModel.state.value.error)
    }

    @Test fun cancellationPropagatesWithoutReportingSaveFailure() = runTest(dispatcher) {
        val manager = Manager(item()).apply { cancelUpdate = true }
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        val job = viewModel.saveName("New")
        runCurrent()
        assertTrue(job.isCancelled)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(0, viewModel.state.value.saveVersion)
    }

    @Test fun removalShowsFreshUnregisteredSnapshotAndClearsLocalMetadata() = runTest(dispatcher) {
        val manager = Manager(item())
        val viewModel = DetailViewModel(address, manager, Clock { 100_001L })
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        manager.deleteGate = gate
        viewModel.delete()
        viewModel.delete()
        runCurrent()
        assertEquals(1, manager.deleteCalls)
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.sensor!!.isRegistered)
        assertEquals("Broadcast", viewModel.state.value.displayName)
        assertEquals("", viewModel.state.value.room)
        assertNull(viewModel.state.value.imageUri)
        assertFalse(viewModel.state.value.isDeleting)
        assertEquals(100_000L, viewModel.state.value.sensor!!.latestReading!!.receptionEpochMillis)
    }

    @Test fun removalOfStaleOrUnavailableSensorShowsAbsentState() = runTest(dispatcher) {
        val manager = Manager(item())
        val viewModel = DetailViewModel(address, manager, Clock { 400_000L })
        runCurrent()
        viewModel.delete()
        runCurrent()
        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.sensor)
        val missing = DetailViewModel(address, Manager(null))
        runCurrent()
        assertTrue(missing.state.value.notFound)
        assertFalse(missing.state.value.loading)
    }

    @Test fun deletionFailureKeepsRegisteredSensorAvailable() = runTest(dispatcher) {
        val manager = Manager(item()).apply { failDelete = true }
        val viewModel = DetailViewModel(address, manager)
        runCurrent()
        viewModel.delete()
        runCurrent()
        assertTrue(viewModel.state.value.sensor!!.isRegistered)
        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDeleting)
    }

    private fun item(registered: Boolean = true): MergedSensor {
        val stored = RegisteredSensor(address, "Stored", "Placement", "photo", 1, "Broadcast", 100_000, -50, 2, false, false, null, "1.0", null, SensorSupportStatus.Supported, emptySet())
        val observation = ScanObservation(address, "Broadcast", -50, 100_000, BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64)))
        return MergedSensor(address, stored.takeIf { registered }, observation, true, SensorSupportStatus.Supported)
    }

    private inner class Manager(initial: MergedSensor?) : SensorManager {
        val flow = MutableStateFlow(initial)
        var updateCalls = 0
        var fullMetadataUpdates = 0
        var updateGate: CompletableDeferred<Unit>? = null
        var failUpdate = false
        var cancelUpdate = false
        var deleteCalls = 0
        var deleteGate: CompletableDeferred<Unit>? = null
        var failDelete = false
        var registeredName: String? = null
        var observed: ScanObservation? = null
        override val sensors: StateFlow<List<MergedSensor>> = MutableStateFlow(emptyList())
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override fun sensor(address: SensorAddress): Flow<MergedSensor?> = flow
        override suspend fun observe(advertisement: ScanObservation) {
            observed = advertisement
            flow.value = flow.value!!.let { current -> current.copy(observation = advertisement, registeredSensor = current.registeredSensor!!.copy(latestReading = advertisement.measurements?.let { LatestReading(it, advertisement.receptionEpochMillis) })) }
        }
        override suspend fun register(address: SensorAddress, displayName: String, room: String?, imageUri: String?) {
            registeredName = displayName
            flow.value = item().let { current -> current.copy(observation = null, registeredSensor = current.registeredSensor!!.copy(displayName = displayName, room = room, imageUri = imageUri)) }
        }
        override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) { fullMetadataUpdates++ }
        private suspend fun update(transform: (RegisteredSensor) -> RegisteredSensor) {
            updateCalls++
            updateGate?.await()
            if (cancelUpdate) throw CancellationException("Canceled")
            if (failUpdate) error("failure")
            flow.value = flow.value!!.let { it.copy(registeredSensor = transform(it.registeredSensor!!)) }
        }
        override suspend fun updateLocalName(address: SensorAddress, displayName: String) = update { it.copy(displayName = displayName) }
        override suspend fun updatePlacement(address: SensorAddress, room: String?) = update { it.copy(room = room) }
        override suspend fun updateImage(address: SensorAddress, imageUri: String?) = update { it.copy(imageUri = imageUri) }
        override suspend fun deleteRegistration(address: SensorAddress) {
            deleteCalls++
            deleteGate?.await()
            if (failDelete) error("failure")
            flow.value = null
        }
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }
}
