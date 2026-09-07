package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.data.local.DownloadedHistoryDao
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.data.local.RegisteredSensorDao
import io.github.mpolci.bthomeclient.data.local.RegisteredSensorEntity
import io.github.mpolci.bthomeclient.data.settings.SettingsStore
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SensorCoordinatorTest {
    @Test
    fun `advertisements update live state and metadata but never write history`() = runTest {
        val registered = FakeRegisteredDao()
        val history = FakeHistoryDao()
        val coordinator = SensorCoordinator(registered, history, FakeSettings(), FakeClock(10_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:01")
        val parser = BTHomeV2Parser()

        coordinator.observe(observation(address, 1_000, parser, "First", "40 02 C4 09 F0 34 12"))
        coordinator.register(address, "Kitchen")
        coordinator.observe(observation(address, 2_000, parser, null, "40 03 BF 13"))
        runCurrent()

        val item = coordinator.sensors.value.single()
        assertEquals(2_000L, item.observation!!.receptionEpochMillis)
        assertEquals(50.55, item.observation!!.measurements!!.humidityPercent!!, 0.0)
        assertEquals(0x1234, registered.rows.value.single().deviceType)
        assertEquals(emptyList<DownloadedHistorySampleEntity>(), history.samples.value)
    }

    @Test
    fun `registration persists the latest reading without writing history`() = runTest {
        val history = FakeHistoryDao()
        val registered = FakeRegisteredDao()
        val coordinator = SensorCoordinator(registered, history, FakeSettings(), FakeClock(1_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:02")

        coordinator.observe(observation(address, 1_000, BTHomeV2Parser(), "Sensor", "40 02 C4 09"))
        coordinator.register(address, "Kitchen")

        assertEquals(emptyList<DownloadedHistorySampleEntity>(), history.samples.value)
        assertEquals(1_000L, registered.rows.value.single().latestReadingEpochMillis)
        assertEquals(25.0, registered.rows.value.single().latestTemperatureCelsius!!, 0.0)
    }

    @Test
    fun `latest reading and original reception survive staleness and a new coordinator`() = runTest {
        val registered = FakeRegisteredDao()
        val history = FakeHistoryDao()
        val clock = FakeClock(1_000)
        val coordinator = SensorCoordinator(registered, history, FakeSettings(), clock, backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:06")
        coordinator.observe(observation(address, 1_000, BTHomeV2Parser(), "Sensor", "40 01 50 02 C4 09"))
        coordinator.register(address, "Cabin")
        clock.now = 301_000
        coordinator.onTimeTick()
        runCurrent()
        assertEquals(false, coordinator.sensors.value.single().isOnline)
        assertEquals(25.0, coordinator.sensors.value.single().latestReading!!.measurements.temperatureCelsius!!, 0.0)

        val restarted = SensorCoordinator(registered, history, FakeSettings(), clock, backgroundScope)
        runCurrent()
        val restored = restarted.sensors.value.single()
        assertEquals(null, restored.observation)
        assertEquals(1_000L, restored.latestReading!!.receptionEpochMillis)
        assertEquals(80, restored.latestReading!!.measurements.batteryPercent)
        assertEquals(emptyList<DownloadedHistorySampleEntity>(), history.samples.value)
    }

    @Test
    fun `invalid and older advertisements do not refresh or replace saved readings`() = runTest {
        val registered = FakeRegisteredDao()
        val clock = FakeClock(1_000)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:07")
        val parser = BTHomeV2Parser()
        val first = SensorCoordinator(registered, FakeHistoryDao(), FakeSettings(), clock, backgroundScope)
        first.observe(observation(address, 1_000, parser, "Sensor", "40 02 C4 09"))
        first.register(address, "Cabin")
        val restarted = SensorCoordinator(registered, FakeHistoryDao(), FakeSettings(), clock, backgroundScope)
        restarted.observe(observation(address, 500, parser, "Older", "40 02 00 00"))
        restarted.observe(observation(address, 2_000, parser, "Sensor", "40 02"))
        runCurrent()
        val sensor = restarted.sensors.value.single()
        assertEquals(SensorSupportStatus.Malformed, sensor.supportStatus)
        assertEquals(1_000L, sensor.latestReading!!.receptionEpochMillis)
        assertEquals(25.0, sensor.latestReading!!.measurements.temperatureCelsius!!, 0.0)
        assertEquals(2_000L, registered.rows.value.single().lastSeenEpochMillis)
    }

    @Test
    fun `a new valid packet replaces the saved snapshot rather than mixing ages`() = runTest {
        val registered = FakeRegisteredDao()
        val coordinator = SensorCoordinator(registered, FakeHistoryDao(), FakeSettings(), FakeClock(1_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:08")
        val parser = BTHomeV2Parser()
        coordinator.observe(observation(address, 1_000, parser, null, "40 02 C4 09"))
        coordinator.register(address, "Cabin")
        coordinator.observe(observation(address, 2_000, parser, null, "40 03 BF 13"))
        assertEquals(null, registered.rows.value.single().latestTemperatureCelsius)
        assertEquals(50.55, registered.rows.value.single().latestHumidityPercent!!, 0.0)
        assertEquals(2_000L, registered.rows.value.single().latestReadingEpochMillis)
    }

    @Test
    fun `offline registered sensors retain metadata but have no live reading`() = runTest {
        val registered = FakeRegisteredDao().also {
            it.rows.value = listOf(
                sensor("AA:BB:CC:DD:EE:03", "Stored").copy(
                    lastSeenEpochMillis = 0,
                    lastSupportStatus = SensorSupportStatus.Unsupported.name,
                ),
            )
        }
        val coordinator = SensorCoordinator(registered, FakeHistoryDao(), FakeSettings(), FakeClock(300_000), backgroundScope)
        runCurrent()

        val item = coordinator.sensors.value.single()
        assertEquals(false, item.isOnline)
        assertEquals(null, item.observation)
        assertEquals(SensorSupportStatus.Unsupported, item.supportStatus)
    }

    @Test
    fun `saving retention prunes only dated downloaded samples`() = runTest {
        val history = FakeHistoryDao()
        val coordinator = SensorCoordinator(FakeRegisteredDao(), history, FakeSettings(), FakeClock(1_000), backgroundScope)

        coordinator.saveSettings(AppSettings(retentionDurationMillis = 200))

        assertEquals(800L, history.lastRetentionCutoff)
        assertEquals(AppSettings(retentionDurationMillis = 200), coordinator.settings.first())
    }

    @Test
    fun `deleting registration exposes the retained observation as unregistered`() = runTest {
        val registered = FakeRegisteredDao()
        val coordinator = SensorCoordinator(registered, FakeHistoryDao(), FakeSettings(), FakeClock(1_000), backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:04")
        val live = observation(address, 1_000, BTHomeV2Parser(), "Sensor", "40 01 64")

        coordinator.observe(live)
        coordinator.register(address, "Registered")
        coordinator.deleteRegistration(address)
        runCurrent()

        assertEquals(false, coordinator.sensors.value.single().isRegistered)
        assertEquals(live, coordinator.sensors.value.single().observation)
    }

    @Test
    fun `tick removes observations at the five minute boundary`() = runTest {
        val clock = FakeClock(0)
        val coordinator = SensorCoordinator(FakeRegisteredDao(), FakeHistoryDao(), FakeSettings(), clock, backgroundScope)
        val address = SensorAddress.require("AA:BB:CC:DD:EE:05")

        coordinator.observe(observation(address, 0, BTHomeV2Parser(), null, "40 01 64"))
        clock.now = 300_000
        coordinator.onTimeTick()
        runCurrent()

        assertEquals(emptyList<io.github.mpolci.bthomeclient.domain.model.MergedSensor>(), coordinator.sensors.value)
    }

    private fun observation(
        address: SensorAddress,
        time: Long,
        parser: BTHomeV2Parser,
        name: String?,
        data: String,
    ) = ScanObservation(address, name, -50, time, parser.parse(0xFCD2, bytes(data)))

    private fun sensor(address: String, name: String) = RegisteredSensorEntity(
        address = address,
        displayName = name,
        room = null,
        imageUri = null,
        registrationOrder = 1,
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

    private class FakeClock(var now: Long) : Clock {
        override fun nowEpochMillis() = now
    }

    private class FakeSettings : SettingsStore {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun save(settings: AppSettings) {
            this.settings.value = settings
        }
    }

    private class FakeRegisteredDao : RegisteredSensorDao {
        val rows = MutableStateFlow<List<RegisteredSensorEntity>>(emptyList())
        override fun observeAll(): Flow<List<RegisteredSensorEntity>> = rows
        override suspend fun findByAddress(address: String) = rows.value.firstOrNull { it.address == address }
        override suspend fun maxRegistrationOrder() = rows.value.maxOfOrNull { it.registrationOrder } ?: 0
        override suspend fun insert(sensor: RegisteredSensorEntity) {
            rows.value += sensor
        }
        override suspend fun update(sensor: RegisteredSensorEntity) {
            rows.value = rows.value.map { if (it.address == sensor.address) sensor else it }
        }
        override suspend fun delete(sensor: RegisteredSensorEntity) {
            rows.value = rows.value.filterNot { it.address == sensor.address }
        }
    }

    private class FakeHistoryDao : DownloadedHistoryDao {
        val samples = MutableStateFlow<List<DownloadedHistorySampleEntity>>(emptyList())
        var state: HistorySyncStateEntity? = null
        var lastRetentionCutoff: Long? = null

        override suspend fun registrationId(address: String): String? = null
        override suspend fun updateHistorySupport(address: String, registrationId: String, support: String) = 0
        override fun observeSyncState(address: String): Flow<HistorySyncStateEntity?> = MutableStateFlow(state)
        override suspend fun syncState(address: String) = state?.takeIf { it.sensorAddress == address }
        override suspend fun samples(address: String) = samples.value.filter { it.sensorAddress == address }
        override suspend fun samplesAtSource(address: String, formatVersion: Int, generation: Long, slot: Int) = samples.value.filter { it.sensorAddress == address && it.sourceFormatVersion == formatVersion && it.sourceGeneration == generation && it.sourceSlot == slot }
        override fun observeSampleCount(address: String) = samples.map { rows -> rows.count { it.sensorAddress == address } }
        override fun observeSamples(address: String) = samples.map { rows -> rows.filter { it.sensorAddress == address } }
        override fun observeSamplesInRange(address: String, fromEpochMillis: Long, toEpochMillis: Long) = samples.map { rows ->
            rows.filter { it.sensorAddress == address && it.recordedEpochMillis in fromEpochMillis..toEpochMillis }
        }
        override suspend fun insertSamples(samples: List<DownloadedHistorySampleEntity>) {
            this.samples.value += samples
        }
        override suspend fun updateSample(sample: DownloadedHistorySampleEntity) {
            samples.value = samples.value.map { if (it.id == sample.id) sample else it }
        }
        override suspend fun insertSyncState(state: HistorySyncStateEntity) {
            this.state = state
        }
        override suspend fun deleteDatedSamplesOlderThan(beforeEpochMillis: Long) {
            lastRetentionCutoff = beforeEpochMillis
            samples.value = samples.value.filter { it.recordedEpochMillis == null || it.recordedEpochMillis >= beforeEpochMillis }
        }
    }

    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
