package app.m1k3.ai.assistant.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.memory.MemoryManager
import app.m1k3.ai.assistant.platform.DeviceInfoProvider
import app.m1k3.ai.assistant.platform.PreferencesStore
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.domain.tools.services.ToolRegistry
import app.m1k3.ai.domain.usecases.chat.ProcessLlmOutputUseCase
import kotlinx.coroutines.CoroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Remember a ChatScreenViewModel scoped to the composition.
 *
 * This composable creates and remembers a ChatScreenViewModel with all
 * required dependencies properly initialized.
 *
 * **Usage:**
 * ```kotlin
 * val viewModel = rememberChatScreenViewModel(
 *     aiEngine = engine,
 *     database = database,
 *     context = LocalContext.current,
 *     projectId = "default"
 * )
 *
 * val uiState by viewModel.collectAsState()
 * ```
 *
 * @param aiEngine The AI engine for generation
 * @param database The database for persistence
 * @param context Android context for platform dependencies
 * @param projectId Project ID for scoping messages
 * @param embeddingEngine Optional embedding engine for RAG (null = RAG disabled)
 * @return ChatScreenViewModel instance
 */
@Composable
fun rememberChatScreenViewModel(
    aiEngine: BaseLlmEngine,
    database: MaDatabase,
    context: Context,
    projectId: String,
    embeddingEngine: EmbeddingEngine? = null
): ChatScreenViewModel {
    val scope = rememberCoroutineScope()

    return remember(projectId, aiEngine, database) {
        createChatScreenViewModel(
            aiEngine = aiEngine,
            database = database,
            context = context,
            projectId = projectId,
            scope = scope,
            embeddingEngine = embeddingEngine
        )
    }
}

/**
 * Koin helper for dependency injection.
 */
private object ViewModelDependencies : KoinComponent {
    val toolRegistry: ToolRegistry? by lazy {
        try {
            val registry: ToolRegistry by inject()
            registry
        } catch (e: Exception) {
            null // Tools not available
        }
    }

    val processLlmOutput: ProcessLlmOutputUseCase? by lazy {
        try {
            val useCase: ProcessLlmOutputUseCase by inject()
            useCase
        } catch (e: Exception) {
            null // Tools not available
        }
    }
}

/**
 * Create a ChatScreenViewModel with all dependencies.
 *
 * This is the factory function that wires up all dependencies.
 */
fun createChatScreenViewModel(
    aiEngine: BaseLlmEngine,
    database: MaDatabase,
    context: Context,
    projectId: String,
    scope: CoroutineScope,
    embeddingEngine: EmbeddingEngine?
): ChatScreenViewModel {
    // Create repositories
    val conversationRepo = ConversationRepository(database)
    val ecoMetricsRepo = EcoMetricsRepository(database)

    // Create platform dependencies
    val deviceInfo = DeviceInfoProvider(context)
    val preferences = PreferencesStore(context)

    // Create optional RAG manager
    val ragManager = embeddingEngine?.let { engine ->
        RAGManager(database, engine)
    }

    // Create optional memory manager
    // Note: MemoryManager requires additional setup that would need to be passed in
    val memoryManager: MemoryManager? = null // TODO: Add MemoryManager when available

    // Get tool calling dependencies from Koin (optional - may not be registered yet)
    val toolRegistry = ViewModelDependencies.toolRegistry
    val processLlmOutput = ViewModelDependencies.processLlmOutput

    return ChatScreenViewModel(
        aiEngine = aiEngine,
        conversationRepo = conversationRepo,
        ecoMetricsRepo = ecoMetricsRepo,
        database = database,
        deviceInfo = deviceInfo,
        preferences = preferences,
        scope = scope,
        projectId = projectId,
        memoryManager = memoryManager,
        ragManager = ragManager,
        toolRegistry = toolRegistry,
        processLlmOutput = processLlmOutput
    )
}
