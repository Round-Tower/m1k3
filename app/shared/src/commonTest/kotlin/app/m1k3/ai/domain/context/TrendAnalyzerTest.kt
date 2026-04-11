package app.m1k3.ai.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for TrendAnalyzer — detects patterns in user context over time.
 *
 * M1K3 doesn't just know what happened today. He knows the TRAJECTORY.
 * "Your screen time is up 30% this week." "Sleep improving — 7.5h avg."
 * The villain sees ALL the patterns.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.85 | context: trend analysis engine
 */
class TrendAnalyzerTest {

    private val analyzer = TrendAnalyzer()

    // --- Moving average ---

    @Test
    fun movingAverageOfSingleValue() {
        val avg = analyzer.movingAverage(listOf(7.0), window = 3)
        assertEquals(1, avg.size)
        assertEquals(7.0, avg[0], 0.01)
    }

    @Test
    fun movingAverageSmooths() {
        val values = listOf(6.0, 7.0, 8.0, 7.0, 6.0)
        val avg = analyzer.movingAverage(values, window = 3)
        assertEquals(5, avg.size)
        // Trailing window: avg[2] = (6+7+8)/3 = 7.0
        assertEquals(7.0, avg[2], 0.01)
        // avg[4] = (8+7+6)/3 = 7.0
        assertEquals(7.0, avg[4], 0.01)
    }

    // --- Week-over-week comparison ---

    @Test
    fun weekOverWeekDetectsIncrease() {
        val thisWeek = listOf(8.0, 7.5, 8.0, 7.0, 8.5, 7.5, 8.0) // avg ~7.8
        val lastWeek = listOf(6.0, 6.5, 6.0, 5.5, 6.5, 6.0, 6.0) // avg ~6.1
        val change = analyzer.weekOverWeekChange(thisWeek, lastWeek)
        assertTrue(change > 0.2, "Should detect >20% increase, got $change")
    }

    @Test
    fun weekOverWeekDetectsDecrease() {
        val thisWeek = listOf(3.0, 3.5, 3.0, 2.5, 3.5)
        val lastWeek = listOf(5.0, 5.5, 5.0, 4.5, 5.5)
        val change = analyzer.weekOverWeekChange(thisWeek, lastWeek)
        assertTrue(change < -0.2, "Should detect decrease, got $change")
    }

    @Test
    fun weekOverWeekHandlesEmptyLists() {
        assertEquals(0.0, analyzer.weekOverWeekChange(emptyList(), emptyList()), 0.01)
    }

    // --- Trend insight generation ---

    @Test
    fun generatesSleepTrendInsight() {
        val snapshots = listOf(
            TrendDataPoint(value = 7.5, timestamp = day(1)),
            TrendDataPoint(value = 7.0, timestamp = day(2)),
            TrendDataPoint(value = 8.0, timestamp = day(3)),
            TrendDataPoint(value = 7.5, timestamp = day(4)),
            TrendDataPoint(value = 8.0, timestamp = day(5)),
            TrendDataPoint(value = 7.0, timestamp = day(6)),
            TrendDataPoint(value = 7.5, timestamp = day(7)),
        )
        val insight = analyzer.analyzeTrend("sleep_hours", snapshots)
        assertTrue(insight.summary.isNotBlank(), "Should produce a summary")
        assertTrue(insight.averageValue > 7.0, "Average should be > 7h")
    }

    @Test
    fun detectsAnomalyInScreenTime() {
        // Stable ~180min pattern, then massive spike — need enough stable data for low std dev
        val snapshots = listOf(
            TrendDataPoint(value = 180.0, timestamp = day(1)),
            TrendDataPoint(value = 185.0, timestamp = day(2)),
            TrendDataPoint(value = 178.0, timestamp = day(3)),
            TrendDataPoint(value = 182.0, timestamp = day(4)),
            TrendDataPoint(value = 180.0, timestamp = day(5)),
            TrendDataPoint(value = 183.0, timestamp = day(6)),
            TrendDataPoint(value = 600.0, timestamp = day(7)), // Anomaly!
        )
        val insight = analyzer.analyzeTrend("screen_time_minutes", snapshots)
        assertTrue(insight.hasAnomaly, "Should detect the screen time spike")
    }

    @Test
    fun noAnomalyInStableData() {
        val snapshots = listOf(
            TrendDataPoint(value = 100.0, timestamp = day(1)),
            TrendDataPoint(value = 105.0, timestamp = day(2)),
            TrendDataPoint(value = 98.0, timestamp = day(3)),
            TrendDataPoint(value = 102.0, timestamp = day(4)),
        )
        val insight = analyzer.analyzeTrend("steps", snapshots)
        assertTrue(!insight.hasAnomaly, "Stable data should have no anomaly")
    }

    @Test
    fun emptyDataProducesNeutralInsight() {
        val insight = analyzer.analyzeTrend("sleep", emptyList())
        assertEquals(TrendDirection.STABLE, insight.direction)
        assertTrue(insight.summary.contains("no data", ignoreCase = true))
    }

    @Test
    fun trendDirectionDetectsUpward() {
        val snapshots = (1..7).map { TrendDataPoint(value = it * 10.0, timestamp = day(it)) }
        val insight = analyzer.analyzeTrend("steps", snapshots)
        assertEquals(TrendDirection.UP, insight.direction)
    }

    @Test
    fun trendDirectionDetectsDownward() {
        val snapshots = (1..7).map { TrendDataPoint(value = (8 - it) * 10.0, timestamp = day(it)) }
        val insight = analyzer.analyzeTrend("screen_time", snapshots)
        assertEquals(TrendDirection.DOWN, insight.direction)
    }

    // Helper: day offset in millis
    private fun day(n: Int): Long = n * 86_400_000L
}
