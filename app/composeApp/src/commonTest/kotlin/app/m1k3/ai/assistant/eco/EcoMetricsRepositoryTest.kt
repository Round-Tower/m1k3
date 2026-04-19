package app.m1k3.ai.assistant.eco

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * TDD RED Phase: EcoMetricsRepository Tests
 *
 * Defines the clean, easy-to-use API for eco metrics tracking.
 * Eco metrics should be front and center, with simple methods for:
 * - Recording metrics after each AI query
 * - Retrieving lifetime environmental savings
 * - Getting daily/session breakdowns for visualization
 * - Privacy verification (0 bytes transmitted)
 *
 * Test Coverage:
 * - Recording metrics with automatic timestamp
 * - Lifetime statistics aggregation
 * - Session-based tracking
 * - Project-based tracking
 * - Daily breakdown for charts
 * - Privacy enforcement validation
 * - Edge cases (no data, multiple sessions)
 */
class EcoMetricsRepositoryTest {
    // ==================== Recording Metrics Tests ====================

    @Test
    fun `recordMetrics saves eco savings to database`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        val savings =
            EcoSavings(
                tokensProcessed = 100,
                waterSavedMl = 120,
                energySavedWh = 3000,
                co2PreventedG = 2,
                bytesSent = 0L,
            )

        // Act
        repository.recordMetrics(savings)

        // Assert
        val metrics = database.ecoMetricsQueries.getAllEcoMetrics(value_ = 1).executeAsList()
        assertEquals(1, metrics.size, "Should save one metric record")

