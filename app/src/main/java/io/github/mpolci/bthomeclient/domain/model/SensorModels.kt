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
    val latestReading: LatestReading? = null,
    val registrationId: String = "",
    val historySupport: HistorySupport = HistorySupport.Unknown,
)

data class LatestReading(val measurements: BTHomeMeasurements, val receptionEpochMillis: Long)

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
enum class HistorySupport { Unknown, Supported, Unsupported }
enum class MeasurementType { Battery, Temperature, Humidity, Pressure, CarbonDioxide }

data class MergedSensor(
    val address: SensorAddress,
    val registeredSensor: RegisteredSensor?,
    val observation: ScanObservation?,
    val isOnline: Boolean,
    val supportStatus: SensorSupportStatus,
) {
    val isRegistered: Boolean get() = registeredSensor != null
    val historySupport: HistorySupport get() = registeredSensor?.historySupport ?: HistorySupport.Unknown
    val latestReading: LatestReading?
        get() = observation?.measurements?.takeIf { it.hasSupportedValue }?.let {
            LatestReading(it, observation.receptionEpochMillis)
        } ?: registeredSensor?.latestReading
}

data class AppSettings(
    val retentionDurationMillis: Long? = null,
) {
    init {
        require(retentionDurationMillis == null || retentionDurationMillis > 0)
    }
}
