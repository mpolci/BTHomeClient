package io.github.mpolci.bthomeclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RegisteredSensorEntity::class, DownloadedHistorySampleEntity::class, HistorySyncStateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BTHomeDatabase : RoomDatabase() {
    abstract fun registeredSensorDao(): RegisteredSensorDao
    abstract fun downloadedHistoryDao(): DownloadedHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sensor_readings")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS downloaded_history_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sensorAddress TEXT NOT NULL, " +
                        "acquisitionSequence INTEGER NOT NULL, " +
                        "sourceFormatVersion INTEGER NOT NULL, " +
                        "sourceGeneration INTEGER NOT NULL, " +
                        "sourceSlot INTEGER NOT NULL, " +
                        "rawPosition BLOB NOT NULL, " +
                        "recordedEpochMillis INTEGER, " +
                        "timeQuality TEXT NOT NULL, " +
                        "temperatureCelsius REAL NOT NULL, " +
                        "humidityPercent INTEGER NOT NULL, " +
                        "pressureHectopascals REAL NOT NULL, " +
                        "batteryPercent INTEGER NOT NULL, " +
                        "FOREIGN KEY(sensorAddress) REFERENCES registered_sensors(address) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_history_samples_sensorAddress_recordedEpochMillis ON downloaded_history_samples (sensorAddress, recordedEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_history_samples_sensorAddress_sourceFormatVersion_sourceGeneration_sourceSlot ON downloaded_history_samples (sensorAddress, sourceFormatVersion, sourceGeneration, sourceSlot)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_history_samples_sensorAddress_acquisitionSequence ON downloaded_history_samples (sensorAddress, acquisitionSequence)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS history_sync_states (" +
                        "sensorAddress TEXT NOT NULL, " +
                        "cursor BLOB NOT NULL, " +
                        "nextAcquisitionSequence INTEGER NOT NULL, " +
                        "lastSuccessfulDownloadEpochMillis INTEGER NOT NULL, " +
                        "syncPending INTEGER NOT NULL, " +
                        "historyTruncated INTEGER NOT NULL, " +
                        "PRIMARY KEY(sensorAddress), " +
                        "FOREIGN KEY(sensorAddress) REFERENCES registered_sensors(address) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }
        }
    }
}
