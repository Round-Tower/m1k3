package app.m1k3.ai.assistant.eco

import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.datetime.Clock

/**
 * EcoMetricsRepository - Clean API for Environmental Impact Tracking
 *
 * **Philosophy:**
 * Eco metrics are front and center in 間 AI. This repository provides
 * a simple, ergonomic API for tracking environmental savings from local
 * AI processing vs cloud inference.
 *
 * **Core Features:**
 * - One-line metric recording: `recordMetrics(savings)`
 * - Lifetime statistics: Total environmental impact
 * - Session tracking: Group metrics by conversation
 * - Project tracking: Per-project environmental savings
 * - Daily breakdown: Data for charts and visualization
 * - Network usage: real bytes from downloads + web search (ADR-0006)
 *
 * **Privacy model:**
 * Chat inference rows always record 0 bytes (stay on-device). Network
 * events from HttpModelDownloadManager and WebSearchExecutor record
 * their real bytes here. The "zero-bytes" invariant was retired in
 * ADR-0006 — privacy enforcement moved to `ManifestPrivacyTest`.
 *
 * **Usage Example:**
 * ```kotlin
 * // After AI inference
 * val savings = EcoCalculator.calculateSavings(tokens)
 * repository.recordMetrics(savings, sessionId = chatSession.id)
 *
 * // Get lifetime stats for dashboard
 * val lifetime = repository.getLifetimeStats()
 * println("Water saved: ${EcoCalculator.formatWater(lifetime.totalWaterMl)}")
 * ```
 */
class EcoMetricsRepository(
    private val database: MaDatabase,
) {
    // ==================== Recording Metrics ====================

    /**
     * Record one EcoMetrics row.
     *
     * Accepts both inference rows (from [EcoCalculator.calculateSavings])
     * and network events (from [EcoCalculator.networkEvent]). No privacy
     * precondition — ADR-0006 retired the zero-bytes invariant.
     *
     * @param savings Environmental + byte data from EcoCalculator
     * @param sessionId Optional chat session ID for grouping
     * @param projectId Optional project ID for linking
     */
    fun recordMetrics(
        savings: EcoSavings,
        sessionId: String? = null,
        projectId: String? = null,
    ) {
        val timestamp = Clock.System.now().toEpochMilliseconds()

        database.ecoMetricsQueries.insertEcoMetrics(
            timestamp = timestamp,
            tokens_processed = savings.tokensProcessed.toLong(),
            water_saved_ml = savings.waterSavedMl.toLong(),
            energy_saved_wh = savings.energySavedWh.toLong(),
            co2_prevented_g = savings.co2PreventedG.toLong(),
            bytes_sent = savings.bytesSent.toLong(),
            bytes_received = savings.bytesReceived.toLong(),
            session_id = sessionId,
            project_id = projectId,
        )
    }

    // ==================== Lifetime Statistics ====================

    /**
     * Get lifetime environmental savings across all queries.
     *
     * Returns total tokens processed, water saved, energy saved, CO2 prevented,
     * query count, and first/last query timestamps.
     *
     * @return LifetimeStats or null if no metrics recorded
     */
    fun getLifetimeStats(): LifetimeStats? {
        val result =
            database.ecoMetricsQueries.getLifetimeStats().executeAsOneOrNull()
                ?: return null

        // Check if we have any data
        if (result.total_queries == 0L) {
            return null
        }

        return LifetimeStats(
            totalTokens = result.total_tokens ?: 0L,
            totalWaterMl = result.total_water_ml ?: 0L,
            totalEnergyWh = result.total_energy_wh ?: 0L,
            totalCo2G = result.total_co2_g ?: 0L,
            totalBytesSent = result.total_bytes_sent ?: 0L,
            totalBytesReceived = result.total_bytes_received ?: 0L,
            totalQueries = result.total_queries ?: 0L,
            firstQueryAt = result.first_query_at ?: 0L,
            lastQueryAt = result.last_query_at ?: 0L,
        )
    }

    // ==================== Session Statistics ====================

    /**
     * Get environmental savings grouped by chat session.
     *
     * Useful for showing per-conversation environmental impact.
     * Sessions are ordered by most recent first.
     *
     * @return List of SessionStats, ordered by session end time (desc)
     */
    fun getSessionStats(): List<SessionStats> =
        database.ecoMetricsQueries
            .getSessionStats()
            .executeAsList()
            .map { row ->
                SessionStats(
                    sessionId = row.session_id ?: "unknown",
                    queries = row.queries?.toInt() ?: 0,
                    tokens = row.tokens ?: 0L,
                    waterMl = row.water_ml ?: 0L,
                    energyWh = row.energy_wh ?: 0L,
                    co2G = row.co2_g ?: 0L,
                    sessionStart = row.session_start ?: 0L,
                    sessionEnd = row.session_end ?: 0L,
                )
            }

    // ==================== Project Statistics ====================

    /**
     * Get environmental savings grouped by project.
     *
     * Shows which projects have the highest environmental impact.
     *
     * @return List of ProjectStats
     */
    fun getProjectStats(): List<ProjectStats> =
        database.ecoMetricsQueries
            .getProjectStats()
            .executeAsList()
            .map { row ->
                ProjectStats(
                    projectId = row.project_id ?: "unknown",
                    queries = row.queries?.toInt() ?: 0,
                    tokens = row.tokens ?: 0L,
                    waterMl = row.water_ml ?: 0L,
                    energyWh = row.energy_wh ?: 0L,
                    co2G = row.co2_g ?: 0L,
                )
            }

    // ==================== Daily Statistics ====================

    /**
     * Get daily breakdown of environmental savings.
     *
     * Returns last N days of metrics for charts and visualization.
     *
     * @param days Number of days to retrieve (default: 7)
     * @return List of DailyStats, ordered by date (desc)
     */
    fun getDailyStats(days: Int = 7): List<DailyStats> {
        val cutoffTimestamp =
            Clock.System.now().toEpochMilliseconds() -
                (days * 24 * 60 * 60 * 1000L)

        return database.ecoMetricsQueries
            .getDailyStats(cutoffTimestamp)
            .executeAsList()
            .map { row ->
                DailyStats(
                    date = row.date ?: "",
                    queries = row.queries?.toInt() ?: 0,
                    tokens = row.tokens ?: 0L,
                    waterMl = row.water_ml ?: 0L,
                    energyWh = row.energy_wh ?: 0L,
                    co2G = row.co2_g ?: 0L,
                )
            }
    }

    // ==================== Network Usage ====================

    /**
     * Total real bytes sent + received across all metrics. Sourced from
     * HttpModelDownloadManager (HuggingFace GGUF) and WebSearchExecutor
     * (DuckDuckGo). Chat inference rows contribute 0 (stays on-device).
     *
     * Used by the "Network usage" breakdown on EcoStatsScreen and by any
     * future privacy dashboard.
     */
    fun getTotalNetworkBytes(): NetworkBytes {
        val result = database.ecoMetricsQueries.getTotalNetworkBytes().executeAsOne()
        return NetworkBytes(
            bytesSent = result.total_bytes_sent ?: 0L,
            bytesReceived = result.total_bytes_received ?: 0L,
        )
    }

    // ==================== Average Savings ====================

    /**
     * Get average environmental savings per query.
     *
     * Useful for showing typical impact of a single AI interaction.
     *
     * @return AverageSavings or null if no data
     */
    fun getAverageSavings(): AverageSavings? {
        val result =
            database.ecoMetricsQueries.getAverageSavings().executeAsOneOrNull()
                ?: return null

        return AverageSavings(
            avgTokens = result.avg_tokens ?: 0.0,
            avgWaterMl = result.avg_water_ml ?: 0.0,
            avgEnergyWh = result.avg_energy_wh ?: 0.0,
            avgCo2G = result.avg_co2_g ?: 0.0,
        )
    }

    // ==================== Helper Methods ====================

    /**
     * Get total number of queries recorded.
     *
     * @return Total query count
     */
    fun getRecordCount(): Long = database.ecoMetricsQueries.getRecordCount().executeAsOne()

    /**
     * Delete metrics older than specified timestamp.
     *
     * Useful for data retention policies.
     *
     * @param timestampMillis Delete metrics before this timestamp
     */
    fun deleteOlderThan(timestampMillis: Long) {
        database.ecoMetricsQueries.deleteOlderThan(timestampMillis)
    }

    /**
     * Delete all metrics for a session.
     *
     * @param sessionId Session ID to delete
     */
    fun deleteSession(sessionId: String) {
        database.ecoMetricsQueries.deleteSession(sessionId)
    }

    /**
     * Delete all metrics for a project.
     *
     * @param projectId Project ID to delete
     */
    fun deleteProject(projectId: String) {
        database.ecoMetricsQueries.deleteProject(projectId)
    }
}

