package app.m1k3.ai.assistant.chat

/**
 * ChatUiState - Single source of truth for ChatScreen UI state.
 *
 * This comprehensive state class consolidates all UI state previously
 * scattered across multiple remember() calls and ViewModels in ChatScreen.
 *
 * **Architecture:**
 * - Immutable data class for predictable state updates
 * - Sealed classes for type-safe state variants
 * - All state changes flow through ChatScreenViewModel
 *
 * **Usage:**
 * ```kotlin
 * val uiState by viewModel.uiState.collectAsState()
 *
 * when (uiState.generationState) {
 *     is GenerationState.Streaming -> showTypingIndicator()
 *     is GenerationState.Complete -> hideTypingIndicator()
 *     // ...
 * }
 * ```
 */
data class ChatUiState(
    /** All messages in the conversation */
    val messages: List<ChatMessage> = emptyList(),

    /** Current text in the input field */
    val inputText: String = "",

    /** Current generation state (Idle, Thinking, Streaming, Complete, Failed) */
    val generationState: GenerationState = GenerationState.Idle,

    /** AI engine initialization state */
    val engineState: EngineState = EngineState.Loading,

    /** Session eco-metrics (water, energy, CO2 saved) */
    val sessionEcoStats: SessionEcoStats = SessionEcoStats(),

    /** Current error to display (null if none) */
    val error: ChatError? = null,

    /** RAG info for display (e.g., "Technical (85%) • 3 facts") */
    val ragInfo: String? = null
)

/**
 * AI generation state machine.
 *
 * Tracks the lifecycle of a single AI response generation.
 */
sealed class GenerationState {
    /** No generation in progress */
    data object Idle : GenerationState()

    /** AI is thinking (before first token) */
    data object Thinking : GenerationState()

    /**
     * Streaming tokens from AI.
     *
     * @property partialText Accumulated response text so far
     * @property tokenCount Number of tokens generated
     */
    data class Streaming(
        val partialText: String,
        val tokenCount: Int
    ) : GenerationState()

    /**
     * Generation completed successfully.
     *
     * @property finalText Complete response text
     * @property stats Generation statistics
     */
    data class Complete(
        val finalText: String,
        val stats: GenerationStats
    ) : GenerationState()

    /**
     * Generation failed.
     *
     * @property error Error that caused the failure
     */
    data class Failed(val error: ChatError) : GenerationState()
}

/**
 * AI engine initialization state.
 */
sealed class EngineState {
    /** Engine is loading/initializing */
    data object Loading : EngineState()

    /** Engine is ready for generation */
    data object Ready : EngineState()

    /**
     * Engine failed to initialize.
     *
     * @property error Error that caused the failure
     */
    data class Failed(val error: ChatError) : EngineState()
}

/**
 * Chat error types for user-friendly error handling.
 *
 * Maps low-level exceptions to user-understandable errors.
 */
sealed class ChatError {
    /** Memory error (OOM during generation) */
    data class OutOfMemory(val message: String) : ChatError()

    /** Generation timeout */
    data class Timeout(val message: String) : ChatError()

    /** Model loading or inference error */
    data class ModelError(val message: String) : ChatError()

    /** Engine initialization error */
    data class EngineInitError(val message: String) : ChatError()

    /** Unknown/unexpected error */
    data class Unknown(val message: String) : ChatError()

    /**
     * Get user-friendly error message.
     */
    fun toUserMessage(): String = when (this) {
        is OutOfMemory -> "Not enough memory. Try closing other apps."
        is Timeout -> "Response took too long. Please try again."
        is ModelError -> "AI model error: $message"
        is EngineInitError -> "Failed to start AI: $message"
        is Unknown -> "Something went wrong: $message"
    }

    /**
     * Get emoji for error type.
     */
    fun toEmoji(): String = when (this) {
        is OutOfMemory -> "💾"
        is Timeout -> "⏱️"
        is ModelError -> "🤖"
        is EngineInitError -> "⚙️"
        is Unknown -> "❌"
    }
}

/**
 * Statistics from a completed generation.
 */
data class GenerationStats(
    /** Total tokens generated */
    val tokenCount: Int,

    /** Generation duration in milliseconds */
    val durationMs: Long,

    /** Tokens per second */
    val tokensPerSecond: Float,

    /** RAG information if used */
    val ragInfo: String? = null,

    /** RAG sources for display */
    val ragSources: String? = null,

    /** RAG confidence score */
    val ragConfidence: Double? = null
) {
    /**
     * Format speed for display.
     */
    fun formatSpeed(): String = "%.1f tok/s".format(tokensPerSecond)

    /**
     * Format duration for display.
     */
    fun formatDuration(): String = when {
        durationMs >= 1000 -> "%.1fs".format(durationMs / 1000.0)
        else -> "${durationMs}ms"
    }

    /**
     * Format full stats for display.
     */
    fun formatFull(): String = "⚡ $tokenCount tokens in ${formatDuration()} (${formatSpeed()})"
}

/**
 * Extension to check if generation is in progress.
 */
val GenerationState.isGenerating: Boolean
    get() = this is GenerationState.Thinking || this is GenerationState.Streaming

/**
 * Extension to check if engine is ready.
 */
val EngineState.isReady: Boolean
    get() = this is EngineState.Ready

/**
 * Extension to check if input is allowed.
 */
val ChatUiState.canSendMessage: Boolean
    get() = engineState.isReady && !generationState.isGenerating && inputText.isNotBlank()
