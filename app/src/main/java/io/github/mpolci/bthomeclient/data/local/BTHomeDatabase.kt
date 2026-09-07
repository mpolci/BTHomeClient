package io.github.mpolci.bthomeclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RegisteredSensorEntity::class, DownloadedHistorySampleEntity::class, HistorySyncStateEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class BTHomeDatabase : RoomDatabase() {
    abstract fun registeredSensorDao(): RegisteredSensorDao
    abstract fun downloadedHistoryDao(): DownloadedHistoryDao

    companion object {
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateKnownSchemaToVersion3(db)
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateKnownSchemaToVersion3(db)
        }

        private fun migrateKnownSchemaToVersion3(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "latestReadingEpochMillis", "INTEGER")
            addColumnIfMissing(db, "latestTemperatureCelsius", "REAL")
            addColumnIfMissing(db, "latestHumidityPercent", "REAL")
            addColumnIfMissing(db, "latestPressureHectopascals", "REAL")
            addColumnIfMissing(db, "latestCarbonDioxidePpm", "INTEGER")
            addColumnIfMissing(db, "latestBatteryPercent", "INTEGER")
            addColumnIfMissing(db, "registrationId", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "historySupport", "TEXT")
            db.execSQL(
                "UPDATE registered_sensors SET registrationId = " +
                    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || " +
                    "lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || " +
                    "lower(hex(randomblob(6))) WHERE registrationId = ''",
            )

            if (!tableExists(db, "downloaded_history_samples")) {
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
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_history_samples_sensorAddress_recordedEpochMillis ON downloaded_history_samples (sensorAddress, recordedEpochMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_history_samples_sensorAddress_sourceFormatVersion_sourceGeneration_sourceSlot ON downloaded_history_samples (sensorAddress, sourceFormatVersion, sourceGeneration, sourceSlot)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_history_samples_sensorAddress_acquisitionSequence ON downloaded_history_samples (sensorAddress, acquisitionSequence)")

            if (!tableExists(db, "history_sync_states")) {
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

            // Legacy rows came from live advertisements, so they must not be relabeled as sensor-downloaded history.
            db.execSQL("DROP TABLE IF EXISTS sensor_readings")
        }

        private fun addColumnIfMissing(db: SupportSQLiteDatabase, name: String, declaration: String) {
            if (!columnExists(db, "registered_sensors", name)) {
                db.execSQL("ALTER TABLE registered_sensors ADD COLUMN $name $declaration")
            }
        }

        private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean =
            db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

        private fun columnExists(db: SupportSQLiteDatabase, table: String, name: String): Boolean =
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }.any { it == name }
            }
    }
}
