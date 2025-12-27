package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.chat.ChatError
import app.m1k3.ai.assistant.chat.GenerationConfigBuilder
import app.m1k3.ai.assistant.chat.GenerationStats
import app.m1k3.ai.assistant.chat.QueryType
import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

private val logger = Logger.withTag("SendMessageUseCase")

/**
 * SendMessageUseCase - Orchestrates sending a message and generating AI response.
 *
 * This use case coordinates:
 * 1. Context retrieval (RAG + memory)
 * 2. Generation config building (device-adaptive)
 * 3. AI response streaming
 * 4. Event emission for state updates
 *
 * **Usage:**
 * ```kotlin
 * val useCase = SendMessageUseCase(
 *     aiEngine = engine,
 *     contextRetrieval = contextRetrievalUseCase,
 *     configBuilder = generationConfigBuilder
 * )
 *
 * useCase.execute("What is photosynthesis?").collect { event ->
 *     when (event) {
 *         is MessageEvent.Started -> showThinking()
 *         is MessageEvent.Streaming -> updateText(event.partialText)
 *         is MessageEvent.Complete -> showComplete(event.response)
 *         is MessageEvent.Failed -> showError(event.error)
 *     }
 * }
 * ```
 *
 * **Design Principles:**
 * - Single Responsibility: Orchestrates message flow only
 * - Event-Driven: Emits events for UI updates (no direct state mutation)
 * - Fail-Safe: Catches and reports errors as events
 */
class SendMessageUseCase(
    private val aiEngine: BaseLlmEngine,
    private val contextRetrieval: ContextRetrievalUseCase,
    private val configBuilder: GenerationConfigBuilder
) {
    /**
     * Execute the message sending flow.
     *
     * @param prompt The user's message
     * @return Flow of MessageEvent for state updates
     */
    fun execute(prompt: String): Flow<MessageEvent> = flow {
        emit(MessageEvent.Started)
        logger.i { "Starting message flow for prompt: ${prompt.take(50)}..." }

        try {
            // 1. Retrieve context
            emit(MessageEvent.RetrievingContext)
            val context = contextRetrieval.retrieveContext(prompt)

            emit(MessageEvent.ContextRetrieved(context))
            logger.d { "Context retrieved: hasRAG=${context.hasRagContext}, hasMemory=${context.hasMemoryContext}" }

            // 2. Build generation config
            val queryType = QueryType.fromIntentCategory(context.intentCategory)
            val config = configBuilder.build(queryType = queryType)

            // 3. Build full prompt with context
            val fullPrompt = if (context.hasContext) {
                "${context.context}\n\nUser: $prompt"
            } else {
                prompt
            }

            // 4. Stream AI response
            val startTime = Clock.System.now().toEpochMilliseconds()
            val accumulated = StringBuilder()
            var tokenCount = 0

            val result = aiEngine.generateStreaming(
                prompt = fullPrompt,
                config = config,
                onToken = { token ->
                    val cleanToken = token.trim()
                    if (cleanToken.isNotEmpty()) {
                        accumulated.append(cleanToken).append(" ")
                        tokenCount++

                        // Note: We can't emit from onToken callback as it's not suspending
                        // The streaming updates are emitted after generation completes
                    }
                }
            )

            // 5. Handle result
            result.onSuccess {
                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                val tokensPerSecond = if (duration > 0) tokenCount * 1000f / duration else 0f

                val stats = GenerationStats(
                    tokenCount = tokenCount,
                    durationMs = duration,
                    tokensPerSecond = tokensPerSecond,
                    ragInfo = context.ragInfo,
                    ragSources = context.ragSources,
                    ragConfidence = context.ragConfidence
                )

                val response = GenerationResponse(
                    text = accumulated.toString().trim(),
                    stats = stats,
                    context = context
                )

                logger.i { "Generation complete: $tokenCount tokens in ${duration}ms (${stats.formatSpeed()})" }
                emit(MessageEvent.Complete(response))

            }.onFailure { e ->
                logger.e(e) { "Generation failed" }
                val error = mapExceptionToError(e)
                emit(MessageEvent.Failed(error))
            }

        } catch (e: Exception) {
            logger.e(e) { "Message flow error" }
            val error = mapExceptionToError(e)
            emit(MessageEvent.Failed(error))
        }
    }

    /**
     * Execute a simple generation (e.g., welcome message) without context retrieval.
     *
     * @param prompt The prompt to generate
     * @param maxTokens Maximum tokens to generate
     * @return Flow of MessageEvent
     */
    fun executeSimple(prompt: String, maxTokens: Int = 100): Flow<MessageEvent> = flow {
        emit(MessageEvent.Started)

        try {
            val config = configBuilder.build(
                queryType = QueryType.CONVERSATIONAL,
                customMaxTokens = maxTokens
            )

            val startTime = Clock.System.now().toEpochMilliseconds()
            val accumulated = StringBuilder()
            var tokenCount = 0

            val result = aiEngine.generateStreaming(
                prompt = prompt,
                config = config,
                onToken = { token ->
                    val cleanToken = token.trim()
                    if (cleanToken.isNotEmpty()) {
                        accumulated.append(cleanToken).append(" ")
                        tokenCount++
                    }
                }
            )

            result.onSuccess {
                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                val tokensPerSecond = if (duration > 0) tokenCount * 1000f / duration else 0f

                val stats = GenerationStats(
                    tokenCount = tokenCount,
                    durationMs = duration,
                    tokensPerSecond = tokensPerSecond
                )

                val response = GenerationResponse(
                    text = accumulated.toString().trim(),
                    stats = stats,
                    context = null
                )

                emit(MessageEvent.Complete(response))

            }.onFailure { e ->
                emit(MessageEvent.Failed(mapExceptionToError(e)))
            }

        } catch (e: Exception) {
            emit(MessageEvent.Failed(mapExceptionToError(e)))
        }
    }

    private fun mapExceptionToError(e: Throwable): ChatError {
        val message = e.message ?: "Unknown error"
        return when {
            message.contains("OutOfMemory", ignoreCase = true) -> ChatError.OutOfMemory(message)
            message.contains("timeout", ignoreCase = true) -> ChatError.Timeout(message)
            message.contains("model", ignoreCase = true) -> ChatError.ModelError(message)
            else -> ChatError.Unknown(message)
        }
    }
}

/**
 * Events emitted during message sending flow.
 *
 * The ViewModel collects these events and updates UI state accordingly.
 */
sealed class MessageEvent {
    /** Message flow started, show thinking indicator */
    data object Started : MessageEvent()

    /** Retrieving context from RAG/memory */
    data object RetrievingContext : MessageEvent()

    /** Context retrieved successfully */
    data class ContextRetrieved(val context: EnrichedContext) : MessageEvent()

    /** Token received during streaming (for future streaming support) */
    data class Streaming(
        val partialText: String,
        val tokenCount: Int
    ) : MessageEvent()

    /** Generation completed successfully */
    data class Complete(val response: GenerationResponse) : MessageEvent()

    /** Generation failed */
    data class Failed(val error: ChatError) : MessageEvent()
}

/**
 * Successful generation response.
 */
data class GenerationResponse(
    /** The generated text */
    val text: String,

    /** Generation statistics */
    val stats: GenerationStats,

    /** Retrieved context (null for simple generations) */
    val context: EnrichedContext?
) {
    /** Check if RAG was used */
    val usedRag: Boolean
        get() = context?.hasRagContext == true

    /** Check if memory was used */
    val usedMemory: Boolean
        get() = context?.hasMemoryContext == true
}
