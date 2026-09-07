package io.github.mpolci.bthomeclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface RegisteredSensorDao {
    @Query("SELECT * FROM registered_sensors ORDER BY registrationOrder ASC")
    fun observeAll(): Flow<List<RegisteredSensorEntity>>

    @Query("SELECT * FROM registered_sensors WHERE address = :address")
    suspend fun findByAddress(address: String): RegisteredSensorEntity?

    @Query("SELECT COALESCE(MAX(registrationOrder), 0) FROM registered_sensors")
    suspend fun maxRegistrationOrder(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sensor: RegisteredSensorEntity)

    @Update
    suspend fun update(sensor: RegisteredSensorEntity)

    @Delete
    suspend fun delete(sensor: RegisteredSensorEntity)

    @Transaction
    suspend fun insertWithNextRegistrationOrder(sensor: RegisteredSensorEntity) {
        insert(sensor.copy(registrationOrder = maxRegistrationOrder() + 1))
    }
}

@Dao
interface DownloadedHistoryDao {
    @Query("SELECT registrationId FROM registered_sensors WHERE address = :address")
    suspend fun registrationId(address: String): String?

    @Query("UPDATE registered_sensors SET historySupport = :support WHERE address = :address AND registrationId = :registrationId")
    suspend fun updateHistorySupport(address: String, registrationId: String, support: String): Int

    @Query("SELECT * FROM history_sync_states WHERE sensorAddress = :address")
    suspend fun syncState(address: String): HistorySyncStateEntity?

    @Query("SELECT * FROM history_sync_states WHERE sensorAddress = :address")
    fun observeSyncState(address: String): Flow<HistorySyncStateEntity?>

    @Query("SELECT COUNT(*) FROM downloaded_history_samples WHERE sensorAddress = :address")
    fun observeSampleCount(address: String): Flow<Int>

    @Query("SELECT * FROM downloaded_history_samples WHERE sensorAddress = :address ORDER BY acquisitionSequence ASC")
    fun observeSamples(address: String): Flow<List<DownloadedHistorySampleEntity>>

    @Query("SELECT * FROM downloaded_history_samples WHERE sensorAddress = :address ORDER BY acquisitionSequence ASC")
    suspend fun samples(address: String): List<DownloadedHistorySampleEntity>

    @Query("SELECT * FROM downloaded_history_samples WHERE sensorAddress = :address AND sourceFormatVersion = :formatVersion AND sourceGeneration = :generation AND sourceSlot = :slot")
    suspend fun samplesAtSource(address: String, formatVersion: Int, generation: Long, slot: Int): List<DownloadedHistorySampleEntity>

    @Query("SELECT * FROM downloaded_history_samples WHERE sensorAddress = :address AND recordedEpochMillis BETWEEN :fromEpochMillis AND :toEpochMillis ORDER BY acquisitionSequence ASC")
    fun observeSamplesInRange(address: String, fromEpochMillis: Long, toEpochMillis: Long): Flow<List<DownloadedHistorySampleEntity>>

    @Insert
    suspend fun insertSamples(samples: List<DownloadedHistorySampleEntity>)

    @androidx.room.Update
    suspend fun updateSample(sample: DownloadedHistorySampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncState(state: HistorySyncStateEntity)

    @Query("DELETE FROM downloaded_history_samples WHERE recordedEpochMillis IS NOT NULL AND recordedEpochMillis < :beforeEpochMillis")
    suspend fun deleteDatedSamplesOlderThan(beforeEpochMillis: Long)

    @Transaction
    suspend fun insertInitialDownload(
        samples: List<DownloadedHistorySampleEntity>,
        state: HistorySyncStateEntity,
    ) {
        insertSamples(samples)
        insertSyncState(state)
    }

    @Transaction
    suspend fun commitDownload(
        samples: List<DownloadedHistorySampleEntity>,
        state: HistorySyncStateEntity,
        mergeExactSourceRecords: Boolean,
    ) {
        if (!mergeExactSourceRecords) {
            insertSamples(samples)
        } else {
            samples.forEach { sample ->
                val existing = samplesAtSource(
                    sample.sensorAddress,
                    sample.sourceFormatVersion,
                    sample.sourceGeneration,
                    sample.sourceSlot,
                ).firstOrNull { it.rawPosition.contentEquals(sample.rawPosition) }
                if (existing == null) {
                    insertSamples(listOf(sample))
                } else {
                    updateSample(sample.copy(id = existing.id, acquisitionSequence = existing.acquisitionSequence))
                }
            }
        }
        insertSyncState(state)
    }

    @Transaction
    suspend fun commitForRegistration(
        samples: List<DownloadedHistorySampleEntity>,
        state: HistorySyncStateEntity,
        mergeExactSourceRecords: Boolean,
        expectedRegistrationId: String,
    ): Boolean {
        if (registrationId(state.sensorAddress) != expectedRegistrationId) return false
        commitDownload(samples, state, mergeExactSourceRecords)
        return true
    }
}
