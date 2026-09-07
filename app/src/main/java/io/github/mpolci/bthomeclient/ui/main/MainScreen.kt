package io.github.mpolci.bthomeclient.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.ble.ScanFailure
import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.ui.common.SensorImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainScanState,
    onPermissionRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onRegister: (MergedSensor) -> Unit,
    onDetails: (MergedSensor) -> Unit,
    nowEpochMillis: Long = currentEpochMillis(),
) {
    val registered = state.sensors.filter { it.isRegistered }
    val detected = state.sensors.filterNot { it.isRegistered }
    Scaffold(topBar = { TopAppBar(title = { Text("Live", Modifier.semantics { heading() }) }, actions = { TextButton(onClick = onSettings, Modifier.semantics { contentDescription = "Settings" }) { Text("Settings") } }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.capability != ScanCapability.Ready || state.failure != null || state.sensors.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state.capability) {
                        ScanCapability.Checking -> Text("Checking Bluetooth capability…")
                        ScanCapability.PermissionMissing -> ActionState("Nearby Devices permission is required.", "Allow", onPermissionRequest)
                        ScanCapability.PermissionPermanentlyDenied -> ActionState("Nearby Devices permission was denied permanently.", "Open settings", onOpenSettings)
                        ScanCapability.BluetoothDisabled -> ActionState("Bluetooth is turned off.", "Enable Bluetooth", onEnableBluetooth)
                        ScanCapability.BleUnsupported -> Text("Bluetooth Low Energy is not supported on this device.")
                        ScanCapability.Ready -> state.failure?.let { failure ->
                            when (failure) {
                                ScanFailure.PermissionDenied -> ActionState(failureMessage(failure), "Allow", onPermissionRequest)
                                ScanFailure.BluetoothDisabled -> ActionState(failureMessage(failure), "Enable Bluetooth", onEnableBluetooth)
                                is ScanFailure.Platform, ScanFailure.Unavailable -> ActionState(failureMessage(failure), "Retry", onRetry)
                            }
                        }
                    }
                    if (state.sensors.isEmpty() && state.capability == ScanCapability.Ready && state.failure == null) {
                        if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if (state.isScanning) "No sensors found yet. Scanning is active." else "No sensors found. Scanning is not active.")
                    }
                }
            }
            if (registered.isNotEmpty()) {
                item(key = "registered-heading") { SensorSectionTitle("Registered sensors", registered.size) }
                items(registered, key = { it.address.value }) { sensor -> SensorCard(sensor, onRegister, onDetails, nowEpochMillis) }
            }
            if (detected.isNotEmpty()) {
                item(key = "detected-heading") {
                    Column {
                        if (registered.isNotEmpty()) HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SensorSectionTitle("Other nearby sensors", detected.size)
                    }
                }
                items(detected, key = { it.address.value }) { sensor -> SensorCard(sensor, onRegister, onDetails, nowEpochMillis) }
            }
        }
    }
}

@Composable private fun ActionState(message: String, action: String, onAction: () -> Unit) { Text(message); Button(onClick = onAction) { Text(action) } }

@Composable
private fun SensorSectionTitle(title: String, count: Int) {
    Text("$title · $count", Modifier.padding(horizontal = 4.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SensorCard(sensor: MergedSensor, onRegister: (MergedSensor) -> Unit, onDetails: (MergedSensor) -> Unit, nowEpochMillis: Long) {
    val reading = sensor.latestReading
    val name = sensor.registeredSensor?.displayName ?: sensor.observation?.advertisedName ?: sensor.address.value
    Card(
        modifier = Modifier.fillMaxWidth().testTag("sensor-${sensor.address.value}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().testTag("sensor-body-${sensor.address.value}").clickable { if (sensor.isRegistered) onDetails(sensor) else onRegister(sensor) }.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                sensor.registeredSensor?.imageUri?.let { SensorImage(it, size = 40.dp) }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!sensor.isRegistered) Surface(shape = MaterialTheme.shapes.small, color = Color.Transparent, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Text("Not registered", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    sensor.registeredSensor?.room?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                reading?.measurements?.batteryPercent?.let { BatteryIndicator(it) }
            }
            if (reading != null) SensorMeasurements(reading.measurements)
            else Text("No reading available", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
            when (sensor.supportStatus) {
                SensorSupportStatus.Unsupported -> Text("Unsupported sensor data", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SensorSupportStatus.Malformed -> Text("Sensor data could not be read", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SensorSupportStatus.Supported -> Unit
            }
            reading?.takeIf { nowEpochMillis - it.receptionEpochMillis >= 300_000L }?.let {
                Text(formatReadingAge(it.receptionEpochMillis, nowEpochMillis), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SensorMeasurements(values: BTHomeMeasurements) {
    val measurements = buildList {
        values.temperatureCelsius?.let { add(Triple("Temperature", "%.1f".format(it), "°C")) }
        values.humidityPercent?.let { add(Triple("Humidity", "%.1f".format(it), "%")) }
        values.pressureHectopascals?.let { add(Triple("Pressure", "%.0f".format(it), "hPa")) }
        values.carbonDioxidePpm?.let { add(Triple("CO₂", it.toString(), "ppm")) }
    }
    if (measurements.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    val unitStyle = MaterialTheme.typography.bodySmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        val columns = (maxWidth.value / (88 * fontScale)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            measurements.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value, unit) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(buildAnnotatedString { append(value); withStyle(unitStyle) { append(" $unit") } }, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BatteryIndicator(percent: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val level = percent.coerceIn(0, 100)
    Canvas(Modifier.padding(top = 4.dp).size(18.dp, 12.dp).semantics {
        contentDescription = "Sensor battery"
        progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), 0f..100f)
    }) {
        val stroke = 1.dp.toPx()
        val bodyWidth = size.width - 2.dp.toPx()
        drawRoundRect(color, Offset(stroke / 2, stroke / 2), Size(bodyWidth - stroke, size.height - stroke), CornerRadius(stroke), style = Stroke(stroke))
        drawRect(color, Offset(bodyWidth, size.height * .3f), Size(2.dp.toPx(), size.height * .4f))
        drawRect(color, Offset(stroke * 2, stroke * 2), Size((bodyWidth - stroke * 4) * level / 100f, size.height - stroke * 4))
    }
}

@Composable
private fun currentEpochMillis(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }
    return now
}

private fun failureMessage(failure: ScanFailure): String = when (failure) {
    is ScanFailure.Platform -> "Bluetooth scan could not start (code ${failure.code})."
    ScanFailure.PermissionDenied -> "Nearby Devices permission is required."
    ScanFailure.BluetoothDisabled -> "Bluetooth is turned off."
    ScanFailure.Unavailable -> "Bluetooth scanning is unavailable."
}
