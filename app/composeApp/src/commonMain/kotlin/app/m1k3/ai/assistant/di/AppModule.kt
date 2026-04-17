package app.m1k3.ai.assistant.di

import androidx.lifecycle.viewmodel.compose.viewModel
import app.m1k3.ai.assistant.chat.ChatScreenViewModel
import app.m1k3.ai.assistant.avatar.PetMetricsRepository
import app.m1k3.ai.assistant.chat.GenerationConfigBuilder
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.history.SearchRepository
import app.m1k3.ai.assistant.history.ExportManager
import app.m1k3.ai.assistant.memory.MemoryDataSource
import app.m1k3.ai.assistant.memory.MemoryRanker
import app.m1k3.ai.assistant.tools.ToolExecutionDataSource
import app.m1k3.ai.assistant.platform.DeviceInfoProviderInterface
import app.m1k3.ai.domain.ai.services.GenerationConfigService
import app.m1k3.ai.domain.chat.services.ContextAssembler
import app.m1k3.ai.domain.chat.services.DeviceContextFormatter
import app.m1k3.ai.domain.chat.services.UnifiedPromptBuilder
import app.m1k3.ai.domain.memory.ImportanceCalculator
import app.m1k3.ai.domain.memory.services.SemanticChunker
import app.m1k3.ai.domain.rag.services.IntentClassifier
import app.m1k3.ai.domain.repositories.KnowledgeRepository
import app.m1k3.ai.domain.usecases.memory.SearchMemoriesUseCase
import app.m1k3.ai.domain.usecases.rag.EnrichPromptWithRAGUseCase
import app.m1k3.ai.domain.usecases.tools.ExecuteToolUseCase
import app.m1k3.ai.domain.usecases.tools.ParseToolCallUseCase
import app.m1k3.ai.domain.usecases.chat.LlmOutputProcessor
import app.m1k3.ai.domain.usecases.chat.ProcessLlmOutputUseCase
import app.m1k3.ai.domain.chat.format.ChatFormat
import app.m1k3.ai.domain.chat.services.ChatFormatter
import app.m1k3.ai.domain.chat.services.DefaultChatFormatter
import app.m1k3.ai.domain.tools.services.ToolCallParser
import app.m1k3.ai.domain.tools.services.DefaultToolCallParser
import app.m1k3.ai.assistant.knowledge.KnowledgeRepositoryImpl
import app.m1k3.ai.assistant.knowledge.SemanticRetrievalService
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.module.dsl.factoryOf

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
     * Memory data source
     *
     * Manages semantic memory metadata via SQLDelight.
     */
    singleOf(::MemoryDataSource)

    /**
     * Tool execution data source
     *
     * Persistent log of every tool call for analytics and debug history.
     */
    singleOf(::ToolExecutionDataSource)

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

    /**
     * ExportManager
     *
     * Handles conversation export to JSON and Markdown formats.
     */
    singleOf(::ExportManager)

    /**
     * Semantic retrieval service
     *
     * RAG knowledge retrieval using embedding-based semantic search.
     */
    single {
        SemanticRetrievalService(
            database = get(),
            embeddingEngine = get() // Platform-specific embedding engine
        )
    }

    // ===== Domain Services =====

    /**
     * Intent classifier
     *
     * Classifies user queries into 20 intent categories for RAG routing.
     */
    single { IntentClassifier() }

    /**
     * Semantic chunker
     *
     * Chunks long text into 100-300 token segments with semantic boundaries.
     */
    single { SemanticChunker() }

    /**
     * Importance calculator
     *
     * Calculates importance scores (0.0-1.0) for memory filtering.
     */
    single { ImportanceCalculator() }

    /**
     * Memory ranker
     *
     * Composite ranking for selecting optimal memories for AI context.
     * Balances similarity, recency, importance, and access frequency.
     */
    single { MemoryRanker() }

    /**
     * Context assembler
     *
     * Combines conversation history, RAG facts, and memories into unified context.
     */
    single { ContextAssembler() }

    /**
     * Unified prompt builder
     *
     * Single point for prompt construction with context + tool schemas.
     */
    single {
        UnifiedPromptBuilder(
            formatter = get<ChatFormatter>(),
            contextAssembler = get(),
            deviceContextFormatter = DeviceContextFormatter()
        )
    }

    /**
     * Generation config service
     *
     * Device-adaptive AI generation configuration (tokens, temperature).
     * Uses device RAM and query type for optimization.
     */
    single {
        val deviceInfo = get<DeviceInfoProviderInterface>()
        val ramGB = deviceInfo.getDeviceRamGB()
        val tier = when {
            ramGB >= 12 -> "Flagship"
            ramGB >= 8 -> "High-end"
            ramGB >= 6 -> "Mid-range"
            else -> "Budget"
        }
        GenerationConfigService(
            deviceRamGB = ramGB,
            deviceTier = tier
        )
    }

    // ===== Domain Repository Implementations =====

    /**
     * Knowledge repository
     *
     * Domain-layer wrapper around SemanticRetrievalService for RAG.
     */
    single<KnowledgeRepository> {
        KnowledgeRepositoryImpl(
            semanticRetrievalService = get() // SemanticRetrievalService registered in platform module
        )
    }

    // Note: MemoryRepository implementation will be registered in platform module
    // (MemoryRepositoryImpl wraps SemanticMemoryManager which requires Android Context)

    // ===== Domain Use Cases =====

    // Note: SearchMemoriesUseCase NOT registered here because MemoryRepository
    // requires projectId scoping. Create instances inline where needed:
    // val useCase = SearchMemoriesUseCase(memoryRepository)

    /**
     * Enrich prompt with RAG use case
     *
     * Orchestrates intent classification, knowledge retrieval, and category boosting.
     */
    single {
        EnrichPromptWithRAGUseCase(
            knowledgeRepository = get(),
            intentClassifier = get()
        )
    }

    // Note: CreateMemoryUseCase will be registered in platform module
    // (requires platform-specific EmbeddingRepository)

    // ===== Tool Calling Infrastructure =====

    /**
     * Tool call parser
     *
     * Parses LLM output for tool calls in JSON or XML format.
     */
    single<ToolCallParser> { DefaultToolCallParser() }

    /**
     * Chat formatter
     *
     * Formats prompts and tool schemas for LLM consumption.
     * Uses Gemma3 format as default (matches SmolLM2).
     */
    single<ChatFormatter> { DefaultChatFormatter(ChatFormat.Gemma3) }

    /**
     * Parse tool call use case
     *
     * Wraps parser with structured result.
     */
    single { ParseToolCallUseCase(parser = get()) }

    /**
     * Execute tool use case
     *
     * Orchestrates: find tool → validate → confirm? → execute.
     * ToolRegistry provided by platform module.
     */
    single { ExecuteToolUseCase(toolRegistry = get()) }

    /**
     * Process LLM output use case
     *
     * Orchestrates parsing and execution of tool calls from LLM output.
     * Primary integration point between LLM generation and tool system.
     */
    single<LlmOutputProcessor> {
        ProcessLlmOutputUseCase(parseToolCallUseCase = get(), executeToolUseCase = get())
    }

    // ===== Utility Layer =====

    /**
     * Eco calculator
     *
     * Calculates environmental savings vs cloud AI.
     * Stateless, so can be singleton.
     */
    single { EcoCalculator }

    // ===== Use Case Layer =====

    /**
     * GenerationConfigBuilder
     *
     * Builds device-adaptive AI generation configurations.
     * Uses DeviceInfoProvider for RAM-based token limit scaling.
     */
    factoryOf(::GenerationConfigBuilder)
    
    // ===== ViewModel Layer =====
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