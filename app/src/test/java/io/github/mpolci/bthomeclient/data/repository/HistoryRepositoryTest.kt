package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.ble.HistoryDownloadEvent
import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.ble.HistoryDownloadRequest
import io.github.mpolci.bthomeclient.ble.HistoryDownloadResult
import io.github.mpolci.bthomeclient.ble.HistoryDownloader
import io.github.mpolci.bthomeclient.data.local.DownloadedHistoryDao
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.domain.model.HistorySupport
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryCursor
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryFlags
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryProtocol
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryRepositoryTest {
    @Test
    fun `normalized empty cursor at generation boundary keeps existing history`() = runTest {
        val saved = cursor(0xFFFF_FFFFL, 255)
        val normalized = cursor(0, 0)
        val existing = storedSample()
        val dao = FakeHistoryDao(listOf(existing), syncState(saved, 1))
        val downloader = FakeDownloader().apply { enqueue { emit(HistoryDownloadEvent.Complete(HistoryDownloadResult.Empty(FLAGS, normalized))) } }
        HistoryRepository(downloader, dao, fixedClock(1_000)).download(ADDRESS, REGISTRATION_ID).toList()
        assertEquals(listOf(existing), dao.sampleRows.value)
        assertArrayEquals(normalized.toByteArray(), dao.state.value!!.cursor)
    }

    @Test
    fun `incremental cursor distance mismatch does not commit`() = runTest {
        val saved = cursor(1, 1)
        val oldState = syncState(saved, 1)
        val dao = FakeHistoryDao(listOf(storedSample()), oldState)
        val downloader = FakeDownloader().apply { enqueue { emit(HistoryDownloadEvent.Complete(transfer(cursor(1, 3), samplePosition()))) } }
        val failure = failureOf { HistoryRepository(downloader, dao, fixedClock(1_000)).download(ADDRESS, REGISTRATION_ID).toList() }
        assertTrue(failure is HistoryDownloadException.Protocol)
        assertEquals(oldState, dao.state.value)
        assertEquals(1, dao.sampleRows.value.size)
    }

    @Test
    fun `successful import applies retention without removing undated records`() = runTest {
        val existing = storedSample().copy(recordedEpochMillis = null)
        val dao = FakeHistoryDao(listOf(existing), syncState(cursor(1, 1), 1))
        val downloader = FakeDownloader().apply { enqueue { emit(HistoryDownloadEvent.Complete(transfer(cursor(1, 2), samplePosition(timestamp = 100)))) } }
        HistoryRepository(downloader, dao, fixedClock(1_000_000), retentionDurationMillis = { 200_000L }).download(ADDRESS, REGISTRATION_ID).toList()
        assertEquals(listOf(existing), dao.sampleRows.value)
        assertEquals(2L, dao.state.value!!.nextAcquisitionSequence)
    }

    @Test
    fun `full download imports only validated sensor history and persists its cursor`() = runTest {
        val dao = FakeHistoryDao()
        val finalCursor = cursor(generation = 4, slot = 1)
        val downloader = FakeDownloader().apply {
            enqueue { request ->
                assertTrue(request is HistoryDownloadRequest.Full)
                assertEquals(123L, request.phoneEpochSeconds)
                emit(HistoryDownloadEvent.Connecting)
                emit(HistoryDownloadEvent.Downloading(1, 1))
                emit(HistoryDownloadEvent.Complete(transfer(finalCursor, samplePosition(timestamp = 1_700_000_000))))
            }
        }
        val repository = HistoryRepository(downloader, dao, fixedClock(123_000))

        val events = repository.download(ADDRESS, REGISTRATION_ID).toList()

        val sample = dao.sampleRows.value.single()
        assertEquals(0L, sample.acquisitionSequence)
        assertEquals(4L, sample.sourceGeneration)
        assertEquals(0, sample.sourceSlot)
        assertEquals(1_700_000_000_000L, sample.recordedEpochMillis)
        assertEquals(HistorySupport.Supported.name, dao.historySupport)
        assertEquals(1L, dao.state.value!!.nextAcquisitionSequence)
        assertArrayEquals(finalCursor.toByteArray(), dao.state.value!!.cursor)
        assertEquals(1, (events.last() as HistoryRepositoryEvent.Complete).summary.importedSamples)
    }

    @Test
    fun `incremental transfer starts from the saved cursor and appends with the next sequence`() = runTest {
        val initialCursor = cursor(generation = 4, slot = 1)
        val finalCursor = cursor(generation = 4, slot = 2)
        val existing = storedSample(acquisitionSequence = 0, generation = 4, slot = 0)
        val dao = FakeHistoryDao(
            initialSamples = listOf(existing),
            initialState = syncState(initialCursor, nextSequence = 1).copy(historyTruncated = true),
        )
        val downloader = FakeDownloader().apply {
            enqueue { request ->
                assertTrue(request is HistoryDownloadRequest.After)
                assertArrayEquals(initialCursor.toByteArray(), (request as HistoryDownloadRequest.After).cursor)
                emit(HistoryDownloadEvent.Complete(transfer(finalCursor, samplePosition(timestamp = 1_700_000_100))))
            }
        }

        HistoryRepository(downloader, dao, fixedClock(200_000)).download(ADDRESS, REGISTRATION_ID).toList()

        assertEquals(listOf(0L, 1L), dao.sampleRows.value.map { it.acquisitionSequence })
        assertEquals(listOf(0, 1), dao.sampleRows.value.map { it.sourceSlot })
        assertEquals(2L, dao.state.value!!.nextAcquisitionSequence)
        assertTrue(dao.state.value!!.historyTruncated)
        assertArrayEquals(finalCursor.toByteArray(), dao.state.value!!.cursor)
    }

    @Test
    fun `incremental empty response keeps the archive and advances only sync metadata`() = runTest {
        val savedCursor = cursor(generation = 7, slot = 8)
        val existing = storedSample(acquisitionSequence = 12, generation = 7, slot = 7)
        val previousState = syncState(savedCursor, nextSequence = 13).copy(historyTruncated = true)
        val dao = FakeHistoryDao(listOf(existing), previousState)
        val downloader = FakeDownloader().apply {
            enqueue { request ->
                assertTrue(request is HistoryDownloadRequest.After)
                emit(HistoryDownloadEvent.Complete(HistoryDownloadResult.Empty(FLAGS, savedCursor)))
            }
        }

        val events = HistoryRepository(downloader, dao, fixedClock(300_000)).download(ADDRESS).toList()

        assertEquals(listOf(existing), dao.sampleRows.value)
        assertEquals(13L, dao.state.value!!.nextAcquisitionSequence)
        assertEquals(300_000L, dao.state.value!!.lastSuccessfulDownloadEpochMillis)
        assertTrue(dao.state.value!!.historyTruncated)
        assertEquals(0, (events.single() as HistoryRepositoryEvent.Complete).summary.importedSamples)
    }

    @Test
    fun `transport failure preserves archive cursor and capability classification`() = runTest {
        val savedCursor = cursor(generation = 1, slot = 1)
        val existing = storedSample()
        val previousState = syncState(savedCursor, nextSequence = 1)
        val dao = FakeHistoryDao(listOf(existing), previousState)
        val downloader = FakeDownloader().apply {
            enqueue { throw HistoryDownloadException.Connection("unreachable") }
        }

        val error = failureOf { HistoryRepository(downloader, dao, fixedClock(1)).download(ADDRESS).toList() }

        assertTrue(error is HistoryDownloadException.Connection)
        assertEquals(listOf(existing), dao.sampleRows.value)
        assertEquals(previousState, dao.state.value)
        assertNull(dao.historySupport)
    }

    @Test
    fun `cancellation preserves archive and cursor`() = runTest {
        val savedCursor = cursor(generation = 1, slot = 1)
        val existing = storedSample()
        val previousState = syncState(savedCursor, nextSequence = 1)
        val dao = FakeHistoryDao(listOf(existing), previousState)
        val transferStarted = CompletableDeferred<Unit>()
        val downloader = FakeDownloader().apply {
            enqueue {
                emit(HistoryDownloadEvent.Downloading(1, 2))
                transferStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val job = launch { HistoryRepository(downloader, dao, fixedClock(1)).download(ADDRESS).toList() }
        transferStarted.await()

        job.cancelAndJoin()

        assertEquals(listOf(existing), dao.sampleRows.value)
        assertEquals(previousState, dao.state.value)
        assertEquals(HistorySupport.Supported.name, dao.historySupport)
    }

    @Test
    fun `only explicit unsupported failure persists unsupported capability`() = runTest {
        val dao = FakeHistoryDao()
        val downloader = FakeDownloader().apply {
            enqueue { throw HistoryDownloadException.Protocol("bad response") }
            enqueue { throw HistoryDownloadException.Unsupported() }
        }
        val repository = HistoryRepository(downloader, dao, fixedClock(1))

        assertTrue(failureOf { repository.download(ADDRESS).toList() } is HistoryDownloadException.Protocol)
        assertNull(dao.historySupport)
        assertTrue(failureOf { repository.download(ADDRESS).toList() } is HistoryDownloadException.Unsupported)
        assertEquals(HistorySupport.Unsupported.name, dao.historySupport)
    }

    @Test
    fun `wrong or missing registration identity prevents transfer from starting`() = runTest {
        val downloader = FakeDownloader()
        val wrongIdentity = FakeHistoryDao().apply { registrationId = "replacement" }
        val missingRegistration = FakeHistoryDao().apply { registrationId = null }

        assertTrue(
            failureOf {
                HistoryRepository(downloader, wrongIdentity, fixedClock(1)).download(ADDRESS, REGISTRATION_ID).toList()
            } is HistoryRegistrationChanged,
        )
        assertTrue(
            failureOf {
                HistoryRepository(downloader, missingRegistration, fixedClock(1)).download(ADDRESS).toList()
            } is HistoryRegistrationChanged,
        )
        assertEquals(0, downloader.requests.size)
    }

    @Test
    fun `registration replacement after validated transfer prevents commit`() = runTest {
        val dao = FakeHistoryDao().apply {
            afterSupportUpdate = { registrationId = "replacement" }
        }
        val finalCursor = cursor(generation = 1, slot = 1)
        val downloader = FakeDownloader().apply {
            enqueue {
                emit(HistoryDownloadEvent.Complete(transfer(finalCursor, samplePosition())))
            }
        }

        val error = failureOf {
            HistoryRepository(downloader, dao, fixedClock(1)).download(ADDRESS, REGISTRATION_ID).toList()
        }

        assertTrue(error is HistoryRegistrationChanged)
        assertTrue(dao.sampleRows.value.isEmpty())
        assertNull(dao.state.value)
        assertEquals(1, dao.rejectedCommits)
    }

    @Test
    fun `concurrent starts are serialized and the second reads the freshly committed cursor`() = runTest {
        val dao = FakeHistoryDao()
        val firstCursor = cursor(generation = 9, slot = 1)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val downloader = FakeDownloader().apply {
            enqueue {
                firstEntered.complete(Unit)
                releaseFirst.await()
                emit(HistoryDownloadEvent.Complete(transfer(firstCursor, samplePosition())))
            }
            enqueue { request ->
                assertTrue(request is HistoryDownloadRequest.After)
                assertArrayEquals(firstCursor.toByteArray(), (request as HistoryDownloadRequest.After).cursor)
                emit(HistoryDownloadEvent.Complete(HistoryDownloadResult.Empty(FLAGS, firstCursor)))
            }
        }
        val repository = HistoryRepository(downloader, dao, fixedClock(1))
        val first = launch { repository.download(ADDRESS).toList() }
        firstEntered.await()
        val second = launch { repository.download(ADDRESS).toList() }
        runCurrent()
        assertEquals(1, downloader.requests.size)

        releaseFirst.complete(Unit)
        first.join()
        second.join()

        assertEquals(2, downloader.requests.size)
        assertTrue(downloader.requests[0] is HistoryDownloadRequest.Full)
        assertTrue(downloader.requests[1] is HistoryDownloadRequest.After)
        assertEquals(1, dao.sampleRows.value.size)
    }

    private class FakeDownloader : HistoryDownloader {
        val requests = mutableListOf<HistoryDownloadRequest>()
        private val handlers = mutableListOf<suspend FlowCollector<HistoryDownloadEvent>.(HistoryDownloadRequest) -> Unit>()

        fun enqueue(handler: suspend FlowCollector<HistoryDownloadEvent>.(HistoryDownloadRequest) -> Unit) {
            handlers += handler
        }

        override fun download(address: SensorAddress, request: HistoryDownloadRequest): Flow<HistoryDownloadEvent> = flow {
            requests += request
            handlers.removeAt(0).invoke(this, request)
        }
    }

    private class FakeHistoryDao(
        initialSamples: List<DownloadedHistorySampleEntity> = emptyList(),
        initialState: HistorySyncStateEntity? = null,
    ) : DownloadedHistoryDao {
        var registrationId: String? = REGISTRATION_ID
        var historySupport: String? = null
        var afterSupportUpdate: (() -> Unit)? = null
        var rejectedCommits = 0
        val sampleRows = MutableStateFlow(initialSamples)
        val state = MutableStateFlow(initialState)

        override suspend fun registrationId(address: String): String? = registrationId

        override suspend fun updateHistorySupport(address: String, registrationId: String, support: String): Int {
            if (this.registrationId != registrationId) return 0
            historySupport = support
            afterSupportUpdate?.invoke()
            afterSupportUpdate = null
            return 1
        }

        override suspend fun syncState(address: String) = state.value?.takeIf { it.sensorAddress == address }
        override fun observeSyncState(address: String) = state.map { it?.takeIf { value -> value.sensorAddress == address } }
        override fun observeSampleCount(address: String) = sampleRows.map { rows -> rows.count { it.sensorAddress == address } }
        override fun observeSamples(address: String) = sampleRows.map { rows -> rows.filter { it.sensorAddress == address } }
        override suspend fun samples(address: String) = sampleRows.value.filter { it.sensorAddress == address }
        override suspend fun samplesAtSource(address: String, formatVersion: Int, generation: Long, slot: Int) = sampleRows.value.filter {
            it.sensorAddress == address && it.sourceFormatVersion == formatVersion && it.sourceGeneration == generation && it.sourceSlot == slot
        }
        override fun observeSamplesInRange(address: String, fromEpochMillis: Long, toEpochMillis: Long) = sampleRows.map { rows ->
            rows.filter { it.sensorAddress == address && it.recordedEpochMillis in fromEpochMillis..toEpochMillis }
        }
        override suspend fun insertSamples(samples: List<DownloadedHistorySampleEntity>) {
            sampleRows.value += samples
        }
        override suspend fun updateSample(sample: DownloadedHistorySampleEntity) {
            sampleRows.value = sampleRows.value.map { if (it.id == sample.id) sample else it }
        }
        override suspend fun insertSyncState(state: HistorySyncStateEntity) {
            this.state.value = state
        }
        override suspend fun deleteDatedSamplesOlderThan(beforeEpochMillis: Long) {
            sampleRows.value = sampleRows.value.filter { it.recordedEpochMillis == null || it.recordedEpochMillis >= beforeEpochMillis }
        }

        override suspend fun commitForRegistration(
            samples: List<DownloadedHistorySampleEntity>,
            state: HistorySyncStateEntity,
            mergeExactSourceRecords: Boolean,
            expectedRegistrationId: String,
        ): Boolean {
            if (registrationId != expectedRegistrationId) {
                rejectedCommits += 1
                return false
            }
            if (!mergeExactSourceRecords) {
                insertSamples(samples)
            } else {
                samples.forEach { sample ->
                    val existing = samplesAtSource(
                        sample.sensorAddress,
                        sample.sourceFormatVersion,
                        sample.sourceGeneration,
                        sample.sourceSlot,
                    ).firstOrNull { it.rawPosition.contentEquals(sample.rawPosition) }
                    if (existing == null) insertSamples(listOf(sample))
                    else updateSample(sample.copy(id = existing.id, acquisitionSequence = existing.acquisitionSequence))
                }
            }
            insertSyncState(state)
            return true
        }
    }

    private fun transfer(cursor: SeneHistoryCursor, raw: ByteArray) =
        HistoryDownloadResult.Transfer(FLAGS, cursor, raw)

    private fun syncState(cursor: SeneHistoryCursor, nextSequence: Long) = HistorySyncStateEntity(
        sensorAddress = ADDRESS.value,
        cursor = cursor.toByteArray(),
        nextAcquisitionSequence = nextSequence,
        lastSuccessfulDownloadEpochMillis = 10,
        syncPending = false,
        historyTruncated = false,
    )

    private fun storedSample(
        acquisitionSequence: Long = 0,
        generation: Long = 1,
        slot: Int = 0,
    ) = DownloadedHistorySampleEntity(
        id = acquisitionSequence + 1,
        sensorAddress = ADDRESS.value,
        acquisitionSequence = acquisitionSequence,
        sourceFormatVersion = SeneHistoryProtocol.formatVersion,
        sourceGeneration = generation,
        sourceSlot = slot,
        rawPosition = samplePosition(),
        recordedEpochMillis = 1_000,
        timeQuality = "DIRECT_UTC",
        temperatureCelsius = 20.0,
        humidityPercent = 40,
        pressureHectopascals = 1_000.0,
        batteryPercent = 70,
    )

    private fun cursor(generation: Long, slot: Int): SeneHistoryCursor = ByteArray(SeneHistoryProtocol.cursorSize).also { bytes ->
        writeUInt16(bytes, 0, SeneHistoryProtocol.formatVersion)
        writeUInt32(bytes, 2, generation)
        writeUInt16(bytes, 6, slot)
        writeUInt16(bytes, 8, SeneHistoryProtocol.crc16CcittFalse(bytes, length = 8))
    }.let(SeneHistoryProtocol::decodeCursor)

    private fun samplePosition(
        timestamp: Long = 100,
        pressureDecaPa: Int = 10_000,
        temperatureDeciC: Int = 200,
        humidity: Int = 40,
        battery: Int = 70,
    ): ByteArray = ByteArray(SeneHistoryProtocol.rawPositionSize) { 0xFF.toByte() }.also { position ->
        position[0] = SeneHistoryProtocol.sampleUtcType.toByte()
        writeUInt32(position, 1, timestamp)
        writeUInt16(position, 5, pressureDecaPa)
        writeUInt16(position, 7, temperatureDeciC)
        position[9] = humidity.toByte()
        position[10] = battery.toByte()
        writeUInt16(position, 11, SeneHistoryProtocol.crc16CcittFalse(position, length = 11))
    }

    private fun writeUInt16(output: ByteArray, offset: Int, value: Int) {
        output[offset] = value.toByte()
        output[offset + 1] = (value shr 8).toByte()
    }

    private fun writeUInt32(output: ByteArray, offset: Int, value: Long) {
        output[offset] = value.toByte()
        output[offset + 1] = (value shr 8).toByte()
        output[offset + 2] = (value shr 16).toByte()
        output[offset + 3] = (value shr 24).toByte()
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (error: Throwable) {
        error
    }

    private fun fixedClock(now: Long) = Clock { now }

    private companion object {
        val ADDRESS = SensorAddress.require("AA:BB:CC:DD:EE:70")
        const val REGISTRATION_ID = "registration-1"
        val FLAGS = SeneHistoryFlags(syncPending = false, historyTruncated = false)
    }
}
