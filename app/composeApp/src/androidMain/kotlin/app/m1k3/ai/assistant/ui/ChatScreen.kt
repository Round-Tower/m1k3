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
import app.m1k3.ai.assistant.ui.components.ChatHeader
import app.m1k3.ai.assistant.ui.components.ChatMessageList
import app.m1k3.ai.assistant.ui.components.EcoIndicator
import app.m1k3.ai.assistant.ui.components.EcoIndicatorVariant
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.assistant.utils.cleanStreamingToken
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val logger = Logger.withTag("ChatScreen")

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
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.EDUCATION,
        app.m1k3.ai.assistant.rag.IntentClassifier.Intent.AI_ML -> QueryType.EDUCATIONAL

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

    // Engaging prompt hints for Gemma 3 IQ3_XXS (personality-driven, not forced brevity)
    // CRITICAL: Tell model to TEACH, not ask questions back (small models deflect)
    val systemPromptHint = when (queryType) {
        QueryType.EDUCATIONAL -> "Teach! Provide information using the facts given. Do NOT ask questions back - explain directly."
        QueryType.TECHNICAL -> "Be precise but make it accessible. Walk through the details step by step."
        QueryType.FACTUAL -> "Share the facts directly with interesting context. Answer the question."
        QueryType.CONVERSATIONAL -> "Be natural and friendly. Have a real conversation!"
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
    val prefs = remember {
        context.getSharedPreferences(
            "ma_ai_prefs",
            android.content.Context.MODE_PRIVATE
        )
    }
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

    // Initialize AI engine on first load
    LaunchedEffect(Unit) {
        scope.launch {
            aiEngine.initialize()
                .onSuccess {
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
                        val batteryManager =
                            context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                        val batteryLevel =
                            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                                ?: -1
                        val batteryInfo =
                            if (batteryLevel > 0) "$batteryLevel% battery" else "battery connected"

                        // Generate personalized welcome with device and knowledge context
                        var welcomeText = ""

                        aiEngine.generateStreaming(
                            prompt =
                                "Greet the user. " +
                                        "Mention that you're running 100% locally on their $deviceModel with $batteryInfo, " +
                                        "and that all conversations are private and never leave their device. " +
                                        "Keep it brief (2-3 sentences), friendly, and conversational.",
                            config = app.m1k3.ai.assistant.ai.GenerationConfig(
                                temperature = 0.5f
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
                        }.onFailure { e ->
                            logger.e(e) { "Welcome message generation failed" }
                            chatViewModel.updateMessage(
                                aiMessageIndex,
                                app.m1k3.ai.assistant.chat.ChatMessage(
                                    text = "⚠️ Failed to generate welcome message: ${e.message}",
                                    isUser = false,
                                    timestamp = aiMessageTimestamp,
                                    isError = true,
                                ),
                            )
                        }
                    }
                }
                .onFailure { e ->
                    logger.e(e) { "AI engine initialization failed" }
                    chatViewModel.addMessage(
                        app.m1k3.ai.assistant.chat.ChatMessage(
                            text =
                                "⚠️ AI engine initialization failed: ${e.message}. " +
                                        "Make sure the GGUF model is in assets/",
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
            ChatMessageList(
                messages = messages,
                isGenerating = isGenerating,
                listState = listState,
                showEcoIndicator = chatState.sessionEcoStats.messageCount > 0
            )
        }

        // Top overlay: Toolbar with blur and gradient
        ChatHeader(
            engineInitialized = engineInitialized,
            avatarState = avatarState,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

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
                            val streamedText =
                                StringBuilder()  // StringBuilder for O(1) append (vs O(n) String +=)
                            var tokenCount = 0
                            var hasContent =
                                false  // Track if we've received any non-whitespace content
                            var ragInfo = ""
                            var ragSources: String? = null
                            var ragConfidence: Double? = null

                            // Phase 3 RAG: Intent-aware knowledge retrieval with enriched prompt
                            // Get device context for personalized responses
                            val deviceModel = android.os.Build.MODEL
                            val activityManager =
                                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                            val memInfo = android.app.ActivityManager.MemoryInfo()
                            activityManager.getMemoryInfo(memInfo)
                            val deviceRamGB = (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()

                            // CLEAN system prompt (identity + personality ONLY, no facts)
                            // LlamaCppEngine will build this, but we provide base for RAG
                            val baseSystemPrompt =
                                "You are M1K3 (Mike), a friendly AI companion running 100% locally on ${deviceModel} (${deviceRamGB}GB RAM). " +
                                        "All conversations are private and never leave the device. " +
                                        "Be curious, enthusiastic, and engaging. Share your knowledge naturally like talking to a friend."

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

                                        // Quality validation: Trust RAGManager threshold (0.5 minSimilarity)
                                        // Note: RAGManager already filters by 0.5f, so we just pass through
                                        if (result.ragApplied) {
                                            // Log quality distribution but don't filter further
                                            val highQuality =
                                                result.retrievedFacts.count { it.similarity >= 0.6f }
                                            val mediumQuality =
                                                result.retrievedFacts.count { it.similarity in 0.5f..0.6f }
                                            logger.i {
                                                "RAG quality: ${highQuality} high (≥0.6), ${mediumQuality} medium (0.5-0.6) of ${result.retrievedFacts.size} total"
                                            }

                                            // If no facts at all, fall back to static KB
                                            if (result.retrievedFacts.isEmpty()) {
                                                logger.w { "No facts found, fallback to static KB" }
                                                result.copy(
                                                    enrichedPrompt = "",  // No RAG facts to add
                                                    ragApplied = false,
                                                )
                                            } else {
                                                result  // Keep all facts (already filtered by RAGManager)
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
                                val avgSimilarity =
                                    ragResult.retrievedFacts.map { it.similarity }.average()
                                        .toFloat()
                                val qualityEmoji = when {
                                    avgSimilarity >= 0.7f -> "✅"  // High quality - reliable
                                    avgSimilarity >= 0.6f -> "⚠️"  // Medium quality - useful
                                    else -> "❓"                    // Low quality - experimental
                                }

                                ragInfo =
                                    "$qualityEmoji ${ragResult.intent.category} (${(ragResult.confidence * 100).toInt()}%) • ${ragResult.retrievedFacts.size} facts"
                                ragSources = ragManager?.formatRAGSources(ragResult.retrievedFacts)
                                ragConfidence =
                                    ragManager?.calculateRAGConfidence(ragResult.retrievedFacts)

                                logger.i {
                                    "RAG: Intent=${ragResult.intent.category} (${(ragResult.confidence * 100).toInt()}%), " +
                                            "Facts=${ragResult.retrievedFacts.size}, AvgSim=${
                                                "%.2f".format(
                                                    avgSimilarity
                                                )
                                            }, " +
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

                            // DEBUG: Log Llamatik three-parameter structure (INFO level for visibility)
                            logger.i {
                                "=== RAG DEBUG: Final Context ===" +
                                        "\n  Query: '$prompt'" +
                                        "\n  Intent: ${ragResult.intent.category} (${(ragResult.confidence * 100).toInt()}%)" +
                                        "\n  RAG Applied: ${ragResult.ragApplied}" +
                                        "\n  Facts Retrieved: ${ragResult.retrievedFacts.size}" +
                                        "\n  Context Length: ${contextString.length} chars" +
                                        "\n  Context Preview: ${
                                            contextString.replace("\n", "\\n")
                                        }..." +
                                        "\n  SystemPrompt Length: ${enhancedSystemPrompt.length} chars"
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

                                // DEBUG: Log raw token from Llamatik
                                logger.d { "RAW TOKEN: '$token' (${token.length} chars)" }

                                // Clean token using optimized regex helper (8x faster than sequential .replace())
                                val cleanedToken =
                                    cleanStreamingToken(token, isStartOfGeneration = !hasContent)

                                // DEBUG: Log cleaned token
                                if (cleanedToken != token) {
                                    logger.d { "CLEANED TOKEN: '$cleanedToken' (was: '$token')" }
                                }

                                // Skip empty tokens (whitespace-only at start already filtered by helper)
                                if (cleanedToken.isEmpty()) {
                                    logger.d { "SKIPPED empty token" }
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
                            }.onSuccess {
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
                                        append(
                                            "⚡ $tokenCount tokens in ${totalTime}ms (${
                                                "%.1f".format(
                                                    tokensPerSec
                                                )
                                            } tok/s)"
                                        )
                                        if (ragInfo.isNotEmpty()) {
                                            append(" • $ragInfo")
                                        }
                                    }

                                // Convert StringBuilder to final String for persistence
                                val finalText = streamedText.toString().ifEmpty { "..." }

                                // DEBUG: Log final generation result
                                logger.i {
                                    "GENERATION COMPLETE: tokenCount=$tokenCount, finalText.length=${finalText.length}, text='${
                                        finalText.take(
                                            100
                                        )
                                    }...'"
                                }

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
                                val hasTemplateTokens =
                                    finalText.contains("<|") || finalText.contains("|>")
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
                            }.onFailure { e ->
                                // Log error for debugging
                                logger.e(e) { "Generation failed: ${e.javaClass.simpleName}" }

                                // Show error on avatar
                                avatarVM.showError("Generation failed")

                                // Error haptic feedback
                                haptics.error()

                                // Create user-friendly error message
                                val errorMessage = when {
                                    e.message?.contains(
                                        "out of memory",
                                        ignoreCase = true
                                    ) == true ->
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
                            }

                            // Cleanup after generation (success or failure)
                            isGenerating = false
                            // Return to idle after a delay
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                avatarVM.returnToIdle()
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
                                )
                                .border(
                                    width = borderWidth,
                                    color = borderColor,
                                    shape = RoundedCornerShape(28.dp),
                                )
                                .padding(start = 20.dp, end = 60.dp, top = 16.dp, bottom = 16.dp),
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
                        )
                        .clickable(
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
                    val arrowColor =
                        if (hasText && enabled) MaColors.White else MaColors.TextDisabled
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
                        end = Offset(
                            centerX - arrowLength / 3,
                            centerY - arrowLength / 2 + arrowLength / 3
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )

                    // Draw arrow head (right line)
                    drawLine(
                        color = arrowColor,
                        start = Offset(centerX, centerY - arrowLength / 2),
                        end = Offset(
                            centerX + arrowLength / 3,
                            centerY - arrowLength / 2 + arrowLength / 3
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
