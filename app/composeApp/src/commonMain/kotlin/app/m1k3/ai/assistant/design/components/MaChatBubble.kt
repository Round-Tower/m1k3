package app.m1k3.ai.assistant.design.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.effects.glassmorphic
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
 * @param modifier Optional modifier
 */
@Composable
fun MaChatBubbleAI(
    text: String,
    timestamp: Long,
    inferenceStats: String? = null,
    isError: Boolean = false,
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
                        borderWidth = 1.dp,
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
                        text = text,
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
