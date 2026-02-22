package app.m1k3.ai.domain.ai.services

import app.m1k3.ai.domain.chat.QueryType
import kotlin.test.*

/**
 * GenerationConfigService Tests - TDD Red Phase
 *
 * Validates device-adaptive AI generation config:
 * 1. Device RAM → base token limits (192-384)
 * 2. Device tier → performance tuning
 * 3. QueryType → temperature & token multipliers
 * 4. Intent category → config overrides
 *
 * **Device Tiers:**
 * - Flagship (12GB+ RAM): 384 base tokens
 * - High-end (8GB+ RAM): 320 base tokens
 * - Mid-range (6GB+ RAM): 256 base tokens
 * - Budget (<6GB RAM): 192 base tokens
 *
 * **QueryType Multipliers:**
 * - CONVERSATIONAL: 0.5x tokens, temp 0.8
 * - TECHNICAL: 1.2x tokens, temp 0.3
 * - CREATIVE: 1.5x tokens, temp 0.9
 * - FACTUAL: 1.0x tokens, temp 0.5
 *
 * **Success Criteria:**
 * - ✅ Flagship devices get higher token limits
 * - ✅ Budget devices get conservative limits
 * - ✅ Creative queries get higher temperature
 * - ✅ Technical queries get lower temperature
 * - ✅ Intent-based config overrides work
 */
class GenerationConfigServiceTest {

    // ========================================
    // Device Tier Tests
    // ========================================

