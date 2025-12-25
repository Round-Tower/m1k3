package app.m1k3.ai.assistant.avatar

/**
 * 間 AI Avatar 3D Animation Engine
 *
 * **UPDATED:** Now supports dynamic animation mapping for ANY model.
 * Uses AnimationIntrospector for intelligent animation selection.
 *
 * Key Changes:
 * - Generic model support (not just Colobus)
 * - Uses ModelMetadata.animations for discovery
 * - Fuzzy matching for emotion → animation mapping
 * - Graceful fallbacks when animations don't exist
 *
 * Legacy Support:
 * - ColobusAnimations constants preserved for backward compatibility
 * - Original getAnimation() method delegates to new system
 */

/**
 * Animation name constants for Colobus monkey model
 *
 * **LEGACY:** Kept for backward compatibility.
 * New code should use AnimationIntrospector.findAnimation() instead.
 */
object ColobusAnimations {
    // Idle animations
    const val IDLE_A = "Idle_A"      // Calm, attentive idle
    const val IDLE_B = "Idle_B"      // Energetic idle
    const val IDLE_C = "Idle_C"      // Neutral idle

    // Emotion animations
    const val FEAR = "Fear"          // Scared, defensive
    const val BOUNCE = "Bounce"      // Excited, playful
    const val DEATH = "Death"        // Error, critical failure
    const val EAT = "Eat"            // Content, satisfied
    const val SIT = "Sit"            // Calm, resting

    // Movement animations
    const val WALK = "Walk"          // Steady progress
    const val RUN = "Run"            // High activity
    const val FLY = "Fly"            // Elevated state
    const val SWIM = "Swim"          // Processing
    const val JUMP = "Jump"          // Excited burst
    const val ROLL = "Roll"          // Playful
    const val SPIN = "Spin"          // Thinking, analyzing

    // Interaction animations
    const val CLICKED = "Clicked"    // Speaking, responding
    const val ATTACK = "Attack"      // Angry reaction
    const val HIT = "Hit"            // Impact, surprise
}

/**
 * Animation metadata
 *
 * **LEGACY:** Use AnimationMetadata instead for new code.
 * Kept for backward compatibility.
 */
data class AnimationInfo(
    val name: String,
    val duration: Float = 1.0f,      // Duration in seconds (estimated)
    val loopable: Boolean = true,     // Whether animation should loop
    val transitionDuration: Float = 0.2f  // Blend time when switching animations
)

/**
 * Convert AnimationMetadata to legacy AnimationInfo
 */
fun AnimationMetadata.toLegacyInfo(): AnimationInfo {
    return AnimationInfo(
        name = name,
        duration = duration,
        loopable = isLoopable,
        transitionDuration = 0.2f
    )
}

/**
 * Avatar 3D Animation Engine
 *
 * Selects appropriate 3D animations based on avatar state.
 *
 * **NEW:** Supports dynamic animation mapping via getAnimation(state, availableAnimations)
 * **LEGACY:** Original getAnimation(state) preserved for backward compatibility
 */
object Avatar3DEngine {

    /**
     * Global animation speed scale factor
     *
     * All animations are multiplied by this value to slow them down.
     * - 1.0f = normal speed
     * - 0.7f = 30% slower (more graceful)
     * - 0.5f = 50% slower (very slow)
     * - 0.4f = 60% slower (relaxed, calm)
     *
     * This is applied in addition to intensity-based speed adjustments.
     */
    const val ANIMATION_SPEED_SCALE = 0.4f

    /**
     * Get animation info for current avatar state (LEGACY)
     *
     * **LEGACY:** This method assumes Colobus animations.
     * Use getAnimation(state, availableAnimations) for generic models.
     *
     * Uses intelligent mapping:
     * 1. Activity takes precedence (GENERATING → Run, THINKING → Spin)
     * 2. Emotion provides fallback (HAPPY → Idle_B, SAD → Sit)
     * 3. Intensity affects animation speed (not animation choice)
     *
     * @param state Current avatar state
     * @return Animation info with name and metadata
     */
    fun getAnimation(state: AvatarState): AnimationInfo {
        // Activity-based animations (highest priority)
        val activityAnimation = when (state.activity) {
            AvatarActivity.LISTENING -> AnimationInfo(
                name = ColobusAnimations.IDLE_A,
                duration = 2.0f,
                loopable = true
            )
            AvatarActivity.THINKING -> AnimationInfo(
                name = ColobusAnimations.SPIN,
                duration = 2.0f,
                loopable = true
            )
            AvatarActivity.GENERATING -> AnimationInfo(
                name = ColobusAnimations.RUN,
                duration = 1.5f,
                loopable = true
            )
            AvatarActivity.SPEAKING -> AnimationInfo(
                name = ColobusAnimations.CLICKED,
                duration = 1.0f,
                loopable = true
            )
            AvatarActivity.ERROR -> AnimationInfo(
                name = ColobusAnimations.DEATH,
                duration = 2.0f,
                loopable = false  // Play once, then hold
            )
            AvatarActivity.IDLE -> null  // Use emotion-based animation
        }

        if (activityAnimation != null) {
            return activityAnimation
        }

        // Emotion-based animations (when activity is IDLE)
        return when (state.emotion) {
            AvatarEmotion.HAPPY -> AnimationInfo(
                name = ColobusAnimations.IDLE_B,  // Energetic idle
                duration = 2.0f,
                loopable = true
            )
            AvatarEmotion.SAD -> AnimationInfo(
                name = ColobusAnimations.SIT,
                duration = 2.5f,
                loopable = true
            )
            AvatarEmotion.ANGRY -> AnimationInfo(
                name = ColobusAnimations.ATTACK,
                duration = 1.5f,
                loopable = true
            )
            AvatarEmotion.SURPRISED -> AnimationInfo(
                name = ColobusAnimations.HIT,  // Recoil reaction
                duration = 1.0f,
                loopable = false  // Play once, then return to idle
            )
            AvatarEmotion.LOVE -> AnimationInfo(
                name = ColobusAnimations.EAT,  // Contentment
                duration = 2.0f,
                loopable = true
            )
            AvatarEmotion.THINKING -> AnimationInfo(
                name = ColobusAnimations.SPIN,
                duration = 2.0f,
                loopable = true
            )
            AvatarEmotion.SLEEPY -> AnimationInfo(
                name = ColobusAnimations.SIT,
                duration = 3.0f,
                loopable = true
            )
            AvatarEmotion.EXCITED -> AnimationInfo(
                name = ColobusAnimations.BOUNCE,
                duration = 1.5f,
                loopable = true
            )
            AvatarEmotion.NEUTRAL -> AnimationInfo(
                name = ColobusAnimations.IDLE_C,  // Neutral idle
                duration = 2.0f,
                loopable = true
            )
        }
    }

