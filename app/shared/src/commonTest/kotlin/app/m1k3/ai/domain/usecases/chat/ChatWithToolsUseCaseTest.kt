package app.m1k3.ai.domain.usecases.chat

import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.ai.GenerationResult
import app.m1k3.ai.domain.ai.LlmEngine
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.events.ChatEvent
import app.m1k3.ai.domain.chat.services.ContextRetrieverInterface
import app.m1k3.ai.domain.config.GenerationConfigBuilder
import app.m1k3.ai.domain.platform.DeviceInfoProviderInterface
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor
import app.m1k3.ai.domain.tools.services.ToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for domain ChatWithToolsUseCase.
 *
 * Tests chat flow with tool calling capabilities using test doubles.
 */
class ChatWithToolsUseCaseTest {

    // ===== Test Doubles =====

    private class FakeDeviceInfo(private val ramGB: Int = 8) : DeviceInfoProviderInterface {
        override fun getDeviceRamGB(): Int = ramGB
        override fun getDeviceModel(): String = "Test Device"
        override fun getBatteryLevel(): Int? = 100
    }

    private class FakeContextRetriever(
        private val context: EnrichedContext = EnrichedContext(
            context = "",
            intentCategory = "GENERAL",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = false,
            hasMemoryContext = false
        )
    ) : ContextRetrieverInterface {
        override suspend fun retrieveContext(prompt: String): EnrichedContext = context
        override fun isRagEnabled(): Boolean = false
    }

    private class FakeLlmEngine(
        private var responseText: String = "Hello!",
        private var shouldFail: Boolean = false,
        private var errorMessage: String = "Test error"
    ) : LlmEngine {
        var lastPrompt: String? = null
        var streamingCallCount = 0

        fun setResponse(text: String) {
            responseText = text
        }

        fun setStreamingError(message: String) {
            shouldFail = true
            errorMessage = message
        }

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override suspend fun generate(prompt: String, config: GenerationConfig): Result<GenerationResult> {
            return Result.success(
                GenerationResult(
                    text = responseText,
                    tokensGenerated = 5,
                    inferenceTimeMs = 100,
                    tokensPerSecond = 50f
                )
            )
        }

        override suspend fun generateStreaming(
            prompt: String,
            config: GenerationConfig,
            onToken: (String) -> Unit
        ): Result<Unit> {
            lastPrompt = prompt
            streamingCallCount++

            if (shouldFail) {
                return Result.failure(RuntimeException(errorMessage))
            }

            responseText.split(" ").forEach { token ->
                onToken(token)
            }

            return Result.success(Unit)
        }

        override fun getOptimalMaxTokens(): Int = 256
        override fun release() {}
    }

    private class FakeToolRegistry(
        private val tools: List<Tool> = emptyList()
    ) : ToolRegistry {
        override fun getAllTools(): List<Tool> = tools
        override fun getToolsByCategory(category: ToolCategory): List<Tool> =
            tools.filter { it.category == category }
        override suspend fun getAvailableTools(): List<Tool> = tools
        override suspend fun getRelevantTools(query: String, maxTools: Int): List<Tool> =
            tools.take(maxTools)  // Simplified for testing
        override fun findTool(toolId: String): Tool? = tools.find { it.id == toolId }
        override suspend fun isToolAvailable(toolId: String): Boolean =
            tools.any { it.id == toolId }
        override fun registerTool(tool: Tool, executor: ToolExecutor) {}
        override fun getExecutor(toolId: String): ToolExecutor? = null
    }

    private class FakeLlmOutputProcessor : LlmOutputProcessor {
        var lastInput: String? = null

        override suspend fun execute(llmOutput: String, confirmedToolIds: Set<String>): ProcessedOutput {
            lastInput = llmOutput
            return ProcessedOutput.TextOnly(llmOutput)
        }

        override fun hasToolCalls(llmOutput: String): Boolean = false

        override fun extractPlainText(llmOutput: String): String = llmOutput
    }

    // ===== Helper Functions =====

    private fun createUseCase(
        engine: FakeLlmEngine = FakeLlmEngine(),
        contextRetriever: FakeContextRetriever = FakeContextRetriever(),
        llmOutputProcessor: FakeLlmOutputProcessor = FakeLlmOutputProcessor(),
        toolRegistry: FakeToolRegistry = FakeToolRegistry()
    ): ChatWithToolsUseCase {
        val configBuilder = GenerationConfigBuilder(FakeDeviceInfo())
        return ChatWithToolsUseCase(
            aiEngine = engine,
            contextRetrieval = contextRetriever,
            processLlmOutput = llmOutputProcessor,
            toolRegistry = toolRegistry,
            configBuilder = configBuilder
        )
    }

