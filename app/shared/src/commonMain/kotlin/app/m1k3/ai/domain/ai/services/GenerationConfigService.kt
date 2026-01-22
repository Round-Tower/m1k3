package app.m1k3.ai.domain.ai.services

import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.chat.QueryType

/**
 * GenerationConfigService - Device-adaptive AI generation config
 *
 * Domain service extracted from GenerationConfigBuilder.
 * Pure Kotlin with minimal dependencies (only QueryType domain entity).
 *
 * **Philosophy:**
 * Device capabilities dictate generation limits. Small models need
 * conservative settings on budget devices, aggressive settings on flagships.
 *
 * **Orchestration:**
 * ```
 * 1. Device RAM → Base token limits (192-384)
 *     ↓
 * 2. QueryType → Token multipliers (0.5x-1.5x)
 *     ↓
 * 3. QueryType → Temperature (0.3-0.9)
 *     ↓
 * 4. Return GenerationConfig
 * ```
 *
 * **Device Tiers:**
 * - Flagship (12GB+ RAM): 384 base tokens
 * - High-end (8GB+ RAM): 320 base tokens
 * - Mid-range (6GB+ RAM): 256 base tokens
 * - Budget (<6GB RAM): 192 base tokens
 *
 * **QueryType Behavior:**
 * - CONVERSATIONAL: 50% tokens, high temp (0.8) - fast, creative
 * - TECHNICAL: 120% tokens, low temp (0.3) - precise, factual
 * - CREATIVE: 150% tokens, high temp (0.9) - imaginative, varied
 * - FACTUAL: 100% tokens, mid temp (0.5) - balanced
 *
 * **Usage:**
 * ```kotlin
 * val service = GenerationConfigService(
 *     deviceRamGB = 12,
 *     deviceTier = "Flagship"
 * )
 *
 * // Build config from QueryType
 * val config = service.buildConfig(QueryType.TECHNICAL)
 * println("Tokens: ${config.maxTokens}, Temp: ${config.temperature}")
 *
 * // Build config from intent category string
 * val config = service.buildFromIntent("Mathematical Calculations")
 * // Returns TECHNICAL config: high tokens, low temp
 * ```
 */
class GenerationConfigService(
    private val deviceRamGB: Int,
    private val deviceTier: String
) {
    /**
     * Build generation config from QueryType
     *
     * @param queryType Type of query (CONVERSATIONAL, TECHNICAL, CREATIVE, FACTUAL)
     * @return GenerationConfig with device-adaptive settings
     */
    fun buildConfig(queryType: QueryType): GenerationConfig {
        val baseTokens = getBaseTokensForDevice()
        val multiplier = queryType.tokenMultiplier
        val maxTokens = (baseTokens * multiplier).toInt()
        val temperature = queryType.temperature

        return GenerationConfig(
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    /**
     * Build generation config from intent category string
     *
     * Maps intent categories to QueryTypes:
     * - "Casual Conversation" → CONVERSATIONAL
     * - "Mathematical Calculations", "Code Debug" → TECHNICAL
     * - "Trivia & Fun Facts" → CREATIVE
     * - Everything else → FACTUAL (default)
     *
     * @param intentCategory Intent category from IntentClassifier
     * @return GenerationConfig with appropriate settings
     */
    fun buildFromIntent(intentCategory: String): GenerationConfig {
        val queryType = mapIntentToQueryType(intentCategory)
        return buildConfig(queryType)
    }

    /**
     * Get base token limit based on device RAM
     *
     * Device tiers:
     * - 12GB+: Flagship (384 tokens)
     * - 8GB+: High-end (320 tokens)
     * - 6GB+: Mid-range (256 tokens)
     * - <6GB: Budget (192 tokens)
     */
    private fun getBaseTokensForDevice(): Int {
        return when {
            deviceRamGB >= 12 -> 384  // Flagship
            deviceRamGB >= 8 -> 320   // High-end
            deviceRamGB >= 6 -> 256   // Mid-range
            else -> 192                // Budget
        }
    }

    /**
     * Map intent category to QueryType
     *
     * Intent categories from IntentClassifier:
     * - "Casual Conversation" → CONVERSATIONAL
     * - "Mathematical Calculations", "Code Debugging", "Technical Explanation" → TECHNICAL
     * - "Trivia & Fun Facts" → CREATIVE
     * - All others → FACTUAL
     */
    private fun mapIntentToQueryType(intentCategory: String): QueryType {
        return when {
            intentCategory.contains("Casual", ignoreCase = true) ||
            intentCategory.contains("Conversation", ignoreCase = true) -> {
                QueryType.CONVERSATIONAL
            }

            intentCategory.contains("Math", ignoreCase = true) ||
            intentCategory.contains("Code", ignoreCase = true) ||
            intentCategory.contains("Debug", ignoreCase = true) ||
            intentCategory.contains("Technical", ignoreCase = true) -> {
                QueryType.TECHNICAL
            }

            intentCategory.contains("Trivia", ignoreCase = true) ||
            intentCategory.contains("Fun", ignoreCase = true) ||
            intentCategory.contains("Creative", ignoreCase = true) -> {
                QueryType.CREATIVE
            }

            else -> QueryType.FACTUAL
        }
    }
}
