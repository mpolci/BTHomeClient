package io.github.mpolci.bthomeclient.bthome

/**
 * Decodes Android BLE service data after its UUID has been extracted by the BLE layer.
 * The implementation deliberately has no Android dependencies for fast JVM verification.
 */
class BTHomeV2Parser {
    fun parse(serviceUuid: Int, serviceData: ByteArray): BTHomeParseResult {
        if (serviceUuid != BTHOME_SERVICE_UUID) {
            return unsupported(BTHomeUnsupportedReason.WrongServiceUuid, serviceData)
        }
        if (serviceData.isEmpty()) {
            return malformed(BTHomeMalformedReason.MissingDeviceInformation, serviceData)
        }

        val deviceInformationByte = serviceData[0].unsigned()
        val deviceInformation = BTHomeDeviceInformation(
            version = (deviceInformationByte shr 5) and 0x07,
            encrypted = deviceInformationByte and ENCRYPTED_MASK != 0,
            triggerBased = deviceInformationByte and TRIGGER_BASED_MASK != 0,
        )
        if (deviceInformation.version != VERSION_2) {
            return unsupported(
                reason = BTHomeUnsupportedReason.UnsupportedVersion,
                data = serviceData,
                deviceInformation = deviceInformation,
            )
        }
        if (deviceInformation.encrypted) {
            return unsupported(
                reason = BTHomeUnsupportedReason.Encrypted,
                data = serviceData,
                deviceInformation = deviceInformation,
            )
        }

        var index = 1
        var previousObjectId = -1
        var measurements = BTHomeMeasurements()
        var metadata = BTHomeProtocolMetadata()
        val diagnostics = mutableListOf<BTHomeDiagnostic>()

        while (index < serviceData.size) {
            val objectId = serviceData[index].unsigned()
            if (objectId < previousObjectId) {
                return malformed(BTHomeMalformedReason.DescendingObjectIds, serviceData)
            }
            previousObjectId = objectId
            index += 1

            val objectLength = OBJECT_LENGTHS[objectId]
            if (objectLength == null) {
                diagnostics += BTHomeDiagnostic.UnsupportedObject(objectId)
                break
            }
            if (serviceData.size - index < objectLength) {
                return malformed(BTHomeMalformedReason.TruncatedObject, serviceData)
            }

            when (objectId) {
                OBJECT_PACKET_ID -> metadata = metadata.copy(packetId = keepFirst(
                    oldValue = metadata.packetId,
                    newValue = serviceData[index].unsigned(),
                    objectId = objectId,
                    diagnostics = diagnostics,
                ))
                OBJECT_BATTERY -> measurements = measurements.copy(batteryPercent = keepFirst(
                    measurements.batteryPercent, serviceData[index].unsigned(), objectId, diagnostics,
                ))
                OBJECT_TEMPERATURE -> measurements = measurements.copy(temperatureCelsius = keepFirst(
                    measurements.temperatureCelsius, serviceData.signed16LittleEndian(index) / 100.0, objectId, diagnostics,
                ))
                OBJECT_HUMIDITY -> measurements = measurements.copy(humidityPercent = keepFirst(
                    measurements.humidityPercent, serviceData.unsigned16LittleEndian(index) / 100.0, objectId, diagnostics,
                ))
                OBJECT_PRESSURE -> measurements = measurements.copy(pressureHectopascals = keepFirst(
                    measurements.pressureHectopascals, serviceData.unsigned24LittleEndian(index) / 100.0, objectId, diagnostics,
                ))
                OBJECT_CARBON_DIOXIDE -> measurements = measurements.copy(carbonDioxidePpm = keepFirst(
                    measurements.carbonDioxidePpm, serviceData.unsigned16LittleEndian(index), objectId, diagnostics,
                ))
                OBJECT_TIMESTAMP -> metadata = metadata.copy(timestampEpochMillis = keepFirst(
                    metadata.timestampEpochMillis, serviceData.unsigned32LittleEndian(index) * MILLIS_PER_SECOND, objectId, diagnostics,
                ))
                OBJECT_DEVICE_TYPE -> metadata = metadata.copy(deviceType = keepFirst(
                    metadata.deviceType, serviceData.unsigned16LittleEndian(index), objectId, diagnostics,
                ))
                OBJECT_FIRMWARE_FOUR_COMPONENTS,
                OBJECT_FIRMWARE_THREE_COMPONENTS -> metadata = metadata.copy(firmwareVersion = keepFirst(
                    metadata.firmwareVersion,
                    serviceData.copyOfRange(index, index + objectLength)
                        .reversedArray()
                        .joinToString(".") { it.unsigned().toString() },
                    objectId,
                    diagnostics,
                ))
            }
            index += objectLength
        }

        if (!measurements.hasSupportedValue) {
            return BTHomeParseResult.Unsupported(
                reason = BTHomeUnsupportedReason.NoSupportedMeasurements,
                diagnostics = diagnostics,
                deviceInformation = deviceInformation,
                metadata = metadata,
                rawServiceDataHex = serviceData.toHex(),
            )
        }
        return BTHomeParseResult.Decoded(
            DecodedBTHomeAdvertisement(deviceInformation, measurements, metadata, diagnostics),
        )
    }

