package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.data.repository.HistoryUpdateState
import io.github.mpolci.bthomeclient.domain.model.HistorySupport
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.history.chart.HistoryChartMath
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
import io.github.mpolci.bthomeclient.ui.main.ScanCapability
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    state: HistoryPageState,
    onBack: () -> Unit,
    onRetry: (SensorAddress?) -> Unit,
    onSensor: (SensorAddress) -> Unit,
    onMeasurement: (HistoryMeasurement) -> Unit,
    onViewport: (HistoryViewport) -> Unit,
    onComparison: (SensorAddress?) -> Unit,
    onFullHistory: () -> Unit,
    capability: ScanCapability = ScanCapability.Ready,
    onResolvePrerequisite: () -> Unit = {},
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val zone = ZoneId.systemDefault()
    if (state.selectedAddress != null && state.selected != null && !state.sensorMissing) {
        HistoryDetail(state, onBack, onRetry, onMeasurement, onViewport, onComparison, onFullHistory, capability, onResolvePrerequisite)
        return
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    state.selected?.sensor?.displayName ?: "History",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!state.sensorMissing) {
                    MeasurementMenu(state.measurement, onMeasurement)
                    if (state.selectedAddress != null) ComparisonMenu(state, onComparison)
                }
            }
            when {
                state.loading && state.sensors.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading saved history…")
                }
                state.sensorMissing -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This sensor is no longer registered.")
                    Text("Return to your sensors to choose another archive.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.selectedAddress == null -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("history-overview"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        HistoryPrerequisite(capability, onResolvePrerequisite)
                        TimelineControls(state, onViewport, onFullHistory)
                        Text("Local time · ${zone.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.sensors.isEmpty()) item {
                        Text("No registered sensors with history.", modifier = Modifier.padding(vertical = 24.dp))
                        Text("Register a sensor from Live to check whether it stores history.")
                    }
                    items(state.sensors, key = { it.address.value }) { data ->
                        Card(onClick = { onSensor(data.address) }, modifier = Modifier.fillMaxWidth().testTag("history-sensor-${data.address.value}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(data.sensor.displayName, style = MaterialTheme.typography.titleMedium)
                                data.sensor.room?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HistoryUpdate(data, state.updates[data.address], capability, { onRetry(data.address) }, onResolvePrerequisite)
                                if (data.points.isNotEmpty()) {
                                    HistoryChart(data.points, state.viewport, state.coverage, state.measurement.unit, Modifier.height(180.dp))
                                    val visible = data.points.filter { state.viewport.fractionAt(it.epochMillis) in 0.0..1.0 }
                                    if (visible.isNotEmpty()) Text(
                                        "Observed ${formatHistoryValue(visible.minOf { it.value })}–${formatHistoryValue(visible.maxOf { it.value })} ${state.measurement.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                    ) else Text("No recorded samples in this window.", style = MaterialTheme.typography.bodySmall)
                                } else HistoryEmpty(data, state.updates[data.address])
                                HistoryCoverage(data)
                            }
                        }
                    }
                }
                else -> Text("No saved history is available.", Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun HistoryDetail(
    state: HistoryPageState,
    onBack: () -> Unit,
    onRetry: (SensorAddress?) -> Unit,
    onMeasurement: (HistoryMeasurement) -> Unit,
    onViewport: (HistoryViewport) -> Unit,
    onComparison: (SensorAddress?) -> Unit,
    onFullHistory: () -> Unit,
    capability: ScanCapability,
    onResolvePrerequisite: () -> Unit,
) {
    val selected = state.selected ?: return
    var showInformation by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var readoutHeight by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("history-detail")) {
            val narrow = maxWidth < 560.dp
            val compact = narrow || density.fontScale > 1.4f
            val largeFont = density.fontScale > 1.4f
            val hasPoints = selected.points.isNotEmpty()
            val topInset = with(density) { headerHeight.toDp() } + 4.dp
            val bottomInset = with(density) { readoutHeight.toDp() } + 8.dp
            if (hasPoints) {
                HistoryChart(
                    selected.points, state.viewport, state.coverage, state.measurement.unit,
                    Modifier.fillMaxSize(), interactive = true,
                    comparisonPoints = state.comparison?.points.orEmpty(), onViewportChange = onViewport,
                    plotPadding = PaddingValues(top = topInset, bottom = bottomInset),
                )
            } else {
                Column(Modifier.fillMaxSize().padding(top = topInset).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    val update = state.updates[selected.address]
                    HistoryPrerequisite(capability, onResolvePrerequisite)
                    when {
                        update is HistoryUpdateState.Failed && update.error !is HistoryDownloadException.Unsupported ->
                            HistoryUpdate(selected, update, capability, { onRetry(selected.address) }, onResolvePrerequisite)
                        update == HistoryUpdateState.Queued || update == HistoryUpdateState.Connecting ||
                            update == HistoryUpdateState.Discovering || update is HistoryUpdateState.Downloading ->
                            HistoryUpdate(selected, update, capability, { onRetry(selected.address) }, onResolvePrerequisite)
                        else -> HistoryEmpty(selected, update)
                    }
                    if (selected.undatedCount > 0) HistoryCoverage(selected)
                }
            }
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    .heightIn(max = maxHeight * .3f).onSizeChanged { headerHeight = it.height }
                    .verticalScroll(rememberScrollState()).testTag("history-detail-header"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("Back", maxLines = 1) }
                        Text(selected.sensor.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!compact) {
                            MeasurementMenu(state.measurement, onMeasurement)
                            TextButton(onClick = onFullHistory) { Text("All history", maxLines = 1) }
                        }
                        TextButton(onClick = { showInformation = true }, modifier = Modifier.testTag("history-info")) { Text("Info", maxLines = 1) }
                        if (hasPoints) ComparisonMenu(state, onComparison)
                    }
                }
                val update = state.updates[selected.address]
                val activeUpdate = update != null && update != HistoryUpdateState.Idle && update !is HistoryUpdateState.Complete
                if (hasPoints && (capability != ScanCapability.Ready || activeUpdate)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            HistoryPrerequisite(capability, onResolvePrerequisite)
                            HistoryUpdate(selected, update, capability, { onRetry(selected.address) }, onResolvePrerequisite)
                        }
                    }
                }
            }
            if (hasPoints) Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                    .widthIn(max = if (state.comparison == null) 520.dp else maxWidth).fillMaxWidth()
                    .heightIn(max = maxHeight * .22f).onSizeChanged { readoutHeight = it.height }.testTag("history-detail-readouts"),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (narrow && !largeFont) {
                        SampleReadout(selected, state.viewport, Modifier.fillMaxWidth(), showName = state.comparison != null)
                        state.comparison?.let { SampleReadout(it, state.viewport, Modifier.fillMaxWidth(), comparison = true) }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SampleReadout(selected, state.viewport, Modifier.weight(1f), showName = state.comparison != null,
                            selectedMeasurement = state.measurement.takeIf { largeFont })
                        state.comparison?.let { SampleReadout(it, state.viewport, Modifier.weight(1f), comparison = true,
                            selectedMeasurement = state.measurement.takeIf { largeFont }) }
                    }
                }
            }
        }
    }
    if (showInformation) AlertDialog(
        onDismissRequest = { showInformation = false },
        title = { Text("History information") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (density.fontScale > 1.4f) Column {
                    MeasurementMenu(state.measurement, onMeasurement)
                    TextButton(onClick = onFullHistory) { Text("All history") }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MeasurementMenu(state.measurement, onMeasurement)
                    TextButton(onClick = onFullHistory) { Text("All history") }
                }
                Text("Local time · ${ZoneId.systemDefault().id}")
                for (data in listOfNotNull(selected, state.comparison)) {
                    SampleReadout(data, state.viewport, Modifier.fillMaxWidth(), comparison = data.address == state.comparisonAddress)
                    data.sync?.let { Text("Last successful update ${formatHistoryTime(it.lastSuccessfulDownloadEpochMillis, seconds = true)}") }
                    HistoryCoverage(data)
                    if (data.points.isEmpty()) HistoryEmpty(data, state.updates[data.address])
                }
            }
        },
        confirmButton = { TextButton(onClick = { showInformation = false }) { Text("Close") } },
    )
}

