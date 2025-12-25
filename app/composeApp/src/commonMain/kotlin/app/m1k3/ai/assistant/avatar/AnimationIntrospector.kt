package app.m1k3.ai.assistant.avatar

/**
 * 間 AI Animation Introspection System
 *
 * Intelligently maps avatar emotions/activities to model animations
 * using fuzzy matching and semantic patterns.
 *
 * Supports:
 * - Auto-discovery of animations from ModelMetadata
 * - Fuzzy name matching (e.g., "Idle_A" matches "idle")
 * - Pattern-based mapping (emotions → animation keywords)
 * - Fallback strategies when exact matches aren't found
 *
 * Design Philosophy:
 * - Works with any animation naming convention
 * - Graceful degradation (always returns an animation)
 * - Platform-agnostic (commonMain)
 */

/**
 * Animation pattern matching scores
 *
 * Used to rank animation matches when multiple candidates exist.
 */
private enum class MatchScore(val value: Int) {
    EXACT(100),      // Exact name match (e.g., "Happy" == "Happy")
    STRONG(75),      // Strong keyword match (e.g., "Idle_B" for "happy")
    MODERATE(50),    // Moderate keyword match (e.g., "Bounce" for "excited")
    WEAK(25),        // Weak keyword match (e.g., "Walk" for "thinking")
    FALLBACK(10)     // Default fallback (first idle or first animation)
}

/**
 * Animation introspection and intelligent mapping
 */
object AnimationIntrospector {

    /**
     * Emotion → animation keyword mapping
     *
     * Maps AvatarEmotion to keywords for fuzzy matching.
     * Ordered by preference (first = strongest match).
     */
    private val emotionKeywords = mapOf(
        AvatarEmotion.HAPPY to listOf("bounce", "idle_b", "happy", "excited", "jump"),
        AvatarEmotion.SAD to listOf("sit", "idle_a", "sad", "death", "walk"),
        AvatarEmotion.ANGRY to listOf("attack", "angry", "hit", "spin"),
        AvatarEmotion.SURPRISED to listOf("hit", "surprised", "bounce", "clicked"),
        AvatarEmotion.LOVE to listOf("eat", "love", "sit", "idle_b"),
        AvatarEmotion.THINKING to listOf("spin", "thinking", "idle_a", "walk"),
        AvatarEmotion.SLEEPY to listOf("sit", "idle_a", "sleepy", "death"),
        AvatarEmotion.EXCITED to listOf("bounce", "jump", "excited", "run", "fly"),
        AvatarEmotion.NEUTRAL to listOf("idle_c", "idle", "neutral")
    )

    /**
     * Activity → animation keyword mapping
     */
    private val activityKeywords = mapOf(
        AvatarActivity.LISTENING to listOf("idle_a", "idle", "sit"),
        AvatarActivity.THINKING to listOf("spin", "thinking", "idle_a"),
        AvatarActivity.GENERATING to listOf("run", "generating", "walk", "fly"),
        AvatarActivity.SPEAKING to listOf("clicked", "speaking", "bounce", "idle_b"),
        AvatarActivity.ERROR to listOf("death", "error", "hit", "fear"),
        AvatarActivity.IDLE to listOf("idle")  // Use emotion-based mapping
    )

    /**
     * Find best animation for avatar state
     *
     * Main API for animation selection. Uses intelligent matching:
     * 1. Activity takes precedence (if not IDLE)
     * 2. Emotion provides fallback
     * 3. Intensity affects speed (not animation choice)
     *
     * @param state Current avatar state
     * @param availableAnimations List of animations from model
     * @return Best matching animation (never null - returns fallback if needed)
     */
    fun findAnimation(
        state: AvatarState,
        availableAnimations: List<AnimationMetadata>
    ): AnimationMetadata {
        if (availableAnimations.isEmpty()) {
            throw IllegalStateException("Model has no animations")
        }

        // Priority 1: Activity-based animation (if not IDLE)
        if (state.activity != AvatarActivity.IDLE) {
            val activityAnim = findBestMatch(
                keywords = activityKeywords[state.activity] ?: emptyList(),
                availableAnimations = availableAnimations
            )
            if (activityAnim != null) {
                return activityAnim
            }
        }

        // Priority 2: Emotion-based animation
        val emotionAnim = findBestMatch(
            keywords = emotionKeywords[state.emotion] ?: emptyList(),
            availableAnimations = availableAnimations
        )
        if (emotionAnim != null) {
            return emotionAnim
        }

        // Priority 3: Fallback to first idle or first animation
        return availableAnimations.firstOrNull { it.isIdle }
            ?: availableAnimations.first()
    }

    /**
     * Find animation by exact name
     *
     * Case-insensitive exact match.
     *
     * @param name Animation name to find
     * @param availableAnimations List of animations
     * @return Matching animation or null
     */
    fun findByName(
        name: String,
        availableAnimations: List<AnimationMetadata>
    ): AnimationMetadata? {
        return availableAnimations.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
    }

