package app.m1k3.ai.domain.usecases.chat

import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.ai.LlmEngine
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.chat.QueryType
import app.m1k3.ai.domain.chat.events.GenerationResponse
import app.m1k3.ai.domain.chat.events.MessageEvent
import app.m1k3.ai.domain.chat.services.ContextRetrieverInterface
import app.m1k3.ai.domain.config.GenerationConfigBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * SendMessageUseCase - Orchestrates sending a message and generating AI response.
 *
 * Domain use case for coordinating:
 * 1. Context retrieval (RAG + memory)
 * 2. Generation config building (device-adaptive)
 * 3. AI response streaming
 * 4. Event emission for state updates
 *
 * Domain use case - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * val useCase = SendMessageUseCase(
 *     aiEngine = engine,
 *     contextRetrieval = contextRetriever,
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
    private val aiEngine: LlmEngine,
    private val contextRetrieval: ContextRetrieverInterface,
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

        try {
            // 1. Retrieve context
            emit(MessageEvent.RetrievingContext)
            val context = contextRetrieval.retrieveContext(prompt)

            emit(MessageEvent.ContextRetrieved(context))

            // 2. Build generation config
            val queryType = QueryType.fromIntentCategory(context.intentCategory)
            val domainConfig = configBuilder.build(queryType = queryType)
            val config = GenerationConfig(
                maxTokens = domainConfig.maxTokens,
                temperature = domainConfig.temperature
            )

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

                emit(MessageEvent.Complete(response))

            }.onFailure { e ->
                val error = mapExceptionToError(e)
                emit(MessageEvent.Failed(error))
            }

        } catch (e: Exception) {
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
            val domainConfig = configBuilder.build(
                queryType = QueryType.CONVERSATIONAL,
                customMaxTokens = maxTokens
            )
            val config = GenerationConfig(
                maxTokens = domainConfig.maxTokens,
                temperature = domainConfig.temperature
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
