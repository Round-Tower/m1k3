package app.m1k3.ai.assistant.avatar

/**
 * 間 AI Avatar 3D Animation Engine
 *
 * Dynamic animation selection for ANY GLB model.
 * Delegates to AnimationIntrospector for intelligent fuzzy matching
 * against whatever animations the model actually has.
 *
 * No hardcoded model assumptions — works with Omabuarts, Quaternius,
 * or any future model set.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.9 | context: removed legacy ColobusAnimations,
 * all animation selection now goes through AnimationIntrospector fuzzy matching
 */
object Avatar3DEngine {

    /**
     * Global animation speed scale factor
     *
     * All skeleton animations are multiplied by this value.
     * - 1.0f = normal (authored) speed
     * - 0.5f = half speed (calmer, more graceful)
     *
     * Applied in Avatar3DView.onFrame on top of intensity-based speed.
     */
    const val ANIMATION_SPEED_SCALE = 0.35f

    /**
     * Get animation for current avatar state
     *
     * Universal animation mapper for any model.
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
     * Maps intensity 0.0–1.0 to speed 0.5x–1.5x.
     * Combined with ANIMATION_SPEED_SCALE in the render loop.
     *
     * @param intensity Avatar intensity (0.0-1.0)
     * @return Speed multiplier (0.5-1.5)
     */
    fun getAnimationSpeed(intensity: Float): Float {
        return 0.5f + intensity
    }
}
