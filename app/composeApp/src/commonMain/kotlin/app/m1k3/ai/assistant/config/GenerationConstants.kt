package app.m1k3.ai.assistant.config

/**
 * Generation Constants - Centralized configuration for AI generation parameters.
 *
 * This object consolidates all magic numbers from ChatScreen.kt into testable,
 * documented constants. All values are tuned for device-adaptive generation
 * with SmolLM2-360M and Gemma 3 models.
 *
 * **Architecture Notes:**
 * - Device tiers based on RAM thresholds (12GB flagship → 4GB budget)
 * - Token limits scale by query type and device capability
 * - Memory retrieval limits prevent context overflow on lower-end devices
 * - Similarity thresholds determine RAG quality indicators
 */
object GenerationConstants {

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
     *
     * Estimated token usage:
     * - FLAGSHIP: ~4K tokens for 20 memories
     * - HIGH_END: ~3K tokens for 15 memories
     * - MID_RANGE: ~2K tokens for 10 memories
     * - BUDGET: ~1K tokens for 5 memories
     */
    object MemoryTopK {
        const val FLAGSHIP = 20
        const val HIGH_END = 15
        const val MID_RANGE = 10
        const val BUDGET = 5
    }

    /**
     * Similarity thresholds for RAG quality indicators.
     * Used to show reliability emoji in chat interface.
     */
    object Similarity {
        /** High quality - reliable RAG results (shows checkmark) */
        const val HIGH_QUALITY = 0.7f

        /** Medium quality - useful but not perfect (shows warning) */
        const val MEDIUM_QUALITY = 0.6f

        /** Minimum threshold for including results (below shows question mark) */
        const val MINIMUM = 0.5f
    }

    /**
     * RAG context budget configuration.
     *
     * Controls how much context is allocated to RAG facts to prevent
     * overwhelming small models (SmolLM2-360M).
     *
     * Uses approximate token counting: 1 token ≈ 4 characters.
     */
    object RAGBudget {
        /** Maximum characters for all RAG context (~400 tokens) */
        const val MAX_CONTEXT_CHARS = 1600

        /** Maximum characters per fact before truncation */
        const val MAX_FACT_CHARS = 100

        /** Maximum number of facts regardless of budget */
        const val MAX_FACT_COUNT = 3
    }

    /**
     * System prompt hints for adaptive generation.
     * Tuned for small models that tend to deflect with questions.
     */
    object SystemPromptHints {
        const val EDUCATIONAL = "Teach! Provide information using the facts given. Do NOT ask questions back - explain directly."
        const val TECHNICAL = "Be precise but make it accessible. Walk through the details step by step."
        const val FACTUAL = "Share the facts directly with interesting context. Answer the question."
        const val CONVERSATIONAL = "Be natural and friendly. Have a real conversation!"
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
     * Used when formatting memories for context.
     */
    object MemoryPreview {
        const val MAX_CONTENT_LENGTH = 200
    }

    /**
     * Context window configuration for conversation history.
     * Controls how much history is included in prompts.
     */
    object ContextWindow {
        /** Maximum characters per message in conversation history */
        const val MAX_MESSAGE_LENGTH = 300

        /** Maximum total context tokens (model limit is ~24K, leave room for response) */
        const val MAX_TOTAL_TOKENS = 4096

        /** Estimated tokens per character (rough approximation) */
        const val TOKENS_PER_CHAR = 0.25f
    }
}
