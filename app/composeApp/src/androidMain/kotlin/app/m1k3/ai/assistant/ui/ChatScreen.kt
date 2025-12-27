package app.m1k3.ai.assistant.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.chat.*
import app.m1k3.ai.assistant.database.*
import app.m1k3.ai.assistant.design.components.*
import app.m1k3.ai.assistant.design.tokens.*
import app.m1k3.ai.assistant.embedding.rememberEmbeddingsViewModel
import app.m1k3.ai.assistant.ui.components.ChatHeader
import app.m1k3.ai.assistant.ui.components.ChatInputBar
import app.m1k3.ai.assistant.ui.components.ChatInputBarContainer
import app.m1k3.ai.assistant.ui.components.ChatMessageList
import app.m1k3.ai.assistant.ui.components.EcoIndicator
import app.m1k3.ai.assistant.ui.components.EcoIndicatorVariant
import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.launch

private val logger = Logger.withTag("ChatScreen")

/**
 * M1K3 AI - Chat Screen
 *
 * Beautiful chat interface with live AI responses.
 * 100% local inference - privacy first!
 *
 * **Architecture:**
 * - Uses ChatScreenViewModel for state management
 * - Delegates to extracted components (ChatInputBar, ChatHeader, etc.)
 * - Minimal UI logic - ViewModel handles business logic
 *
 * **Responsibilities:**
 * - Compose UI layout
 * - Event delegation to ViewModel
 * - Avatar state synchronization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onEcoStatsClick: () -> Unit = {},
    aiEngine: BaseLlmEngine,
    database: MaDatabase,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Avatar state management
    val avatarVM = rememberAvatarViewModel()
    val avatarState by avatarVM.collectAsState()

    // Embeddings for RAG (Phase 3)
    val embeddingsVM = rememberEmbeddingsViewModel()

    // ChatScreenViewModel - Single source of truth for chat state
    val viewModel = rememberChatScreenViewModel(
        aiEngine = aiEngine,
        database = database,
        context = context,
        projectId = "default",
        embeddingEngine = embeddingsVM.getEngine()
    )
    val uiState by viewModel.collectAsState()

    // Sync avatar with generation state
    LaunchedEffect(uiState.generationState) {
        when (uiState.generationState) {
            is GenerationState.Thinking -> avatarVM.startThinking()
            is GenerationState.Streaming -> avatarVM.startSpeaking()
            is GenerationState.Complete -> {
                val complete = uiState.generationState as GenerationState.Complete
                avatarVM.processMessage(complete.finalText, isUserMessage = false)
                kotlinx.coroutines.delay(2000)
                avatarVM.returnToIdle()
            }
            is GenerationState.Failed -> {
                avatarVM.showError("Generation failed")
                kotlinx.coroutines.delay(2000)
                avatarVM.returnToIdle()
            }
            else -> {}
        }
    }

    // Initialize AI engine
    LaunchedEffect(Unit) {
        viewModel.initializeEngine()
    }

    // Auto-scroll when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(uiState.messages.size - 1)
            } catch (e: Exception) {
                // Scroll animation failed, continue
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize()
    ) {
        // Background: Messages list (behind overlays)
        Column(modifier = Modifier.fillMaxSize()) {
            // Eco Indicator - Real-time environmental impact
            EcoIndicatorSection(
                stats = uiState.sessionEcoStats,
                onEcoStatsClick = onEcoStatsClick
            )

            // Messages list
            ChatMessageList(
                messages = uiState.messages.map { msg ->
                    // Convert ChatMessage to the format expected by ChatMessageList
                    app.m1k3.ai.assistant.chat.ChatMessage(
                        text = msg.text,
                        isUser = msg.isUser,
                        timestamp = msg.timestamp,
                        isError = msg.isError,
                        inferenceStats = msg.inferenceStats,
                        ragSources = msg.ragSources
                    )
                },
                isGenerating = uiState.generationState.isGenerating,
                listState = listState,
                showEcoIndicator = uiState.sessionEcoStats.messageCount > 0
            )
        }

        // Top overlay: Toolbar with blur and gradient
        ChatHeader(
            engineInitialized = uiState.engineState.isReady,
            avatarState = avatarState,
            onClearClick = { viewModel.clearConversation() },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // Bottom overlay: Input bar with gradient
        ChatInputBarContainer(
            inputBar = {
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = {
                        // Process avatar emotion from user message
                        avatarVM.processMessage(uiState.inputText, isUserMessage = true)
                        viewModel.sendMessage()
                    },
                    enabled = uiState.isInputEnabled
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Error dialog
        uiState.error?.let { error ->
            ErrorSnackbar(
                error = error,
                onDismiss = { viewModel.clearError() }
            )
        }
    }
}

/**
 * Eco Indicator Section - Shows environmental impact.
 */
@Composable
private fun EcoIndicatorSection(
    stats: SessionEcoStats,
    onEcoStatsClick: () -> Unit
) {
    if (stats.messageCount > 0) {
        Box(
            modifier = Modifier
                .testTag("eco_indicator")
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(top = 120.dp), // Account for toolbar overlay
            contentAlignment = Alignment.Center
        ) {
            EcoIndicator(
                stats = stats,
                onClick = onEcoStatsClick,
                variant = EcoIndicatorVariant.COMPACT
            )
        }
    }
}

/**
 * Error Snackbar - Shows error messages.
 */
@Composable
private fun ErrorSnackbar(
    error: ChatError,
    onDismiss: () -> Unit
) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaColors.White)
            }
        },
        containerColor = MaColors.Error,
        contentColor = MaColors.White
    ) {
        Text("${error.toEmoji()} ${error.toUserMessage()}")
    }
}

/**
 * ChatBubble - Renders a single message bubble.
 */
@Composable
fun ChatBubble(message: app.m1k3.ai.assistant.chat.ChatMessage) {
    if (message.isUser) {
        MaChatBubbleUser(
            text = message.text,
            timestamp = message.timestamp
        )
    } else {
        MaChatBubbleAI(
            text = message.text,
            timestamp = message.timestamp,
            inferenceStats = message.inferenceStats,
            isError = message.isError,
            ragSources = message.ragSources
        )
    }
}
