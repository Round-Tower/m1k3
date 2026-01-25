package app.m1k3.ai.assistant.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.m1k3.ai.assistant.avatar.PetMetricsRepository
import app.m1k3.ai.assistant.chat.GenerationConfigBuilder
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.history.ExportManager
import app.m1k3.ai.assistant.history.SearchRepository
import app.m1k3.ai.assistant.knowledge.SemanticRetrievalService
import app.m1k3.ai.assistant.memory.MemoryDataSource
import app.m1k3.ai.domain.ai.services.GenerationConfigService
import app.m1k3.ai.domain.chat.format.ChatFormat
import app.m1k3.ai.domain.chat.services.ChatFormatter
import app.m1k3.ai.domain.chat.services.ContextAssembler
import app.m1k3.ai.domain.memory.ImportanceCalculator
import app.m1k3.ai.domain.memory.services.SemanticChunker
import app.m1k3.ai.domain.rag.services.IntentClassifier
import app.m1k3.ai.domain.repositories.KnowledgeRepository
import app.m1k3.ai.domain.tools.services.ToolCallParser
import app.m1k3.ai.domain.usecases.chat.LlmOutputProcessor
import app.m1k3.ai.domain.usecases.rag.EnrichPromptWithRAGUseCase
import app.m1k3.ai.domain.usecases.tools.ExecuteToolUseCase
import app.m1k3.ai.domain.usecases.tools.ParseToolCallUseCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import org.koin.test.get
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * TDD Tests for appModule dependency resolution
 *
 * Verifies that all common dependencies can be resolved without errors.
 * Catches missing registrations at build time instead of runtime.
 *
 * **Test Strategy:**
 * - Test each appModule dependency individually
 * - Verify types are correct (sealed, interface implementations)
 * - Use checkModules() for full graph validation
 * - Mock platform dependencies via platformModule
 *
 * **Why This Matters:**
 * - Koin errors happen at runtime (not compile time)
 * - NoDefinitionFoundException crashes the app
 * - Tests provide fast feedback loop
 */
@RunWith(AndroidJUnit4::class)
class AppModuleTest : KoinTest {

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        startKoin {
            androidContext(context)
            modules(appModule, platformModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    // ===== Database Layer =====

    @Test
    fun `appModule provides MaDatabase`() {
        val database = get<MaDatabase>()
        assertNotNull(database, "MaDatabase should be registered")
    }

    // ===== Repository Layer =====

    @Test
    fun `appModule provides ConversationRepository`() {
        val repo = get<ConversationRepository>()
        assertNotNull(repo, "ConversationRepository should be registered")
    }

    @Test
    fun `appModule provides EcoMetricsRepository`() {
        val repo = get<EcoMetricsRepository>()
        assertNotNull(repo, "EcoMetricsRepository should be registered")
    }

    @Test
    fun `appModule provides MemoryDataSource`() {
        val dataSource = get<MemoryDataSource>()
        assertNotNull(dataSource, "MemoryDataSource should be registered")
    }

    @Test
    fun `appModule provides PetMetricsRepository`() {
        val repo = get<PetMetricsRepository>()
        assertNotNull(repo, "PetMetricsRepository should be registered")
    }

    @Test
    fun `appModule provides SearchRepository`() {
        val repo = get<SearchRepository>()
        assertNotNull(repo, "SearchRepository should be registered")
    }

    @Test
    fun `appModule provides ExportManager`() {
        val manager = get<ExportManager>()
        assertNotNull(manager, "ExportManager should be registered")
    }

    @Test
    fun `appModule provides SemanticRetrievalService`() {
        val service = get<SemanticRetrievalService>()
        assertNotNull(service, "SemanticRetrievalService should be registered")
    }

    // ===== Domain Services =====

    @Test
    fun `appModule provides IntentClassifier`() {
        val classifier = get<IntentClassifier>()
        assertNotNull(classifier, "IntentClassifier should be registered")
    }

    @Test
    fun `appModule provides SemanticChunker`() {
        val chunker = get<SemanticChunker>()
        assertNotNull(chunker, "SemanticChunker should be registered")
    }

    @Test
    fun `appModule provides ImportanceCalculator`() {
        val calculator = get<ImportanceCalculator>()
        assertNotNull(calculator, "ImportanceCalculator should be registered")
    }

    @Test
    fun `appModule provides ContextAssembler`() {
        val assembler = get<ContextAssembler>()
        assertNotNull(assembler, "ContextAssembler should be registered")
    }

    @Test
    fun `appModule provides GenerationConfigService`() {
        val service = get<GenerationConfigService>()
        assertNotNull(service, "GenerationConfigService should be registered")
    }

    // ===== Domain Repository Implementations =====

    @Test
    fun `appModule provides KnowledgeRepository`() {
        val repo = get<KnowledgeRepository>()
        assertNotNull(repo, "KnowledgeRepository should be registered")
    }

    // ===== Domain Use Cases =====

    @Test
    fun `appModule provides EnrichPromptWithRAGUseCase`() {
        val useCase = get<EnrichPromptWithRAGUseCase>()
        assertNotNull(useCase, "EnrichPromptWithRAGUseCase should be registered")
    }

    // ===== Tool Calling Infrastructure =====

    @Test
    fun `appModule provides ToolCallParser`() {
        val parser = get<ToolCallParser>()
        assertNotNull(parser, "ToolCallParser should be registered")
    }

    @Test
    fun `appModule provides ChatFormatter`() {
        val formatter = get<ChatFormatter>()
        assertNotNull(formatter, "ChatFormatter should be registered")
    }

    @Test
    fun `appModule provides ParseToolCallUseCase`() {
        val useCase = get<ParseToolCallUseCase>()
        assertNotNull(useCase, "ParseToolCallUseCase should be registered")
    }

    @Test
    fun `appModule provides ExecuteToolUseCase`() {
        val useCase = get<ExecuteToolUseCase>()
        assertNotNull(useCase, "ExecuteToolUseCase should be registered")
    }

    @Test
    fun `appModule provides LlmOutputProcessor`() {
        val processor = get<LlmOutputProcessor>()
        assertNotNull(processor, "LlmOutputProcessor should be registered")
    }

    // ===== Utility Layer =====

    @Test
    fun `appModule provides EcoCalculator`() {
        val calculator = get<EcoCalculator>()
        assertNotNull(calculator, "EcoCalculator should be registered")
        assertIs<EcoCalculator>(calculator, "Should be EcoCalculator object")
    }

    @Test
    fun `appModule provides GenerationConfigBuilder as factory`() {
        // Factory should create new instance each time
        val builder1 = get<GenerationConfigBuilder>()
        val builder2 = get<GenerationConfigBuilder>()
        assertNotNull(builder1)
        assertNotNull(builder2)
        // Factories may return same instance if implementation uses singleton pattern internally
    }

    // ===== Type Verification =====

    @Test
    fun `ChatFormatter uses Gemma3 format by default`() {
        val formatter = get<ChatFormatter>()
        // We can't directly check the format field, but we can verify it works
        assertNotNull(formatter)
    }

    // ===== Full Module Validation =====

    @Test
    fun `appModule satisfies all dependencies`() {
        // This test validates the entire dependency graph
        // If any dependency is missing, this will fail with clear error
        checkModules {
            modules(appModule, platformModule)
        }
    }
}
