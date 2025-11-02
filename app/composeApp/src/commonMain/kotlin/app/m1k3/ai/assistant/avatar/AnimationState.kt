package app.m1k3.ai.assistant.avatar

/**
 * 間 AI Animation State Machine
 *
 * Manages 3D avatar animation lifecycle for lifelike behavior.
 *
 * State Flow:
 * ```
 * USER_DIRECTED (user taps emotion)
 *       ↓ non-loopable completes
 * AUTO_IDLE (emotion-appropriate idle)
 *       ↓ 10-15s timer
 * IDLE_VARIANT (random idle for liveliness)
 *       ↓ 2s playback
 * AUTO_IDLE (return to base)
 * ```
 *
 * Features:
 * - Auto-return to idle after non-loopable animations (Death, Hit, Jump)
 * - Idle variation cycling every 10-15s to prevent monotony
 * - User interruption support (tap emotion mid-animation)
 * - Emotion-aware idle selection (Happy→energetic, Sad→calm)
 */

/**
 * Animation playback state machine
 *
 * Defines the current animation lifecycle state.
 *
 * Priority: USER_DIRECTED > AUTO_IDLE > IDLE_VARIANT
 */
enum class AnimationPlaybackState {
    /**
     * User-directed animation (highest priority)
     *
     * Triggered by: User tapping emotion/activity button
     * Duration: Until completion (non-loopable) or user changes state
     * Next State: AUTO_IDLE (if non-loopable completes)
     *
     * Examples: Death, Hit, Jump, Bounce (user-initiated)
     */
    USER_DIRECTED,

    /**
     * Automatic idle animation (medium priority)
     *
     * Triggered by: USER_DIRECTED completes, or initial state
     * Duration: Until user action or idle timeout (10-15s)
     * Next State: IDLE_VARIANT (after timeout), USER_DIRECTED (user action)
     *
     * Examples: Idle_C (neutral), Idle_B (happy), Sit (sad)
     */
    AUTO_IDLE,

    /**
     * Idle variation for liveliness (low priority)
     *
     * Triggered by: AUTO_IDLE timeout (10-15s)
     * Duration: ~2 seconds
     * Next State: AUTO_IDLE (return to base)
     *
     * Examples: Idle_A, Idle_B, Idle_C (cycled randomly)
     */
    IDLE_VARIANT
}

/**
 * Animation transition trigger
 *
 * Defines what caused an animation state change.
 */
enum class AnimationTrigger {
    /** User tapped emotion/activity button */
    USER_ACTION,

    /** Non-loopable animation reached end (e.g., Death finished) */
    COMPLETION,

    /** Idle timeout reached (10-15s) - time for variation */
    IDLE_TIMEOUT,

    /** User action interrupted current animation */
    INTERRUPTION,

    /** System initialization */
    INIT
}

/**
 * Complete animation playback state
 *
 * Tracks everything needed for lifelike animation management.
 *
 * @property currentAnimIndex Animation index in metadata.animations
 * @property currentAnimName Animation name (e.g., "Idle_C", "Death")
 * @property playbackState Current lifecycle state
 * @property startTime System.nanoTime() when animation started
 * @property trigger What caused this animation change
 * @property previousAnimName Previous animation (for debugging)
 */
data class AnimationPlaybackInfo(
    val currentAnimIndex: Int,
    val currentAnimName: String,
    val playbackState: AnimationPlaybackState,
    val startTime: Long,
    val trigger: AnimationTrigger = AnimationTrigger.INIT,
    val previousAnimName: String? = null
) {
    /**
     * Elapsed time since animation started (seconds)
     */
    fun getElapsedSeconds(currentTimeNanos: Long): Float {
        return if (startTime > 0L) {
            (currentTimeNanos - startTime) / 1_000_000_000.0f
        } else {
            0f
        }
    }

    /**
     * Check if animation has completed
     *
     * @param currentTimeNanos Current frame time (from onFrame)
     * @param animDuration Animation duration (from metadata)
     * @param isLoopable Whether animation loops
     * @return True if non-loopable animation finished
     */
    fun isComplete(
        currentTimeNanos: Long,
        animDuration: Float,
        isLoopable: Boolean
    ): Boolean {
        if (isLoopable) return false  // Looping animations never complete

        val elapsed = getElapsedSeconds(currentTimeNanos)
        return elapsed >= animDuration
    }
}

/**
 * Animation state machine helper
 *
 * Utility functions for state transitions and idle selection.
 */
object AnimationStateManager {

    /**
     * Should auto-return to idle?
     *
     * Returns true if non-loopable animation completed and should transition.
     *
     * @param playbackInfo Current playback state
     * @param currentTimeNanos Frame time from onFrame
     * @param animMetadata Animation metadata
     * @return True if should transition to AUTO_IDLE
     */
    fun shouldAutoReturnToIdle(
        playbackInfo: AnimationPlaybackInfo,
        currentTimeNanos: Long,
        animMetadata: AnimationMetadata
    ): Boolean {
        return playbackInfo.playbackState == AnimationPlaybackState.USER_DIRECTED &&
                playbackInfo.isComplete(
                    currentTimeNanos = currentTimeNanos,
                    animDuration = animMetadata.duration,
                    isLoopable = animMetadata.isLoopable
                )
    }

