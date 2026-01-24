package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.chat.QueryType
import app.m1k3.ai.assistant.chat.GenerationConfigBuilder
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolRegistry
import app.m1k3.ai.domain.usecases.chat.ProcessLlmOutputUseCase
import app.m1k3.ai.domain.usecases.chat.ProcessedOutput
import app.m1k3.ai.domain.chat.events.ChatEvent
import app.m1k3.ai.domain.chat.events.ChatResponse
import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

private val logger = Logger.withTag("ChatWithToolsUseCase")

/**
 * ChatWithToolsUseCase - Orchestrates chat with tool calling capabilities.
 *
 * This use case extends the basic message flow with:
 * 1. Tool schema injection into prompts
 * 2. Tool call detection in LLM responses
 * 3. Tool execution with confirmation handling
 * 4. Result formatting for display
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
 *     contextRetrieval = contextRetrievalUseCase,
 *     processLlmOutput = processLlmOutputUseCase,
 *     toolRegistry = toolRegistry
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
class ChatWithToolsUseCase(
    private val aiEngine: BaseLlmEngine,
    private val contextRetrieval: ContextRetrievalUseCase,
    private val processLlmOutput: ProcessLlmOutputUseCase,
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
    ): Flow<ChatEvent> = flow {
        emit(ChatEvent.Started)
        logger.i { "Starting chat flow for: ${prompt.take(50)}..." }

        try {
            // 1. Retrieve context (RAG + memory + history)
            emit(ChatEvent.RetrievingContext)
            val context = contextRetrieval.retrieveContext(prompt)
            emit(ChatEvent.ContextRetrieved(context))
            logger.d { "Context: hasRAG=${context.hasRagContext}, hasMemory=${context.hasMemoryContext}" }

            // 2. Build prompt with tool schemas
            val fullPrompt = buildPromptWithTools(prompt, context)

            // 3. Build generation config
            val queryType = QueryType.fromIntentCategory(context.intentCategory)
            val config = configBuilder?.build(queryType = queryType)
                ?: GenerationConfig()

            // 4. Generate response
            emit(ChatEvent.Generating)
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

            result.onSuccess {
                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                val rawResponse = accumulated.toString().trim()

                logger.d { "Generated $tokenCount tokens in ${duration}ms" }

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
                        emit(ChatEvent.Complete(response))
                    }

                    is ProcessedOutput.WithTools -> {
                        // Tools detected and executed
                        emit(ChatEvent.ToolsExecuted(
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
                        emit(ChatEvent.Complete(response))
                    }
                }

            }.onFailure { e ->
                logger.e(e) { "Generation failed" }
                emit(ChatEvent.Failed(mapExceptionToError(e)))
            }

        } catch (e: Exception) {
            logger.e(e) { "Chat flow error" }
            emit(ChatEvent.Failed(mapExceptionToError(e)))
        }
    }

    /**
     * Build prompt with context and tool schemas.
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

        // Add tool schemas if tools are available
        val availableTools = toolRegistry.getAvailableTools()
        if (availableTools.isNotEmpty()) {
            appendLine("You have access to the following tools:")
            availableTools.forEach { tool ->
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

// ChatEvent and ChatResponse are now imported from domain
