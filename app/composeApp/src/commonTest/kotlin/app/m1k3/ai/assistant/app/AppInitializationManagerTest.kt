package app.m1k3.ai.assistant.app

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD Tests for AppInitializationManager
 *
 * Verifies Koin DI setup and Filament 3D engine initialization
 * Tests error handling with type-safe sealed results
 *
 * **Test Strategy (Red → Green → Refactor):**
 * - Uses MockLogger to capture logs without Kermit dependency
 * - Sealed class results avoid exceptions for expected failures
 * - AAA pattern: Arrange, Act, Assert
 * - Can inject failures via MockLogger for error path testing
 */
class AppInitializationManagerTest {

    private lateinit var mockLogger: MockLogger
    private lateinit var manager: AppInitializationManager

    private fun setup() {
        mockLogger = MockLogger()
        manager = AppInitializationManager(mockLogger)
    }

    // ============ Koin Initialization Tests ============

    @Test
    fun `initializeKoin succeeds on valid state`() {
        // GREEN: Verify Koin initialization returns Success
        setup()

        val result = manager.initializeKoin()

        assertIs<InitializationResult.Success>(result)
        assertTrue(mockLogger.debugMessages.any { it.contains("Koin") })
    }

    @Test
    fun `initializeKoin handles failure with error result`() {
        // GREEN: Verify Koin failure returns sealed result with exception
        setup()
        mockLogger.simulateKoinFailure = true

        val result = manager.initializeKoin()

        assertIs<InitializationResult.KoinSetupFailed>(result)
        val failResult = result as InitializationResult.KoinSetupFailed
        assertTrue(failResult.error.message?.contains("Koin") == true)
        assertTrue(mockLogger.errorMessages.isNotEmpty())
    }

    @Test
    fun `initializeKoin logs error on failure`() {
        // GREEN: Verify error is logged for debugging
        setup()
        mockLogger.simulateKoinFailure = true

        manager.initializeKoin()

        assertTrue(mockLogger.errorMessages.any { it.contains("Koin") })
    }

    // ============ Filament Initialization Tests ============

    @Test
    fun `initializeFilament succeeds on valid state`() {
        // GREEN: Verify Filament initialization returns Success
        setup()

        val result = manager.initializeFilament()

        assertIs<InitializationResult.Success>(result)
        assertTrue(mockLogger.debugMessages.any { it.contains("Filament") })
    }

    @Test
    fun `initializeFilament handles failure with error result`() {
        // GREEN: Verify Filament failure returns sealed result with exception
        setup()
        mockLogger.simulateFilamentFailure = true

        val result = manager.initializeFilament()

        assertIs<InitializationResult.FilamentSetupFailed>(result)
        val failResult = result as InitializationResult.FilamentSetupFailed
        assertTrue(failResult.error.message?.contains("Filament") == true)
    }

    // ============ Initialization Sequence Tests ============

    @Test
    fun `full initialization sequence succeeds with both components ready`() {
        // GREEN: Verify complete initialization sequence
        setup()

        val koinResult = manager.initializeKoin()
        val filamentResult = manager.initializeFilament()

        assertIs<InitializationResult.Success>(koinResult)
        assertIs<InitializationResult.Success>(filamentResult)
    }

    @Test
    fun `partial initialization continues despite Koin failure`() {
        // GREEN: Verify Filament succeeds even if Koin fails (independent)
        setup()
        mockLogger.simulateKoinFailure = true

        val koinResult = manager.initializeKoin()
        val filamentResult = manager.initializeFilament()

        assertIs<InitializationResult.KoinSetupFailed>(koinResult)
        assertIs<InitializationResult.Success>(filamentResult)
    }

    @Test
    fun `partial initialization continues despite Filament failure`() {
        // GREEN: Verify Koin succeeds even if Filament fails (independent)
        setup()
        mockLogger.simulateFilamentFailure = true

        val koinResult = manager.initializeKoin()
        val filamentResult = manager.initializeFilament()

        assertIs<InitializationResult.Success>(koinResult)
        assertIs<InitializationResult.FilamentSetupFailed>(filamentResult)
    }
}

/**
 * Mock Logger for testing
 *
 * Implements ILogger and captures calls for test assertions
 * Allows injecting failures for error path testing by detecting component names in messages
 */
class MockLogger : ILogger {
    val debugMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<String>()

    var simulateKoinFailure = false
    var simulateFilamentFailure = false

    override fun i(message: String) {
        debugMessages.add(message)
        if (simulateKoinFailure && message.contains("Koin")) {
            throw Exception("Koin initialization failed (simulated)")
        }
        if (simulateFilamentFailure && message.contains("Filament")) {
            throw Exception("Filament initialization failed (simulated)")
        }
    }

    override fun e(error: Throwable?, message: String) {
        errorMessages.add(message)
    }
}
