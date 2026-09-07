package io.github.mpolci.bthomeclient.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.data.repository.SensorManager
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.util.Clock
import io.github.mpolci.bthomeclient.util.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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
    val isRegistering: Boolean = false,
    val saveVersion: Int = 0,
    val error: String? = null,
)

class DetailViewModel(
    private val address: SensorAddress,
    private val manager: SensorManager,
    private val clock: Clock = SystemClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailState())
    val state = mutableState.asStateFlow()
    private var retainedUnregistered: MergedSensor? = null
    private var retainedObservation: ScanObservation? = null

    init {
        viewModelScope.launch {
            manager.sensor(address).collect { sensor ->
                sensor?.observation?.let { observation ->
                    if (retainedObservation?.receptionEpochMillis?.let { it > observation.receptionEpochMillis } != true) retainedObservation = observation
                }
                if (sensor?.isRegistered == false) retainedUnregistered = sensor
                if (sensor?.isRegistered == true) retainedUnregistered = null
                showSensor(sensor ?: retainedUnregistered?.copy(isOnline = false))
            }
        }
    }

    fun saveName(value: String) = saveField {
        require(value.isNotBlank()) { "A local name is required." }
        manager.updateLocalName(address, value.trim())
    }

    fun saveRoom(value: String) = saveField {
        manager.updatePlacement(address, value.trim().ifBlank { null })
    }

    fun saveImage(uri: String?) = saveField {
        manager.updateImage(address, uri)
    }

    private fun saveField(update: suspend () -> Unit) = viewModelScope.launch {
        if (busy() || state.value.sensor?.isRegistered != true) return@launch
        mutableState.update { it.copy(isSaving = true, error = null) }
        try {
            update()
            mutableState.update { it.copy(saveVersion = it.saveVersion + 1) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            mutableState.update { it.copy(error = error.message ?: "Unable to save sensor.") }
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "Unable to save sensor. Try again.") }
        } finally {
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    fun register() = viewModelScope.launch {
        val sensor = state.value.sensor ?: return@launch
        if (busy() || sensor.isRegistered) return@launch
        val observation = sensor.observation ?: retainedObservation
        val name = observation?.advertisedName?.trim()?.takeIf { it.isNotBlank() } ?: address.value
        mutableState.update { it.copy(isRegistering = true, error = null) }
        try {
            manager.register(address, name)
            // A page may outlive the nearby feed. Seed the saved reading with its original reception time.
            observation?.let { manager.observe(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "Unable to register sensor. Try again.") }
        } finally {
            mutableState.update { it.copy(isRegistering = false) }
        }
    }

    fun delete() = viewModelScope.launch {
        val sensor = state.value.sensor ?: return@launch
        if (busy() || !sensor.isRegistered) return@launch
        mutableState.update { it.copy(isDeleting = true, error = null) }
        try {
            manager.deleteRegistration(address)
            retainedUnregistered = retainedObservation?.takeIf { it.receptionEpochMillis > clock.nowEpochMillis() - 300_000L }?.let {
                sensor.copy(registeredSensor = null, observation = it, isOnline = true)
            }
            showSensor(retainedUnregistered)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "Unable to remove sensor. Try again.") }
        } finally {
            mutableState.update { it.copy(isDeleting = false) }
        }
    }

    private fun busy() = state.value.let { it.isSaving || it.isDeleting || it.isRegistering }

    private fun showSensor(sensor: MergedSensor?) {
        mutableState.update { current ->
            val registered = sensor?.registeredSensor
            current.copy(
                sensor = sensor,
                loading = false,
                notFound = sensor == null,
                displayName = registered?.displayName ?: sensor?.observation?.advertisedName?.takeIf { it.isNotBlank() } ?: sensor?.address?.value.orEmpty(),
                room = registered?.room.orEmpty(),
                imageUri = registered?.imageUri,
            )
        }
    }
}
