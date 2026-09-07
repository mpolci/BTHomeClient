package io.github.mpolci.bthomeclient.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.data.repository.HistorySessionCoordinator
import io.github.mpolci.bthomeclient.data.repository.HistoryStore
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.HistorySupport
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.history.chart.HistoryChartMath
import io.github.mpolci.bthomeclient.history.chart.HistoryChartPoint
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val address: SensorAddress?,
    private val manager: SensorManager,
    private val store: HistoryStore,
    private val session: HistorySessionCoordinator,
    initialSelection: HistorySelection? = null,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val measurement = MutableStateFlow(initialSelection?.measurement ?: HistoryMeasurement.Temperature)
    private var viewport = initialSelection?.viewport
    private var comparisonAddress: SensorAddress? = null
    private var viewportRevision = 0L
    private val active = MutableStateFlow(false)
    private val requested = mutableSetOf<RequestIdentity>()
    private val registered = manager.sensors.map { sensors -> sensors.mapNotNull { it.registeredSensor } }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val mutableState = MutableStateFlow(HistoryPageState(measurement = measurement.value, selectedAddress = address, viewport = viewport ?: HistoryViewport.fit(null)))
    val state = mutableState.asStateFlow()
    private var preparedSensors = emptyList<PreparedSensor>()
    private var preparedMeasurement = measurement.value
    private var archivesLoading = true

    init {
        val archives = registered.map { sensors -> sensors.map { RegistrationIdentity(it.address, it.registrationId) } }
            .distinctUntilChanged().flatMapLatest { identities ->
                if (identities.isEmpty()) flowOf(emptyList())
                else combine(identities.map { identity ->
                    combine(store.observeSamples(identity.address), store.observeSyncState(identity.address)) { samples, sync ->
                        Archive(identity, samples, sync)
                    }
                }) { it.toList() }
            }
        val prepared = combine(archives, measurement) { items, selectedMeasurement ->
            PreparedArchives(selectedMeasurement, items.map { archive ->
                val points = archive.samples.mapNotNull { sample ->
                    val time = sample.recordedEpochMillis ?: return@mapNotNull null
                    val value = selectedMeasurement.value(sample)
                    if (!value.isFinite()) null else HistoryChartPoint(sample.id, time, sample.acquisitionSequence, value)
                }.sortedWith(compareBy<HistoryChartPoint> { it.epochMillis }.thenBy { it.acquisitionSequence }.thenBy { it.id })
                PreparedArchive(archive, points, HistoryChartMath.coverage(points))
            })
        }.flowOn(computationDispatcher)
        viewModelScope.launch {
            combine(registered, prepared) { sensors, data -> sensors to data }.collect { (sensors, data) ->
                val byIdentity = data.archives.associateBy { it.archive.identity }
                archivesLoading = sensors.any { RegistrationIdentity(it.address, it.registrationId) !in byIdentity }
                preparedSensors = sensors.map { sensor ->
                    val archive = byIdentity[RegistrationIdentity(sensor.address, sensor.registrationId)]
                    PreparedSensor(HistorySensorData(sensor, archive?.archive?.samples.orEmpty(), archive?.points.orEmpty(), archive?.archive?.sync), archive?.coverage)
                }
                preparedMeasurement = data.measurement
                rebuild()
            }
        }
        viewModelScope.launch {
            session.updates.collect { updates -> mutableState.update { it.copy(updates = updates) } }
        }
        viewModelScope.launch {
            combine(registered, session.sessionId, active) { sensors, sessionId, started -> Triple(sensors, sessionId, started) }
                .collect { (sensors, sessionId, started) ->
                    if (!started || sessionId == 0L) return@collect
                    requested.removeAll { it.sessionId != sessionId }
                    sensors.filter { (address == null || it.address == address) && it.historySupport != HistorySupport.Unsupported }.forEach { sensor ->
                        val identity = RequestIdentity(sessionId, sensor.address, sensor.registrationId)
                        if (requested.add(identity)) session.request(sensor.address, sensor.registrationId)
                    }
                }
        }
    }

    fun screenStarted() { active.value = true }
    fun screenStopped() { active.value = false }

    fun retry(address: SensorAddress? = this.address) {
        if (!active.value) return
        registered.value.filter { (address == null || it.address == address) && it.historySupport != HistorySupport.Unsupported }
            .forEach { session.request(it.address, it.registrationId, retry = true) }
    }

    fun setMeasurement(value: HistoryMeasurement) { measurement.value = value }

    fun setViewport(value: HistoryViewport) {
        val revision = ++viewportRevision
        val snapshot = state.value
        viewModelScope.launch {
            val adjusted = withContext(computationDispatcher) {
                val clamped = value.clamp(snapshot.coverage)
                if (value.centerMillis == snapshot.viewport.centerMillis) {
                    clamped.copy(centerMillis = value.centerMillis)
                } else {
                    val sample = snapshot.selected?.let { HistoryChartMath.nearestSample(it.points, clamped.centerMillis) }
                    if (sample == null) clamped else clamped.copy(centerMillis = sample.epochMillis)
                }
            }
            if (viewportRevision == revision) {
                viewport = adjusted
                rebuild()
            }
        }
    }

    fun setComparison(address: SensorAddress?) {
        comparisonAddress = address?.takeIf { candidate ->
            this.address != null && candidate != this.address && state.value.sensors.any { it.address == candidate && it.points.isNotEmpty() }
        }
        rebuild()
    }

    fun showAllHistory() {
        viewportRevision++
        viewport = state.value.coverage?.let { HistoryViewport.fit(it) }
        rebuild()
    }

    fun selection() = HistorySelection(measurement.value, viewport ?: state.value.viewport.takeIf { state.value.coverage != null })

    private fun rebuild() {
        val visible = preparedSensors.filter { it.data.sensor.historySupport != HistorySupport.Unsupported || it.data.samples.isNotEmpty() || it.data.address == address }
        if (!archivesLoading && visible.none { it.data.address == comparisonAddress && it.data.points.isNotEmpty() }) comparisonAddress = null
        val ranges = visible.filter { address == null || it.data.address == address || it.data.address == comparisonAddress }.mapNotNull { it.coverage }
        val coverage = ranges.takeIf { it.isNotEmpty() }?.let { HistoryTimeRange(it.minOf { range -> range.startMillis }, it.maxOf { range -> range.endMillis }) }
        if (viewport == null && coverage != null) viewport = HistoryViewport.fit(coverage)
        mutableState.update { current ->
            current.copy(
                sensors = visible.map { it.data },
                measurement = preparedMeasurement,
                viewport = viewport ?: HistoryViewport.fit(null),
                coverage = coverage,
                comparisonAddress = comparisonAddress,
                loading = archivesLoading,
                sensorMissing = address != null && registered.value.none { it.address == address },
            )
        }
    }

    private data class RegistrationIdentity(val address: SensorAddress, val registrationId: String)
    private data class RequestIdentity(val sessionId: Long, val address: SensorAddress, val registrationId: String)
    private data class Archive(val identity: RegistrationIdentity, val samples: List<DownloadedHistorySampleEntity>, val sync: HistorySyncStateEntity?)
    private data class PreparedArchive(val archive: Archive, val points: List<HistoryChartPoint>, val coverage: HistoryTimeRange?)
    private data class PreparedArchives(val measurement: HistoryMeasurement, val archives: List<PreparedArchive>)
    private data class PreparedSensor(val data: HistorySensorData, val coverage: HistoryTimeRange?)
}
