package io.github.mpolci.bthomeclient.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
    fun migrationFromOriginalVersion1RemovesLiveHistoryAndCreatesArchiveTables() {
        val fixture = createLegacyDatabase("original-v1-migration-test", LegacyLayout.OriginalVersion1)
        fixture.raw.use { legacy ->
            insertRegistration(legacy)
            legacy.execSQL(
                "INSERT INTO sensor_readings (sensorAddress, receptionEpochMillis) VALUES (?, ?)",
                arrayOf<Any?>(TEST_ADDRESS, 500L),
            )
        }
        val reopened = openMigrated(fixture.context, fixture.name)
        try {
            runBlocking {
                val migrated = reopened.registeredSensorDao().findByAddress(TEST_ADDRESS)!!
                assertEquals("Cabin", migrated.displayName)
                assertEquals(null, migrated.latestReadingEpochMillis)
                assertEquals(false, migrated.registrationId.isBlank())
                assertEquals(null, migrated.historySupport)
                assertEquals(false, tableExists(reopened.openHelper.readableDatabase, "sensor_readings"))
                assertEquals(0, reopened.downloadedHistoryDao().observeSampleCount(TEST_ADDRESS).first())
            }
        } finally { reopened.close(); fixture.context.deleteDatabase(fixture.name) }
    }

    @Test
    fun migrationFromRewrittenVersion1PreservesDownloadedArchive() {
        assertDownloadedArchiveSurvives("rewritten-v1-migration-test", LegacyLayout.RewrittenVersion1)
    }

    @Test
    fun migrationFromPreviousVersion2PreservesDownloadedArchive() {
        assertDownloadedArchiveSurvives("previous-v2-migration-test", LegacyLayout.PreviousVersion2)
    }

    @Test
    fun migrationFromSnapshotVersion2PreservesLatestReadingAndArchive() {
        val fixture = createLegacyDatabase("snapshot-v2-migration-test", LegacyLayout.SnapshotVersion2)
        fixture.raw.use { legacy ->
            insertRegistration(legacy, includeSnapshot = true)
            insertDownloadedArchive(legacy)
        }
        val reopened = openMigrated(fixture.context, fixture.name)
        try {
            runBlocking {
                val record = reopened.registeredSensorDao().findByAddress(TEST_ADDRESS)!!.toDomain()
                assertEquals(1_000L, record.latestReading!!.receptionEpochMillis)
                assertEquals(25.0, record.latestReading!!.measurements.temperatureCelsius!!, 0.0)
                assertEquals(1, reopened.downloadedHistoryDao().observeSampleCount(TEST_ADDRESS).first())
            }
        } finally { reopened.close(); fixture.context.deleteDatabase(fixture.name) }
    }

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

    private fun assertDownloadedArchiveSurvives(name: String, layout: LegacyLayout) {
        val fixture = createLegacyDatabase(name, layout)
        fixture.raw.use { legacy ->
            insertRegistration(legacy)
            insertDownloadedArchive(legacy)
        }
        val reopened = openMigrated(fixture.context, fixture.name)
        try {
            runBlocking {
                val registration = reopened.registeredSensorDao().findByAddress(TEST_ADDRESS)!!
                val samples = reopened.downloadedHistoryDao().samples(TEST_ADDRESS)
                assertEquals(false, registration.registrationId.isBlank())
                assertEquals(null, registration.historySupport)
                assertEquals(listOf(77L), samples.map { it.acquisitionSequence })
                assertEquals(78L, reopened.downloadedHistoryDao().syncState(TEST_ADDRESS)!!.nextAcquisitionSequence)
            }
        } finally { reopened.close(); fixture.context.deleteDatabase(fixture.name) }
    }

    private fun createLegacyDatabase(name: String, layout: LegacyLayout): LegacyFixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL(
            "CREATE TABLE registered_sensors (" +
                "address TEXT NOT NULL, displayName TEXT NOT NULL, room TEXT, imageUri TEXT, " +
                "registrationOrder INTEGER NOT NULL, advertisedName TEXT, lastSeenEpochMillis INTEGER, " +
                "lastRssi INTEGER, lastBTHomeVersion INTEGER, lastEncrypted INTEGER, lastTriggerBased INTEGER, " +
                "deviceType INTEGER, firmwareVersion TEXT, protocolDiagnostic TEXT, lastSupportStatus TEXT, " +
                "supportedMeasurementTypes TEXT" +
                (if (layout.hasSnapshot) ", latestReadingEpochMillis INTEGER, latestTemperatureCelsius REAL, " +
                    "latestHumidityPercent REAL, latestPressureHectopascals REAL, latestCarbonDioxidePpm INTEGER, " +
                    "latestBatteryPercent INTEGER" else "") +
                ", PRIMARY KEY(address))",
        )
        if (layout.hasDownloadedArchive) createDownloadedArchiveTables(raw) else createOriginalLiveHistoryTable(raw)
        raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        // Exact source schemas:
        // f5b8579:app/schemas/io.github.mpolci.bthomeclient.data.local.BTHomeDatabase/1.json (original v1),
        // 4ba2d5b:app/schemas/io.github.mpolci.bthomeclient.data.local.BTHomeDatabase/1.json (rewritten v1),
        // 7ba8484:app/schemas/io.github.mpolci.bthomeclient.data.local.BTHomeDatabase/2.json (previous v2), and
        // app/schemas/io.github.mpolci.bthomeclient.data.local.BTHomeDatabase/2.json (M0 snapshot v2).
        raw.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)", arrayOf(layout.identityHash))
        raw.version = layout.version
        return LegacyFixture(context, name, raw)
    }

    private fun createOriginalLiveHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE sensor_readings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "sensorAddress TEXT NOT NULL, receptionEpochMillis INTEGER NOT NULL, bthomeTimestampEpochMillis INTEGER, " +
                "temperatureCelsius REAL, humidityPercent REAL, pressureHectopascals REAL, carbonDioxidePpm INTEGER, " +
                "batteryPercent INTEGER, packetId INTEGER, FOREIGN KEY(sensorAddress) REFERENCES registered_sensors(address) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX index_sensor_readings_sensorAddress ON sensor_readings (sensorAddress)")
        db.execSQL("CREATE INDEX index_sensor_readings_sensorAddress_receptionEpochMillis ON sensor_readings (sensorAddress, receptionEpochMillis)")
    }

    private fun createDownloadedArchiveTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE downloaded_history_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "sensorAddress TEXT NOT NULL, acquisitionSequence INTEGER NOT NULL, sourceFormatVersion INTEGER NOT NULL, " +
                "sourceGeneration INTEGER NOT NULL, sourceSlot INTEGER NOT NULL, rawPosition BLOB NOT NULL, " +
                "recordedEpochMillis INTEGER, timeQuality TEXT NOT NULL, temperatureCelsius REAL NOT NULL, " +
                "humidityPercent INTEGER NOT NULL, pressureHectopascals REAL NOT NULL, batteryPercent INTEGER NOT NULL, " +
                "FOREIGN KEY(sensorAddress) REFERENCES registered_sensors(address) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX index_downloaded_history_samples_sensorAddress_recordedEpochMillis ON downloaded_history_samples (sensorAddress, recordedEpochMillis)")
        db.execSQL("CREATE INDEX index_downloaded_history_samples_sensorAddress_sourceFormatVersion_sourceGeneration_sourceSlot ON downloaded_history_samples (sensorAddress, sourceFormatVersion, sourceGeneration, sourceSlot)")
        db.execSQL("CREATE INDEX index_downloaded_history_samples_sensorAddress_acquisitionSequence ON downloaded_history_samples (sensorAddress, acquisitionSequence)")
        db.execSQL(
            "CREATE TABLE history_sync_states (sensorAddress TEXT NOT NULL, cursor BLOB NOT NULL, " +
                "nextAcquisitionSequence INTEGER NOT NULL, lastSuccessfulDownloadEpochMillis INTEGER NOT NULL, " +
                "syncPending INTEGER NOT NULL, historyTruncated INTEGER NOT NULL, PRIMARY KEY(sensorAddress), " +
                "FOREIGN KEY(sensorAddress) REFERENCES registered_sensors(address) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
    }

    private fun insertRegistration(db: SQLiteDatabase, includeSnapshot: Boolean = false) {
        val columns = if (includeSnapshot) {
            "address, displayName, registrationOrder, latestReadingEpochMillis, latestTemperatureCelsius"
        } else {
            "address, displayName, registrationOrder"
        }
        val placeholders = if (includeSnapshot) "?, ?, ?, ?, ?" else "?, ?, ?"
        val values: Array<Any?> = if (includeSnapshot) {
            arrayOf(TEST_ADDRESS, "Cabin", 1L, 1_000L, 25.0)
        } else {
            arrayOf(TEST_ADDRESS, "Cabin", 1L)
        }
        db.execSQL("INSERT INTO registered_sensors ($columns) VALUES ($placeholders)", values)
    }

    private fun insertDownloadedArchive(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO downloaded_history_samples (sensorAddress, acquisitionSequence, sourceFormatVersion, " +
                "sourceGeneration, sourceSlot, rawPosition, recordedEpochMillis, timeQuality, temperatureCelsius, " +
                "humidityPercent, pressureHectopascals, batteryPercent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(TEST_ADDRESS, 77L, 1, 3L, 4, ByteArray(16) { it.toByte() }, 1_000L, "DIRECT_UTC", 25.0, 50, 1_013.0, 86),
        )
        db.execSQL(
            "INSERT INTO history_sync_states (sensorAddress, cursor, nextAcquisitionSequence, lastSuccessfulDownloadEpochMillis, " +
                "syncPending, historyTruncated) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(TEST_ADDRESS, ByteArray(10), 78L, 2_000L, 0, 0),
        )
    }

    private fun openMigrated(context: Context, name: String) =
        Room.databaseBuilder(context, BTHomeDatabase::class.java, name)
            .addMigrations(BTHomeDatabase.MIGRATION_1_3, BTHomeDatabase.MIGRATION_2_3)
            .build()

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, name: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private data class LegacyFixture(val context: Context, val name: String, val raw: SQLiteDatabase)

    private enum class LegacyLayout(
        val version: Int,
        val identityHash: String,
        val hasDownloadedArchive: Boolean,
        val hasSnapshot: Boolean,
    ) {
        OriginalVersion1(1, "3814e9864df0eb7bf3a913ac4a13dc4b", false, false),
        RewrittenVersion1(1, "8c925439964a48f14195aa14c9e785c3", true, false),
        PreviousVersion2(2, "8c925439964a48f14195aa14c9e785c3", true, false),
        SnapshotVersion2(2, "f366655393bb5fda691396ced0e17ba2", true, true),
    }

    private companion object {
        const val TEST_ADDRESS = "AA:BB:CC:DD:EE:10"
    }
}
