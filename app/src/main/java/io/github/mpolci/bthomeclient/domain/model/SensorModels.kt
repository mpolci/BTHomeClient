package io.github.mpolci.bthomeclient.domain.model

import io.github.mpolci.bthomeclient.bthome.BTHomeDeviceInformation
import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import io.github.mpolci.bthomeclient.bthome.BTHomeParseResult
import io.github.mpolci.bthomeclient.bthome.BTHomeProtocolMetadata

data class RegisteredSensor(
    val address: SensorAddress,
    val displayName: String,
    val room: String?,
    val imageUri: String?,
    val registrationOrder: Long,
    val advertisedName: String?,
    val lastSeenEpochMillis: Long?,
    val lastRssi: Int?,
    val lastBTHomeVersion: Int?,
    val lastEncrypted: Boolean?,
    val lastTriggerBased: Boolean?,
    val deviceType: Int?,
    val firmwareVersion: String?,
    val protocolDiagnostic: String?,
    val lastSupportStatus: SensorSupportStatus?,
    val supportedMeasurementTypes: Set<MeasurementType>,
)

data class SensorReading(
    val address: SensorAddress,
    val receptionEpochMillis: Long,
    val bthomeTimestampEpochMillis: Long?,
    val measurements: BTHomeMeasurements,
    val packetId: Int?,
)

data class ScanObservation(
    val address: SensorAddress,
    val advertisedName: String?,
    val rssi: Int,
    val receptionEpochMillis: Long,
    val parseResult: BTHomeParseResult,
) {
    val deviceInformation: BTHomeDeviceInformation?
        get() = when (parseResult) {
            is BTHomeParseResult.Decoded -> parseResult.advertisement.deviceInformation
            is BTHomeParseResult.Unsupported -> parseResult.deviceInformation
            is BTHomeParseResult.Malformed -> null
        }

    val metadata: BTHomeProtocolMetadata?
        get() = when (parseResult) {
            is BTHomeParseResult.Decoded -> parseResult.advertisement.metadata
            is BTHomeParseResult.Unsupported -> parseResult.metadata
            is BTHomeParseResult.Malformed -> null
        }

    val measurements: BTHomeMeasurements?
        get() = (parseResult as? BTHomeParseResult.Decoded)?.advertisement?.measurements
}

enum class SensorSupportStatus { Supported, Unsupported, Malformed }
enum class MeasurementType { Battery, Temperature, Humidity, Pressure, CarbonDioxide }

data class MergedSensor(
    val address: SensorAddress,
    val registeredSensor: RegisteredSensor?,
    val observation: ScanObservation?,
    val isOnline: Boolean,
    val supportStatus: SensorSupportStatus,
    val latestReading: SensorReading?,
) {
    val isRegistered: Boolean get() = registeredSensor != null
}

data class AppSettings(
    val minimumPersistenceIntervalMillis: Long = DEFAULT_MINIMUM_PERSISTENCE_INTERVAL_MILLIS,
    val retentionDurationMillis: Long? = null,
) {
    init {
        require(minimumPersistenceIntervalMillis > 0)
        require(retentionDurationMillis == null || retentionDurationMillis > 0)
    }

    companion object {
        const val DEFAULT_MINIMUM_PERSISTENCE_INTERVAL_MILLIS = 15 * 60 * 1_000L
    }
}
