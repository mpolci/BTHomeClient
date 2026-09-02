package io.github.mpolci.bthomeclient.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryControlResponse
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryCursor
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryDataPacket
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryFlags
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryProtocol
import io.github.mpolci.bthomeclient.history.sene.SeneHistoryTransferAccumulator
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

interface HistoryDownloader {
    fun download(address: SensorAddress, request: HistoryDownloadRequest): Flow<HistoryDownloadEvent>
}

sealed interface HistoryDownloadRequest {
    val phoneEpochSeconds: Long

    data class Full(override val phoneEpochSeconds: Long) : HistoryDownloadRequest
    data class After(
        override val phoneEpochSeconds: Long,
        val cursor: ByteArray,
    ) : HistoryDownloadRequest
}

sealed interface HistoryDownloadEvent {
    data object Connecting : HistoryDownloadEvent
    data object Discovering : HistoryDownloadEvent
    data class Downloading(val receivedPositions: Int, val totalPositions: Int) : HistoryDownloadEvent
    data class Complete(val result: HistoryDownloadResult) : HistoryDownloadEvent
}

sealed interface HistoryDownloadResult {
    data class Transfer(
        val flags: SeneHistoryFlags,
        val finalCursor: SeneHistoryCursor,
        val rawPositions: ByteArray,
    ) : HistoryDownloadResult

    data class Empty(
        val flags: SeneHistoryFlags,
        val finalCursor: SeneHistoryCursor,
    ) : HistoryDownloadResult
}

sealed class HistoryDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionLost(cause: SecurityException? = null) : HistoryDownloadException("Nearby Devices permission was lost.", cause)
    class BluetoothDisabled : HistoryDownloadException("Bluetooth is turned off.")
    class Connection(message: String) : HistoryDownloadException(message)
    class Unsupported : HistoryDownloadException("This sensor does not expose the SENE-A history service.")
    class Protocol(message: String, cause: Throwable? = null) : HistoryDownloadException(message, cause)
    class Remote(val response: SeneHistoryControlResponse) : HistoryDownloadException("Sensor rejected the history request: $response")
    class Timeout(message: String) : HistoryDownloadException(message)
}

