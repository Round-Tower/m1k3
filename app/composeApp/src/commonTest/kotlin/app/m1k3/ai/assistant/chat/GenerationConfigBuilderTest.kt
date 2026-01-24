package app.m1k3.ai.assistant.chat

import app.m1k3.ai.assistant.config.GenerationConstants
import app.m1k3.ai.assistant.mocks.MockDeviceInfoProvider
import app.m1k3.ai.assistant.platform.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for GenerationConfigBuilder.
 *
 * Validates device-adaptive generation config creation with 100% coverage.
 */
class GenerationConfigBuilderTest {

    // ===== QueryType Mapping Tests =====

    @Test
    fun `QueryType fromIntentCategory maps HISTORY to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("HISTORY"))
    }

    @Test
    fun `QueryType fromIntentCategory maps SCIENCE to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("SCIENCE"))
    }

    @Test
    fun `QueryType fromIntentCategory maps GEOGRAPHY to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("GEOGRAPHY"))
    }

    @Test
    fun `QueryType fromIntentCategory maps EDUCATION to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("EDUCATION"))
    }

    @Test
    fun `QueryType fromIntentCategory maps AI_ML to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("AI_ML"))
    }

    @Test
    fun `QueryType fromIntentCategory maps AI and ML with spaces to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("AI & ML"))
    }

    @Test
    fun `QueryType fromIntentCategory maps MOVIES_TV to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("MOVIES_TV"))
    }

    @Test
    fun `QueryType fromIntentCategory maps MUSIC to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("MUSIC"))
    }

    @Test
    fun `QueryType fromIntentCategory maps SPORTS to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("SPORTS"))
    }

    @Test
    fun `QueryType fromIntentCategory maps FOOD to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("FOOD"))
    }

    @Test
    fun `QueryType fromIntentCategory maps LIFESTYLE to EDUCATIONAL`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("LIFESTYLE"))
    }

    @Test
    fun `QueryType fromIntentCategory maps MATH to TECHNICAL`() {
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("MATH"))
    }

    @Test
    fun `QueryType fromIntentCategory maps CODE_DEBUG to TECHNICAL`() {
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("CODE_DEBUG"))
    }

    @Test
    fun `QueryType fromIntentCategory maps CODE DEBUG with space to TECHNICAL`() {
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("CODE DEBUG"))
    }

    @Test
    fun `QueryType fromIntentCategory maps TECHNICAL_EXPLANATION to TECHNICAL`() {
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("TECHNICAL_EXPLANATION"))
    }

    @Test
    fun `QueryType fromIntentCategory maps SYSTEM to TECHNICAL`() {
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("SYSTEM"))
    }

    @Test
    fun `QueryType fromIntentCategory maps DEVICE_TECH to FACTUAL`() {
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("DEVICE_TECH"))
    }

    @Test
    fun `QueryType fromIntentCategory maps WIFI_NETWORK to FACTUAL`() {
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("WIFI_NETWORK"))
    }

    @Test
    fun `QueryType fromIntentCategory maps SECURITY to FACTUAL`() {
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("SECURITY"))
    }

    @Test
    fun `QueryType fromIntentCategory maps TROUBLESHOOTING to FACTUAL`() {
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("TROUBLESHOOTING"))
    }

    @Test
    fun `QueryType fromIntentCategory maps TRIVIA to FACTUAL`() {
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("TRIVIA"))
    }

    @Test
    fun `QueryType fromIntentCategory maps TECHNOLOGY to FACTUAL`() {
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("TECHNOLOGY"))
    }

    @Test
    fun `QueryType fromIntentCategory maps CONVERSATIONAL to CONVERSATIONAL`() {
        assertEquals(QueryType.CONVERSATIONAL, QueryType.fromIntentCategory("CONVERSATIONAL"))
    }

    @Test
    fun `QueryType fromIntentCategory maps GENERAL to CONVERSATIONAL`() {
        assertEquals(QueryType.CONVERSATIONAL, QueryType.fromIntentCategory("GENERAL"))
    }

    @Test
    fun `QueryType fromIntentCategory maps unknown to CONVERSATIONAL`() {
        assertEquals(QueryType.CONVERSATIONAL, QueryType.fromIntentCategory("UNKNOWN_CATEGORY"))
    }

    @Test
    fun `QueryType fromIntentCategory handles lowercase input`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("history"))
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("math"))
        assertEquals(QueryType.FACTUAL, QueryType.fromIntentCategory("security"))
    }

    @Test
    fun `QueryType fromIntentCategory handles whitespace`() {
        assertEquals(QueryType.EDUCATIONAL, QueryType.fromIntentCategory("  HISTORY  "))
        assertEquals(QueryType.TECHNICAL, QueryType.fromIntentCategory("  MATH  "))
    }

    // ===== GenerationConfigBuilder Token Calculation Tests =====

    @Test
    fun `calculateMaxTokens returns FLAGSHIP tokens for flagship device with EDUCATIONAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val tokens = builder.calculateMaxTokens(QueryType.EDUCATIONAL, 12)
        assertEquals(GenerationConstants.TokenLimits.Educational.FLAGSHIP, tokens)
    }

    @Test
    fun `calculateMaxTokens returns HIGH_END tokens for high-end device with EDUCATIONAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.highEnd())
        val tokens = builder.calculateMaxTokens(QueryType.EDUCATIONAL, 8)
        assertEquals(GenerationConstants.TokenLimits.Educational.HIGH_END, tokens)
    }

    @Test
    fun `calculateMaxTokens returns MID_RANGE tokens for mid-range device with EDUCATIONAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val tokens = builder.calculateMaxTokens(QueryType.EDUCATIONAL, 6)
        assertEquals(GenerationConstants.TokenLimits.Educational.MID_RANGE, tokens)
    }

    @Test
    fun `calculateMaxTokens returns BUDGET tokens for budget device with EDUCATIONAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.budget())
        val tokens = builder.calculateMaxTokens(QueryType.EDUCATIONAL, 4)
        assertEquals(GenerationConstants.TokenLimits.Educational.BUDGET, tokens)
    }

    @Test
    fun `calculateMaxTokens returns correct tokens for TECHNICAL queries`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val tokens = builder.calculateMaxTokens(QueryType.TECHNICAL, 12)
        assertEquals(GenerationConstants.TokenLimits.Technical.FLAGSHIP, tokens)
    }

    @Test
    fun `calculateMaxTokens returns correct tokens for FACTUAL queries`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val tokens = builder.calculateMaxTokens(QueryType.FACTUAL, 12)
        assertEquals(GenerationConstants.TokenLimits.Factual.FLAGSHIP, tokens)
    }

    @Test
    fun `calculateMaxTokens returns correct tokens for CONVERSATIONAL queries`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val tokens = builder.calculateMaxTokens(QueryType.CONVERSATIONAL, 12)
        assertEquals(GenerationConstants.TokenLimits.Conversational.FLAGSHIP, tokens)
    }

    // ===== Temperature Tests =====

    @Test
    fun `getDefaultTemperature returns FOCUSED for EDUCATIONAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        assertEquals(GenerationConstants.Temperature.FOCUSED, builder.getDefaultTemperature(QueryType.EDUCATIONAL))
    }

    @Test
    fun `getDefaultTemperature returns FOCUSED for TECHNICAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        assertEquals(GenerationConstants.Temperature.FOCUSED, builder.getDefaultTemperature(QueryType.TECHNICAL))
    }

    @Test
    fun `getDefaultTemperature returns DEFAULT for FACTUAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        assertEquals(GenerationConstants.Temperature.DEFAULT, builder.getDefaultTemperature(QueryType.FACTUAL))
    }

    @Test
    fun `getDefaultTemperature returns CREATIVE for CONVERSATIONAL`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        assertEquals(GenerationConstants.Temperature.CREATIVE, builder.getDefaultTemperature(QueryType.CONVERSATIONAL))
    }

    // ===== Build Method Tests =====

    @Test
    fun `build creates config with default query type`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.build()

        // Default is CONVERSATIONAL
        assertEquals(GenerationConstants.TokenLimits.Conversational.MID_RANGE, config.maxTokens)
        assertEquals(GenerationConstants.Temperature.CREATIVE, config.temperature)
    }

    @Test
    fun `build uses custom temperature when provided`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.build(queryType = QueryType.EDUCATIONAL, temperature = 0.9f)

        assertEquals(0.9f, config.temperature)
    }

    @Test
    fun `build uses custom maxTokens when provided`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.build(queryType = QueryType.EDUCATIONAL, customMaxTokens = 999)

        assertEquals(999, config.maxTokens)
    }

    @Test
    fun `build respects all custom parameters`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.build(
            queryType = QueryType.TECHNICAL,
            temperature = 0.1f,
            customMaxTokens = 500
        )

        assertEquals(500, config.maxTokens)
        assertEquals(0.1f, config.temperature)
    }

    // ===== BuildFromIntent Tests =====

    @Test
    fun `buildFromIntent creates config for SCIENCE intent`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.highEnd())
        val config = builder.buildFromIntent("SCIENCE")

        // SCIENCE maps to EDUCATIONAL
        assertEquals(GenerationConstants.TokenLimits.Educational.HIGH_END, config.maxTokens)
        assertEquals(GenerationConstants.Temperature.FOCUSED, config.temperature)
    }

    @Test
    fun `buildFromIntent creates config for CODE_DEBUG intent`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val config = builder.buildFromIntent("CODE_DEBUG")

        // CODE_DEBUG maps to TECHNICAL
        assertEquals(GenerationConstants.TokenLimits.Technical.FLAGSHIP, config.maxTokens)
        assertEquals(GenerationConstants.Temperature.FOCUSED, config.temperature)
    }

    @Test
    fun `buildFromIntent creates config for SECURITY intent`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.buildFromIntent("SECURITY")

        // SECURITY maps to FACTUAL
        assertEquals(GenerationConstants.TokenLimits.Factual.MID_RANGE, config.maxTokens)
        assertEquals(GenerationConstants.Temperature.DEFAULT, config.temperature)
    }

    @Test
    fun `buildFromIntent uses custom temperature`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.buildFromIntent("SCIENCE", temperature = 0.8f)

        assertEquals(0.8f, config.temperature)
    }

    @Test
    fun `buildFromIntent uses custom maxTokens`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val config = builder.buildFromIntent("SCIENCE", customMaxTokens = 1500)

        assertEquals(1500, config.maxTokens)
    }

    // ===== Device Tier Tests =====

    @Test
    fun `getDeviceTier returns FLAGSHIP for 12GB device`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        assertEquals(DeviceTier.FLAGSHIP, builder.getDeviceTier())
    }

    @Test
    fun `getDeviceTier returns HIGH_END for 8GB device`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.highEnd())
        assertEquals(DeviceTier.HIGH_END, builder.getDeviceTier())
    }

    @Test
    fun `getDeviceTier returns MID_RANGE for 6GB device`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        assertEquals(DeviceTier.MID_RANGE, builder.getDeviceTier())
    }

    @Test
    fun `getDeviceTier returns BUDGET for 4GB device`() {
        val builder = GenerationConfigBuilder(MockDeviceInfoProvider.budget())
        assertEquals(DeviceTier.BUDGET, builder.getDeviceTier())
    }

    @Test
    fun `getDeviceRamGB returns correct RAM value`() {
        val mock = MockDeviceInfoProvider(ramGB = 10)
        val builder = GenerationConfigBuilder(mock)
        assertEquals(10, builder.getDeviceRamGB())
    }

    // ===== Token Limit Tier Tests (All Query Types x All Device Tiers) =====

    @Test
    fun `TECHNICAL tokens scale correctly by device tier`() {
        val flagshipBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val highEndBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.highEnd())
        val midRangeBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val budgetBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.budget())

        assertTrue(
            flagshipBuilder.calculateMaxTokens(QueryType.TECHNICAL, 12) >
                    highEndBuilder.calculateMaxTokens(QueryType.TECHNICAL, 8)
        )
        assertTrue(
            highEndBuilder.calculateMaxTokens(QueryType.TECHNICAL, 8) >
                    midRangeBuilder.calculateMaxTokens(QueryType.TECHNICAL, 6)
        )
        assertTrue(
            midRangeBuilder.calculateMaxTokens(QueryType.TECHNICAL, 6) >
                    budgetBuilder.calculateMaxTokens(QueryType.TECHNICAL, 4)
        )
    }

    @Test
    fun `FACTUAL tokens scale correctly by device tier`() {
        val flagshipBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val highEndBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.highEnd())
        val midRangeBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val budgetBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.budget())

        assertTrue(
            flagshipBuilder.calculateMaxTokens(QueryType.FACTUAL, 12) >
                    highEndBuilder.calculateMaxTokens(QueryType.FACTUAL, 8)
        )
        assertTrue(
            highEndBuilder.calculateMaxTokens(QueryType.FACTUAL, 8) >
                    midRangeBuilder.calculateMaxTokens(QueryType.FACTUAL, 6)
        )
        assertTrue(
            midRangeBuilder.calculateMaxTokens(QueryType.FACTUAL, 6) >
                    budgetBuilder.calculateMaxTokens(QueryType.FACTUAL, 4)
        )
    }

    @Test
    fun `CONVERSATIONAL tokens scale correctly by device tier`() {
        val flagshipBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.flagship())
        val highEndBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.highEnd())
        val midRangeBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.midRange())
        val budgetBuilder = GenerationConfigBuilder(MockDeviceInfoProvider.budget())

        assertTrue(
            flagshipBuilder.calculateMaxTokens(QueryType.CONVERSATIONAL, 12) >
                    highEndBuilder.calculateMaxTokens(QueryType.CONVERSATIONAL, 8)
        )
        assertTrue(
            highEndBuilder.calculateMaxTokens(QueryType.CONVERSATIONAL, 8) >
                    midRangeBuilder.calculateMaxTokens(QueryType.CONVERSATIONAL, 6)
        )
        assertTrue(
            midRangeBuilder.calculateMaxTokens(QueryType.CONVERSATIONAL, 6) >
                    budgetBuilder.calculateMaxTokens(QueryType.CONVERSATIONAL, 4)
        )
    }

    // ===== Edge Cases =====

    @Test
    fun `calculateMaxTokens handles very low RAM as budget tier`() {
        val mock = MockDeviceInfoProvider(ramGB = 2)
        val builder = GenerationConfigBuilder(mock)

        // 2GB should be treated as budget tier
        assertEquals(
            GenerationConstants.TokenLimits.Educational.BUDGET,
            builder.calculateMaxTokens(QueryType.EDUCATIONAL, 2)
        )
    }

    @Test
    fun `calculateMaxTokens handles very high RAM as flagship tier`() {
        val mock = MockDeviceInfoProvider(ramGB = 24)
        val builder = GenerationConfigBuilder(mock)

        // 24GB should be treated as flagship tier
        assertEquals(
            GenerationConstants.TokenLimits.Educational.FLAGSHIP,
            builder.calculateMaxTokens(QueryType.EDUCATIONAL, 24)
        )
    }

    @Test
    fun `QueryType enum has all expected values`() {
        val values = QueryType.entries
        assertEquals(5, values.size)
        assertTrue(values.contains(QueryType.EDUCATIONAL))
        assertTrue(values.contains(QueryType.TECHNICAL))
        assertTrue(values.contains(QueryType.FACTUAL))
        assertTrue(values.contains(QueryType.CONVERSATIONAL))
        assertTrue(values.contains(QueryType.CREATIVE))
    }
}
