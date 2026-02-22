package app.m1k3.ai.domain.config

/**
 * Generation Defaults - Centralized configuration for AI generation parameters.
 *
 * Platform-agnostic configuration for device-adaptive generation.
 * All values are tuned for SmolLM2-360M and Gemma 3 models.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * **Architecture Notes:**
 * - Device tiers based on RAM thresholds (12GB flagship → 4GB budget)
 * - Token limits scale by query type and device capability
 * - Memory retrieval limits prevent context overflow on lower-end devices
 * - Similarity thresholds determine RAG quality indicators
 */
object GenerationDefaults {

    /**
     * Device RAM thresholds in GB.
     * Used for adaptive token limits and memory retrieval scaling.
     */
    object DeviceRam {
        /** Flagship devices (Pixel 8 Pro, Galaxy S24 Ultra) */
        const val FLAGSHIP = 12

        /** High-end devices (Pixel 8, Galaxy S24) */
        const val HIGH_END = 8

        /** Mid-range devices (Pixel 6a, Galaxy A54) */
        const val MID_RANGE = 6

        /** Budget devices (minimum supported) */
        const val BUDGET = 4
    }

    /**
     * Token limits by query type and device tier.
     *
     * ALL SET TO 0 = Use engine's getOptimalMaxTokens() for device-adaptive limits.
     * This unleashes model intelligence by allowing:
     * - 2GB devices: 1024 tokens (~768 words)
     * - 6GB devices: 2048 tokens (~1536 words)
     * - 12GB+ devices: 4096 tokens (~3000 words)
     *
     * Model will naturally stop at <end_of_turn> token before hitting limits.
     */
    object TokenLimits {

        /** Educational: Teach me, explain, how does X work */
        object Educational {
            const val FLAGSHIP = 0       // Use engine's getOptimalMaxTokens()
            const val HIGH_END = 0       // Use engine's getOptimalMaxTokens()
            const val MID_RANGE = 0      // Use engine's getOptimalMaxTokens()
            const val BUDGET = 0         // Use engine's getOptimalMaxTokens()
            const val MINIMUM = 0        // Use engine's getOptimalMaxTokens()
        }

        /** Technical: Code, debugging, technical problems */
        object Technical {
            const val FLAGSHIP = 0       // Use engine's getOptimalMaxTokens()
            const val HIGH_END = 0       // Use engine's getOptimalMaxTokens()
            const val MID_RANGE = 0      // Use engine's getOptimalMaxTokens()
            const val BUDGET = 0         // Use engine's getOptimalMaxTokens()
            const val MINIMUM = 0        // Use engine's getOptimalMaxTokens()
        }

        /** Factual: Who/what/when/where factual questions */
        object Factual {
            const val FLAGSHIP = 0       // Use engine's getOptimalMaxTokens()
            const val HIGH_END = 0       // Use engine's getOptimalMaxTokens()
            const val MID_RANGE = 0      // Use engine's getOptimalMaxTokens()
            const val BUDGET = 0         // Use engine's getOptimalMaxTokens()
            const val MINIMUM = 0        // Use engine's getOptimalMaxTokens()
        }

        /** Conversational: Casual chat, greetings, general discussion */
        object Conversational {
            const val FLAGSHIP = 0       // Use engine's getOptimalMaxTokens()
            const val HIGH_END = 0       // Use engine's getOptimalMaxTokens()
            const val MID_RANGE = 0      // Use engine's getOptimalMaxTokens()
            const val BUDGET = 0         // Use engine's getOptimalMaxTokens()
            const val MINIMUM = 0        // Use engine's getOptimalMaxTokens()
        }
    }

    /**
     * Memory retrieval limits (topK) for semantic memory search.
     * Higher values provide more context but use more tokens.
     */
    object MemoryTopK {
        const val FLAGSHIP = 20
        const val HIGH_END = 15
        const val MID_RANGE = 10
        const val BUDGET = 5
    }

    /**
     * Similarity thresholds for RAG quality indicators.
     */
    object Similarity {
        /** High quality - reliable RAG results */
        const val HIGH_QUALITY = 0.7f

        /** Medium quality - useful but not perfect */
        const val MEDIUM_QUALITY = 0.6f

        /** Minimum threshold for including results */
        const val MINIMUM = 0.5f
    }

    /**
     * Generation temperature defaults.
     * Lower = more focused, higher = more creative.
     */
    object Temperature {
        const val DEFAULT = 0.5f
        const val CREATIVE = 0.7f
        const val FOCUSED = 0.3f
    }

    /**
     * Memory content preview limits.
     */
    object MemoryPreview {
        const val MAX_CONTENT_LENGTH = 200
    }

    /**
     * Context window configuration for conversation history.
     */
    object ContextWindow {
        const val MAX_MESSAGE_LENGTH = 300
        const val MAX_TOTAL_TOKENS = 4096
        const val TOKENS_PER_CHAR = 0.25f
    }
}
