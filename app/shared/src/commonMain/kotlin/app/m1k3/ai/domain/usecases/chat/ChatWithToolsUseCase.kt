package app.m1k3.ai.domain.usecases.chat

import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.ai.LlmEngine
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.chat.QueryType
import app.m1k3.ai.domain.chat.events.ChatEvent
import app.m1k3.ai.domain.chat.events.ChatResponse
import app.m1k3.ai.domain.chat.services.ContextRetrieverInterface
import app.m1k3.ai.domain.config.GenerationConfigBuilder
import app.m1k3.ai.domain.tools.services.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ChatWithToolsUseCase - Orchestrates chat with tool calling capabilities.
 *
 * Domain use case for coordinating:
 * 1. Context retrieval (RAG + memory + history)
 * 2. Tool schema injection into prompts
 * 3. LLM generation with adaptive config
 * 4. Tool call detection and execution
 * 5. Response formatting with tool results
 *
 * Domain use case - Pure Kotlin, no platform dependencies.
 *
 * **Flow:**
 * ```
 * User Message
 *      │
 *      ▼
 * Context Retrieval (RAG + Memory)
 *      │
 *      ▼
 * Prompt Building (+ Tool Schemas)
 *      │
 *      ▼
 * LLM Generation
 *      │
 *      ▼
 * Tool Detection & Execution
 *      │
 *      ▼
 * Response Formatting
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val useCase = ChatWithToolsUseCase(
 *     aiEngine = engine,
 *     contextRetrieval = contextRetriever,
 *     processLlmOutput = processLlmOutputUseCase,
 *     toolRegistry = toolRegistry,
 *     configBuilder = generationConfigBuilder
 * )
 *
 * useCase.execute("What's my battery level?").collect { event ->
 *     when (event) {
 *         is ChatEvent.Started -> showThinking()
 *         is ChatEvent.ToolsExecuted -> showToolResults(event.results)
 *         is ChatEvent.Complete -> showResponse(event.response)
 *         is ChatEvent.Failed -> showError(event.error)
 *     }
 * }
 * ```
 */
