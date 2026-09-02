package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun HistoryScreen(address: String, state: HistoryState, onDownload: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("History"); Text("Sensor: $address")
        if (state.downloading) { CircularProgressIndicator(); Text("Downloading ${state.received}/${state.total}") }
        state.summary?.let { Text(it) }; state.error?.let { Text(it) }
        Button(onClick = onDownload, enabled = !state.downloading, modifier = Modifier.fillMaxWidth()) { Text("Download history") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
