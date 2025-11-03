package app.m1k3.ai.assistant.avatar

import kotlin.math.PI
import kotlin.math.sin

/**
 * 間 AI Procedural Animator
 *
 * Generates code-based animations for static 3D models (no skeleton/bones).
 * Used for simple models like the mask avatar that don't have baked animations.
 *
 * Features:
 * - **Rotation** - Emotion-based spin speed (Happy=fast, Sad=slow)
 * - **Scale Pulse** - Activity-based breathing/speaking effect
 * - **Color Tint** - Emotion-based color overlays (if material API available)
 *
 * Example:
 * ```kotlin
 * val animator = ProceduralAnimator(avatarState)
 * onFrame = { frameTime ->
 *     val rotationY = animator.getRotationY(elapsedSeconds)
 *     val scale = animator.getScale(elapsedSeconds)
 *     node.rotation = Rotation(0f, rotationY, 0f)
 *     node.scale = Scale(scale, scale, scale)
 * }
 * ```
 *
 * Architecture:
 * - **Time-based** - All effects calculated from elapsed time
 * - **Stateless** - No internal state, pure functions
 * - **Emotion-aware** - Effects adapt to AvatarState
 * - **Activity-aware** - Intensity varies with activity
 */
class ProceduralAnimator(
    private val state: AvatarState
) {

    /**
     * Get Y-axis rotation angle (degrees) based on emotion
     *
     * Rotation speed varies by emotion to convey personality:
     * - **Happy/Excited** - Fast spin (45-90°/sec) - energetic
     * - **Neutral** - Medium spin (30°/sec) - calm
     * - **Sad/Sleepy** - Slow spin (10-15°/sec) - lethargic
     * - **Angry** - Fast jittery spin (60°/sec) - agitated
     *
     * @param elapsedSeconds Time since animation started
     * @return Rotation angle in degrees (0-360, wraps)
     */
    fun getRotationY(elapsedSeconds: Float): Float {
        val degreesPerSecond = getRotationSpeed()
        val angle = (elapsedSeconds * degreesPerSecond) % 360f
        return angle
    }

    /**
     * Get rotation speed in degrees per second
     *
     * Maps emotions to rotation speeds for personality expression.
     */
    fun getRotationSpeed(): Float {
        return when (state.emotion) {
            AvatarEmotion.HAPPY -> 45f        // 8s per rotation
            AvatarEmotion.EXCITED -> 90f      // 4s per rotation
            AvatarEmotion.SAD -> 10f          // 36s per rotation
            AvatarEmotion.SLEEPY -> 5f        // 72s per rotation (very slow)
            AvatarEmotion.ANGRY -> 60f        // 6s per rotation (agitated)
            AvatarEmotion.SURPRISED -> 75f    // 4.8s per rotation
            AvatarEmotion.LOVE -> 30f         // 12s per rotation (gentle)
            AvatarEmotion.THINKING -> 20f     // 18s per rotation (slow)
            AvatarEmotion.NEUTRAL -> 30f      // 12s per rotation
        }
    }

    /**
     * Get scale multiplier for breathing/speaking effect
     *
     * Creates pulsing effect that varies by activity:
     * - **SPEAKING** - Large pulse (±8%) - animated speaking
     * - **GENERATING** - Medium pulse (±5%) - thinking hard
     * - **THINKING** - Small pulse (±3%) - light breathing
     * - **IDLE** - Tiny pulse (±2%) - calm breathing
     * - **ERROR** - Fast pulse (±10%) - alert state
     *
     * Pulse frequency: 2 seconds per cycle (0.5 Hz)
     *
     * @param elapsedSeconds Time since animation started
     * @return Scale multiplier (0.9-1.1 typically)
     */
    fun getScale(elapsedSeconds: Float): Float {
        val amplitude = getScaleAmplitude()
        val frequency = getScaleFrequency()

        // Sine wave oscillation: 1.0 ± amplitude
        val phase = elapsedSeconds * frequency * 2f * PI.toFloat()
        val oscillation = sin(phase) * amplitude

        return 1.0f + oscillation
    }

    /**
     * Get scale pulse amplitude based on activity
     *
     * Higher activity = larger pulses for visibility.
     */
    fun getScaleAmplitude(): Float {
        return when (state.activity) {
            AvatarActivity.SPEAKING -> 0.08f    // 8% pulse
            AvatarActivity.GENERATING -> 0.05f  // 5% pulse
            AvatarActivity.THINKING -> 0.03f    // 3% pulse
            AvatarActivity.LISTENING -> 0.02f   // 2% pulse
            AvatarActivity.ERROR -> 0.10f       // 10% pulse (alert!)
            AvatarActivity.IDLE -> 0.02f        // 2% breathing
        }
    }

    /**
     * Get scale pulse frequency in Hz
     *
     * Faster activities = faster pulse rate.
     */
    fun getScaleFrequency(): Float {
        return when (state.activity) {
            AvatarActivity.SPEAKING -> 0.6f     // 1.67s per cycle
            AvatarActivity.GENERATING -> 0.5f   // 2s per cycle
            AvatarActivity.ERROR -> 0.8f        // 1.25s per cycle (rapid)
            else -> 0.4f                        // 2.5s per cycle (calm)
        }
    }

    /**
     * Get color tint for emotion-based material overlay
     *
     * Returns RGBA float array for material parameter setting.
     * Alpha controls tint strength (0.0 = no tint, 1.0 = full tint).
     *
     * Color mapping:
     * - **Happy** - Warm orange (#FF8C00, 0.3 alpha)
     * - **Sad** - Cool blue (#4169E1, 0.3 alpha)
     * - **Angry** - Red (#DC143C, 0.4 alpha)
     * - **Excited** - Yellow (#FFD700, 0.3 alpha)
     * - **Love** - Pink (#FF69B4, 0.3 alpha)
     * - **Thinking** - Purple (#9370DB, 0.25 alpha)
     * - **Sleepy** - Dark blue (#191970, 0.4 alpha)
     * - **Surprised** - Cyan (#00CED1, 0.3 alpha)
     * - **Neutral** - No tint (white, 0.0 alpha)
     *
     * @return RGBA color array [r, g, b, a] in range 0.0-1.0
     */
    fun getColorTint(): FloatArray {
        return when (state.emotion) {
            AvatarEmotion.HAPPY -> floatArrayOf(1.0f, 0.55f, 0.0f, 0.3f)      // Orange
            AvatarEmotion.SAD -> floatArrayOf(0.25f, 0.41f, 0.88f, 0.3f)      // Royal Blue
            AvatarEmotion.ANGRY -> floatArrayOf(0.86f, 0.08f, 0.24f, 0.4f)    // Crimson
            AvatarEmotion.EXCITED -> floatArrayOf(1.0f, 0.84f, 0.0f, 0.3f)    // Gold
            AvatarEmotion.LOVE -> floatArrayOf(1.0f, 0.41f, 0.71f, 0.3f)      // Hot Pink
            AvatarEmotion.THINKING -> floatArrayOf(0.58f, 0.44f, 0.86f, 0.25f) // Medium Purple
            AvatarEmotion.SLEEPY -> floatArrayOf(0.10f, 0.10f, 0.44f, 0.4f)   // Midnight Blue
            AvatarEmotion.SURPRISED -> floatArrayOf(0.0f, 0.81f, 0.82f, 0.3f) // Dark Turquoise
            AvatarEmotion.NEUTRAL -> floatArrayOf(1.0f, 1.0f, 1.0f, 0.0f)     // No tint
        }
    }

    /**
     * Get color tint with activity override
     *
     * ERROR activity always shows red, regardless of emotion.
     *
     * @return RGBA color array [r, g, b, a]
     */
    fun getColorTintWithActivity(): FloatArray {
        return if (state.activity == AvatarActivity.ERROR) {
            floatArrayOf(1.0f, 0.0f, 0.0f, 0.5f)  // Bright red for errors
        } else {
            getColorTint()
        }
    }

    /**
     * Get rotation axis for variety
     *
     * Most emotions rotate on Y-axis (vertical spin).
     * Some emotions add subtle wobble on other axes.
     *
     * @return Triple of (X-axis, Y-axis, Z-axis) rotation speeds in °/sec
     */
    fun getRotationAxis(): Triple<Float, Float, Float> {
        return when (state.emotion) {
            AvatarEmotion.SURPRISED -> Triple(5f, getRotationSpeed(), 0f)  // Slight head shake
            AvatarEmotion.ANGRY -> Triple(0f, getRotationSpeed(), 3f)      // Slight roll
            else -> Triple(0f, getRotationSpeed(), 0f)                     // Pure Y-axis spin
        }
    }

    companion object {
        /**
         * Check if model needs procedural animation
         *
         * Static models (no animations) use procedural animator.
         * Animated models use animation state machine instead.
         *
         * @param modelConfig Model configuration
         * @return True if should use ProceduralAnimator
         */
        fun shouldUseProcedural(modelConfig: ModelConfig): Boolean {
            return !modelConfig.hasAnimations ||
                   modelConfig.modelType == ModelType.STATIC
        }

        /**
         * Default rotation speed for neutral state
         */
        const val DEFAULT_ROTATION_SPEED = 30f  // degrees per second

        /**
         * Default scale pulse amplitude for idle state
         */
        const val DEFAULT_SCALE_AMPLITUDE = 0.02f  // ±2%

        /**
         * Default scale pulse frequency
         */
        const val DEFAULT_SCALE_FREQUENCY = 0.4f  // Hz (2.5s per cycle)
    }
}

