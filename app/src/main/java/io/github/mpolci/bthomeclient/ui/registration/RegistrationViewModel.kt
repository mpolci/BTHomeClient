package io.github.mpolci.bthomeclient.ui.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.ui.editor.EditorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegistrationState(
    val address: SensorAddress,
    val sensor: MergedSensor? = null,
    val loading: Boolean = true,
    val displayName: String = "",
    val room: String = "",
    val imageUri: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class RegistrationViewModel(
    private val address: SensorAddress,
    private val manager: SensorManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        RegistrationState(address = address, displayName = "Sensor ${address.value.takeLast(5)}"),
    )
    val state: StateFlow<RegistrationState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<EditorEvent>()
    val events = mutableEvents.asSharedFlow()
    private var nameEdited = false
    private var initializedName = false
    init {
        viewModelScope.launch {
            manager.sensor(address).collect { sensor ->
                mutableState.update { current ->
                    val advertised = sensor?.observation?.advertisedName
                    if (!nameEdited && !initializedName && advertised != null) {
                        initializedName = true
                        current.copy(sensor = sensor, loading = false, displayName = advertised)
                    } else current.copy(sensor = sensor, loading = false)
                }
            }
        }
    }

    fun edit(displayName: String = state.value.displayName, room: String = state.value.room, imageUri: String? = state.value.imageUri) {
        if (displayName != state.value.displayName) nameEdited = true
        mutableState.update { it.copy(displayName = displayName, room = room, imageUri = imageUri, error = null) }
    }

    fun save() = viewModelScope.launch {
        val current = state.value
        if (current.isSaving) return@launch
        if (current.displayName.isBlank()) { mutableState.update { it.copy(error = "A display name is required.") }; return@launch }
        mutableState.update { it.copy(isSaving = true) }
        runCatching { manager.register(address, current.displayName, current.room, current.imageUri) }
            .onSuccess { mutableState.update { it.copy(isSaving = false) }; mutableEvents.emit(EditorEvent.Saved) }
            .onFailure { mutableState.update { it.copy(isSaving = false, error = "Unable to save sensor.") } }
    }
}
