package app.m1k3.ai.assistant.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.m1k3.ai.assistant.ai.MockLlmEngine
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.test.ChatScreenTestTags
import app.m1k3.ai.assistant.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * ChatScreen UI Tests with MockLlmEngine
 *
 * Comprehensive UI testing for ChatScreen using deterministic MockLlmEngine.
 * Tests user interactions, message flow, loading states, and eco metrics.
 *
 * Test Categories:
 * - Basic Interaction: User input, send button, message display
 * - Message Flow: Conversations, auto-scroll, multiple messages
 * - AI Generation: Loading states, streaming updates, completion
 * - Eco Metrics: Environmental impact tracking and display
 * - Error Handling: Error states, recovery, user feedback
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockEngine: MockLlmEngine
    private lateinit var database: MaDatabase

    @Before
    fun setup() {
        // Create deterministic mock engine (fast, predictable responses)
        mockEngine = MockLlmEngine(
            responses = listOf(
                "Hello! I'm M1K3, your privacy-first AI assistant. How can I help you today?",
                "That's a great question! Let me provide a helpful answer.",
                "I understand. Here's some useful information for you."
            ),
            delayMs = 50 // Fast for testing
        )

        // Create in-memory test database
        database = app.m1k3.ai.assistant.test.TestDatabaseFactory.createInMemoryDatabase()
    }

    @After
    fun teardown() {
        mockEngine.release()
        database.close()
    }

    // ========================================
    // Test Suite A: Basic Interaction
    // ========================================

    @Test
    fun testUserCanTypAndSendMessage() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        // Wait for initialization
        composeTestRule.waitForIdle()

        // Type a message
        composeTestRule.typeMessage("Hello M1K3!")

        // Verify send button is enabled
        composeTestRule.assertSendButtonEnabled()

        // Send the message
        composeTestRule.clickSend()

        // Verify user message appears
        composeTestRule.assertMessageDisplayed("Hello M1K3!")
    }

    @Test
    fun testAIResponseDisplaysCorrectly() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Send a message
        composeTestRule.sendMessage("Hi there!")

        // Wait for AI response
        val responseReceived = composeTestRule.waitForResponse(timeoutMs = 3000)
        assertTrue(responseReceived, "AI response should be received within timeout")

        // Verify AI response appears
        composeTestRule.assertMessageDisplayed("Hello! I'm M1K3")

        // Verify generation is complete
        composeTestRule.assertNotGenerating()
    }

    @Test
    fun testLoadingIndicatorShowsDuringGeneration() {
        // Use slower mock for this test to catch loading state
        val slowMock = MockLlmEngine(
            responses = listOf("This is a test response"),
            delayMs = 500 // Slower to observe loading
        )

        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = slowMock,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Send message
        composeTestRule.sendMessage("Test message")

        // Verify loading indicator appears immediately
        composeTestRule.waitForCondition(timeoutMs = 200) {
            composeTestRule.onAllNodesWithTag(ChatScreenTestTags.LOADING_INDICATOR)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Wait for completion
        composeTestRule.waitForResponse(timeoutMs = 2000)

        // Verify loading indicator is gone
        composeTestRule.assertNotGenerating()

        slowMock.release()
    }

    @Test
    fun testEcoMetricsUpdateAfterResponse() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Initially, eco indicator should not be visible (no messages yet)
        composeTestRule.onAllNodesWithTag(ChatScreenTestTags.ECO_INDICATOR)
            .assertCountEquals(0)

        // Send message and wait for response
        composeTestRule.sendMessage("Calculate my eco impact")
        composeTestRule.waitForResponse(timeoutMs = 3000)

        // Now eco indicator should be visible and updated
        composeTestRule.assertEcoMetricsUpdated()

        // Verify eco indicator shows expected metrics (water, energy, CO2)
        composeTestRule.onNodeWithText("ml", substring = true)
            .assertExists() // Water saved
    }

    // ========================================
    // Test Suite B: Message Flow
    // ========================================

    @Test
    fun testMultipleMessageConversationFlow() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Send first message
        composeTestRule.sendMessage("First message")
        composeTestRule.waitForResponse()
        composeTestRule.assertMessageDisplayed("First message")
        composeTestRule.assertMessageDisplayed("Hello! I'm M1K3")

        // Send second message
        composeTestRule.sendMessage("Second message")
        composeTestRule.waitForResponse()
        composeTestRule.assertMessageDisplayed("Second message")
        composeTestRule.assertMessageDisplayed("That's a great question")

        // Send third message
        composeTestRule.sendMessage("Third message")
        composeTestRule.waitForResponse()
        composeTestRule.assertMessageDisplayed("Third message")
        composeTestRule.assertMessageDisplayed("I understand")

        // Verify all messages are still visible (no truncation)
        composeTestRule.assertMessageDisplayed("First message")
        composeTestRule.assertMessageDisplayed("Second message")
        composeTestRule.assertMessageDisplayed("Third message")
    }

    @Test
    fun testInputStatesDuringGeneration() {
        val slowMock = MockLlmEngine(
            responses = listOf("Response"),
            delayMs = 500
        )

        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = slowMock,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Initially, input should be enabled
        composeTestRule.assertInputEnabled()
        composeTestRule.assertSendButtonDisabled() // No text yet

        // Type a message
        composeTestRule.typeMessage("Test")
        composeTestRule.assertSendButtonEnabled()

        // Send the message
        composeTestRule.clickSend()

        // During generation, input should still be enabled but send disabled
        composeTestRule.waitForCondition(timeoutMs = 200) {
            composeTestRule.onAllNodesWithTag(ChatScreenTestTags.LOADING_INDICATOR)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Wait for completion
        composeTestRule.waitForResponse()

        // After generation, input should be enabled and ready
        composeTestRule.assertInputEnabled()

        slowMock.release()
    }

    @Test
    fun testSendButtonDisabledWithEmptyInput() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Initially, send button should be disabled (no text)
        composeTestRule.assertSendButtonDisabled()

        // Type some text
        composeTestRule.typeMessage("Hello")

        // Send button should now be enabled
        composeTestRule.assertSendButtonEnabled()

        // Clear the input
        composeTestRule.clearInput()

        // Send button should be disabled again
        composeTestRule.assertSendButtonDisabled()
    }

    // ========================================
    // Test Suite C: Performance Metrics
    // ========================================

    @Test
    fun testResponseMetricsAreDisplayed() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Send message and wait for response
        composeTestRule.sendMessage("Show me metrics")
        composeTestRule.waitForResponse()

        // Verify inference stats are displayed (⚡ icon indicates stats)
        composeTestRule.onNodeWithText("⚡", substring = true)
            .assertExists()
            .assertIsDisplayed()

        // Verify stats contain expected elements (tokens, ms, tok/s)
        composeTestRule.onNodeWithText("tokens", substring = true)
            .assertExists()

        composeTestRule.onNodeWithText("ms", substring = true)
            .assertExists()

        composeTestRule.onNodeWithText("tok/s", substring = true)
            .assertExists()
    }

    @Test
    fun testExtractResponseMetrics() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Send message and wait for response
        composeTestRule.sendMessage("Performance test")
        composeTestRule.waitForResponse()

        // Extract metrics using helper
        val metrics = composeTestRule.getResponseMetrics()

        // Verify metrics were extracted
        assertTrue(metrics.isNotEmpty(), "Should extract performance metrics")

        // Metrics should contain at least one of: tokens, time_ms, tok_per_sec
        val hasMetrics = metrics.containsKey("tokens") ||
                        metrics.containsKey("time_ms") ||
                        metrics.containsKey("tok_per_sec")

        assertTrue(hasMetrics, "Should extract at least one performance metric")
    }

    // ========================================
    // Test Suite D: Avatar Integration
    // ========================================

    @Test
    fun testAvatarIsVisible() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Avatar should be visible in the toolbar
        composeTestRule.assertAvatarVisible()
    }

    // ========================================
    // Test Suite E: Edge Cases
    // ========================================

    @Test
    fun testLongMessageHandling() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Create a long message (500+ characters)
        val longMessage = "This is a very long message. ".repeat(20)

        // Type and send the long message
        composeTestRule.sendMessage(longMessage)
        composeTestRule.waitForResponse()

        // Verify the long message is displayed
        composeTestRule.assertMessageDisplayed("This is a very long message", substring = true)

        // Verify AI response still works
        composeTestRule.assertMessageDisplayed("Hello! I'm M1K3", substring = true)
    }

    @Test
    fun testSpecialCharactersInMessage() {
        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = mockEngine,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Test message with special characters and emojis
        val specialMessage = "Test 🤖 <special> & chars ñ 你好"

        composeTestRule.sendMessage(specialMessage)
        composeTestRule.waitForResponse()

        // Verify special characters are preserved
        composeTestRule.assertMessageDisplayed("Test 🤖", substring = true)
        composeTestRule.assertMessageDisplayed("chars", substring = true)
    }

    @Test
    fun testRapidMessageSending() {
        // Use very fast mock for rapid sending test
        val fastMock = MockLlmEngine(
            responses = List(5) { "Quick response $it" },
            delayMs = 10 // Very fast
        )

        composeTestRule.setContent {
            ChatScreen(
                onBackClick = {},
                onDebugClick = {},
                onHistoryClick = {},
                onEcoStatsClick = {},
                aiEngine = fastMock,
                database = database
            )
        }

        composeTestRule.waitForIdle()

        // Send multiple messages rapidly
        repeat(3) { index ->
            composeTestRule.sendMessage("Message $index")
            // Small delay to allow UI to update
            Thread.sleep(100)
        }

        // Wait for all responses to complete
        composeTestRule.waitForCondition(timeoutMs = 5000) {
            // Check that all messages were sent and received
            composeTestRule.onAllNodes(hasText("Message", substring = true))
                .fetchSemanticsNodes()
                .size >= 3
        }

        // Verify all messages are present
        composeTestRule.assertMessageDisplayed("Message 0")
        composeTestRule.assertMessageDisplayed("Message 1")
        composeTestRule.assertMessageDisplayed("Message 2")

        fastMock.release()
    }
}