        val saved = metrics.first()
        assertEquals(100, saved.tokens_processed)
        assertEquals(120, saved.water_saved_ml)
        assertEquals(3000, saved.energy_saved_wh)
        assertEquals(2, saved.co2_prevented_g)
        assertEquals(0, saved.bytes_sent)
    }

    @Test
    fun `recordMetrics with session ID groups metrics by session`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)
        val sessionId = "session_001"

        val savings =
            EcoSavings(
                tokensProcessed = 100,
                waterSavedMl = 120,
                energySavedWh = 3000,
                co2PreventedG = 2,
                bytesSent = 0L,
            )

        // Act
        repository.recordMetrics(savings, sessionId = sessionId)

        // Assert
        val sessionMetrics =
            database.ecoMetricsQueries
                .getEcoMetricsBySession(sessionId)
                .executeAsList()

        assertEquals(1, sessionMetrics.size)
        assertEquals(sessionId, sessionMetrics.first().session_id)
    }

    @Test
    fun `recordMetrics with project ID links to project`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)
        val projectId = "project_001"

        val savings =
            EcoSavings(
                tokensProcessed = 100,
                waterSavedMl = 120,
                energySavedWh = 3000,
                co2PreventedG = 2,
                bytesSent = 0L,
            )

        // Act
        repository.recordMetrics(savings, projectId = projectId)

        // Assert
        val projectMetrics =
            database.ecoMetricsQueries
                .getEcoMetricsByProject(projectId)
                .executeAsList()

        assertEquals(1, projectMetrics.size)
        assertEquals(projectId, projectMetrics.first().project_id)
    }

    @Test
    fun `recordMetrics accepts network event with real bytes`() {
        // ADR-0006: bytes_sent / bytes_received are now real. Downloads
        // and web searches record their actual bytes. No precondition.
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        val downloadEvent =
            EcoCalculator.networkEvent(
                bytesSent = 512L,
                bytesReceived = 484_000_000L, // Qwen3-0.6B Q4_K_M ~484MB
            )

        repository.recordMetrics(downloadEvent, sessionId = "download:qwen3-0.6b")

        val bytes = repository.getTotalNetworkBytes()
        assertEquals(512L, bytes.bytesSent)
        assertEquals(484_000_000L, bytes.bytesReceived)
    }

    // ==================== Lifetime Stats Tests ====================

    @Test
    fun `getLifetimeStats returns aggregated environmental savings`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Record multiple queries
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))
        repository.recordMetrics(EcoSavings(200, 240, 6000, 4, 0L))
        repository.recordMetrics(EcoSavings(50, 60, 1500, 1, 0L))

        // Act
        val stats = repository.getLifetimeStats()

        // Assert
        assertNotNull(stats, "Should return lifetime stats")
        assertEquals(350, stats.totalTokens, "Total tokens: 100 + 200 + 50")
        assertEquals(420, stats.totalWaterMl, "Total water: 120 + 240 + 60")
        assertEquals(10500, stats.totalEnergyWh, "Total energy: 3000 + 6000 + 1500")
        assertEquals(7, stats.totalCo2G, "Total CO2: 2 + 4 + 1")
        assertEquals(0L, stats.totalBytesSent, "Chat inference rows: bytesSent = 0")
        assertEquals(0L, stats.totalBytesReceived, "Chat inference rows: bytesReceived = 0")
        assertEquals(3, stats.totalQueries)
    }

    @Test
    fun `getLifetimeStats returns null when no data exists`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Act
        val stats = repository.getLifetimeStats()

        // Assert
        assertNull(stats, "Should return null when no metrics recorded")
    }

    @Test
    fun `getLifetimeStats includes first and last query timestamps`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        val startTime = Clock.System.now().toEpochMilliseconds()

        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))
        Thread.sleep(10) // Small delay to ensure different timestamps
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))

        // Act
        val stats = repository.getLifetimeStats()

        // Assert
        assertNotNull(stats)
        assertTrue(stats.firstQueryAt >= startTime, "First query timestamp should be valid")
        assertTrue(stats.lastQueryAt > stats.firstQueryAt, "Last query should be after first")
    }

    // ==================== Session Stats Tests ====================

    @Test
    fun `getSessionStats groups metrics by session`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Session 1: 2 queries
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L), sessionId = "session_001")
        repository.recordMetrics(EcoSavings(200, 240, 6000, 4, 0L), sessionId = "session_001")

        // Session 2: 1 query
        repository.recordMetrics(EcoSavings(50, 60, 1500, 1, 0L), sessionId = "session_002")

        // Act
        val sessionStats = repository.getSessionStats()

        // Assert
        assertEquals(2, sessionStats.size, "Should have 2 sessions")

        val session1 = sessionStats.find { it.sessionId == "session_001" }
        assertNotNull(session1)
        assertEquals(2, session1.queries)
        assertEquals(300, session1.tokens)
        assertEquals(360, session1.waterMl)

        val session2 = sessionStats.find { it.sessionId == "session_002" }
        assertNotNull(session2)
        assertEquals(1, session2.queries)
    }

    @Test
    fun `getSessionStats orders by most recent first`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L), sessionId = "old_session")
        Thread.sleep(10)
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L), sessionId = "new_session")

        // Act
        val sessionStats = repository.getSessionStats()

        // Assert
        assertEquals("new_session", sessionStats.first().sessionId, "Most recent session first")
    }

    // ==================== Project Stats Tests ====================

    @Test
    fun `getProjectStats aggregates by project`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Project A: 2 queries
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L), projectId = "project_a")
        repository.recordMetrics(EcoSavings(200, 240, 6000, 4, 0L), projectId = "project_a")

        // Project B: 1 query
        repository.recordMetrics(EcoSavings(50, 60, 1500, 1, 0L), projectId = "project_b")

        // Act
        val projectStats = repository.getProjectStats()

        // Assert
        assertEquals(2, projectStats.size)

        val projectA = projectStats.find { it.projectId == "project_a" }
        assertNotNull(projectA)
        assertEquals(2, projectA.queries)
        assertEquals(300, projectA.tokens)
    }

    // ==================== Daily Stats Tests ====================

    @Test
    fun `getDailyStats returns breakdown by day`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Record metrics for today
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))
        repository.recordMetrics(EcoSavings(200, 240, 6000, 4, 0L))

        // Act
        val dailyStats = repository.getDailyStats(days = 7)

        // Assert
        assertTrue(dailyStats.isNotEmpty(), "Should have daily stats")

        val today = dailyStats.first()
        assertEquals(2, today.queries)
        assertEquals(300, today.tokens)
        assertEquals(360, today.waterMl)
    }

    @Test
    fun `getDailyStats respects day limit`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))

        // Act
        val last7Days = repository.getDailyStats(days = 7)
        val last30Days = repository.getDailyStats(days = 30)

        // Assert
        assertTrue(last7Days.size <= 7, "Should limit to 7 days")
        assertTrue(last30Days.size <= 30, "Should limit to 30 days")
    }

    // ==================== Network Usage ====================

    @Test
    fun `chat inference rows contribute zero to network bytes`() {
        // Chat stays on-device — inference rows record 0 bytes even though
        // the schema no longer enforces it. Verified in aggregate.
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        repository.recordMetrics(EcoCalculator.calculateSavings(100))
        repository.recordMetrics(EcoCalculator.calculateSavings(200))

        val bytes = repository.getTotalNetworkBytes()
        assertEquals(0L, bytes.bytesSent)
        assertEquals(0L, bytes.bytesReceived)
        assertEquals(0L, bytes.total)
    }

    @Test
    fun `getTotalNetworkBytes sums inference and network rows correctly`() {
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Two inference rows (0 bytes) + one download (net bytes) + one search
        repository.recordMetrics(EcoCalculator.calculateSavings(100))
        repository.recordMetrics(EcoCalculator.calculateSavings(200))
        repository.recordMetrics(EcoCalculator.networkEvent(bytesSent = 100L, bytesReceived = 5_000_000L))
        repository.recordMetrics(EcoCalculator.networkEvent(bytesSent = 200L, bytesReceived = 4_000L))

        val bytes = repository.getTotalNetworkBytes()
        assertEquals(300L, bytes.bytesSent)
        assertEquals(5_004_000L, bytes.bytesReceived)
    }

    // ==================== Average Savings Tests ====================

    @Test
    fun `getAverageSavings calculates per-query averages`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))
        repository.recordMetrics(EcoSavings(200, 240, 6000, 4, 0L))

        // Act
        val averages = repository.getAverageSavings()

        // Assert
        assertNotNull(averages)
        assertEquals(150.0, averages.avgTokens, 0.1, "Average tokens: (100 + 200) / 2")
        assertEquals(180.0, averages.avgWaterMl, 0.1, "Average water: (120 + 240) / 2")
        assertEquals(4500.0, averages.avgEnergyWh, 0.1, "Average energy: (3000 + 6000) / 2")
        assertEquals(3.0, averages.avgCo2G, 0.1, "Average CO2: (2 + 4) / 2")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `multiple queries in same session aggregate correctly`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)
        val sessionId = "test_session"

        // Simulate a conversation with 5 queries
        repeat(5) {
            repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L), sessionId = sessionId)
        }

        // Act
        val sessionStats = repository.getSessionStats()

        // Assert
        assertEquals(1, sessionStats.size, "Should have 1 session")
        val session = sessionStats.first()
        assertEquals(5, session.queries)
        assertEquals(500, session.tokens)
        assertEquals(600, session.waterMl)
    }

    @Test
    fun `metrics without session or project ID still recorded`() {
        // Arrange
        val database = createTestDatabase()
        val repository = EcoMetricsRepository(database)

        // Act
        repository.recordMetrics(EcoSavings(100, 120, 3000, 2, 0L))

        // Assert
        val stats = repository.getLifetimeStats()
        assertNotNull(stats)
        assertEquals(1, stats.totalQueries)
    }

    // ==================== Helper Functions ====================

    private fun createTestDatabase(): MaDatabase = TestDatabaseFactory.createInMemoryDatabase()
}
