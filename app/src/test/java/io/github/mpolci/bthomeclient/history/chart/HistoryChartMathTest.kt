package io.github.mpolci.bthomeclient.history.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryChartMathTest {
    @Test
    fun `fits empty singleton and multi-month coverage`() {
        assertEquals(HistoryViewport(0L, 60_000L), HistoryViewport.fit(null))
        assertEquals(HistoryViewport(42L, 60_000L), HistoryViewport.fit(HistoryTimeRange(42L, 42L)))
        val start = 1_700_000_000_000L
        val duration = 180L * 24 * 60 * 60 * 1_000

        val viewport = HistoryViewport.fit(HistoryTimeRange(start, start + duration))

        assertEquals(start, viewport.startMillis)
        assertEquals(start + duration, viewport.endMillis)
        assertEquals(0.5, viewport.fractionAt(viewport.centerMillis), 0.0)
    }

    @Test
    fun `pan allows first and last samples to reach the fixed cursor`() {
        val coverage = HistoryTimeRange(10_000L, 30_000L)
        val viewport = HistoryViewport.fit(coverage)

        val first = viewport.pan(-100_000L, coverage)
        val last = viewport.pan(100_000L, coverage)

        assertEquals(10_000L, first.centerMillis)
        assertEquals(0L, first.startMillis)
        assertEquals(0.5, first.fractionAt(coverage.startMillis), 0.0)
        assertEquals(30_000L, last.centerMillis)
        assertEquals(40_000L, last.endMillis)
        assertEquals(0.5, last.fractionAt(coverage.endMillis), 0.0)
    }

    @Test
    fun `centered zoom preserves cursor and respects coverage and minimum span`() {
        val coverage = HistoryTimeRange(0L, 100_000L)
        val viewport = HistoryViewport(23_456L, 40_000L)

        assertEquals(HistoryViewport(23_456L, 20_000L), viewport.zoom(2.0, coverage))
        assertEquals(HistoryViewport(23_456L, 1_000L), viewport.zoom(Double.MAX_VALUE, coverage))
        assertEquals(HistoryViewport(23_456L, 100_000L), viewport.zoom(Double.MIN_VALUE, coverage))
        for (factor in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(viewport, viewport.zoom(factor, coverage))
        }
        assertEquals(1_000L, HistoryViewport.fit(HistoryTimeRange(100L, 200L)).spanMillis)
    }

    @Test
    fun `time arithmetic saturates without wrapping and retains nearby timestamp precision`() {
        val positive = HistoryViewport(Long.MAX_VALUE - 10L, 1_000L)
        val negative = HistoryViewport(Long.MIN_VALUE + 10L, 1_000L)

        assertEquals(Long.MAX_VALUE, positive.endMillis)
        assertEquals(Long.MIN_VALUE, negative.startMillis)
        assertEquals(Long.MAX_VALUE, positive.pan(Long.MAX_VALUE).centerMillis)
        assertEquals(Long.MIN_VALUE, negative.pan(Long.MIN_VALUE).centerMillis)
        assertEquals(0.501, positive.fractionAt(Long.MAX_VALUE - 9L), 0.0000001)
        val coverage = HistoryTimeRange(Long.MIN_VALUE, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, coverage.spanMillis)
        assertEquals(-1L, HistoryViewport.fit(coverage).centerMillis)
    }

    @Test
    fun `chronological preparation preserves duplicates and acquisition identity`() {
        val samples = listOf(point(3, 200), point(2, 100), point(1, 100))

        val prepared = HistoryChartMath.prepare(samples)

        assertEquals(listOf(1L, 2L, 3L), prepared.map { it.point.id })
        assertEquals(listOf(true, false, false), prepared.map { it.startsSegment })
        assertEquals(samples.toSet(), prepared.map { it.point }.toSet())
    }

    @Test
    fun `clock regression creates disconnected runs even when chronological runs overlap`() {
        val samples = listOf(point(1, 100), point(2, 300), point(3, 200), point(4, 400))

        val prepared = HistoryChartMath.prepare(samples)

        assertEquals(listOf(1L, 3L, 2L, 4L), prepared.map { it.point.id })
        assertTrue(prepared.all { it.startsSegment })
    }

    @Test
    fun `explicit gap breaks lines while irregular cadence alone does not`() {
        val samples = listOf(point(1, 0), point(2, 100), point(3, 1_000), point(4, 1_100))

        assertEquals(
            listOf(true, false, false, false),
            HistoryChartMath.prepare(samples).map { it.startsSegment },
        )
        assertEquals(
            listOf(true, false, true, false),
            HistoryChartMath.prepare(samples, maxConnectionGapMillis = 100L).map { it.startsSegment },
        )
        assertEquals(
            listOf(true, true),
            HistoryChartMath.prepare(
                listOf(point(1, Long.MIN_VALUE), point(2, Long.MAX_VALUE)),
                maxConnectionGapMillis = Long.MAX_VALUE,
            ).map { it.startsSegment },
        )
    }

    @Test
    fun `source position holes break lines without inferring a time cadence`() {
        val samples = listOf(
            point(1, 0, sequence = 10), point(2, 100, sequence = 11),
            point(3, 100_000, sequence = 12), point(4, 100_001, sequence = 14),
            point(5, 900_000, sequence = 15),
        )

        val prepared = HistoryChartMath.prepare(samples)

        assertEquals(listOf(true, false, false, true, false), prepared.map { it.startsSegment })
        val reduced = HistoryChartMath.reduce(prepared, HistoryViewport.fit(HistoryChartMath.coverage(samples)), 1)
        assertEquals(listOf(1L, 4L), reduced.filter { it.startsSegment }.map { it.point.id })
        assertEquals(
            listOf(true, true),
            HistoryChartMath.prepare(listOf(
                point(1, 0, sequence = Long.MIN_VALUE), point(2, 1, sequence = Long.MAX_VALUE),
            )).map { it.startsSegment },
        )
    }

    @Test
    fun `non-finite observations are excluded without bridging across them`() {
        val samples = listOf(
            point(1, 0), point(2, 10, Double.NaN), point(3, 20),
            point(4, 30, Double.POSITIVE_INFINITY), point(5, 40),
        )

        val prepared = HistoryChartMath.prepare(samples)

        assertEquals(listOf(1L, 3L, 5L), prepared.map { it.point.id })
        assertTrue(prepared.all { it.startsSegment })
        assertEquals(HistoryTimeRange(0L, 40L), HistoryChartMath.coverage(samples))
        assertNull(HistoryChartMath.coverage(listOf(point(1, 0, Double.NaN))))
    }

    @Test
    fun `nearest returns real asynchronous observations and deterministic acquisition ties`() {
        val first = listOf(point(1, 100), point(2, 300))
        val second = listOf(point(3, 180), point(4, 290))

        assertEquals(first[0], HistoryChartMath.nearestSample(first, 200L))
        assertEquals(second[0], HistoryChartMath.nearestSample(second, 200L))
        assertEquals(point(8, 100, sequence = 1), HistoryChartMath.nearestSample(
            listOf(point(9, 100, sequence = 1), point(8, 100, sequence = 1), point(1, 100, sequence = 2)),
            100L,
        ))
        assertNull(HistoryChartMath.nearestSample(emptyList(), 100L))
        assertNull(HistoryChartMath.nearestSample(listOf(point(1, 0, Double.NaN)), 100L))
    }

    @Test
    fun `nearest compares extreme timestamps without overflow or loss of small distances`() {
        val samples = listOf(point(1, Long.MIN_VALUE), point(2, Long.MAX_VALUE))

        assertEquals(samples[1], HistoryChartMath.nearestSample(samples, 0L))
        assertEquals(samples[0], HistoryChartMath.nearestSample(samples, -1L))
        assertEquals(point(2, Long.MAX_VALUE - 1L), HistoryChartMath.nearestSample(
            listOf(point(1, Long.MAX_VALUE - 2L), point(2, Long.MAX_VALUE - 1L)),
            Long.MAX_VALUE,
        ))
    }

    @Test
    fun `pixel reduction keeps first last and both extrema in observation order`() {
        val samples = listOf(
            point(1, 100, 3.0), point(2, 200, 2.0), point(3, 300, -90.0),
            point(4, 400, 110.0), point(5, 500, 4.0), point(6, 600, 5.0),
        )

        val reduced = HistoryChartMath.reduce(HistoryChartMath.prepare(samples), HistoryViewport(500L, 1_000L), 1)

        assertEquals(listOf(1L, 3L, 4L, 6L), reduced.map { it.point.id })
        assertEquals(listOf(true, false, false, false), reduced.map { it.startsSegment })
        assertTrue(reduced.all { it.point in samples })
    }

    @Test
    fun `pixel reduction retains independent gaps and duplicate timestamp extrema`() {
        val samples = listOf(
            point(1, 100, 5.0), point(2, 100, -20.0), point(3, 100, 40.0), point(4, 100, 6.0),
            point(5, 900, 7.0), point(6, 900, 50.0),
        )

        val prepared = HistoryChartMath.prepare(samples, maxConnectionGapMillis = 100L)
        val reduced = HistoryChartMath.reduce(prepared, HistoryViewport(500L, 1_000L), 1)

        assertEquals(samples, reduced.map { it.point })
        assertEquals(listOf(true, false, false, false, true, false), reduced.map { it.startsSegment })
    }

    @Test
    fun `reduction clips with actual adjacent samples and does not bridge missing segments`() {
        val viewport = HistoryViewport(1_000L, 1_000L)
        val samples = listOf(point(1, 0), point(2, 100), point(3, 2_000), point(4, 3_000))

        val continuous = HistoryChartMath.reduce(HistoryChartMath.prepare(samples), viewport, 10)
        val disconnected = HistoryChartMath.reduce(
            HistoryChartMath.prepare(samples, maxConnectionGapMillis = 500L), viewport, 10,
        )

        assertEquals(listOf(2L, 3L), continuous.map { it.point.id })
        assertTrue(continuous.first().startsSegment)
        assertFalse(continuous.last().startsSegment)
        assertTrue(disconnected.isEmpty())
    }

    @Test
    fun `long series reduction is bounded by pixel bins while preserving all global extrema`() {
        val samples = (0 until 100_000).map { index ->
            point(index.toLong(), index.toLong() * 60_000L, when (index) {
                12_345 -> -1_000.0
                87_654 -> 1_000.0
                else -> 20.0
            })
        }
        val reduced = HistoryChartMath.reduce(
            HistoryChartMath.prepare(samples), HistoryViewport.fit(HistoryChartMath.coverage(samples)), 300,
        )

        assertTrue(reduced.size <= 300 * 4)
        assertEquals(samples.first(), reduced.first().point)
        assertEquals(samples.last(), reduced.last().point)
        assertEquals(-1_000.0, reduced.minOf { it.point.value }, 0.0)
        assertEquals(1_000.0, reduced.maxOf { it.point.value }, 0.0)
    }

    @Test
    fun `empty and singleton reduction are readable and safe at zero width`() {
        val viewport = HistoryViewport(0L, 1_000L)
        val prepared = HistoryChartMath.prepare(listOf(point(1, 0)))

        assertTrue(HistoryChartMath.reduce(emptyList(), viewport, 100).isEmpty())
        assertTrue(HistoryChartMath.reduce(prepared, viewport, 0).isEmpty())
        assertEquals(prepared, HistoryChartMath.reduce(prepared, viewport, 100))
    }

    @Test
    fun `value ranges remain finite for empty constant subnormal and extreme data`() {
        assertEquals(HistoryValueRange(0.0, 1.0), HistoryValueRange.fromPoints(emptyList()))
        assertEquals(HistoryValueRange(19.0, 21.0), HistoryValueRange.fromPoints(listOf(point(1, 0, 20.0))))
        for (constant in listOf(0.0, Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE)) {
            val range = HistoryValueRange.fromPoints(listOf(point(1, 0, constant)))
            assertTrue(range.minimum.isFinite() && range.maximum.isFinite())
            assertTrue(range.minimum < range.maximum)
            assertTrue(range.fractionAt(constant).isFinite())
        }
        val extreme = HistoryValueRange(-Double.MAX_VALUE, Double.MAX_VALUE)
        assertEquals(0.0, extreme.fractionAt(-Double.MAX_VALUE), 0.0)
        assertEquals(0.5, extreme.fractionAt(0.0), 0.0)
        assertEquals(1.0, extreme.fractionAt(Double.MAX_VALUE), 0.0)
        val subnormal = HistoryValueRange(0.0, Double.MIN_VALUE)
        assertEquals(1.0, subnormal.fractionAt(Double.MIN_VALUE), 0.0)
    }

    private fun point(id: Long, time: Long, value: Double = id.toDouble(), sequence: Long = id) =
        HistoryChartPoint(id = id, epochMillis = time, acquisitionSequence = sequence, value = value)
}
