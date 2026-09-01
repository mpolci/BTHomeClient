package io.github.mpolci.bthomeclient.data.local

import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorReading

fun RegisteredSensorEntity.toDomain() = RegisteredSensor(
    address = SensorAddress.require(address),
    displayName = displayName,
    room = room,
    imageUri = imageUri,
    registrationOrder = registrationOrder,
    advertisedName = advertisedName,
    lastSeenEpochMillis = lastSeenEpochMillis,
    lastRssi = lastRssi,
    lastBTHomeVersion = lastBTHomeVersion,
    lastEncrypted = lastEncrypted,
    lastTriggerBased = lastTriggerBased,
    deviceType = deviceType,
    firmwareVersion = firmwareVersion,
    protocolDiagnostic = protocolDiagnostic,
    lastSupportStatus = lastSupportStatus?.let { io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus.valueOf(it) },
    supportedMeasurementTypes = supportedMeasurementTypes?.split(',')?.filter { it.isNotBlank() }?.map { io.github.mpolci.bthomeclient.domain.model.MeasurementType.valueOf(it) }?.toSet().orEmpty(),
)

fun RegisteredSensor.toEntity() = RegisteredSensorEntity(
    address = address.value,
    displayName = displayName,
    room = room,
    imageUri = imageUri,
    registrationOrder = registrationOrder,
    advertisedName = advertisedName,
    lastSeenEpochMillis = lastSeenEpochMillis,
    lastRssi = lastRssi,
    lastBTHomeVersion = lastBTHomeVersion,
    lastEncrypted = lastEncrypted,
    lastTriggerBased = lastTriggerBased,
    deviceType = deviceType,
    firmwareVersion = firmwareVersion,
    protocolDiagnostic = protocolDiagnostic,
    lastSupportStatus = lastSupportStatus?.name,
    supportedMeasurementTypes = supportedMeasurementTypes.map { it.name }.sorted().joinToString(",").ifEmpty { null },
)

fun SensorReadingEntity.toDomain() = SensorReading(
    address = SensorAddress.require(sensorAddress),
    receptionEpochMillis = receptionEpochMillis,
    bthomeTimestampEpochMillis = bthomeTimestampEpochMillis,
    measurements = BTHomeMeasurements(
        batteryPercent = batteryPercent,
        temperatureCelsius = temperatureCelsius,
        humidityPercent = humidityPercent,
        pressureHectopascals = pressureHectopascals,
        carbonDioxidePpm = carbonDioxidePpm,
    ),
    packetId = packetId,
)

fun SensorReading.toEntity() = SensorReadingEntity(
    sensorAddress = address.value,
    receptionEpochMillis = receptionEpochMillis,
    bthomeTimestampEpochMillis = bthomeTimestampEpochMillis,
    temperatureCelsius = measurements.temperatureCelsius,
    humidityPercent = measurements.humidityPercent,
    pressureHectopascals = measurements.pressureHectopascals,
    carbonDioxidePpm = measurements.carbonDioxidePpm,
    batteryPercent = measurements.batteryPercent,
    packetId = packetId,
)
