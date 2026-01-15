package app.m1k3.ai.assistant.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.design.effects.glassmorphic
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * M1K3 Bubble Components
 *
 * Message bubbles for chat conversations:
 * - MaChatBubbleUser: Orange-accented user messages (right-aligned)
 * - MaChatBubbleAI: Glassmorphic AI messages (left-aligned)
 *
 * Features:
 * - Streaming text support (updates during generation)
 * - Inference statistics display for AI messages
 * - Error state styling
 * - Timestamp display
 * - AMOLED-optimized colors
 */

/**
 * User message bubble
 *
 * Styled with M1K3 orange accent border and dark background.
 * Right-aligned for conversational clarity.
 *
 * @param text Message content
 * @param timestamp Unix timestamp (milliseconds)
 * @param modifier Optional modifier
 */
@Composable
fun MaChatBubbleUser(
    text: String,
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaSpacing.base, vertical = MaSpacing.sm),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(MaRadius.md))
                    .background(MaColors.BgTertiary)
                    .border(
                        width = 1.5.dp,
                        color = MaColors.Orange,
                        shape = RoundedCornerShape(MaRadius.md)
                    )
                    .padding(horizontal = MaSpacing.md, vertical = MaSpacing.base)
            ) {
                Text(
                    text = text,
                    style = MaTypography.bodyLarge,
                    color = MaColors.TextPrimary
                )
            }

            // Timestamp
            Text(
                text = formatTimestamp(timestamp),
                style = MaTypography.labelSmall,
                color = MaColors.TextDisabled,
                modifier = Modifier.padding(top = MaSpacing.xs, end = MaSpacing.sm)
            )
        }
    }
}

/**
 * AI message bubble
 *
 * Styled with glassmorphic liquid glass aesthetic.
 * Left-aligned with optional inference stats display.
 *
 * @param text Message content (supports streaming updates)
 * @param timestamp Unix timestamp (milliseconds)
 * @param inferenceStats Optional stats string (e.g., "42 tok/s • 256 tokens • 6.1s")
 * @param isError Whether message is an error (shows red accent)
 * @param ragSources Optional RAG sources (Phase 3)
 * @param modifier Optional modifier
 */
@Composable
fun MaChatBubbleAI(
    text: String,
    timestamp: Long,
    inferenceStats: String? = null,
    isError: Boolean = false,
    ragSources: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .animateContentSize()
            .fillMaxWidth()
            .padding(horizontal = MaSpacing.base, vertical = MaSpacing.sm),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .glassmorphic(
                        backgroundColor = if (isError) MaColors.ErrorBg else MaColors.BgElevated,
                        borderColor = if (isError) MaColors.Error else MaColors.BorderLight,
                        borderWidth = 0.dp,
                        shape = RoundedCornerShape(MaRadius.md)
                    )
                    .padding(horizontal = MaSpacing.md, vertical = MaSpacing.base)
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                ) {
                    Text(
                        // Defense-in-depth: trim leading whitespace for non-streaming sources
                        // (e.g., database retrieval, error messages, manual text entry)
                        // Note: Streaming text is already cleaned in ChatScreen.kt:cleanStreamingToken()
                        text = text.trimStart(),
                        style = MaTypography.bodyLarge,
                        color = if (isError) MaColors.Error else MaColors.TextPrimary
                    )

                    // Inference statistics (if available)
                    if (inferenceStats != null) {
                        Text(
                            text = inferenceStats,
                            style = MaTypography.labelSmall,
                            color = MaColors.TextDisabled,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // RAG status indicator with collapsible sources
                    if (ragSources != null) {
                        var isExpanded by remember { mutableStateOf(false) }
                        val factCount = ragSources.lines().count { it.isNotBlank() }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MaSpacing.sm)
                        ) {
                            // RAG badge - clickable to expand sources
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MaRadius.sm))
                                    .background(MaColors.Orange.copy(alpha = 0.15f))
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(horizontal = MaSpacing.sm, vertical = MaSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📚",
                                    style = MaTypography.labelSmall
                                )
                                Text(
                                    text = "RAG Enhanced",
                                    style = MaTypography.labelSmall,
                                    color = MaColors.Orange,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "• $factCount ${if (factCount == 1) "fact" else "facts"}",
                                    style = MaTypography.labelSmall,
                                    color = MaColors.TextSecondary
                                )
                                Text(
                                    text = if (isExpanded) "▲" else "▼",
                                    style = MaTypography.labelSmall,
                                    color = MaColors.TextDisabled
                                )
                            }

                            // Expandable sources list
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Text(
                                    text = ragSources,
                                    style = MaTypography.labelSmall,
                                    color = MaColors.TextSecondary,
                                    modifier = Modifier.padding(
                                        start = MaSpacing.sm,
                                        top = MaSpacing.xs,
                                        end = MaSpacing.sm
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Timestamp
            Text(
                text = formatTimestamp(timestamp),
                style = MaTypography.labelSmall,
                color = MaColors.TextDisabled,
                modifier = Modifier.padding(top = MaSpacing.xs, start = MaSpacing.sm)
            )
        }
    }
}

