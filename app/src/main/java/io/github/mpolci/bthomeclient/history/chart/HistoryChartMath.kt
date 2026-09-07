package io.github.mpolci.bthomeclient.history.chart

import kotlin.math.abs
import kotlin.math.max

/**
 * One recorded observation. Identity and acquisition order survive time sorting and reduction.
 * [acquisitionSequence] preserves source positions, including unrepresented intervening records.
 */
data class HistoryChartPoint(
    val id: Long,
    val epochMillis: Long,
    val acquisitionSequence: Long,
    val value: Double,
)

/** Inclusive dated coverage; a singleton has equal endpoints. */
data class HistoryTimeRange(val startMillis: Long, val endMillis: Long) {
    init {
        require(startMillis <= endMillis)
    }

    /** Durations beyond the signed millisecond range saturate instead of wrapping. */
    val spanMillis: Long
        get() = positiveDistance(endMillis, startMillis).coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
}

/** The fixed cursor is at [centerMillis]. Padding allows either coverage endpoint to reach it. */
data class HistoryViewport(val centerMillis: Long, val spanMillis: Long) {
    init {
        require(spanMillis >= MIN_SPAN_MILLIS)
    }

    val startMillis: Long get() = saturatedAdd(centerMillis, -(spanMillis / 2))
    val endMillis: Long get() = saturatedAdd(centerMillis, spanMillis - spanMillis / 2)

    /** Returns an unclipped plot coordinate; 0.5 is always the fixed cursor. */
    fun fractionAt(epochMillis: Long): Double =
        0.5 + signedDistance(epochMillis, centerMillis) / spanMillis.toDouble()

    fun pan(deltaMillis: Long, coverage: HistoryTimeRange? = null): HistoryViewport =
        copy(centerMillis = saturatedAdd(centerMillis, deltaMillis)).clamp(coverage)

    /** A factor above one zooms in. Invalid gesture factors leave the viewport unchanged. */
    fun zoom(factor: Double, coverage: HistoryTimeRange? = null): HistoryViewport {
        if (!factor.isFinite() || factor <= 0.0) return clamp(coverage)
        return copy(spanMillis = (spanMillis.toDouble() / factor).toLong().coerceAtLeast(MIN_SPAN_MILLIS))
            .clamp(coverage)
    }

    /** Bounds the cursor, not the padded edges, without moving an in-coverage center. */
    fun clamp(coverage: HistoryTimeRange?): HistoryViewport {
        if (coverage == null) return this
        val maximumSpan = coverage.spanMillis.takeIf { it > 0L } ?: SINGLETON_SPAN_MILLIS
        return copy(
            centerMillis = centerMillis.coerceIn(coverage.startMillis, coverage.endMillis),
            spanMillis = spanMillis.coerceIn(MIN_SPAN_MILLIS, max(MIN_SPAN_MILLIS, maximumSpan)),
        )
    }

    companion object {
        const val MIN_SPAN_MILLIS = 1_000L
        const val SINGLETON_SPAN_MILLIS = 60_000L

        fun fit(coverage: HistoryTimeRange?): HistoryViewport {
            if (coverage == null) return HistoryViewport(0L, SINGLETON_SPAN_MILLIS)
            // This midpoint cannot overflow, including ranges spanning the sign boundary.
            val center = (coverage.startMillis and coverage.endMillis) +
                ((coverage.startMillis xor coverage.endMillis) shr 1)
            return HistoryViewport(
                centerMillis = center,
                spanMillis = (coverage.spanMillis.takeIf { it > 0L } ?: SINGLETON_SPAN_MILLIS)
                    .coerceAtLeast(MIN_SPAN_MILLIS),
            )
        }
    }
}

/** A chronological observation with an explicit break before disconnected data. */
data class HistoryChartEntry(val point: HistoryChartPoint, val startsSegment: Boolean)

/** Finite display bounds. A constant series receives padding without changing its observations. */
data class HistoryValueRange(val minimum: Double, val maximum: Double) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum < maximum)
    }

    /** Normalization avoids overflowing when the two bounds have opposite, extreme signs. */
    fun fractionAt(value: Double): Double {
        if (!value.isFinite()) return 0.5
        val scale = max(abs(minimum), abs(maximum)).coerceAtLeast(java.lang.Double.MIN_NORMAL)
        return ((value / scale - minimum / scale) / (maximum / scale - minimum / scale))
            .coerceIn(0.0, 1.0)
    }

    companion object {
        fun fromPoints(points: List<HistoryChartPoint>): HistoryValueRange {
            val finite = points.asSequence().map { it.value }.filter { it.isFinite() }
            val minimum = finite.minOrNull() ?: return HistoryValueRange(0.0, 1.0)
            val maximum = finite.maxOrNull() ?: minimum
            if (minimum < maximum) return HistoryValueRange(minimum, maximum)
            val padding = max(abs(minimum) * 0.05, 1.0)
            return HistoryValueRange(
                (minimum - padding).coerceAtLeast(-Double.MAX_VALUE),
                (maximum + padding).coerceAtMost(Double.MAX_VALUE),
            )
        }
    }
}

