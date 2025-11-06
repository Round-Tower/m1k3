package app.m1k3.ai.assistant.chat

import androidx.compose.runtime.*
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * ChatViewModel - Manages chat state with eco tracking integration
 *
 * **Philosophy:**
 * Every message matters for the environment. ChatViewModel automatically tracks
 * environmental savings from local AI inference, providing transparency and
 * eco-consciousness without user intervention.
 *
 * **Features:**
 * - Chat message state management
 * - Automatic eco-metrics tracking (water, energy, CO2)
 * - Conversation history integration
 * - Message persistence to database
 * - Real-time environmental savings display
 *
 * **Usage Example:**
 * ```kotlin
 * val chatVM = rememberChatViewModel(database, projectId)
 * val state by chatVM.collectAsState()
 *
 * // After AI generates response
 * chatVM.recordMessage(
 *     content = aiResponse,
 *     role = "assistant",
 *     tokens = tokenCount
 * )
 *
 * // Display eco stats
 * Text("💧 Saved: ${state.sessionEcoStats.waterMl}ml water")
 * ```
 */
class ChatViewModel(
    private val database: MaDatabase,
    private val projectId: String,
    private val scope: CoroutineScope
) {
    // Repositories
    private val conversationRepo = ConversationRepository(database)
    private val ecoMetricsRepo = EcoMetricsRepository(database)

    // State flows
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Message state for chat UI
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Current session tracking
    private val sessionId = "chat_session_${Clock.System.now().toEpochMilliseconds()}"
    private var currentConversationId: Long? = null

    init {
        // Load conversation history FIRST
        loadMessages()

        // Create or resume conversation
        scope.launch {
            try {
                val convId = conversationRepo.createConversation(
                    projectId = projectId,
                    title = "Chat Session" // Will be updated with first user message
                )
                currentConversationId = convId
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to initialize conversation: ${e.message}"
                )
            }
        }
    }

    /**
     * Load conversation history from database.
     * Called automatically on initialization.
     */
    private fun loadMessages() {
        scope.launch {
            try {
                val dbMessages = database.messageQueries
                    .getMessagesForProject(projectId)
                    .executeAsList()

                val chatMessages = dbMessages.map { it.toChatMessage() }
                _messages.value = chatMessages

                println("✅ [ChatViewModel] Loaded ${chatMessages.size} messages from database")
            } catch (e: Exception) {
                println("❌ [ChatViewModel] Failed to load messages: ${e.message}")
                _state.value = _state.value.copy(
                    error = "Failed to load conversation history: ${e.message}"
                )
            }
        }
    }

    /**
     * Convert database Message to UI ChatMessage.
     */
    private fun app.m1k3.ai.assistant.database.Message.toChatMessage(): ChatMessage {
        return ChatMessage(
            text = this.content,
            isUser = this.role == "user",
            timestamp = this.timestamp,
            isError = false,
            inferenceStats = if (this.tokens != null && this.role == "assistant") {
                "⚡ ${this.tokens} tokens"
            } else null,
            ragSources = this.rag_sources
        )
    }

    /**
     * Add a message to the UI state.
     * Used when generating new messages during the session.
     */
    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    /**
     * Update a message in the UI state.
     * Used for streaming updates during generation.
     */
    fun updateMessage(index: Int, message: ChatMessage) {
        val updatedList = _messages.value.toMutableList()
        if (index in updatedList.indices) {
            updatedList[index] = message
            _messages.value = updatedList
        }
    }

    /**
     * Record a message and track eco-metrics.
     *
     * Automatically:
     * - Saves message to database
     * - Calculates environmental savings
     * - Records eco-metrics
     * - Updates session statistics
     * - Updates conversation metadata
     *
     * @param content Message content
     * @param role Message role ("user" or "assistant")
     * @param tokens Token count (for eco calculations)
     * @param ragSources RAG knowledge sources (Phase 3)
     * @param ragConfidence RAG confidence score (Phase 3)
     */
    fun recordMessage(
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

                // Calculate eco savings for this message
                val savings = if (role == "assistant" && tokens > 0) {
                    EcoCalculator.calculateSavings(tokens)
                } else {
                    null
                }

                // Save message to database
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

                // Record eco-metrics for assistant responses
                if (savings != null) {
                    ecoMetricsRepo.recordMetrics(
                        savings = savings,
                        sessionId = sessionId,
                        projectId = projectId
                    )

                    // Update session eco stats
                    val currentStats = _state.value.sessionEcoStats
                    _state.value = _state.value.copy(
                        sessionEcoStats = SessionEcoStats(
                            totalTokens = currentStats.totalTokens + tokens,
                            waterMl = currentStats.waterMl + savings.waterSavedMl,
                            energyWh = currentStats.energyWh + savings.energySavedWh,
                            co2G = currentStats.co2G + savings.co2PreventedG,
                            messageCount = currentStats.messageCount + 1
                        )
                    )
                }

                // Update conversation metadata
                currentConversationId?.let { convId ->
                    // Update title with first user message
                    if (role == "user" && conversationRepo.getConversationById(convId)?.title == "Chat Session") {
                        val title = content.take(50) + if (content.length > 50) "..." else ""
                        conversationRepo.updateConversationTitle(convId, title)
                    }

                    // Increment message count
                    conversationRepo.incrementMessageCount(convId, tokens.toLong())
                }

                // Clear error if any
                _state.value = _state.value.copy(error = null)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to record message: ${e.message}"
                )
            }
        }
    }

    /**
     * Get current conversation ID.
     *
     * @return Conversation ID or null if not initialized
     */
    fun getCurrentConversationId(): Long? = currentConversationId

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Reset session stats.
     */
    fun resetSessionStats() {
        _state.value = _state.value.copy(
            sessionEcoStats = SessionEcoStats()
        )
    }

    /**
     * Clear all conversation history for the current project.
     *
     * ⚠️ WARNING: This is a destructive operation and cannot be undone.
     * Use for debugging and testing only.
     *
     * This method:
     * 1. Deletes all messages for the project from database
     * 2. Clears the UI message list
     * 3. Resets conversation ID (will create new conversation on next message)
     * 4. Resets session stats
     */
    fun clearConversation() {
        scope.launch {
            try {
                println("🗑️ [ChatViewModel] Clearing conversation history for project: $projectId")

                // Delete all messages from database
                database.messageQueries.deleteMessagesForProject(projectId)

                // Delete all conversations for this project
                val conversations = conversationRepo.getConversationsByProject(projectId)
                conversations.forEach { conv ->
                    conversationRepo.deleteConversation(conv.id)
                }

                // Clear UI state
                _messages.value = emptyList()

                // Reset conversation ID (will create new on next message)
                currentConversationId = null

                // Reset session stats
                resetSessionStats()

                println("✅ [ChatViewModel] Conversation history cleared successfully")
            } catch (e: Exception) {
                println("❌ [ChatViewModel] Failed to clear conversation: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(
                    error = "Failed to clear conversation: ${e.message}"
                )
            }
        }
    }
}

