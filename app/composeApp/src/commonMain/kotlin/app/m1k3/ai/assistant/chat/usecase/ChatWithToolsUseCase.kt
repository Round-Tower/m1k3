package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.chat.QueryType
import app.m1k3.ai.assistant.chat.GenerationConfigBuilder
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.chat.services.UnifiedPromptBuilder
import app.m1k3.ai.domain.platform.DeviceContext
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolRegistry
import app.m1k3.ai.domain.usecases.chat.LlmOutputProcessor
import app.m1k3.ai.domain.usecases.chat.ProcessedOutput
import app.m1k3.ai.domain.chat.events.ChatEvent
import app.m1k3.ai.domain.chat.events.ChatResponse
import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
    private val processLlmOutput: LlmOutputProcessor,
    private val toolRegistry: ToolRegistry,
    private val configBuilder: GenerationConfigBuilder? = null,
    private val promptBuilder: UnifiedPromptBuilder? = null,
    /** M1K3 system prompt — injected so personality persists across tool-calling path */
    var systemPrompt: String = ""
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
        confirmedToolIds: Set<String> = emptySet(),
        deviceContext: DeviceContext? = null
    ): Flow<ChatEvent> = flow {
        emit(ChatEvent.Started)
        logger.i { "Starting chat flow for: ${prompt.take(50)}..." }

        try {
            // 1. Retrieve context (RAG + memory + history)
            emit(ChatEvent.RetrievingContext)
            val context = contextRetrieval.retrieveContext(prompt)
            emit(ChatEvent.ContextRetrieved(context))
            logger.d { "Context: hasRAG=${context.hasRagContext}, hasMemory=${context.hasMemoryContext}" }

            // 2. Get relevant tools and build prompt with tool schemas
            val relevantTools = toolRegistry.getRelevantTools(prompt, maxTools = 3)
            println("DEBUG(ChatWithTools) TOOLS: ${relevantTools.size} relevant for '${prompt.take(40)}': ${relevantTools.map { it.id }}")
            val fullPrompt = buildPromptWithTools(prompt, context, deviceContext, relevantTools)

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
                logger.d { "Raw LLM response (${rawResponse.length} chars): ${rawResponse.take(200)}" }
                println("DEBUG(ChatWithTools) Raw response (${rawResponse.length} chars): ${rawResponse.take(300)}")

                val processed = processLlmOutput.execute(rawResponse, confirmedToolIds)
                logger.d { "Processed output type: ${processed::class.simpleName}" }
                println("DEBUG(ChatWithTools) Processed: ${processed::class.simpleName}")

                when (processed) {
                    is ProcessedOutput.TextOnly -> {
                        // Check if the filter selected KNOWLEDGE tools that the model ignored
                        val knowledgeTools = relevantTools.filter { it.category == ToolCategory.KNOWLEDGE }
                        if (knowledgeTools.isNotEmpty()) {
                            // Force-execute: the model didn't call the tool, but we know it should
                            println("DEBUG(ChatWithTools) Force-executing ${knowledgeTools.size} KNOWLEDGE tools the model ignored")
                            val forceResults = forceExecuteTools(knowledgeTools, prompt)

                            emit(ChatEvent.ToolsExecuted(
                                results = forceResults,
                                hasPendingConfirmations = false
                            ))

                            val stats = buildStats(tokenCount, duration, context)
                            val response = ChatResponse(
                                text = processed.text,
                                stats = stats,
                                context = context,
                                toolResults = forceResults,
                                toolResultsFormatted = forceResults.joinToString("\n") { r ->
                                    when (r) {
                                        is ToolResult.Success -> "${r.toolId}: ${r.output}"
                                        is ToolResult.Failure -> "${r.toolId}: ${r.error.displayMessage}"
                                        else -> ""
                                    }
                                }
                            )
                            emit(ChatEvent.Complete(response))
                        } else {
                            // No KNOWLEDGE tools — just text response
                            val stats = buildStats(tokenCount, duration, context)
                            val response = ChatResponse(
                                text = processed.text,
                                stats = stats,
                                context = context,
                                toolResults = null
                            )
                            emit(ChatEvent.Complete(response))
                        }
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
     * Force-execute KNOWLEDGE tools when the model ignores them.
     *
     * Small models (Qwen 0.6B) often answer from memory instead of calling
     * web_search. When the ToolFilter selected KNOWLEDGE tools but the model
     * didn't call them, we execute them directly with the user's query.
     */
    private suspend fun forceExecuteTools(
        tools: List<Tool>,
        userQuery: String
    ): List<ToolResult> = withContext(Dispatchers.IO) {
        tools.mapNotNull { tool ->
            val executor = toolRegistry.getExecutor(tool.id) ?: return@mapNotNull null
            val call = ToolCall(
                toolId = tool.id,
                arguments = mapOf("query" to userQuery),
                rawText = "[force-injected by ToolFilter]"
            )
            try {
                executor.execute(call)
            } catch (e: Exception) {
                logger.e(e) { "Force-execute ${tool.id} failed" }
                null
            }
        }
    }

    private suspend fun buildPromptWithTools(
        userPrompt: String,
        context: EnrichedContext,
        deviceContext: DeviceContext? = null,
        relevantTools: List<Tool>? = null
    ): String {
        val tools = relevantTools ?: toolRegistry.getRelevantTools(userPrompt, maxTools = 3)
        logger.i { "TOOLS: ${tools.size} relevant for '${userPrompt.take(40)}': ${tools.map { it.id }}" }
        logger.i { "SYSTEM: ${systemPrompt.take(80)}..." }
        logger.i { "BUILDER: promptBuilder=${promptBuilder != null}" }

        // Use unified builder if available — pass system prompt for M1K3 personality
        promptBuilder?.let { builder ->
            return builder.build(
                userPrompt = userPrompt,
                context = context,
                tools = tools,
                systemPrompt = systemPrompt,
                deviceContext = deviceContext
            )
        }

        // Legacy fallback (for backwards compatibility)
        return buildString {
            if (context.hasContext) {
                appendLine(context.context)
                appendLine()
            }

            if (tools.isNotEmpty()) {
                appendLine("You have access to the following tools:")
                tools.forEach { tool ->
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

            append("User: $userPrompt")
        }
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
