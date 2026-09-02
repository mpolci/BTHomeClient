package io.github.mpolci.bthomeclient.history.sene

/** Validates and accumulates a single READY-announced history stream. */
class SeneHistoryTransferAccumulator(
    private val transferId: Int,
    val positionCount: Int,
) {
    private val rawPositions = ByteArray(positionCount * SeneHistoryProtocol.rawPositionSize)
    private var receivedPositions = 0

    init {
        require(transferId in 1..255)
        require(positionCount in 1..SeneHistoryProtocol.maxPositionCount)
    }

    fun append(packet: SeneHistoryDataPacket): Int {
        require(packet.transferId == transferId) { "History data packet has the wrong transfer ID." }
        require(packet.firstPositionIndex == receivedPositions) { "History data packet is not contiguous." }
        require(receivedPositions + packet.positionCount <= positionCount) { "History data packet exceeds the announced position count." }
        packet.rawPositions.copyInto(rawPositions, receivedPositions * SeneHistoryProtocol.rawPositionSize)
        receivedPositions += packet.positionCount
        return receivedPositions
    }

    fun complete(doneTransferId: Int): ByteArray {
        require(doneTransferId == transferId) { "DONE has the wrong transfer ID." }
        require(receivedPositions == positionCount) { "DONE arrived before all announced positions." }
        return rawPositions.copyOf()
    }
}
