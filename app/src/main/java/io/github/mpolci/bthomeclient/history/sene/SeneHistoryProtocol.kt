package io.github.mpolci.bthomeclient.history.sene

import java.util.UUID
import kotlin.math.abs

/**
 * Android-independent codec for the SENE-A history-download protocol and its
 * persisted logger positions.
 */
object SeneHistoryProtocol {
    val serviceUuid: UUID = UUID.fromString("7f510001-1b15-4f0d-8f6c-7e9c5a1e0000")
    val controlPointUuid: UUID = UUID.fromString("7f510002-1b15-4f0d-8f6c-7e9c5a1e0000")
    val dataStreamUuid: UUID = UUID.fromString("7f510003-1b15-4f0d-8f6c-7e9c5a1e0000")

    const val protocolVersion = 0x01
    const val cursorSize = 10
    const val rawPositionSize = 16
    const val dataPacketHeaderSize = 4
    const val maxPositionCount = 130_560
    const val maxRawTransferBytes = maxPositionCount * rawPositionSize

    const val openFullOpcode = 0x01
    const val openAfterOpcode = 0x02
    const val cancelOpcode = 0x03

    const val readyOpcode = 0x81
    const val emptyOpcode = 0x82
    const val staleCursorOpcode = 0x83
    const val busyOpcode = 0x84
    const val notReadyOpcode = 0x85
    const val invalidRequestOpcode = 0x86
    const val storageErrorOpcode = 0x87
    const val canceledOpcode = 0x88
    const val doneOpcode = 0x89

    const val syncPendingFlag = 0x01
    const val historyTruncatedFlag = 0x02
    const val knownFlagsMask = syncPendingFlag or historyTruncatedFlag

    const val formatVersion = 1
    const val slotsPerGeneration = 255

    const val sampleUnsyncedType = 0x01
    const val sampleUtcType = 0x02
    const val bootType = 0x10
    const val timeSyncType = 0x11

    fun encodeOpenFull(phoneEpochSeconds: Long): ByteArray = ByteArray(6).also { command ->
        command[0] = openFullOpcode.toByte()
        command[1] = protocolVersion.toByte()
        writeUInt32LittleEndian(command, 2, requireUInt32(phoneEpochSeconds, "phoneEpochSeconds"))
    }

    fun encodeOpenAfter(phoneEpochSeconds: Long, cursor: ByteArray): ByteArray {
        requireCursor(cursor)
        return ByteArray(16).also { command ->
            command[0] = openAfterOpcode.toByte()
            command[1] = protocolVersion.toByte()
            writeUInt32LittleEndian(command, 2, requireUInt32(phoneEpochSeconds, "phoneEpochSeconds"))
            cursor.copyInto(command, destinationOffset = 6)
        }
    }

    fun encodeCancel(transferId: Int): ByteArray {
        requireTransferId(transferId, "transferId")
        return byteArrayOf(cancelOpcode.toByte(), protocolVersion.toByte(), transferId.toByte())
    }

    fun decodeControlResponse(message: ByteArray): SeneHistoryControlResponse {
        require(message.size >= 2) { "Control response must contain an opcode and protocol version." }
        require(message[1].unsigned() == protocolVersion) { "Unsupported history protocol version." }

        return when (message[0].unsigned()) {
            readyOpcode -> decodeReady(message)
            emptyOpcode -> decodeEmpty(message)
            staleCursorOpcode -> SeneHistoryControlResponse.StaleCursor.also { requireLength(message, 2) }
            busyOpcode -> SeneHistoryControlResponse.Busy(decodeTransferResponse(message, "BUSY", allowZeroId = true))
            notReadyOpcode -> SeneHistoryControlResponse.NotReady.also { requireLength(message, 2) }
            invalidRequestOpcode -> SeneHistoryControlResponse.InvalidRequest.also { requireLength(message, 2) }
            storageErrorOpcode -> SeneHistoryControlResponse.StorageError(
                decodeTransferResponse(message, "STORAGE_ERROR", allowZeroId = true),
            )
            canceledOpcode -> SeneHistoryControlResponse.Canceled(
                decodeTransferResponse(message, "CANCELED", allowZeroId = false),
            )
            doneOpcode -> SeneHistoryControlResponse.Done(decodeTransferResponse(message, "DONE", allowZeroId = false))
            else -> throw SeneHistoryProtocolException("Unknown history control response opcode 0x%02X.".format(message[0].unsigned()))
        }
    }

