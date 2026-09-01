package io.github.mpolci.bthomeclient.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "registered_sensors")
data class RegisteredSensorEntity(
    @PrimaryKey val address: String,
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
    val lastSupportStatus: String?,
    val supportedMeasurementTypes: String?,
)

@Entity(
    tableName = "sensor_readings",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredSensorEntity::class,
            parentColumns = ["address"],
            childColumns = ["sensorAddress"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sensorAddress"), Index(value = ["sensorAddress", "receptionEpochMillis"])],
)
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorAddress: String,
    val receptionEpochMillis: Long,
    val bthomeTimestampEpochMillis: Long?,
    val temperatureCelsius: Double?,
    val humidityPercent: Double?,
    val pressureHectopascals: Double?,
    val carbonDioxidePpm: Int?,
    val batteryPercent: Int?,
    val packetId: Int?,
)