/** Android GATT transport. Importing and cursor persistence are deliberately owned by the repository layer. */
class AndroidHistoryDownloader(
    context: Context,
    private val timeouts: HistoryDownloadTimeouts = HistoryDownloadTimeouts(),
) : HistoryDownloader {
    private val appContext = context.applicationContext
    private val transferMutex = Mutex()

    override fun download(address: SensorAddress, request: HistoryDownloadRequest): Flow<HistoryDownloadEvent> = flow {
        transferMutex.withLock {
            ensureConnectPermission()
            val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw HistoryDownloadException.Connection("Bluetooth is unavailable.")
            if (!adapter.isEnabled) throw HistoryDownloadException.BluetoothDisabled()
            val device = try {
                adapter.getRemoteDevice(address.value)
            } catch (error: IllegalArgumentException) {
                throw HistoryDownloadException.Connection("The registered Bluetooth address is invalid.")
            } catch (error: SecurityException) {
                throw HistoryDownloadException.PermissionLost(error)
            }

            val events = Channel<GattEvent>(Channel.UNLIMITED)
            var gatt: BluetoothGatt? = null
            var activeTransferId: Int? = null
            try {
                emit(HistoryDownloadEvent.Connecting)
                gatt = connect(device, events)
                awaitConnection(gatt, events)
                requestConnectionOptimizations(gatt, events)

                emit(HistoryDownloadEvent.Discovering)
                discoverServices(gatt, events)
                val service = gatt.getService(SeneHistoryProtocol.serviceUuid)
                    ?: throw HistoryDownloadException.Unsupported()
                val control = service.getCharacteristic(SeneHistoryProtocol.controlPointUuid)
                    ?: throw HistoryDownloadException.Unsupported()
                val data = service.getCharacteristic(SeneHistoryProtocol.dataStreamUuid)
                    ?: throw HistoryDownloadException.Unsupported()
                configureCccd(gatt, control, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE, events)
                configureCccd(gatt, data, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, events)

                writeOpen(gatt, control, request, events)
                when (val response = awaitControlResponse(events, timeouts.responseMillis)) {
                    is SeneHistoryControlResponse.Empty -> emit(
                        HistoryDownloadEvent.Complete(HistoryDownloadResult.Empty(response.flags, response.finalCursor)),
                    )
                    is SeneHistoryControlResponse.Ready -> {
                        activeTransferId = response.transferId
                        val accumulator = SeneHistoryTransferAccumulator(response.transferId, response.positionCount)
                        var received = 0
                        while (received < response.positionCount) {
                            when (val event = awaitEvent(events, timeouts.dataInactivityMillis, "History data timed out.")) {
                                is GattEvent.Data -> {
                                    val packet = try {
                                        SeneHistoryProtocol.decodeDataPacket(event.value)
                                    } catch (error: IllegalArgumentException) {
                                        throw HistoryDownloadException.Protocol("Malformed history data packet.", error)
                                    }
                                    received = try {
                                        accumulator.append(packet)
                                    } catch (error: IllegalArgumentException) {
                                        throw HistoryDownloadException.Protocol("History data packet is out of sequence.", error)
                                    }
                                    emit(HistoryDownloadEvent.Downloading(received, response.positionCount))
                                }
                                is GattEvent.Control -> throwRemoteOrProtocol(decodeControl(event.value))
                                is GattEvent.Disconnected -> throw HistoryDownloadException.Connection("The sensor disconnected during history transfer.")
                                else -> Unit
                            }
                        }
                        val terminal = awaitControlResponse(events, timeouts.terminalMillis)
                        if (terminal !is SeneHistoryControlResponse.Done) {
                            throwRemoteOrProtocol(terminal)
                        }
                        val raw = try {
                            accumulator.complete(terminal.transferId)
                        } catch (error: IllegalArgumentException) {
                            throw HistoryDownloadException.Protocol("History completion is invalid.", error)
                        }
                        activeTransferId = null
                        emit(HistoryDownloadEvent.Complete(HistoryDownloadResult.Transfer(response.flags, response.finalCursor, raw)))
                    }
                    else -> throwRemoteOrProtocol(response)
                }
            } catch (error: SecurityException) {
                throw HistoryDownloadException.PermissionLost(error)
            } catch (error: CancellationException) {
                withContext(NonCancellable) { cancelAndClose(gatt, activeTransferId, events) }
                throw error
            } finally {
                activeTransferId = null
                try {
                    gatt?.disconnect()
                    gatt?.close()
                } catch (_: SecurityException) {
                    // Permission loss is already exposed by the protected operation that detected it.
                }
                events.close()
            }
        }
    }

    @Suppress("MissingPermission")
    private fun connect(device: BluetoothDevice, events: Channel<GattEvent>): BluetoothGatt = try {
        val callback = callback(events)
        device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw HistoryDownloadException.Connection("Could not start the Bluetooth connection.")
    } catch (error: SecurityException) {
        throw HistoryDownloadException.PermissionLost(error)
    }

    @Suppress("MissingPermission")
    private suspend fun awaitConnection(gatt: BluetoothGatt, events: ReceiveChannel<GattEvent>) {
        when (val event = awaitEvent(events, timeouts.connectionMillis, "Connection timed out.")) {
            is GattEvent.Connection -> if (event.status != BluetoothGatt.GATT_SUCCESS || event.newState != BluetoothGatt.STATE_CONNECTED) {
                throw HistoryDownloadException.Connection("Could not connect to the sensor (status ${event.status}).")
            }
            is GattEvent.Disconnected -> throw HistoryDownloadException.Connection("The sensor disconnected while connecting.")
            else -> throw HistoryDownloadException.Protocol("Unexpected GATT callback while connecting.")
        }
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    @Suppress("MissingPermission")
    private suspend fun requestConnectionOptimizations(gatt: BluetoothGatt, events: ReceiveChannel<GattEvent>) {
        if (!gatt.requestMtu(PREFERRED_MTU)) return
        val mtuEvent = try {
            awaitEvent(events, timeouts.operationMillis, "MTU negotiation timed out.")
        } catch (_: HistoryDownloadException.Timeout) {
            return
        }
        if (mtuEvent !is GattEvent.Mtu || mtuEvent.status != BluetoothGatt.GATT_SUCCESS) return
        gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
    }

    @Suppress("MissingPermission")
    private suspend fun discoverServices(gatt: BluetoothGatt, events: ReceiveChannel<GattEvent>) {
        if (!gatt.discoverServices()) throw HistoryDownloadException.Connection("Could not start service discovery.")
        val event = awaitEvent(events, timeouts.operationMillis, "Service discovery timed out.")
        if (event !is GattEvent.Services || event.status != BluetoothGatt.GATT_SUCCESS) {
            throw HistoryDownloadException.Connection("Service discovery failed.")
        }
    }

    @Suppress("MissingPermission")
    private suspend fun configureCccd(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        events: ReceiveChannel<GattEvent>,
    ) {
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
            ?: throw HistoryDownloadException.Unsupported()
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            throw HistoryDownloadException.Connection("Could not enable history updates.")
        }
        val enqueued = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!enqueued) throw HistoryDownloadException.Connection("Could not configure history updates.")
        val event = awaitEvent(events, timeouts.operationMillis, "History update configuration timed out.")
        if (event !is GattEvent.DescriptorWrite || event.descriptor !== descriptor || event.status != BluetoothGatt.GATT_SUCCESS) {
            throw HistoryDownloadException.Connection("History update configuration failed.")
        }
    }

    @Suppress("MissingPermission")
    private suspend fun writeOpen(
        gatt: BluetoothGatt,
        control: BluetoothGattCharacteristic,
        request: HistoryDownloadRequest,
        events: ReceiveChannel<GattEvent>,
    ) {
        val command = when (request) {
            is HistoryDownloadRequest.Full -> SeneHistoryProtocol.encodeOpenFull(request.phoneEpochSeconds)
            is HistoryDownloadRequest.After -> SeneHistoryProtocol.encodeOpenAfter(request.phoneEpochSeconds, request.cursor)
        }
        val enqueued = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(control, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            control.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            control.value = command
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(control)
        }
        if (!enqueued) throw HistoryDownloadException.Connection("Could not send the history request.")
        val event = awaitEvent(events, timeouts.operationMillis, "History request write timed out.")
        if (event !is GattEvent.CharacteristicWrite || event.characteristic !== control || event.status != BluetoothGatt.GATT_SUCCESS) {
            throw HistoryDownloadException.Connection("History request write failed.")
        }
    }

    private suspend fun awaitControlResponse(events: ReceiveChannel<GattEvent>, timeoutMillis: Long): SeneHistoryControlResponse {
        while (true) {
            when (val event = awaitEvent(events, timeoutMillis, "History response timed out.")) {
                is GattEvent.Control -> return decodeControl(event.value)
                is GattEvent.Data -> throw HistoryDownloadException.Protocol("History data arrived before READY or after the final position.")
                is GattEvent.Disconnected -> throw HistoryDownloadException.Connection("The sensor disconnected before responding.")
                else -> Unit
            }
        }
    }

    private fun decodeControl(value: ByteArray): SeneHistoryControlResponse = try {
        SeneHistoryProtocol.decodeControlResponse(value)
    } catch (error: IllegalArgumentException) {
        throw HistoryDownloadException.Protocol("Malformed history control response.", error)
    }

    private fun throwRemoteOrProtocol(response: SeneHistoryControlResponse): Nothing = when (response) {
        is SeneHistoryControlResponse.Busy,
        is SeneHistoryControlResponse.Canceled,
        is SeneHistoryControlResponse.Done,
        is SeneHistoryControlResponse.InvalidRequest,
        is SeneHistoryControlResponse.NotReady,
        is SeneHistoryControlResponse.StaleCursor,
        is SeneHistoryControlResponse.StorageError,
        -> throw HistoryDownloadException.Remote(response)
        is SeneHistoryControlResponse.Empty,
        is SeneHistoryControlResponse.Ready,
        -> throw HistoryDownloadException.Protocol("Unexpected history control response.")
    }

    private suspend fun awaitEvent(events: ReceiveChannel<GattEvent>, timeoutMillis: Long, timeoutMessage: String): GattEvent = try {
        withTimeout(timeoutMillis) { events.receive() }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        throw HistoryDownloadException.Timeout(timeoutMessage)
    }

    @Suppress("MissingPermission")
    private suspend fun cancelAndClose(gatt: BluetoothGatt?, transferId: Int?, events: ReceiveChannel<GattEvent>) {
        if (gatt == null || transferId == null) return
        val service = gatt.getService(SeneHistoryProtocol.serviceUuid) ?: return
        val control = service.getCharacteristic(SeneHistoryProtocol.controlPointUuid) ?: return
        val command = SeneHistoryProtocol.encodeCancel(transferId)
        try {
            val enqueued = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(control, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                control.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                control.value = command
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(control)
            }
            if (enqueued) withTimeout(timeouts.cancelMillis) { events.receive() }
        } catch (_: Exception) {
            // Disconnect is the firmware-supported cancellation fallback.
        }
    }

    private fun ensureConnectPermission() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw HistoryDownloadException.PermissionLost()
        }
    }

    private fun callback(events: Channel<GattEvent>) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            events.trySend(if (newState == BluetoothGatt.STATE_DISCONNECTED) GattEvent.Disconnected(status) else GattEvent.Connection(status, newState))
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) { events.trySend(GattEvent.Services(status)) }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { events.trySend(GattEvent.Mtu(mtu, status)) }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { events.trySend(GattEvent.DescriptorWrite(descriptor, status)) }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { events.trySend(GattEvent.CharacteristicWrite(characteristic, status)) }
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) { events.trySend(eventFor(characteristic, characteristic.value?.copyOf() ?: ByteArray(0))) }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) { events.trySend(eventFor(characteristic, value.copyOf())) }

        private fun eventFor(characteristic: BluetoothGattCharacteristic, value: ByteArray): GattEvent = when (characteristic.uuid) {
            SeneHistoryProtocol.controlPointUuid -> GattEvent.Control(value)
            SeneHistoryProtocol.dataStreamUuid -> GattEvent.Data(value)
            else -> GattEvent.Other
        }
    }

    data class HistoryDownloadTimeouts(
        val connectionMillis: Long = 30_000,
        val operationMillis: Long = 10_000,
        val responseMillis: Long = 10_000,
        val dataInactivityMillis: Long = 10_000,
        val terminalMillis: Long = 10_000,
        val cancelMillis: Long = 1_000,
    )

    private sealed interface GattEvent {
        data class Connection(val status: Int, val newState: Int) : GattEvent
        data class Disconnected(val status: Int) : GattEvent
        data class Services(val status: Int) : GattEvent
        data class Mtu(val mtu: Int, val status: Int) : GattEvent
        data class DescriptorWrite(val descriptor: BluetoothGattDescriptor, val status: Int) : GattEvent
        data class CharacteristicWrite(val characteristic: BluetoothGattCharacteristic, val status: Int) : GattEvent
        data class Control(val value: ByteArray) : GattEvent
        data class Data(val value: ByteArray) : GattEvent
        data object Other : GattEvent
    }

    private companion object {
        const val PREFERRED_MTU = 247
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
