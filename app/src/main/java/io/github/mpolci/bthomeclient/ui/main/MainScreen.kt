package io.github.mpolci.bthomeclient.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.ble.ScanFailure

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
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Sensors", Modifier.semantics { heading() }) }, actions = { TextButton(onClick = onSettings, Modifier.semantics { contentDescription = "Settings" }) { Text("Settings") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Column(Modifier.padding(horizontal = 16.dp)) {
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
            if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.sensors.isEmpty() && state.capability == ScanCapability.Ready && state.failure == null) Text(if (state.isScanning) "No sensors found yet. Scanning is active." else "No sensors found. Scanning is not active.")
            } }
            items(state.sensors, key = { it.address.value }) { sensor -> SensorCard(sensor, onRegister, onDetails, Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable private fun ActionState(message: String, action: String, onAction: () -> Unit) { Text(message); Button(onClick = onAction) { Text(action) } }

@Composable
private fun SensorCard(sensor: MergedSensor, onRegister: (MergedSensor) -> Unit, onDetails: (MergedSensor) -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable(sensor.address.value) { mutableStateOf(false) }
    val measurements = sensor.observation?.measurements
    val name = sensor.registeredSensor?.displayName ?: sensor.observation?.advertisedName ?: sensor.address.value
    val availability = if (sensor.isOnline) "Online" else "Offline"
    val status = when (sensor.supportStatus) {
        SensorSupportStatus.Supported -> availability
        SensorSupportStatus.Unsupported -> "$availability · Unsupported"
        SensorSupportStatus.Malformed -> "$availability · Malformed"
    }
    val cardColors = if (sensor.isRegistered) {
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("sensor-${sensor.address.value}"),
        colors = cardColors,
    ) {
        Column(Modifier.testTag("sensor-body-${sensor.address.value}").clickable { if (sensor.isRegistered) onDetails(sensor) else onRegister(sensor) }.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(name, style = MaterialTheme.typography.titleMedium); Text(if (sensor.isRegistered) "Registered · $status" else "Unregistered · $status") }
                IconButton(onClick = { expanded = !expanded }, Modifier.semantics { contentDescription = if (expanded) "Collapse $name" else "Expand $name" }) { Text(if (expanded) "⌃" else "⌄") }
            }
            if (measurements == null) Text("No live reading available") else SensorMeasurements(measurements)
            if (expanded) Text("Address: ${sensor.address.value}\nRSSI: ${sensor.observation?.rssi ?: "—"}\nLast seen: ${formatLastSeen(sensor.observation?.receptionEpochMillis ?: sensor.registeredSensor?.lastSeenEpochMillis)}\nBTHome version: ${sensor.observation?.deviceInformation?.version ?: sensor.registeredSensor?.lastBTHomeVersion ?: "—"}\nEncrypted: ${sensor.observation?.deviceInformation?.encrypted ?: sensor.registeredSensor?.lastEncrypted ?: "—"}\nTrigger based: ${sensor.observation?.deviceInformation?.triggerBased ?: sensor.registeredSensor?.lastTriggerBased ?: "—"}\nPacket ID: ${sensor.observation?.metadata?.packetId ?: "—"}\nDevice type: ${sensor.observation?.metadata?.deviceType ?: sensor.registeredSensor?.deviceType ?: "—"}\nFirmware: ${sensor.observation?.metadata?.firmwareVersion ?: sensor.registeredSensor?.firmwareVersion ?: "—"}\nSupported: ${(if (sensor.isRegistered) sensor.registeredSensor?.supportedMeasurementTypes else measurements?.let(::measurementTypes))?.joinToString() ?: "—"}\nDiagnostic: ${sensor.registeredSensor?.protocolDiagnostic ?: diagnosticLabel(sensor.observation?.parseResult)}")
        }
    }
}

@Composable
private fun SensorMeasurements(values: io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements) {
    Column {
        values.temperatureCelsius?.let { value -> Text("Temperature: %.2f °C".format(value)) }
        values.humidityPercent?.let { value -> Text("Humidity: %.2f %%".format(value)) }
        values.pressureHectopascals?.let { value -> Text("Pressure: %.2f hPa".format(value)) }
        values.carbonDioxidePpm?.let { value -> Text("CO₂: $value ppm") }
        values.batteryPercent?.let { value -> Text("Battery: $value%") }
    }
}

private fun failureMessage(failure: ScanFailure): String = when (failure) {
    is ScanFailure.Platform -> "Bluetooth scan could not start (code ${failure.code})."
    ScanFailure.PermissionDenied -> "Nearby Devices permission is required."
    ScanFailure.BluetoothDisabled -> "Bluetooth is turned off."
    ScanFailure.Unavailable -> "Bluetooth scanning is unavailable."
}
