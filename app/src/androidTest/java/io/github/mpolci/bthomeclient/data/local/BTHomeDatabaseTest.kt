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
    fun deletingSensorCascadesToReadings() = runBlocking {
        val sensors = database.registeredSensorDao()
        val readings = database.sensorReadingDao()
        sensors.insertWithNextRegistrationOrder(sensor("AA:BB:CC:DD:EE:01", "First"))
        readings.insert(
            SensorReadingEntity(
                sensorAddress = "AA:BB:CC:DD:EE:01",
                receptionEpochMillis = 1,
                bthomeTimestampEpochMillis = null,
                temperatureCelsius = null,
                humidityPercent = null,
                pressureHectopascals = null,
                carbonDioxidePpm = null,
                batteryPercent = null,
                packetId = null,
            ),
        )
        sensors.delete(sensor("AA:BB:CC:DD:EE:01", "First").copy(registrationOrder = 1))

        assertEquals(0, readings.observeHistory("AA:BB:CC:DD:EE:01").first().size)
    }

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