@ExperimentalTime
class ChatWithToolsUseCase(
    private val aiEngine: LlmEngine,
    private val contextRetrieval: ContextRetrieverInterface,
    private val processLlmOutput: LlmOutputProcessor,
    private val toolRegistry: ToolRegistry,
    private val configBuilder: GenerationConfigBuilder? = null
) {
    /**
     * Execute the chat flow with tool support.
     *
     * @param prompt The user's message
     * @param confirmedToolIds Tools that have been confirmed by user (for re-execution)
     * @return Flow of ChatEvent for state updates
     */
    fun execute(
        prompt: String,
        confirmedToolIds: Set<String> = emptySet()
    ): Flow<ChatEvent> = channelFlow {
        send(ChatEvent.Started)

        try {
            // 1. Retrieve context (RAG + memory + history)
            send(ChatEvent.RetrievingContext)
            val context = contextRetrieval.retrieveContext(prompt)
            send(ChatEvent.ContextRetrieved(context))

            // 2. Build prompt with tool schemas
            val fullPrompt = buildPromptWithTools(prompt, context)

            // 3. Build generation config
            val queryType = QueryType.fromIntentCategory(context.intentCategory)
            val domainConfig = configBuilder?.build(queryType = queryType)
            val config = if (domainConfig != null) {
                GenerationConfig(
                    maxTokens = domainConfig.maxTokens,
                    temperature = domainConfig.temperature
                )
            } else {
                GenerationConfig()
            }

            // 4. Generate response with streaming
            send(ChatEvent.Generating)
            val startTime = Clock.System.now().toEpochMilliseconds()
            val accumulated = StringBuilder()       // visible response text
            val thinkingAccumulated = StringBuilder() // <think> block content
            var tokenCount = 0
            var isInThinkBlock = false
            var thinkBlockStartMs = startTime

            val result = aiEngine.generateStreaming(
                prompt = fullPrompt,
                config = config,
                onToken = { token ->
                    if (token.isEmpty()) return@generateStreaming

                    // Detect <think> / </think> in the token stream.
                    // Qwen3.5 may tokenise as "< think>" (with space) — use regex.
                    val THINK_OPEN = Regex("< *think *>", RegexOption.IGNORE_CASE)
                    val THINK_CLOSE = Regex("</ *think *>", RegexOption.IGNORE_CASE)
                    val buffer = (if (isInThinkBlock) thinkingAccumulated else accumulated).toString() + token
                    when {
                        !isInThinkBlock && THINK_OPEN.containsMatchIn(buffer) -> {
                            val beforeThink = THINK_OPEN.split(buffer).first()
                            accumulated.clear()
                            accumulated.append(beforeThink)
                            isInThinkBlock = true
                            thinkBlockStartMs = Clock.System.now().toEpochMilliseconds()
                            thinkingAccumulated.clear()
                        }
                        isInThinkBlock && THINK_CLOSE.containsMatchIn(buffer) -> {
                            val parts = THINK_CLOSE.split(buffer)
                            thinkingAccumulated.clear()
                            thinkingAccumulated.append(parts.first())
                            isInThinkBlock = false
                            accumulated.append(parts.drop(1).joinToString(""))
                        }
                        isInThinkBlock -> thinkingAccumulated.append(token)
                        else -> accumulated.append(token)
                    }

                    tokenCount++
                    trySend(ChatEvent.Streaming(
                        partialText = accumulated.toString(),
                        tokenCount = tokenCount,
                        thinkingPartial = thinkingAccumulated.toString().ifEmpty { null },
                        isThinking = isInThinkBlock
                    ))
                }
            )

            result.onSuccess {
                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                val rawResponse = accumulated.toString()

                // 5. Process for tool calls
                val processed = processLlmOutput.execute(rawResponse, confirmedToolIds)

                when (processed) {
                    is ProcessedOutput.TextOnly -> {
                        // No tools - just text response
                        val stats = buildStats(tokenCount, duration, context)
                        val response = ChatResponse(
                            text = processed.text,
                            stats = stats,
                            context = context,
                            toolResults = null
                        )
                        send(ChatEvent.Complete(response))
                    }

                    is ProcessedOutput.WithTools -> {
                        // Tools detected and executed
                        send(ChatEvent.ToolsExecuted(
                            results = processed.toolResults,
                            hasPendingConfirmations = processed.hasPendingConfirmations
                        ))

                        val stats = buildStats(tokenCount, duration, context)
                        val response = ChatResponse(
                            text = processed.plainText,
                            stats = stats,
                            context = context,
                            toolResults = processed.toolResults,
                            toolResultsFormatted = processed.formatResultsForDisplay()
                        )
                        send(ChatEvent.Complete(response))
                    }
                }

            }.onFailure { e ->
                send(ChatEvent.Failed(mapExceptionToError(e)))
            }

        } catch (e: Exception) {
            send(ChatEvent.Failed(mapExceptionToError(e)))
        }
    }

    /**
     * Build prompt with context and tool schemas.
     *
     * **Tool Filtering:** Uses getRelevantTools() to filter by query relevance
     * for small models, reducing prompt bloat from ~150 tokens (all tools)
     * to 0-50 tokens (0-3 relevant tools).
     */
    private suspend fun buildPromptWithTools(
        userPrompt: String,
        context: EnrichedContext
    ): String = buildString {
        // Add context if available
        if (context.hasContext) {
            appendLine(context.context)
            appendLine()
        }

        // Add tool schemas for relevant tools only
        val relevantTools = toolRegistry.getRelevantTools(userPrompt, maxTools = 3)
        if (relevantTools.isNotEmpty()) {
            appendLine("You have access to the following tools:")
            relevantTools.forEach { tool ->
                appendLine("- ${tool.id}: ${tool.description}")
                if (tool.parameters.isNotEmpty()) {
                    tool.parameters.forEach { param ->
                        val req = if (param.required) "required" else "optional"
                        appendLine("    ${param.name} ($req): ${param.description}")
                    }
                }
            }
            appendLine()
            appendLine("To use a tool, respond with JSON: {\"tool\": \"tool_id\", \"args\": {...}}")
            appendLine()
        }

        // Add user prompt
        append("User: $userPrompt")
    }

    private fun buildStats(
        tokenCount: Int,
        durationMs: Long,
        context: EnrichedContext
    ): GenerationStats {
        val tokensPerSecond = if (durationMs > 0) tokenCount * 1000f / durationMs else 0f
        return GenerationStats(
            tokenCount = tokenCount,
            durationMs = durationMs,
            tokensPerSecond = tokensPerSecond,
            ragInfo = context.ragInfo,
            ragSources = context.ragSources,
            ragConfidence = context.ragConfidence
        )
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
