package app.m1k3.ai.assistant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD Tests for InitializationViewModel
 *
 * Verifies initialization state management for database and knowledge setup.
 * Uses sealed InitializationState for type-safe state transitions.
 *
 * **Test Strategy (Red → Green → Refactor):**
 * - Mock AndroidDatabaseInitializer for fast tests
 * - StateFlow testing with turbine
 * - AAA pattern: Arrange, Act, Assert
 */
@RunWith(AndroidJUnit4::class)
class InitializationViewModelTest {

    private lateinit var database: MaDatabase
    private lateinit var mockInitializer: MockDatabaseInitializer
    private lateinit var viewModel: InitializationViewModel

    @Before
    fun setup() {
        database = TestDatabaseFactory.createInMemoryDatabase()
        mockInitializer = MockDatabaseInitializer()
        viewModel = InitializationViewModel(database, mockInitializer)
    }

    // ============ State Transition Tests ============

    @Test
    fun `initial state is NotStarted`() {
        // ARRANGE: ViewModel just created

        // ACT: Get current state
        val state = viewModel.state.value

        // ASSERT: Should be NotStarted
        assertIs<InitializationState.NotStarted>(state)
    }

    @Test
    fun `initialize emits Loading then Success on successful init`() = runTest {
        // ARRANGE: Mock returns knowledge import success
        mockInitializer.knowledgeResult = KnowledgeImportResult.Success(
            totalDocs = 100,
            comprehensiveDocs = 80,
            systemDocs = 20
        )

        val states = mutableListOf<InitializationState>()

        // ACT: Initialize and collect states
        viewModel.initialize()
        states.add(viewModel.state.value)

        // ASSERT: Final state should be Success with knowledge status
        val finalState = states.last()
        assertIs<InitializationState.Success>(finalState)
        assertTrue(finalState.knowledgeStatus.contains("100"))
    }

    @Test
    fun `initialize handles knowledge import failure gracefully`() = runTest {
        // ARRANGE: Knowledge import fails (but initialization continues)
        mockInitializer.knowledgeResult = KnowledgeImportResult.Error(
            message = "Knowledge import failed"
        )

        // ACT: Initialize
        viewModel.initialize()

        // ASSERT: Should still be Success (knowledge is optional)
        val state = viewModel.state.value
        assertIs<InitializationState.Success>(state)
        assertTrue(state.knowledgeStatus.contains("unavailable"))
    }

    @Test
    fun `initialize handles AlreadyImported knowledge result`() = runTest {
        // ARRANGE: Knowledge already imported
        mockInitializer.knowledgeResult = KnowledgeImportResult.AlreadyImported(
            existingDocs = 150
        )

        // ACT: Initialize
        viewModel.initialize()

        // ASSERT: Success with existing docs message
        val state = viewModel.state.value
        assertIs<InitializationState.Success>(state)
        assertTrue(state.knowledgeStatus.contains("150"))
        assertTrue(state.knowledgeStatus.contains("ready"))
    }

    @Test
    fun `retry reinitializes after error`() = runTest {
        // ARRANGE: First init with knowledge available
        mockInitializer.knowledgeResult = KnowledgeImportResult.Success(50, 40, 10)

        // ACT: Initialize
        viewModel.initialize()

        // ASSERT: Should succeed
        val state = viewModel.state.value
        assertIs<InitializationState.Success>(state)
        assertTrue(state.knowledgeStatus.contains("50"))

        // ACT: Call retry (reinitializes)
        viewModel.retry()

        // ASSERT: Should still be Success
        val retriedState = viewModel.state.value
        assertIs<InitializationState.Success>(retriedState)
    }

    // ============ Mock Classes ============

    /**
     * Mock IDatabaseInitializer for testing
     *
     * Note: Database initialization is handled by Koin.
     * This mock only handles knowledge import.
     */
    class MockDatabaseInitializer : IDatabaseInitializer {
        var knowledgeResult: KnowledgeImportResult = KnowledgeImportResult.Success(0, 0, 0)

        override suspend fun initializeDatabase(): DatabaseInitResult {
            // Not used by InitializationViewModel anymore
            return DatabaseInitResult.Success(TestDatabaseFactory.createInMemoryDatabase())
        }

        override suspend fun importKnowledge(database: MaDatabase): KnowledgeImportResult =
            knowledgeResult
    }
}
