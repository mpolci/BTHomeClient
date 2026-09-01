package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.bthome.BTHomeParseResult
import io.github.mpolci.bthomeclient.data.local.RegisteredSensorDao
import io.github.mpolci.bthomeclient.data.local.SensorReadingDao
import io.github.mpolci.bthomeclient.data.local.toDomain
import io.github.mpolci.bthomeclient.data.local.toEntity
import io.github.mpolci.bthomeclient.data.settings.SettingsStore
import io.github.mpolci.bthomeclient.domain.model.MeasurementType
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorReading
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SensorFeed { val sensors: StateFlow<List<MergedSensor>>; suspend fun observe(advertisement: ScanObservation) }
interface SensorManager : SensorFeed {
    val settings: Flow<AppSettings>
    fun sensor(address: SensorAddress): Flow<MergedSensor?>
    suspend fun register(address: SensorAddress, displayName: String, room: String? = null, imageUri: String? = null)
    suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?)
    suspend fun saveSettings(settings: AppSettings)
    fun observeHistory(address: SensorAddress): Flow<List<SensorReading>> = emptyFlow()
    fun observeHistoryInRange(address: SensorAddress, fromEpochMillis: Long, toEpochMillis: Long): Flow<List<SensorReading>> = emptyFlow()
}

class SensorCoordinator(
    private val registeredSensorDao: RegisteredSensorDao,
    private val sensorReadingDao: SensorReadingDao,
    private val settingsRepository: SettingsStore,
    private val clock: Clock,
    applicationScope: CoroutineScope,
) : SensorManager {
    private val mutationMutex = Mutex()
    private val observations = MutableStateFlow<Map<SensorAddress, ScanObservation>>(emptyMap())
    private val nowEpochMillis = MutableStateFlow(clock.nowEpochMillis())

    override val sensors: StateFlow<List<MergedSensor>> = combine(
        registeredSensorDao.observeAll(),
        observations,
        nowEpochMillis,
        sensorReadingDao.observeLatestForAllSensors(),
    ) { registered, currentObservations, now, readings ->
        merge(registered.map { it.toDomain() }, currentObservations, now, readings.associateBy { it.sensorAddress })
    }.stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    init {
        applicationScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MILLIS)
                onTimeTick()
            }
        }
    }

    suspend fun onTimeTick() = mutationMutex.withLock {
        val now = clock.nowEpochMillis()
        observations.value = observations.value.filterValues { it.receptionEpochMillis > now - ONLINE_WINDOW_MILLIS }
        nowEpochMillis.value = now
    }

    override suspend fun observe(advertisement: ScanObservation) = mutationMutex.withLock {
        val previous = observations.value[advertisement.address]
        if (previous != null && advertisement.receptionEpochMillis < previous.receptionEpochMillis) return
        observations.value = observations.value + (advertisement.address to advertisement)
        nowEpochMillis.value = clock.nowEpochMillis()

        val existing = registeredSensorDao.findByAddress(advertisement.address.value) ?: return
        val updated = existing.copy(
            advertisedName = advertisement.advertisedName ?: existing.advertisedName,
            lastSeenEpochMillis = advertisement.receptionEpochMillis,
            lastRssi = advertisement.rssi,
            lastBTHomeVersion = advertisement.deviceInformation?.version ?: existing.lastBTHomeVersion,
            lastEncrypted = advertisement.deviceInformation?.encrypted ?: existing.lastEncrypted,
            lastTriggerBased = advertisement.deviceInformation?.triggerBased ?: existing.lastTriggerBased,
            deviceType = advertisement.metadata?.deviceType ?: existing.deviceType,
            firmwareVersion = advertisement.metadata?.firmwareVersion ?: existing.firmwareVersion,
            protocolDiagnostic = advertisement.parseResult.diagnosticText(),
            lastSupportStatus = advertisement.supportStatus().name,
            supportedMeasurementTypes = (existing.supportedMeasurementTypes?.split(',')?.filter { it.isNotBlank() }.orEmpty() + advertisement.measurementTypes().map { it.name }).distinct().sorted().joinToString(",").ifEmpty { null },
        )
        registeredSensorDao.update(updated)

        val decoded = advertisement.parseResult as? BTHomeParseResult.Decoded ?: return
        val reading = SensorReading(
            address = advertisement.address,
            receptionEpochMillis = advertisement.receptionEpochMillis,
            bthomeTimestampEpochMillis = decoded.advertisement.metadata.timestampEpochMillis,
            measurements = decoded.advertisement.measurements,
            packetId = decoded.advertisement.metadata.packetId,
        )
        val settings = settingsRepository.settings.first()
        val latest = sensorReadingDao.latest(advertisement.address.value)
        if (latest == null || reading.receptionEpochMillis - latest.receptionEpochMillis >= settings.minimumPersistenceIntervalMillis) {
            sensorReadingDao.insertAndApplyRetention(
                reading = reading.toEntity(),
                beforeEpochMillis = settings.retentionDurationMillis?.let { clock.nowEpochMillis() - it },
            )
        }
    }

    override suspend fun register(
        address: SensorAddress,
        displayName: String,
        room: String?,
        imageUri: String?,
    ) = mutationMutex.withLock {
        require(displayName.isNotBlank())
        val observation = observations.value[address]
        registeredSensorDao.insertWithNextRegistrationOrder(
            RegisteredSensor(
                address = address,
                displayName = displayName.trim(),
                room = room?.trim()?.ifBlank { null },
                imageUri = imageUri,
                registrationOrder = 0,
                advertisedName = observation?.advertisedName,
                lastSeenEpochMillis = observation?.receptionEpochMillis,
                lastRssi = observation?.rssi,
                lastBTHomeVersion = observation?.deviceInformation?.version,
                lastEncrypted = observation?.deviceInformation?.encrypted,
                lastTriggerBased = observation?.deviceInformation?.triggerBased,
                deviceType = observation?.metadata?.deviceType,
                firmwareVersion = observation?.metadata?.firmwareVersion,
                protocolDiagnostic = observation?.parseResult?.diagnosticText(),
                lastSupportStatus = observation?.supportStatus(),
                supportedMeasurementTypes = observation?.measurementTypes()?.toSet().orEmpty(),
            ).toEntity(),
        )
        val decoded = observation?.parseResult as? BTHomeParseResult.Decoded ?: return
        val settings = settingsRepository.settings.first()
        sensorReadingDao.insertAndApplyRetention(
            SensorReading(address, observation.receptionEpochMillis, decoded.advertisement.metadata.timestampEpochMillis, decoded.advertisement.measurements, decoded.advertisement.metadata.packetId).toEntity(),
            settings.retentionDurationMillis?.let { clock.nowEpochMillis() - it },
        )
    }

    override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = mutationMutex.withLock {
        require(displayName.isNotBlank())
        val current = requireNotNull(registeredSensorDao.findByAddress(address.value))
        registeredSensorDao.update(current.copy(displayName = displayName.trim(), room = room?.trim()?.ifBlank { null }, imageUri = imageUri))
    }

    override val settings: Flow<AppSettings> get() = settingsRepository.settings
    override fun sensor(address: SensorAddress): Flow<MergedSensor?> = sensors.map { items -> items.firstOrNull { it.address == address } }
    override fun observeHistory(address: SensorAddress): Flow<List<SensorReading>> = sensorReadingDao.observeHistory(address.value).map { it.map { reading -> reading.toDomain() } }
    override fun observeHistoryInRange(address: SensorAddress, fromEpochMillis: Long, toEpochMillis: Long): Flow<List<SensorReading>> = sensorReadingDao.observeHistoryInRange(address.value, fromEpochMillis, toEpochMillis).map { it.map { reading -> reading.toDomain() } }

    override suspend fun saveSettings(settings: AppSettings): Unit = mutationMutex.withLock {
        settingsRepository.save(settings)
        settings.retentionDurationMillis?.let { sensorReadingDao.deleteOlderThan(clock.nowEpochMillis() - it) }
    }

    private fun merge(
        registered: List<RegisteredSensor>,
        currentObservations: Map<SensorAddress, ScanObservation>,
        now: Long,
        latestReadings: Map<String, io.github.mpolci.bthomeclient.data.local.SensorReadingEntity>,
    ): List<MergedSensor> {
        val registeredAddresses = registered.mapTo(mutableSetOf()) { it.address }
        val registeredItems = registered.map { sensor ->
            val observation = currentObservations[sensor.address]
            MergedSensor(
                address = sensor.address,
                registeredSensor = sensor,
                observation = observation,
                isOnline = (observation?.receptionEpochMillis ?: sensor.lastSeenEpochMillis ?: Long.MIN_VALUE) > now - ONLINE_WINDOW_MILLIS,
                supportStatus = observation?.supportStatus() ?: sensor.lastSupportStatus ?: SensorSupportStatus.Supported,
                latestReading = latestReadings[sensor.address.value]?.toDomain(),
            )
        }
        val unregisteredItems = currentObservations.values
            .asSequence()
            .filter { it.address !in registeredAddresses }
            .filter { it.receptionEpochMillis > now - ONLINE_WINDOW_MILLIS }
            .sortedWith(compareByDescending<ScanObservation> { it.receptionEpochMillis }.thenBy { it.address.value })
            .map { observation ->
                MergedSensor(
                    address = observation.address,
                    registeredSensor = null,
                    observation = observation,
                    isOnline = true,
                    supportStatus = observation.supportStatus(),
                    latestReading = null,
                )
            }
            .toList()
        return registeredItems + unregisteredItems
    }

    private fun ScanObservation.supportStatus() = when (parseResult) {
        is BTHomeParseResult.Decoded -> SensorSupportStatus.Supported
        is BTHomeParseResult.Unsupported -> SensorSupportStatus.Unsupported
        is BTHomeParseResult.Malformed -> SensorSupportStatus.Malformed
    }

    private fun ScanObservation.measurementTypes(): List<MeasurementType> = measurements?.let {
        buildList {
            if (it.batteryPercent != null) add(MeasurementType.Battery)
            if (it.temperatureCelsius != null) add(MeasurementType.Temperature)
            if (it.humidityPercent != null) add(MeasurementType.Humidity)
            if (it.pressureHectopascals != null) add(MeasurementType.Pressure)
            if (it.carbonDioxidePpm != null) add(MeasurementType.CarbonDioxide)
        }
    }.orEmpty()

    private fun BTHomeParseResult.diagnosticText(): String? = when (this) {
        is BTHomeParseResult.Decoded -> advertisement.diagnostics.takeIf { it.isNotEmpty() }?.joinToString()
        is BTHomeParseResult.Unsupported -> reason.name
        is BTHomeParseResult.Malformed -> reason.name
    }

    private companion object {
        const val ONLINE_WINDOW_MILLIS = 5 * 60 * 1_000L
        const val TICK_INTERVAL_MILLIS = 60 * 1_000L
    }
}
