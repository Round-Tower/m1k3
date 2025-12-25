package app.m1k3.ai.assistant.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing

/**
 * Typing indicator bubble showing animated dots while AI is generating a response.
 *
 * Displays three animated dots with sequential pulsing animation to indicate
 * that the AI is "typing" or generating a response.
 *
 * Design:
 * - Appears in chat as a left-aligned bubble (AI side)
 * - Three dots pulse sequentially with smooth animation
 * - Uses Ma design system colors and spacing
 * - Consistent with MaChatBubble visual style
 *
 * @param modifier Optional modifier for the bubble
 */
@Composable
fun TypingIndicatorBubble(
    modifier: Modifier = Modifier
) {
    // Create infinite animation for the dots
    val infiniteTransition = rememberInfiniteTransition()

    // Animate each dot with sequential delay
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // AI message bubble (left-aligned)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MaSpacing.md, end = MaSpacing.xxl),  // More padding on right (AI bubble left-aligned)
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .wrapContentSize(),
            color = MaColors.BgSecondary,
            shape = RoundedCornerShape(MaRadius.md),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = MaSpacing.lg, vertical = MaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dot 1
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dot1Alpha)
                        .background(
                            color = MaColors.TextSecondary,
                            shape = CircleShape
                        )
                )

                // Dot 2
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dot2Alpha)
                        .background(
                            color = MaColors.TextSecondary,
                            shape = CircleShape
                        )
                )

                // Dot 3
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dot3Alpha)
                        .background(
                            color = MaColors.TextSecondary,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
