package io.github.mpolci.bthomeclient.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GattOperationEventTest {
    @Test fun mtuReportedForAnAlreadyOpenLinkDoesNotAnswerTheNextOperation() = runTest {
        val events = Channel<GattEvent>(Channel.UNLIMITED)
        events.trySend(GattEvent.Mtu(247, 0))
        events.trySend(GattEvent.Other)
        events.trySend(GattEvent.Services(0))
        assertEquals(GattEvent.Services(0), awaitOperationEvent(events, 1_000, "timed out"))
    }

    @Test fun disconnectionIsReturnedToTheCaller() = runTest {
        val events = Channel<GattEvent>(Channel.UNLIMITED)
        events.trySend(GattEvent.Mtu(247, 0))
        events.trySend(GattEvent.Disconnected(8))
        assertEquals(GattEvent.Disconnected(8), awaitOperationEvent(events, 1_000, "timed out"))
    }

    @Test fun skippedCallbacksDoNotExtendTheTimeout() = runTest {
        val events = Channel<GattEvent>(Channel.UNLIMITED)
        events.trySend(GattEvent.Mtu(247, 0))
        val error = runCatching { awaitOperationEvent(events, 1_000, "Service discovery timed out.") }.exceptionOrNull()
        assertTrue(error is HistoryDownloadException.Timeout)
        assertEquals("Service discovery timed out.", error?.message)
    }
}
