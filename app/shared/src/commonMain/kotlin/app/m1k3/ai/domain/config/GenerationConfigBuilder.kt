package app.m1k3.ai.domain.config

import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.chat.QueryType
import app.m1k3.ai.domain.platform.DeviceInfoProviderInterface
import app.m1k3.ai.domain.platform.DeviceTier

/**
 * Generation Config Builder - Builds adaptive AI generation configurations.
 *
 * Creates device-appropriate GenerationConfig based on:
 * - Query intent type (Educational, Technical, Factual, Conversational, Creative)
 * - Device RAM tier (Flagship, High-End, Mid-Range, Budget)
 * - User preferences (temperature, custom limits)
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * val builder = GenerationConfigBuilder(deviceInfo)
 *
 * val config = builder.build(
 *     queryType = QueryType.EDUCATIONAL,
 *     temperature = 0.7f
 * )
 * ```
 */
class GenerationConfigBuilder(
    private val deviceInfo: DeviceInfoProviderInterface
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
        val temp = temperature ?: queryType.temperature

        return GenerationConfig(
            maxTokens = maxTokens,
            temperature = temp
        )
    }

    /**
     * Build a GenerationConfig from a RAG intent category string.
     *
     * @param intentCategory The intent category (e.g., "SCIENCE", "CODE_DEBUG")
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
    fun getDeviceTier(): DeviceTier {
        val ramGB = deviceInfo.getDeviceRamGB()
        return when {
            ramGB >= GenerationDefaults.DeviceRam.FLAGSHIP -> DeviceTier.FLAGSHIP
            ramGB >= GenerationDefaults.DeviceRam.HIGH_END -> DeviceTier.HIGH_END
            ramGB >= GenerationDefaults.DeviceRam.MID_RANGE -> DeviceTier.MID_RANGE
            ramGB >= GenerationDefaults.DeviceRam.BUDGET -> DeviceTier.BUDGET
            else -> DeviceTier.LOW_END
        }
    }

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
            QueryType.CREATIVE -> calculateEducationalTokens(deviceRamGB) // Same as educational
        }
    }

    // ===== Private Token Calculations =====

    private fun calculateEducationalTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationDefaults.DeviceRam.FLAGSHIP ->
            GenerationDefaults.TokenLimits.Educational.FLAGSHIP
        deviceRamGB >= GenerationDefaults.DeviceRam.HIGH_END ->
            GenerationDefaults.TokenLimits.Educational.HIGH_END
        deviceRamGB >= GenerationDefaults.DeviceRam.MID_RANGE ->
            GenerationDefaults.TokenLimits.Educational.MID_RANGE
        else ->
            GenerationDefaults.TokenLimits.Educational.BUDGET
    }

    private fun calculateTechnicalTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationDefaults.DeviceRam.FLAGSHIP ->
            GenerationDefaults.TokenLimits.Technical.FLAGSHIP
        deviceRamGB >= GenerationDefaults.DeviceRam.HIGH_END ->
            GenerationDefaults.TokenLimits.Technical.HIGH_END
        deviceRamGB >= GenerationDefaults.DeviceRam.MID_RANGE ->
            GenerationDefaults.TokenLimits.Technical.MID_RANGE
        else ->
            GenerationDefaults.TokenLimits.Technical.BUDGET
    }

    private fun calculateFactualTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationDefaults.DeviceRam.FLAGSHIP ->
            GenerationDefaults.TokenLimits.Factual.FLAGSHIP
        deviceRamGB >= GenerationDefaults.DeviceRam.HIGH_END ->
            GenerationDefaults.TokenLimits.Factual.HIGH_END
        deviceRamGB >= GenerationDefaults.DeviceRam.MID_RANGE ->
            GenerationDefaults.TokenLimits.Factual.MID_RANGE
        else ->
            GenerationDefaults.TokenLimits.Factual.BUDGET
    }

    private fun calculateConversationalTokens(deviceRamGB: Int): Int = when {
        deviceRamGB >= GenerationDefaults.DeviceRam.FLAGSHIP ->
            GenerationDefaults.TokenLimits.Conversational.FLAGSHIP
        deviceRamGB >= GenerationDefaults.DeviceRam.HIGH_END ->
            GenerationDefaults.TokenLimits.Conversational.HIGH_END
        deviceRamGB >= GenerationDefaults.DeviceRam.MID_RANGE ->
            GenerationDefaults.TokenLimits.Conversational.MID_RANGE
        else ->
            GenerationDefaults.TokenLimits.Conversational.BUDGET
    }
}