/**
 * Chat UI state.
 */
data class ChatState(
    val sessionEcoStats: SessionEcoStats = SessionEcoStats(),
    val error: String? = null
)

/**
 * Session eco-metrics statistics.
 */
data class SessionEcoStats(
    val totalTokens: Int = 0,
    val waterMl: Long = 0,
    val energyWh: Long = 0,
    val co2G: Long = 0,
    val messageCount: Int = 0
) {
    /**
     * Format water for display.
     */
    fun formatWater(): String {
        return when {
            waterMl >= 1000 -> String.format("%.2f L", waterMl / 1000.0)
            else -> "$waterMl ml"
        }
    }

    /**
     * Format energy for display.
     */
    fun formatEnergy(): String {
        return when {
            energyWh >= 1000 -> String.format("%.2f kWh", energyWh / 1000.0)
            else -> "$energyWh Wh"
        }
    }

    /**
     * Format CO2 for display.
     */
    fun formatCO2(): String {
        return when {
            co2G >= 1000 -> String.format("%.2f kg", co2G / 1000.0)
            else -> "$co2G g"
        }
    }
}

/**
 * Chat message data class for UI.
 *
 * Represents a single message in the chat conversation.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false,
    val inferenceStats: String? = null,
    val ragSources: String? = null  // Phase 3: RAG source tracking
)

/**
 * Create and remember chat view model.
 *
 * @param database Database instance
 * @param projectId Project to track messages under
 * @return Chat view model scoped to composition
 */
@Composable
fun rememberChatViewModel(
    database: MaDatabase,
    projectId: String
): ChatViewModel {
    val scope = rememberCoroutineScope()
    return remember(projectId) {
        ChatViewModel(
            database = database,
            projectId = projectId,
            scope = scope
        )
    }
}

/**
 * Collect chat state as Compose State.
 *
 * @return Current chat state
 */
@Composable
fun ChatViewModel.collectAsState(): State<ChatState> {
    return state.collectAsState()
}
