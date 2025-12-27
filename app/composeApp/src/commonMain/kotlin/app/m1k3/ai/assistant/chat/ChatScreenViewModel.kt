package app.m1k3.ai.assistant.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.ai.GenerationConfig
import app.m1k3.ai.assistant.chat.usecase.ContextRetrievalUseCase
import app.m1k3.ai.assistant.chat.usecase.EnrichedContext
import app.m1k3.ai.assistant.config.GenerationConstants
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.memory.MemoryManager
import app.m1k3.ai.assistant.platform.DeviceInfoProviderInterface
import app.m1k3.ai.assistant.platform.PreferencesStoreInterface
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val logger = Logger.withTag("ChatScreenViewModel")

/**
 * ChatScreenViewModel - Manages ChatScreen UI state with MVVM architecture.
 *
 * This ViewModel consolidates all state management previously scattered across
 * ChatScreen.kt's 12+ responsibilities into a single, testable class.
 *
 * **Architecture (MVVM with Use Cases):**
 * - Single StateFlow for all UI state (ChatUiState)
 * - Delegates to use cases for business logic:
 *   - ContextRetrievalUseCase: RAG + memory retrieval
 *   - GenerationConfigBuilder: Device-adaptive config
 * - Handles side effects: database, eco metrics, state updates
 *
 * **Responsibilities:**
 * - UI state management via ChatUiState
 * - AI engine initialization
 * - Coordinating message flow with use cases
 * - Eco-metrics tracking
 * - Error handling
 */
