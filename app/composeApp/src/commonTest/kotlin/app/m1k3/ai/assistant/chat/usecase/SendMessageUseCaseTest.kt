package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.chat.ChatError
import app.m1k3.ai.assistant.chat.GenerationConfigBuilder
import app.m1k3.ai.assistant.mocks.MockBaseLlmEngine
import app.m1k3.ai.assistant.mocks.MockDeviceInfoProvider
import app.m1k3.ai.assistant.mocks.TestPreferencesStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SendMessageUseCase.
 *
 * Tests message flow orchestration with mocked dependencies.
 */
class SendMessageUseCaseTest {

    // ===== Helper Functions =====

    private fun createUseCase(
        mockEngine: MockBaseLlmEngine = MockBaseLlmEngine.withGreeting()
    ): SendMessageUseCase {
        val deviceInfo = MockDeviceInfoProvider.midRange()
        val preferences = TestPreferencesStore()
        val contextRetrieval = ContextRetrievalUseCase(
            deviceInfo = deviceInfo,
            preferences = preferences,
            ragManager = null,
            memoryManager = null
        )
        val configBuilder = GenerationConfigBuilder(deviceInfo)

        return SendMessageUseCase(
            aiEngine = mockEngine,
            contextRetrieval = contextRetrieval,
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
        val mockEngine = MockBaseLlmEngine()
        mockEngine.setResponse("Hello world!")
        val useCase = createUseCase(mockEngine)

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
        val mockEngine = MockBaseLlmEngine.withStreamingError()
        val useCase = createUseCase(mockEngine)

        val events = useCase.execute("Test").toList()

        assertTrue(events.any { it is MessageEvent.Failed })
    }

    @Test
    fun `execute Failed event contains ChatError`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        mockEngine.setStreamingError(RuntimeException("Model crashed"))
        val useCase = createUseCase(mockEngine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.Unknown>(failed.error)
    }

    @Test
    fun `execute maps OutOfMemory error correctly`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        mockEngine.setStreamingError(RuntimeException("OutOfMemory in native code"))
        val useCase = createUseCase(mockEngine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.OutOfMemory>(failed.error)
    }

    @Test
    fun `execute maps Timeout error correctly`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        mockEngine.setStreamingError(RuntimeException("Request timeout exceeded"))
        val useCase = createUseCase(mockEngine)

        val events = useCase.execute("Test").toList()
        val failed = events.filterIsInstance<MessageEvent.Failed>().first()

        assertIs<ChatError.Timeout>(failed.error)
    }

    @Test
    fun `execute maps Model error correctly`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        mockEngine.setStreamingError(RuntimeException("Model inference failed"))
        val useCase = createUseCase(mockEngine)

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

        // Should not have RetrievingContext event
        assertFalse(events.any { it is MessageEvent.RetrievingContext })
        assertFalse(events.any { it is MessageEvent.ContextRetrieved })
    }

    @Test
    fun `executeSimple respects maxTokens parameter`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        val useCase = createUseCase(mockEngine)

        useCase.executeSimple("Test", maxTokens = 50).toList()

        assertEquals(50, mockEngine.lastConfig?.maxTokens)
    }

    @Test
    fun `executeSimple defaults to 100 maxTokens`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        val useCase = createUseCase(mockEngine)

        useCase.executeSimple("Test").toList()

        assertEquals(100, mockEngine.lastConfig?.maxTokens)
    }

    @Test
    fun `executeSimple response has null context`() = runTest {
        val useCase = createUseCase()
        val events = useCase.executeSimple("Test").toList()
        val complete = events.filterIsInstance<MessageEvent.Complete>().first()

        assertNull(complete.response.context)
    }

    // ===== GenerationResponse Tests =====

    @Test
    fun `GenerationResponse usedRag returns true when context has RAG`() {
        val context = EnrichedContext(
            context = "test",
            intentCategory = "SCIENCE",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = true,
            hasMemoryContext = false
        )
        val response = GenerationResponse(
            text = "response",
            stats = app.m1k3.ai.assistant.chat.GenerationStats(10, 100, 10f),
            context = context
        )

        assertTrue(response.usedRag)
        assertFalse(response.usedMemory)
    }

    @Test
    fun `GenerationResponse usedMemory returns true when context has memory`() {
        val context = EnrichedContext(
            context = "test",
            intentCategory = "GENERAL",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = false,
            hasMemoryContext = true
        )
        val response = GenerationResponse(
            text = "response",
            stats = app.m1k3.ai.assistant.chat.GenerationStats(10, 100, 10f),
            context = context
        )

        assertFalse(response.usedRag)
        assertTrue(response.usedMemory)
    }

    @Test
    fun `GenerationResponse with null context returns false for usedRag and usedMemory`() {
        val response = GenerationResponse(
            text = "response",
            stats = app.m1k3.ai.assistant.chat.GenerationStats(10, 100, 10f),
            context = null
        )

        assertFalse(response.usedRag)
        assertFalse(response.usedMemory)
    }

    // ===== Engine Interaction Tests =====

    @Test
    fun `execute calls engine generateStreaming`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        val useCase = createUseCase(mockEngine)

        useCase.execute("Test prompt").toList()

        assertEquals(1, mockEngine.streamingCallCount)
    }

    @Test
    fun `execute passes prompt to engine`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        val useCase = createUseCase(mockEngine)

        useCase.execute("My test prompt").toList()

        assertTrue(mockEngine.lastPrompt?.contains("My test prompt") == true)
    }

    @Test
    fun `executeSimple calls engine generateStreaming`() = runTest {
        val mockEngine = MockBaseLlmEngine()
        val useCase = createUseCase(mockEngine)

        useCase.executeSimple("Test").toList()

        assertEquals(1, mockEngine.streamingCallCount)
    }

    // ===== MessageEvent Type Tests =====

    @Test
    fun `MessageEvent Started is a singleton`() {
        val event1 = MessageEvent.Started
        val event2 = MessageEvent.Started
        assertEquals(event1, event2)
    }

    @Test
    fun `MessageEvent RetrievingContext is a singleton`() {
        val event1 = MessageEvent.RetrievingContext
        val event2 = MessageEvent.RetrievingContext
        assertEquals(event1, event2)
    }

    @Test
    fun `MessageEvent Streaming stores partial text and token count`() {
        val event = MessageEvent.Streaming(
            partialText = "Hello world",
            tokenCount = 2
        )
        assertEquals("Hello world", event.partialText)
        assertEquals(2, event.tokenCount)
    }

    @Test
    fun `MessageEvent Failed stores error`() {
        val error = ChatError.Unknown("test error")
        val event = MessageEvent.Failed(error)
        assertEquals(error, event.error)
    }
}