// ==================== Data Classes ====================

/**
 * Lifetime environmental savings statistics.
 */
data class LifetimeStats(
    val totalTokens: Long,
    val totalWaterMl: Long,
    val totalEnergyWh: Long,
    val totalCo2G: Long,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val totalQueries: Long,
    val firstQueryAt: Long,
    val lastQueryAt: Long,
)

/**
 * Network bytes summary — real upload / download from
 * HttpModelDownloadManager + WebSearchExecutor. Chat inference rows
 * contribute 0 (stay on-device).
 */
data class NetworkBytes(
    val bytesSent: Long,
    val bytesReceived: Long,
) {
    val total: Long get() = bytesSent + bytesReceived
}

/**
 * Session-based statistics for grouping metrics.
 */
data class SessionStats(
    val sessionId: String,
    val queries: Int,
    val tokens: Long,
    val waterMl: Long,
    val energyWh: Long,
    val co2G: Long,
    val sessionStart: Long,
    val sessionEnd: Long,
)

/**
 * Project-based statistics for grouping metrics.
 */
data class ProjectStats(
    val projectId: String,
    val queries: Int,
    val tokens: Long,
    val waterMl: Long,
    val energyWh: Long,
    val co2G: Long,
)

/**
 * Daily breakdown for charts/graphs.
 */
data class DailyStats(
    val date: String,
    val queries: Int,
    val tokens: Long,
    val waterMl: Long,
    val energyWh: Long,
    val co2G: Long,
)

/**
 * Average savings per query.
 */
data class AverageSavings(
    val avgTokens: Double,
    val avgWaterMl: Double,
    val avgEnergyWh: Double,
    val avgCo2G: Double,
)
