package ai.ma.generation

/**
 * Adaptive Generation Configuration - PHASE1.5-007
 *
 * Adjusts generation parameters (max_tokens, temperature) based on query type
 * and device capabilities, enabling optimal responses for different use cases.
 *
 * **Problem Solved:**
 * Current fixed 256 tokens cuts off educational explanations mid-thought.
 * Query: "Can you teach me about quantum entanglement?" → 256 tokens (incomplete)
 *
 * **Solution:**
 * - Educational queries ("teach me", "explain") → 1024 tokens on 8GB devices
 * - Technical queries (coding, debugging) → 768 tokens
 * - Conversational queries (casual chat) → 256 tokens
 * - Factual queries ("what is", "who") → 512 tokens
 *
 * **Temperature Adaptation:**
 * - Technical (code): 0.3 (deterministic)
 * - Factual: 0.5 (consistent)
 * - Educational: 0.6 (engaging)
 * - Conversational: 0.7 (natural)
 *
 * Example:
 * ```kotlin
 * val config = AdaptiveGeneration.getConfig(
 *     query = "Can you teach me about AI?",
 *     deviceRamGB = 8L
 * )
 * // Returns: GenerationConfig(maxTokens=1024, temperature=0.6, reason="Educational query on 8GB device")
 * ```
 */
object AdaptiveGeneration {

    /**
     * Get optimal generation configuration for a query.
     *
     * @param query User's input query
     * @param deviceRamGB Device RAM in gigabytes
     * @param userPreferences Optional user verbosity preferences
     * @return Optimized generation configuration
     */
    fun getConfig(
        query: String,
        deviceRamGB: Long,
        userPreferences: UserPreferences = UserPreferences()
    ): GenerationConfig {
        val queryType = detectQueryType(query)

        val baseMaxTokens = calculateMaxTokens(queryType, deviceRamGB)
        val temperature = calculateTemperature(queryType)

        // Apply user preference multiplier
        val finalMaxTokens = (baseMaxTokens * userPreferences.verbosityMultiplier).toInt()
            .coerceIn(minimumTokens(queryType), maximumTokens(deviceRamGB))

        return GenerationConfig(
            maxTokens = finalMaxTokens,
            temperature = temperature,
            queryType = queryType,
            reason = buildReasonString(queryType, deviceRamGB, userPreferences)
        )
    }

    /**
     * Detect the type of query based on content and structure.
     *
     * @param query User's input query
     * @return Detected query type
     */
    fun detectQueryType(query: String): QueryType {
        val lower = query.lowercase().trim()

        return when {
            // Educational queries: explanations, teaching, learning
            lower.containsAny(
                "teach me", "explain", "how does", "how do", "how can",
                "what are the steps", "help me understand", "walk me through",
                "tell me about", "describe", "elaborate"
            ) -> QueryType.EDUCATIONAL

            // Technical queries: coding, debugging, problem-solving
            lower.containsAny(
                "write code", "function", "program", "debug", "error",
                "implement", "algorithm", "fix", "optimize", "refactor",
                "code review", "syntax", "compile"
            ) -> QueryType.TECHNICAL

            // Factual queries: definitions, facts, specific information
            lower.containsAny(
                "what is", "who is", "when did", "where is", "define",
                "which", "how many", "how much", "name the", "list"
            ) -> QueryType.FACTUAL

            // Conversational queries: everything else (default)
            else -> QueryType.CONVERSATIONAL
        }
    }

    /**
     * Calculate optimal max tokens based on query type and device RAM.
     */
    private fun calculateMaxTokens(queryType: QueryType, deviceRamGB: Long): Int {
        return when (queryType) {
            QueryType.EDUCATIONAL -> {
                // Educational needs long, detailed explanations
                when {
                    deviceRamGB >= 12 -> 1536  // Very detailed (high-end devices)
                    deviceRamGB >= 8 -> 1024   // Full explanation (recommended minimum)
                    deviceRamGB >= 6 -> 768    // Good explanation
                    deviceRamGB >= 4 -> 512    // Basic explanation
                    else -> 384                 // Minimal (low-end devices)
                }
            }
            QueryType.TECHNICAL -> {
                // Technical needs medium length with code examples
                when {
                    deviceRamGB >= 12 -> 1024  // Code + detailed explanation
                    deviceRamGB >= 8 -> 768    // Code + good explanation
                    deviceRamGB >= 6 -> 512    // Code + basic explanation
                    deviceRamGB >= 4 -> 384    // Code snippet only
                    else -> 256                 // Minimal code
                }
            }
            QueryType.FACTUAL -> {
                // Factual needs concise, specific answers
                when {
                    deviceRamGB >= 8 -> 512    // Detailed fact + context
                    deviceRamGB >= 6 -> 384    // Fact + some context
                    deviceRamGB >= 4 -> 256    // Fact only
                    else -> 192                 // Brief fact
                }
            }
            QueryType.CONVERSATIONAL -> {
                // Conversational needs short, friendly responses
                when {
                    deviceRamGB >= 6 -> 384    // Friendly + elaboration
                    deviceRamGB >= 4 -> 256    // Standard response
                    else -> 192                 // Brief response
                }
            }
        }
    }

