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
interface SensorReadingDao {
    @Insert
    suspend fun insert(reading: SensorReadingEntity): Long

    @Query("SELECT * FROM sensor_readings WHERE sensorAddress = :address ORDER BY receptionEpochMillis DESC LIMIT 1")
    suspend fun latest(address: String): SensorReadingEntity?

    @Query("SELECT * FROM sensor_readings WHERE sensorAddress = :address ORDER BY receptionEpochMillis ASC")
    fun observeHistory(address: String): Flow<List<SensorReadingEntity>>

    @Query("SELECT * FROM sensor_readings WHERE sensorAddress = :address AND receptionEpochMillis BETWEEN :fromEpochMillis AND :toEpochMillis ORDER BY receptionEpochMillis ASC")
    fun observeHistoryInRange(address: String, fromEpochMillis: Long, toEpochMillis: Long): Flow<List<SensorReadingEntity>>

    @Query("SELECT sensor_readings.* FROM sensor_readings INNER JOIN (SELECT sensorAddress, MAX(receptionEpochMillis) AS latestReceptionEpochMillis FROM sensor_readings GROUP BY sensorAddress) latest ON sensor_readings.sensorAddress = latest.sensorAddress AND sensor_readings.receptionEpochMillis = latest.latestReceptionEpochMillis")
    fun observeLatestForAllSensors(): Flow<List<SensorReadingEntity>>

    @Query("DELETE FROM sensor_readings WHERE receptionEpochMillis < :beforeEpochMillis")
    suspend fun deleteOlderThan(beforeEpochMillis: Long)

    @Transaction
    suspend fun insertAndApplyRetention(reading: SensorReadingEntity, beforeEpochMillis: Long?) {
        insert(reading)
        if (beforeEpochMillis != null) deleteOlderThan(beforeEpochMillis)
    }
}
