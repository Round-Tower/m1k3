package app.m1k3.ai.assistant.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import app.m1k3.ai.assistant.design.tokens.MaDurations
import kotlinx.coroutines.delay

/**
 * 間 AI Avatar Animation System
 *
 * Provides smooth transitions between emotions, activities, and visual states.
 * Uses Compose Animation APIs for fluid, performant animations.
 */

/**
 * Animated avatar state manager
 *
 * Handles smooth transitions between states with interpolation.
 * Updates animationProgress automatically for continuous animations.
 *
 * @return Animated state with smooth transitions
 */
@Composable
fun rememberAnimatedAvatarState(
    targetState: AvatarState,
    transitionDuration: Long = MaDurations.normal.toLong()
): AvatarState {
    val animatedIntensity by animateFloatAsState(
        targetValue = targetState.intensity,
        animationSpec = tween(durationMillis = transitionDuration.toInt()),
        label = "intensity"
    )

    // Continuous animation progress for breathing/idle effects
    val infiniteTransition = rememberInfiniteTransition(label = "avatarLoop")
    val loopProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loopProgress"
    )

    return remember(targetState, animatedIntensity, loopProgress) {
        targetState.copy(
            intensity = animatedIntensity,
            animationProgress = if (targetState.isAnimating) loopProgress else 0f
        )
    }
}

/**
 * Emotion transition animator
 *
 * Provides smooth color and geometry transitions when emotions change.
 *
 * @param targetEmotion Target emotion to transition to
 * @param duration Transition duration in milliseconds
 * @return Current transitioning state
 */
@Composable
fun rememberEmotionTransition(
    targetEmotion: AvatarEmotion,
    duration: Long = 300
): AvatarEmotion {
    var currentEmotion by remember { mutableStateOf(targetEmotion) }

    LaunchedEffect(targetEmotion) {
        if (currentEmotion != targetEmotion) {
            delay(duration)
            currentEmotion = targetEmotion
        }
    }

    return currentEmotion
}

/**
 * Activity-based animation controller
 *
 * Automatically animates avatar based on current activity:
 * - IDLE: Slow breathing effect
 * - LISTENING: Gentle pulse
 * - THINKING: Rotating/analyzing motion
 * - GENERATING: Fast pulse
 * - SPEAKING: Speech animation
 * - ERROR: Alert shake
 *
 * @param activity Current avatar activity
 * @return Animation parameters for the activity
 */
@Composable
fun rememberActivityAnimation(
    activity: AvatarActivity
): ActivityAnimationState {
    val infiniteTransition = rememberInfiniteTransition(label = "activityAnimation")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (activity) {
            AvatarActivity.GENERATING -> 1.08f
            AvatarActivity.LISTENING -> 1.03f
            AvatarActivity.THINKING -> 1.05f
            else -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (activity) {
                    AvatarActivity.GENERATING -> 400
                    AvatarActivity.LISTENING -> 1200
                    AvatarActivity.THINKING -> 800
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (activity == AvatarActivity.THINKING) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (activity == AvatarActivity.ERROR) 10f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeOffset"
    )

    return ActivityAnimationState(
        scale = pulseScale,
        rotation = rotation,
        offsetX = if (activity == AvatarActivity.ERROR) shakeOffset else 0f,
        offsetY = 0f
    )
}

/**
 * Activity animation state
 */
data class ActivityAnimationState(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

/**
 * Entrance animation for avatar
 *
 * Plays once when avatar first appears.
 *
 * @param onComplete Callback when animation completes
 * @return Animation progress (0f to 1f)
 */
@Composable
fun rememberEntranceAnimation(
    onComplete: () -> Unit = {}
): Float {
    var hasPlayed by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (hasPlayed) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        ),
        finishedListener = { onComplete() },
        label = "entrance"
    )

    LaunchedEffect(Unit) {
        hasPlayed = true
    }

    return progress
}

/**
 * Glow pulse animation
 *
 * Creates pulsing glow effect for avatar antenna bulb.
 *
 * @param isActive Whether glow should pulse
 * @return Glow intensity (0f to 1f)
 */
@Composable
fun rememberGlowPulse(isActive: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")

    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive) 1f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    return if (isActive) glowIntensity else 0.3f
}

/**
 * Bounce animation
 *
 * Creates bouncing effect for avatar during active states.
 *
 * @param isActive Whether avatar should bounce
 * @return Vertical offset in pixels
 */
@Composable
fun rememberBounceAnimation(isActive: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActive) -12f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceOffset"
    )

    return if (isActive) bounceOffset else 0f
}

/**
 * Speech wave animation
 *
 * Creates wave-like motion for avatar during speech/speaking activity.
 *
 * @param isSpeaking Whether avatar is speaking
 * @return Wave parameters (amplitude, frequency)
 */
@Composable
fun rememberSpeechWaveAnimation(isSpeaking: Boolean): SpeechWaveState {
    val infiniteTransition = rememberInfiniteTransition(label = "speechWave")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isSpeaking) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    return SpeechWaveState(
        amplitude = if (isSpeaking) 8f else 0f,
        frequency = 2f,
        phase = phase
    )
}

data class SpeechWaveState(
    val amplitude: Float = 0f,
    val frequency: Float = 1f,
    val phase: Float = 0f
)

/**
 * Usage Examples:
 * ```kotlin
 * @Composable
 * fun AvatarDisplay() {
 *     // Basic animated state
 *     val targetState = AvatarState(
 *         emotion = AvatarEmotion.HAPPY,
 *         activity = AvatarActivity.SPEAKING
 *     )
 *     val animatedState = rememberAnimatedAvatarState(targetState)
 *
 *     // Activity-based animation
 *     val activityAnim = rememberActivityAnimation(AvatarActivity.THINKING)
 *
 *     // Entrance animation
 *     val entranceProgress = rememberEntranceAnimation()
 *
 *     Canvas(modifier = Modifier.size(280.dp).scale(activityAnim.scale)) {
 *         with(AvatarEngine) {
 *             drawRobotAvatar(state = animatedState)
 *         }
 *     }
 * }
 * ```
 */
