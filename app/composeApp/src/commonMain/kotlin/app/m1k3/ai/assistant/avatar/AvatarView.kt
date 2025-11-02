package app.m1k3.ai.assistant.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarEngine.drawRobotAvatar
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * 間 AI Avatar View
 *
 * Main avatar display component (280dp).
 * Integrates all avatar systems:
 * - Canvas rendering (AvatarEngine)
 * - Emotion detection
 * - Activity animations
 * - State management (AvatarViewModel)
 */

/**
 * Full-size avatar display with emotion and activity indicators
 *
 * @param state Current avatar state (emotion, activity, intensity)
 * @param modifier Optional modifier
 * @param showInfo Whether to show emotion/activity labels
 * @param onClick Optional click handler for interactive demos
 */
@Composable
fun AvatarView(
    state: AvatarState,
    modifier: Modifier = Modifier,
    showInfo: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    // Animate state transitions
    val animatedState = rememberAnimatedAvatarState(
        targetState = state,
        transitionDuration = 300
    )

    // Activity-based animations
    val activityAnim = rememberActivityAnimation(state.activity)

    // Entrance animation (plays once on mount)
    val entranceProgress = rememberEntranceAnimation()

    // Bounce animation for active states
    val bounceOffset = rememberBounceAnimation(state.isAnimating)

    MaCard(
        modifier = modifier
            .size(280.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaSpacing.base),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar canvas
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(activityAnim.scale * entranceProgress)
                    .graphicsLayer {
                        rotationZ = activityAnim.rotation
                        translationX = activityAnim.offsetX
                        translationY = activityAnim.offsetY + bounceOffset
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRobotAvatar(
                        state = animatedState,
                        geometry = RobotGeometry(),
                        animation = AvatarAnimation()
                    )
                }
            }

            if (showInfo) {
                Spacer(modifier = Modifier.height(MaSpacing.base))

                // Emotion label
                Text(
                    text = "${state.emotion.emoji} ${state.emotion.displayName}",
                    style = MaTypography.titleMedium,
                    color = state.displayColor,
                    fontWeight = FontWeight.Bold
                )

                // Activity label
                if (state.activity != AvatarActivity.IDLE) {
                    Text(
                        text = state.activity.displayName,
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                }

                // Status message
                if (state.message != null) {
                    Spacer(modifier = Modifier.height(MaSpacing.xs))
                    Text(
                        text = state.message,
                        style = MaTypography.labelSmall,
                        color = MaColors.TextDisabled
                    )
                }

                // Intensity indicator
                Text(
                    text = "Intensity: ${(state.intensity * 100).toInt()}%",
                    style = MaTypography.labelSmall,
                    color = MaColors.TextDisabled
                )
            }
        }
    }
}

/**
 * Compact avatar display (200dp)
 *
 * Smaller version without labels, for headers/toolbars.
 *
 * @param state Current avatar state
 * @param modifier Optional modifier
 */
@Composable
fun AvatarViewCompact(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    val animatedState = rememberAnimatedAvatarState(state)
    val activityAnim = rememberActivityAnimation(state.activity)

    Box(
        modifier = modifier
            .size(200.dp)
            .scale(activityAnim.scale)
            .graphicsLayer {
                rotationZ = activityAnim.rotation
                translationX = activityAnim.offsetX
                translationY = activityAnim.offsetY
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRobotAvatar(
                state = animatedState,
                geometry = RobotGeometry(),
                animation = AvatarAnimation()
            )
        }
    }
}

/**
 * Avatar emotion selector
 *
 * Interactive grid for testing/selecting emotions.
 *
 * @param onEmotionSelected Callback when emotion is selected
 * @param modifier Optional modifier
 */
@Composable
fun AvatarEmotionSelector(
    onEmotionSelected: (AvatarEmotion) -> Unit,
    modifier: Modifier = Modifier
) {
    val emotions = remember {
        listOf(
            AvatarEmotion.HAPPY,
            AvatarEmotion.SAD,
            AvatarEmotion.ANGRY,
            AvatarEmotion.SURPRISED,
            AvatarEmotion.LOVE,
            AvatarEmotion.THINKING,
            AvatarEmotion.SLEEPY,
            AvatarEmotion.EXCITED,
            AvatarEmotion.NEUTRAL
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
    ) {
        Text(
            text = "Avatar Emotions",
            style = MaTypography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaColors.TextPrimary
        )

        // Grid of emotion buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
        ) {
            emotions.chunked(3).forEach { row ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                ) {
                    row.forEach { emotion ->
                        MaCard(
                            onClick = { onEmotionSelected(emotion) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaSpacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = emotion.emoji,
                                    style = MaTypography.headlineSmall
                                )
                                Text(
                                    text = emotion.displayName,
                                    style = MaTypography.labelSmall,
                                    color = emotion.primaryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Avatar activity indicator
 *
 * Shows current AI activity with animation.
 *
 * @param activity Current activity
 * @param modifier Optional modifier
 */
@Composable
fun AvatarActivityIndicator(
    activity: AvatarActivity,
    modifier: Modifier = Modifier
) {
    val activityAnim = rememberActivityAnimation(activity)

    Row(
        modifier = modifier
            .scale(activityAnim.scale)
            .padding(horizontal = MaSpacing.base, vertical = MaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Activity indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer {
                    alpha = if (activity.isActive) 1f else 0.3f
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = when (activity) {
                        AvatarActivity.LISTENING -> androidx.compose.ui.graphics.Color.Blue
                        AvatarActivity.THINKING -> androidx.compose.ui.graphics.Color.Magenta
                        AvatarActivity.GENERATING -> MaColors.Orange
                        AvatarActivity.SPEAKING -> androidx.compose.ui.graphics.Color.Green
                        AvatarActivity.ERROR -> MaColors.Error
                        AvatarActivity.IDLE -> MaColors.TextDisabled
                    }
                )
            }
        }

        Text(
            text = activity.displayName,
            style = MaTypography.bodySmall,
            color = if (activity.isActive) MaColors.TextPrimary else MaColors.TextDisabled,
            fontWeight = if (activity.isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * @Composable
 * fun AvatarDemo() {
 *     val viewModel = rememberAvatarViewModel()
 *     val state by viewModel.collectAsState()
 *
 *     Column {
 *         // Main avatar display
 *         AvatarView(
 *             state = state,
 *             showInfo = true,
 *             onClick = { viewModel.flashEmotion(AvatarEmotion.EXCITED) }
 *         )
 *
 *         // Emotion selector
 *         AvatarEmotionSelector(
 *             onEmotionSelected = { viewModel.setEmotion(it, 0.8f) }
 *         )
 *
 *         // Activity indicator
 *         AvatarActivityIndicator(activity = state.activity)
 *     }
 * }
 *
 * @Composable
 * fun ChatScreenWithAvatar() {
 *     val viewModel = rememberAvatarViewModel()
 *
 *     // Sync with AI
 *     LaunchedEffect(isGenerating) {
 *         viewModel.syncWithAI(isGenerating)
 *     }
 *
 *     // Compact avatar in header
 *     AvatarViewCompact(
 *         state = viewModel.avatarState.collectAsState().value,
 *         modifier = Modifier.size(80.dp)
 *     )
 * }
 * ```
 */
