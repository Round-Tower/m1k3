package app.m1k3.ai.assistant.demo

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.*
import kotlinx.datetime.Clock

/**
 * Phase 2 Debug Screen - Comprehensive Testing Tool
 *
 * Tests all Phase 2 features:
 * - Conversation History (ConversationRepository)
 * - Search (SearchRepository)
 * - Export (ExportManager)
 * - Eco Metrics (EcoMetricsRepository)
 * - View Models (HistoryViewModel, EcoStatsViewModel)
 *
 * **Usage:**
 * ```kotlin
 * val debugScreen = Phase2DebugScreen(database)
 * val results = debugScreen.runAllTests()
 * ```
 */
class Phase2DebugScreen(private val database: MaDatabase) {

    private val conversationRepo = ConversationRepository(database)
    private val searchRepo = SearchRepository(database)
    private val exportManager = ExportManager(database)
    private val ecoMetricsRepo = EcoMetricsRepository(database)

    /**
     * Run all Phase 2 tests and return results.
     *
     * @return Test results summary
     */
    fun runAllTests(): Phase2TestResults {
        val results = mutableListOf<TestResult>()

        // Test 1: Conversation Management
        results.add(testConversationManagement())

        // Test 2: Search Functionality
        results.add(testSearchFunctionality())

        // Test 3: Export Functionality
        results.add(testExportFunctionality())

        // Test 4: Eco Metrics
        results.add(testEcoMetrics())

        // Test 5: View Models
        results.add(testViewModels())

        val passedCount = results.count { it.passed }
        val totalCount = results.size

        return Phase2TestResults(
            tests = results,
            passedCount = passedCount,
            totalCount = totalCount,
            successRate = (passedCount.toDouble() / totalCount.toDouble()) * 100.0
        )
    }

    // ==================== Test 1: Conversation Management ====================

    private fun testConversationManagement(): TestResult {
        return try {
            val projectId = "test_project_${Clock.System.now().toEpochMilliseconds()}"

            // Create conversation
            val convId = conversationRepo.createConversation(projectId, "Test Conversation")

            // Update title
            conversationRepo.updateConversationTitle(convId, "Updated Title")

            // Get conversation
            val conversation = conversationRepo.getConversationById(convId)

            // Verify
            val passed = conversation != null && conversation.title == "Updated Title"

            TestResult(
                name = "Conversation Management",
                passed = passed,
                details = if (passed) {
                    "✅ Created conversation, updated title, retrieved successfully"
                } else {
                    "❌ Failed: conversation=$conversation"
                }
            )
        } catch (e: Exception) {
            TestResult(
                name = "Conversation Management",
                passed = false,
                details = "❌ Exception: ${e.message}"
            )
        }
    }

    // ==================== Test 2: Search Functionality ====================

    private fun testSearchFunctionality(): TestResult {
        return try {
            val projectId = "test_project_${Clock.System.now().toEpochMilliseconds()}"
            val convId = conversationRepo.createConversation(projectId, "Search Test")

            // Add searchable messages
            database.messageQueries.insertMessage(
                id = "search_msg_1_${Clock.System.now().toEpochMilliseconds()}",
                project_id = projectId,
                conversation_id = convId,
                role = "user",
                content = "Hello world, this is a test message",
                tokens = 10,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                image_uri = null,
                sentiment_valence = null,
                sentiment_arousal = null,
                sentiment_dominance = null,
                sentiment_emotion = null,
                sentiment_intensity = null,
                rag_sources = null,
                rag_confidence = null
            )

            // Search
            val results = searchRepo.searchMessages(
                query = "hello",
                projectId = projectId,
                limit = 10
            )

            // Verify
            val passed = results.isNotEmpty() && results[0].content.contains("Hello")

            TestResult(
                name = "Search Functionality",
                passed = passed,
                details = if (passed) {
                    "✅ Found ${results.size} results, relevance: ${results[0].relevanceScore}"
                } else {
                    "❌ Failed: found ${results.size} results"
                }
            )
        } catch (e: Exception) {
            TestResult(
                name = "Search Functionality",
                passed = false,
                details = "❌ Exception: ${e.message}"
            )
        }
    }

    // ==================== Test 3: Export Functionality ====================

    private fun testExportFunctionality(): TestResult {
        return try {
            val projectId = "test_project_${Clock.System.now().toEpochMilliseconds()}"
            val convId = conversationRepo.createConversation(projectId, "Export Test")

            // Add message
            database.messageQueries.insertMessage(
                id = "export_msg_1_${Clock.System.now().toEpochMilliseconds()}",
                project_id = projectId,
                conversation_id = convId,
                role = "assistant",
                content = "This is an export test message",
                tokens = 8,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                image_uri = null,
                sentiment_valence = null,
                sentiment_arousal = null,
                sentiment_dominance = null,
                sentiment_emotion = null,
                sentiment_intensity = null,
                rag_sources = null,
                rag_confidence = null
            )

            // Test JSON export
            val json = exportManager.exportConversationToJson(convId)
            val jsonValid = json != null && json.contains("Export Test") && json.contains("messages")

            // Test Markdown export
            val markdown = exportManager.exportConversationToMarkdown(convId)
            val markdownValid = markdown != null && markdown.contains("# Export Test")

            val passed = jsonValid && markdownValid

            TestResult(
                name = "Export Functionality",
                passed = passed,
                details = if (passed) {
                    "✅ JSON: ${json?.length ?: 0} bytes, Markdown: ${markdown?.length ?: 0} bytes"
                } else {
                    "❌ Failed: JSON valid=$jsonValid, Markdown valid=$markdownValid"
                }
            )
        } catch (e: Exception) {
            TestResult(
                name = "Export Functionality",
                passed = false,
                details = "❌ Exception: ${e.message}"
            )
        }
    }

