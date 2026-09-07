package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HistoryUpdateState {
    data object Idle : HistoryUpdateState
    data object Queued : HistoryUpdateState
    data object Connecting : HistoryUpdateState
    data object Discovering : HistoryUpdateState
    data class Downloading(val received: Int, val total: Int) : HistoryUpdateState
    data class Complete(val summary: HistoryDownloadSummary) : HistoryUpdateState
    data class Failed(val error: Throwable) : HistoryUpdateState
}

/** Owns one foreground history session. The supplied scope must use a confined dispatcher. */
class HistorySessionCoordinator(
    private val store: HistoryStore,
    private val scope: CoroutineScope,
) {
    private val mutableUpdates = MutableStateFlow<Map<SensorAddress, HistoryUpdateState>>(emptyMap())
    val updates = mutableUpdates.asStateFlow()
    private val mutableSessionId = MutableStateFlow(0L)
    val sessionId = mutableSessionId.asStateFlow()
    private val transfers = Mutex()
    private val requested = mutableMapOf<SensorAddress, Request>()
    private val queue = linkedMapOf<SensorAddress, Request>()
    private var active = false
    private var nextRequestId = 0L
    private var worker: Job? = null
    private var activeRequest: Request? = null

    fun begin() {
        scope.launch {
            if (active) return@launch
            active = true
            mutableSessionId.value += 1
            requested.clear()
            queue.clear()
            mutableUpdates.value = emptyMap()
        }
    }

    fun end() {
        scope.launch {
            if (!active) return@launch
            active = false
            queue.clear()
            requested.clear()
            worker?.cancel()
            worker = null
            activeRequest = null
            mutableUpdates.value = mutableUpdates.value.mapValues { (_, state) ->
                if (state is HistoryUpdateState.Complete || state is HistoryUpdateState.Failed) state else HistoryUpdateState.Idle
            }
        }
    }

    fun request(address: SensorAddress, registrationId: String, retry: Boolean = false) {
        scope.launch {
            if (!active) return@launch
            val previous = requested[address]
            if (previous?.registrationId == registrationId && (!retry || mutableUpdates.value[address] !is HistoryUpdateState.Failed)) return@launch
            val request = Request(address, registrationId, mutableSessionId.value, ++nextRequestId)
            requested[address] = request
            queue.remove(address)
            if (activeRequest?.address == address) {
                worker?.cancel()
                worker = null
                activeRequest = null
            }
            queue[address] = request
            publish(address, HistoryUpdateState.Queued)
            startWorker()
        }
    }

    private fun startWorker() {
        if (worker?.isActive == true || queue.isEmpty()) return
        val session = mutableSessionId.value
        worker = scope.launch(start = CoroutineStart.LAZY) {
            val thisWorker = currentCoroutineContext()[Job]!!
            try {
                while (active && mutableSessionId.value == session && queue.isNotEmpty()) {
                    val item = queue.entries.first().value
                    queue.remove(item.address)
                    activeRequest = item
                    try {
                        // A canceled transfer may still be closing GATT. Wait for that cleanup before the next one.
                        transfers.withLock {
                            if (!isCurrent(item)) return@withLock
                            publish(item.address, HistoryUpdateState.Connecting)
                            store.download(item.address, item.registrationId).collect { event ->
                                if (thisWorker.isActive && isCurrent(item)) {
                                    publish(item.address, when (event) {
                                        HistoryRepositoryEvent.Connecting -> HistoryUpdateState.Connecting
                                        HistoryRepositoryEvent.Discovering -> HistoryUpdateState.Discovering
                                        is HistoryRepositoryEvent.Downloading -> HistoryUpdateState.Downloading(event.receivedPositions, event.totalPositions)
                                        is HistoryRepositoryEvent.Complete -> HistoryUpdateState.Complete(event.summary)
                                    })
                                }
                            }
                            if (thisWorker.isActive && isCurrent(item) && mutableUpdates.value[item.address] !is HistoryUpdateState.Complete) {
                                publish(item.address, HistoryUpdateState.Failed(HistoryDownloadException.Protocol("History transfer ended without a completion result.")))
                            }
                        }
                    } catch (error: CancellationException) {
                        if (isCurrent(item)) publish(item.address, HistoryUpdateState.Idle)
                        throw error
                    } catch (error: Exception) {
                        if (thisWorker.isActive && isCurrent(item)) publish(item.address, HistoryUpdateState.Failed(error))
                    } finally {
                        if (activeRequest == item) activeRequest = null
                    }
                }
            } finally {
                if (worker === thisWorker) worker = null
            }
        }
        worker?.start()
    }

    private fun isCurrent(request: Request) = active && mutableSessionId.value == request.session && requested[request.address] == request
    private fun publish(address: SensorAddress, state: HistoryUpdateState) { mutableUpdates.value = mutableUpdates.value + (address to state) }
    private data class Request(val address: SensorAddress, val registrationId: String, val session: Long, val id: Long)
}
