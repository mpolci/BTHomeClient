package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.data.repository.HistoryUpdateState
import io.github.mpolci.bthomeclient.domain.model.*
import io.github.mpolci.bthomeclient.history.chart.HistoryChartPoint
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
import io.github.mpolci.bthomeclient.ui.main.ScanCapability
import io.github.mpolci.bthomeclient.ui.theme.BTHomeClientTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun overviewOpensSensorAndExposesImmediateMeasurementAndTimeControls() {
        val sensor = sensor(1)
        var opened: SensorAddress? = null
        var measurement: HistoryMeasurement? = null
        var viewport: HistoryViewport? = null
        var resets = 0
        compose.setContent {
            BTHomeClientTheme {
                HistoryScreen(page(sensor), {}, {}, { opened = it }, { measurement = it }, { viewport = it }, {}, { resets++ })
            }
        }
        compose.onNodeWithTag("history-measurement").performClick()
        compose.onNodeWithText("Humidity").performClick()
        assertEquals(HistoryMeasurement.Humidity, measurement)
        performTimelineAction("Later")
        assertNotNull(viewport)
        compose.onNodeWithText("Zoom in").performClick()
        assertEquals(5_000L, viewport!!.spanMillis)
        compose.onNodeWithText("All history").performClick()
        assertEquals(1, resets)
        compose.onNodeWithTag("history-sensor-${sensor.address.value}").performClick()
        assertEquals(sensor.address, opened)
        compose.onNodeWithText("Download history").assertDoesNotExist()
    }

    @Test
    fun timelineControlsRemainUsableAtLargeFontOnANarrowLayout() {
        var pans = 0
        var resets = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    HistoryScreen(page(sensor(1)), {}, {}, {}, {}, { pans++ }, {}, { resets++ })
                }
            }
        }

        for (label in listOf("Zoom in", "Zoom out")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
        }
        compose.onNodeWithText("All history").performScrollTo().assertIsDisplayed().performClick()
        performTimelineAction("Earlier")
        performTimelineAction("Later")
        assertEquals(4, pans)
        assertEquals(1, resets)
    }

    @Test
    fun unknownTransferLengthIsIndeterminateUntilATotalIsAvailable() {
        val sensor = sensor(1)
        val state = mutableStateOf(page(sensor).copy(updates = mapOf(sensor.address to HistoryUpdateState.Downloading(20, 0))))
        compose.setContent { HistoryScreen(state.value, {}, {}, {}, {}, {}, {}, {}) }

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        compose.onNodeWithText("Updating history · 20 records received").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(updates = mapOf(sensor.address to HistoryUpdateState.Downloading(20, -1))) }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(updates = mapOf(sensor.address to HistoryUpdateState.Downloading(20, 50))) }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.4f, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun cachedChartRemainsVisibleOnFailureAndRetryIsContextual() {
        val sensor = sensor(1)
        var retried: SensorAddress? = null
        val state = page(sensor).copy(updates = mapOf(sensor.address to HistoryUpdateState.Failed(HistoryDownloadException.Connection("Out of range"))))
        compose.setContent { HistoryScreen(state, {}, { retried = it }, {}, {}, {}, {}, {}) }

        compose.onNodeWithText("Sensor could not be reached.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("history_chart", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        assertEquals(sensor.address, retried)
    }

    @Test
    fun connectingEmptyAndUndatedArchivesHaveDistinctExplanations() {
        val sensor = sensor(1, emptyList())
        val state = mutableStateOf(page(sensor).copy(updates = mapOf(sensor.address to HistoryUpdateState.Connecting)))
        compose.setContent { HistoryScreen(state.value, {}, {}, {}, {}, {}, {}, {}) }

        compose.onNodeWithText("Connecting to sensor…").assertIsDisplayed()
        compose.onNodeWithText("No local history yet.").assertIsDisplayed()
        compose.runOnIdle { state.value = page(sensor.copy(sync = sync(sensor.address))) }
        compose.onNodeWithText("No saved samples are available after the last successful update.").assertIsDisplayed()
        compose.runOnIdle { state.value = page(sensor(1, listOf(null))) }
        compose.onNodeWithText("Saved samples have no date and cannot be plotted on a timeline.").assertIsDisplayed()
        compose.onNodeWithText("1 undated samples are not plotted.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("history_chart").assertDoesNotExist()
    }

    @Test
    fun prerequisiteActionsDoNotHideCachedData() {
        val capability = mutableStateOf(ScanCapability.PermissionMissing)
        var resolves = 0
        compose.setContent {
            HistoryScreen(page(sensor(1)), {}, {}, {}, {}, {}, {}, {}, capability.value, { resolves++ })
        }
        compose.onNodeWithText("Allow").performScrollTo().performClick()
        compose.onNodeWithTag("history_chart", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { capability.value = ScanCapability.PermissionPermanentlyDenied }
        compose.onNodeWithText("Open settings").performScrollTo().performClick()
        compose.runOnIdle { capability.value = ScanCapability.BluetoothDisabled }
        compose.onNodeWithText("Enable Bluetooth").performScrollTo().performClick()
        assertEquals(3, resolves)
        compose.onNodeWithTag("history_chart", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { capability.value = ScanCapability.BleUnsupported }
        compose.onNodeWithText("Bluetooth Low Energy is unavailable.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailComparisonShowsEachActualRecordedTimestampAndStoredQuantities() {
        val cabin = sensor(1, listOf(1_000L))
        val outside = sensor(2, listOf(1_800L))
        val state = mutableStateOf(page(cabin, outside).copy(selectedAddress = cabin.address))
        var comparison: SensorAddress? = null
        compose.setContent {
            HistoryScreen(state.value, {}, {}, {}, {}, {}, {
                comparison = it
                state.value = state.value.copy(comparisonAddress = it)
            }, {})
        }
        compose.onNodeWithContentDescription("Compare sensors").performClick()
        compose.onNodeWithText(outside.sensor.displayName).performClick()
        assertEquals(outside.address, comparison)
        compose.onNodeWithText("Recorded ${recordedTime(1_000L)}", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Recorded ${recordedTime(1_800L)}", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("${NumberFormat.getNumberInstance().format(20.5)} °C · 62% · ${NumberFormat.getNumberInstance().apply { isGroupingUsed = false }.format(1001.3)} hPa · 85% battery").assertCountEquals(2)
        compose.onNodeWithText("Dashed · ${outside.sensor.displayName}", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("history-info").performClick()
        compose.onNodeWithText("Local time · ${ZoneId.systemDefault().id}").assertIsDisplayed()
    }

    @Test
    fun detailChartFillsTheAvailableSurfaceBehindItsReadoutOverlay() {
        val sensor = sensor(1)
        compose.setContent { HistoryScreen(page(sensor).copy(selectedAddress = sensor.address), {}, {}, {}, {}, {}, {}, {}) }

        val detail = compose.onNodeWithTag("history-detail").fetchSemanticsNode().boundsInRoot
        val chart = compose.onNodeWithTag("history_chart").fetchSemanticsNode().boundsInRoot
        val readout = compose.onNodeWithTag("history-detail-readouts").fetchSemanticsNode().boundsInRoot
        assertEquals(detail.height, chart.height, 1f)
        assertEquals(detail.width, chart.width, 1f)
        assertTrue(readout.top > chart.top && readout.bottom < chart.bottom)
        assertTrue("The readout should leave the plot visible", readout.height <= detail.height * .24f)
        compose.onNodeWithText("Recorded ${recordedTime(0L)}").assertIsDisplayed()
    }

    @Test
    fun detailCoverageNotesExpandInInformationWithoutCoveringThePlot() {
        val initial = sensor(1, listOf(0L, null))
        val data = initial.copy(sync = sync(initial.address).copy(historyTruncated = true, syncPending = true))
        compose.setContent { HistoryScreen(page(data).copy(selectedAddress = data.address), {}, {}, {}, {}, {}, {}, {}) }

        compose.onNodeWithText("Older sensor records were overwritten", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("history-info").performClick()
        compose.onNodeWithText("History information").assertIsDisplayed()
        compose.onNodeWithText("Older sensor records were overwritten", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 undated samples", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sensor time synchronization is pending", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("History information").assertDoesNotExist()
    }

    @Test
    fun recordedValuesUseLocalDecimalFormattingWithoutLosingPrecision() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            val initial = sensor(1)
            val data = initial.copy(
                samples = initial.samples.map { it.copy(temperatureCelsius = 20.1234567, pressureHectopascals = 1001.1234567) },
                points = initial.points.map { it.copy(value = 20.1234567) },
            )
            compose.setContent { HistoryScreen(page(data).copy(selectedAddress = data.address), {}, {}, {}, {}, {}, {}, {}) }
            compose.onNodeWithText("20,1234567 °C · 62% · 1001,1234567 hPa · 85% battery").assertIsDisplayed()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun overviewWithoutDatedHistoryDoesNotShowEpochTimelineControls() {
        val sensor = sensor(1, emptyList())
        compose.setContent { HistoryScreen(page(sensor).copy(coverage = null), {}, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("history-timeline").assertDoesNotExist()
        compose.onNodeWithTag("history-time-position").assertDoesNotExist()
        compose.onNodeWithText("No local history yet.").assertIsDisplayed()
    }

    @Test
    fun emptyDetailShowsOneStateAndRetryWithoutSyntheticAxesOrReadout() {
        val sensor = sensor(1, emptyList())
        var retried: SensorAddress? = null
        compose.setContent {
            HistoryScreen(page(sensor).copy(selectedAddress = sensor.address,
                updates = mapOf(sensor.address to HistoryUpdateState.Failed(HistoryDownloadException.Connection("Unavailable")))),
                {}, { retried = it }, {}, {}, {}, {}, {})
        }
        compose.onNodeWithTag("history_chart", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("history-detail-readouts").assertDoesNotExist()
        compose.onAllNodesWithText("No local history", substring = true).assertCountEquals(1)
        compose.onNodeWithText("Retry").performClick()
        assertEquals(sensor.address, retried)
    }

    @Test
    fun largeFontDetailKeepsOneHeaderRowAndOffersFullControlsAndValuesInInfo() {
        val sensor = sensor(1)
        var resets = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                HistoryScreen(page(sensor).copy(selectedAddress = sensor.address), {}, {}, {}, {}, {}, {}, { resets++ })
            }
        }
        compose.onNodeWithTag("history-measurement").assertDoesNotExist()
        compose.onNodeWithText("All history").assertDoesNotExist()
        compose.onNodeWithTag("history-info").performClick()
        compose.onNodeWithTag("history-measurement").assertIsDisplayed()
        compose.onNodeWithText("All history").performClick()
        assertEquals(1, resets)
        compose.onNodeWithText("85% battery", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun comparisonMenuOffersOnlyDatedArchivesIncludingCachedUnsupportedSensors() {
        val primary = sensor(1)
        val empty = sensor(2, emptyList())
        val cached = sensor(3).let { it.copy(sensor = it.sensor.copy(historySupport = HistorySupport.Unsupported)) }
        var selected: SensorAddress? = null
        compose.setContent {
            HistoryScreen(page(primary, empty, cached).copy(selectedAddress = primary.address), {}, {}, {}, {}, {}, { selected = it }, {})
        }

        compose.onNodeWithContentDescription("Compare sensors").performClick()
        compose.onNodeWithText(empty.sensor.displayName).assertDoesNotExist()
        compose.onNodeWithText(cached.sensor.displayName).performClick()
        assertEquals(cached.address, selected)
    }

    @Test
    fun unsupportedAndDeletedRegistrationsHaveClearStates() {
        val initial = sensor(1, emptyList())
        val unsupported = initial.copy(sensor = initial.sensor.copy(historySupport = HistorySupport.Unsupported))
        val state = mutableStateOf(page(unsupported))
        var backs = 0
        compose.setContent { HistoryScreen(state.value, { backs++ }, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("This sensor does not support stored history.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(sensorMissing = true, selectedAddress = initial.address) }
        compose.onNodeWithText("This sensor is no longer registered.").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun coverageLimitationsRemainVisibleBesideCachedChart() {
        val initial = sensor(1, listOf(0L, null))
        val data = initial.copy(sync = sync(initial.address).copy(syncPending = true, historyTruncated = true))
        compose.setContent { HistoryScreen(page(data), {}, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Older sensor records were overwritten; coverage may be incomplete.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sensor time synchronization is pending", substring = true).assertIsDisplayed()
    }

    @Test
    fun overviewScrollPositionSurvivesDetailAndBack() {
        val sensors = (1..8).map { sensor(it) }
        val target = sensors[5]
        val state = mutableStateOf(page(*sensors.toTypedArray()))
        compose.setContent {
            HistoryScreen(state.value, { state.value = state.value.copy(selectedAddress = null) }, {},
                { state.value = state.value.copy(selectedAddress = it) }, {}, {}, {}, {})
        }
        val tag = "history-sensor-${target.address.value}"
        compose.onNodeWithTag("history-overview").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("history-overview").assertDoesNotExist()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun performTimelineAction(label: String) {
        val actions = compose.onNodeWithTag("history-time-position").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.single { it.label == label }.action()) }
    }

    private fun page(vararg sensors: HistorySensorData) = HistoryPageState(
        sensors = sensors.toList(), coverage = HistoryTimeRange(0L, 10_000L),
        viewport = HistoryViewport(5_000L, 10_000L), loading = false,
    )

    private fun sensor(index: Int, times: List<Long?> = listOf(0L, 10_000L)): HistorySensorData {
        val address = SensorAddress.require("AA:BB:CC:DD:EE:${index.toString().padStart(2, '0')}")
        val registered = RegisteredSensor(
            address, "Sensor $index", null, null, index.toLong(), null, null, null, null, null,
            null, null, null, null, null, emptySet(), historySupport = HistorySupport.Supported,
        )
        val samples = times.mapIndexed { sequence, time -> DownloadedHistorySampleEntity(
            id = sequence + 1L, sensorAddress = address.value, acquisitionSequence = sequence.toLong(),
            sourceFormatVersion = 1, sourceGeneration = 1, sourceSlot = sequence, rawPosition = ByteArray(16),
            recordedEpochMillis = time, timeQuality = if (time == null) "UNAVAILABLE" else "DIRECT_UTC",
            temperatureCelsius = 20.5, humidityPercent = 62, pressureHectopascals = 1001.3, batteryPercent = 85,
        ) }
        val points = samples.mapNotNull { sample -> sample.recordedEpochMillis?.let {
            HistoryChartPoint(sample.id, it, sample.acquisitionSequence, sample.temperatureCelsius)
        } }
        return HistorySensorData(registered, samples, points, null)
    }

    private fun sync(address: SensorAddress) = HistorySyncStateEntity(address.value, ByteArray(10), 1L, 20_000L, false, false)
    private fun recordedTime(time: Long) = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss.SSS z", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
}
