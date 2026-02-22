package app.m1k3.ai.domain.usecases.chat

import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.ai.LlmEngine
import app.m1k3.ai.domain.ai.GenerationResult
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.events.MessageEvent
import app.m1k3.ai.domain.chat.services.ContextRetrieverInterface
import app.m1k3.ai.domain.config.GenerationConfigBuilder
import app.m1k3.ai.domain.platform.DeviceInfoProviderInterface
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for domain SendMessageUseCase.
 *
 * Tests message flow orchestration with test doubles.
 */
class SendMessageUseCaseTest {

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
        ),
        private val ragEnabled: Boolean = false
    ) : ContextRetrieverInterface {
        override suspend fun retrieveContext(prompt: String): EnrichedContext = context
        override fun isRagEnabled(): Boolean = ragEnabled
    }

    private class FakeLlmEngine(
        private var responseText: String = "Hello!",
        private var shouldFail: Boolean = false,
        private var errorMessage: String = "Test error"
    ) : LlmEngine {
        var lastPrompt: String? = null
        var lastConfig: GenerationConfig? = null
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
            lastConfig = config
            streamingCallCount++

            if (shouldFail) {
                return Result.failure(RuntimeException(errorMessage))
            }

            // Simulate token-by-token generation
            responseText.split(" ").forEach { token ->
                onToken(token)
            }

            return Result.success(Unit)
        }

        override fun getOptimalMaxTokens(): Int = 256
        override fun release() {}
    }

    // ===== Helper Functions =====

    private fun createUseCase(
        engine: FakeLlmEngine = FakeLlmEngine(),
        contextRetriever: FakeContextRetriever = FakeContextRetriever()
    ): SendMessageUseCase {
        val configBuilder = GenerationConfigBuilder(FakeDeviceInfo())
        return SendMessageUseCase(
            aiEngine = engine,
            contextRetrieval = contextRetriever,
            configBuilder = configBuilder
        )
    }

    // ===== Basic Flow Tests =====

    @Test
    fun `execute emits Started event first`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertIs<MessageEvent.Started>(events.first())
    }

    @Test
    fun `execute emits RetrievingContext event`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is MessageEvent.RetrievingContext })
    }

    @Test
    fun `execute emits ContextRetrieved event`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is MessageEvent.ContextRetrieved })
    }

    @Test
    fun `execute emits Complete event on success`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertTrue(events.any { it is MessageEvent.Complete })
    }

    @Test
    fun `execute events are in correct order`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Hello").toList()

        assertEquals(4, events.size)
        assertIs<MessageEvent.Started>(events[0])
        assertIs<MessageEvent.RetrievingContext>(events[1])
        assertIs<MessageEvent.ContextRetrieved>(events[2])
        assertIs<MessageEvent.Complete>(events[3])
    }

    // ===== Complete Response Tests =====

    @Test
    fun `execute Complete event contains generated text`() = runTest {
        val engine = FakeLlmEngine(responseText = "Hello world!")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val complete = events.filterIsInstance<MessageEvent.Complete>().first()

        assertEquals("Hello world!", complete.response.text)
    }

    @Test
    fun `execute Complete event contains generation stats`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Test").toList()
        val complete = events.filterIsInstance<MessageEvent.Complete>().first()

        assertTrue(complete.response.stats.tokenCount > 0)
        assertTrue(complete.response.stats.durationMs >= 0)
    }

    @Test
    fun `execute Complete response indicates no RAG used when disabled`() = runTest {
        val useCase = createUseCase()
        val events = useCase.execute("Test").toList()
        val complete = events.filterIsInstance<MessageEvent.Complete>().first()

        assertFalse(complete.response.usedRag)
        assertFalse(complete.response.usedMemory)
    }

    // ===== Error Handling Tests =====

    @Test
    fun `execute emits Failed event on streaming error`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("Something went wrong")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()

        assertTrue(events.any { it is MessageEvent.Failed })
    }

    @Test
    fun `execute Failed event contains ChatError`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("Something went wrong")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.Unknown>(failed.error)
    }

    @Test
    fun `execute maps OutOfMemory error correctly`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("OutOfMemory in native code")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.OutOfMemory>(failed.error)
    }

    @Test
    fun `execute maps Timeout error correctly`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("Request timeout exceeded")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.Timeout>(failed.error)
    }

    @Test
    fun `execute maps Model error correctly`() = runTest {
        val engine = FakeLlmEngine()
        engine.setStreamingError("Model inference failed")
        val useCase = createUseCase(engine = engine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.ModelError>(failed.error)
    }

    // ===== ExecuteSimple Tests =====

    @Test
    fun `executeSimple emits Started event`() = runTest {
        val useCase = createUseCase()
        val events = useCase.executeSimple("Say hello").toList()

        assertIs<MessageEvent.Started>(events.first())
    }

    @Test
    fun `executeSimple emits Complete event on success`() = runTest {
        val useCase = createUseCase()
        val events = useCase.executeSimple("Say hello").toList()

        assertTrue(events.any { it is MessageEvent.Complete })
    }

    @Test
    fun `executeSimple does not retrieve context`() = runTest {
        val useCase = createUseCase()
        val events = useCase.executeSimple("Say hello").toList()

        assertFalse(events.any { it is MessageEvent.RetrievingContext })
        assertFalse(events.any { it is MessageEvent.ContextRetrieved })
    }

    @Test
    fun `executeSimple respects maxTokens parameter`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.executeSimple("Test", maxTokens = 50).toList()

        assertEquals(50, engine.lastConfig?.maxTokens)
    }

    @Test
    fun `executeSimple defaults to 100 maxTokens`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.executeSimple("Test").toList()

        assertEquals(100, engine.lastConfig?.maxTokens)
    }

    @Test
    fun `executeSimple response has null context`() = runTest {
        val useCase = createUseCase()
        val events = useCase.executeSimple("Test").toList()
        val complete = events.filterIsInstance<MessageEvent.Complete>().first()

        assertNull(complete.response.context)
    }

    // ===== Engine Interaction Tests =====

    @Test
    fun `execute calls engine generateStreaming`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.execute("Test prompt").toList()

        assertEquals(1, engine.streamingCallCount)
    }

    @Test
    fun `execute passes prompt to engine`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.execute("My test prompt").toList()

        assertTrue(engine.lastPrompt?.contains("My test prompt") == true)
    }

    @Test
    fun `executeSimple calls engine generateStreaming`() = runTest {
        val engine = FakeLlmEngine()
        val useCase = createUseCase(engine = engine)

        useCase.executeSimple("Test").toList()

        assertEquals(1, engine.streamingCallCount)
    }
}
