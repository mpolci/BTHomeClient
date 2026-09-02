package io.github.mpolci.bthomeclient.data.local

import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress

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
