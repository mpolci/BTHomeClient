package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryPlaceholderScreen(address: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("History", style = MaterialTheme.typography.headlineMedium)
        Text("Sensor: $address")
        Text("History visualization is deferred in the first release. Readings and history APIs are prepared and stored locally.")
        Button(onClick = onBack) { Text("Back") }
    }
}
