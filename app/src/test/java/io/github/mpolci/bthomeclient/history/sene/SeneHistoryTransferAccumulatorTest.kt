package io.github.mpolci.bthomeclient.history.sene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SeneHistoryTransferAccumulatorTest {
    @Test
    fun `accepts contiguous packets and completes only at the announced count`() {
        val accumulator = SeneHistoryTransferAccumulator(7, 2)
        val first = packet(7, 0, 0x11)
        val second = packet(7, 1, 0x22)

        assertEquals(1, accumulator.append(first))
        assertEquals(2, accumulator.append(second))
        assertArrayEquals(first.rawPositions + second.rawPositions, accumulator.complete(7))
    }

    @Test
    fun `rejects wrong IDs gaps overflow premature done and duplicates`() {
        val accumulator = SeneHistoryTransferAccumulator(7, 1)

        assertThrows(IllegalArgumentException::class.java) { accumulator.append(packet(8, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { accumulator.append(packet(7, 1, 0)) }
        assertThrows(IllegalArgumentException::class.java) { accumulator.complete(7) }

        accumulator.append(packet(7, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { accumulator.append(packet(7, 1, 0)) }
        assertThrows(IllegalArgumentException::class.java) { accumulator.complete(8) }
    }

    private fun packet(transferId: Int, index: Int, marker: Int) = SeneHistoryDataPacket(
        transferId = transferId,
        firstPositionIndex = index,
        rawPositions = ByteArray(SeneHistoryProtocol.rawPositionSize) { marker.toByte() },
    )
}
