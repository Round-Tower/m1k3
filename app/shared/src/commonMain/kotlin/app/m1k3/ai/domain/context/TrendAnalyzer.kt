package app.m1k3.ai.domain.context

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Trend analysis engine for user context patterns.
 *
 * Detects: direction (up/down/stable), week-over-week changes,
 * anomalies (>2 std dev), and generates human-readable summaries.
 *
 * Pure Kotlin — no platform dependencies, fully testable.
 * Trend insights become part of M1K3's system prompt context
 * and get embedded as memories for RAG retrieval.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.85 | context: trend detection
 */
class TrendAnalyzer {

    /**
     * Analyze a time-series of data points for trends and anomalies.
     *
     * @param metric Name of the metric (e.g. "sleep_hours", "screen_time_minutes")
     * @param dataPoints Time-ordered data points
     * @return TrendInsight with direction, average, anomaly detection, and summary
     */
    fun analyzeTrend(metric: String, dataPoints: List<TrendDataPoint>): TrendInsight {
        if (dataPoints.isEmpty()) {
            return TrendInsight(
                metric = metric,
                direction = TrendDirection.STABLE,
                averageValue = 0.0,
                changePercent = 0.0,
                hasAnomaly = false,
                summary = "No data for $metric trend analysis yet."
            )
        }

        val values = dataPoints.map { it.value }
        val avg = values.average()
        val stdDev = standardDeviation(values)

        // Detect direction via linear regression slope
        val direction = detectDirection(values)

        // Detect anomalies (>2 standard deviations from mean)
        val hasAnomaly = stdDev > 0 && values.any { abs(it - avg) > 2 * stdDev }

        // Week-over-week change (if enough data)
        val changePercent = if (values.size >= 4) {
            val midpoint = values.size / 2
            val recentHalf = values.drop(midpoint)
            val olderHalf = values.take(midpoint)
            weekOverWeekChange(recentHalf, olderHalf)
        } else 0.0

        val summary = buildSummary(metric, avg, direction, changePercent, hasAnomaly)

        return TrendInsight(
            metric = metric,
            direction = direction,
            averageValue = avg,
            changePercent = changePercent,
            hasAnomaly = hasAnomaly,
            summary = summary
        )
    }

    /**
     * Calculate moving average with given window size.
     * Returns same-length list (partial windows at edges use available data).
     */
    fun movingAverage(values: List<Double>, window: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val w = window.coerceAtLeast(1)
        return values.indices.map { i ->
            val start = (i - w + 1).coerceAtLeast(0)
            values.subList(start, i + 1).average()
        }
    }

    /**
     * Week-over-week percentage change between two periods.
     * Returns fractional change (0.3 = 30% increase, -0.2 = 20% decrease).
     */
    fun weekOverWeekChange(thisWeek: List<Double>, lastWeek: List<Double>): Double {
        if (thisWeek.isEmpty() || lastWeek.isEmpty()) return 0.0
        val thisAvg = thisWeek.average()
        val lastAvg = lastWeek.average()
        if (lastAvg == 0.0) return 0.0
        return (thisAvg - lastAvg) / lastAvg
    }

    private fun detectDirection(values: List<Double>): TrendDirection {
        if (values.size < 3) return TrendDirection.STABLE

        // Simple linear regression slope
        val n = values.size.toDouble()
        val xMean = (n - 1) / 2.0
        val yMean = values.average()

        var numerator = 0.0
        var denominator = 0.0
        values.forEachIndexed { i, y ->
            val xDiff = i - xMean
            numerator += xDiff * (y - yMean)
            denominator += xDiff * xDiff
        }

        if (denominator == 0.0) return TrendDirection.STABLE
        val slope = numerator / denominator

        // Normalize slope relative to mean to detect meaningful change
        val normalizedSlope = if (yMean != 0.0) slope / yMean else 0.0

        return when {
            normalizedSlope > 0.05 -> TrendDirection.UP
            normalizedSlope < -0.05 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance)
    }

    private fun buildSummary(
        metric: String,
        avg: Double,
        direction: TrendDirection,
        changePercent: Double,
        hasAnomaly: Boolean
    ): String = buildString {
        val label = metric.replace("_", " ")
        val directionWord = when (direction) {
            TrendDirection.UP -> "trending up"
            TrendDirection.DOWN -> "trending down"
            TrendDirection.STABLE -> "stable"
        }

        append("${label.replaceFirstChar { it.uppercase() }}: $directionWord")
        append(", avg ${"%.1f".format(avg)}")

        if (abs(changePercent) > 0.05) {
            val pct = "${"%.0f".format(abs(changePercent) * 100)}%"
            append(if (changePercent > 0) " (up $pct recently)" else " (down $pct recently)")
        }

        if (hasAnomaly) {
            append(" ⚠️ unusual spike detected")
        }
    }
}

/** A single data point in a time series */
data class TrendDataPoint(
    val value: Double,
    val timestamp: Long
)

/** Direction of a trend */
enum class TrendDirection { UP, DOWN, STABLE }

/** Result of trend analysis for a single metric */
data class TrendInsight(
    val metric: String,
    val direction: TrendDirection,
    val averageValue: Double,
    val changePercent: Double,
    val hasAnomaly: Boolean,
    val summary: String
)
