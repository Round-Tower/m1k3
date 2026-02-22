package app.m1k3.ai.assistant.app

/**
 * Simple logging interface for testing
 * Allows implementations to use either KermitLogger or test mocks
 */
interface ILogger {
    fun i(message: String)
    fun e(error: Throwable?, message: String)
}

/**
 * Sealed class representing initialization result
 *
 * Type-safe result handling without exceptions for expected failures
 * Follows project pattern (e.g., DatabaseInitResult, KnowledgeImportResult)
 *
 * Variants:
 * - Success: Both Koin and Filament (or whichever component) initialized
 * - KoinSetupFailed: Koin DI initialization failed with exception
 * - FilamentSetupFailed: Filament 3D engine initialization failed with exception
 */
sealed class InitializationResult {
    data object Success : InitializationResult()
    data class KoinSetupFailed(val error: Exception) : InitializationResult()
    data class FilamentSetupFailed(val error: Exception) : InitializationResult()
}

/**
 * AppInitializationManager
 *
 * Handles Koin DI and Filament 3D engine initialization
 *
 * **Responsibilities:**
 * - Initialize Koin dependency injection framework
 * - Initialize Filament 3D rendering engine
 * - Return type-safe results instead of throwing exceptions
 * - Log all initialization steps for debugging
 * - Ensure Koin initializes before Filament
 *
 * **Error Handling:**
 * - Catches initialization exceptions
 * - Returns sealed failure types (KoinSetupFailed, FilamentSetupFailed)
 * - Logs errors for debugging
 * - Allows partial success (e.g., Koin succeeds but Filament fails)
 *
 * **Dependencies (Injected for testability):**
 * - logger: KermitLogger for debug/info/error messages
 * - koinInitializer: Callable to initialize Koin (mockable for tests)
 * - filamentInitializer: Callable to initialize Filament (mockable for tests)
 *
 * **Pattern:**
 * - Pure logic, no static methods
 * - Fully mockable dependencies
 * - Testable without Android context
 *
 * **Usage:**
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private val logger = Logger.withTag("MainActivity")
 *     private val appInitManager = AppInitializationManager(
 *         logger = logger,
 *         koinInitializer = {
 *             startKoin {
 *                 androidContext(this@MainActivity)
 *                 modules(allModules)
 *             }
 *         },
 *         filamentInitializer = { FilamentSetup.initialize() }
 *     )
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         val koinResult = appInitManager.initializeKoin()
 *         when (koinResult) {
 *             is InitializationResult.Success -> logger.i("Koin ready")
 *             is InitializationResult.KoinSetupFailed -> logger.e(koinResult.error, "Koin failed")
 *             else -> {}
 *         }
 *
 *         val filamentResult = appInitManager.initializeFilament()
 *         when (filamentResult) {
 *             is InitializationResult.Success -> logger.i("Filament ready")
 *             is InitializationResult.FilamentSetupFailed -> logger.e(filamentResult.error, "Filament failed")
 *             else -> {}
 *         }
 *     }
 * }
 * ```
 */
class AppInitializationManager(
    private val logger: ILogger,
    private val koinInitializer: () -> Unit = { /* Implemented in Android */ },
    private val filamentInitializer: () -> Unit = { /* Implemented in Android */ }
) {

    /**
     * Initialize Koin dependency injection framework
     *
     * Wraps Koin initialization with error handling and logging
     *
     * @return InitializationResult.Success or InitializationResult.KoinSetupFailed
     */
    fun initializeKoin(): InitializationResult {
        return try {
            koinInitializer()
            logger.i("Koin DI initialized successfully")
            InitializationResult.Success
        } catch (e: Exception) {
            logger.e(e, "Failed to initialize Koin DI")
            InitializationResult.KoinSetupFailed(e)
        }
    }

    /**
     * Initialize Filament 3D rendering engine
     *
     * Wraps Filament initialization with error handling and logging
     *
     * @return InitializationResult.Success or InitializationResult.FilamentSetupFailed
     */
    fun initializeFilament(): InitializationResult {
        return try {
            filamentInitializer()
            logger.i("Filament 3D engine initialized")
            InitializationResult.Success
        } catch (e: Exception) {
            logger.e(e, "Failed to initialize Filament 3D engine")
            InitializationResult.FilamentSetupFailed(e)
        }
    }
}