    /**
     * Get emotion-appropriate idle animation
     *
     * Selects best idle based on current emotion:
     * - HAPPY → Idle_B (energetic)
     * - SAD/SLEEPY → Idle_A or Sit (calm)
     * - EXCITED → Idle_B or Bounce
     * - NEUTRAL → Idle_C
     * - Others → Matched via keywords
     *
     * @param emotion Current avatar emotion
     * @param availableAnimations All animations from metadata
     * @return Best matching idle animation
     */
    fun getEmotionAppropriateIdle(
        emotion: AvatarEmotion,
        availableAnimations: List<AnimationMetadata>
    ): AnimationMetadata {
        val idlePreferences = when (emotion) {
            AvatarEmotion.HAPPY -> listOf("idle_b", "bounce", "idle_c")
            AvatarEmotion.SAD -> listOf("sit", "idle_a")
            AvatarEmotion.EXCITED -> listOf("bounce", "idle_b", "idle_c")
            AvatarEmotion.SLEEPY -> listOf("sit", "idle_a")
            AvatarEmotion.ANGRY -> listOf("idle_a", "idle_c")
            AvatarEmotion.SURPRISED -> listOf("idle_c", "idle_b")
            AvatarEmotion.LOVE -> listOf("eat", "idle_b", "idle_c")
            AvatarEmotion.THINKING -> listOf("idle_c", "idle_a")
            AvatarEmotion.NEUTRAL -> listOf("idle_c", "idle_a", "idle_b")
        }

        // Find first matching idle
        for (keyword in idlePreferences) {
            val match = availableAnimations.find {
                it.name.contains(keyword, ignoreCase = true) &&
                        it.isLoopable  // Only select loopable idles
            }
            if (match != null) return match
        }

        // Fallback: first idle animation
        return availableAnimations.firstOrNull { it.isIdle }
            ?: availableAnimations.first()
    }

    /**
     * Get random idle variant (different from current)
     *
     * For liveliness system - picks random idle that isn't currently playing.
     *
     * @param currentAnimName Current animation name
     * @param availableAnimations All animations from metadata
     * @return Random idle animation (or null if no alternatives)
     */
    fun getRandomIdleVariant(
        currentAnimName: String,
        availableAnimations: List<AnimationMetadata>
    ): AnimationMetadata? {
        val idleAnimations = AnimationIntrospector.getIdleAnimations(availableAnimations)

        return idleAnimations
            .filter { it.name != currentAnimName && it.isLoopable }
            .randomOrNull()
    }
}

/**
 * Extension: Check if animation is idle
 */
val AnimationMetadata.isIdle: Boolean
    get() = name.contains("idle", ignoreCase = true) ||
            name.contains("sit", ignoreCase = true)

/**
 * Usage Examples:
 * ```kotlin
 * // Create initial playback state
 * var playbackInfo by remember {
 *     mutableStateOf(
 *         AnimationPlaybackInfo(
 *             currentAnimIndex = 0,
 *             currentAnimName = "Idle_C",
 *             playbackState = AnimationPlaybackState.AUTO_IDLE,
 *             startTime = System.nanoTime(),
 *             trigger = AnimationTrigger.INIT
 *         )
 *     )
 * }
 *
 * // Check for completion in onFrame
 * onFrame = { frameTimeNanos ->
 *     val currentAnim = metadata.animations[playbackInfo.currentAnimIndex]
 *
 *     if (AnimationStateManager.shouldAutoReturnToIdle(
 *         playbackInfo, frameTimeNanos, currentAnim
 *     )) {
 *         // Transition to auto idle
 *         val idleAnim = AnimationStateManager.getEmotionAppropriateIdle(
 *             emotion = state.emotion,
 *             availableAnimations = metadata.animations
 *         )
 *
 *         playbackInfo = AnimationPlaybackInfo(
 *             currentAnimIndex = idleAnim.index,
 *             currentAnimName = idleAnim.name,
 *             playbackState = AnimationPlaybackState.AUTO_IDLE,
 *             startTime = frameTimeNanos,
 *             trigger = AnimationTrigger.COMPLETION,
 *             previousAnimName = playbackInfo.currentAnimName
 *         )
 *     }
 * }
 *
 * // Idle variation system
 * LaunchedEffect(playbackInfo.playbackState) {
 *     while (playbackInfo.playbackState == AnimationPlaybackState.AUTO_IDLE) {
 *         delay((10_000..15_000).random().toLong())
 *
 *         if (playbackInfo.playbackState == AnimationPlaybackState.AUTO_IDLE) {
 *             val variant = AnimationStateManager.getRandomIdleVariant(
 *                 playbackInfo.currentAnimName,
 *                 metadata.animations
 *             )
 *
 *             if (variant != null) {
 *                 playbackInfo = playbackInfo.copy(
 *                     currentAnimIndex = variant.index,
 *                     currentAnimName = variant.name,
 *                     playbackState = AnimationPlaybackState.IDLE_VARIANT,
 *                     startTime = System.nanoTime(),
 *                     trigger = AnimationTrigger.IDLE_TIMEOUT
 *                 )
 *
 *                 // Return to AUTO_IDLE after 2s
 *                 delay(2000)
 *                 if (playbackInfo.playbackState == AnimationPlaybackState.IDLE_VARIANT) {
 *                     playbackInfo = playbackInfo.copy(
 *                         playbackState = AnimationPlaybackState.AUTO_IDLE
 *                     )
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
