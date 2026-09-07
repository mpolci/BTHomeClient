package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.ble.HistoryDownloadEvent
import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.ble.HistoryDownloadRequest
import io.github.mpolci.bthomeclient.ble.HistoryDownloadResult
import io.github.mpolci.bthomeclient.ble.HistoryDownloader
import io.github.mpolci.bthomeclient.data.local.DownloadedHistoryDao
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryProtocol
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.mpolci.bthomeclient.domain.model.HistorySupport

interface HistoryStore {
    fun observeSamples(address: SensorAddress): Flow<List<DownloadedHistorySampleEntity>>
    fun observeSyncState(address: SensorAddress): Flow<HistorySyncStateEntity?>
    fun download(address: SensorAddress, expectedRegistrationId: String? = null): Flow<HistoryRepositoryEvent>
}

class HistoryRegistrationChanged : IllegalStateException("Sensor registration changed during the history update.")

class HistoryRepository(
    private val downloader: HistoryDownloader,
    private val historyDao: DownloadedHistoryDao,
    private val clock: Clock,
    private val mutationMutex: Mutex = Mutex(),
) : HistoryStore {
    private val transferMutex = Mutex()
    override fun observeSamples(address: SensorAddress) = historyDao.observeSamples(address.value)
    override fun observeSyncState(address: SensorAddress) = historyDao.observeSyncState(address.value)
    override fun download(address: SensorAddress, expectedRegistrationId: String?): Flow<HistoryRepositoryEvent> = flow {
        transferMutex.withLock {
        val identity = mutationMutex.withLock { historyDao.registrationId(address.value) } ?: throw HistoryRegistrationChanged()
        if (expectedRegistrationId != null && identity != expectedRegistrationId) throw HistoryRegistrationChanged()
        var state = historyDao.syncState(address.value)
        var forceFull = state == null
        while (true) {
            val request = if (forceFull) HistoryDownloadRequest.Full(phoneEpochSeconds()) else HistoryDownloadRequest.After(phoneEpochSeconds(), requireNotNull(state).cursor)
            try {
                downloader.download(address, request).collect { event ->
                    when (event) {
                        HistoryDownloadEvent.Connecting -> emit(HistoryRepositoryEvent.Connecting)
                        HistoryDownloadEvent.Discovering -> emit(HistoryRepositoryEvent.Discovering)
                        is HistoryDownloadEvent.Downloading -> {
                            markSupport(address, identity, HistorySupport.Supported)
                            emit(HistoryRepositoryEvent.Downloading(event.receivedPositions, event.totalPositions))
                        }
                        is HistoryDownloadEvent.Complete -> {
                            markSupport(address, identity, HistorySupport.Supported)
                            val summary = when (val result = event.result) {
                                is HistoryDownloadResult.Empty -> commitEmpty(address, state, request, result, identity)
                                is HistoryDownloadResult.Transfer -> commitTransfer(address, state, request, result, identity)
                            }
                            emit(HistoryRepositoryEvent.Complete(summary))
                        }
                    }
                }
                return@flow
            } catch (error: HistoryDownloadException.Unsupported) {
                markSupport(address, identity, HistorySupport.Unsupported)
                throw error
            } catch (error: HistoryDownloadException.Remote) {
                if (error.response is io.github.mpolci.bthomeclient.history.sene.SeneHistoryControlResponse.StaleCursor && state != null && !forceFull) {
                    forceFull = true
                } else throw error
            }
        }
        }
    }

    private suspend fun markSupport(address: SensorAddress, identity: String, support: HistorySupport) = mutationMutex.withLock {
        if (historyDao.updateHistorySupport(address.value, identity, support.name) != 1) throw HistoryRegistrationChanged()
    }

    private suspend fun commit(address: SensorAddress, identity: String, samples: List<DownloadedHistorySampleEntity>, state: HistorySyncStateEntity, merge: Boolean) = mutationMutex.withLock {
        if (!historyDao.commitForRegistration(samples, state, merge, identity)) throw HistoryRegistrationChanged()
    }

    private suspend fun commitEmpty(address: SensorAddress, state: HistorySyncStateEntity?, request: HistoryDownloadRequest, result: HistoryDownloadResult.Empty, identity: String): HistoryDownloadSummary {
        if (request is HistoryDownloadRequest.After) require(result.finalCursor.toByteArray().contentEquals(request.cursor)) { "Incremental EMPTY cursor is discontinuous." }
        commit(address, identity, emptyList(), syncState(address, state?.nextAcquisitionSequence ?: 0, result.finalCursor.toByteArray(), result.flags), false)
        return HistoryDownloadSummary(0, 0, 0, 0, result.flags.syncPending, result.flags.historyTruncated)
    }

    private suspend fun commitTransfer(address: SensorAddress, state: HistorySyncStateEntity?, request: HistoryDownloadRequest, result: HistoryDownloadResult.Transfer, identity: String): HistoryDownloadSummary {
        val decoded = SeneHistoryProtocol.decodeRawPositions(result.rawPositions)
        val positions = sourcePositions(result.finalCursor.generation, result.finalCursor.nextSlot, result.rawPositions.size / SeneHistoryProtocol.rawPositionSize)
        val base = state?.nextAcquisitionSequence ?: 0
        val samples = decoded.samples.map { sample ->
            val source = positions[sample.positionIndex]
            DownloadedHistorySampleEntity(
                sensorAddress = address.value, acquisitionSequence = base + sample.positionIndex,
                sourceFormatVersion = SeneHistoryProtocol.formatVersion, sourceGeneration = source.first, sourceSlot = source.second,
                rawPosition = result.rawPositions.copyOfRange(sample.positionIndex * SeneHistoryProtocol.rawPositionSize, (sample.positionIndex + 1) * SeneHistoryProtocol.rawPositionSize),
                recordedEpochMillis = sample.recordedEpochMillis, timeQuality = sample.timeQuality.name,
                temperatureCelsius = sample.temperatureCelsius, humidityPercent = sample.humidityPercent,
                pressureHectopascals = sample.pressureHectopascals, batteryPercent = sample.batteryPercent,
            )
        }
        commit(address, identity, samples, syncState(address, base + positions.size, result.finalCursor.toByteArray(), result.flags), request is HistoryDownloadRequest.Full && state != null)
        return HistoryDownloadSummary(samples.size, decoded.erasedPositionCount, decoded.invalidPositionCount, decoded.undatedSampleCount, result.flags.syncPending, result.flags.historyTruncated)
    }

    private fun syncState(address: SensorAddress, next: Long, cursor: ByteArray, flags: io.github.mpolci.bthomeclient.history.sene.SeneHistoryFlags) = HistorySyncStateEntity(address.value, cursor, next, clock.nowEpochMillis(), flags.syncPending, flags.historyTruncated)
    private fun phoneEpochSeconds(): Long = clock.nowEpochMillis() / 1_000L
    private fun sourcePositions(finalGeneration: Long, finalSlot: Int, count: Int): List<Pair<Long, Int>> {
        var generation = finalGeneration
        var slot = finalSlot
        if (slot == 255) { generation = (generation + 1) and 0xFFFF_FFFFL; slot = 0 }
        return MutableList(count) { 0L to 0 }.also { positions ->
            for (index in count - 1 downTo 0) { if (slot == 0) { generation = (generation - 1) and 0xFFFF_FFFFL; slot = 255 }; slot -= 1; positions[index] = generation to slot }
        }
    }
}

sealed interface HistoryRepositoryEvent {
    data object Connecting : HistoryRepositoryEvent
    data object Discovering : HistoryRepositoryEvent
    data class Downloading(val receivedPositions: Int, val totalPositions: Int) : HistoryRepositoryEvent
    data class Complete(val summary: HistoryDownloadSummary) : HistoryRepositoryEvent
}
data class HistoryDownloadSummary(val importedSamples: Int, val erasedPositions: Int, val invalidPositions: Int, val undatedSamples: Int, val syncPending: Boolean, val historyTruncated: Boolean)
