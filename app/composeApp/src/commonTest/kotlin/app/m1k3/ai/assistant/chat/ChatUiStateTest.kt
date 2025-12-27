package app.m1k3.ai.assistant.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ChatUiState and related state classes.
 */
class ChatUiStateTest {

    // ===== GenerationState Tests =====

    @Test
    fun `GenerationState Idle is not generating`() {
        val state = GenerationState.Idle
        assertFalse(state.isGenerating)
    }

    @Test
    fun `GenerationState Thinking is generating`() {
        val state = GenerationState.Thinking
        assertTrue(state.isGenerating)
    }

    @Test
    fun `GenerationState Streaming is generating`() {
        val state = GenerationState.Streaming(partialText = "Hello", tokenCount = 5)
        assertTrue(state.isGenerating)
    }

    @Test
    fun `GenerationState Complete is not generating`() {
        val stats = GenerationStats(tokenCount = 10, durationMs = 1000, tokensPerSecond = 10f)
        val state = GenerationState.Complete(finalText = "Hello", stats = stats)
        assertFalse(state.isGenerating)
    }

    @Test
    fun `GenerationState Failed is not generating`() {
        val state = GenerationState.Failed(ChatError.Unknown("test"))
        assertFalse(state.isGenerating)
    }

    // ===== EngineState Tests =====

    @Test
    fun `EngineState Loading is not ready`() {
        val state = EngineState.Loading
        assertFalse(state.isReady)
    }

    @Test
    fun `EngineState Ready is ready`() {
        val state = EngineState.Ready
        assertTrue(state.isReady)
    }

    @Test
    fun `EngineState Failed is not ready`() {
        val state = EngineState.Failed(ChatError.EngineInitError("test"))
        assertFalse(state.isReady)
    }

    // ===== ChatError Tests =====

    @Test
    fun `ChatError OutOfMemory has correct user message`() {
        val error = ChatError.OutOfMemory("OOM")
        assertTrue(error.toUserMessage().contains("memory"))
    }

    @Test
    fun `ChatError Timeout has correct user message`() {
        val error = ChatError.Timeout("timeout")
        assertTrue(error.toUserMessage().contains("too long"))
    }

    @Test
    fun `ChatError ModelError includes original message`() {
        val error = ChatError.ModelError("model failed")
        assertTrue(error.toUserMessage().contains("model failed"))
    }

    @Test
    fun `ChatError EngineInitError includes original message`() {
        val error = ChatError.EngineInitError("init failed")
        assertTrue(error.toUserMessage().contains("init failed"))
    }

    @Test
    fun `ChatError Unknown includes original message`() {
        val error = ChatError.Unknown("unknown error")
        assertTrue(error.toUserMessage().contains("unknown error"))
    }

    @Test
    fun `ChatError emojis are not empty`() {
        assertTrue(ChatError.OutOfMemory("").toEmoji().isNotEmpty())
        assertTrue(ChatError.Timeout("").toEmoji().isNotEmpty())
        assertTrue(ChatError.ModelError("").toEmoji().isNotEmpty())
        assertTrue(ChatError.EngineInitError("").toEmoji().isNotEmpty())
        assertTrue(ChatError.Unknown("").toEmoji().isNotEmpty())
    }

    // ===== GenerationStats Tests =====

    @Test
    fun `GenerationStats formatSpeed shows correct format`() {
        val stats = GenerationStats(tokenCount = 100, durationMs = 2000, tokensPerSecond = 50f)
        assertEquals("50.0 tok/s", stats.formatSpeed())
    }

    @Test
    fun `GenerationStats formatDuration shows milliseconds for short durations`() {
        val stats = GenerationStats(tokenCount = 10, durationMs = 500, tokensPerSecond = 20f)
        assertEquals("500ms", stats.formatDuration())
    }

    @Test
    fun `GenerationStats formatDuration shows seconds for longer durations`() {
        val stats = GenerationStats(tokenCount = 100, durationMs = 2500, tokensPerSecond = 40f)
        assertEquals("2.5s", stats.formatDuration())
    }

    @Test
    fun `GenerationStats formatFull includes all info`() {
        val stats = GenerationStats(tokenCount = 100, durationMs = 2000, tokensPerSecond = 50f)
        val formatted = stats.formatFull()
        assertTrue(formatted.contains("100"))
        assertTrue(formatted.contains("tok/s"))
    }

    // ===== ChatUiState Tests =====

    @Test
    fun `ChatUiState default has empty messages`() {
        val state = ChatUiState()
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `ChatUiState default has empty input`() {
        val state = ChatUiState()
        assertEquals("", state.inputText)
    }

    @Test
    fun `ChatUiState default engine is loading`() {
        val state = ChatUiState()
        assertEquals(EngineState.Loading, state.engineState)
    }

    @Test
    fun `ChatUiState default generation is idle`() {
        val state = ChatUiState()
        assertEquals(GenerationState.Idle, state.generationState)
    }

    @Test
    fun `ChatUiState canSendMessage is false when engine not ready`() {
        val state = ChatUiState(
            engineState = EngineState.Loading,
            generationState = GenerationState.Idle,
            inputText = "Hello"
        )
        assertFalse(state.canSendMessage)
    }

    @Test
    fun `ChatUiState canSendMessage is false when generating`() {
        val state = ChatUiState(
            engineState = EngineState.Ready,
            generationState = GenerationState.Thinking,
            inputText = "Hello"
        )
        assertFalse(state.canSendMessage)
    }

    @Test
    fun `ChatUiState canSendMessage is false when input is blank`() {
        val state = ChatUiState(
            engineState = EngineState.Ready,
            generationState = GenerationState.Idle,
            inputText = "   "
        )
        assertFalse(state.canSendMessage)
    }

    @Test
    fun `ChatUiState canSendMessage is true when all conditions met`() {
        val state = ChatUiState(
            engineState = EngineState.Ready,
            generationState = GenerationState.Idle,
            inputText = "Hello"
        )
        assertTrue(state.canSendMessage)
    }

    // ===== ChatMessage Tests =====

    @Test
    fun `ChatMessage user message has isUser true`() {
        val message = ChatMessage(
            text = "Hello",
            isUser = true,
            timestamp = 0L
        )
        assertTrue(message.isUser)
    }

    @Test
    fun `ChatMessage AI message has isUser false`() {
        val message = ChatMessage(
            text = "Hello",
            isUser = false,
            timestamp = 0L
        )
        assertFalse(message.isUser)
    }

    @Test
    fun `ChatMessage defaults have correct values`() {
        val message = ChatMessage(
            text = "Hello",
            isUser = true,
            timestamp = 12345L
        )
        assertFalse(message.isError)
        assertEquals(null, message.inferenceStats)
        assertEquals(null, message.ragSources)
    }
}