class ChatScreenViewModel(
    private val aiEngine: BaseLlmEngine,
    private val conversationRepo: ConversationRepository,
    private val ecoMetricsRepo: EcoMetricsRepository,
    private val database: MaDatabase,
    private val deviceInfo: DeviceInfoProviderInterface,
    private val preferences: PreferencesStoreInterface,
    private val scope: CoroutineScope,
    private val projectId: String,
    private val memoryManager: MemoryManager? = null,
    private val ragManager: RAGManager? = null
) {
    // ===== Use Cases (lazy initialization) =====

    private val contextRetrieval: ContextRetrievalUseCase by lazy {
        ContextRetrievalUseCase(
            deviceInfo = deviceInfo,
            preferences = preferences,
            ragManager = ragManager,
            memoryManager = memoryManager
        )
    }

    private val configBuilder: GenerationConfigBuilder by lazy {
        GenerationConfigBuilder(deviceInfo)
    }

    // ===== State =====

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Session tracking
    private val sessionId = "chat_session_${Clock.System.now().toEpochMilliseconds()}"
    private var currentConversationId: Long? = null

    init {
        loadMessages()
        initializeConversation()
    }

    // ===== Public Actions =====

    /**
     * Initialize the AI engine.
     * Call this from LaunchedEffect(Unit) in the composable.
     */
    fun initializeEngine() {
        if (_uiState.value.engineState is EngineState.Ready) {
            logger.d { "Engine already initialized" }
            return
        }

        scope.launch {
            try {
                logger.i { "Initializing AI engine..." }
                _uiState.update { it.copy(engineState = EngineState.Loading) }

                val result = aiEngine.initialize()

                result.onSuccess {
                    _uiState.update { it.copy(engineState = EngineState.Ready) }
                    logger.i { "AI engine ready" }

                    if (_uiState.value.messages.isEmpty()) {
                        generateWelcomeMessage()
                    }
                }.onFailure { e ->
                    val error = ChatError.EngineInitError(e.message ?: "Initialization failed")
                    _uiState.update { it.copy(engineState = EngineState.Failed(error)) }
                    logger.e(e) { "Engine initialization failed" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to initialize AI engine" }
                val error = mapExceptionToError(e)
                _uiState.update { it.copy(engineState = EngineState.Failed(error)) }
            }
        }
    }

    /**
     * Update the input text field.
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Send a message and generate AI response.
     */
    fun sendMessage() {
        val inputText = _uiState.value.inputText.trim()
        if (inputText.isBlank()) return
        if (_uiState.value.generationState.isGenerating) return

        scope.launch {
            try {
                // Clear input and add user message
                val userMessage = ChatMessage(
                    text = inputText,
                    isUser = true,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )

                _uiState.update {
                    it.copy(
                        inputText = "",
                        messages = it.messages + userMessage,
                        generationState = GenerationState.Thinking,
                        error = null
                    )
                }

                // Record user message
                recordMessage(inputText, "user", 0)

                // Generate AI response
                generateResponse(inputText)

            } catch (e: Exception) {
                logger.e(e) { "Failed to send message" }
                val error = mapExceptionToError(e)
                _uiState.update {
                    it.copy(
                        generationState = GenerationState.Failed(error),
                        error = error
                    )
                }
            }
        }
    }

    /**
     * Clear the current error.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear all conversation history.
     */
    fun clearConversation() {
        scope.launch {
            try {
                logger.i { "Clearing conversation for project: $projectId" }

                database.messageQueries.deleteMessagesForProject(projectId)

                val conversations = conversationRepo.getConversationsByProject(projectId)
                conversations.forEach { conv ->
                    conversationRepo.deleteConversation(conv.id)
                }

                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        sessionEcoStats = SessionEcoStats(),
                        error = null
                    )
                }

                currentConversationId = null
                initializeConversation()

                logger.i { "Conversation cleared" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to clear conversation" }
                _uiState.update {
                    it.copy(error = ChatError.Unknown("Failed to clear: ${e.message}"))
                }
            }
        }
    }

    // ===== Internal Methods =====

    private fun loadMessages() {
        scope.launch {
            try {
                val dbMessages = database.messageQueries
                    .getMessagesForProject(projectId)
                    .executeAsList()

                val chatMessages = dbMessages.map { msg ->
                    ChatMessage(
                        text = msg.content,
                        isUser = msg.role == "user",
                        timestamp = msg.timestamp,
                        isError = false,
                        inferenceStats = msg.tokens?.let { "⚡ $it tokens" },
                        ragSources = msg.rag_sources
                    )
                }

                _uiState.update { it.copy(messages = chatMessages) }
                logger.i { "Loaded ${chatMessages.size} messages" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to load messages" }
            }
        }
    }

    private fun initializeConversation() {
        scope.launch {
            try {
                currentConversationId = conversationRepo.createConversation(
                    projectId = projectId,
                    title = "Chat Session"
                )
            } catch (e: Exception) {
                logger.e(e) { "Failed to create conversation" }
            }
        }
    }

    private suspend fun generateWelcomeMessage() {
        val placeholderMessage = ChatMessage(
            text = "",
            isUser = false,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + placeholderMessage,
                generationState = GenerationState.Thinking
            )
        }

        try {
            val welcomePrompt = "Say a brief, friendly greeting as M1K3, a helpful AI assistant. Keep it under 2 sentences."

            // Use GenerationConfigBuilder for config
            val config = configBuilder.build(
                queryType = QueryType.CONVERSATIONAL,
                customMaxTokens = 100
            )

            val accumulated = StringBuilder()
            var tokenCount = 0
            val startTime = Clock.System.now().toEpochMilliseconds()

            val result = aiEngine.generateStreaming(
                prompt = welcomePrompt,
                config = config,
                onToken = { token ->
                    val cleanToken = token.trim()
                    if (cleanToken.isNotEmpty()) {
                        accumulated.append(cleanToken).append(" ")
                        tokenCount++

                        _uiState.update { state ->
                            val updatedMessages = state.messages.toMutableList()
                            if (updatedMessages.isNotEmpty()) {
                                updatedMessages[updatedMessages.lastIndex] = updatedMessages.last().copy(
                                    text = accumulated.toString().trim()
                                )
                            }
                            state.copy(
                                messages = updatedMessages,
                                generationState = GenerationState.Streaming(
                                    partialText = accumulated.toString().trim(),
                                    tokenCount = tokenCount
                                )
                            )
                        }
                    }
                }
            )

            result.onSuccess {
                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                val stats = GenerationStats(
                    tokenCount = tokenCount,
                    durationMs = duration,
                    tokensPerSecond = if (duration > 0) tokenCount * 1000f / duration else 0f
                )

                _uiState.update { state ->
                    val updatedMessages = state.messages.toMutableList()
                    if (updatedMessages.isNotEmpty()) {
                        updatedMessages[updatedMessages.lastIndex] = updatedMessages.last().copy(
                            text = accumulated.toString().trim(),
                            inferenceStats = stats.formatFull()
                        )
                    }
                    state.copy(
                        messages = updatedMessages,
                        generationState = GenerationState.Complete(
                            finalText = accumulated.toString().trim(),
                            stats = stats
                        )
                    )
                }

                recordEcoMetrics(tokenCount)
                recordMessage(accumulated.toString().trim(), "assistant", tokenCount)

                logger.i { "Welcome message generated: $tokenCount tokens in ${duration}ms" }
            }.onFailure { e ->
                logger.e(e) { "Welcome generation failed" }
                _uiState.update { it.copy(generationState = GenerationState.Idle) }
            }
        } catch (e: Exception) {
            logger.e(e) { "Welcome generation error" }
            _uiState.update { it.copy(generationState = GenerationState.Idle) }
        }
    }

    private suspend fun generateResponse(prompt: String) {
        val placeholderMessage = ChatMessage(
            text = "",
            isUser = false,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )

        _uiState.update {
            it.copy(messages = it.messages + placeholderMessage)
        }

        try {
            // Use ContextRetrievalUseCase for context building
            val context = contextRetrieval.retrieveContext(prompt)

            // Use GenerationConfigBuilder for device-adaptive config
            val config = configBuilder.buildFromIntent(context.intentCategory)

            val accumulated = StringBuilder()
            var tokenCount = 0
            val startTime = Clock.System.now().toEpochMilliseconds()

            // Build full prompt with context
            val fullPrompt = if (context.hasContext) {
                "${context.context}\n\nUser: $prompt"
            } else {
                prompt
            }

            val result = aiEngine.generateStreaming(
                prompt = fullPrompt,
                config = config,
                onToken = { token ->
                    val cleanToken = token.trim()
                    if (cleanToken.isNotEmpty()) {
                        accumulated.append(cleanToken).append(" ")
                        tokenCount++

                        _uiState.update { state ->
                            val updatedMessages = state.messages.toMutableList()
                            if (updatedMessages.isNotEmpty()) {
                                updatedMessages[updatedMessages.lastIndex] = updatedMessages.last().copy(
                                    text = accumulated.toString().trim()
                                )
                            }
                            state.copy(
                                messages = updatedMessages,
                                generationState = GenerationState.Streaming(
                                    partialText = accumulated.toString().trim(),
                                    tokenCount = tokenCount
                                )
                            )
                        }
                    }
                }
            )

            result.onSuccess {
                handleGenerationSuccess(accumulated, tokenCount, startTime, context)
            }.onFailure { e ->
                handleGenerationFailure(e)
            }
        } catch (e: Exception) {
            handleGenerationFailure(e)
        }
    }

    private fun handleGenerationSuccess(
        accumulated: StringBuilder,
        tokenCount: Int,
        startTime: Long,
        context: EnrichedContext
    ) {
        val duration = Clock.System.now().toEpochMilliseconds() - startTime
        val stats = GenerationStats(
            tokenCount = tokenCount,
            durationMs = duration,
            tokensPerSecond = if (duration > 0) tokenCount * 1000f / duration else 0f,
            ragInfo = context.ragInfo,
            ragSources = context.ragSources,
            ragConfidence = context.ragConfidence
        )

        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            if (updatedMessages.isNotEmpty()) {
                updatedMessages[updatedMessages.lastIndex] = updatedMessages.last().copy(
                    text = accumulated.toString().trim(),
                    inferenceStats = stats.formatFull(),
                    ragSources = context.ragSources
                )
            }
            state.copy(
                messages = updatedMessages,
                generationState = GenerationState.Complete(
                    finalText = accumulated.toString().trim(),
                    stats = stats
                ),
                ragInfo = context.ragInfo
            )
        }

        recordEcoMetrics(tokenCount)
        recordMessage(
            content = accumulated.toString().trim(),
            role = "assistant",
            tokens = tokenCount,
            ragSources = context.ragSources,
            ragConfidence = context.ragConfidence
        )

        logger.i { "Response generated: $tokenCount tokens in ${duration}ms (${stats.formatSpeed()})" }
    }

    private fun handleGenerationFailure(e: Throwable) {
        logger.e(e as? Exception ?: RuntimeException(e)) { "Generation failed" }
        val chatError = mapExceptionToError(e as? Exception ?: RuntimeException(e))
        _uiState.update { state ->
            val updatedMessages = state.messages.dropLast(1)
            state.copy(
                messages = updatedMessages,
                generationState = GenerationState.Failed(chatError),
                error = chatError
            )
        }
    }

    private fun recordEcoMetrics(tokenCount: Int) {
        if (tokenCount <= 0) return

        scope.launch {
            try {
                val savings = EcoCalculator.calculateSavings(tokenCount)

                ecoMetricsRepo.recordMetrics(
                    savings = savings,
                    sessionId = sessionId,
                    projectId = projectId
                )

                _uiState.update { state ->
                    state.copy(
                        sessionEcoStats = SessionEcoStats(
                            totalTokens = state.sessionEcoStats.totalTokens + tokenCount,
                            waterMl = state.sessionEcoStats.waterMl + savings.waterSavedMl,
                            energyWh = state.sessionEcoStats.energyWh + savings.energySavedWh,
                            co2G = state.sessionEcoStats.co2G + savings.co2PreventedG,
                            messageCount = state.sessionEcoStats.messageCount + 1
                        )
                    )
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed to record eco metrics" }
            }
        }
    }

    private fun recordMessage(
        content: String,
        role: String,
        tokens: Int,
        ragSources: String? = null,
        ragConfidence: Double? = null
    ) {
        scope.launch {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                val messageId = "msg_${now}_${(0..9999).random()}"

                database.messageQueries.insertMessage(
                    id = messageId,
                    project_id = projectId,
                    conversation_id = currentConversationId,
                    role = role,
                    content = content,
                    tokens = tokens.toLong(),
                    timestamp = now,
                    image_uri = null,
                    sentiment_valence = null,
                    sentiment_arousal = null,
                    sentiment_dominance = null,
                    sentiment_emotion = null,
                    sentiment_intensity = null,
                    rag_sources = ragSources,
                    rag_confidence = ragConfidence
                )

                if (role == "user") {
                    currentConversationId?.let { convId ->
                        val conv = conversationRepo.getConversationById(convId)
                        if (conv?.title == "Chat Session") {
                            val title = content.take(50) + if (content.length > 50) "..." else ""
                            conversationRepo.updateConversationTitle(convId, title)
                        }
                        conversationRepo.incrementMessageCount(convId, tokens.toLong())
                    }
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed to record message" }
            }
        }
    }

    private fun mapExceptionToError(e: Exception): ChatError {
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
 * Collect ChatScreenViewModel state as Compose State.
 */
@Composable
fun ChatScreenViewModel.collectAsState(): State<ChatUiState> {
    return uiState.collectAsState()
}
