package io.github.mpolci.bthomeclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RegisteredSensorEntity::class, SensorReadingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BTHomeDatabase : RoomDatabase() {
    abstract fun registeredSensorDao(): RegisteredSensorDao
    abstract fun sensorReadingDao(): SensorReadingDao
}
