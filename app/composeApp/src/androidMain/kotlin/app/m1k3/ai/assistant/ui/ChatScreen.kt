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
import app.m1k3.ai.assistant.embedding.EmbeddingModelManager
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.assistant.ui.components.EcoIndicator
import app.m1k3.ai.assistant.ui.components.EcoIndicatorVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    // Initialize knowledge retrieval service - PHASE1.5: Semantic retrieval with embeddings
    val embeddingEngine =
        remember(context) {
            runBlocking {
                try {
                    val manager = EmbeddingModelManager(context)
                    val engine = manager.getEmbeddingEngine()
                    // Load the model
                    val loadResult = engine.loadModel()
                    if (loadResult.isSuccess) {
                        println("✅ [RAG] Loaded embedding engine: ${engine.modelName}")
                        engine
                    } else {
                        println("⚠️ [RAG] Failed to load embedding model: ${loadResult.exceptionOrNull()?.message}")
                        null
                    }
                } catch (e: Exception) {
                    println("⚠️ [RAG] Failed to initialize embedding engine: ${e.message}")
                    null
                }
            }
        }

    // RAG Manager - Phase 3: Intent-aware knowledge retrieval
    val ragManager =
        remember(database, embeddingEngine) {
            if (embeddingEngine != null) {
                println("✅ [RAG] Using RAGManager with ${embeddingEngine.modelName} embeddings + Intent Classification")
                RAGManager(database, embeddingEngine)
            } else {
                println("⚠️ [RAG] Embedding engine not available, RAG disabled")
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

                // Loading indicator
                if (isGenerating) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("loading_indicator"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
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
                                    println("🗑️ [DEBUG] Manually clearing conversation history")
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

                        // 3D Avatar with activity/emotion feedback (reduced size to prevent clipping)
                        AvatarView(
                            state = avatarState,
                            use3D = true,
                            showInfo = false,
                            modifier = Modifier
                                .testTag("avatar")
                                .size(64.dp) // Reduced from 100.dp to prevent clipping
                                .padding(4.dp)
                                .clip(CircleShape)
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
                                var streamedText = ""
                                var tokenCount = 0
                                var ragInfo = ""
                                var ragSources: String? = null
                                var ragConfidence: Double? = null

                                // Phase 3 RAG: Intent-aware knowledge retrieval with enriched prompt
                                val systemPrompt =
                                    "You are M1K3, a privacy-first AI assistant running 100% locally. " +
                                        "Be helpful, concise, and informative. "
//                                        knowledgeContext

                                // RAG with error handling and quality validation
                                val ragResult =
                                    try {
                                        if (ragManager != null && ragEnabled) {
                                            val result =
                                                ragManager.enrichPrompt(
                                                    userQuery = prompt,
                                                    systemPrompt = systemPrompt,
                                                    enableRAG = true,
                                                )

                                            // Quality validation: Filter out low-quality facts (<0.65 similarity)
                                            if (result.ragApplied) {
                                                val highQualityFacts = result.retrievedFacts.filter { it.similarity >= 0.65f }
                                                if (highQualityFacts.size < result.retrievedFacts.size) {
                                                    println(
                                                        "⚠️ [RAG] Filtered ${result.retrievedFacts.size - highQualityFacts.size} low-quality facts (<0.65 similarity)",
                                                    )
                                                }

                                                // If no high-quality facts remain, fall back to system prompt
                                                if (highQualityFacts.isEmpty()) {
                                                    println("⚠️ [RAG] No high-quality facts found, using base prompt")
                                                    result.copy(
                                                        enrichedPrompt = systemPrompt,
                                                        retrievedFacts = emptyList(),
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
                                                println("⚙️ [RAG] Disabled by user preference")
                                            } else {
                                                println("⚠️ [RAG] Manager not available (embedding engine not initialized)")
                                            }
                                            RAGManager.RAGResult(
                                                enrichedPrompt = systemPrompt,
                                                intent = app.m1k3.ai.assistant.rag.IntentClassifier.Intent.GENERAL,
                                                confidence = 0f,
                                                retrievedFacts = emptyList(),
                                                ragApplied = false,
                                            )
                                        }
                                    } catch (e: Exception) {
                                        println("❌ [RAG] Enrichment failed: ${e.message}")
                                        e.printStackTrace()
                                        // Fall back to system prompt without crashing
                                        RAGManager.RAGResult(
                                            enrichedPrompt = systemPrompt,
                                            intent = app.m1k3.ai.assistant.rag.IntentClassifier.Intent.GENERAL,
                                            confidence = 0f,
                                            retrievedFacts = emptyList(),
                                            ragApplied = false,
                                        )
                                    }

                                // Use RAG-enriched system prompt if RAG was applied
                                // Otherwise use base system prompt
                                val enrichedSystemPrompt = if (ragResult.ragApplied) {
                                    ragResult.enrichedPrompt  // Contains RAG knowledge + base instructions
                                } else {
                                    systemPrompt  // Just base M1K3 instructions
                                }

                                // Track RAG usage for display and database
                                if (ragResult.ragApplied) {
                                    ragInfo =
                                        "${ragResult.intent.category} (${(ragResult.confidence * 100).toInt()}%) • ${ragResult.retrievedFacts.size} facts"
                                    ragSources = ragManager?.formatRAGSources(ragResult.retrievedFacts)
                                    ragConfidence = ragManager?.calculateRAGConfidence(ragResult.retrievedFacts)
                                    println(
                                        "📚 [RAG] Intent: ${ragResult.intent.category} (confidence: ${(ragResult.confidence * 100).toInt()}%)",
                                    )
                                    println("📚 [RAG] Retrieved ${ragResult.retrievedFacts.size} facts")
                                    println("📚 [RAG] Enhanced system prompt length: ${enrichedSystemPrompt.length} chars")
                                } else {
                                    println("📚 [RAG] No retrieval for intent: ${ragResult.intent.category}")
                                }

                                // DEBUG: Log exact prompts being sent to model
                                println("🔍 [PROMPT-DEBUG] System prompt length: ${enrichedSystemPrompt.length} chars")
                                println("🔍 [PROMPT-DEBUG] System prompt preview (first 200 chars): ${enrichedSystemPrompt.take(200)}")
                                println("🔍 [PROMPT-DEBUG] User prompt: \"$prompt\"")
                                println("🔍 [PROMPT-DEBUG] Knowledge context: $knowledgeContext")

                                // Use device-adaptive max tokens based on RAM
                                // 12GB+: 512 tokens, 8-12GB: 384, 6-8GB: 256, 4-6GB: 128, <4GB: 64
                                aiEngine.generateStreaming(
                                    prompt = prompt,  // User's question ONLY (no labels, no duplication)
                                    config = app.m1k3.ai.assistant.ai.GenerationConfig(
                                        systemPrompt = enrichedSystemPrompt,  // System instructions (with RAG if applicable)
                                        maxTokens = aiEngine.getOptimalMaxTokens(), // Device-adaptive
                                        temperature = 0.9f, // INCREASED from 0.5f - Higher temperature reduces repetition loops
                                        // Only pass static KB summary if RAG didn't retrieve anything
                                        knowledgeContext = if (ragResult.ragApplied) null else knowledgeContext
                                    )
                                ) { token ->
                                    // Clean chat template tokens before appending (defense-in-depth)
                                    val cleanedToken = token
                                        .replace("<|im_start|>", "")
                                        .replace("<|im_end|>", "")
                                        .replace("<|endoftext|>", "")
                                        .replace("<|", "")  // Partial fragments
                                        .replace("|>", "")

                                    // Append each cleaned token as it arrives
                                    streamedText += cleanedToken
                                    tokenCount++

                                    // Update the message in real-time
                                    chatViewModel.updateMessage(
                                        aiMessageIndex,
                                        app.m1k3.ai.assistant.chat.ChatMessage(
                                            text = streamedText,
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

                                chatViewModel.updateMessage(
                                    aiMessageIndex,
                                    app.m1k3.ai.assistant.chat.ChatMessage(
                                        text = streamedText.ifEmpty { "..." },
                                        isUser = false,
                                        timestamp = aiMessageTimestamp,
                                        inferenceStats = statsText,
                                        ragSources = ragSources, // Phase 3: Track RAG sources
                                    ),
                                )

                                // Record assistant message with eco-metrics and RAG metadata (Phase 3)
                                chatViewModel.recordMessage(
                                    content = streamedText.ifEmpty { "..." },
                                    role = "assistant",
                                    tokens = tokenCount,
                                    ragSources = ragSources,
                                    ragConfidence = ragConfidence,
                                )

                                // Log complete response for debugging
                                println("✅ [RESPONSE-COMPLETE] Generation finished")
                                println("   📝 Full response: \"$streamedText\"")
                                println("   📊 Stats: ${streamedText.length} chars / $tokenCount tokens")
                                println("   ⚡ Performance: ${"%.1f".format(tokensPerSec)} tok/s in ${totalTime}ms")
                                println("   🔍 Special tokens: ${if (streamedText.contains("<|") || streamedText.contains("|>")) "DETECTED ⚠️" else "clean ✓"}")
                                if (ragInfo.isNotEmpty()) {
                                    println("   📚 RAG: $ragInfo")
                                }

                                // Detect emotion from AI response
                                avatarVM.processMessage(streamedText, isUserMessage = false)
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
                                avatarVM.showError("Generation failed")

                                // Error haptic feedback
                                haptics.error()

                                chatViewModel.addMessage(
                                    app.m1k3.ai.assistant.chat.ChatMessage(
                                        text = "Error: ${e.message}",
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