    // ===== Basic Flow Tests =====

    @Test
    fun `execute emits Started event first`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertIs<ChatEvent.Started>(events.first())
    }

    @Test
    fun `execute emits RetrievingContext event`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is ChatEvent.RetrievingContext })
    }

    @Test
    fun `execute emits ContextRetrieved event`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is ChatEvent.ContextRetrieved })
    }

    @Test
    fun `execute emits Generating event`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is ChatEvent.Generating })
    }

    @Test
    fun `execute emits Complete event on success`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is ChatEvent.Complete })
    }

    @Test
    fun `execute events are in correct order`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertEquals(5, events.size)
        assertIs<ChatEvent.Started>(events[0])
        assertIs<ChatEvent.RetrievingContext>(events[1])
        assertIs<ChatEvent.ContextRetrieved>(events[2])
        assertIs<ChatEvent.Generating>(events[3])
        assertIs<ChatEvent.Complete>(events[4])
    }

    // ===== Response Tests =====

    @Test
    fun `execute Complete event contains generated text`() = runTest {
        val engine = FakeLlmEngine(responseText = "Hello world!")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val complete = events.filterIsInstance<ChatEvent.Complete>().first()

        assertEquals("Hello world!", complete.response.text)
    }

    @Test
    fun `execute Complete event contains generation stats`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Test").toList()
        val complete = events.filterIsInstance<ChatEvent.Complete>().first()

        assertTrue(complete.response.stats.tokenCount > 0)
        assertTrue(complete.response.stats.durationMs >= 0)
    }

    // ===== Error Handling Tests =====

    @Test
    fun `execute emits Failed event on streaming error`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("Something went wrong")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()

        assertTrue(events.any { it is ChatEvent.Failed })
    }

    @Test
    fun `execute maps OutOfMemory error correctly`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("OutOfMemory in native code")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<ChatEvent.Failed>().first()

        assertIs<ChatError.OutOfMemory>(failed.error)
    }

    @Test
    fun `execute maps Timeout error correctly`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("Request timeout exceeded")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<ChatEvent.Failed>().first()

        assertIs<ChatError.Timeout>(failed.error)
    }

    // ===== Tool Schema Tests =====

    @Test
    fun `execute includes tool schemas in prompt when tools available`() = runTest {
        val tool = Tool(
            id = "battery_level",
            name = "Get Battery Level",
            description = "Gets the current battery level",
            parameters = emptyList(),
            category = ToolCategory.DEVICE_INFO
        )
        val toolRegistry = FakeToolRegistry(tools = listOf(tool))
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine, toolRegistry = toolRegistry)

        useCase.execute("What's my battery?").toList()

        assertTrue(engine.lastPrompt?.contains("battery_level") == true)
        assertTrue(engine.lastPrompt?.contains("Gets the current battery level") == true)
    }

    @Test
    fun `execute does not include tool schemas when no tools available`() = runTest {
        val toolRegistry = FakeToolRegistry(tools = emptyList())
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine, toolRegistry = toolRegistry)

        useCase.execute("Hello").toList()

        assertFalse(engine.lastPrompt?.contains("You have access to the following tools") == true)
    }

    // ===== Engine Interaction Tests =====

    @Test
    fun `execute calls engine generateStreaming`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.execute("Test").toList()

        assertEquals(1, engine.streamingCallCount)
    }

    @Test
    fun `execute passes prompt to engine`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.execute("My test prompt").toList()

        assertTrue(engine.lastPrompt?.contains("My test prompt") == true)
    }

    // ===== Process LLM Output Tests =====

    @Test
    fun `execute passes raw response to LlmOutputProcessor`() = runTest {
        val engine = FakeLlmEngine(responseText = "Generated response")
        val llmOutputProcessor = FakeLlmOutputProcessor()
        val useCase = createUseCase(engine = engine, llmOutputProcessor = llmOutputProcessor)

        useCase.execute("Test").toList()

        assertEquals("Generated response", llmOutputProcessor.lastInput)
    }
}
