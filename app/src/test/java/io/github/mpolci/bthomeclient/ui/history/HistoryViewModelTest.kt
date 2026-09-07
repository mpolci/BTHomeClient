package io.github.mpolci.bthomeclient.ui.history

import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.data.repository.*
import io.github.mpolci.bthomeclient.domain.model.*
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
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
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = SensorAddress.require("AA:BB:CC:DD:EE:01")
    private val second = SensorAddress.require("AA:BB:CC:DD:EE:02")
    private val third = SensorAddress.require("AA:BB:CC:DD:EE:03")
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun overviewRequestsEligibleSensorsAndKeepsUnsupportedArchives() = runTest(dispatcher) {
        val fourth = SensorAddress.require("AA:BB:CC:DD:EE:04")
        val manager = Manager(listOf(sensor(first), sensor(second, HistorySupport.Supported), sensor(third, HistorySupport.Unsupported), sensor(fourth, HistorySupport.Unsupported)))
        val store = Store().apply { samples(third).value = listOf(sample(third, 1, 10_000L)) }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(null, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        model.screenStarted()
        runCurrent()
        assertEquals(listOf(first, second), store.calls.map { it.first })
        assertEquals(listOf(first, second, third), model.state.value.sensors.map { it.address })
        assertFalse(model.state.value.loading)
        assertEquals(HistoryTimeRange(10_000, 10_000), model.state.value.coverage)
    }

    @Test fun detailOnlyUpdatesItsSensorAndDeduplicatesAcrossMetadataDataAndRouteChanges() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first), sensor(second)))
        val store = Store()
        val session = HistorySessionCoordinator(store, backgroundScope)
        val detail = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        detail.screenStarted()
        runCurrent()
        assertEquals(listOf(first), store.calls.map { it.first })
        manager.items.value = manager.items.value.map { it.copy(registeredSensor = it.registeredSensor!!.copy(displayName = "Renamed")) }
        store.samples(first).value = listOf(sample(first, 1, 10_000L))
        detail.screenStopped()
        detail.screenStarted()
        runCurrent()
        assertEquals(1, store.calls.size)
        val overview = HistoryViewModel(null, manager, store, session, computationDispatcher = dispatcher)
        overview.screenStarted()
        runCurrent()
        assertEquals(listOf(first, second), store.calls.map { it.first })
        assertEquals("Renamed", detail.state.value.selected!!.sensor.displayName)
        session.end()
        session.begin()
        runCurrent()
        assertEquals(2, store.calls.count { it.first == first })
        assertEquals(2, store.calls.count { it.first == second })
    }

    @Test fun inactivePageDoesNotRequestNewSensorsUntilStarted() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first)))
        val store = Store()
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(null, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        runCurrent()
        assertTrue(store.calls.isEmpty())
        model.screenStarted()
        runCurrent()
        model.screenStopped()
        manager.items.value = listOf(sensor(first), sensor(second))
        runCurrent()
        assertEquals(listOf(first), store.calls.map { it.first })
        model.screenStarted()
        runCurrent()
        assertEquals(listOf(first, second), store.calls.map { it.first })
    }

    @Test fun successfulZeroImportPreservesExistingArchiveAndUndatedSamplesRemainDistinct() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first), sensor(second)))
        val store = Store().apply {
            samples(first).value = listOf(sample(first, 1, 10_000L))
            samples(second).value = listOf(sample(second, 2, null))
        }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(null, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        model.screenStarted()
        runCurrent()
        assertEquals(1, model.state.value.sensors.first { it.address == first }.points.size)
        val undated = model.state.value.sensors.first { it.address == second }
        assertEquals(1, undated.samples.size)
        assertEquals(1, undated.undatedCount)
        assertTrue(undated.points.isEmpty())
        assertEquals(0, (model.state.value.updates[first] as HistoryUpdateState.Complete).summary.importedSamples)
        store.samples(first).value = emptyList()
        runCurrent()
        assertTrue(model.state.value.sensors.first { it.address == first }.samples.isEmpty())
        assertNull(model.state.value.coverage)
    }

    @Test fun failedUpdateKeepsCacheAndSelectionAndRetryIsContextual() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first)))
        val store = Store().apply {
            samples(first).value = listOf(sample(first, 1, 10_000L), sample(first, 2, 30_000L))
            failures[first] = HistoryDownloadException.Connection("Not nearby")
        }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val selection = HistorySelection(HistoryMeasurement.Humidity, HistoryViewport(10_000, 4_000))
        val model = HistoryViewModel(first, manager, store, session, selection, dispatcher)
        session.begin()
        model.screenStarted()
        runCurrent()
        assertEquals(2, model.state.value.selected!!.points.size)
        assertEquals(selection.viewport, model.state.value.viewport)
        assertTrue(model.state.value.updates[first] is HistoryUpdateState.Failed)
        store.failures.clear()
        model.retry()
        runCurrent()
        assertEquals(2, store.calls.size)
        assertTrue(model.state.value.updates[first] is HistoryUpdateState.Complete)
        assertEquals(selection, model.selection())
    }

    @Test fun shortCoverageFitsAvailableDataAndLaterDownloadsPreserveExploration() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first)))
        val store = Store().apply { samples(first).value = listOf(sample(first, 2, 30_000L), sample(first, 1, 10_000L)) }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        runCurrent()
        assertEquals(HistoryViewport(20_000, 20_000), model.state.value.viewport)
        assertEquals(listOf(10_000L, 30_000L), model.state.value.selected!!.points.map { it.epochMillis })
        model.setViewport(HistoryViewport(28_000, 5_000))
        runCurrent()
        assertEquals(HistoryViewport(30_000, 5_000), model.state.value.viewport)
        store.samples(first).value = store.samples(first).value + sample(first, 3, 90_000L)
        runCurrent()
        assertEquals(HistoryViewport(30_000, 5_000), model.state.value.viewport)
        assertEquals(HistoryTimeRange(10_000, 90_000), model.state.value.coverage)
        model.setMeasurement(HistoryMeasurement.Pressure)
        runCurrent()
        assertEquals(HistoryViewport(30_000, 5_000), model.state.value.viewport)
        assertEquals(1001.0, model.state.value.selected!!.points.first().value, 0.0)
        model.showAllHistory()
        assertEquals(HistoryViewport(50_000, 80_000), model.state.value.viewport)
    }

    @Test fun overviewAndDirectDetailStartWithLatestWeekAndFollowDownloadsUntilExploration() = runTest(dispatcher) {
        val day = 86_400_000L
        val manager = Manager(listOf(sensor(first)))
        val store = Store().apply { samples(first).value = listOf(sample(first, 1, 0), sample(first, 2, 90 * day)) }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val overview = HistoryViewModel(null, manager, store, session, computationDispatcher = dispatcher)
        val detail = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        runCurrent()
        for (model in listOf(overview, detail)) {
            assertEquals(83 * day, model.state.value.viewport.startMillis)
            assertEquals(90 * day, model.state.value.viewport.endMillis)
        }
        store.samples(first).value += sample(first, 3, 100 * day)
        runCurrent()
        assertEquals(93 * day, overview.state.value.viewport.startMillis)
        assertEquals(100 * day, detail.state.value.viewport.endMillis)
        overview.setViewport(HistoryViewport(40 * day, 2 * day))
        detail.showAllHistory()
        runCurrent()
        val all = detail.state.value.viewport
        store.samples(first).value += sample(first, 4, 110 * day)
        runCurrent()
        assertEquals(HistoryViewport(40 * day, 2 * day), overview.state.value.viewport)
        assertEquals(all, detail.state.value.viewport)
        val openedFromOverview = HistoryViewModel(first, manager, store, session, overview.selection(), dispatcher)
        runCurrent()
        assertEquals(overview.state.value.viewport, openedFromOverview.state.value.viewport)
    }

    @Test fun zoomPreservesFitMidpointWhilePanningSnapsToRecordedSample() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first)))
        val store = Store().apply { samples(first).value = listOf(sample(first, 1, 10_000L), sample(first, 2, 30_000L)) }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        runCurrent()
        assertEquals(HistoryViewport(20_000, 20_000), model.state.value.viewport)
        model.setViewport(HistoryViewport(20_000, 5_000))
        runCurrent()
        assertEquals(HistoryViewport(20_000, 5_000), model.state.value.viewport)
        model.setViewport(HistoryViewport(21_000, 5_000))
        runCurrent()
        assertEquals(HistoryViewport(30_000, 5_000), model.state.value.viewport)
    }

    @Test fun foregroundRestartRequeuesRequestsPreviouslyIgnoredByInactiveSession() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first)))
        val store = Store()
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(null, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        model.screenStarted()
        runCurrent()
        model.screenStopped()
        session.end()
        runCurrent()
        manager.items.value = listOf(sensor(first), sensor(second))
        model.screenStarted()
        runCurrent()
        assertEquals(listOf(first), store.calls.map { it.first })
        session.begin()
        runCurrent()
        assertEquals(listOf(first, first, second), store.calls.map { it.first })
    }

    @Test fun comparisonRequiresOtherRegisteredDatedDataAndDisappearsOnDeletion() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first), sensor(second), sensor(third)))
        val store = Store().apply {
            samples(first).value = listOf(sample(first, 1, 10_000L))
            samples(second).value = listOf(sample(second, 2, 50_000L))
            samples(third).value = listOf(sample(third, 3, null))
        }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        runCurrent()
        model.setComparison(first)
        assertNull(model.state.value.comparisonAddress)
        model.setComparison(third)
        assertNull(model.state.value.comparisonAddress)
        model.setComparison(second)
        assertEquals(second, model.state.value.comparisonAddress)
        assertEquals(HistoryTimeRange(10_000, 50_000), model.state.value.coverage)
        manager.items.value = listOf(sensor(first))
        runCurrent()
        assertNull(model.state.value.comparisonAddress)
        manager.items.value = emptyList()
        runCurrent()
        assertTrue(model.state.value.sensorMissing)
        assertNull(model.state.value.selected)
    }

    @Test fun changedRegistrationRequestsNewIdentityAndDoesNotRetainOldArchive() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first)))
        val store = Store().apply { samples(first).value = listOf(sample(first, 1, 10_000L)) }
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        model.screenStarted()
        runCurrent()
        store.samples(first).value = emptyList()
        val newSensor = sensor(first).let { it.copy(registeredSensor = it.registeredSensor!!.copy(registrationId = "new-registration")) }
        manager.items.value = listOf(newSensor)
        runCurrent()
        assertEquals("new-registration", store.calls.last().second)
        assertEquals(2, store.calls.size)
        assertTrue(model.state.value.selected!!.samples.isEmpty())
    }

    @Test fun unsupportedDetailRemainsReadableWithoutStartingDownload() = runTest(dispatcher) {
        val manager = Manager(listOf(sensor(first, HistorySupport.Unsupported)))
        val store = Store()
        val session = HistorySessionCoordinator(store, backgroundScope)
        val model = HistoryViewModel(first, manager, store, session, computationDispatcher = dispatcher)
        session.begin()
        model.screenStarted()
        runCurrent()
        assertTrue(store.calls.isEmpty())
        assertEquals(HistorySupport.Unsupported, model.state.value.selected!!.sensor.historySupport)
        assertFalse(model.state.value.sensorMissing)
    }

    private fun sensor(address: SensorAddress, support: HistorySupport = HistorySupport.Unknown): MergedSensor {
        val registration = RegisteredSensor(address, address.value, null, null, 1, null, null, null, null, null, null, null, null, null, SensorSupportStatus.Supported, emptySet(), registrationId = "registration-${address.value}", historySupport = support)
        return MergedSensor(address, registration, null, false, SensorSupportStatus.Supported)
    }

    private fun sample(address: SensorAddress, id: Long, epoch: Long?) = DownloadedHistorySampleEntity(id, address.value, id, 1, 0, id.toInt(), byteArrayOf(), epoch, if (epoch == null) "UNAVAILABLE" else "DIRECT_UTC", 20.0 + id, 40 + id.toInt(), 1000.0 + id, 90)

    private class Manager(initial: List<MergedSensor>) : SensorManager {
        val items = MutableStateFlow(initial)
        override val sensors: StateFlow<List<MergedSensor>> = items
        override val settings: Flow<AppSettings> = flowOf(AppSettings())
        override fun sensor(address: SensorAddress) = items.map { sensors -> sensors.firstOrNull { it.address == address } }
        override suspend fun observe(advertisement: ScanObservation) = Unit
        override suspend fun register(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = Unit
        override suspend fun deleteRegistration(address: SensorAddress) = Unit
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }

    private class Store : HistoryStore {
        private val sampleFlows = mutableMapOf<SensorAddress, MutableStateFlow<List<DownloadedHistorySampleEntity>>>()
        val failures = mutableMapOf<SensorAddress, Exception>()
        val calls = mutableListOf<Pair<SensorAddress, String?>>()
        fun samples(address: SensorAddress) = sampleFlows.getOrPut(address) { MutableStateFlow(emptyList()) }
        override fun observeSamples(address: SensorAddress): Flow<List<DownloadedHistorySampleEntity>> = samples(address)
        override fun observeSyncState(address: SensorAddress): Flow<HistorySyncStateEntity?> = flowOf(null)
        override fun download(address: SensorAddress, expectedRegistrationId: String?): Flow<HistoryRepositoryEvent> = flow {
            calls += address to expectedRegistrationId
            emit(HistoryRepositoryEvent.Connecting)
            failures[address]?.let { throw it }
            emit(HistoryRepositoryEvent.Complete(HistoryDownloadSummary(0, 0, 0, 0, false, false)))
        }
    }
}
