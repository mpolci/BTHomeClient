package io.github.mpolci.bthomeclient.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

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
    val latestReadingEpochMillis: Long? = null,
    val latestTemperatureCelsius: Double? = null,
    val latestHumidityPercent: Double? = null,
    val latestPressureHectopascals: Double? = null,
    val latestCarbonDioxidePpm: Int? = null,
    val latestBatteryPercent: Int? = null,
    @ColumnInfo(defaultValue = "''") val registrationId: String = "",
    val historySupport: String? = null,
)

@Entity(
    tableName = "downloaded_history_samples",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredSensorEntity::class,
            parentColumns = ["address"],
            childColumns = ["sensorAddress"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sensorAddress", "recordedEpochMillis"]),
        Index(value = ["sensorAddress", "sourceFormatVersion", "sourceGeneration", "sourceSlot"]),
        Index(value = ["sensorAddress", "acquisitionSequence"]),
    ],
)
data class DownloadedHistorySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorAddress: String,
    val acquisitionSequence: Long,
    val sourceFormatVersion: Int,
    val sourceGeneration: Long,
    val sourceSlot: Int,
    val rawPosition: ByteArray,
    val recordedEpochMillis: Long?,
    val timeQuality: String,
    val temperatureCelsius: Double,
    val humidityPercent: Int,
    val pressureHectopascals: Double,
    val batteryPercent: Int,
)

@Entity(
    tableName = "history_sync_states",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredSensorEntity::class,
            parentColumns = ["address"],
            childColumns = ["sensorAddress"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HistorySyncStateEntity(
    @PrimaryKey val sensorAddress: String,
    val cursor: ByteArray,
    val nextAcquisitionSequence: Long,
    val lastSuccessfulDownloadEpochMillis: Long,
    val syncPending: Boolean,
    val historyTruncated: Boolean,
)
