package app.m1k3.ai.assistant.chat

import app.m1k3.ai.assistant.ai.GenerationConfig
import app.m1k3.ai.assistant.config.GenerationConstants
import app.m1k3.ai.assistant.platform.DeviceInfoProvider
import app.m1k3.ai.assistant.platform.DeviceTier
import app.m1k3.ai.assistant.platform.getDeviceTier

/**
 * GenerationConfigBuilder - Builds adaptive AI generation configurations.
 *
 * Creates device-appropriate GenerationConfig based on:
 * - Query intent type (Educational, Technical, Factual, Conversational)
 * - Device RAM tier (Flagship, High-End, Mid-Range, Budget)
 * - User preferences (temperature, custom limits)
 *
 * **Usage:**
 * ```kotlin
 * val builder = GenerationConfigBuilder(deviceInfo)
 *
 * // Build config for a query
 * val config = builder.build(
 *     queryType = QueryType.EDUCATIONAL,
 *     temperature = 0.7f
 * )
 *
 * // Or infer type from intent category
 * val config = builder.buildFromIntent(
 *     intentCategory = "SCIENCE",
 *     temperature = null  // Use default
 * )
 * ```
 *
 * **Design Principles:**
 * - Single Responsibility: Only builds generation configs
 * - Testable: No side effects, pure configuration logic
 * - Device-adaptive: Automatically adjusts to device capabilities
 */
class GenerationConfigBuilder(
    private val deviceInfo: DeviceInfoProvider
) {
    /**
     * Build a GenerationConfig for the given query type.
     *
     * @param queryType The type of query being made
     * @param temperature Temperature for generation (null = default for query type)
     * @param customMaxTokens Override max tokens (null = device-adaptive)
     * @return Configured GenerationConfig
     */
    fun build(
        queryType: QueryType = QueryType.CONVERSATIONAL,
        temperature: Float? = null,
        customMaxTokens: Int? = null
    ): GenerationConfig {
        val deviceRamGB = deviceInfo.getDeviceRamGB()
        val maxTokens = customMaxTokens ?: calculateMaxTokens(queryType, deviceRamGB)
        val temp = temperature ?: getDefaultTemperature(queryType)

        return GenerationConfig(
            maxTokens = maxTokens,
            temperature = temp
        )
    }

    /**
     * Build a GenerationConfig from a RAG intent category string.
     *
     * @param intentCategory The intent category from IntentClassifier (e.g., "SCIENCE", "CODE_DEBUG")
     * @param temperature Temperature for generation (null = default)
     * @param customMaxTokens Override max tokens (null = device-adaptive)
     * @return Configured GenerationConfig
     */
    fun buildFromIntent(
        intentCategory: String,
        temperature: Float? = null,
        customMaxTokens: Int? = null
    ): GenerationConfig {
        val queryType = QueryType.fromIntentCategory(intentCategory)
        return build(queryType, temperature, customMaxTokens)
    }

    /**
     * Get the device tier for the current device.
     */
    fun getDeviceTier(): DeviceTier = deviceInfo.getDeviceTier()

    /**
     * Get the current device RAM in GB.
     */
    fun getDeviceRamGB(): Int = deviceInfo.getDeviceRamGB()

    /**
     * Calculate appropriate max tokens for a query type and device.
     *
     * @param queryType The type of query
     * @param deviceRamGB Device RAM in GB
     * @return Maximum tokens for generation
     */
    fun calculateMaxTokens(queryType: QueryType, deviceRamGB: Int): Int {
        return when (queryType) {
            QueryType.EDUCATIONAL -> calculateEducationalTokens(deviceRamGB)
            QueryType.TECHNICAL -> calculateTechnicalTokens(deviceRamGB)
            QueryType.FACTUAL -> calculateFactualTokens(deviceRamGB)
            QueryType.CONVERSATIONAL -> calculateConversationalTokens(deviceRamGB)
        }
    }

    /**
     * Get the default temperature for a query type.
     *
     * Educational/Technical queries use lower temperature for accuracy.
     * Conversational queries use higher temperature for creativity.
     */
    fun getDefaultTemperature(queryType: QueryType): Float {
        return when (queryType) {
            QueryType.EDUCATIONAL -> GenerationConstants.Temperature.FOCUSED
            QueryType.TECHNICAL -> GenerationConstants.Temperature.FOCUSED
            QueryType.FACTUAL -> GenerationConstants.Temperature.DEFAULT
            QueryType.CONVERSATIONAL -> GenerationConstants.Temperature.CREATIVE
        }
    }

    // ===== Private Token Calculations =====

    private fun calculateEducationalTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationConstants.DeviceRam.FLAGSHIP ->
            GenerationConstants.TokenLimits.Educational.FLAGSHIP
        deviceRamGB >= GenerationConstants.DeviceRam.HIGH_END ->
            GenerationConstants.TokenLimits.Educational.HIGH_END
        deviceRamGB >= GenerationConstants.DeviceRam.MID_RANGE ->
            GenerationConstants.TokenLimits.Educational.MID_RANGE
        else ->
            GenerationConstants.TokenLimits.Educational.BUDGET
    }

    private fun calculateTechnicalTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationConstants.DeviceRam.FLAGSHIP ->
            GenerationConstants.TokenLimits.Technical.FLAGSHIP
        deviceRamGB >= GenerationConstants.DeviceRam.HIGH_END ->
            GenerationConstants.TokenLimits.Technical.HIGH_END
        deviceRamGB >= GenerationConstants.DeviceRam.MID_RANGE ->
            GenerationConstants.TokenLimits.Technical.MID_RANGE
        else ->
            GenerationConstants.TokenLimits.Technical.BUDGET
    }

    private fun calculateFactualTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationConstants.DeviceRam.FLAGSHIP ->
            GenerationConstants.TokenLimits.Factual.FLAGSHIP
        deviceRamGB >= GenerationConstants.DeviceRam.HIGH_END ->
            GenerationConstants.TokenLimits.Factual.HIGH_END
        deviceRamGB >= GenerationConstants.DeviceRam.MID_RANGE ->
            GenerationConstants.TokenLimits.Factual.MID_RANGE
        else ->
            GenerationConstants.TokenLimits.Factual.BUDGET
    }

    private fun calculateConversationalTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationConstants.DeviceRam.FLAGSHIP ->
            GenerationConstants.TokenLimits.Conversational.FLAGSHIP
        deviceRamGB >= GenerationConstants.DeviceRam.HIGH_END ->
            GenerationConstants.TokenLimits.Conversational.HIGH_END
        deviceRamGB >= GenerationConstants.DeviceRam.MID_RANGE ->
            GenerationConstants.TokenLimits.Conversational.MID_RANGE
        else ->
            GenerationConstants.TokenLimits.Conversational.BUDGET
    }
}

