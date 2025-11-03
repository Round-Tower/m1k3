package app.m1k3.ai.assistant.design.tokens

/**
 * M1K3 AI Animation Duration System
 *
 * Consistent timing for animations and transitions.
 * Creates fluid, intentional motion without feeling sluggish.
 *
 * Philosophy: Fast enough to feel responsive, slow enough to perceive
 */
object MaDurations {
    /** 100ms - Instant feedback (hover, ripple start) */
    const val instant = 100

    /** 150ms - Fast transitions (focus, simple state changes) */
    const val fast = 150

    /** 250ms - Standard transitions (most animations, fades, slides) */
    const val normal = 250

    /** 300ms - Avatar emotion transitions */
    const val emotion = 300

    /** 400ms - Slower transitions (major state changes, glow pulses) */
    const val slow = 400

    /** 600ms - Complex animations (entrance, exit) */
    const val complex = 600

    /** 1500ms - Glow pulse loop */
    const val glow = 1500

    /** 2000ms - Avatar idle breathing loop */
    const val breathing = 2000
}

/**
 * Duration Usage Guidelines
 *
 * instant (100ms):  Hover effects, ripple starts, immediate feedback
 * fast (150ms):     Focus rings, simple state toggles
 * normal (250ms):   Fades, slides, most component animations (DEFAULT)
 * emotion (300ms):  Avatar emotion crossfades
 * slow (400ms):     Major state changes, glow pulses
 * complex (600ms):  Screen transitions, entrance/exit animations
 * glow (1500ms):    Orange glow pulse on buttons/avatar
 * breathing (2000ms): Avatar idle breathing animation
 *
 * Example Usage:
 * ```kotlin
 * val alpha by animateFloatAsState(
 *     targetValue = if (visible) 1f else 0f,
 *     animationSpec = tween(durationMillis = MaDurations.normal)
 * )
 * ```
 */
