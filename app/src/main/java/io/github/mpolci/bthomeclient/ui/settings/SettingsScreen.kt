package io.github.mpolci.bthomeclient.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    retentions: List<Long?>,
    onBack: () -> Unit,
    onRetention: (Long?) -> Unit,
    onSave: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit = {},
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode -> ChoiceRow(if (mode == ThemeMode.System) "Follow system" else mode.name, state.pending.themeMode == mode, { onThemeMode(mode) }, "settings-theme-${mode.name}") }
            Text("Downloaded history retention", style = MaterialTheme.typography.titleMedium)
            Text("This setting applies to downloaded history for registered sensors.")
            retentions.forEach { retention -> ChoiceRow(retentionLabel(retention), state.pending.retentionDurationMillis == retention, { onRetention(retention) }, "settings-retention-${retention ?: "unlimited"}") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("settings-save")) { if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Save") }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).testTag(tag), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        RadioButton(selected, onClick = null)
    }
}

private fun retentionLabel(retention: Long?): String = when (retention) {
    null -> "Unlimited"
    else -> "${retention / 86_400_000} days"
}
