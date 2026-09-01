package io.github.mpolci.bthomeclient.bthome

/** Protocol metadata carried by the BTHome Device Information byte. */
data class BTHomeDeviceInformation(
    val version: Int,
    val encrypted: Boolean,
    val triggerBased: Boolean,
)

data class BTHomeMeasurements(
    val batteryPercent: Int? = null,
    val temperatureCelsius: Double? = null,
    val humidityPercent: Double? = null,
    val pressureHectopascals: Double? = null,
    val carbonDioxidePpm: Int? = null,
) {
    val hasSupportedValue: Boolean
        get() = batteryPercent != null || temperatureCelsius != null || humidityPercent != null ||
            pressureHectopascals != null || carbonDioxidePpm != null
}

data class BTHomeProtocolMetadata(
    val packetId: Int? = null,
    val timestampEpochMillis: Long? = null,
    val deviceType: Int? = null,
    val firmwareVersion: String? = null,
)

data class DecodedBTHomeAdvertisement(
    val deviceInformation: BTHomeDeviceInformation,
    val measurements: BTHomeMeasurements,
    val metadata: BTHomeProtocolMetadata,
    val diagnostics: List<BTHomeDiagnostic> = emptyList(),
)

sealed interface BTHomeDiagnostic {
    data class UnsupportedObject(val id: Int) : BTHomeDiagnostic
    data class DuplicateObject(val id: Int) : BTHomeDiagnostic
}

sealed interface BTHomeParseResult {
    data class Decoded(val advertisement: DecodedBTHomeAdvertisement) : BTHomeParseResult

    data class Unsupported(
        val reason: BTHomeUnsupportedReason,
        val diagnostics: List<BTHomeDiagnostic> = emptyList(),
        val deviceInformation: BTHomeDeviceInformation? = null,
        val metadata: BTHomeProtocolMetadata? = null,
        val rawServiceDataHex: String? = null,
    ) : BTHomeParseResult

    data class Malformed(
        val reason: BTHomeMalformedReason,
        val rawServiceDataHex: String? = null,
    ) : BTHomeParseResult
}

enum class BTHomeUnsupportedReason {
    WrongServiceUuid,
    UnsupportedVersion,
    Encrypted,
    NoSupportedMeasurements,
}

enum class BTHomeMalformedReason {
    MissingDeviceInformation,
    TruncatedObject,
    DescendingObjectIds,
}