@Composable
private fun MeasurementMenu(measurement: HistoryMeasurement, onMeasurement: (HistoryMeasurement) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("history-measurement")) {
            Text(measurement.label, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HistoryMeasurement.entries.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label) }, onClick = { expanded = false; onMeasurement(choice) })
            }
        }
    }
}

@Composable
private fun ComparisonMenu(state: HistoryPageState, onComparison: (SensorAddress?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = "Compare sensors" }) {
            val color = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(24.dp)) {
                drawLine(color, Offset(0f, size.height * .3f), Offset(size.width, size.height * .3f), 2.dp.toPx())
                drawLine(color, Offset(0f, size.height * .7f), Offset(size.width, size.height * .7f), 2.dp.toPx())
                drawLine(color, Offset(size.width * .7f, 0f), Offset(size.width, size.height * .3f), 2.dp.toPx())
                drawLine(color, Offset(0f, size.height * .7f), Offset(size.width * .3f, size.height), 2.dp.toPx())
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No comparison") }, onClick = { expanded = false; onComparison(null) })
            val candidates = state.sensors.filter { it.address != state.selectedAddress && it.points.isNotEmpty() }
            candidates.forEach { data ->
                DropdownMenuItem(text = { Text(data.sensor.displayName) }, onClick = { expanded = false; onComparison(data.address) })
            }
            if (candidates.isEmpty()) DropdownMenuItem(text = { Text("No other sensors with dated history") }, onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun TimelineControls(state: HistoryPageState, onViewport: (HistoryViewport) -> Unit, onFullHistory: () -> Unit) {
    val coverage = state.coverage ?: return
    val fitted = HistoryViewport.fit(coverage)
    Column(Modifier.fillMaxWidth().testTag("history-timeline")) {
        Text("${formatHistoryTime(state.viewport.startMillis, seconds = state.viewport.spanMillis < 3_600_000L)} — ${formatHistoryTime(state.viewport.endMillis, seconds = state.viewport.spanMillis < 3_600_000L)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = fitted.fractionAt(state.viewport.centerMillis).toFloat().coerceIn(0f, 1f),
            onValueChange = { fraction ->
                val center = fitted.pan(((fraction - 0.5) * fitted.spanMillis).toLong(), coverage).centerMillis
                onViewport(state.viewport.copy(centerMillis = center).clamp(coverage))
            },
            enabled = coverage.startMillis < coverage.endMillis,
            modifier = Modifier.testTag("history-time-position").semantics {
                contentDescription = "Timeline position"
                customActions = listOf(
                    CustomAccessibilityAction("Earlier") { onViewport(state.viewport.pan(-state.viewport.spanMillis / 4, coverage)); true },
                    CustomAccessibilityAction("Later") { onViewport(state.viewport.pan(state.viewport.spanMillis / 4, coverage)); true },
                )
            },
        )
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { onViewport(state.viewport.zoom(2.0, coverage)) }, modifier = Modifier.weight(1f)) { Text("Zoom in") }
            TextButton(onClick = { onViewport(state.viewport.zoom(0.5, coverage)) }, modifier = Modifier.weight(1f)) { Text("Zoom out") }
            TextButton(onClick = onFullHistory, modifier = Modifier.weight(1f)) { Text("All history") }
        }
    }
}

