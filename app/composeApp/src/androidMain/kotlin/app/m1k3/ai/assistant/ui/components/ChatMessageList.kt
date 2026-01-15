package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.chat.ChatMessage
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import app.m1k3.ai.assistant.ui.ChatBubble
import app.m1k3.ai.assistant.design.components.TypingIndicatorBubble

/**
 * Scrollable list of chat messages with typing indicator.
 *
 * Features:
 * - Lazy loading for performance with large conversations
 * - Typing indicator during AI generation
 * - Proper spacing and padding for overlays (header, input bar)
 * - Content size animation for smooth updates
 *
 * @param messages List of chat messages to display
 * @param isGenerating Whether AI is currently generating a response
 * @param listState LazyList state for scroll control
 * @param showEcoIndicator Whether to show eco stats (affects top padding)
 * @param modifier Optional modifier for customization
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    listState: LazyListState,
    showEcoIndicator: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .testTag("message_list")
            .animateContentSize()
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = if (showEcoIndicator) 180.dp else 120.dp, // Account for toolbar + optional eco indicator
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

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun ChatMessageListEmptyPreview() {
    MaTheme {
        ChatMessageList(
            messages = emptyList(),
            isGenerating = false,
            listState = rememberLazyListState(),
            showEcoIndicator = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview
@Composable
private fun ChatMessageListWithMessagesPreview() {
    MaTheme {
        ChatMessageList(
            messages = listOf(
                ChatMessage(
                    text = "Hello! How are you?",
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                ),
                ChatMessage(
                    text = "I'm doing great! How can I help you?",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
            ),
            isGenerating = false,
            listState = rememberLazyListState(),
            showEcoIndicator = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview
@Composable
private fun ChatMessageListGeneratingPreview() {
    MaTheme {
        ChatMessageList(
            messages = listOf(
                ChatMessage(
                    text = "Tell me about machine learning",
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
            ),
            isGenerating = true,
            listState = rememberLazyListState(),
            showEcoIndicator = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview
@Composable
private fun ChatMessageListWithEcoIndicatorPreview() {
    MaTheme {
        ChatMessageList(
            messages = List(5) { index ->
                ChatMessage(
                    text = "Message $index",
                    isUser = index % 2 == 0,
                    timestamp = System.currentTimeMillis() + (index * 1000)
                )
            },
            isGenerating = false,
            listState = rememberLazyListState(),
            showEcoIndicator = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}
