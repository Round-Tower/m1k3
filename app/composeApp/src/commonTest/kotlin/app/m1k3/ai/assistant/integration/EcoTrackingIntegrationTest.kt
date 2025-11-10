package app.m1k3.ai.assistant.integration

import app.m1k3.ai.assistant.chat.ChatViewModel
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * 間 AI - End-to-End Eco Tracking Integration Test
 *
 * Tests the complete flow from message recording to eco-metrics calculation
 * and database persistence, validating Phase 2 integration.
 *
 * **Test Scenario:**
 * 1. User sends a message (no eco-metrics)
 * 2. AI responds with 250 tokens
 * 3. Eco-metrics are automatically calculated
 * 4. Database records are created for message and metrics
 * 5. SessionEcoStats are updated in real-time
 * 6. LifetimeStats accumulate correctly
 *
 * **Philosophy:**
 * This test ensures every token saved contributes to environmental transparency.
 */
class EcoTrackingIntegrationTest {

    private lateinit var database: app.m1k3.ai.assistant.database.MaDatabase
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var ecoMetricsRepository: EcoMetricsRepository
    private lateinit var conversationRepository: ConversationRepository

    @BeforeTest
    fun setup() {
        database = TestDatabaseFactory.createInMemoryDatabase()
        ecoMetricsRepository = EcoMetricsRepository(database)
        conversationRepository = ConversationRepository(database)
        chatViewModel = ChatViewModel(
            conversationRepo = conversationRepository,
            ecoMetricsRepo = ecoMetricsRepository,
            database = database,
            projectId = "integration_test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `end-to-end eco tracking flow validates complete integration`() {
        runBlocking {
            // ARRANGE
            val userMessage = "What is artificial intelligence?"
            val aiResponse = "Artificial intelligence (AI) refers to computer systems designed to perform tasks that typically require human intelligence, such as learning, reasoning, problem-solving, and understanding natural language."
            val tokenCount = 250

            // ACT 1: User sends message (no eco-metrics)
            chatViewModel.recordMessage(
                content = userMessage,
                role = "user",
                tokens = 0
            )
            delay(150) // Wait for async database operations

            // ACT 2: AI responds with tokens (eco-metrics calculated)
            chatViewModel.recordMessage(
                content = aiResponse,
                role = "assistant",
                tokens = tokenCount
            )
            delay(150) // Wait for async database operations

            // ASSERT 1: SessionEcoStats are updated correctly
            val sessionStats = chatViewModel.state.value.sessionEcoStats
            assertEquals(tokenCount, sessionStats.totalTokens, "Session should track all tokens")
            assertEquals(1, sessionStats.messageCount, "Should count assistant messages only")
            assertTrue(sessionStats.waterMl > 0, "Water savings should be calculated")
            assertTrue(sessionStats.energyWh > 0, "Energy savings should be calculated")
            assertTrue(sessionStats.co2G > 0, "CO2 savings should be calculated")

            // ASSERT 2: Eco-metrics are calculated correctly
            val expectedSavings = EcoCalculator.calculateSavings(tokenCount)
            assertEquals(expectedSavings.waterSavedMl, sessionStats.waterMl, "Water calculation should match EcoCalculator")
            assertEquals(expectedSavings.energySavedWh, sessionStats.energyWh, "Energy calculation should match EcoCalculator")
            assertEquals(expectedSavings.co2PreventedG, sessionStats.co2G, "CO2 calculation should match EcoCalculator")

            // ASSERT 3: Messages are persisted to database
            val allMessages = database.messageQueries.getAllMessagesByProject("integration_test_project").executeAsList()
            assertEquals(2, allMessages.size, "Should have 2 messages (user + assistant)")

            val userMsg = allMessages.find { it.role == "user" }
            assertNotNull(userMsg, "User message should be persisted")
            assertEquals(userMessage, userMsg.content, "User message content should match")
            assertEquals(0L, userMsg.tokens, "User message should have 0 tokens")

            val assistantMsg = allMessages.find { it.role == "assistant" }
            assertNotNull(assistantMsg, "Assistant message should be persisted")
            assertEquals(aiResponse, assistantMsg.content, "Assistant message content should match")
            assertEquals(tokenCount.toLong(), assistantMsg.tokens, "Assistant message tokens should match")

            // ASSERT 4: Conversation is created and updated
            val conversationId = chatViewModel.getCurrentConversationId()
            assertNotNull(conversationId, "Conversation should be created")

            val conversation = conversationRepository.getConversationById(conversationId)
            assertNotNull(conversation, "Conversation should exist in database")
            assertEquals("integration_test_project", conversation.projectId, "Conversation should be in correct project")
            assertTrue(conversation.title?.startsWith(userMessage.take(20)) == true, "Conversation title should be set from first user message")
            assertEquals(2, conversation.messageCount, "Conversation should count both messages")
            assertEquals(tokenCount.toLong(), conversation.tokenCount, "Conversation should track assistant tokens")

            // ASSERT 5: Eco-metrics are persisted to database
            val ecoMetrics = database.ecoMetricsQueries.getMetricsBySession(
                sessionId = chatViewModel.getCurrentConversationId().toString() // Use conversation ID as session
            ).executeAsList()

            assertTrue(ecoMetrics.isNotEmpty(), "Eco-metrics should be persisted")

            // ASSERT 6: Lifetime stats accumulate correctly
            val lifetimeStats = ecoMetricsRepository.getLifetimeStats()
            assertEquals(tokenCount.toLong(), lifetimeStats.totalTokens, "Lifetime should accumulate all tokens")
            assertEquals(1L, lifetimeStats.totalQueries, "Lifetime should count assistant messages")
            assertTrue(lifetimeStats.totalWaterMl > 0, "Lifetime water should accumulate")
            assertTrue(lifetimeStats.totalEnergyWh > 0, "Lifetime energy should accumulate")
            assertTrue(lifetimeStats.totalCo2G > 0, "Lifetime CO2 should accumulate")
            assertEquals(0L, lifetimeStats.totalBytesSent, "No bytes should be sent (100% local)")

            // ASSERT 7: Project stats are tracked correctly
            val projectStats = ecoMetricsRepository.getProjectStats("integration_test_project")
            assertNotNull(projectStats, "Project stats should exist")
            assertEquals(1, projectStats.queries, "Project should count assistant messages")
            assertEquals(tokenCount.toLong(), projectStats.tokens, "Project should track all tokens")
            assertEquals(sessionStats.waterMl, projectStats.waterMl, "Project water should match session")
            assertEquals(sessionStats.energyWh, projectStats.energyWh, "Project energy should match session")
            assertEquals(sessionStats.co2G, projectStats.co2G, "Project CO2 should match session")

            println("✅ End-to-end eco tracking integration test passed!")
            println("📊 Stats: ${sessionStats.totalTokens} tokens → ${sessionStats.waterMl}ml water, ${sessionStats.energyWh}Wh energy, ${sessionStats.co2G}g CO2 saved")
        }
    }

    @Test
    fun `multiple messages accumulate eco-metrics correctly`() {
        runBlocking {
            // ARRANGE: Three conversation turns
            val turns = listOf(
                Pair("What is AI?", 100),
                Pair("How does machine learning work?", 200),
                Pair("Explain neural networks.", 300)
            )

            // ACT: Simulate full conversation
            for ((query, tokens) in turns) {
                // User message
                chatViewModel.recordMessage(
                    content = query,
                    role = "user",
                    tokens = 0
                )
                delay(100)

                // AI response
                chatViewModel.recordMessage(
                    content = "Response to: $query",
                    role = "assistant",
                    tokens = tokens
                )
                delay(100)
            }

            // ASSERT: Cumulative tracking
            val sessionStats = chatViewModel.state.value.sessionEcoStats
            val totalTokens = turns.sumOf { it.second }
            val expectedMessages = turns.size

            assertEquals(totalTokens, sessionStats.totalTokens, "Should accumulate all tokens")
            assertEquals(expectedMessages, sessionStats.messageCount, "Should count all assistant messages")

            // Verify cumulative eco-metrics
            val expectedSavings = EcoCalculator.calculateSavings(totalTokens)
            assertEquals(expectedSavings.waterSavedMl, sessionStats.waterMl, "Cumulative water should match")
            assertEquals(expectedSavings.energySavedWh, sessionStats.energyWh, "Cumulative energy should match")
            assertEquals(expectedSavings.co2PreventedG, sessionStats.co2G, "Cumulative CO2 should match")

            // Verify database persistence
            val allMessages = database.messageQueries.getAllMessagesByProject("integration_test_project").executeAsList()
            assertEquals(6, allMessages.size, "Should have 6 messages (3 user + 3 assistant)")

            val lifetimeStats = ecoMetricsRepository.getLifetimeStats()
            assertEquals(totalTokens.toLong(), lifetimeStats.totalTokens, "Lifetime should match cumulative")

            println("✅ Multi-message accumulation test passed!")
            println("📊 Cumulative: $totalTokens tokens → ${sessionStats.formatWater()}, ${sessionStats.formatEnergy()}, ${sessionStats.formatCO2()}")
        }
    }

    @Test
    fun `user messages do not contribute to eco-metrics`() {
        runBlocking {
            // ARRANGE
            val userMessage1 = "Hello M1K3"
            val userMessage2 = "How are you?"

            // ACT: Send multiple user messages (no AI responses)
            chatViewModel.recordMessage(content = userMessage1, role = "user", tokens = 0)
            delay(100)
            chatViewModel.recordMessage(content = userMessage2, role = "user", tokens = 0)
            delay(100)

            // ASSERT: Eco-metrics should remain zero
            val sessionStats = chatViewModel.state.value.sessionEcoStats
            assertEquals(0, sessionStats.totalTokens, "User messages should not count tokens")
            assertEquals(0, sessionStats.messageCount, "User messages should not count toward eco-metrics")
            assertEquals(0L, sessionStats.waterMl, "No water savings from user messages")
            assertEquals(0L, sessionStats.energyWh, "No energy savings from user messages")
            assertEquals(0L, sessionStats.co2G, "No CO2 savings from user messages")

            // Messages should still be persisted
            val allMessages = database.messageQueries.getAllMessagesByProject("integration_test_project").executeAsList()
            assertEquals(2, allMessages.size, "User messages should still be persisted")

            println("✅ User message exclusion test passed!")
        }
    }

    @Test
    fun `privacy validation ensures zero bytes transmitted`() {
        runBlocking {
            // ARRANGE & ACT: Full conversation
            chatViewModel.recordMessage(content = "Test query", role = "user", tokens = 0)
            delay(100)
            chatViewModel.recordMessage(content = "Test response", role = "assistant", tokens = 500)
            delay(100)

            // ASSERT: Privacy-first validation
            val lifetimeStats = ecoMetricsRepository.getLifetimeStats()
            assertEquals(0L, lifetimeStats.totalBytesSent, "🔒 CRITICAL: Zero bytes should be transmitted (100% local)")

            // Verify no network activity indicators in eco-metrics
            val allMetrics = database.ecoMetricsQueries.getAllMetrics().executeAsList()
            allMetrics.forEach { metric ->
                assertEquals(0L, metric.bytes_sent, "Every metric record should show 0 bytes sent")
            }

            println("✅ Privacy validation passed: 0 bytes transmitted!")
            println("🔒 100% local processing confirmed")
        }
    }
}