    fun decodeDataPacket(packet: ByteArray): SeneHistoryDataPacket {
        require(packet.size >= dataPacketHeaderSize + rawPositionSize) {
            "History data packet must include a header and at least one position."
        }
        require((packet.size - dataPacketHeaderSize) % rawPositionSize == 0) {
            "History data packet ends in a partial position."
        }

        val transferId = packet[0].unsigned()
        requireTransferId(transferId, "data packet transferId")
        return SeneHistoryDataPacket(
            transferId = transferId,
            firstPositionIndex = packet.readUInt24LittleEndian(1),
            rawPositions = packet.copyOfRange(dataPacketHeaderSize, packet.size),
        )
    }

    fun decodeCursor(cursor: ByteArray): SeneHistoryCursor = requireCursor(cursor)

    fun crc16CcittFalse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset <= data.size - length) { "CRC range is outside the input." }
        var crc = 0xFFFF
        for (index in offset until offset + length) {
            crc = crc xor (data[index].unsigned() shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    /**
     * Decodes a completed, contiguous raw transfer in acquisition order. The
     * transport validates packet sequence and count before calling this method.
     */
    fun decodeRawPositions(rawPositions: ByteArray): SeneHistoryRecordDecodeResult {
        require(rawPositions.size % rawPositionSize == 0) { "Raw history data ends in a partial position." }
        require(rawPositions.size <= maxRawTransferBytes) { "Raw history data exceeds the maximum transfer size." }

        val validSamples = mutableListOf<UnresolvedSample>()
        val anchorsBySession = mutableMapOf<Int, MutableList<TimeAnchor>>()
        var session = 0
        var erasedPositionCount = 0
        var invalidPositionCount = 0
        var unsupportedPositionCount = 0

        rawPositions.indices.step(rawPositionSize).forEach { offset ->
            val positionIndex = offset / rawPositionSize
            if (rawPositions.isErasedPosition(offset)) {
                erasedPositionCount += 1
                return@forEach
            }
            if (!rawPositions.hasValidPositionCrcAndPadding(offset)) {
                invalidPositionCount += 1
                return@forEach
            }

            when (val type = rawPositions[offset].unsigned()) {
                bootType -> session += 1
                timeSyncType -> anchorsBySession.getOrPut(session) { mutableListOf() } += TimeAnchor(
                    utcSeconds = rawPositions.readUInt32LittleEndian(offset + 1),
                    monotonicSeconds = rawPositions.readUInt32LittleEndian(offset + 5),
                )
                sampleUnsyncedType,
                sampleUtcType,
                -> {
                    val humidity = rawPositions[offset + 9].unsigned()
                    val battery = rawPositions[offset + 10].unsigned()
                    if (humidity > 100 || battery > 100) {
                        invalidPositionCount += 1
                    } else {
                        validSamples += UnresolvedSample(
                            positionIndex = positionIndex,
                            session = session,
                            utc = type == sampleUtcType,
                            timestampSeconds = rawPositions.readUInt32LittleEndian(offset + 1),
                            pressureHectopascals = rawPositions.readUInt16LittleEndian(offset + 5) / 10.0,
                            temperatureCelsius = rawPositions.readInt16LittleEndian(offset + 7) / 10.0,
                            humidityPercent = humidity,
                            batteryPercent = battery,
                        )
                    }
                }
                else -> unsupportedPositionCount += 1
            }
        }

        var undatedSampleCount = 0
        val samples = validSamples.map { sample ->
            val timestampSeconds = if (sample.utc) {
                sample.timestampSeconds
            } else {
                reconstructUtcSeconds(sample.timestampSeconds, anchorsBySession[sample.session].orEmpty())
            }
            val timeQuality = when {
                sample.utc -> SeneHistoryTimeQuality.DIRECT_UTC
                timestampSeconds != null -> SeneHistoryTimeQuality.RECONSTRUCTED
                else -> SeneHistoryTimeQuality.UNAVAILABLE
            }
            if (timeQuality == SeneHistoryTimeQuality.UNAVAILABLE) undatedSampleCount += 1
            SeneHistorySample(
                positionIndex = sample.positionIndex,
                recordedEpochMillis = timestampSeconds?.toEpochMillis(),
                timeQuality = timeQuality,
                temperatureCelsius = sample.temperatureCelsius,
                humidityPercent = sample.humidityPercent,
                pressureHectopascals = sample.pressureHectopascals,
                batteryPercent = sample.batteryPercent,
            )
        }

        return SeneHistoryRecordDecodeResult(
            samples = samples,
            erasedPositionCount = erasedPositionCount,
            invalidPositionCount = invalidPositionCount,
            unsupportedPositionCount = unsupportedPositionCount,
            undatedSampleCount = undatedSampleCount,
        )
    }

    private fun decodeReady(message: ByteArray): SeneHistoryControlResponse.Ready {
        requireLength(message, 20)
        val transferId = message[2].unsigned()
        requireTransferId(transferId, "READY transferId")
        val flags = decodeFlags(message[3].unsigned())
        val responseFormatVersion = message.readUInt16LittleEndian(4)
        require(responseFormatVersion == formatVersion) { "Unsupported READY record format." }
        val positionCount = message.readUInt32LittleEndian(6)
        require(positionCount in 1L..maxPositionCount.toLong()) { "READY position count is outside the supported range." }
        val finalCursor = decodeCursor(message.copyOfRange(10, 20))
        return SeneHistoryControlResponse.Ready(transferId, flags, responseFormatVersion, positionCount.toInt(), finalCursor)
    }

    private fun decodeEmpty(message: ByteArray): SeneHistoryControlResponse.Empty {
        requireLength(message, 14)
        val flags = decodeFlags(message[2].unsigned())
        require(message[3].unsigned() == 0) { "EMPTY reserved byte must be zero." }
        return SeneHistoryControlResponse.Empty(flags, decodeCursor(message.copyOfRange(4, 14)))
    }

    private fun decodeTransferResponse(message: ByteArray, responseName: String, allowZeroId: Boolean): Int {
        requireLength(message, 3)
        val transferId = message[2].unsigned()
        if (!allowZeroId) requireTransferId(transferId, "$responseName transferId")
        return transferId
    }

    private fun decodeFlags(value: Int): SeneHistoryFlags {
        require(value and knownFlagsMask.inv() == 0) { "History response contains reserved flag bits." }
        return SeneHistoryFlags(
            syncPending = value and syncPendingFlag != 0,
            historyTruncated = value and historyTruncatedFlag != 0,
        )
    }

    private fun requireCursor(cursor: ByteArray): SeneHistoryCursor {
        requireLength(cursor, cursorSize, "Cursor")
        val format = cursor.readUInt16LittleEndian(0)
        require(format == formatVersion) { "Unsupported history cursor format." }
        val slot = cursor.readUInt16LittleEndian(6)
        require(slot in 0..slotsPerGeneration) { "History cursor slot is outside the supported range." }
        val expectedCrc = crc16CcittFalse(cursor, length = 8)
        require(cursor.readUInt16LittleEndian(8) == expectedCrc) { "History cursor CRC is invalid." }
        return SeneHistoryCursor(format, cursor.readUInt32LittleEndian(2), slot, cursor.copyOf())
    }

    private fun requireLength(message: ByteArray, expectedLength: Int, label: String = "Control response") {
        require(message.size == expectedLength) { "$label must be exactly $expectedLength bytes." }
    }

    private fun requireTransferId(transferId: Int, label: String) {
        require(transferId in 1..255) { "$label must be in 1..255." }
    }

    private fun requireUInt32(value: Long, label: String): Long {
        require(value in 0..UINT32_MAX) { "$label must fit the unsigned 32-bit protocol field." }
        return value
    }

    private fun reconstructUtcSeconds(sampleMonotonicSeconds: Long, anchors: List<TimeAnchor>): Long? {
        val anchor = anchors.minByOrNull { abs(it.monotonicSeconds - sampleMonotonicSeconds) } ?: return null
        val reconstructed = anchor.utcSeconds + sampleMonotonicSeconds - anchor.monotonicSeconds
        return reconstructed.takeIf { it in 0..UINT32_MAX }
    }

    private fun Long.toEpochMillis(): Long = Math.multiplyExact(this, MILLIS_PER_SECOND)

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun ByteArray.readUInt16LittleEndian(offset: Int): Int =
        this[offset].unsigned() or (this[offset + 1].unsigned() shl 8)

    private fun ByteArray.readInt16LittleEndian(offset: Int): Int =
        readUInt16LittleEndian(offset).let { if (it and 0x8000 == 0) it else it - 0x10000 }

    private fun ByteArray.readUInt24LittleEndian(offset: Int): Int =
        this[offset].unsigned() or
            (this[offset + 1].unsigned() shl 8) or
            (this[offset + 2].unsigned() shl 16)

    private fun ByteArray.readUInt32LittleEndian(offset: Int): Long =
        this[offset].unsigned().toLong() or
            (this[offset + 1].unsigned().toLong() shl 8) or
            (this[offset + 2].unsigned().toLong() shl 16) or
            (this[offset + 3].unsigned().toLong() shl 24)

    private fun writeUInt32LittleEndian(output: ByteArray, offset: Int, value: Long) {
        output[offset] = value.toByte()
        output[offset + 1] = (value shr 8).toByte()
        output[offset + 2] = (value shr 16).toByte()
        output[offset + 3] = (value shr 24).toByte()
    }

    private fun ByteArray.isErasedPosition(offset: Int): Boolean =
        (offset until offset + rawPositionSize).all { this[it].unsigned() == 0xFF }

    private fun ByteArray.hasValidPositionCrcAndPadding(offset: Int): Boolean =
        this[offset + 13].unsigned() == 0xFF &&
            this[offset + 14].unsigned() == 0xFF &&
            this[offset + 15].unsigned() == 0xFF &&
            readUInt16LittleEndian(offset + 11) == crc16CcittFalse(this, offset, 11)

    private data class TimeAnchor(
        val utcSeconds: Long,
        val monotonicSeconds: Long,
    )

    private data class UnresolvedSample(
        val positionIndex: Int,
        val session: Int,
        val utc: Boolean,
        val timestampSeconds: Long,
        val pressureHectopascals: Double,
        val temperatureCelsius: Double,
        val humidityPercent: Int,
        val batteryPercent: Int,
    )

    private const val UINT32_MAX = 0xFFFF_FFFFL
    private const val MILLIS_PER_SECOND = 1_000L
}

class SeneHistoryProtocolException(message: String) : IllegalArgumentException(message)

class SeneHistoryCursor internal constructor(
    val formatVersion: Int,
    val generation: Long,
    val nextSlot: Int,
    private val encoded: ByteArray,
) {
    fun toByteArray(): ByteArray = encoded.copyOf()
}

data class SeneHistoryFlags(
    val syncPending: Boolean,
    val historyTruncated: Boolean,
)

sealed interface SeneHistoryControlResponse {
    data class Ready(
        val transferId: Int,
        val flags: SeneHistoryFlags,
        val formatVersion: Int,
        val positionCount: Int,
        val finalCursor: SeneHistoryCursor,
    ) : SeneHistoryControlResponse

    data class Empty(
        val flags: SeneHistoryFlags,
        val finalCursor: SeneHistoryCursor,
    ) : SeneHistoryControlResponse

    data object StaleCursor : SeneHistoryControlResponse
    data class Busy(val transferId: Int) : SeneHistoryControlResponse
    data object NotReady : SeneHistoryControlResponse
    data object InvalidRequest : SeneHistoryControlResponse
    data class StorageError(val transferId: Int) : SeneHistoryControlResponse
    data class Canceled(val transferId: Int) : SeneHistoryControlResponse
    data class Done(val transferId: Int) : SeneHistoryControlResponse
}

data class SeneHistoryDataPacket(
    val transferId: Int,
    val firstPositionIndex: Int,
    val rawPositions: ByteArray,
) {
    val positionCount: Int get() = rawPositions.size / SeneHistoryProtocol.rawPositionSize
}

enum class SeneHistoryTimeQuality { DIRECT_UTC, RECONSTRUCTED, UNAVAILABLE }

data class SeneHistorySample(
    val positionIndex: Int,
    val recordedEpochMillis: Long?,
    val timeQuality: SeneHistoryTimeQuality,
    val temperatureCelsius: Double,
    val humidityPercent: Int,
    val pressureHectopascals: Double,
    val batteryPercent: Int,
)

data class SeneHistoryRecordDecodeResult(
    val samples: List<SeneHistorySample>,
    val erasedPositionCount: Int,
    val invalidPositionCount: Int,
    val unsupportedPositionCount: Int,
    val undatedSampleCount: Int,
)
