package io.github.mpolci.bthomeclient.ui.main

import io.github.mpolci.bthomeclient.ble.BleScanner
import io.github.mpolci.bthomeclient.ble.ScanEvent
import io.github.mpolci.bthomeclient.data.repository.SensorFeed
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScanViewModelTest {
    @Test
    fun `typed failure clears scanner then retry starts one fresh collector`() = runTest(dispatcher) {
        val scanner = FailureThenActiveScanner()
        val viewModel = MainScanViewModel(scanner, FakeFeed())
        viewModel.screenStarted()
        viewModel.refreshCapability(ScanCapability.Ready)
        runCurrent()
        assertEquals(io.github.mpolci.bthomeclient.ble.ScanFailure.Platform(7), viewModel.state.value.failure)
        assertEquals(false, viewModel.state.value.isScanning)
        assertEquals(1, scanner.closed)
        viewModel.retry()
        runCurrent()
        assertEquals(null, viewModel.state.value.failure)
        assertTrue(viewModel.state.value.isScanning)
        assertEquals(2, scanner.starts)
        assertEquals(1, scanner.maximumActive)
        viewModel.screenStopped()
        runCurrent()
        assertEquals(2, scanner.closed)
    }

    @Test
    fun `scanner events are delivered in order and feed state preserves scan state`() = runTest(dispatcher) {
        val scanner = EventScanner()
        val feed = RecordingFeed()
        val viewModel = MainScanViewModel(scanner, feed)
        viewModel.screenStarted()
        viewModel.refreshCapability(ScanCapability.Ready)
        runCurrent()
        val first = observation("AA:BB:CC:DD:EE:91", 1)
        val second = observation("AA:BB:CC:DD:EE:92", 2)
        scanner.events.emit(ScanEvent.Advertisement(first))
        scanner.events.emit(ScanEvent.Advertisement(second))
        runCurrent()
        assertEquals(listOf(first, second), feed.observed)
        feed.sensorsMutable.value = listOf(
            io.github.mpolci.bthomeclient.domain.model.MergedSensor(first.address, null, null, false, io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus.Supported),
        )
        runCurrent()
        assertEquals(listOf(first.address), viewModel.state.value.sensors.map { it.address })
        assertTrue(viewModel.state.value.isScanning)
        assertEquals(ScanCapability.Ready, viewModel.state.value.capability)
    }

    @Test
    fun `capability transitions cancel and restart without overlapping scans`() = runTest(dispatcher) {
        val scanner = FakeScanner()
        val viewModel = MainScanViewModel(scanner, FakeFeed())
        viewModel.screenStarted()
        viewModel.refreshCapability(ScanCapability.Ready)
        runCurrent()
        assertEquals(1, scanner.starts)
        viewModel.refreshCapability(ScanCapability.BluetoothDisabled)
        runCurrent()
        assertEquals(1, scanner.stops)
        assertEquals(false, viewModel.state.value.isScanning)
        assertEquals(ScanCapability.BluetoothDisabled, viewModel.state.value.capability)
        viewModel.refreshCapability(ScanCapability.Ready)
        runCurrent()
        assertEquals(2, scanner.starts)
        assertEquals(1, scanner.maximumActive)
        viewModel.refreshCapability(ScanCapability.BluetoothDisabled)
        runCurrent()
        viewModel.refreshCapability(ScanCapability.BluetoothDisabled)
        runCurrent()
        assertEquals(2, scanner.stops)
        assertEquals(1, scanner.maximumActive)
    }

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lifecycle starts scanner only when ready and stops idempotently`() = runTest(dispatcher) {
        val scanner = FakeScanner()
        val viewModel = MainScanViewModel(scanner, FakeFeed())
        viewModel.screenStarted()
        runCurrent()
        assertEquals(0, scanner.starts)
        viewModel.refreshCapability(ScanCapability.Ready)
        runCurrent()
        assertEquals(1, scanner.starts)
        assertTrue(viewModel.state.value.isScanning)
        viewModel.screenStarted()
        runCurrent()
        assertEquals(1, scanner.starts)
        viewModel.screenStopped()
        runCurrent()
        assertEquals(1, scanner.stops)
        assertEquals(false, viewModel.state.value.isScanning)
        viewModel.screenStopped()
        runCurrent()
        assertEquals(1, scanner.stops)
    }

    private class FakeScanner : BleScanner {
        var starts = 0; var stops = 0; var active = 0; var maximumActive = 0
        override fun scan(): Flow<ScanEvent> = callbackFlow {
            starts += 1; active += 1; maximumActive = maxOf(maximumActive, active)
            awaitClose { stops += 1; active -= 1 }
        }
    }
    private class FakeFeed : SensorFeed {
        override val sensors: StateFlow<List<io.github.mpolci.bthomeclient.domain.model.MergedSensor>> = MutableStateFlow(emptyList())
        override suspend fun observe(advertisement: ScanObservation) = Unit
    }
    private class EventScanner : BleScanner {
        val events = MutableSharedFlow<ScanEvent>()
        override fun scan(): Flow<ScanEvent> = events
    }
    private class FailureThenActiveScanner : BleScanner {
        var starts = 0; var closed = 0; var active = 0; var maximumActive = 0
        override fun scan(): Flow<ScanEvent> {
            starts += 1
            return if (starts == 1) flow {
                active += 1; maximumActive = maxOf(maximumActive, active)
                emit(ScanEvent.Failure(io.github.mpolci.bthomeclient.ble.ScanFailure.Platform(7)))
                active -= 1; closed += 1
            } else callbackFlow {
                active += 1; maximumActive = maxOf(maximumActive, active)
                awaitClose { active -= 1; closed += 1 }
            }
        }
    }
    private class RecordingFeed : SensorFeed {
        val sensorsMutable = MutableStateFlow(emptyList<io.github.mpolci.bthomeclient.domain.model.MergedSensor>())
        override val sensors = sensorsMutable
        val observed = mutableListOf<ScanObservation>()
        override suspend fun observe(advertisement: ScanObservation) { yield(); observed += advertisement }
    }
    private fun observation(address: String, time: Long) = ScanObservation(
        io.github.mpolci.bthomeclient.domain.model.SensorAddress.require(address),
        null,
        -50,
        time,
        io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser().parse(0xFCD2, byteArrayOf(0x40, 0x01, 0x64)),
    )
}
