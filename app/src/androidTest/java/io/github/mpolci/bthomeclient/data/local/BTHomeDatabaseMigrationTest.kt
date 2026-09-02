package io.github.mpolci.bthomeclient.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BTHomeDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BTHomeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFromV1PreservesRegistrationAndDropsAdvertisementHistory() = runBlocking {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL("INSERT INTO registered_sensors (address, displayName, registrationOrder) VALUES ('AA:BB:CC:DD:EE:01', 'Legacy', 1)")
            execSQL("INSERT INTO sensor_readings (sensorAddress, receptionEpochMillis) VALUES ('AA:BB:CC:DD:EE:01', 1)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, BTHomeDatabase.MIGRATION_1_2).close()
        val migrated = BTHomeDatabase.MIGRATION_1_2
        val database = androidx.room.Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BTHomeDatabase::class.java,
            TEST_DATABASE,
        ).addMigrations(migrated).allowMainThreadQueries().build()

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
