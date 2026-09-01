package io.github.mpolci.bthomeclient.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mpolci.bthomeclient.ble.BleScanner
import io.github.mpolci.bthomeclient.ble.ScanEvent
import io.github.mpolci.bthomeclient.domain.model.MergedSensor
import io.github.mpolci.bthomeclient.data.repository.SensorFeed
import io.github.mpolci.bthomeclient.ble.ScanFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

enum class ScanCapability { Checking, BleUnsupported, PermissionMissing, PermissionPermanentlyDenied, BluetoothDisabled, Ready }
data class MainScanState(val sensors: List<MergedSensor> = emptyList(), val capability: ScanCapability = ScanCapability.Checking, val isScanning: Boolean = false, val failure: ScanFailure? = null)

class MainScanViewModel(private val scanner: BleScanner, private val coordinator: SensorFeed) : ViewModel() {
    private val mutableState = MutableStateFlow(MainScanState())
    val state: StateFlow<MainScanState> = mutableState.asStateFlow()
    private var scanJob: Job? = null
    private var started = false
    init { viewModelScope.launch { coordinator.sensors.collect { mutableState.update { state -> state.copy(sensors = it) } } } }
    fun refreshCapability(capability: ScanCapability) { viewModelScope.launch { mutableState.update { it.copy(capability = capability, failure = null) }; if (capability != ScanCapability.Ready) stopScan(); reconcile() } }
    fun screenStarted() { viewModelScope.launch { started = true; reconcile() } }
    fun screenStopped() { viewModelScope.launch { started = false; stopScan() } }
    fun retry() { viewModelScope.launch { mutableState.update { it.copy(failure = null) }; stopScan(); reconcile() } }
    private fun reconcile() {
        if (!started || mutableState.value.capability != ScanCapability.Ready || scanJob != null) return
        scanJob = viewModelScope.launch {
            mutableState.update { it.copy(isScanning = true) }
            try { scanner.scan().collect { event -> when (event) {
                is ScanEvent.Advertisement -> coordinator.observe(event.observation)
                is ScanEvent.Failure -> { mutableState.update { it.copy(isScanning = false, failure = event.error) } }
            } } } finally { mutableState.update { it.copy(isScanning = false) }; if (scanJob == kotlinx.coroutines.currentCoroutineContext()[Job]) scanJob = null }
        }
    }
    private suspend fun stopScan() { scanJob?.cancelAndJoin(); scanJob = null; mutableState.update { it.copy(isScanning = false) } }
}