@Composable
private fun HistoryPrerequisite(capability: ScanCapability, onResolve: () -> Unit, modifier: Modifier = Modifier) {
    val description = when (capability) {
        ScanCapability.Ready -> return
        ScanCapability.Checking -> "Checking Bluetooth access…"
        ScanCapability.BleUnsupported -> "Bluetooth Low Energy is unavailable. Saved history remains readable."
        ScanCapability.PermissionMissing, ScanCapability.PermissionPermanentlyDenied -> "Nearby devices permission is needed to update history."
        ScanCapability.BluetoothDisabled -> "Bluetooth is off. Saved history remains readable."
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(description, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        val action = when (capability) {
            ScanCapability.PermissionMissing -> "Allow"
            ScanCapability.PermissionPermanentlyDenied -> "Open settings"
            ScanCapability.BluetoothDisabled -> "Enable Bluetooth"
            else -> null
        }
        if (action != null) TextButton(onClick = onResolve) { Text(action) }
    }
}

@Composable
private fun HistoryUpdate(
    data: HistorySensorData,
    update: HistoryUpdateState?,
    capability: ScanCapability,
    onRetry: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedStatus = if (data.samples.isNotEmpty()) "Saved history is available." else "No local history is available yet."
    val text = when (update) {
        HistoryUpdateState.Queued -> "Waiting to update…"
        HistoryUpdateState.Connecting -> "Connecting to sensor…"
        HistoryUpdateState.Discovering -> "Checking sensor history support…"
        is HistoryUpdateState.Downloading -> if (update.total > 0) "Updating history · ${update.received} / ${update.total} records"
            else "Updating history · ${update.received} records received"
        is HistoryUpdateState.Complete -> "History is up to date"
        is HistoryUpdateState.Failed -> when (update.error) {
            is HistoryDownloadException.PermissionLost -> "Permission was lost. $savedStatus"
            is HistoryDownloadException.BluetoothDisabled -> "Bluetooth was turned off. $savedStatus"
            is HistoryDownloadException.Unsupported -> "This sensor does not support stored history."
            is HistoryDownloadException.Timeout, is HistoryDownloadException.Connection -> "Sensor could not be reached. $savedStatus"
            is HistoryDownloadException.Protocol -> "History transfer was interrupted. $savedStatus"
            is HistoryDownloadException.Remote -> "Sensor could not complete the update. $savedStatus"
            else -> "History update failed. $savedStatus"
        }
        else -> data.sync?.let { "Last update ${formatHistoryTime(it.lastSuccessfulDownloadEpochMillis)}" }
    }
    if (text == null) return
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (update is HistoryUpdateState.Failed && update.error !is HistoryDownloadException.Unsupported && capability == ScanCapability.Ready) {
                val action = when (update.error) {
                    is HistoryDownloadException.PermissionLost -> "Allow"
                    is HistoryDownloadException.BluetoothDisabled -> "Enable Bluetooth"
                    else -> "Retry"
                }
                TextButton(onClick = if (action == "Retry") onRetry else onResolve) { Text(action) }
            }
        }
        if (update is HistoryUpdateState.Downloading && update.total > 0) {
            LinearProgressIndicator(
                progress = { (update.received.toFloat() / update.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (update is HistoryUpdateState.Downloading || update == HistoryUpdateState.Connecting || update == HistoryUpdateState.Discovering) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HistoryEmpty(data: HistorySensorData, update: HistoryUpdateState?) {
    val text = when {
        data.sensor.historySupport == HistorySupport.Unsupported || (update as? HistoryUpdateState.Failed)?.error is HistoryDownloadException.Unsupported ->
            "This sensor does not support stored history."
        data.undatedCount > 0 -> "Saved samples have no date and cannot be plotted on a timeline."
        data.sync != null || update is HistoryUpdateState.Complete -> "No saved samples are available after the last successful update."
        else -> "No local history yet."
    }
    Text(text, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun HistoryCoverage(data: HistorySensorData, compact: Boolean = false) {
    val notes = buildList {
        if (!compact) HistoryChartMath.coverage(data.points)?.let { add("Saved coverage ${formatHistoryTime(it.startMillis)} — ${formatHistoryTime(it.endMillis)}") }
        if (data.undatedCount > 0) add("${data.undatedCount} undated samples are not plotted.")
        if (data.sync?.historyTruncated == true) add("Older sensor records were overwritten; coverage may be incomplete.")
        if (data.sync?.syncPending == true) add("Sensor time synchronization is pending; some records may remain undated.")
    }
    if (notes.isNotEmpty()) Text(
        notes.joinToString(" "), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = Int.MAX_VALUE,
    )
}

@Composable
private fun SampleReadout(
    data: HistorySensorData,
    viewport: HistoryViewport,
    modifier: Modifier,
    comparison: Boolean = false,
    showName: Boolean = true,
    selectedMeasurement: HistoryMeasurement? = null,
) {
    val nearest = HistoryChartMath.nearestSample(data.points, viewport.centerMillis)
    val sample = nearest?.let { point -> data.samples.firstOrNull { it.id == point.id && it.acquisitionSequence == point.acquisitionSequence } }
    Column(modifier.testTag("history-readout-${data.address.value}")) {
        val label = if (showName) (if (comparison) "Dashed · " else "") + data.sensor.displayName + " · " else ""
        if (sample?.recordedEpochMillis != null) {
            if (selectedMeasurement != null) {
                Text(
                    "${formatHistoryValue(selectedMeasurement.value(sample))} ${selectedMeasurement.unit}" +
                        if (showName) " · " + (if (comparison) "Dashed · " else "") + data.sensor.displayName else "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(formatHistoryTime(sample.recordedEpochMillis, seconds = true), style = MaterialTheme.typography.labelSmall)
            } else {
                Text("${label}Recorded ${formatHistoryTime(sample.recordedEpochMillis, seconds = true)}", style = MaterialTheme.typography.labelSmall)
                Text(
                    "${formatHistoryValue(sample.temperatureCelsius)} °C · ${sample.humidityPercent}% · " +
                        "${formatHistoryValue(sample.pressureHectopascals)} hPa · ${sample.batteryPercent}% battery",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else Text("${label}No dated sample", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatHistoryTime(epochMillis: Long, seconds: Boolean = false): String =
    DateTimeFormatter.ofPattern(if (seconds) "d MMM yyyy HH:mm:ss.SSS z" else "d MMM yyyy HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

private fun formatHistoryValue(value: Double): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    isGroupingUsed = false
    maximumFractionDigits = 340
}.format(value)
