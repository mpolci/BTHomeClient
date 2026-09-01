package io.github.mpolci.bthomeclient.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.util.Clock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

sealed interface ScanEvent {
    data class Advertisement(val observation: ScanObservation) : ScanEvent
    data class Failure(val error: ScanFailure) : ScanEvent
}

sealed interface ScanFailure { data class Platform(val code: Int) : ScanFailure; data object PermissionDenied : ScanFailure; data object BluetoothDisabled : ScanFailure; data object Unavailable : ScanFailure }
fun interface BleScanner { fun scan(): Flow<ScanEvent> }

class AndroidBleScanner(
    private val context: Context,
    private val clock: Clock,
    parser: BTHomeV2Parser,
) : BleScanner {
    private val mapper = BleAdvertisementMapper(parser)
    override fun scan(): Flow<ScanEvent> = callbackFlow {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            trySend(ScanEvent.Failure(ScanFailure.PermissionDenied)); close(); return@callbackFlow
        }
        val adapter = try { context.getSystemService(BluetoothManager::class.java)?.adapter } catch (_: SecurityException) { trySend(ScanEvent.Failure(ScanFailure.PermissionDenied)); close(); return@callbackFlow }
        val enabled = try { adapter?.isEnabled } catch (_: SecurityException) { trySend(ScanEvent.Failure(ScanFailure.PermissionDenied)); close(); return@callbackFlow }
        if (enabled == false) { trySend(ScanEvent.Failure(ScanFailure.BluetoothDisabled)); close(); return@callbackFlow }
        val scanner = try { adapter?.bluetoothLeScanner } catch (_: SecurityException) { trySend(ScanEvent.Failure(ScanFailure.PermissionDenied)); close(); return@callbackFlow }
        if (adapter == null || scanner == null) { trySend(ScanEvent.Failure(ScanFailure.Unavailable)); close(); return@callbackFlow }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val record = result.scanRecord ?: return
                    val data = record.getServiceData(BTHOME_UUID) ?: return
                    mapper.map(result.device.address, record.deviceName, result.rssi, clock.nowEpochMillis(), 0xFCD2, data)?.let(::trySend)
                } catch (_: SecurityException) { trySend(ScanEvent.Failure(ScanFailure.PermissionDenied)); close() }
            }
            override fun onScanFailed(errorCode: Int) { trySend(ScanEvent.Failure(ScanFailure.Platform(errorCode))); close() }
        }
        try { scanner.startScan(bthomeScanFilters(), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback) }
        catch (_: SecurityException) { trySend(ScanEvent.Failure(ScanFailure.PermissionDenied)); close() }
        catch (_: IllegalStateException) { trySend(ScanEvent.Failure(ScanFailure.Unavailable)); close() }
        catch (_: IllegalArgumentException) { trySend(ScanEvent.Failure(ScanFailure.Unavailable)); close() }
        awaitClose { try { scanner.stopScan(callback) } catch (_: SecurityException) {} }
    }
    private companion object { val BTHOME_UUID = ParcelUuid(UUID.fromString("0000fcd2-0000-1000-8000-00805f9b34fb")) }
}

fun bthomeScanFilters(): List<ScanFilter> {
    // Some Android offload stacks do not match empty or masked service-data filters.
    val uuid = ParcelUuid(UUID.fromString("0000fcd2-0000-1000-8000-00805f9b34fb"))
    val deviceInformation = byteArrayOf(0x40, 0x41, 0x44, 0x45, 0x20, 0x21, 0x24, 0x25)
    return deviceInformation.map { value ->
        ScanFilter.Builder().setServiceData(uuid, byteArrayOf(value)).build()
    }
}
