package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.chat.ContextWindowState
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * ContextWindowIndicator - Displays context window usage in chat.
 *
 * Shows:
 * - Number of conversation messages in context
 * - Estimated token usage as progress bar
 * - Device tier for transparency
 *
 * Tapping expands to show more detail.
 */
@Composable
fun ContextWindowIndicator(
    state: ContextWindowState,
    modifier: Modifier = Modifier
) {
    // Don't show if no history
    if (state.historyMessageCount == 0 && state.historyTokens == 0) {
        return
    }

    var expanded by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = (state.usagePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "context_progress"
    )

    val progressColor = when {
        state.usagePercent < 50 -> MaterialTheme.colorScheme.primary
        state.usagePercent < 80 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .testTag("context_window_indicator")
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(12.dp)
    ) {
        Column {
            // Compact view (always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Context icon and message count
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.historyMessageCount} msgs in context",
                        style = MaTypography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp
                    )
                }

                // Right: Token usage badge
                Text(
                    text = "${state.usagePercent.toInt()}%",
                    style = MaTypography.labelMedium,
                    color = progressColor
                )
            }

            // Progress bar
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Token count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tokens",
                        style = MaTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.formatUsage(),
                        style = MaTypography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Device tier
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Device",
                        style = MaTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.deviceTier,
                        style = MaTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Help text
                Text(
                    text = "Tap to collapse • Context includes recent conversation history",
                    style = MaTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
private fun ContextWindowIndicatorLowUsagePreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            ContextWindowIndicator(
                state = ContextWindowState(
                    historyMessageCount = 5,
                    historyTokens = 500,
                    maxContextTokens = 2000,
                    deviceTier = "High-end"
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun ContextWindowIndicatorMediumUsagePreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            ContextWindowIndicator(
                state = ContextWindowState(
                    historyMessageCount = 12,
                    historyTokens = 1200,
                    maxContextTokens = 2000,
                    deviceTier = "Mid-range"
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun ContextWindowIndicatorHighUsagePreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            ContextWindowIndicator(
                state = ContextWindowState(
                    historyMessageCount = 20,
                    historyTokens = 1800,
                    maxContextTokens = 2000,
                    deviceTier = "Budget"
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