/**
 * Model type enum for avatar system
 *
 * Determines how model is rendered and animated.
 */
enum class ModelType {
    /**
     * Static model (no skeleton/animations)
     *
     * Examples: Mask, geometric shapes, props
     * Rendering: Procedural animations only
     */
    STATIC,

    /**
     * Animated model (has skeleton + baked animations)
     *
     * Examples: Quirky Series animals (Colobus, Sparrow, etc.)
     * Rendering: Full animation state machine
     */
    ANIMATED
}

/**
 * Usage Example:
 * ```kotlin
 * @Composable
 * fun RenderStaticModel(state: AvatarState, modelNode: ModelNode) {
 *     val animator = remember(state.emotion, state.activity) {
 *         ProceduralAnimator(state)
 *     }
 *
 *     var startTime by remember { mutableLongStateOf(System.nanoTime()) }
 *
 *     Scene(
 *         onFrame = { frameTimeNanos ->
 *             val elapsedSeconds = (frameTimeNanos - startTime) / 1_000_000_000.0f
 *
 *             // Apply rotation
 *             val rotationY = animator.getRotationY(elapsedSeconds)
 *             modelNode.rotation = Rotation(0f, rotationY, 0f)
 *
 *             // Apply scale pulse
 *             val scale = animator.getScale(elapsedSeconds)
 *             modelNode.scale = Scale(scale, scale, scale)
 *
 *             // Apply color tint (if material API available)
 *             val colorTint = animator.getColorTintWithActivity()
 *             modelNode.materialInstance?.setParameter("baseColorTint", colorTint)
 *         }
 *     )
 * }
 * ```
 */
