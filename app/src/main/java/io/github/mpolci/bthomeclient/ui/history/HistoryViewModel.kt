package io.github.mpolci.bthomeclient.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.data.repository.HistoryRepository
import io.github.mpolci.bthomeclient.data.repository.HistoryRepositoryEvent
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryState(val downloading: Boolean = false, val received: Int = 0, val total: Int = 0, val summary: String? = null, val error: String? = null)
class HistoryViewModel(private val address: SensorAddress, private val repository: HistoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryState())
    val state = mutableState.asStateFlow()
    fun download() = viewModelScope.launch {
        if (mutableState.value.downloading) return@launch
        mutableState.value = HistoryState(downloading = true)
        runCatching { repository.download(address).collect { event ->
            when (event) {
                HistoryRepositoryEvent.Connecting, HistoryRepositoryEvent.Discovering -> Unit
                is HistoryRepositoryEvent.Downloading -> mutableState.value = mutableState.value.copy(received = event.receivedPositions, total = event.totalPositions)
                is HistoryRepositoryEvent.Complete -> mutableState.value = HistoryState(summary = "Imported ${event.summary.importedSamples} samples. Invalid/erased: ${event.summary.invalidPositions + event.summary.erasedPositions}.")
            }
        } }.onFailure { mutableState.value = HistoryState(error = it.message ?: "History download failed.") }
    }
}