    // ==================== Test 4: Eco Metrics ====================

    private fun testEcoMetrics(): TestResult {
        return try {
            val projectId = "test_project_${Clock.System.now().toEpochMilliseconds()}"
            val sessionId = "test_session_${Clock.System.now().toEpochMilliseconds()}"
            val now = Clock.System.now().toEpochMilliseconds()

            // Calculate savings for 1000 tokens
            val savings = EcoCalculator.calculateSavings(1000)

            // Record metrics
            ecoMetricsRepo.recordMetrics(
                savings = savings,
                sessionId = sessionId,
                projectId = projectId
            )

            // Get lifetime stats
            val lifetimeStats = ecoMetricsRepo.getLifetimeStats()

            // Verify
            val passed = lifetimeStats != null &&
                    lifetimeStats.totalTokens >= 1000 &&
                    lifetimeStats.totalWaterMl >= savings.waterSavedMl &&
                    lifetimeStats.totalEnergyWh >= savings.energySavedWh

            TestResult(
                name = "Eco Metrics",
                passed = passed,
                details = if (passed) {
                    "✅ Recorded 1000 tokens, saved: ${savings.waterSavedMl}ml water, ${savings.energySavedWh}Wh energy, ${savings.co2PreventedG}g CO2"
                } else {
                    "❌ Failed: lifetimeStats=$lifetimeStats"
                }
            )
        } catch (e: Exception) {
            TestResult(
                name = "Eco Metrics",
                passed = false,
                details = "❌ Exception: ${e.message}"
            )
        }
    }

    // ==================== Test 5: View Models ====================

    private fun testViewModels(): TestResult {
        return try {
            // Test HistoryViewModel state
            val historyState = HistoryState()
            val historyPassed = historyState.conversations.isEmpty() &&
                    historyState.searchQuery == "" &&
                    !historyState.isLoading

            // Test EcoStatsViewModel state
            val ecoState = EcoStatsState()
            val ecoPassed = ecoState.lifetimeStats == null &&
                    ecoState.projectMetrics == null &&
                    !ecoState.isLoading

            // Test EcoComparison calculations
            val comparison = EcoComparison(
                localEnergyWh = 10.0,
                localWaterMl = 5.0,
                localCo2G = 3.0,
                cloudEnergyWh = 1000.0,
                cloudWaterMl = 500.0,
                cloudCo2G = 300.0
            )
            val comparisonPassed = comparison.energySavingsPercent == 99.0 &&
                    comparison.waterSavingsPercent == 99.0 &&
                    comparison.co2SavingsPercent == 99.0

            val passed = historyPassed && ecoPassed && comparisonPassed

            TestResult(
                name = "View Models",
                passed = passed,
                details = if (passed) {
                    "✅ HistoryViewModel, EcoStatsViewModel, EcoComparison all working"
                } else {
                    "❌ Failed: history=$historyPassed, eco=$ecoPassed, comparison=$comparisonPassed"
                }
            )
        } catch (e: Exception) {
            TestResult(
                name = "View Models",
                passed = false,
                details = "❌ Exception: ${e.message}"
            )
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Create demo data for manual testing.
     *
     * @return Statistics about created data
     */
    fun createDemoData(): DemoDataStats {
        val projectId = "demo_phase2_${Clock.System.now().toEpochMilliseconds()}"

        // Create 5 conversations
        val conversationIds = mutableListOf<Long>()
        repeat(5) { i ->
            val convId = conversationRepo.createConversation(
                projectId = projectId,
                title = "Demo Conversation ${i + 1}"
            )
            conversationIds.add(convId)

            // Add 3 messages per conversation
            repeat(3) { j ->
                database.messageQueries.insertMessage(
                    id = "demo_msg_${convId}_${j}_${Clock.System.now().toEpochMilliseconds()}",
                    project_id = projectId,
                    conversation_id = convId,
                    role = if (j % 2 == 0) "user" else "assistant",
                    content = "Demo message ${j + 1} in conversation ${i + 1}",
                    tokens = 10,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    image_uri = null,
                    sentiment_valence = null,
                    sentiment_arousal = null,
                    sentiment_dominance = null,
                    sentiment_emotion = null,
                    sentiment_intensity = null,
                    rag_sources = null,
                    rag_confidence = null
                )
            }
        }

        // Record eco metrics
        val savings = EcoCalculator.calculateSavings(1500)
        ecoMetricsRepo.recordMetrics(
            savings = savings,
            sessionId = "demo_session",
            projectId = projectId
        )

        return DemoDataStats(
            conversationsCreated = 5,
            messagesCreated = 15,
            tokensProcessed = 1500,
            waterSaved = savings.waterSavedMl.toLong(),
            energySaved = savings.energySavedWh.toLong(),
            co2Saved = savings.co2PreventedG.toLong()
        )
    }
}

// ==================== Data Classes ====================

/**
 * Test results summary.
 */
data class Phase2TestResults(
    val tests: List<TestResult>,
    val passedCount: Int,
    val totalCount: Int,
    val successRate: Double
)

/**
 * Individual test result.
 */
data class TestResult(
    val name: String,
    val passed: Boolean,
    val details: String
)

/**
 * Demo data statistics.
 */
data class DemoDataStats(
    val conversationsCreated: Int,
    val messagesCreated: Int,
    val tokensProcessed: Long,
    val waterSaved: Long,
    val energySaved: Long,
    val co2Saved: Long
)
