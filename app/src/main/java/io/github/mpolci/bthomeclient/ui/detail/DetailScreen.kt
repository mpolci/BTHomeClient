package io.github.mpolci.bthomeclient.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.ui.common.SensorImage
import io.github.mpolci.bthomeclient.ui.main.diagnosticLabel
import io.github.mpolci.bthomeclient.ui.main.formatLastSeen
import io.github.mpolci.bthomeclient.ui.main.mergedMeasurements

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
    onHistory: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Sensor details") }, navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("Loading sensor…") }
            state.notFound -> Column(Modifier.padding(padding).padding(16.dp)) { Text("Registered sensor not found."); Button(onClick = onBack) { Text("Back") } }
            else -> DetailContent(state, onNameChange, onRoomChange, onChooseImage, onRemoveImage, onSave, onHistory, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailState,
    onNameChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier,
) {
    val sensor = state.sensor ?: return
    val availability = if (sensor.isOnline) "Online" else "Offline"
    val status = when (sensor.supportStatus) {
        SensorSupportStatus.Supported -> availability
        SensorSupportStatus.Unsupported -> "$availability · Unsupported"
        SensorSupportStatus.Malformed -> "$availability · Malformed"
    }
    val values = mergedMeasurements(sensor.observation?.measurements, sensor.latestReading?.measurements)
    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.displayName, style = MaterialTheme.typography.headlineSmall)
        Text("Registered · $status")
        SensorMeasurements(values)
        SensorDiagnostics(state)
        SensorImage(state.imageUri)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseImage) { Text(if (state.imageUri == null) "Choose image" else "Replace image") }
            if (state.imageUri != null) OutlinedButton(onClick = onRemoveImage) { Text("Remove image") }
        }
        OutlinedTextField(state.displayName, onNameChange, Modifier.fillMaxWidth().testTag("detail-name"), label = { Text("Display name") }, enabled = !state.isSaving, isError = state.error != null)
        OutlinedTextField(state.room, onRoomChange, Modifier.fillMaxWidth().testTag("detail-room"), label = { Text("Room or position (optional)") }, enabled = !state.isSaving)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSave, enabled = !state.isSaving && state.displayName.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("detail-save")) { if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Save") }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().testTag("detail-history")) { Text("History") }
    }
}

@Composable private fun SensorMeasurements(values: io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements) { Column { values.temperatureCelsius?.let { Text("Temperature: %.2f °C".format(it)) }; values.humidityPercent?.let { Text("Humidity: %.2f %%".format(it)) }; values.pressureHectopascals?.let { Text("Pressure: %.2f hPa".format(it)) }; values.carbonDioxidePpm?.let { Text("CO₂: $it ppm") }; values.batteryPercent?.let { Text("Battery: $it%") } } }

@Composable private fun SensorDiagnostics(state: DetailState) {
    val sensor = state.sensor ?: return
    val observation = sensor.observation
    val registered = sensor.registeredSensor
    val diagnostic = observation?.let { diagnosticLabel(it.parseResult) } ?: registered?.protocolDiagnostic ?: "—"
    Text("Address: ${sensor.address.value}\nRSSI: ${observation?.rssi ?: "—"}\nLast seen: ${formatLastSeen(observation?.receptionEpochMillis ?: registered?.lastSeenEpochMillis)}\nBTHome version: ${observation?.deviceInformation?.version ?: registered?.lastBTHomeVersion ?: "—"}\nEncrypted: ${observation?.deviceInformation?.encrypted ?: registered?.lastEncrypted ?: "—"}\nTrigger based: ${observation?.deviceInformation?.triggerBased ?: registered?.lastTriggerBased ?: "—"}\nPacket ID: ${observation?.metadata?.packetId ?: sensor.latestReading?.packetId ?: "—"}\nDevice type: ${observation?.metadata?.deviceType ?: registered?.deviceType ?: "—"}\nFirmware: ${observation?.metadata?.firmwareVersion ?: registered?.firmwareVersion ?: "—"}\nSupported: ${registered?.supportedMeasurementTypes?.joinToString() ?: "—"}\nDiagnostic: $diagnostic")
}
