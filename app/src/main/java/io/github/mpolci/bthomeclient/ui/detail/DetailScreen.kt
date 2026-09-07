package io.github.mpolci.bthomeclient.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import io.github.mpolci.bthomeclient.domain.model.HistorySupport
import io.github.mpolci.bthomeclient.domain.model.SensorSupportStatus
import io.github.mpolci.bthomeclient.ui.common.SensorImage
import io.github.mpolci.bthomeclient.ui.main.diagnosticLabel
import io.github.mpolci.bthomeclient.ui.main.formatReadingAge
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailState,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveRoom: (String) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onRegister: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
    nowEpochMillis: Long = currentEpochMillis(),
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Sensor") }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                val color = MaterialTheme.colorScheme.onSurface
                Canvas(Modifier.size(24.dp)) {
                    drawLine(color, Offset(size.width * .8f, size.height * .5f), Offset(size.width * .2f, size.height * .5f), 2.dp.toPx())
                    drawLine(color, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .5f, size.height * .2f), 2.dp.toPx())
                    drawLine(color, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .5f, size.height * .8f), 2.dp.toPx())
                }
            }
        })
    }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Loading sensor…") }
            state.notFound || state.sensor == null -> Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sensor is no longer nearby.")
                Text("Return to Live to find it again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onBack) { Text("Back to Live") }
            }
            else -> DetailContent(state, onSaveName, onSaveRoom, onChooseImage, onRemoveImage, onRegister, onDelete, onHistory, nowEpochMillis, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailState,
    onSaveName: (String) -> Unit,
    onSaveRoom: (String) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onRegister: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
    nowEpochMillis: Long,
    modifier: Modifier,
) {
    val sensor = state.sensor ?: return
    val registered = sensor.registeredSensor
    val observation = sensor.observation
    val reading = sensor.latestReading
    val busy = state.isSaving || state.isDeleting || state.isRegistering
    var editedField by rememberSaveable(sensor.address.value) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(sensor.address.value) { mutableStateOf("") }
    var saveVersionAtOpen by rememberSaveable(sensor.address.value) { mutableStateOf(0) }
    var showDeleteConfirmation by rememberSaveable(sensor.address.value) { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    var wasRegistered by rememberSaveable(sensor.address.value) { mutableStateOf(sensor.isRegistered) }
    LaunchedEffect(sensor.isRegistered) {
        if (sensor.isRegistered && !wasRegistered) nameFocus.requestFocus()
        wasRegistered = sensor.isRegistered
    }
    LaunchedEffect(state.saveVersion, sensor.isRegistered) {
        if (state.saveVersion > saveVersionAtOpen || !sensor.isRegistered) editedField = null
        if (!sensor.isRegistered) showDeleteConfirmation = false
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            if (sensor.isRegistered) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.imageUri == null) {
                        Surface(
                            onClick = onChooseImage,
                            enabled = !busy,
                            modifier = Modifier.size(64.dp).testTag("detail-photo").semantics { contentDescription = "Add sensor photo" },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) { Box(contentAlignment = Alignment.Center) { Text("Add\nphoto", style = MaterialTheme.typography.labelMedium) } }
                    } else {
                        SensorImage(state.imageUri, Modifier.testTag("detail-photo").clickable(enabled = !busy, role = Role.Button, onClickLabel = "Change sensor photo", onClick = onChooseImage), size = 64.dp)
                        TextButton(onClick = onRemoveImage, enabled = !busy, modifier = Modifier.testTag("detail-remove-photo")) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                EditableLabel(state.displayName, "Edit local name", MaterialTheme.typography.headlineSmall, sensor.isRegistered, !busy, "detail-edit-name", Modifier.focusRequester(nameFocus)) {
                    draft = state.displayName; saveVersionAtOpen = state.saveVersion; editedField = "name"
                }
                if (sensor.isRegistered) {
                    EditableLabel(state.room.ifBlank { "Add placement" }, "Edit placement", MaterialTheme.typography.bodyMedium, true, !busy, "detail-edit-room") {
                        draft = state.room; saveVersionAtOpen = state.saveVersion; editedField = "room"
                    }
                } else {
                    Text("Not registered", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (reading == null) Text("No reading available") else SensorMeasurements(reading.measurements)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val reception = reading?.receptionEpochMillis ?: observation?.receptionEpochMillis ?: registered?.lastSeenEpochMillis
                Text(reception?.let { formatReadingAge(it, nowEpochMillis) } ?: "Reception time unavailable", style = MaterialTheme.typography.bodyMedium)
                reading?.measurements?.batteryPercent?.let { Text("Sensor battery: $it%", style = MaterialTheme.typography.bodyMedium) }
                when (sensor.supportStatus) {
                    SensorSupportStatus.Unsupported -> Text("Unsupported sensor data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SensorSupportStatus.Malformed -> Text("Sensor data could not be read", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SensorSupportStatus.Supported -> Unit
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SensorFact("Broadcast name", observation?.advertisedName?.takeIf { it.isNotBlank() } ?: registered?.advertisedName?.takeIf { it.isNotBlank() } ?: "Not reported")
            SensorFact("Bluetooth address", sensor.address.value)
            SensorFact("History support", when (sensor.historySupport) {
                HistorySupport.Unknown -> "Not checked"
                HistorySupport.Supported -> "Available"
                HistorySupport.Unsupported -> "Not supported"
            })
        }
        state.error?.let { Text(it, Modifier.testTag("detail-error"), color = MaterialTheme.colorScheme.error) }
        if (!sensor.isRegistered) {
            Button(onClick = onRegister, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("detail-register")) {
                Text(if (state.isRegistering) "Registering…" else "Register sensor")
            }
        } else when (sensor.historySupport) {
            HistorySupport.Unknown, HistorySupport.Supported -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onHistory, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("detail-history")) {
                    Text(if (sensor.historySupport == HistorySupport.Unknown) "Check history" else "Open history")
                }
                Text(if (sensor.historySupport == HistorySupport.Unknown) "Checks this sensor when opened." else "Updates automatically when opened.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HistorySupport.Unsupported -> Text("History is not supported by this sensor. Live readings remain available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SensorDiagnostics(state)
        if (sensor.isRegistered) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { showDeleteConfirmation = true }, enabled = !busy, modifier = Modifier.testTag("detail-delete"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (state.isDeleting) "Removing…" else "Remove sensor")
            }
        }
    }
    editedField?.let { field ->
        val inputFocus = remember { FocusRequester() }
        LaunchedEffect(field) { inputFocus.requestFocus() }
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) editedField = null },
            title = { Text(if (field == "name") "Name in this app" else "Placement · optional") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth().testTag("detail-field-input").focusRequester(inputFocus), singleLine = true, enabled = !state.isSaving, label = { Text(if (field == "name") "Local name" else "Placement") })
                    Text(if (field == "name") "The broadcast name stays unchanged." else "Leave empty to remove the description.", style = MaterialTheme.typography.bodySmall)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { if (field == "name") onSaveName(draft) else onSaveRoom(draft) }, enabled = !state.isSaving && (field != "name" || draft.isNotBlank()), modifier = Modifier.testTag("detail-field-save")) { Text(if (state.isSaving) "Saving…" else "Save") } },
            dismissButton = { TextButton(onClick = { editedField = null }, enabled = !state.isSaving) { Text("Cancel") } },
        )
    }
    if (showDeleteConfirmation) {
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Remove ${state.displayName}?") },
            text = { Text("Its local name, placement, photo and downloaded history will be deleted from this app.\n\nHistory stored on the sensor will not be erased.") },
            confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; onDelete() }, modifier = Modifier.testTag("detail-confirm-delete"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove sensor") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditableLabel(text: String, editLabel: String, style: TextStyle, editable: Boolean, enabled: Boolean, tag: String, editModifier: Modifier = Modifier, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f, fill = false).then(if (tag == "detail-edit-name") Modifier.semantics { heading() } else Modifier), style = style)
        if (editable) {
            IconButton(onClick = onEdit, enabled = enabled, modifier = editModifier.size(44.dp).testTag(tag).semantics { contentDescription = editLabel }) {
                val color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                val iconSize = with(LocalDensity.current) { style.fontSize.toDp() }
                Canvas(Modifier.size(iconSize)) {
                    val path = Path().apply {
                        moveTo(size.width * .16f, size.height * .65f)
                        lineTo(size.width * .65f, size.height * .16f)
                        lineTo(size.width * .84f, size.height * .35f)
                        lineTo(size.width * .35f, size.height * .84f)
                        lineTo(size.width * .12f, size.height * .88f)
                        close()
                    }
                    drawPath(path, color, style = Stroke(1.5.dp.toPx()))
                    drawLine(color, Offset(size.width * .56f, size.height * .25f), Offset(size.width * .75f, size.height * .44f), 1.5.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun SensorMeasurements(values: BTHomeMeasurements) {
    val measurements = buildList {
        values.temperatureCelsius?.let { add("Temperature" to "%.1f °C".format(it)) }
        values.humidityPercent?.let { add("Humidity" to "%.1f %%".format(it)) }
        values.pressureHectopascals?.let { add("Pressure" to "%.0f hPa".format(it)) }
        values.carbonDioxidePpm?.let { add("CO₂" to "$it ppm") }
    }
    if (measurements.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth.value / (94 * fontScale)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            measurements.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SensorFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SensorDiagnostics(state: DetailState) {
    val sensor = state.sensor ?: return
    val observation = sensor.observation
    val registered = sensor.registeredSensor
    val facts = buildList {
        (observation?.rssi ?: registered?.lastRssi)?.let { add("Signal" to "$it dBm") }
        (observation?.deviceInformation?.version ?: registered?.lastBTHomeVersion)?.let { add("BTHome version" to it.toString()) }
        (observation?.metadata?.firmwareVersion ?: registered?.firmwareVersion)?.let { add("Firmware" to it) }
        (observation?.metadata?.deviceType ?: registered?.deviceType)?.let { add("Device type" to it.toString()) }
        (observation?.deviceInformation?.encrypted ?: registered?.lastEncrypted)?.let { add("Encrypted advertisement" to if (it) "Yes" else "No") }
        (observation?.deviceInformation?.triggerBased ?: registered?.lastTriggerBased)?.let { add("Trigger based" to if (it) "Yes" else "No") }
        observation?.metadata?.packetId?.let { add("Packet ID" to it.toString()) }
        (observation?.let { diagnosticLabel(it.parseResult) } ?: registered?.protocolDiagnostic)?.takeIf { it != "None" && it != "—" }?.let { add("Data diagnostic" to it) }
    }
    if (facts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sensor information", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            facts.forEach { (label, value) -> SensorFact(label, value) }
        }
    }
}

@Composable
private fun currentEpochMillis(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }
    return now
}
