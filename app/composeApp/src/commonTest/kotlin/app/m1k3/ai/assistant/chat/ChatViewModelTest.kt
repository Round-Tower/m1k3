package app.m1k3.ai.assistant.chat

import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * ChatViewModel Tests
 *
 * Tests for chat state management with eco-metrics tracking integration.
 */
class ChatViewModelTest {

    @Test
    fun `initial state is empty`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val viewModel = ChatViewModel(
            database = database,
            projectId = "test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Assert
        val state = viewModel.state.value
        assertEquals(0, state.sessionEcoStats.totalTokens, "Should start with 0 tokens")
        assertEquals(0L, state.sessionEcoStats.waterMl, "Should start with 0 water saved")
        assertEquals(0L, state.sessionEcoStats.energyWh, "Should start with 0 energy saved")
        assertEquals(0L, state.sessionEcoStats.co2G, "Should start with 0 CO2 saved")
        assertEquals(0, state.sessionEcoStats.messageCount, "Should start with 0 messages")
        assertNull(state.error, "Should have no error initially")
    }

    @Test
    fun `recordMessage creates conversation on first message`() {
        runBlocking {
            // Arrange
            val database = TestDatabaseFactory.createInMemoryDatabase()
            val viewModel = ChatViewModel(
                database = database,
                projectId = "test_project",
                scope = CoroutineScope(Dispatchers.Default)
            )

            // Act
            viewModel.recordMessage(
                content = "Hello",
                role = "user",
                tokens = 5
            )
            delay(100) // Wait for async operation

            // Assert
            val conversationId = viewModel.getCurrentConversationId()
            assertNotNull(conversationId, "Should create conversation")

            val conversationRepo = ConversationRepository(database)
            val conversation = conversationRepo.getConversationById(conversationId)
            assertNotNull(conversation, "Conversation should exist in database")
        }
    }

    @Test
    fun `recordMessage saves user message without eco-metrics`() {
        runBlocking {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val viewModel = ChatViewModel(
            database = database,
            projectId = "test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act
        viewModel.recordMessage(
            content = "What is AI?",
            role = "user",
            tokens = 10
        )
        delay(100)

        // Assert
        val state = viewModel.state.value
        assertEquals(0, state.sessionEcoStats.totalTokens, "User messages should not count toward eco stats")
        assertEquals(0L, state.sessionEcoStats.waterMl, "Should not track eco for user messages")
        assertEquals(0, state.sessionEcoStats.messageCount, "Should not increment message count for user")
        }
    }

    @Test
    fun `recordMessage saves assistant message with eco-metrics`() {
        runBlocking {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val viewModel = ChatViewModel(
            database = database,
            projectId = "test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act
        viewModel.recordMessage(
            content = "AI is artificial intelligence",
            role = "assistant",
            tokens = 100
        )
        delay(100)

        // Assert
        val state = viewModel.state.value
        assertEquals(100, state.sessionEcoStats.totalTokens, "Should track tokens")
        assertTrue(state.sessionEcoStats.waterMl > 0, "Should calculate water savings")
        assertTrue(state.sessionEcoStats.energyWh > 0, "Should calculate energy savings")
        assertTrue(state.sessionEcoStats.co2G > 0, "Should calculate CO2 savings")
        assertEquals(1, state.sessionEcoStats.messageCount, "Should increment message count")
        }
    }

    @Test
    fun `recordMessage accumulates eco-metrics across multiple messages`() {
        runBlocking {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val viewModel = ChatViewModel(
            database = database,
            projectId = "test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act - Record 3 assistant messages
        viewModel.recordMessage(content = "Response 1", role = "assistant", tokens = 100)
        delay(100)
        viewModel.recordMessage(content = "Response 2", role = "assistant", tokens = 200)
        delay(100)
        viewModel.recordMessage(content = "Response 3", role = "assistant", tokens = 300)
        delay(100)

        // Assert
        val state = viewModel.state.value
        assertEquals(600, state.sessionEcoStats.totalTokens, "Should accumulate tokens")
        assertEquals(3, state.sessionEcoStats.messageCount, "Should count all assistant messages")
        assertTrue(state.sessionEcoStats.waterMl > 0, "Should accumulate water savings")
        assertTrue(state.sessionEcoStats.energyWh > 0, "Should accumulate energy savings")
        assertTrue(state.sessionEcoStats.co2G > 0, "Should accumulate CO2 savings")
        }
    }

    @Test
    fun `formatWater displays small values in ml`() {
        // Arrange
        val stats = SessionEcoStats(waterMl = 500)

        // Assert
        assertEquals("500 ml", stats.formatWater(), "Should format small values in ml")
    }

    @Test
    fun `formatWater displays large values in L`() {
        // Arrange
        val stats = SessionEcoStats(waterMl = 2500)

        // Assert
        assertEquals("2.50 L", stats.formatWater(), "Should format large values in L")
    }

    @Test
    fun `formatEnergy displays small values in Wh`() {
        // Arrange
        val stats = SessionEcoStats(energyWh = 800)

        // Assert
        assertEquals("800 Wh", stats.formatEnergy(), "Should format small values in Wh")
    }

    @Test
    fun `formatEnergy displays large values in kWh`() {
        // Arrange
        val stats = SessionEcoStats(energyWh = 1500)

        // Assert
        assertEquals("1.50 kWh", stats.formatEnergy(), "Should format large values in kWh")
    }

    @Test
    fun `formatCO2 displays small values in g`() {
        // Arrange
        val stats = SessionEcoStats(co2G = 750)

        // Assert
        assertEquals("750 g", stats.formatCO2(), "Should format small values in g")
    }

    @Test
    fun `formatCO2 displays large values in kg`() {
        // Arrange
        val stats = SessionEcoStats(co2G = 1200)

        // Assert
        assertEquals("1.20 kg", stats.formatCO2(), "Should format large values in kg")
    }

    @Test
    fun `clearError resets error state`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val viewModel = ChatViewModel(
            database = database,
            projectId = "test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act
        viewModel.clearError()

        // Assert
        assertNull(viewModel.state.value.error, "Should clear error")
    }

    @Test
    fun `resetSessionStats clears all statistics`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val viewModel = ChatViewModel(
            database = database,
            projectId = "test_project",
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act
        viewModel.resetSessionStats()

        // Assert
        val state = viewModel.state.value
        assertEquals(0, state.sessionEcoStats.totalTokens, "Should reset tokens")
        assertEquals(0L, state.sessionEcoStats.waterMl, "Should reset water")
        assertEquals(0L, state.sessionEcoStats.energyWh, "Should reset energy")
        assertEquals(0L, state.sessionEcoStats.co2G, "Should reset CO2")
        assertEquals(0, state.sessionEcoStats.messageCount, "Should reset message count")
    }
}