    /**
     * Calculate optimal temperature based on query type.
     */
    private fun calculateTemperature(queryType: QueryType): Float {
        return when (queryType) {
            QueryType.TECHNICAL -> 0.3f      // Deterministic, accurate code
            QueryType.FACTUAL -> 0.5f        // Consistent, reliable facts
            QueryType.EDUCATIONAL -> 0.6f    // Engaging, varied explanations
            QueryType.CONVERSATIONAL -> 0.7f // Natural, friendly conversation
        }
    }

    /**
     * Get minimum tokens required for query type.
     */
    private fun minimumTokens(queryType: QueryType): Int {
        return when (queryType) {
            QueryType.EDUCATIONAL -> 384   // Can't explain in less
            QueryType.TECHNICAL -> 256     // Need space for code
            QueryType.FACTUAL -> 128       // Brief fact minimum
            QueryType.CONVERSATIONAL -> 64 // Short response OK
        }
    }

    /**
     * Get maximum tokens allowed based on device RAM.
     */
    private fun maximumTokens(deviceRamGB: Long): Int {
        return when {
            deviceRamGB >= 12 -> 2048  // High-end devices
            deviceRamGB >= 8 -> 1536   // Mid-high devices
            deviceRamGB >= 6 -> 1024   // Mid-range devices
            deviceRamGB >= 4 -> 768    // Low-mid devices
            else -> 512                 // Low-end devices
        }
    }

    /**
     * Build human-readable reason string for debugging.
     */
    private fun buildReasonString(
        queryType: QueryType,
        deviceRamGB: Long,
        userPreferences: UserPreferences
    ): String {
        return buildString {
            append("Query type: ${queryType.name.lowercase()}")
            append(", Device: ${deviceRamGB}GB RAM")
            if (userPreferences.verbosityMultiplier != 1.0f) {
                append(", Verbosity: ${userPreferences.verbosityMultiplier}x")
            }
        }
    }

    /**
     * String extension for checking multiple substrings.
     */
    private fun String.containsAny(vararg phrases: String): Boolean {
        return phrases.any { this.contains(it) }
    }
}

/**
 * Query Type Classification
 *
 * Categorizes user queries to determine optimal generation parameters.
 */
enum class QueryType {
    /**
     * Educational: "teach me", "explain", "how does X work"
     * Needs: Long, detailed explanations (1024+ tokens)
     * Temperature: 0.6 (engaging but informative)
     */
    EDUCATIONAL,

    /**
     * Technical: coding, debugging, problem-solving
     * Needs: Medium length with code examples (768 tokens)
     * Temperature: 0.3 (deterministic, accurate)
     */
    TECHNICAL,

    /**
     * Factual: "what is", "who invented", "when did"
     * Needs: Concise, specific answers (512 tokens)
     * Temperature: 0.5 (consistent facts)
     */
    FACTUAL,

    /**
     * Conversational: casual chat, greetings, general dialogue
     * Needs: Short, friendly responses (256 tokens)
     * Temperature: 0.7 (natural conversation)
     */
    CONVERSATIONAL
}

/**
 * Generation Configuration
 *
 * Contains all parameters needed for optimal text generation.
 */
data class GenerationConfig(
    /**
     * Maximum number of tokens to generate.
     */
    val maxTokens: Int,

    /**
     * Sampling temperature (0.0 = deterministic, 1.0 = very creative).
     */
    val temperature: Float,

    /**
     * Detected query type for this configuration.
     */
    val queryType: QueryType,

    /**
     * Human-readable reason for these parameters (debugging).
     */
    val reason: String
) {
    init {
        require(maxTokens > 0) { "maxTokens must be positive, got $maxTokens" }
        require(temperature in 0.0f..1.0f) { "temperature must be in [0,1], got $temperature" }
    }

    /**
     * Returns a copy with adjusted max tokens (for user preference).
     */
    fun withMaxTokens(newMaxTokens: Int): GenerationConfig {
        return copy(maxTokens = newMaxTokens)
    }

    /**
     * Returns a copy with adjusted temperature (for user preference).
     */
    fun withTemperature(newTemperature: Float): GenerationConfig {
        return copy(temperature = newTemperature)
    }
}

/**
 * User Preferences for Generation
 *
 * Allows users to customize verbosity and creativity.
 */
data class UserPreferences(
    /**
     * Verbosity multiplier:
     * - 0.5 = concise (half the tokens)
     * - 1.0 = default
     * - 1.5 = verbose (50% more tokens)
     * - 2.0 = very verbose (double tokens)
     */
    val verbosityMultiplier: Float = 1.0f,

    /**
     * Optional temperature override (null = use default for query type).
     */
    val temperatureOverride: Float? = null
) {
    init {
        require(verbosityMultiplier > 0) { "verbosityMultiplier must be positive" }
        temperatureOverride?.let {
            require(it in 0.0f..1.0f) { "temperature must be in [0,1]" }
        }
    }

    companion object {
        /**
         * Preset: Concise responses (50% tokens).
         */
        val CONCISE = UserPreferences(verbosityMultiplier = 0.5f)

        /**
         * Preset: Default balanced responses.
         */
        val BALANCED = UserPreferences(verbosityMultiplier = 1.0f)

        /**
         * Preset: Verbose responses (150% tokens).
         */
        val VERBOSE = UserPreferences(verbosityMultiplier = 1.5f)

        /**
         * Preset: Very verbose responses (200% tokens).
         */
        val VERY_VERBOSE = UserPreferences(verbosityMultiplier = 2.0f)
    }
}