    @Test
    fun `flagship device gets maximum tokens`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // Flagship: 384 base tokens * 1.0 (FACTUAL) = 384
        assertEquals(384, config.maxTokens!!)
    }

    @Test
    fun `high-end device gets high token limit`() {
        val service = GenerationConfigService(
            deviceRamGB = 8,
            deviceTier = "High-end"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // High-end: 320 base tokens * 1.0 (FACTUAL) = 320
        assertEquals(320, config.maxTokens!!)
    }

    @Test
    fun `mid-range device gets moderate token limit`() {
        val service = GenerationConfigService(
            deviceRamGB = 6,
            deviceTier = "Mid-range"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // Mid-range: 256 base tokens * 1.0 (FACTUAL) = 256
        assertEquals(256, config.maxTokens!!)
    }

    @Test
    fun `budget device gets conservative token limit`() {
        val service = GenerationConfigService(
            deviceRamGB = 4,
            deviceTier = "Budget"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // Budget: 192 base tokens * 1.0 (FACTUAL) = 192
        assertEquals(192, config.maxTokens!!)
    }

    // ========================================
    // QueryType Multiplier Tests
    // ========================================

    @Test
    fun `conversational queries get reduced tokens`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildConfig(QueryType.CONVERSATIONAL)

        // 384 base * 0.7 (CONVERSATIONAL) = 268
        assertEquals(268, config.maxTokens!!)
    }

    @Test
    fun `technical queries get increased tokens`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildConfig(QueryType.TECHNICAL)

        // 384 base * 1.2 (TECHNICAL) = 460
        assertEquals(460, config.maxTokens!!)
    }

    @Test
    fun `creative queries get maximum tokens`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildConfig(QueryType.CREATIVE)

        // 384 base * 1.5 (CREATIVE) = 576
        assertEquals(576, config.maxTokens!!)
    }

    // ========================================
    // Temperature Tests
    // ========================================

    @Test
    fun `conversational queries get high temperature`() {
        val service = GenerationConfigService(
            deviceRamGB = 8,
            deviceTier = "High-end"
        )

        val config = service.buildConfig(QueryType.CONVERSATIONAL)

        // CONVERSATIONAL temp = 0.7
        assertEquals(0.7f, config.temperature!!, 0.01f)
    }

    @Test
    fun `technical queries get low temperature`() {
        val service = GenerationConfigService(
            deviceRamGB = 8,
            deviceTier = "High-end"
        )

        val config = service.buildConfig(QueryType.TECHNICAL)

        // TECHNICAL temp = 0.3
        assertEquals(0.3f, config.temperature!!, 0.01f)
    }

    @Test
    fun `creative queries get maximum temperature`() {
        val service = GenerationConfigService(
            deviceRamGB = 8,
            deviceTier = "High-end"
        )

        val config = service.buildConfig(QueryType.CREATIVE)

        // CREATIVE temp = 0.9
        assertEquals(0.9f, config.temperature!!, 0.01f)
    }

    @Test
    fun `factual queries get moderate temperature`() {
        val service = GenerationConfigService(
            deviceRamGB = 8,
            deviceTier = "High-end"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // FACTUAL temp = 0.5
        assertEquals(0.5f, config.temperature!!, 0.01f)
    }

    // ========================================
    // Intent-Based Config Tests
    // ========================================

    @Test
    fun `conversational intent uses conversational config`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildFromIntent("Casual Conversation")

        // Should use CONVERSATIONAL: 384 * 0.7 = 268 tokens, temp 0.7
        assertEquals(268, config.maxTokens!!)
        assertEquals(0.7f, config.temperature!!, 0.01f)
    }

    @Test
    fun `math intent uses technical config`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildFromIntent("Mathematical Calculations")

        // Should use TECHNICAL: 384 * 1.2 = 460 tokens, temp 0.3
        assertEquals(460, config.maxTokens!!)
        assertEquals(0.3f, config.temperature!!, 0.01f)
    }

    @Test
    fun `trivia intent uses creative config`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildFromIntent("Trivia & Fun Facts")

        // Should use CREATIVE: 384 * 1.5 = 576 tokens, temp 0.9
        assertEquals(576, config.maxTokens!!)
        assertEquals(0.9f, config.temperature!!, 0.01f)
    }

    @Test
    fun `unknown intent defaults to factual config`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        val config = service.buildFromIntent("Unknown Category")

        // Should use FACTUAL: 384 * 1.0 = 384 tokens, temp 0.5
        assertEquals(384, config.maxTokens!!)
        assertEquals(0.5f, config.temperature!!, 0.01f)
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `extremely low RAM gets minimum tokens`() {
        val service = GenerationConfigService(
            deviceRamGB = 2,
            deviceTier = "Budget"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // Budget tier: 192 tokens minimum
        assertEquals(192, config.maxTokens!!)
    }

    @Test
    fun `extremely high RAM gets maximum tokens`() {
        val service = GenerationConfigService(
            deviceRamGB = 24,
            deviceTier = "Flagship"
        )

        val config = service.buildConfig(QueryType.FACTUAL)

        // Flagship tier: 384 base tokens
        assertEquals(384, config.maxTokens!!)
    }

    @Test
    fun `token limits are always positive`() {
        val service = GenerationConfigService(
            deviceRamGB = 4,
            deviceTier = "Budget"
        )

        // Even with minimum config, tokens should be positive
        val config = service.buildConfig(QueryType.CONVERSATIONAL)

        // 192 base * 0.5 = 96 tokens (still positive)
        assertTrue(config.maxTokens!! > 0)
    }

    @Test
    fun `temperature stays within valid range`() {
        val service = GenerationConfigService(
            deviceRamGB = 12,
            deviceTier = "Flagship"
        )

        // Test all query types
        QueryType.values().forEach { queryType ->
            val config = service.buildConfig(queryType)

            // Temperature should be 0.0 to 1.0
            assertTrue(config.temperature!! >= 0f)
            assertTrue(config.temperature!! <= 1f)
        }
    }

    // ========================================
    // Combined Scenarios
    // ========================================

    @Test
    fun `budget device with creative query still works`() {
        val service = GenerationConfigService(
            deviceRamGB = 4,
            deviceTier = "Budget"
        )

        val config = service.buildConfig(QueryType.CREATIVE)

        // 192 base * 1.5 = 288 tokens (reasonable for budget)
        assertEquals(288, config.maxTokens!!)
        assertEquals(0.9f, config.temperature!!, 0.01f)
    }

    @Test
    fun `flagship device with conversational query optimizes for speed`() {
        val service = GenerationConfigService(
            deviceRamGB = 16,
            deviceTier = "Flagship"
        )

        val config = service.buildConfig(QueryType.CONVERSATIONAL)

        // 384 base * 0.7 = 268 tokens (natural, conversational responses)
        assertEquals(268, config.maxTokens!!)
        assertEquals(0.7f, config.temperature!!, 0.01f)
    }
}