/** Pure helpers; no interpolation, inferred cadence, or synthetic observations are introduced. */
object HistoryChartMath {
    /**
     * Detects time regressions in acquisition order, then sorts all finite observations by time.
     * Connections never cross regression runs, discarded non-finite values, source-position
     * discontinuities, or an explicitly supplied maximum time gap. A sequence increase greater
     * than one conservatively marks unrepresented source positions, including erased/invalid
     * records and boot/time-sync markers; it is not an inferred sampling cadence.
     * Duplicate timestamps remain separate observations, ordered by
     * acquisition sequence and then ID. No gap threshold is inferred from irregular sampling.
     */
    fun prepare(
        points: List<HistoryChartPoint>,
        maxConnectionGapMillis: Long? = null,
    ): List<HistoryChartEntry> {
        require(maxConnectionGapMillis == null || maxConnectionGapMillis >= 0L)
        var run = 0
        var previous: HistoryChartPoint? = null
        val runs = points.sortedWith(acquisitionOrder).mapNotNull { point ->
            if (!point.value.isFinite()) {
                run++
                previous = null
                null
            } else {
                if (previous?.let {
                        point.epochMillis < it.epochMillis ||
                            (point.acquisitionSequence > it.acquisitionSequence &&
                                positiveDistance(point.acquisitionSequence, it.acquisitionSequence) > 1uL)
                    } == true) run++
                previous = point
                point to run
            }
        }.sortedWith(compareBy<Pair<HistoryChartPoint, Int>> { it.first.epochMillis }
            .thenBy { it.first.acquisitionSequence }.thenBy { it.first.id })
        return runs.mapIndexed { index, (point, segment) ->
            val preceding = runs.getOrNull(index - 1)
            HistoryChartEntry(
                point = point,
                startsSegment = preceding == null || preceding.second != segment ||
                    (maxConnectionGapMillis != null &&
                        positiveDistance(point.epochMillis, preceding.first.epochMillis) >
                        maxConnectionGapMillis.toULong()),
            )
        }
    }

    fun coverage(points: List<HistoryChartPoint>): HistoryTimeRange? {
        val finite = points.filter { it.value.isFinite() }
        if (finite.isEmpty()) return null
        return HistoryTimeRange(finite.minOf { it.epochMillis }, finite.maxOf { it.epochMillis })
    }

    /** Finds an actual finite sample. Equal distances choose earlier acquisition, then lower ID. */
    fun nearestSample(points: List<HistoryChartPoint>, epochMillis: Long): HistoryChartPoint? =
        points.asSequence().filter { it.value.isFinite() }.minWithOrNull { first, second ->
            val distanceComparison = distance(first.epochMillis, epochMillis)
                .compareTo(distance(second.epochMillis, epochMillis))
            if (distanceComparison != 0) distanceComparison else acquisitionOrder.compare(first, second)
        }

    /**
     * Retains first, last, minimum and maximum actual observations in each pixel bin of each
     * connected segment. One observation just outside either visible edge is retained where
     * needed to clip a real connecting line. Segments and extrema may require more than four
     * points per pixel in total; preserving gaps takes precedence over a fixed output count.
     * Input must be the chronological result of [prepare].
     */
    fun reduce(
        entries: List<HistoryChartEntry>,
        viewport: HistoryViewport,
        pixelWidth: Int,
    ): List<HistoryChartEntry> {
        if (entries.isEmpty() || pixelWidth <= 0) return emptyList()
        val result = mutableListOf<HistoryChartEntry>()
        var segmentStart = 0
        while (segmentStart < entries.size) {
            var segmentEnd = segmentStart + 1
            while (segmentEnd < entries.size && !entries[segmentEnd].startsSegment) segmentEnd++
            var visibleStart = segmentStart
            while (visibleStart < segmentEnd && viewport.fractionAt(entries[visibleStart].point.epochMillis) < 0.0) {
                visibleStart++
            }
            var visibleEnd = visibleStart
            while (visibleEnd < segmentEnd && viewport.fractionAt(entries[visibleEnd].point.epochMillis) <= 1.0) {
                visibleEnd++
            }
            // Include the adjoining endpoints only if a real segment intersects the viewport.
            if (visibleStart < segmentEnd && visibleEnd > segmentStart) {
                val start = max(segmentStart, visibleStart - 1)
                val end = minOf(segmentEnd, visibleEnd + 1)
                var binStart = start
                var firstOutput = true
                while (binStart < end) {
                    val bin = pixelBin(entries[binStart].point, viewport, pixelWidth)
                    var binEnd = binStart + 1
                    var minimum = binStart
                    var maximum = binStart
                    while (binEnd < end && pixelBin(entries[binEnd].point, viewport, pixelWidth) == bin) {
                        if (entries[binEnd].point.value < entries[minimum].point.value) minimum = binEnd
                        if (entries[binEnd].point.value > entries[maximum].point.value) maximum = binEnd
                        binEnd++
                    }
                    for (index in listOf(binStart, minimum, maximum, binEnd - 1).distinct().sorted()) {
                        result += entries[index].copy(startsSegment = firstOutput)
                        firstOutput = false
                    }
                    binStart = binEnd
                }
            }
            segmentStart = segmentEnd
        }
        return result
    }

    private fun pixelBin(point: HistoryChartPoint, viewport: HistoryViewport, pixelWidth: Int): Int {
        val fraction = viewport.fractionAt(point.epochMillis)
        return when {
            fraction < 0.0 -> -1
            fraction > 1.0 -> pixelWidth
            else -> (fraction * pixelWidth).toInt().coerceAtMost(pixelWidth - 1)
        }
    }

    private val acquisitionOrder = compareBy<HistoryChartPoint> { it.acquisitionSequence }.thenBy { it.id }
}

private fun positiveDistance(later: Long, earlier: Long): ULong = later.toULong() - earlier.toULong()

private fun distance(first: Long, second: Long): ULong =
    if (first >= second) positiveDistance(first, second) else positiveDistance(second, first)

private fun signedDistance(first: Long, second: Long): Double =
    if (first >= second) positiveDistance(first, second).toDouble() else -positiveDistance(second, first).toDouble()

private fun saturatedAdd(value: Long, delta: Long): Long {
    val result = value + delta
    return if (((value xor result) and (delta xor result)) < 0L) {
        if (delta >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
    } else result
}
