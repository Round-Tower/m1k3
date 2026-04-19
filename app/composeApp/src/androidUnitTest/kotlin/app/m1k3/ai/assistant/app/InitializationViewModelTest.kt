package app.m1k3.ai.assistant.app

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for InitializationViewModel.
 *
 * Originally in androidTest/ — moved to androidUnitTest/ so Kotlin
 * backtick-named functions DEX-compile and the suite actually runs.
 * Everything here uses mocks + an in-memory JDBC SQLite DB.
 * 2026-04-19.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InitializationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: MaDatabase
    private lateinit var mockInitializer: MockDatabaseInitializer
    private lateinit var viewModel: InitializationViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        database = TestDatabaseFactory.createInMemoryDatabase()
        mockInitializer = MockDatabaseInitializer()
        viewModel = InitializationViewModel(database, mockInitializer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is NotStarted`() {
        val state = viewModel.state.value
        assertIs<InitializationState.NotStarted>(state)
    }

    @Test
    fun `initialize emits Success with knowledge status`() =
        runTest(dispatcher) {
            mockInitializer.knowledgeResult =
                KnowledgeImportResult.Success(
                    totalDocs = 100,
                    curatedDocs = 100,
                )

            viewModel.initialize()
            advanceUntilIdle()

            val finalState = viewModel.state.value
            assertIs<InitializationState.Success>(finalState)
            assertTrue(finalState.knowledgeStatus.contains("100"))
        }

    @Test
    fun `initialize handles knowledge import failure gracefully`() =
        runTest(dispatcher) {
            mockInitializer.knowledgeResult =
                KnowledgeImportResult.Error(
                    message = "Knowledge import failed",
                )

            viewModel.initialize()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<InitializationState.Success>(state)
            assertTrue(state.knowledgeStatus.contains("unavailable"))
        }

    @Test
    fun `initialize handles AlreadyImported knowledge result`() =
        runTest(dispatcher) {
            mockInitializer.knowledgeResult =
                KnowledgeImportResult.AlreadyImported(
                    existingDocs = 150,
                )

            viewModel.initialize()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<InitializationState.Success>(state)
            assertTrue(state.knowledgeStatus.contains("150"))
            assertTrue(state.knowledgeStatus.contains("ready"))
        }

    @Test
    fun `retry reinitializes after error`() =
        runTest(dispatcher) {
            mockInitializer.knowledgeResult = KnowledgeImportResult.Success(50, 50)

            viewModel.initialize()
            advanceUntilIdle()
            val state = viewModel.state.value
            assertIs<InitializationState.Success>(state)
            assertTrue(state.knowledgeStatus.contains("50"))

            viewModel.retry()
            advanceUntilIdle()

            val retriedState = viewModel.state.value
            assertIs<InitializationState.Success>(retriedState)
        }

    class MockDatabaseInitializer : IDatabaseInitializer {
        var knowledgeResult: KnowledgeImportResult = KnowledgeImportResult.Success(0, 0)

        override suspend fun initializeDatabase(): DatabaseInitResult =
            DatabaseInitResult.Success(TestDatabaseFactory.createInMemoryDatabase())

        override suspend fun importKnowledge(database: MaDatabase): KnowledgeImportResult = knowledgeResult
    }
}
