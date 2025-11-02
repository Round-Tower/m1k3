package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.ai.SmolLM2Engine
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.knowledge.KnowledgeRetrievalService
import app.m1k3.ai.assistant.knowledge.PromptEnhancer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * 間 AI - Chat Screen
 *
 * Beautiful chat interface with live AI responses.
 * 100% local inference on your Pixel 6 Pro!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    aiEngine: SmolLM2Engine,
    database: MaDatabase
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var engineInitialized by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Initialize knowledge retrieval service
    val retrievalService = remember { KnowledgeRetrievalService(database) }

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
                    Column {
                        Text(
                            "間 AI Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (engineInitialized) "🟢 AI Ready (100% Local)" else "🔄 Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (engineInitialized) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("← Back", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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

                        val prompt = inputText
                        inputText = ""
                        isGenerating = true

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
                                listState.animateScrollToItem(messages.size - 1)

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
                                        if (tokenCount % 3 == 0) {  // Scroll every 3 tokens to reduce UI updates
                                            listState.animateScrollToItem(messages.size - 1)
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

                                // Final scroll
                                listState.animateScrollToItem(messages.size - 1)

                            } catch (e: Exception) {
                                messages = messages + ChatMessage(
                                    text = "Error: ${e.message}",
                                    isUser = false,
                                    timestamp = Clock.System.now().toEpochMilliseconds(),
                                    isError = true
                                )
                            } finally {
                                isGenerating = false
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                message.inferenceStats?.let { stats ->
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask 間 AI anything...") },
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            FilledTonalButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank()
            ) {
                Text("➤", style = MaterialTheme.typography.headlineSmall)
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
