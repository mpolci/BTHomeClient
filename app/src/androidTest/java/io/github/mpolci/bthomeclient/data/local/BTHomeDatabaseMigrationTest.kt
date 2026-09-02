package io.github.mpolci.bthomeclient.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BTHomeDatabaseMigrationTest {
    @Test
    fun migrationFromV1PreservesRegistrationAndDropsAdvertisementHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.openOrCreateDatabase(TEST_DATABASE, 0, null).apply {
            execSQL("CREATE TABLE registered_sensors (address TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, room TEXT, imageUri TEXT, registrationOrder INTEGER NOT NULL, advertisedName TEXT, lastSeenEpochMillis INTEGER, lastRssi INTEGER, lastBTHomeVersion INTEGER, lastEncrypted INTEGER, lastTriggerBased INTEGER, deviceType INTEGER, firmwareVersion TEXT, protocolDiagnostic TEXT, lastSupportStatus TEXT, supportedMeasurementTypes TEXT)")
            execSQL("CREATE TABLE sensor_readings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sensorAddress TEXT NOT NULL, receptionEpochMillis INTEGER NOT NULL, bthomeTimestampEpochMillis INTEGER, temperatureCelsius REAL, humidityPercent REAL, pressureHectopascals REAL, carbonDioxidePpm INTEGER, batteryPercent INTEGER, packetId INTEGER, FOREIGN KEY(sensorAddress) REFERENCES registered_sensors(address) ON DELETE CASCADE)")
            execSQL("INSERT INTO registered_sensors (address, displayName, registrationOrder) VALUES ('AA:BB:CC:DD:EE:01', 'Legacy', 1)")
            execSQL("INSERT INTO sensor_readings (sensorAddress, receptionEpochMillis) VALUES ('AA:BB:CC:DD:EE:01', 1)")
            setVersion(1)
            close()
        }

        val database = androidx.room.Room.databaseBuilder(
            context,
            BTHomeDatabase::class.java,
            TEST_DATABASE,
        ).addMigrations(BTHomeDatabase.MIGRATION_1_2).allowMainThreadQueries().build()

        try {
            assertEquals("Legacy", database.registeredSensorDao().findByAddress("AA:BB:CC:DD:EE:01")!!.displayName)
            assertEquals(0, database.downloadedHistoryDao().observeSampleCount("AA:BB:CC:DD:EE:01").value())
            assertEquals(null, database.downloadedHistoryDao().syncState("AA:BB:CC:DD:EE:01"))
        } finally {
            database.close()
        }
    }

    private suspend fun kotlinx.coroutines.flow.Flow<Int>.value(): Int = first()

    private companion object {
        const val TEST_DATABASE = "bthome-migration-test"
    }
}
