package app.m1k3.ai.assistant.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.chat.ToolExecutionResult
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * ToolCallPill — collapsible pill showing tool execution results.
 *
 * Two states:
 * - **Executing**: pulsing "Calling tools..." animation
 * - **Complete**: "N tools used" — tap to expand and see individual results
 *
 * Each tool result shows its ID and output, with success/failure indicators.
 * Follows the same pattern as ThinkingPill for visual consistency.
 */
@Composable
fun ToolCallPill(
    toolResults: List<ToolExecutionResult>,
    isExecuting: Boolean,
    modifier: Modifier = Modifier
) {
    if (toolResults.isEmpty() && !isExecuting) return

    var expanded by remember { mutableStateOf(false) }

    // Pulse animation for the "Calling tools..." state
    val pulse = rememberInfiniteTransition(label = "tool-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "tool-alpha"
    )

    val headerAlpha = if (isExecuting) pulseAlpha else 1f
    val successCount = toolResults.count { it.isSuccess }
    val failureCount = toolResults.count { !it.isSuccess }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaColors.bgElevated())
            .clickable(
                enabled = !isExecuting && toolResults.isNotEmpty(),
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { expanded = !expanded }
            .padding(horizontal = MaSpacing.md, vertical = MaSpacing.sm)
            .animateContentSize()
    ) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isExecuting) "\uD83D\uDD27" else if (failureCount > 0) "\u26A0\uFE0F" else "\u2705",
                fontSize = 14.sp,
                modifier = Modifier.alpha(headerAlpha)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    isExecuting -> "Calling tools\u2026"
                    toolResults.size == 1 -> formatToolName(toolResults[0].toolId)
                    else -> "${toolResults.size} tools used"
                },
                style = MaTypography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontStyle = if (isExecuting) FontStyle.Italic else FontStyle.Normal
                ),
                color = MaColors.textMuted().copy(alpha = headerAlpha),
                modifier = Modifier.weight(1f)
            )
            if (!isExecuting && toolResults.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaColors.textDisabled(),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expanded content — individual tool results
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(MaSpacing.xs))
                toolResults.forEach { result ->
                    ToolResultRow(result)
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

/**
 * Single tool result row — shows tool name + truncated output.
 */
@Composable
private fun ToolResultRow(result: ToolExecutionResult) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = if (result.isSuccess) "\u2022" else "\u2717",
            fontSize = 11.sp,
            color = if (result.isSuccess) MaColors.Orange else MaColors.Error,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatToolName(result.toolId),
                style = MaTypography.labelSmall.copy(fontSize = 11.sp),
                color = MaColors.textSecondary()
            )
            Text(
                text = result.displayResult.take(120),
                style = MaTypography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic
                ),
                color = MaColors.textMuted(),
                maxLines = 2,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Format tool ID into a readable name.
 * "get_battery" → "Get Battery"
 * "web_search" → "Web Search"
 */
internal fun formatToolName(toolId: String): String =
    toolId.split("_").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
