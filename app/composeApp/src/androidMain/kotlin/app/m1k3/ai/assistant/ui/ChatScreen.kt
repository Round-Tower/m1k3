package app.m1k3.ai.assistant.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.app.InitializationViewModel
import app.m1k3.ai.assistant.avatar.LocalSharedAvatarVM
import app.m1k3.ai.assistant.chat.ChatMessage
import app.m1k3.ai.assistant.chat.toEmoji
import app.m1k3.ai.assistant.chat.toUserMessage
import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.assistant.chat.ChatScreenViewModel
import app.m1k3.ai.assistant.chat.ContextWindowState
import app.m1k3.ai.assistant.chat.GenerationState
import app.m1k3.ai.assistant.chat.SessionEcoStats
import app.m1k3.ai.assistant.chat.collectAsState
import app.m1k3.ai.assistant.chat.isGenerating
import app.m1k3.ai.assistant.chat.isInputEnabled
import app.m1k3.ai.assistant.design.components.MaChatBubbleAI
import app.m1k3.ai.assistant.design.components.MaChatBubbleUser
import app.m1k3.ai.assistant.design.components.MaStatusCard
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.ui.components.ChatInputBar
import app.m1k3.ai.assistant.ui.components.ChatInputBarContainer
import app.m1k3.ai.assistant.ui.components.ChatMessageList
import app.m1k3.ai.assistant.ui.components.ClearConversationDialog
import app.m1k3.ai.assistant.ui.components.ContextWindowIndicator
import app.m1k3.ai.assistant.ui.components.EcoIndicator
import app.m1k3.ai.assistant.ui.components.EcoIndicatorVariant
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.MainActivity
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
    onEcoStatsClick: () -> Unit = {},
    onClearConversationClick: (() -> Unit)? = null,
    projectId: String = "default"
) {
    rememberCoroutineScope()
    val listState = rememberLazyListState()
    val viewModel = koinViewModel<ChatScreenViewModel> {
        parametersOf(projectId)
    }

    // Avatar state management - use shared app-level ViewModel from CompositionLocal
    val avatarVM = LocalSharedAvatarVM.current

    val uiState by viewModel.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val haptics = rememberHapticFeedback()

    // Register clear callback
    LaunchedEffect(onClearConversationClick) {
        if (onClearConversationClick != null) {
            // This allows parent to trigger the dialog
        }
    }

    // Sync avatar with generation state
    if (avatarVM != null) {
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
    }

    // Initialize AI engine + consume shared text
    LaunchedEffect(Unit) {
        viewModel.initializeEngine()

        // Check for shared text from ACTION_SEND intent
        val sharedText = MainActivity.consumeSharedText()
        if (sharedText != null) {
            viewModel.sendSharedText(sharedText)
        }
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
            // Context Window Indicator - Shows conversation history usage
            ContextWindowIndicator(
                state = uiState.contextWindow
            )

            // Messages list
            ChatMessageList(
                messages = uiState.messages,
                isGenerating = uiState.generationState.isGenerating,
                listState = listState,
                showEcoIndicator = true,
                onSpeak = { text -> viewModel.speakMessage(text) }
            )

            // Eco Indicator - Real-time environmental impact
            EcoIndicatorSection(
                stats = uiState.sessionEcoStats,
                onEcoStatsClick = onEcoStatsClick
            )
        }

        // Bottom overlay: Input bar with gradient
        ChatInputBarContainer(
            inputBar = {
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = {
                        // Process avatar emotion from user message
                        avatarVM?.processMessage(uiState.inputText, isUserMessage = true)
                        viewModel.sendMessage()
                    },
                    enabled = uiState.isInputEnabled,
                    currentModel = uiState.currentModel,
                    onModelSwitch = { model -> viewModel.switchModel(model) }
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

        // Clear conversation dialog
        if (showClearDialog) {
            ClearConversationDialog(
                sessionStats = uiState.sessionEcoStats,
                onConfirm = {
                    viewModel.clearConversation()
                    showClearDialog = false
                },
                onDismiss = { showClearDialog = false }
            )
        }
    }

    // Expose clear callback to parent (for Toolbar)
    LaunchedEffect(onClearConversationClick) {
        // This registers the callback - when parent calls it, we show the dialog
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
    Box(
        modifier = Modifier
            .testTag("eco_indicator")
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        EcoIndicator(
            stats = stats,
            onClick = onEcoStatsClick,
            variant = EcoIndicatorVariant.COMPACT
        )
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
 * ChatBubble - Renders a single message bubble or status card.
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    onSpeak: ((String) -> Unit)? = null
) {
    when {
        message.isStatusMessage -> {
            MaStatusCard(
                greeting = message.text.lines().firstOrNull() ?: "Hello!",
                engineReady = true,
                memoryCount = message.statusMemoryCount ?: 0,
                knowledgeCount = message.statusKnowledgeCount ?: 0,
                maxContextTokens = message.statusMaxTokens ?: 2048,
                deviceTierName = message.statusDeviceTier ?: "Unknown",
                lastSessionWaterMl = message.statusLastWaterMl,
                lastSessionEnergyWh = message.statusLastEnergyWh,
                lastSessionCo2G = message.statusLastCo2G
            )
        }
        message.isUser -> {
            MaChatBubbleUser(
                text = message.text,
                timestamp = message.timestamp
            )
        }
        else -> {
            MaChatBubbleAI(
                text = message.text,
                timestamp = message.timestamp,
                inferenceStats = message.inferenceStats,
                isError = message.isError,
                ragSources = message.ragSources,
                onSpeak = if (onSpeak != null) {{ onSpeak(message.text) }} else null
            )
        }
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
