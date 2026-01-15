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
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.chat.*
import app.m1k3.ai.assistant.database.*
import app.m1k3.ai.assistant.design.components.*
import app.m1k3.ai.assistant.design.tokens.*
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.embedding.rememberEmbeddingsViewModel
import app.m1k3.ai.assistant.ui.components.ChatHeader
import app.m1k3.ai.assistant.ui.components.ChatInputBar
import app.m1k3.ai.assistant.ui.components.ChatInputBarContainer
import app.m1k3.ai.assistant.ui.components.ChatMessageList
import app.m1k3.ai.assistant.ui.components.ContextWindowIndicator
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

            // Context Window Indicator - Shows conversation history usage
            ContextWindowIndicator(
                state = uiState.contextWindow
            )

            // Messages list
            ChatMessageList(
                messages = uiState.messages,
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

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun ChatScreenEmptyPreview() {
    MaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaColors.BgPrimary)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ChatMessageList(
                    messages = emptyList(),
                    isGenerating = false,
                    listState = rememberLazyListState(),
                    showEcoIndicator = false
                )
            }

            ChatHeader(
                engineInitialized = true,
                avatarState = AvatarState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            ChatInputBarContainer(
                inputBar = {
                    ChatInputBar(
                        text = "",
                        onTextChange = {},
                        onSend = {},
                        enabled = true
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Preview
@Composable
private fun ChatScreenWithMessagesPreview() {
    MaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaColors.BgPrimary)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ChatMessageList(
                    messages = listOf(
                        ChatMessage(
                            text = "What is machine learning?",
                            isUser = true,
                            timestamp = System.currentTimeMillis()
                        ),
                        ChatMessage(
                            text = "Machine learning is a subset of artificial intelligence that enables systems to learn and improve from experience without being explicitly programmed.",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            inferenceStats = "⚡ 42 tokens in 2.3s"
                        )
                    ),
                    isGenerating = false,
                    listState = rememberLazyListState(),
                    showEcoIndicator = true
                )
            }

            ChatHeader(
                engineInitialized = true,
                avatarState = AvatarState(emotion = AvatarEmotion.HAPPY),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            ChatInputBarContainer(
                inputBar = {
                    ChatInputBar(
                        text = "",
                        onTextChange = {},
                        onSend = {},
                        enabled = true
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Preview
@Composable
private fun ChatScreenGeneratingPreview() {
    MaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaColors.BgPrimary)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ContextWindowIndicator(
                    state = ContextWindowState(
                        historyMessageCount = 5,
                        historyTokens = 500,
                        maxContextTokens = 4096,
                        deviceTier = "High-end"
                    )
                )

                ChatMessageList(
                    messages = listOf(
                        ChatMessage(
                            text = "Explain quantum computing",
                            isUser = true,
                            timestamp = System.currentTimeMillis()
                        )
                    ),
                    isGenerating = true,
                    listState = rememberLazyListState(),
                    showEcoIndicator = false
                )
            }

            ChatHeader(
                engineInitialized = true,
                avatarState = AvatarState(
                    emotion = AvatarEmotion.THINKING,
                    activity = AvatarActivity.GENERATING
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            ChatInputBarContainer(
                inputBar = {
                    ChatInputBar(
                        text = "",
                        onTextChange = {},
                        onSend = {},
                        enabled = false
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