/**
 * Query type classification for generation configuration.
 *
 * Maps RAG intent categories to simplified query types for
 * token limit and temperature selection.
 */
enum class QueryType {
    /**
     * Educational queries: history, science, geography, learning.
     * Requires longer, detailed responses.
     */
    EDUCATIONAL,

    /**
     * Technical queries: code debugging, math, technical explanations.
     * Requires precise, structured responses.
     */
    TECHNICAL,

    /**
     * Factual queries: device tech, WiFi, security, troubleshooting, trivia.
     * Requires accurate, concise responses.
     */
    FACTUAL,

    /**
     * Conversational queries: greetings, casual chat, general topics.
     * Allows more creative, natural responses.
     */
    CONVERSATIONAL;

    companion object {
        /**
         * Map RAG intent category to QueryType.
         *
         * Intent categories from IntentClassifier are mapped to
         * simplified query types for configuration purposes.
         */
        fun fromIntentCategory(category: String): QueryType {
            val normalizedCategory = category.uppercase().trim()

            return when {
                // Educational categories
                normalizedCategory in EDUCATIONAL_INTENTS -> EDUCATIONAL

                // Technical categories
                normalizedCategory in TECHNICAL_INTENTS -> TECHNICAL

                // Factual categories
                normalizedCategory in FACTUAL_INTENTS -> FACTUAL

                // Default to conversational
                else -> CONVERSATIONAL
            }
        }

        // Intent category mappings (from IntentClassifier)
        private val EDUCATIONAL_INTENTS = setOf(
            "HISTORY", "SCIENCE", "GEOGRAPHY",
            "EDUCATION", "AI_ML", "AI & ML",
            "MOVIES_TV", "MUSIC", "SPORTS",
            "FOOD", "LIFESTYLE"
        )

        private val TECHNICAL_INTENTS = setOf(
            "MATH", "CODE_DEBUG", "CODE DEBUG",
            "TECHNICAL_EXPLANATION", "TECHNICAL EXPLANATION",
            "SYSTEM"
        )

        private val FACTUAL_INTENTS = setOf(
            "DEVICE_TECH", "DEVICE TECH",
            "WIFI_NETWORK", "WIFI NETWORK",
            "SECURITY", "SECURITY_PRIVACY", "SECURITY & PRIVACY",
            "TROUBLESHOOTING", "DIAGNOSTIC",
            "TRIVIA", "TECHNOLOGY"
        )
    }
}
