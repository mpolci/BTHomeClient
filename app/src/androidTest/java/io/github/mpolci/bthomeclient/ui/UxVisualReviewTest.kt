package io.github.mpolci.bthomeclient.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.bthome.BTHomeDeviceInformation
import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import io.github.mpolci.bthomeclient.bthome.BTHomeParseResult
import io.github.mpolci.bthomeclient.bthome.BTHomeProtocolMetadata
import io.github.mpolci.bthomeclient.bthome.DecodedBTHomeAdvertisement
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.data.repository.HistoryDownloadSummary
import io.github.mpolci.bthomeclient.data.repository.HistoryUpdateState
import io.github.mpolci.bthomeclient.domain.model.HistorySupport
import io.github.mpolci.bthomeclient.domain.model.LatestReading
import io.github.mpolci.bthomeclient.domain.model.MeasurementType
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.history.chart.HistoryChartMath
import io.github.mpolci.bthomeclient.history.chart.HistoryChartPoint
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
import io.github.mpolci.bthomeclient.testing.UxReviewActivity
import io.github.mpolci.bthomeclient.ui.detail.DetailScreen
import io.github.mpolci.bthomeclient.ui.detail.DetailState
import io.github.mpolci.bthomeclient.ui.history.HistoryMeasurement
import io.github.mpolci.bthomeclient.ui.history.HistoryPageState
import io.github.mpolci.bthomeclient.ui.history.HistoryScreen
import io.github.mpolci.bthomeclient.ui.history.HistorySensorData
import io.github.mpolci.bthomeclient.ui.main.MainScanState
import io.github.mpolci.bthomeclient.ui.main.MainScreen
import io.github.mpolci.bthomeclient.ui.main.ScanCapability
import io.github.mpolci.bthomeclient.ui.theme.BTHomeClientTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class UxVisualReviewTest {
    @get:Rule val compose = createAndroidComposeRule<UxReviewActivity>()

    @Before
    fun usePortraitOrientation() {
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
    }

    @Test
    fun captureLiveOverview() {
        val photo = syntheticPhotoUri()
        setReviewContent {
            MainScreen(
                state = MainScanState(
                    sensors = listOf(
                        registeredSensor(
                            address = "AA:BB:CC:DD:EE:01",
                            name = "Forward cabin",
                            room = "Above the wardrobe",
                            photo = photo,
                            readingTime = NOW - 90_000,
                        ),
                        registeredSensor(
                            address = "AA:BB:CC:DD:EE:02",
                            name = "Outdoors",
                            room = "Under the cockpit canopy",
                            readingTime = NOW - 7_200_000,
                            measurements = BTHomeMeasurements(61, 18.4, 78.0, 1_011.0, null),
                            online = false,
                        ),
                        registeredSensor(
                            address = "AA:BB:CC:DD:EE:03",
                            name = "Engine bay",
                            room = "Port side",
                            readingTime = NOW - 150_000,
                            measurements = BTHomeMeasurements(null, 31.7, null, null, null),
                        ),
                        nearbySensor(
                            address = "AA:BB:CC:DD:EE:04",
                            name = "SENE-7C2A",
                            measurements = BTHomeMeasurements(72, 22.8, 55.0, 1_014.0, null),
                        ),
                    ),
                    capability = ScanCapability.Ready,
                    isScanning = true,
                ),
                onPermissionRequest = {}, onOpenSettings = {}, onEnableBluetooth = {}, onRetry = {},
                onSettings = {}, onRegister = {}, onDetails = {}, nowEpochMillis = NOW, onHistory = {},
            )
        }
        capture("01-live-overview.png")
    }

    @Test
    fun captureRegisteredSensor() {
        val sensor = registeredSensor(
            address = "AA:BB:CC:DD:EE:11", name = "Forward cabin", room = "Above the wardrobe",
            photo = syntheticPhotoUri(), readingTime = NOW - 85_000,
        )
        setReviewContent { detail(DetailState(sensor = sensor, loading = false, displayName = "Forward cabin", room = "Above the wardrobe", imageUri = sensor.registeredSensor?.imageUri)) }
        capture("02-sensor-registered.png")
    }

    @Test
    fun captureUnregisteredSensor() {
        val sensor = nearbySensor("AA:BB:CC:DD:EE:12", "SENE-7C2A")
        setReviewContent { detail(DetailState(sensor = sensor, loading = false, displayName = "SENE-7C2A")) }
        capture("03-sensor-unregistered.png")
    }

    @Test
    fun captureTextDialog() {
        val sensor = registeredSensor("AA:BB:CC:DD:EE:13", "Forward cabin", "Above the wardrobe")
        setReviewContent { detail(DetailState(sensor = sensor, loading = false, displayName = "Forward cabin", room = "Above the wardrobe")) }
        compose.onNodeWithTag("detail-edit-name").performClick()
        capture("04-sensor-name-dialog.png")
    }

    @Test
    fun captureRemovalDialog() {
        val sensor = registeredSensor("AA:BB:CC:DD:EE:14", "Forward cabin", "Above the wardrobe")
        setReviewContent { detail(DetailState(sensor = sensor, loading = false, displayName = "Forward cabin", room = "Above the wardrobe")) }
        compose.onNodeWithTag("detail-delete").performScrollTo().performClick()
        capture("05-sensor-removal-dialog.png")
    }

    @Test
    fun captureSixMonthHistoryOverview() {
        val history = syntheticHistory()
        setReviewContent { historyScreen(history.overview) }
        capture("06-history-overview-six-months.png")
    }

    @Test
    fun captureLandscapeHistoryDetailWithComparison() {
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        val history = syntheticHistory()
        setReviewContent { historyScreen(history.detail) }
        capture("07-history-detail-landscape-comparison.png")
    }

    @Test
    fun captureNoDataHistoryError() {
        val sensor = registeredRecord("AA:BB:CC:DD:EE:31", "Aft cabin", "Lower berth")
        val data = HistorySensorData(sensor, emptyList(), emptyList(), null)
        val state = HistoryPageState(
            sensors = listOf(data),
            updates = mapOf(sensor.address to HistoryUpdateState.Failed(HistoryDownloadException.Connection("Synthetic unreachable sensor"))),
            measurement = HistoryMeasurement.Temperature,
            selectedAddress = sensor.address,
            viewport = HistoryViewport.fit(null),
            loading = false,
        )
        setReviewContent { historyScreen(state) }
        capture("08-history-no-data-error.png")
    }

    @Test
    fun captureLargeFontLive() {
        setReviewContent(fontScale = 1.6f) {
            MainScreen(
                MainScanState(
                    sensors = listOf(
                        registeredSensor("AA:BB:CC:DD:EE:41", "Forward cabin with a long local name", "Above the wardrobe", readingTime = NOW - 600_000),
                        nearbySensor("AA:BB:CC:DD:EE:42", "SENE-7C2A"),
                    ),
                    capability = ScanCapability.Ready,
                ),
                {}, {}, {}, {}, {}, {}, {}, NOW, {},
            )
        }
        capture("09-live-large-font-160.png")
    }

    @Test
    fun captureLargeFontSensor() {
        val sensor = registeredSensor("AA:BB:CC:DD:EE:43", "Forward cabin with a long local name", "Above the wardrobe", readingTime = NOW - 600_000)
        setReviewContent(fontScale = 1.6f) {
            detail(DetailState(sensor = sensor, loading = false, displayName = sensor.registeredSensor!!.displayName, room = sensor.registeredSensor.room.orEmpty()))
        }
        capture("10-sensor-large-font-160.png")
    }

    @Test
    fun captureLargeFontLandscapeHistoryDetail() {
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        val history = syntheticHistory()
        setReviewContent(fontScale = 1.6f) { historyScreen(history.detail) }
        capture("11-history-detail-landscape-large-font-160.png")
    }

    @Test
    fun captureLargeFontDenseLiveCard() {
        setReviewContent(fontScale = 1.6f) {
            MainScreen(
                MainScanState(
                    sensors = listOf(
                        registeredSensor(
                            "AA:BB:CC:DD:EE:44",
                            "Forward cabin with a long local name",
                            "Above the starboard wardrobe",
                            photo = syntheticPhotoUri(),
                            readingTime = NOW - 7_200_000,
                        ),
                    ),
                    capability = ScanCapability.Ready,
                ),
                {}, {}, {}, {}, {}, {}, {}, NOW, {},
            )
        }
        capture("12-live-dense-card-large-font-160.png")
    }

    @Composable
    private fun detail(state: DetailState) {
        DetailScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, nowEpochMillis = NOW)
    }

    @Composable
    private fun historyScreen(state: HistoryPageState) {
        HistoryScreen(state, {}, {}, {}, {}, {}, {}, {})
    }

    private fun setReviewContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BTHomeClientTheme(darkTheme = true, content = content)
            }
        }
    }

    private fun capture(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        SystemClock.sleep(800)
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ux-review"))
        val file = File(directory, fileName)
        FileOutputStream(file).use { output ->
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            screenshot.recycle()
        }
        println("UX_REVIEW_PNG=${file.absolutePath}")
    }

    private fun setOrientation(requested: Int, expected: Int) {
        compose.activity.requestedOrientation = requested
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == expected }
    }

    private fun syntheticPhotoUri(): String {
        val file = File(compose.activity.cacheDir, "ux-review-sensor-photo.png")
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 64, 53))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(196, 238, 215); strokeWidth = 10f }
        canvas.drawCircle(80f, 68f, 38f, paint)
        paint.color = Color.rgb(80, 132, 108)
        canvas.drawRect(28f, 112f, 132f, 136f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun registeredSensor(
        address: String,
        name: String,
        room: String? = null,
        photo: String? = null,
        readingTime: Long = NOW - 60_000,
        measurements: BTHomeMeasurements = DEFAULT_MEASUREMENTS,
        online: Boolean = true,
    ): MergedSensor {
        val record = registeredRecord(address, name, room, photo, LatestReading(measurements, readingTime))
        return MergedSensor(record.address, record, null, online, SensorSupportStatus.Supported)
    }

    private fun registeredRecord(
        address: String,
        name: String,
        room: String? = null,
        photo: String? = null,
        reading: LatestReading? = null,
    ) = RegisteredSensor(
        address = SensorAddress.require(address), displayName = name, room = room, imageUri = photo,
        registrationOrder = 1, advertisedName = "SENE-${address.takeLast(2)}", lastSeenEpochMillis = reading?.receptionEpochMillis,
        lastRssi = -57, lastBTHomeVersion = 2, lastEncrypted = false, lastTriggerBased = false,
        deviceType = 0x1234, firmwareVersion = "1.4.2", protocolDiagnostic = null,
        lastSupportStatus = SensorSupportStatus.Supported,
        supportedMeasurementTypes = setOf(MeasurementType.Temperature, MeasurementType.Humidity, MeasurementType.Pressure, MeasurementType.Battery),
        latestReading = reading, registrationId = "fixture-$address", historySupport = HistorySupport.Supported,
    )

    private fun nearbySensor(
        address: String,
        name: String,
        measurements: BTHomeMeasurements = DEFAULT_MEASUREMENTS,
    ): MergedSensor {
        val sensorAddress = SensorAddress.require(address)
        val observation = ScanObservation(
            sensorAddress, name, -61, NOW - 45_000,
            BTHomeParseResult.Decoded(
                DecodedBTHomeAdvertisement(
                    BTHomeDeviceInformation(2, encrypted = false, triggerBased = false),
                    measurements,
                    BTHomeProtocolMetadata(packetId = 17, deviceType = 0x1234, firmwareVersion = "1.4.2"),
                ),
            ),
        )
        return MergedSensor(sensorAddress, null, observation, true, SensorSupportStatus.Supported)
    }

    private fun syntheticHistory(): SyntheticHistory {
        val start = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()
        val coverage = HistoryTimeRange(start, end)
        val first = historySensor(
            registeredRecord("AA:BB:CC:DD:EE:21", "Forward cabin", "Above the wardrobe"),
            start,
            listOf(20.5, 21.0, 21.8, 22.3, 35.0, 22.1, 21.6, 20.9),
            dayOffsets = listOf(0, 12, 29, 47, 63, 121, 150, 181),
        )
        val second = historySensor(
            registeredRecord("AA:BB:CC:DD:EE:22", "Aft cabin", "Lower berth"),
            start,
            listOf(19.8, 20.2, 20.7, 21.4, 22.0, 21.2, 20.4),
            dayOffsets = listOf(0, 20, 42, 66, 109, 145, 181),
        )
        val third = historySensor(
            registeredRecord("AA:BB:CC:DD:EE:23", "Outdoors", "Cockpit canopy"),
            start,
            listOf(7.0, 12.5, 18.0, 25.0, 31.5, 24.0, 15.0),
            dayOffsets = listOf(0, 31, 60, 91, 121, 152, 181),
        )
        val viewport = HistoryViewport.fit(coverage)
        val complete = HistoryUpdateState.Complete(HistoryDownloadSummary(0, 0, 0, 0, false, false))
        val sensors = listOf(first, second, third)
        val overview = HistoryPageState(
            sensors = sensors, updates = sensors.associate { it.address to complete }, measurement = HistoryMeasurement.Temperature,
            viewport = viewport, coverage = coverage, loading = false,
        )
        return SyntheticHistory(
            overview,
            overview.copy(selectedAddress = first.address, comparisonAddress = second.address),
        )
    }

    private fun historySensor(
        sensor: RegisteredSensor,
        start: Long,
        values: List<Double>,
        dayOffsets: List<Int>,
    ): HistorySensorData {
        val samples = values.mapIndexed { index, value ->
            DownloadedHistorySampleEntity(
                id = index + sensor.registrationOrder * 100, sensorAddress = sensor.address.value,
                acquisitionSequence = index.toLong(), sourceFormatVersion = 1, sourceGeneration = 1,
                sourceSlot = index, rawPosition = ByteArray(16),
                recordedEpochMillis = start + dayOffsets[index] * DAY_MILLIS,
                timeQuality = "DIRECT_UTC", temperatureCelsius = value, humidityPercent = 55 + index,
                pressureHectopascals = 1_010.0 + index, batteryPercent = 94 - index,
            )
        }
        val points = samples.map { sample ->
            HistoryChartPoint(sample.id, requireNotNull(sample.recordedEpochMillis), sample.acquisitionSequence, sample.temperatureCelsius)
        }
        return HistorySensorData(
            sensor, samples, points,
            HistorySyncStateEntity(sensor.address.value, ByteArray(10), samples.size.toLong(), NOW - 60_000, false, false),
        )
    }

    private data class SyntheticHistory(val overview: HistoryPageState, val detail: HistoryPageState)

    private companion object {
        val NOW = Instant.parse("2026-09-07T12:00:00Z").toEpochMilli()
        const val DAY_MILLIS = 86_400_000L
        val DEFAULT_MEASUREMENTS = BTHomeMeasurements(86, 24.8, 52.0, 1_013.0, 740)
    }
}