    /**
     * Find animations matching pattern
     *
     * Returns all animations whose name contains the pattern.
     *
     * @param pattern Pattern to search for (case-insensitive)
     * @param availableAnimations List of animations
     * @return List of matching animations (may be empty)
     */
    fun findMatching(
        pattern: String,
        availableAnimations: List<AnimationMetadata>
    ): List<AnimationMetadata> {
        return availableAnimations.filter {
            it.name.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Get all idle animations
     *
     * Useful for rotating between idle variants.
     *
     * @param availableAnimations List of animations
     * @return List of idle animations (may be empty)
     */
    fun getIdleAnimations(
        availableAnimations: List<AnimationMetadata>
    ): List<AnimationMetadata> {
        return availableAnimations.filter { it.isIdle }
    }

    /**
     * Get all movement animations
     *
     * @param availableAnimations List of animations
     * @return List of movement animations (walk, run, fly, etc.)
     */
    fun getMovementAnimations(
        availableAnimations: List<AnimationMetadata>
    ): List<AnimationMetadata> {
        return availableAnimations.filter { it.isMovement }
    }

    /**
     * Suggest animations for emotion
     *
     * Returns all animations that could represent the emotion,
     * ranked by match score.
     *
     * @param emotion Avatar emotion
     * @param availableAnimations List of animations
     * @return List of (animation, score) pairs, sorted by score
     */
    fun suggestForEmotion(
        emotion: AvatarEmotion,
        availableAnimations: List<AnimationMetadata>
    ): List<Pair<AnimationMetadata, Int>> {
        val keywords = emotionKeywords[emotion] ?: emptyList()
        return availableAnimations.mapNotNull { anim ->
            val score = calculateMatchScore(anim.name, keywords)
            if (score > 0) Pair(anim, score) else null
        }.sortedByDescending { it.second }
    }

    /**
     * Find best matching animation from keyword list
     *
     * @param keywords List of keywords (ordered by preference)
     * @param availableAnimations List of animations
     * @return Best matching animation or null
     */
    private fun findBestMatch(
        keywords: List<String>,
        availableAnimations: List<AnimationMetadata>
    ): AnimationMetadata? {
        for (keyword in keywords) {
            val match = availableAnimations.firstOrNull {
                it.name.contains(keyword, ignoreCase = true)
            }
            if (match != null) {
                return match
            }
        }
        return null
    }

    /**
     * Calculate match score for animation name
     *
     * @param animationName Animation name to score
     * @param keywords Keywords to match against
     * @return Match score (0 = no match, 100 = exact match)
     */
    private fun calculateMatchScore(
        animationName: String,
        keywords: List<String>
    ): Int {
        val lowerName = animationName.lowercase()

        // Check for exact match
        if (keywords.any { lowerName == it.lowercase() }) {
            return MatchScore.EXACT.value
        }

        // Check for strong keyword match (first 2 keywords)
        if (keywords.take(2).any { lowerName.contains(it.lowercase()) }) {
            return MatchScore.STRONG.value
        }

        // Check for moderate keyword match (next 3 keywords)
        if (keywords.drop(2).take(3).any { lowerName.contains(it.lowercase()) }) {
            return MatchScore.MODERATE.value
        }

        // Check for weak keyword match (remaining keywords)
        if (keywords.drop(5).any { lowerName.contains(it.lowercase()) }) {
            return MatchScore.WEAK.value
        }

        return 0
    }

    /**
     * Generate animation mapping report
     *
     * Useful for debugging and understanding how emotions map to animations.
     *
     * @param availableAnimations List of animations from model
     * @return Human-readable mapping report
     */
    fun generateMappingReport(
        availableAnimations: List<AnimationMetadata>
    ): String {
        val report = StringBuilder()
        report.appendLine("Animation Mapping Report")
        report.appendLine("=" .repeat(50))
        report.appendLine()
        report.appendLine("Available Animations (${availableAnimations.size}):")
        availableAnimations.forEach { anim ->
            report.appendLine("  • ${anim.name} (${anim.duration}s, ${if (anim.isLoopable) "loop" else "once"})")
        }
        report.appendLine()
        report.appendLine("Emotion Mappings:")
        AvatarEmotion.entries.forEach { emotion ->
            val suggestions = suggestForEmotion(emotion, availableAnimations)
            val best = suggestions.firstOrNull()
            if (best != null) {
                report.appendLine("  ${emotion.emoji} ${emotion.displayName} → ${best.first.name} (score: ${best.second})")
            } else {
                report.appendLine("  ${emotion.emoji} ${emotion.displayName} → [no match]")
            }
        }
        report.appendLine()
        report.appendLine("Activity Mappings:")
        AvatarActivity.entries.forEach { activity ->
            val testState = AvatarState(emotion = AvatarEmotion.NEUTRAL, activity = activity)
            val anim = findAnimation(testState, availableAnimations)
            report.appendLine("  ${activity.displayName} → ${anim.name}")
        }

        return report.toString()
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Load model metadata
 * val metadata = GLBModelLoader.loadMetadata("models/Sparrow_Animations.glb")
 * val animations = metadata.animations
 *
 * // Find animation for current state
 * val state = AvatarState(
 *     emotion = AvatarEmotion.HAPPY,
 *     activity = AvatarActivity.GENERATING
 * )
 * val anim = AnimationIntrospector.findAnimation(state, animations)
 * println("Playing: ${anim.name}")  // Output: "Playing: Run" (activity takes precedence)
 *
 * // Find animation by exact name
 * val walkAnim = AnimationIntrospector.findByName("Walk", animations)
 *
 * // Find all idle variants
 * val idleAnims = AnimationIntrospector.getIdleAnimations(animations)
 * println("Found ${idleAnims.size} idle animations")
 *
 * // Get suggestions for emotion
 * val happySuggestions = AnimationIntrospector.suggestForEmotion(
 *     AvatarEmotion.HAPPY,
 *     animations
 * )
 * happySuggestions.forEach { (anim, score) ->
 *     println("  ${anim.name} (score: $score)")
 * }
 *
 * // Generate mapping report (for debugging)
 * val report = AnimationIntrospector.generateMappingReport(animations)
 * println(report)
 * ```
 */
