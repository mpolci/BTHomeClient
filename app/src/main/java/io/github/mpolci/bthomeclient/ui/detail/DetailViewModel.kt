package io.github.mpolci.bthomeclient.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.ui.editor.EditorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val sensor: MergedSensor? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val displayName: String = "",
    val room: String = "",
    val imageUri: String? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
)

class DetailViewModel(
    private val address: SensorAddress,
    private val manager: SensorManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailState())
    val state = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<EditorEvent>()
    val events = mutableEvents.asSharedFlow()
    private var dirtyName = false
    private var dirtyRoom = false
    private var dirtyImage = false
    private var metadataInitialized = false

    init {
        viewModelScope.launch {
            manager.sensor(address).collect { sensor ->
                mutableState.update { current ->
                    val registered = sensor?.registeredSensor
                    if (registered == null) current.copy(sensor = sensor, loading = false, notFound = true)
                    else current.copy(sensor = sensor, loading = false, notFound = false, displayName = if (dirtyName || metadataInitialized) current.displayName else registered.displayName, room = if (dirtyRoom || metadataInitialized) current.room else registered.room.orEmpty(), imageUri = if (dirtyImage || metadataInitialized) current.imageUri else registered.imageUri).also { metadataInitialized = true }
                }
            }
        }
    }

    fun edit(name: String = state.value.displayName, room: String = state.value.room, imageUri: String? = state.value.imageUri) {
        if (name != state.value.displayName) dirtyName = true
        if (room != state.value.room) dirtyRoom = true
        if (imageUri != state.value.imageUri) dirtyImage = true
        mutableState.update { it.copy(displayName = name, room = room, imageUri = imageUri, error = null) }
    }

    fun save() = viewModelScope.launch {
        val current = state.value
        if (current.isSaving || current.isDeleting || current.displayName.isBlank()) {
            if (current.displayName.isBlank()) mutableState.update { it.copy(error = "A display name is required.") }
            return@launch
        }
        mutableState.update { it.copy(isSaving = true) }
        runCatching { manager.updateEditableMetadata(address, current.displayName, current.room, current.imageUri) }
            .onSuccess { mutableState.update { it.copy(isSaving = false) }; mutableEvents.emit(EditorEvent.Saved) }
            .onFailure { mutableState.update { it.copy(isSaving = false, error = "Unable to save sensor.") } }
    }

    fun delete() = viewModelScope.launch {
        val current = state.value
        if (current.isSaving || current.isDeleting) return@launch

        mutableState.update { it.copy(isDeleting = true, error = null) }
        runCatching { manager.deleteRegistration(address) }
            .onSuccess {
                mutableState.update { it.copy(isDeleting = false) }
                mutableEvents.emit(EditorEvent.Deleted)
            }
            .onFailure {
                mutableState.update {
                    it.copy(isDeleting = false, error = "Unable to remove sensor.")
                }
            }
    }
}
