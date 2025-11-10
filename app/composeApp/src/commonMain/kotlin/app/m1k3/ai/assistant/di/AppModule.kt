package app.m1k3.ai.assistant.di

import app.m1k3.ai.assistant.avatar.AvatarViewModel
import app.m1k3.ai.assistant.avatar.PetMetricsRepository
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.history.SearchRepository
import app.m1k3.ai.assistant.memory.MemoryRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * 間 AI Koin Dependency Injection Modules
 *
 * Provides centralized dependency management for:
 * - ViewModels (ChatViewModel, AvatarViewModel)
 * - Repositories (Conversation, EcoMetrics, Memory)
 * - Database (SQLDelight instance)
 * - AI Engine (LlamaCppEngine)
 *
 * Architecture Benefits:
 * - ✅ Testability: Easy to inject mocks
 * - ✅ Modularity: Clear separation of concerns
 * - ✅ Lifecycle management: Koin handles scopes
 * - ✅ Type safety: Compile-time dependency resolution
 *
 * Usage:
 * ```kotlin
 * // In MainActivity.kt
 * startKoin {
 *     modules(appModule, platformModule)
 * }
 *
 * // In composables
 * @Composable
 * fun ChatScreen() {
 *     val chatVM: ChatViewModel = koinViewModel()
 *     val avatarVM: AvatarViewModel = koinViewModel()
 *     // ...
 * }
 * ```
 */

/**
 * Core application module
 *
 * Contains common dependencies shared across all platforms.
 */
val appModule = module {
    // ===== Database Layer =====

    /**
     * SQLDelight database instance
     *
     * Platform-specific driver injected via platformModule.
     * Singleton ensures single database connection.
     */
    single<MaDatabase> {
        get<DatabaseFactory>().createDatabase()
    }

    // ===== Repository Layer =====

    /**
     * Conversation repository
     *
     * Manages chat history and conversation metadata.
     */
    singleOf(::ConversationRepository)

    /**
     * Eco metrics repository
     *
     * Tracks environmental impact (water, energy, CO2 savings).
     */
    singleOf(::EcoMetricsRepository)

    /**
     * Memory repository
     *
     * Manages semantic memory chunks with HNSW vector index.
     */
    singleOf(::MemoryRepository)

    /**
     * Pet metrics repository
     *
     * Tracks pixel pet stats (happiness, energy, health).
     */
    singleOf(::PetMetricsRepository)

    /**
     * Search repository
     *
     * Handles full-text search across conversations and messages.
     */
    singleOf(::SearchRepository)

    // ===== Utility Layer =====

    /**
     * Eco calculator
     *
     * Calculates environmental savings vs cloud AI.
     * Stateless, so can be singleton.
     */
    single { EcoCalculator }

    // ===== ViewModel Layer =====

    // TODO: Add ViewModels once we resolve CoroutineScope injection
    // Avatar ViewModel requires CoroutineScope parameter
}

/**
 * Platform-specific module
 *
 * Implemented in each platform's source set:
 * - androidMain: Android-specific dependencies (SQLDelight Android driver)
 * - iosMain: iOS-specific dependencies (SQLDelight Native driver)
 * - jvmMain: JVM-specific dependencies (SQLDelight JDBC driver)
 */
expect val platformModule: Module

/**
 * All modules combined
 *
 * Convenience property for startKoin { modules(...) }
 */
val allModules = listOf(appModule, platformModule)

/**
 * Usage Examples:
 * ```kotlin
 * // ===== In MainActivity.kt (Android) =====
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         // Initialize Koin DI
 *         startKoin {
 *             androidContext(this@MainActivity)
 *             modules(allModules)
 *         }
 *
 *         setContent {
 *             App()
 *         }
 *     }
 * }
 *
 * // ===== In Composables =====
 * @Composable
 * fun ChatScreen() {
 *     // Inject ViewModels via Koin
 *     val chatVM: ChatViewModel = koinViewModel()
 *     val avatarVM: AvatarViewModel = koinViewModel()
 *
 *     // Use ViewModels
 *     val messages by chatVM.messages.collectAsState()
 *     val avatarState by avatarVM.avatarState.collectAsState()
 *
 *     // ... UI
 * }
 *
 * // ===== In Tests =====
 * class ChatViewModelTest {
 *     @Test
 *     fun testSendMessage() {
 *         startKoin {
 *             modules(
 *                 module {
 *                     single<BaseLlmEngine> { MockLlmEngine() }
 *                     single<ConversationRepository> { MockConversationRepo() }
 *                     viewModelOf(::ChatViewModel)
 *                 }
 *             )
 *         }
 *
 *         val chatVM: ChatViewModel = get()
 *         // ... test logic
 *     }
 * }
 *
 * // ===== Manual Dependency Injection (without Koin) =====
 * // If Koin initialization fails, you can still use manual DI:
 * val database = DatabaseFactory().createDatabase()
 * val conversationRepo = ConversationRepository(database)
 * val ecoMetricsRepo = EcoMetricsRepository(database)
 * val aiEngine = LlamaCppEngine("models/gemma-3-270m-it-Q2_K.gguf", 24576)
 * val chatViewModel = ChatViewModel(conversationRepo, ecoMetricsRepo, aiEngine)
 * ```
 */
