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

/**
 * Unit tests for InitializationViewModel.
 *
 * Knowledge seeding retired 2026-04-20 — the VM now just opens the DB
 * and emits the state. These tests cover: initial state, success path,
 * database-init failure, retry-after-error.
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
    fun initialStateIsNotStarted() {
        assertIs<InitializationState.NotStarted>(viewModel.state.value)
    }

    @Test
    fun initializeEmitsSuccessOnDatabaseOpen() =
        runTest(dispatcher) {
            viewModel.initialize()
            advanceUntilIdle()

            assertIs<InitializationState.Success>(viewModel.state.value)
        }

    @Test
    fun initializeEmitsErrorWhenDatabaseFails() =
        runTest(dispatcher) {
            mockInitializer.dbResult = DatabaseInitResult.Error("boom", null)

            viewModel.initialize()
            advanceUntilIdle()

            assertIs<InitializationState.Error>(viewModel.state.value)
        }

    @Test
    fun retryReinitializesAfterError() =
        runTest(dispatcher) {
            mockInitializer.dbResult = DatabaseInitResult.Error("boom", null)
            viewModel.initialize()
            advanceUntilIdle()
            assertIs<InitializationState.Error>(viewModel.state.value)

            mockInitializer.dbResult = DatabaseInitResult.Success(database)
            viewModel.retry()
            advanceUntilIdle()

            assertIs<InitializationState.Success>(viewModel.state.value)
        }

    class MockDatabaseInitializer : IDatabaseInitializer {
        var dbResult: DatabaseInitResult =
            DatabaseInitResult.Success(
                TestDatabaseFactory.createInMemoryDatabase(),
            )

        override suspend fun initializeDatabase(): DatabaseInitResult = dbResult
    }
}
