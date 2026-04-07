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
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * ThinkingPill — collapsible pill showing the model's reasoning process.
 *
 * Two states:
 * - **Thinking in progress**: pulsing "Thinking..." with live content preview
 * - **Complete**: "Thought for Xs" — tap to expand and read the full reasoning
 *
 * Inspired by how extended thinking should feel: transparent, explorable,
 * never intrusive.
 */
@Composable
fun ThinkingPill(
    thinkingContent: String?,
    isThinking: Boolean,
    thinkingDurationMs: Long?,
    modifier: Modifier = Modifier
) {
    if (thinkingContent.isNullOrEmpty() && !isThinking) return

    var expanded by remember { mutableStateOf(false) }

    // Pulse animation for the "Thinking..." state
    val pulse = rememberInfiniteTransition(label = "think-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "think-alpha"
    )

    val headerAlpha = if (isThinking) pulseAlpha else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaColors.bgElevated())
            .clickable(
                enabled = !isThinking && !thinkingContent.isNullOrEmpty(),
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
                text = "🧠",
                fontSize = 14.sp,
                modifier = Modifier.alpha(headerAlpha)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isThinking) "Thinking…"
                       else thinkingDurationMs?.let { ms ->
                           val secs = ms / 1000f
                           "Thought for ${"%.1f".format(secs)}s"
                       } ?: "Thought",
                style = MaTypography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontStyle = if (isThinking) FontStyle.Italic else FontStyle.Normal
                ),
                color = MaColors.textMuted().copy(alpha = headerAlpha),
                modifier = Modifier.weight(1f)
            )
            if (!isThinking && !thinkingContent.isNullOrEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaColors.textDisabled(),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expanded content
        AnimatedVisibility(
            visible = expanded || isThinking,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(MaSpacing.xs))
                Text(
                    text = thinkingContent ?: "",
                    style = MaTypography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaColors.textMuted(),
                    lineHeight = 18.sp,
                    modifier = Modifier.alpha(0.8f)
                )
            }
        }
    }
}
