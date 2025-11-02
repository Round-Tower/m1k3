package app.m1k3.ai.assistant.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarEngine.drawRobotAvatar
import app.m1k3.ai.assistant.design.effects.glassmorphic
import app.m1k3.ai.assistant.design.tokens.MaRadius

/**
 * 間 AI Mini Avatar Indicator
 *
 * Compact 80dp avatar for headers, toolbars, and floating indicators.
 * Features:
 * - Minimal space footprint
 * - Emotion-based color glow
 * - Activity pulse animation
 * - Clickable for expanding to full avatar view
 */

/**
 * Mini avatar indicator (80dp)
 *
 * Compact avatar perfect for top bars and corners.
 *
 * @param state Current avatar state
 * @param modifier Optional modifier
 * @param onClick Optional click handler (e.g., to expand to full view)
 * @param showGlow Whether to show emotion-based glow effect
 */
@Composable
fun MiniAvatarIndicator(
    state: AvatarState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showGlow: Boolean = true
) {
    val animatedState = rememberAnimatedAvatarState(state, transitionDuration = 200)
    val activityAnim = rememberActivityAnimation(state.activity)
    val glowPulse = rememberGlowPulse(state.isAnimating)

    Box(
        modifier = modifier
            .defaultMinSize(48.dp, 48.dp)  // Minimum size, but respects external size modifier
            .scale(activityAnim.scale)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .glassmorphic(shape = CircleShape)
            .graphicsLayer {
                translationX = activityAnim.offsetX
                translationY = activityAnim.offsetY
                rotationZ = activityAnim.rotation
            },
        contentAlignment = Alignment.Center
    ) {
        // Glow effect (if enabled)
        if (showGlow && state.isAnimating) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()  // Fill parent size instead of hardcoded 80dp
                    .graphicsLayer { alpha = glowPulse * 0.5f }
            ) {
                drawCircle(
                    color = state.displayColor.copy(alpha = 0.3f),
                    radius = size.minDimension / 2
                )
            }
        }

        // Avatar canvas - uses 75% of parent size for padding
        Canvas(modifier = Modifier.fillMaxSize(0.75f)) {
            drawRobotAvatar(
                state = animatedState,
                geometry = RobotGeometry(
                    headSize = size.minDimension * 0.9f,
                    headCornerRadius = 8f,
                    eyeWidth = size.width * 0.27f,  // Proportional to canvas size
                    eyeHeight = 5f,
                    eyeSpacing = size.width * 0.4f,  // Proportional to canvas size
                    antennaHeight = size.height * 0.2f,  // Proportional to canvas size
                    antennaWidth = 3f,
                    antennaBulbRadius = 4f,
                    mouthWidth = size.width * 0.4f,  // Proportional to canvas size
                    mouthHeight = 3f
                )
            )
        }

        // Activity indicator dot (top-right corner)
        if (state.activity != AvatarActivity.IDLE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = when (state.activity) {
                            AvatarActivity.LISTENING -> androidx.compose.ui.graphics.Color.Blue
                            AvatarActivity.THINKING -> androidx.compose.ui.graphics.Color.Magenta
                            AvatarActivity.GENERATING -> androidx.compose.ui.graphics.Color(0xFFE25303) // M1K3 Orange
                            AvatarActivity.SPEAKING -> androidx.compose.ui.graphics.Color.Green
                            AvatarActivity.ERROR -> androidx.compose.ui.graphics.Color.Red
                            else -> androidx.compose.ui.graphics.Color.Gray
                        }
                    )
                }
            }
        }
    }
}

/**
 * Floating mini avatar (with positioning)
 *
 * Avatar that can float in a corner with automatic positioning.
 *
 * @param state Current avatar state
 * @param position Corner position (TopStart, TopEnd, BottomStart, BottomEnd)
 * @param onClick Optional click handler
 * @param modifier Optional modifier (applied to container Box)
 */
@Composable
fun FloatingMiniAvatar(
    state: AvatarState,
    position: FloatingPosition = FloatingPosition.TopEnd,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MiniAvatarIndicator(
            state = state,
            onClick = onClick,
            modifier = Modifier
                .align(position.alignment)
                .padding(16.dp)
        )
    }
}

enum class FloatingPosition(val alignment: Alignment) {
    TopStart(Alignment.TopStart),
    TopEnd(Alignment.TopEnd),
    BottomStart(Alignment.BottomStart),
    BottomEnd(Alignment.BottomEnd)
}

/**
 * Mini avatar with badge
 *
 * Shows notification count or status badge.
 *
 * @param state Current avatar state
 * @param badgeCount Number to display in badge (0 = no badge)
 * @param modifier Optional modifier
 * @param onClick Optional click handler
 */
@Composable
fun MiniAvatarWithBadge(
    state: AvatarState,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        MiniAvatarIndicator(
            state = state,
            onClick = onClick
        )

        // Badge (if count > 0)
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .glassmorphic(shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    style = app.m1k3.ai.assistant.design.tokens.MaTypography.labelSmall,
                    color = app.m1k3.ai.assistant.design.tokens.MaColors.White
                )
            }
        }
    }
}

/**
 * Mini avatar row (multiple avatars in a row)
 *
 * Useful for showing multiple AI states or conversation participants.
 *
 * @param states List of avatar states to display
 * @param modifier Optional modifier
 * @param maxVisible Maximum number of avatars to show (rest collapsed)
 * @param onClick Optional click handler (receives avatar index)
 */
@Composable
fun MiniAvatarRow(
    states: List<AvatarState>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
    onClick: ((Int) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-16).dp) // Overlapping
    ) {
        states.take(maxVisible).forEachIndexed { index, state ->
            MiniAvatarIndicator(
                state = state,
                onClick = if (onClick != null) {{ onClick(index) }} else null,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { translationX = index * -8f }
            )
        }

        // "+N more" indicator
        if (states.size > maxVisible) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .glassmorphic(shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = "+${states.size - maxVisible}",
                    style = app.m1k3.ai.assistant.design.tokens.MaTypography.labelMedium,
                    color = app.m1k3.ai.assistant.design.tokens.MaColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * @Composable
 * fun TopBar() {
 *     val avatarVM = rememberAvatarViewModel()
 *     val state by avatarVM.collectAsState()
 *
 *     Row(
 *         modifier = Modifier.fillMaxWidth().padding(16.dp),
 *         horizontalArrangement = Arrangement.SpaceBetween
 *     ) {
 *         Text("間 AI", style = MaterialTheme.typography.titleLarge)
 *
 *         // Mini avatar in top bar
 *         MiniAvatarIndicator(
 *             state = state,
 *             onClick = { /* Expand to full avatar view */ }
 *         )
 *     }
 * }
 *
 * @Composable
 * fun ChatInterface() {
 *     val avatarVM = rememberAvatarViewModel()
 *     val state by avatarVM.collectAsState()
 *
 *     Box(modifier = Modifier.fillMaxSize()) {
 *         // Main chat content
 *         ChatMessages()
 *
 *         // Floating avatar in corner
 *         FloatingMiniAvatar(
 *             state = state,
 *             position = FloatingPosition.BottomEnd,
 *             onClick = { /* Show avatar settings */ }
 *         )
 *     }
 * }
 *
 * @Composable
 * fun NotificationBadge() {
 *     val state = AvatarState(emotion = AvatarEmotion.EXCITED)
 *
 *     MiniAvatarWithBadge(
 *         state = state,
 *         badgeCount = 5,  // 5 unread messages
 *         onClick = { /* Open messages */ }
 *     )
 * }
 * ```
 */
