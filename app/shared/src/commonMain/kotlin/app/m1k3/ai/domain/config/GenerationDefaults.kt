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
     * Educational queries get more tokens for comprehensive explanations.
     * Technical queries balance detail with precision.
     * Factual/Conversational queries are more concise.
     */
    object TokenLimits {

        /** Educational: Teach me, explain, how does X work */
        object Educational {
            const val FLAGSHIP = 1536    // Full comprehensive teaching
            const val HIGH_END = 1024    // Detailed explanations
            const val MID_RANGE = 768    // Balanced detail
            const val BUDGET = 512       // Concise but complete
            const val MINIMUM = 512      // Fallback for very low RAM
        }

        /** Technical: Code, debugging, technical problems */
        object Technical {
            const val FLAGSHIP = 1024    // Complex code/debugging
            const val HIGH_END = 768     // Code with explanations
            const val MID_RANGE = 512    // Focused technical
            const val BUDGET = 384       // Minimal technical
            const val MINIMUM = 384      // Fallback
        }

        /** Factual: Who/what/when/where factual questions */
        object Factual {
            const val FLAGSHIP = 512     // Detailed facts
            const val HIGH_END = 384     // Comprehensive facts
            const val MID_RANGE = 320    // Focused facts
            const val BUDGET = 256       // Concise facts
            const val MINIMUM = 256      // Fallback
        }

        /** Conversational: Casual chat, greetings, general discussion */
        object Conversational {
            const val FLAGSHIP = 512     // Natural lengthy chat
            const val HIGH_END = 384     // Conversational
            const val MID_RANGE = 320    // Friendly chat
            const val BUDGET = 256       // Brief chat
            const val MINIMUM = 256      // Fallback
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
