package app.m1k3.ai.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.ai.SmolLM2Engine
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.knowledge.KnowledgeRetrievalService
import app.m1k3.ai.assistant.knowledge.PromptEnhancer
import app.m1k3.ai.assistant.design.components.MaChatBubbleUser
import app.m1k3.ai.assistant.design.components.MaChatBubbleAI
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.avatar.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * M1K3 AI - Chat Screen
 *
 * Beautiful chat interface with live AI responses.
 * 100% local inference on your Pixel 6 Pro!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    aiEngine: SmolLM2Engine,
    database: MaDatabase
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var engineInitialized by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Haptic feedback for message outcomes
    val haptics = rememberHapticFeedback()

    // Avatar state management
    val avatarVM = rememberAvatarViewModel()
    val avatarState by avatarVM.collectAsState()

    // Initialize knowledge retrieval service
    val retrievalService = remember { KnowledgeRetrievalService(database) }

    // Sync avatar with AI generation state
    LaunchedEffect(isGenerating) {
        avatarVM.syncWithAI(isGenerating)
    }

    // Initialize AI engine on first load
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                aiEngine.initialize()
                engineInitialized = true

                // Generate context-aware welcome message
                val aiMessageTimestamp = Clock.System.now().toEpochMilliseconds()
                val aiMessageIndex = messages.size

                messages = messages + ChatMessage(
                    text = "...",
                    isUser = false,
                    timestamp = aiMessageTimestamp,
                    inferenceStats = "🔄 Initializing..."
                )

                // Generate personalized welcome
                var welcomeText = ""
                aiEngine.generateStreaming(
                    prompt = "Greet the user warmly and introduce yourself as M1K3, their privacy-first AI assistant. " +
                            "Mention that you run 100% locally on their device and respect their privacy. Keep it brief and friendly.",
                    maxTokens = 128,  // Short welcome message
                    temperature = 0.3f  // Slightly creative but still coherent
                ) { token ->
                    welcomeText += token
                    // Update welcome message in real-time
                    withContext(Dispatchers.Main) {
                        val updatedMessages = messages.toMutableList()
                        updatedMessages[aiMessageIndex] = ChatMessage(
                            text = welcomeText,
                            isUser = false,
                            timestamp = aiMessageTimestamp
                        )
                        messages = updatedMessages
                    }
                }

            } catch (e: Exception) {
                messages = messages + ChatMessage(
                    text = "⚠️ AI engine initialization failed: ${e.message}. " +
                            "Make sure the ONNX model is in assets/",
                    isUser = false,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    isError = true
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "M1K3",
                                style = MaTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.TextPrimary
                            )
                            Text(
                                if (engineInitialized) "🟢 AI Ready (100% Local)" else "🔄 Loading...",
                                style = MaTypography.bodySmall,
                                color = if (engineInitialized) MaColors.Orange else MaColors.TextSecondary
                            )
                        }
                        // Mini avatar indicator
                        MiniAvatarIndicator(
                            state = avatarState,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("← Back", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaColors.BgPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onDebugClick,
                containerColor = MaColors.Orange,
                contentColor = MaColors.White
            ) {
                Text("🎨", style = MaterialTheme.typography.headlineMedium)
            }
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isGenerating && engineInitialized) {
                        val userMessage = ChatMessage(
                            text = inputText,
                            isUser = true,
                            timestamp = Clock.System.now().toEpochMilliseconds()
                        )
                        messages = messages + userMessage

                        // Detect emotion from user message
                        avatarVM.processMessage(inputText, isUserMessage = true)

                        val prompt = inputText
                        inputText = ""
                        isGenerating = true
                        avatarVM.startThinking()

                        scope.launch {
                            try {
                                // Add placeholder AI message that will be updated during streaming
                                val aiMessageTimestamp = Clock.System.now().toEpochMilliseconds()
                                val aiMessageIndex = messages.size

                                messages = messages + ChatMessage(
                                    text = "...",
                                    isUser = false,
                                    timestamp = aiMessageTimestamp,
                                    inferenceStats = "🔄 Generating..."
                                )

                                // Auto-scroll to show the new message
                                try {
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    // Scroll animation failed, but continue with inference
                                }

                                // Use streaming generation for real-time updates
                                val startTime = System.currentTimeMillis()
                                var streamedText = ""
                                var tokenCount = 0
                                var ragInfo = ""

                                // RAG: Retrieve relevant knowledge before generation
                                val retrievedFacts = retrievalService.retrieve(prompt, limit = 3)
                                val enhancedPrompt = PromptEnhancer.enhancePrompt(prompt, retrievedFacts)

                                // Track RAG usage for display
                                if (enhancedPrompt.hasKnowledge) {
                                    ragInfo = PromptEnhancer.formatKnowledgeSummary(retrievedFacts)
                                    println("📚 [RAG] $ragInfo")
                                    println("📚 [RAG] Enhanced prompt length: ${enhancedPrompt.enhancedQuery.length} chars")
                                }

                                // Use device-adaptive max tokens (will be exposed from engine)
                                // For now, use 256 which is reasonable for 6GB+ devices
                                aiEngine.generateStreaming(
                                    prompt = enhancedPrompt.enhancedQuery,  // Use enhanced prompt with knowledge
                                    maxTokens = 256,  // TODO: Use aiEngine.getOptimalMaxTokens()
                                    temperature = 0.0f  // Greedy decoding for coherent output
                                ) { token ->
                                    // Append each token as it arrives
                                    streamedText += token
                                    tokenCount++

                                    // Update the message in real-time on the MAIN thread
                                    withContext(Dispatchers.Main) {
                                        val updatedMessages = messages.toMutableList()
                                        updatedMessages[aiMessageIndex] = ChatMessage(
                                            text = streamedText,
                                            isUser = false,
                                            timestamp = aiMessageTimestamp,
                                            inferenceStats = "⚡ Streaming... ($tokenCount tokens)"
                                        )
                                        messages = updatedMessages

                                        // Auto-scroll to keep the message visible
                                        // Use scrollToItem (instant) during streaming to avoid MutatorMutex conflicts
                                        // that would cancel the generation coroutine with CancellationException
                                        if (tokenCount % 3 == 0) {  // Scroll every 3 tokens to reduce UI updates
                                            try {
                                                listState.scrollToItem(messages.size - 1)
                                            } catch (e: Exception) {
                                                // Ignore scroll failures - inference must continue regardless
                                            }
                                        }
                                    }
                                }

                                // Final update with complete stats
                                val totalTime = System.currentTimeMillis() - startTime
                                val tokensPerSec = if (totalTime > 0) {
                                    (tokenCount * 1000.0f) / totalTime
                                } else {
                                    0f
                                }

                                // Build stats with RAG info
                                val statsText = buildString {
                                    append("⚡ $tokenCount tokens in ${totalTime}ms (${"%.1f".format(tokensPerSec)} tok/s)")
                                    if (ragInfo.isNotEmpty()) {
                                        append(" • $ragInfo")
                                    }
                                }

                                val updatedMessages = messages.toMutableList()
                                updatedMessages[aiMessageIndex] = ChatMessage(
                                    text = streamedText.ifEmpty { "..." },
                                    isUser = false,
                                    timestamp = aiMessageTimestamp,
                                    inferenceStats = statsText
                                )
                                messages = updatedMessages

                                // Detect emotion from AI response
                                avatarVM.processMessage(streamedText, isUserMessage = false)
                                avatarVM.startSpeaking()

                                // Success haptic feedback
                                haptics.success()

                                // Final scroll with animation (safe since streaming is done)
                                try {
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    // Scroll animation failed, but inference already completed
                                }

                            } catch (e: Exception) {
                                avatarVM.showError("Generation failed")

                                // Error haptic feedback
                                haptics.error()

                                messages = messages + ChatMessage(
                                    text = "Error: ${e.message}",
                                    isUser = false,
                                    timestamp = Clock.System.now().toEpochMilliseconds(),
                                    isError = true
                                )
                            } finally {
                                isGenerating = false
                                // Return to idle after a delay
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    avatarVM.returnToIdle()
                                }
                            }
                        }
                    }
                },
                enabled = engineInitialized && !isGenerating
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }

            // Loading indicator
            if (isGenerating) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
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
            isError = message.isError
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val hasText = text.isNotBlank()

    // Haptic feedback controller
    val haptics = rememberHapticFeedback()

    // Track previous text for "typing start" detection
    var previousText by remember { mutableStateOf(text) }

    // Detect typing start for haptic feedback
    LaunchedEffect(text) {
        if (previousText.isEmpty() && text.isNotEmpty()) {
            haptics.light()  // Subtle feedback when typing starts
        }
        previousText = text
    }

    // Enhanced focus animations
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaColors.BorderLight
            isFocused -> MaColors.Orange
            else -> MaColors.BorderLight
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )

    // Subtle elevation on focus
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 4.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )

    // Subtle scale on focus for depth perception
    val fieldScale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fieldScale"
    )

    // Animated send button scale with bouncy spring
    val sendButtonScale by animateFloatAsState(
        targetValue = if (hasText && enabled) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sendButtonScale"
    )

    // Glow pulse animation when focused
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.15f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "glowAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaColors.BgPrimary
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            // Glow effect behind field when focused
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = MaColors.Orange.copy(alpha = glowAlpha)
                        )
                )
            }

            // Integrated input field with send button
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 180.dp)
                    .scale(fieldScale)
                    .shadow(
                        elevation = elevation,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = MaColors.Orange.copy(alpha = 0.3f)
                    ),
                enabled = enabled,
                textStyle = MaTypography.bodyLarge.copy(
                    color = if (enabled) MaColors.TextPrimary else MaColors.TextDisabled
                ),
                cursorBrush = SolidColor(MaColors.Orange),
                maxLines = 6,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (hasText && enabled) onSend() }
                ),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                color = if (enabled) MaColors.BgSecondary else MaColors.BgPrimary,
                                shape = RoundedCornerShape(28.dp)
                            )
                            .border(
                                width = borderWidth,
                                color = borderColor,
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(start = 20.dp, end = 60.dp, top = 16.dp, bottom = 16.dp)
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Message M1K3 AI...",
                                style = MaTypography.bodyLarge,
                                color = MaColors.TextDisabled,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Integrated send button (Claude-style)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .scale(sendButtonScale)
                    .clip(CircleShape)
                    .background(
                        color = if (hasText && enabled) MaColors.Orange else MaColors.BgSecondary,
                        shape = CircleShape
                    )
                    .clickable(
                        enabled = hasText && enabled,
                        onClick = {
                            haptics.strong()  // Strong feedback on send
                            onSend()
                        },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Custom arrow icon (↑)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(20.dp)
                ) {
                    val arrowColor = if (hasText && enabled) MaColors.White else MaColors.TextDisabled
                    val strokeWidth = 2.5f
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val arrowLength = size.height * 0.5f

                    // Draw arrow shaft (vertical line)
                    drawLine(
                        color = arrowColor,
                        start = Offset(centerX, centerY + arrowLength / 2),
                        end = Offset(centerX, centerY - arrowLength / 2),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    // Draw arrow head (left line)
                    drawLine(
                        color = arrowColor,
                        start = Offset(centerX, centerY - arrowLength / 2),
                        end = Offset(centerX - arrowLength / 3, centerY - arrowLength / 2 + arrowLength / 3),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    // Draw arrow head (right line)
                    drawLine(
                        color = arrowColor,
                        start = Offset(centerX, centerY - arrowLength / 2),
                        end = Offset(centerX + arrowLength / 3, centerY - arrowLength / 2 + arrowLength / 3),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Chat message data class
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false,
    val inferenceStats: String? = null
)
