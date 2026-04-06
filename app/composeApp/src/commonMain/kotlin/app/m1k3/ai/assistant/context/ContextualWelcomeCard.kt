package app.m1k3.ai.assistant.context

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.domain.context.ContextualGreetingBuilder
import app.m1k3.ai.domain.context.GreetingResult
import app.m1k3.ai.domain.context.UserContext
import kotlinx.coroutines.delay

/**
 * Contextual Welcome Card — replaces MaStatusCard for the opening message.
 *
 * Shows a personal, data-rich greeting built from the user's real context:
 *   "Good morning, Kev"
 *   "Dublin, Ireland"
 *   "7h 20m sleep · 2,847 steps"
 *   "1h 42m screen time today"
 *   "3 notifications waiting"
 *   "Ready when you are."
 *
 * Lines appear with a staggered entrance animation.
 * Missing data = missing line (never shows "null" or empty rows).
 *
 * If no context is available, falls back to a minimal warm greeting.
 */
@Composable
fun ContextualWelcomeCard(
    context: UserContext,
    onRequestLocation: (() -> Unit)? = null,
    onRequestHealth: (() -> Unit)? = null,
    onRequestScreenTime: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val greeting = remember(context) {
        ContextualGreetingBuilder().build(context)
    }

    val shape = RoundedCornerShape(MaRadius.lg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaSpacing.md, vertical = MaSpacing.base)
            .clip(shape)
            .background(MaColors.bgElevated())
            .border(width = 1.dp, color = MaColors.OrangeDim, shape = shape)
            .padding(MaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
    ) {
        // Staggered entrance — each line slides in with delay
        StaggeredLine(index = 0) {
            Text(
                text = greeting.greeting,
                style = MaTypography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaColors.textPrimary()
            )
        }

        greeting.locationLine?.let { line ->
            StaggeredLine(index = 1) {
                ContextLine(text = line, color = MaColors.Orange)
            }
        }

        greeting.healthLine?.let { line ->
            StaggeredLine(index = 2) {
                ContextLine(text = line, color = MaColors.textSecondary())
            }
        }

        greeting.screenTimeLine?.let { line ->
            StaggeredLine(index = 3) {
                ContextLine(text = line, color = MaColors.textSecondary())
            }
        }

        greeting.notificationLine?.let { line ->
            StaggeredLine(index = 4) {
                ContextLine(
                    text = line,
                    color = if (line.startsWith("0")) MaColors.textMuted() else MaColors.textSecondary()
                )
            }
        }

        Spacer(Modifier.height(MaSpacing.xs))

        StaggeredLine(index = 5) {
            Text(
                text = greeting.closingLine,
                style = MaTypography.bodyMedium,
                color = MaColors.textMuted(),
                fontWeight = FontWeight.Normal
            )
        }

        // Progressive permission nudges — only show if data is missing
        // and there's an action to take. Non-intrusive.
        if (!context.hasAnyContext) {
            Spacer(Modifier.height(MaSpacing.sm))
            PermissionNudge(
                text = "Share more to get a personal greeting",
                onTap = onRequestLocation
            )
        } else {
            // Show targeted nudges for missing data sources
            if (context.location == null && onRequestLocation != null) {
                PermissionNudge(
                    text = "+ Add your location",
                    onTap = onRequestLocation
                )
            }
            if (context.health == null && onRequestHealth != null) {
                PermissionNudge(
                    text = "+ Connect Health",
                    onTap = onRequestHealth
                )
            }
            if (context.screenTime == null && onRequestScreenTime != null) {
                PermissionNudge(
                    text = "+ Add screen time",
                    onTap = onRequestScreenTime
                )
            }
        }
    }
}

/**
 * A single context data line.
 */
@Composable
private fun ContextLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaTypography.bodySmall,
        color = color
    )
}

/**
 * Soft permission nudge — looks like a secondary action, not an alert.
 */
@Composable
private fun PermissionNudge(text: String, onTap: (() -> Unit)?) {
    if (onTap == null) return
    Text(
        text = text,
        style = MaTypography.labelSmall,
        color = MaColors.Orange.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onTap)
    )
}

/**
 * Staggered entrance animation — each line slides in with a delay.
 *
 * @param index 0-based index determines delay (80ms per step).
 */
@Composable
private fun StaggeredLine(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it / 3 }
        )
    ) {
        content()
    }
}
