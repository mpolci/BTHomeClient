package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.bthome.BTHomeParseResult
import io.github.mpolci.bthomeclient.data.local.RegisteredSensorDao
import io.github.mpolci.bthomeclient.data.local.DownloadedHistoryDao
import io.github.mpolci.bthomeclient.data.local.toDomain
import io.github.mpolci.bthomeclient.data.local.toEntity
import io.github.mpolci.bthomeclient.data.settings.SettingsStore
import io.github.mpolci.bthomeclient.domain.model.MeasurementType
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.domain.model.LatestReading
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    suspend fun updateLocalName(address: SensorAddress, displayName: String) {
        val current = requireNotNull(sensor(address).first()?.registeredSensor)
        updateEditableMetadata(address, displayName, current.room, current.imageUri)
    }
    suspend fun updatePlacement(address: SensorAddress, room: String?) {
        val current = requireNotNull(sensor(address).first()?.registeredSensor)
        updateEditableMetadata(address, current.displayName, room, current.imageUri)
    }
    suspend fun updateImage(address: SensorAddress, imageUri: String?) {
        val current = requireNotNull(sensor(address).first()?.registeredSensor)
        updateEditableMetadata(address, current.displayName, current.room, imageUri)
    }
    suspend fun deleteRegistration(address: SensorAddress)
    suspend fun saveSettings(settings: AppSettings)
}

class SensorCoordinator(
    private val registeredSensorDao: RegisteredSensorDao,
    private val downloadedHistoryDao: DownloadedHistoryDao,
    private val settingsRepository: SettingsStore,
    private val clock: Clock,
    applicationScope: CoroutineScope,
    private val mutationMutex: Mutex = Mutex(),
) : SensorManager {
    private val observations = MutableStateFlow<Map<SensorAddress, ScanObservation>>(emptyMap())
    private val nowEpochMillis = MutableStateFlow(clock.nowEpochMillis())

    override val sensors: StateFlow<List<MergedSensor>> = combine(
        registeredSensorDao.observeAll(),
        observations,
        nowEpochMillis,
    ) { registered, currentObservations, now ->
        merge(registered.map { it.toDomain() }, currentObservations, now)
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
        val existing = registeredSensorDao.findByAddress(advertisement.address.value)
        if (existing?.lastSeenEpochMillis?.let { advertisement.receptionEpochMillis < it } == true) return
        observations.value = observations.value + (advertisement.address to advertisement)
        nowEpochMillis.value = clock.nowEpochMillis()

        if (existing == null) return
        val measurements = advertisement.measurements?.takeIf { it.hasSupportedValue }
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
            latestReadingEpochMillis = if (measurements != null) advertisement.receptionEpochMillis else existing.latestReadingEpochMillis,
            latestTemperatureCelsius = if (measurements != null) measurements.temperatureCelsius else existing.latestTemperatureCelsius,
            latestHumidityPercent = if (measurements != null) measurements.humidityPercent else existing.latestHumidityPercent,
            latestPressureHectopascals = if (measurements != null) measurements.pressureHectopascals else existing.latestPressureHectopascals,
            latestCarbonDioxidePpm = if (measurements != null) measurements.carbonDioxidePpm else existing.latestCarbonDioxidePpm,
            latestBatteryPercent = if (measurements != null) measurements.batteryPercent else existing.latestBatteryPercent,
        )
        registeredSensorDao.update(updated)
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
                latestReading = observation?.measurements?.takeIf { it.hasSupportedValue }?.let { LatestReading(it, observation.receptionEpochMillis) },
                registrationId = java.util.UUID.randomUUID().toString(),
            ).toEntity(),
        )
    }

    override suspend fun updateEditableMetadata(address: SensorAddress, displayName: String, room: String?, imageUri: String?) = mutationMutex.withLock {
        require(displayName.isNotBlank())
        val current = requireNotNull(registeredSensorDao.findByAddress(address.value))
        registeredSensorDao.update(current.copy(displayName = displayName.trim(), room = room?.trim()?.ifBlank { null }, imageUri = imageUri))
    }

    override suspend fun updateLocalName(address: SensorAddress, displayName: String) = mutationMutex.withLock {
        require(displayName.isNotBlank())
        val current = requireNotNull(registeredSensorDao.findByAddress(address.value))
        registeredSensorDao.update(current.copy(displayName = displayName.trim()))
    }

    override suspend fun updatePlacement(address: SensorAddress, room: String?) = mutationMutex.withLock {
        val current = requireNotNull(registeredSensorDao.findByAddress(address.value))
        registeredSensorDao.update(current.copy(room = room?.trim()?.ifBlank { null }))
    }

    override suspend fun updateImage(address: SensorAddress, imageUri: String?) = mutationMutex.withLock {
        val current = requireNotNull(registeredSensorDao.findByAddress(address.value))
        registeredSensorDao.update(current.copy(imageUri = imageUri))
    }

    override suspend fun deleteRegistration(address: SensorAddress) {
        mutationMutex.withLock {
            registeredSensorDao.findByAddress(address.value)?.let { registeredSensorDao.delete(it) }
        }
    }

    override val settings: Flow<AppSettings> get() = settingsRepository.settings
    override fun sensor(address: SensorAddress): Flow<MergedSensor?> = sensors.map { items -> items.firstOrNull { it.address == address } }
    override suspend fun saveSettings(settings: AppSettings): Unit = mutationMutex.withLock {
        settingsRepository.save(settings)
        settings.retentionDurationMillis?.let { downloadedHistoryDao.deleteDatedSamplesOlderThan(clock.nowEpochMillis() - it) }
    }

    private fun merge(
        registered: List<RegisteredSensor>,
        currentObservations: Map<SensorAddress, ScanObservation>,
        now: Long,
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
