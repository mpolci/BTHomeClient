package io.github.mpolci.bthomeclient.data.repository

import io.github.mpolci.bthomeclient.ble.HistoryDownloadException
import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistorySessionCoordinatorTest {
    private val first = SensorAddress.require("AA:BB:CC:DD:EE:01")
    private val second = SensorAddress.require("AA:BB:CC:DD:EE:02")
    private val third = SensorAddress.require("AA:BB:CC:DD:EE:03")
    private val summary = HistoryDownloadSummary(2, 0, 0, 0, false, false)

    @Test fun queueIsSerializedAndDuplicateRequestsDoNotRestartTransfers() = runTest {
        val store = Store()
        val a = store.prepare(first)
        val b = store.prepare(second)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "a")
        coordinator.request(second, "b")
        coordinator.request(first, "a")
        runCurrent()
        assertEquals(listOf(first to "a"), store.started)
        assertEquals(HistoryUpdateState.Queued, coordinator.updates.value[second])
        a.events.send(HistoryRepositoryEvent.Discovering)
        runCurrent()
        assertEquals(HistoryUpdateState.Discovering, coordinator.updates.value[first])
        a.events.send(HistoryRepositoryEvent.Downloading(3, 9))
        runCurrent()
        assertEquals(HistoryUpdateState.Downloading(3, 9), coordinator.updates.value[first])
        a.finish()
        runCurrent()
        assertEquals(listOf(first to "a", second to "b"), store.started)
        assertEquals(HistoryUpdateState.Complete(summary), coordinator.updates.value[first])
        coordinator.request(first, "a", retry = true)
        b.finish()
        runCurrent()
        assertEquals(2, store.started.size)
        assertEquals(1, store.maxActive)
        assertEquals(0, store.active)
    }

    @Test fun failureNeedsExplicitRetryAndDoesNotAbortOtherSensors() = runTest {
        val store = Store()
        val a = store.prepare(first)
        val b = store.prepare(second)
        val retry = store.prepare(first)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "a")
        coordinator.request(second, "b")
        runCurrent()
        val error = HistoryDownloadException.Connection("Not nearby")
        a.events.close(error)
        runCurrent()
        assertSame(error, (coordinator.updates.value[first] as HistoryUpdateState.Failed).error)
        assertEquals(2, store.started.size)
        coordinator.request(first, "a")
        runCurrent()
        assertEquals(HistoryUpdateState.Failed(error), coordinator.updates.value[first])
        coordinator.request(first, "a", retry = true)
        coordinator.request(first, "a", retry = true)
        runCurrent()
        assertEquals(HistoryUpdateState.Queued, coordinator.updates.value[first])
        b.finish()
        runCurrent()
        assertEquals(3, store.started.size)
        retry.finish()
        runCurrent()
        assertEquals(HistoryUpdateState.Complete(summary), coordinator.updates.value[first])
    }

    @Test fun endCancelsActiveAndQueuedWorkWithoutLateUpdates() = runTest {
        val store = Store()
        val a = store.prepare(first)
        store.prepare(second)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "a")
        coordinator.request(second, "b")
        runCurrent()
        coordinator.end()
        runCurrent()
        a.events.trySend(HistoryRepositoryEvent.Complete(summary))
        runCurrent()
        assertEquals(HistoryUpdateState.Idle, coordinator.updates.value[first])
        assertEquals(HistoryUpdateState.Idle, coordinator.updates.value[second])
        assertEquals(1, store.started.size)
        assertEquals(0, store.active)
        coordinator.request(second, "b")
        runCurrent()
        assertEquals(1, store.started.size)
    }

    @Test fun beginIsIdempotentAndNewSessionAllowsNewUpdateAfterCleanup() = runTest {
        val store = Store()
        val old = store.prepare(first)
        val next = store.prepare(first)
        old.cleanup = CompletableDeferred()
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.request(first, "a")
        runCurrent()
        assertEquals(0, store.started.size)
        coordinator.begin()
        coordinator.begin()
        coordinator.request(first, "a")
        runCurrent()
        assertEquals(1L, coordinator.sessionId.value)
        coordinator.end()
        coordinator.begin()
        coordinator.request(first, "a")
        runCurrent()
        assertEquals(2L, coordinator.sessionId.value)
        assertEquals(1, store.started.size)
        old.cleanup!!.complete(Unit)
        runCurrent()
        assertEquals(2, store.started.size)
        assertEquals(1, store.maxActive)
        next.finish()
        runCurrent()
        assertEquals(HistoryUpdateState.Complete(summary), coordinator.updates.value[first])
    }

    @Test fun newRegistrationReplacesQueuedIdentityBeforeDownload() = runTest {
        val store = Store()
        val a = store.prepare(first)
        val b = store.prepare(second)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "a")
        coordinator.request(second, "old-registration")
        coordinator.request(second, "new-registration")
        runCurrent()
        a.finish()
        runCurrent()
        assertEquals(listOf(first to "a", second to "new-registration"), store.started)
        b.finish()
        runCurrent()
        assertEquals(HistoryUpdateState.Complete(summary), coordinator.updates.value[second])
    }

    @Test fun newRegistrationCancelsObsoleteActiveTransferAndKeepsOtherQueueItems() = runTest {
        val store = Store()
        val old = store.prepare(first)
        old.cleanup = CompletableDeferred()
        val b = store.prepare(second)
        val replacement = store.prepare(first)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "old-registration")
        coordinator.request(second, "b")
        runCurrent()
        coordinator.request(first, "new-registration")
        runCurrent()
        assertEquals(HistoryUpdateState.Queued, coordinator.updates.value[first])
        assertEquals(1, store.started.size)
        old.cleanup!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(first to "old-registration", second to "b"), store.started)
        b.finish()
        runCurrent()
        assertEquals(first to "new-registration", store.started.last())
        replacement.finish()
        runCurrent()
        assertEquals(HistoryUpdateState.Complete(summary), coordinator.updates.value[first])
        assertEquals(1, store.maxActive)
    }

    @Test fun terminalResultsRemainAfterEndAndClearAtNextBegin() = runTest {
        val store = Store()
        val a = store.prepare(first)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "a")
        runCurrent()
        a.finish()
        runCurrent()
        coordinator.end()
        runCurrent()
        assertEquals(HistoryUpdateState.Complete(summary), coordinator.updates.value[first])
        coordinator.begin()
        runCurrent()
        assertTrue(coordinator.updates.value.isEmpty())
    }

    @Test fun incompleteFlowIsFailureAndRemainingQueueContinues() = runTest {
        val store = Store()
        val a = store.prepare(first)
        val b = store.prepare(third)
        val coordinator = HistorySessionCoordinator(store, backgroundScope)
        coordinator.begin()
        coordinator.request(first, "a")
        coordinator.request(third, "c")
        runCurrent()
        a.events.close()
        runCurrent()
        assertTrue((coordinator.updates.value[first] as HistoryUpdateState.Failed).error is HistoryDownloadException.Protocol)
        assertEquals(third to "c", store.started.last())
        b.finish()
        runCurrent()
    }

    private inner class Transfer {
        val events = Channel<HistoryRepositoryEvent>(Channel.UNLIMITED)
        var cleanup: CompletableDeferred<Unit>? = null
        fun finish() {
            events.trySend(HistoryRepositoryEvent.Complete(summary)).getOrThrow()
            events.close()
        }
    }

    private inner class Store : HistoryStore {
        val started = mutableListOf<Pair<SensorAddress, String?>>()
        private val prepared = mutableMapOf<SensorAddress, ArrayDeque<Transfer>>()
        var active = 0
        var maxActive = 0
        fun prepare(address: SensorAddress) = Transfer().also { prepared.getOrPut(address) { ArrayDeque() }.addLast(it) }
        override fun observeSamples(address: SensorAddress): Flow<List<DownloadedHistorySampleEntity>> = flowOf(emptyList())
        override fun observeSyncState(address: SensorAddress): Flow<HistorySyncStateEntity?> = flowOf(null)
        override fun download(address: SensorAddress, expectedRegistrationId: String?): Flow<HistoryRepositoryEvent> = flow {
            val transfer = prepared.getValue(address).removeFirst()
            started += address to expectedRegistrationId
            active++
            maxActive = maxOf(maxActive, active)
            try {
                emit(HistoryRepositoryEvent.Connecting)
                for (event in transfer.events) emit(event)
            } finally {
                withContext(NonCancellable) { transfer.cleanup?.await() }
                active--
            }
        }
    }
}
