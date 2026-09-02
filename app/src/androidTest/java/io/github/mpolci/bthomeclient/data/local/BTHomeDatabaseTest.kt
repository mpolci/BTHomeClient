package io.github.mpolci.bthomeclient.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BTHomeDatabaseTest {
    private lateinit var database: BTHomeDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BTHomeDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun registeredSensorsAreOrderedByAllocatedRegistrationOrder() = runBlocking {
        val dao = database.registeredSensorDao()
        dao.insertWithNextRegistrationOrder(sensor("AA:BB:CC:DD:EE:01", "First"))
        dao.insertWithNextRegistrationOrder(sensor("AA:BB:CC:DD:EE:02", "Second"))

        assertEquals(listOf("First", "Second"), dao.observeAll().first().map { it.displayName })
    }

    @Test
    fun deletingSensorCascadesToDownloadedSamplesAndSyncState() = runBlocking {
        val sensors = database.registeredSensorDao()
        val history = database.downloadedHistoryDao()
        val address = "AA:BB:CC:DD:EE:01"
        sensors.insertWithNextRegistrationOrder(sensor(address, "First"))
        history.insertInitialDownload(
            listOf(sample(address)),
            HistorySyncStateEntity(address, ByteArray(10), 1, 1, false, false),
        )

        sensors.delete(sensor(address, "First").copy(registrationOrder = 1))

        assertEquals(0, history.observeSampleCount(address).first())
        assertEquals(null, history.syncState(address))
    }

    @Test
    fun retentionPrunesOnlyDatedDownloadedSamples() = runBlocking {
        val address = "AA:BB:CC:DD:EE:01"
        database.registeredSensorDao().insertWithNextRegistrationOrder(sensor(address, "First"))
        val history = database.downloadedHistoryDao()
        history.insertSamples(listOf(sample(address, recordedEpochMillis = 99), sample(address, recordedEpochMillis = null)))

        history.deleteDatedSamplesOlderThan(100)

        assertEquals(listOf(null), history.observeSamples(address).first().map { it.recordedEpochMillis })
    }

    private fun sample(address: String, recordedEpochMillis: Long? = 1) = DownloadedHistorySampleEntity(
        sensorAddress = address,
        acquisitionSequence = 0,
        sourceFormatVersion = 1,
        sourceGeneration = 1,
        sourceSlot = 0,
        rawPosition = ByteArray(16),
        recordedEpochMillis = recordedEpochMillis,
        timeQuality = "DIRECT_UTC",
        temperatureCelsius = 20.0,
        humidityPercent = 40,
        pressureHectopascals = 1000.0,
        batteryPercent = 80,
    )

    private fun sensor(address: String, name: String) = RegisteredSensorEntity(
        address = address,
        displayName = name,
        room = null,
        imageUri = null,
        registrationOrder = 0,
        advertisedName = null,
        lastSeenEpochMillis = null,
        lastRssi = null,
        lastBTHomeVersion = null,
        lastEncrypted = null,
        lastTriggerBased = null,
        deviceType = null,
        firmwareVersion = null,
        protocolDiagnostic = null,
        lastSupportStatus = null,
        supportedMeasurementTypes = null,
    )
}
