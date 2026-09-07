package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.mpolci.bthomeclient.history.chart.HistoryChartEntry
import io.github.mpolci.bthomeclient.history.chart.HistoryChartMath
import io.github.mpolci.bthomeclient.history.chart.HistoryChartPoint
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryValueRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Actual historical observations on a shared, fixed-center time axis.
 * [plotPadding] reserves measured control/readout space while keeping the Canvas and gesture
 * surface full size. Axes and observations remain inside the unobscured plotting area.
 */
@Composable
fun HistoryChart(
    points: List<HistoryChartPoint>,
    viewport: HistoryViewport,
    coverage: HistoryTimeRange?,
    unit: String,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    comparisonPoints: List<HistoryChartPoint> = emptyList(),
    onViewportChange: (HistoryViewport) -> Unit = {},
    plotPadding: PaddingValues = PaddingValues(0.dp),
) {
    val primary = remember(points) { HistoryChartMath.prepare(points) }
    val comparison = remember(comparisonPoints) { HistoryChartMath.prepare(comparisonPoints) }
    val valueRange = remember(points, comparisonPoints) { HistoryValueRange.fromPoints(points + comparisonPoints) }
    val zone = ZoneId.systemDefault()
    val axisFormatter = remember(zone, viewport.spanMillis) {
        val pattern = when {
            viewport.spanMillis < 60_000L -> "d MMM yy\nHH:mm:ss.SSS z"
            viewport.spanMillis < 3_600_000L -> "d MMM yy\nHH:mm:ss z"
            else -> "d MMM yy\nHH:mm z"
        }
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(zone)
    }
    val spokenFormatter = remember(zone) { DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.getDefault()).withZone(zone) }
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val tickValues = listOf(valueRange.maximum, valueRange.minimum / 2.0 + valueRange.maximum / 2.0, valueRange.minimum)
    val tickLabels = tickValues.map { "${formatChartValue(it)} $unit" }
    val tickLayouts = tickLabels.map { textMeasurer.measure(it, style = textStyle) }
    val axisWidth = tickLayouts.maxOf { it.size.width }.toFloat()
    val latestViewport by rememberUpdatedState(viewport)
    val latestCoverage by rememberUpdatedState(coverage)
    val latestOnViewportChange by rememberUpdatedState(onViewportChange)
    val gestureAxisWidth by rememberUpdatedState(axisWidth)
    val summary = if (primary.isEmpty() && comparison.isEmpty()) {
        "No dated samples. Unit: $unit. Timezone: ${zone.id}."
    } else {
        "${primary.size} recorded samples" +
            (if (comparison.isNotEmpty()) ", ${comparison.size} comparison samples" else "") +
            ". Scale ${formatChartValue(valueRange.minimum)} to ${formatChartValue(valueRange.maximum)} $unit. " +
            "Timezone: ${zone.id}."
    }
    // Canvas requires a preferred size; a minimum alone can leave its hit target at zero height.
    val chartModifier = modifier.fillMaxWidth().height(180.dp)
        .testTag("history_chart")
        .semantics {
            contentDescription = "History chart. $summary"
            stateDescription = "Center: ${spokenFormatter.format(Instant.ofEpochMilli(viewport.centerMillis))}. " +
                "From ${spokenFormatter.format(Instant.ofEpochMilli(viewport.startMillis))} " +
                "to ${spokenFormatter.format(Instant.ofEpochMilli(viewport.endMillis))}."
            if (interactive) {
                customActions = listOf(
                    CustomAccessibilityAction("Earlier") {
                        onViewportChange(viewport.pan(-(viewport.spanMillis / 4), coverage)); true
                    },
                    CustomAccessibilityAction("Later") {
                        onViewportChange(viewport.pan(viewport.spanMillis / 4, coverage)); true
                    },
                    CustomAccessibilityAction("Zoom in") { onViewportChange(viewport.zoom(2.0, coverage)); true },
                    CustomAccessibilityAction("Zoom out") { onViewportChange(viewport.zoom(0.5, coverage)); true },
                    CustomAccessibilityAction("All history") { onViewportChange(HistoryViewport.fit(coverage)); true },
                )
            }
        }
    Canvas(
        modifier = if (interactive) chartModifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var anchor = latestViewport
                var requested = anchor
                var lastDispatched = anchor
                var panPixels = 0f
                var zoomFactor = 1.0
                var zooming = false
                var pastTouchSlop = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) break
                    if (!zooming && event.changes.count { it.pressed } > 1) {
                        // Keep the current cursor fixed throughout a two-finger gesture,
                        // including events where both fingers translate without changing scale.
                        zooming = true
                        anchor = requested
                        zoomFactor = 1.0
                        pastTouchSlop = false
                    }
                    if (zooming && event.changes.count { it.pressed && it.previousPressed } > 1) {
                        zoomFactor *= event.calculateZoom().toDouble()
                        val motion = abs(1.0 - zoomFactor) * event.calculateCentroidSize(useCurrent = false)
                        if (pastTouchSlop || motion > viewConfiguration.touchSlop) {
                            pastTouchSlop = true
                            requested = anchor.zoom(zoomFactor, latestCoverage)
                        }
                    } else if (!zooming) {
                        panPixels += event.calculatePan().x
                        if (pastTouchSlop || abs(panPixels) > viewConfiguration.touchSlop) {
                            pastTouchSlop = true
                            val plotWidth = (size.width - minOf(gestureAxisWidth + 12.dp.toPx(), size.width * .4f) - 8.dp.toPx())
                                .coerceAtLeast(1f)
                            requested = anchor.pan((-panPixels / plotWidth * anchor.spanMillis.toDouble()).toLong(), latestCoverage)
                        }
                    }
                    if (pastTouchSlop) {
                        // Accumulating against this gesture's anchor also preserves all movement
                        // when several pointer events arrive before the parent recomposes.
                        if (requested != lastDispatched) {
                            latestOnViewportChange(requested)
                            lastDispatched = requested
                        }
                        event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        } else chartModifier,
    ) {
        val left = minOf(axisWidth + 12.dp.toPx(), size.width * 0.4f)
        val right = max(left + 1f, size.width - 8.dp.toPx())
        val startLabel = textMeasurer.measure(axisFormatter.format(Instant.ofEpochMilli(viewport.startMillis)), style = textStyle)
        val endLabel = textMeasurer.measure(axisFormatter.format(Instant.ofEpochMilli(viewport.endMillis)), style = textStyle)
        val top = plotPadding.calculateTopPadding().toPx() + max(8.dp.toPx(), tickLayouts.maxOf { it.size.height }.toFloat() / 2f)
        val bottom = max(top + 1f, size.height - plotPadding.calculateBottomPadding().toPx() - max(startLabel.size.height, endLabel.size.height) - 10.dp.toPx())
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)
        for (index in tickLabels.indices) {
            val y = top + height * index / 2f
            drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val label = textMeasurer.measure(
                tickLabels[index], style = textStyle, maxLines = 1,
                constraints = Constraints(maxWidth = (left - 6.dp.toPx()).toInt().coerceAtLeast(1)),
            )
            drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
        }
        drawText(startLabel, topLeft = Offset(left, bottom + 6.dp.toPx()))
        if (startLabel.size.width + endLabel.size.width + 8.dp.toPx() < width) {
            drawText(endLabel, topLeft = Offset(right - endLabel.size.width, bottom + 6.dp.toPx()))
        }
        clipRect(left = left - 3.dp.toPx(), top = top - 3.dp.toPx(), right = right + 3.dp.toPx(), bottom = bottom + 3.dp.toPx()) {
            drawHistorySeries(
                HistoryChartMath.reduce(primary, viewport, width.toInt()), viewport, valueRange,
                left, top, width, height, colors.primary, dashed = false,
            )
            drawHistorySeries(
                HistoryChartMath.reduce(comparison, viewport, width.toInt()), viewport, valueRange,
                left, top, width, height, colors.tertiary, dashed = true,
            )
            if (interactive) {
                drawLine(
                    colors.onSurface.copy(alpha = 0.65f),
                    Offset(left + width / 2f, top), Offset(left + width / 2f, bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
        if (primary.isEmpty() && comparison.isEmpty()) {
            val emptyLabel = textMeasurer.measure("No dated samples", style = textStyle)
            drawText(emptyLabel, topLeft = Offset(left + max(0f, (width - emptyLabel.size.width) / 2f), top + height / 2f))
        }
    }
}

private fun DrawScope.drawHistorySeries(
    entries: List<HistoryChartEntry>,
    viewport: HistoryViewport,
    valueRange: HistoryValueRange,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    dashed: Boolean,
) {
    val path = Path()
    val coordinates = entries.map { entry ->
        Offset(
            left + viewport.fractionAt(entry.point.epochMillis).toFloat() * width,
            top + (1.0 - valueRange.fractionAt(entry.point.value)).toFloat() * height,
        )
    }
    entries.forEachIndexed { index, entry ->
        val position = coordinates[index]
        if (entry.startsSegment || index == 0) path.moveTo(position.x, position.y)
        else path.lineTo(position.x, position.y)
    }
    drawPath(path, color, style = Stroke(
        width = 2.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())) else null,
    ))
    entries.forEachIndexed { index, entry ->
        val isolated = (entry.startsSegment || index == 0) &&
            (index == entries.lastIndex || entries[index + 1].startsSegment)
        if (isolated || entries.size <= 40) {
            drawCircle(color, radius = if (isolated) 3.dp.toPx() else 2.dp.toPx(), center = coordinates[index])
        }
    }
}

internal fun formatChartValue(value: Double): String = when {
    value == 0.0 -> "0"
    abs(value) >= 100_000.0 || abs(value) < 0.01 -> String.format(Locale.getDefault(), "%.2g", value)
    else -> String.format(Locale.getDefault(), "%.1f", value)
}
