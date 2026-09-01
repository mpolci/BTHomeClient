package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.data.local.RegisteredSensorDao
import io.github.mpolci.bthomeclient.data.local.RegisteredSensorEntity
import io.github.mpolci.bthomeclient.data.local.SensorReadingDao
import io.github.mpolci.bthomeclient.data.local.SensorReadingEntity
import io.github.mpolci.bthomeclient.data.settings.SettingsStore
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SensorCoordinatorTest {
    @Test
    fun `deleting registration exposes fresh observation as unregistered`() = runTest {
        val registered = FakeRegisteredDao()
        val readings = FakeReadingDao()
        val clock = FakeClock(1_000)
        val coordinator = SensorCoordinator(registered, readings, FakeSettings(), clock, backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:90")
        val observation = ScanObservation(
            address,
            "Sensor",
            -50,
            1_000,
            BTHomeV2Parser().parse(0xFCD2, bytes("40 01 64")),
        )

        coordinator.observe(observation)
        coordinator.register(address, "Registered")
        runCurrent()
        assertEquals(true, coordinator.sensors.value.single().isRegistered)

        coordinator.deleteRegistration(address)
        runCurrent()

        assertEquals(emptyList<RegisteredSensorEntity>(), registered.rows.value)
        assertEquals(false, coordinator.sensors.value.single().isRegistered)
        assertEquals(observation, coordinator.sensors.value.single().observation)
    }

    @Test
    fun `history range and settings APIs expose mapped current values`() = runTest {
        val readings = FakeReadingDao()
        val settings = FakeSettings()
        val coordinator = SensorCoordinator(FakeRegisteredDao(), readings, settings, FakeClock(10_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:80")
        readings.seed(
            SensorReadingEntity(1, address.value, 100, null, 1.0, null, null, null, null, null),
            SensorReadingEntity(2, address.value, 200, null, 2.0, null, null, null, null, null),
            SensorReadingEntity(3, address.value, 300, null, 3.0, null, null, null, null, null),
        )
        assertEquals(listOf(100L, 200L, 300L), coordinator.observeHistory(address).first().map { it.receptionEpochMillis })
        assertEquals(listOf(200L), coordinator.observeHistoryInRange(address, 200, 200).first().map { it.receptionEpochMillis })
        assertEquals(io.github.mpolci.bthomeclient.domain.model.AppSettings(), coordinator.settings.first())
        val updated = io.github.mpolci.bthomeclient.domain.model.AppSettings(60_000, null)
        coordinator.saveSettings(updated)
        assertEquals(updated, coordinator.settings.first())
    }

    @Test
    fun `retention keeps unlimited rows then prunes on save and insert`() = runTest {
        val readings = FakeReadingDao()
        val clock = FakeClock(1_000)
        val coordinator = SensorCoordinator(FakeRegisteredDao(), readings, FakeSettings(), clock, backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:70")
        coordinator.register(address, "Retention")
        readings.seed(
            SensorReadingEntity(1, address.value, 100, null, null, null, null, null, 1, null),
            SensorReadingEntity(2, address.value, 500, null, null, null, null, null, 2, null),
        )
        coordinator.saveSettings(io.github.mpolci.bthomeclient.domain.model.AppSettings(1, null))
        val parser = BTHomeV2Parser()
        fun sample(time: Long) = ScanObservation(address, null, -50, time, parser.parse(0xFCD2, bytes("40 01 64")))
        coordinator.observe(sample(1_000))
        assertEquals(listOf(100L, 500L, 1_000L), readings.inserted.map { it.receptionEpochMillis })

        coordinator.saveSettings(io.github.mpolci.bthomeclient.domain.model.AppSettings(1, 200))
        assertEquals(listOf(1_000L), readings.inserted.map { it.receptionEpochMillis })
        clock.now = 1_300
        coordinator.observe(sample(1_300))
        assertEquals(listOf(1_300L), readings.inserted.map { it.receptionEpochMillis })
    }

    @Test
    fun `concurrent same sensor observations serialize to one persisted sample`() = runTest {
        val registered = FakeRegisteredDao()
        val readings = FakeReadingDao()
        val coordinator = SensorCoordinator(registered, readings, FakeSettings(), FakeClock(10_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:60")
        coordinator.register(address, "Concurrent")
        val parser = BTHomeV2Parser()
        val jobs = (1..32).map { index ->
            launch {
                coordinator.observe(
                    ScanObservation(address, "Sensor $index", -50, 1_000, parser.parse(0xFCD2, bytes("40 02 C4 09"))),
                )
            }
        }
        jobs.joinAll()
        runCurrent()

        assertEquals(1, readings.inserted.size)
        assertEquals(1_000L, readings.inserted.single().receptionEpochMillis)
        assertEquals(1_000L, registered.rows.value.single().lastSeenEpochMillis)
        assertEquals(1_000L, coordinator.sensors.value.single().observation!!.receptionEpochMillis)
    }

    @Test
    fun `persistence throttle is independent per sensor and includes exact interval boundary`() = runTest {
        val registered = FakeRegisteredDao()
        val readings = FakeReadingDao()
        val coordinator = SensorCoordinator(registered, readings, FakeSettings(), FakeClock(1_000_000), backgroundScope)
        val first = SensorAddress.require("AA:BB:CC:DD:EE:50")
        val second = SensorAddress.require("AA:BB:CC:DD:EE:51")
        coordinator.register(first, "A")
        coordinator.register(second, "B")
        val parser = BTHomeV2Parser()
        fun sample(address: SensorAddress, time: Long) = ScanObservation(address, null, -50, time, parser.parse(0xFCD2, bytes("40 01 64")))

        coordinator.observe(sample(first, 0))
        coordinator.observe(sample(first, 899_999))
        coordinator.observe(sample(second, 899_999))
        coordinator.observe(sample(first, 900_000))

        assertEquals(
            listOf("AA:BB:CC:DD:EE:50@0", "AA:BB:CC:DD:EE:51@899999", "AA:BB:CC:DD:EE:50@900000"),
            readings.inserted.map { "${it.sensorAddress}@${it.receptionEpochMillis}" },
        )
    }

    @Test
    fun `unregistered observations remain live only and never persist`() = runTest {
        val registered = FakeRegisteredDao()
        val readings = FakeReadingDao()
        val coordinator = SensorCoordinator(registered, readings, FakeSettings(), FakeClock(10_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:40")
        val parser = BTHomeV2Parser()
        coordinator.observe(ScanObservation(address, "First", -60, 1_000, parser.parse(0xFCD2, bytes("40 02 C4 09"))))
        coordinator.observe(ScanObservation(address, "Latest", -50, 2_000, parser.parse(0xFCD2, bytes("40 03 BF 13"))))
        runCurrent()

        val item = coordinator.sensors.value.single()
        assertEquals("Latest", item.observation!!.advertisedName)
        assertEquals(2_000L, item.observation!!.receptionEpochMillis)
        assertEquals(emptyList<RegisteredSensorEntity>(), registered.rows.value)
        assertEquals(emptyList<SensorReadingEntity>(), readings.inserted)
    }

    @Test
    fun `editable metadata update preserves technical fields`() = runTest {
        val original = RegisteredSensorEntity(
            "AA:BB:CC:DD:EE:30", "Old name", "Old room", "old-uri", 42, "Advertisement", 1234,
            -61, 2, true, false, 0x4321, "4.2.1.0", "UnsupportedObject", "Unsupported", "Humidity,Temperature",
        )
        val registered = FakeRegisteredDao().also { it.rows.value = listOf(original) }
        val coordinator = SensorCoordinator(registered, FakeReadingDao(), FakeSettings(), FakeClock(10_000), backgroundScope)

        coordinator.updateEditableMetadata(SensorAddress.require(original.address), "  New name  ", "  New room  ", "new-uri")

        val updated = registered.rows.value.single()
        assertEquals("New name", updated.displayName)
        assertEquals("New room", updated.room)
        assertEquals("new-uri", updated.imageUri)
        assertEquals(original.copy(displayName = updated.displayName, room = updated.room, imageUri = updated.imageUri), updated)
    }

    @Test
    fun `newer sparse observation preserves metadata and older observation is ignored`() = runTest {
        val registered = FakeRegisteredDao()
        val coordinator = SensorCoordinator(registered, FakeReadingDao(), FakeSettings(), FakeClock(10_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:20")
        val parser = BTHomeV2Parser()
        coordinator.observe(ScanObservation(address, "Original", -50, 1_000, parser.parse(0xFCD2, bytes("40 02 C4 09 F0 34 12 F1 00 01 02 04"))))
        coordinator.register(address, "Registered")
        coordinator.observe(ScanObservation(address, null, -51, 2_000, parser.parse(0xFCD2, bytes("40 03 BF 13"))))
        coordinator.observe(ScanObservation(address, "Older", -99, 1_500, parser.parse(0xFCD2, bytes("40 02 10 00 F0 78 56 F1 01 01 01 01"))))
        runCurrent()

        val row = registered.rows.value.single()
        assertEquals("Original", row.advertisedName)
        assertEquals(0x1234, row.deviceType)
        assertEquals("4.2.1.0", row.firmwareVersion)
        assertEquals(2_000L, row.lastSeenEpochMillis)
        assertEquals("Humidity,Temperature", row.supportedMeasurementTypes)
        val merged = coordinator.sensors.value.single()
        assertEquals(2_000L, merged.observation!!.receptionEpochMillis)
        assertEquals(50.55, merged.observation!!.measurements!!.humidityPercent!!, 0.0)
    }

    @Test
    fun `restart keeps registered offline sensor with persisted status and latest reading`() = runTest {
        val address = "AA:BB:CC:DD:EE:10"
        val registered = FakeRegisteredDao().also {
            it.rows.value = listOf(
                RegisteredSensorEntity(
                    address, "Stored", null, null, 1, "Advertised", 0, -42, 2, false, false,
                    0x1234, "1.0.0", "NoSupportedMeasurements", "Unsupported", "Temperature",
                ),
            )
        }
        val readings = FakeReadingDao().also {
            it.seed(SensorReadingEntity(1, address, 123, null, 22.5, null, null, null, 90, 7))
        }
        val coordinator = SensorCoordinator(registered, readings, FakeSettings(), FakeClock(300_000), backgroundScope)
        runCurrent()

        val item = coordinator.sensors.value.single()
        assertEquals(false, item.isOnline)
        assertEquals(null, item.observation)
        assertEquals(io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus.Unsupported, item.supportStatus)
        assertEquals(22.5, item.latestReading!!.measurements.temperatureCelsius!!, 0.0)
        assertEquals(90, item.latestReading!!.measurements.batteryPercent)
    }

    @Test
    fun `registered sensors precede fresh unregistered sensors in stable order`() = runTest {
        val registered = FakeRegisteredDao()
        val coordinator = SensorCoordinator(registered, FakeReadingDao(), FakeSettings(), FakeClock(10_000), backgroundScope)
        val registeredFirst = SensorAddress.require("AA:BB:CC:DD:EE:FE")
        val registeredSecond = SensorAddress.require("AA:BB:CC:DD:EE:01")
        coordinator.register(registeredFirst, "First registered")
        coordinator.register(registeredSecond, "Second registered")

        val parser = BTHomeV2Parser()
        fun observation(address: String, time: Long) = ScanObservation(
            address = SensorAddress.require(address),
            advertisedName = null,
            rssi = -50,
            receptionEpochMillis = time,
            parseResult = parser.parse(0xFCD2, bytes("40 01 64")),
        )
        coordinator.observe(observation("AA:BB:CC:DD:EE:03", 9_000))
        coordinator.observe(observation("AA:BB:CC:DD:EE:02", 9_000))
        coordinator.observe(observation("AA:BB:CC:DD:EE:04", 9_500))
        runCurrent()

        assertEquals(
            listOf(
                "AA:BB:CC:DD:EE:FE",
                "AA:BB:CC:DD:EE:01",
                "AA:BB:CC:DD:EE:04",
                "AA:BB:CC:DD:EE:02",
                "AA:BB:CC:DD:EE:03",
            ),
            coordinator.sensors.value.map { it.address.value },
        )
        assertEquals(listOf(true, true, false, false, false), coordinator.sensors.value.map { it.isRegistered })
    }

    @Test
    fun `registration persists active sample and throttle prevents duplicate`() = runTest {
        val registered = FakeRegisteredDao()
        val readings = FakeReadingDao()
        val settings = FakeSettings()
        val clock = FakeClock(1_000)
        val coordinator = SensorCoordinator(registered, readings, settings, clock, backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:FF")
        val observation = ScanObservation(address, "Sensor", -60, 1_000, BTHomeV2Parser().parse(0xFCD2, bytes("40 02 C4 09")))

        coordinator.observe(observation)
        coordinator.register(address, "Kitchen")
        coordinator.observe(observation.copy(receptionEpochMillis = 2_000))

        assertEquals(1, readings.inserted.size)
        assertEquals(1, registered.rows.value.size)
    }

    @Test
    fun `tick removes observation exactly at five minute boundary`() = runTest {
        val clock = FakeClock(0)
        val coordinator = SensorCoordinator(FakeRegisteredDao(), FakeReadingDao(), FakeSettings(), clock, backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:01")
        coordinator.observe(ScanObservation(address, null, -1, 0, BTHomeV2Parser().parse(0xFCD2, bytes("40 01 64"))))
        runCurrent()
        assertEquals(1, coordinator.sensors.value.size)
        clock.now = 300_000
        coordinator.onTimeTick()
        runCurrent()
        // Freshness is strictly newer than now minus five minutes.
        assertEquals(0, coordinator.sensors.value.size)
    }

    private class FakeClock(var now: Long) : Clock { override fun nowEpochMillis() = now }
    private class FakeSettings : SettingsStore {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun save(settings: AppSettings) { this.settings.value = settings }
    }
    private class FakeRegisteredDao : RegisteredSensorDao {
        val rows = MutableStateFlow<List<RegisteredSensorEntity>>(emptyList())
        override fun observeAll(): Flow<List<RegisteredSensorEntity>> = rows
        override suspend fun findByAddress(address: String) = rows.value.firstOrNull { it.address == address }
        override suspend fun maxRegistrationOrder() = rows.value.maxOfOrNull { it.registrationOrder } ?: 0
        override suspend fun insert(sensor: RegisteredSensorEntity) { rows.value += sensor }
        override suspend fun update(sensor: RegisteredSensorEntity) { rows.value = rows.value.map { if (it.address == sensor.address) sensor else it } }
        override suspend fun delete(sensor: RegisteredSensorEntity) { rows.value = rows.value.filterNot { it.address == sensor.address } }
    }
    private class FakeReadingDao : SensorReadingDao {
        val inserted = mutableListOf<SensorReadingEntity>()
        private val allRows = MutableStateFlow(emptyList<SensorReadingEntity>())
        private val latestRows = MutableStateFlow(emptyList<SensorReadingEntity>())
        fun seed(vararg readings: SensorReadingEntity) { inserted += readings; refresh() }
        override suspend fun insert(reading: SensorReadingEntity) = 0L.also { inserted += reading; refresh() }
        override suspend fun latest(address: String) = inserted.lastOrNull { it.sensorAddress == address }
        override fun observeHistory(address: String) = allRows.map { rows -> rows.filter { it.sensorAddress == address }.sortedBy { it.receptionEpochMillis } }
        override fun observeHistoryInRange(address: String, fromEpochMillis: Long, toEpochMillis: Long) = allRows.map { rows -> rows.filter { it.sensorAddress == address && it.receptionEpochMillis in fromEpochMillis..toEpochMillis }.sortedBy { it.receptionEpochMillis } }
        override fun observeLatestForAllSensors() = latestRows
        override suspend fun deleteOlderThan(beforeEpochMillis: Long) { inserted.removeAll { it.receptionEpochMillis < beforeEpochMillis }; refresh() }
        private fun refresh() { allRows.value = inserted.toList(); latestRows.value = inserted.groupBy { it.sensorAddress }.map { it.value.maxBy { row -> row.receptionEpochMillis } } }
    }
    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
