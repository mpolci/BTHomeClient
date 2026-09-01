package io.github.mpolci.bthomeclient.ui.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.ui.common.SensorImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    state: RegistrationState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register sensor") },
                navigationIcon = { IconButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Address: ${state.address.value}")
            state.sensor?.let { Text(if (it.isOnline) "Current status: Online" else "Current status: Offline") }
                ?: Text("Not currently visible")
            SensorImage(state.imageUri)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChooseImage) { Text(if (state.imageUri == null) "Choose image" else "Replace image") }
                if (state.imageUri != null) OutlinedButton(onClick = onRemoveImage) { Text("Remove image") }
            }
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().testTag("registration-name"),
                label = { Text("Display name") },
                isError = state.error != null,
                enabled = !state.isSaving,
                singleLine = true,
            )
            OutlinedTextField(
                value = state.room,
                onValueChange = onRoomChange,
                modifier = Modifier.fillMaxWidth().testTag("registration-room"),
                label = { Text("Room or position (optional)") },
                enabled = !state.isSaving,
                singleLine = true,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.displayName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("registration-save"),
            ) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Save")
            }
        }
    }
}
