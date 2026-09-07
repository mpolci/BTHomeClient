package io.github.mpolci.bthomeclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.ThemeMode
import io.github.mpolci.bthomeclient.ui.editor.EditorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val current: AppSettings = AppSettings(),
    val pending: AppSettings = AppSettings(),
    val edited: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val manager: SensorManager,
) : ViewModel() {
    companion object {
        val retentions = listOf<Long?>(
            null,
            7L * 86_400_000,
            30L * 86_400_000,
            90L * 86_400_000,
            365L * 86_400_000,
        )
    }

    private val mutableState = MutableStateFlow(SettingsState())
    val state = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<EditorEvent>()
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            manager.settings.collect { settings ->
                mutableState.update { current ->
                    if (current.edited) current.copy(current = settings)
                    else SettingsState(settings, settings)
                }
            }
        }
    }

    fun edit(retention: Long? = state.value.pending.retentionDurationMillis, themeMode: ThemeMode = state.value.pending.themeMode) {
        if (retention !in retentions) {
            mutableState.update { it.copy(error = "Invalid settings selection.") }
            return
        }
        mutableState.update {
            it.copy(pending = AppSettings(retention, themeMode), edited = true, error = null)
        }
    }

    fun save() = viewModelScope.launch {
        val current = state.value
        if (current.isSaving) return@launch
        mutableState.update { it.copy(isSaving = true, error = null) }
        runCatching { manager.saveSettings(current.pending) }
            .onSuccess {
                mutableState.update { SettingsState(current.pending, current.pending) }
                mutableEvents.emit(EditorEvent.Saved)
            }
            .onFailure {
                mutableState.update { it.copy(isSaving = false, error = "Unable to save settings.") }
            }
    }
}