    /**
     * Get animation for current avatar state (GENERIC)
     *
     * **NEW:** Universal animation mapper for any model.
     * Uses AnimationIntrospector for intelligent fuzzy matching.
     *
     * @param state Current avatar state
     * @param availableAnimations List of animations from ModelMetadata
     * @return Best matching animation metadata
     */
    fun getAnimation(
        state: AvatarState,
        availableAnimations: List<AnimationMetadata>
    ): AnimationMetadata {
        return AnimationIntrospector.findAnimation(state, availableAnimations)
    }

    /**
     * Get animation playback speed multiplier based on intensity
     *
     * Intensity affects how fast/slow animation plays:
     * - 0.0 → 0.5x speed (very slow)
     * - 0.5 → 1.0x speed (normal)
     * - 1.0 → 1.5x speed (very fast)
     *
     * @param intensity Avatar intensity (0.0-1.0)
     * @return Speed multiplier (0.5-1.5)
     */
    fun getAnimationSpeed(intensity: Float): Float {
        return 0.5f + intensity  // Maps 0.0-1.0 to 0.5-1.5
    }

    /**
     * Get idle animation variant based on time
     *
     * Rotates through idle variants to prevent monotony:
     * - 0-10s: Idle_C (neutral)
     * - 10-20s: Idle_A (calm)
     * - 20-30s: Idle_B (energetic)
     * - 30s+: repeat
     *
     * @param timeInState Seconds in current state
     * @return Idle animation name
     */
    fun getIdleVariant(timeInState: Float): String {
        return when ((timeInState.toInt() / 10) % 3) {
            0 -> ColobusAnimations.IDLE_C
            1 -> ColobusAnimations.IDLE_A
            2 -> ColobusAnimations.IDLE_B
            else -> ColobusAnimations.IDLE_C
        }
    }

    /**
     * Check if animation should blend to new animation
     *
     * Smooth blending for natural transitions:
     * - Same animation → no blend
     * - Non-loopable → wait until complete
     * - Loopable → blend immediately
     *
     * @param currentAnimation Current animation playing
     * @param newAnimation New animation to play
     * @return Whether to start blending
     */
    fun shouldBlend(currentAnimation: AnimationInfo, newAnimation: AnimationInfo): Boolean {
        if (currentAnimation.name == newAnimation.name) return false
        if (!currentAnimation.loopable) return false  // Let non-loopable finish
        return true
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // ===== NEW GENERIC API (Recommended) =====
 *
 * // Load model metadata
 * val metadata = GLBModelLoader.loadMetadata(modelLoader, "models/Sparrow_Animations.glb")
 * val state = AvatarState(
 *     emotion = AvatarEmotion.HAPPY,
 *     activity = AvatarActivity.GENERATING
 * )
 *
 * // Get animation using intelligent fuzzy matching
 * val anim = Avatar3DEngine.getAnimation(state, metadata.animations)
 * // Returns: AnimationMetadata(name="Run", duration=1.5f, isLoopable=true)
 * // Works with ANY model (Colobus, Sparrow, Gecko, etc.)
 *
 * // Play animation
 * modelNode.playAnimation(
 *     animationIndex = anim.index,
 *     loop = anim.isLoopable,
 *     speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
 * )
 *
 * // ===== LEGACY API (Colobus Only) =====
 *
 * // Get animation for Colobus (assumes hardcoded animations)
 * val colobusAnim = Avatar3DEngine.getAnimation(state)
 * // Returns: AnimationInfo(name="Run", duration=1.5f, loopable=true)
 *
 * // Get playback speed from intensity
 * val speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
 * // intensity=0.8 → speed=1.3x
 *
 * // Rotate idle variants to prevent monotony (Colobus-specific)
 * val idleVariant = Avatar3DEngine.getIdleVariant(timeInState = 15f)
 * // Returns: "Idle_A" (second 10-second cycle)
 * ```
 */