/**
 * Format Unix timestamp to "HH:MM" format
 *
 * @param timestamp Unix timestamp in milliseconds
 * @return Formatted time string (e.g., "14:32")
 */
@OptIn(ExperimentalTime::class)
private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')

    return "$hour:$minute"
}

/**
 * Usage Examples:
 * ```kotlin
 * // User message
 * MaChatBubbleUser(
 *     text = "What's the weather like?",
 *     timestamp = Clock.System.now().toEpochMilliseconds()
 * )
 *
 * // AI message
 * MaChatBubbleAI(
 *     text = "Based on current conditions...",
 *     timestamp = Clock.System.now().toEpochMilliseconds(),
 *     inferenceStats = "42 tok/s • 128 tokens • 3.0s"
 * )
 *
 * // Error message
 * MaChatBubbleAI(
 *     text = "⚠️ Model initialization failed",
 *     timestamp = Clock.System.now().toEpochMilliseconds(),
 *     isError = true
 * )
 *
 * // Streaming (updates in real-time)
 * var streamingText by remember { mutableStateOf("The sky is") }
 * MaChatBubbleAI(
 *     text = streamingText,  // Updates: "The sky is" → "The sky is blue"
 *     timestamp = Clock.System.now().toEpochMilliseconds()
 * )
 * ```
 */

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun MaChatBubbleUserPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            MaChatBubbleUser(
                text = PreviewFixtures.sampleShortText,
                timestamp = PreviewFixtures.sampleUserMessageTimestamp
            )
        }
    }
}

@Preview
@Composable
private fun MaChatBubbleUserLongTextPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            MaChatBubbleUser(
                text = PreviewFixtures.sampleLongText,
                timestamp = PreviewFixtures.sampleUserMessageTimestamp
            )
        }
    }
}

@Preview
@Composable
private fun MaChatBubbleAIPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            MaChatBubbleAI(
                text = "Hi there! How can I help you today?",
                timestamp = PreviewFixtures.sampleAiMessageTimestamp
            )
        }
    }
}

@Preview
@Composable
private fun MaChatBubbleAIWithStatsPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            MaChatBubbleAI(
                text = "Machine learning is a subset of artificial intelligence where systems learn and improve from experience without being explicitly programmed.",
                timestamp = PreviewFixtures.sampleAiMessageTimestamp,
                inferenceStats = PreviewFixtures.sampleInferenceStatsLong
            )
        }
    }
}

@Preview
@Composable
private fun MaChatBubbleAIWithRagPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            MaChatBubbleAI(
                text = "Based on the knowledge base, machine learning algorithms can be categorized into supervised, unsupervised, and reinforcement learning.",
                timestamp = PreviewFixtures.sampleAiMessageTimestamp,
                inferenceStats = PreviewFixtures.sampleInferenceStatsMedium,
                ragSources = PreviewFixtures.sampleRagSourceMultiple
            )
        }
    }
}

@Preview
@Composable
private fun MaChatBubbleAIErrorPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            MaChatBubbleAI(
                text = "⚠️ Model initialization failed. Please try again.",
                timestamp = PreviewFixtures.sampleAiMessageTimestamp,
                isError = true
            )
        }
    }
}

@Preview
@Composable
private fun MaChatBubbleConversationPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
        ) {
            Text(
                "Sample Conversation:",
                style = MaTypography.labelSmall,
                color = MaColors.TextSecondary,
                modifier = Modifier.padding(bottom = MaSpacing.sm)
            )

            MaChatBubbleUser(
                text = "Hello! What is machine learning?",
                timestamp = PreviewFixtures.sampleUserMessageTimestamp
            )

            MaChatBubbleAI(
                text = "Machine learning is a subset of artificial intelligence where systems learn and improve from experience without being explicitly programmed.",
                timestamp = PreviewFixtures.sampleAiMessageTimestamp,
                inferenceStats = PreviewFixtures.sampleInferenceStatsLong,
                ragSources = PreviewFixtures.sampleRagSourceSingle
            )

            MaChatBubbleUser(
                text = "Tell me more!",
                timestamp = PreviewFixtures.sampleUserMessageTimestamp + 2000
            )

            MaChatBubbleAI(
                text = "There are three main types: supervised learning (with labeled data), unsupervised learning (finding patterns), and reinforcement learning (reward-based).",
                timestamp = PreviewFixtures.sampleAiMessageTimestamp + 2000,
                inferenceStats = PreviewFixtures.sampleInferenceStatsLong
            )
        }
    }
}
