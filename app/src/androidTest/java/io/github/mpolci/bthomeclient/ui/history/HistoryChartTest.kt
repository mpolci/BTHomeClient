package io.github.mpolci.bthomeclient.ui.history

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.history.chart.HistoryChartPoint
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport
import io.github.mpolci.bthomeclient.ui.theme.BTHomeClientTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryChartTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyChartHasReadableAccessibleState() {
        compose.setContent {
            BTHomeClientTheme {
                HistoryChart(emptyList(), HistoryViewport.fit(null), null, "°C", Modifier.height(180.dp))
            }
        }

        compose.onNodeWithTag("history_chart").assertIsDisplayed()
            .assertContentDescriptionContains("No dated samples", substring = true)
    }

    @Test
    fun comparisonDescribesActualSampleCountsAndCommonUnit() {
        compose.setContent {
            BTHomeClientTheme {
                HistoryChart(
                    samples, HistoryViewport.fit(coverage), coverage, "°C",
                    comparisonPoints = listOf(HistoryChartPoint(5, 2_000L, 5, 18.0)),
                )
            }
        }

        compose.onNodeWithTag("history_chart")
            .assertContentDescriptionContains("2 recorded samples, 1 comparison samples", substring = true)
            .assertContentDescriptionContains("°C", substring = true)
            .assertContentDescriptionContains("Timezone:", substring = true)
    }

    @Test
    fun accessiblePanAndZoomKeepTheFixedCenterAndAllowEndpointSelection() {
        var viewport by mutableStateOf(HistoryViewport(5_000L, 10_000L))
        compose.setContent {
            BTHomeClientTheme {
                HistoryChart(samples, viewport, coverage, "°C", interactive = true, onViewportChange = { viewport = it })
            }
        }

        performChartAction("Zoom in")
        compose.runOnIdle { assertEquals(HistoryViewport(5_000L, 5_000L), viewport) }
        repeat(4) { performChartAction("Earlier") }
        compose.runOnIdle { assertEquals(0L, viewport.centerMillis) }
        performChartAction("All history")
        compose.runOnIdle { assertEquals(HistoryViewport.fit(coverage), viewport) }
    }

    @Test
    fun draggingMovesTheTimelineAndKeepsItsSpan() {
        var viewport by mutableStateOf(HistoryViewport(5_000L, 4_000L))
        var changes = 0
        compose.setContent {
            BTHomeClientTheme {
                HistoryChart(samples, viewport, coverage, "°C", interactive = true, onViewportChange = { changes++; viewport = it })
            }
        }

        val chart = compose.onNodeWithTag("history_chart")
        val bounds = chart.fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Chart bounds $bounds within root $rootBounds", bounds.width > 0f && bounds.height > 0f)
        chart.assertIsDisplayed().performTouchInput {
            swipe(Offset(width * .75f, height * .55f), Offset(width * .25f, height * .55f), durationMillis = 320L)
        }

        compose.runOnIdle {
            assertTrue("Expected gesture callbacks; received $changes", changes > 1)
            assertTrue("Pan did not accumulate movement: $viewport", viewport.centerMillis > 6_000L)
            assertTrue(viewport.centerMillis <= coverage.endMillis)
            assertEquals(4_000L, viewport.spanMillis)
        }
    }

    @Test
    fun pinchChangesSpanWithoutMovingTheCenter() {
        var viewport by mutableStateOf(HistoryViewport(5_000L, 8_000L))
        var changes = 0
        compose.setContent {
            BTHomeClientTheme {
                HistoryChart(samples, viewport, coverage, "°C", interactive = true, onViewportChange = { changes++; viewport = it })
            }
        }

        val chart = compose.onNodeWithTag("history_chart")
        val bounds = chart.fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Chart bounds $bounds within root $rootBounds", bounds.width > 0f && bounds.height > 0f)
        chart.assertIsDisplayed().performTouchInput {
            down(0, Offset(width * .4f, height * .55f))
            down(1, Offset(width * .6f, height * .55f))
        }
        compose.onNodeWithTag("history_chart").performTouchInput {
            repeat(8) { step ->
                val delta = (step + 1) * .025f
                updatePointerTo(0, Offset(width * (.4f - delta), height * .55f))
                updatePointerTo(1, Offset(width * (.6f + delta), height * .55f))
                move(delayMillis = 32L)
            }
            up(0)
            up(1)
        }

        compose.runOnIdle {
            assertEquals(5_000L, viewport.centerMillis)
            assertTrue("Expected gesture callbacks; received $changes", changes > 1)
            assertTrue("Pinch did not accumulate scale: $viewport", viewport.spanMillis < 4_000L)
        }
    }

    @Test
    fun movingTwoFingersTogetherKeepsTheFixedCursorInPlace() {
        var viewport by mutableStateOf(HistoryViewport(5_000L, 8_000L))
        compose.setContent {
            HistoryChart(samples, viewport, coverage, "°C", interactive = true, onViewportChange = { viewport = it })
        }
        compose.onNodeWithTag("history_chart").performTouchInput {
            down(0, Offset(width * .3f, height * .5f))
            down(1, Offset(width * .5f, height * .5f))
            repeat(8) { step ->
                val delta = (step + 1) * .025f
                updatePointerTo(0, Offset(width * (.3f + delta), height * .5f))
                updatePointerTo(1, Offset(width * (.5f + delta), height * .5f))
                move(delayMillis = 32L)
            }
            up(0)
            up(1)
        }
        compose.runOnIdle { assertEquals(HistoryViewport(5_000L, 8_000L), viewport) }
    }

    private fun performChartAction(label: String) {
        val actions = compose.onNodeWithTag("history_chart").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.single { it.label == label }.action()) }
        compose.waitForIdle()
    }

    private val coverage = HistoryTimeRange(0L, 10_000L)
    private val samples = listOf(HistoryChartPoint(1, 0L, 1, 20.0), HistoryChartPoint(2, 10_000L, 2, 21.0))
}