    private fun <T> keepFirst(
        oldValue: T?,
        newValue: T,
        objectId: Int,
        diagnostics: MutableList<BTHomeDiagnostic>,
    ): T = if (oldValue == null) newValue else {
        diagnostics += BTHomeDiagnostic.DuplicateObject(objectId)
        oldValue
    }

    private fun unsupported(
        reason: BTHomeUnsupportedReason,
        data: ByteArray,
        deviceInformation: BTHomeDeviceInformation? = null,
        metadata: BTHomeProtocolMetadata? = null,
    ) = BTHomeParseResult.Unsupported(
        reason = reason,
        deviceInformation = deviceInformation,
        metadata = metadata,
        rawServiceDataHex = data.toHex(),
    )

    private fun malformed(reason: BTHomeMalformedReason, data: ByteArray) =
        BTHomeParseResult.Malformed(reason, data.toHex())

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun ByteArray.unsigned16LittleEndian(index: Int): Int =
        this[index].unsigned() or (this[index + 1].unsigned() shl 8)

    private fun ByteArray.signed16LittleEndian(index: Int): Int =
        unsigned16LittleEndian(index).let { if (it and 0x8000 == 0) it else it - 0x10000 }

    private fun ByteArray.unsigned24LittleEndian(index: Int): Int =
        this[index].unsigned() or (this[index + 1].unsigned() shl 8) or (this[index + 2].unsigned() shl 16)

    private fun ByteArray.unsigned32LittleEndian(index: Int): Long =
        this[index].unsigned().toLong() or
            (this[index + 1].unsigned().toLong() shl 8) or
            (this[index + 2].unsigned().toLong() shl 16) or
            (this[index + 3].unsigned().toLong() shl 24)

    private fun ByteArray.toHex(): String = joinToString(separator = " ") { "%02X".format(it.unsigned()) }

    private companion object {
        const val BTHOME_SERVICE_UUID = 0xFCD2
        const val VERSION_2 = 2
        const val ENCRYPTED_MASK = 0x01
        const val TRIGGER_BASED_MASK = 0x04
        const val MILLIS_PER_SECOND = 1_000L

        const val OBJECT_PACKET_ID = 0x00
        const val OBJECT_BATTERY = 0x01
        const val OBJECT_TEMPERATURE = 0x02
        const val OBJECT_HUMIDITY = 0x03
        const val OBJECT_PRESSURE = 0x04
        const val OBJECT_CARBON_DIOXIDE = 0x12
        const val OBJECT_TIMESTAMP = 0x50
        const val OBJECT_DEVICE_TYPE = 0xF0
        const val OBJECT_FIRMWARE_FOUR_COMPONENTS = 0xF1
        const val OBJECT_FIRMWARE_THREE_COMPONENTS = 0xF2

        val OBJECT_LENGTHS = mapOf(
            OBJECT_PACKET_ID to 1,
            OBJECT_BATTERY to 1,
            OBJECT_TEMPERATURE to 2,
            OBJECT_HUMIDITY to 2,
            OBJECT_PRESSURE to 3,
            OBJECT_CARBON_DIOXIDE to 2,
            OBJECT_TIMESTAMP to 4,
            OBJECT_DEVICE_TYPE to 2,
            OBJECT_FIRMWARE_FOUR_COMPONENTS to 4,
            OBJECT_FIRMWARE_THREE_COMPONENTS to 3,
        )
    }
}
