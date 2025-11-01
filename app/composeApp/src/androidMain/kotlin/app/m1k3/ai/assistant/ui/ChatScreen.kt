package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.ai.SmolLM2Engine
import kotlinx.coroutines.launch
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
    aiEngine: SmolLM2Engine
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var engineInitialized by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Initialize AI engine on first load
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                aiEngine.initialize()
                engineInitialized = true

                // Add welcome message
                messages = messages + ChatMessage(
                    text = "Hello! I'm 間 AI, running 100% locally on your Pixel 6 Pro. " +
                            "I'm powered by SmolLM2-360M and I respect your privacy - " +
                            "zero network transmission. Ask me anything!",
                    isUser = false,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
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
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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
                                val result = aiEngine.generate(prompt, maxTokens = 128)

                                val aiMessage = ChatMessage(
                                    text = result.text,
                                    isUser = false,
                                    timestamp = Clock.System.now().toEpochMilliseconds(),
                                    inferenceStats = "⚡ ${result.tokensGenerated} tokens in ${result.inferenceTimeMs}ms"
                                )
                                messages = messages + aiMessage

                                // Auto-scroll to bottom
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

            FilledIconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
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
