package app.m1k3.ai.assistant.eco

import kotlin.test.*

/**
 * TDD RED Phase: EcoCalculator Tests
 *
 * These tests define the expected behavior of the EcoCalculator.
 * They will fail until we implement the actual EcoCalculator class (GREEN phase).
 *
 * Test Coverage:
 * - Baseline savings calculations (per 100 tokens)
 * - Linear scaling for different token counts
 * - Formatting functions (human-readable output)
 * - Achievement milestone detection
 * - Edge cases (0 tokens, negative tokens, large values)
 */
class EcoCalculatorTest {
    // ==================== Savings Calculation Tests ====================

    @Test
    fun `calculateSavings for 100 tokens returns baseline values`() {
        // Arrange
        val tokens = 100

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(100, savings.tokensProcessed, "Tokens processed should match input")
        assertEquals(120, savings.waterSavedMl, "Water saved should be 120ml for 100 tokens")
        assertEquals(3000, savings.energySavedWh, "Energy saved should be 3000 mWh (3 Wh) for 100 tokens")
        assertEquals(2, savings.co2PreventedG, "CO2 prevented should be 2g for 100 tokens")
        assertEquals(0, savings.bytesSent, "Bytes sent should always be 0 (privacy)")
    }

    @Test
    fun `calculateSavings scales linearly for 1000 tokens`() {
        // Arrange
        val tokens = 1000

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(1000, savings.tokensProcessed)
        assertEquals(1200, savings.waterSavedMl, "10x tokens should give 10x water savings")
        assertEquals(30000, savings.energySavedWh, "10x tokens should give 10x energy savings")
        assertEquals(20, savings.co2PreventedG, "10x tokens should give 10x CO2 savings")
    }

    @Test
    fun `calculateSavings for 50 tokens gives half baseline`() {
        // Arrange
        val tokens = 50

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(60, savings.waterSavedMl, "Half tokens should give half water savings")
        assertEquals(1500, savings.energySavedWh, "Half tokens should give half energy savings")
        assertEquals(1, savings.co2PreventedG, "Half tokens should give half CO2 savings")
    }

    @Test
    fun `calculateSavings for 250 tokens scales correctly`() {
        // Arrange
        val tokens = 250

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(300, savings.waterSavedMl, "2.5x tokens should give 2.5x water savings")
        assertEquals(7500, savings.energySavedWh, "2.5x tokens should give 2.5x energy savings")
        assertEquals(5, savings.co2PreventedG, "2.5x tokens should give 2.5x CO2 savings")
    }

    @Test
    fun `inference rows record zero network bytes`() {
        // Chat inference is on-device — rows created by calculateSavings
        // never carry bytes. Real-network rows go through networkEvent.
        val testCases = listOf(1, 50, 100, 500, 1000, 10000)

        testCases.forEach { tokens ->
            val savings = EcoCalculator.calculateSavings(tokens)
            assertEquals(0, savings.bytesSent, "Inference: bytesSent stays 0 for $tokens tokens")
            assertEquals(0, savings.bytesReceived, "Inference: bytesReceived stays 0 for $tokens tokens")
        }
    }

    @Test
    fun `networkEvent creates row with zero inference but real bytes`() {
        val event = EcoCalculator.networkEvent(bytesSent = 1024, bytesReceived = 5_000_000)

        assertEquals(0, event.tokensProcessed, "Network event has no inference tokens")
        assertEquals(0, event.waterSavedMl)
        assertEquals(0, event.energySavedWh)
        assertEquals(0, event.co2PreventedG)
        assertEquals(1024, event.bytesSent)
        assertEquals(5_000_000, event.bytesReceived)
    }

    @Test
    fun `networkEvent rejects negative bytes`() {
        assertFails { EcoCalculator.networkEvent(bytesSent = -1, bytesReceived = 0) }
        assertFails { EcoCalculator.networkEvent(bytesSent = 0, bytesReceived = -1) }
    }

    @Test
    fun `cloudBytesAvoided scales linearly from 100-token baseline`() {
        // 6 KB per 100 tokens — order-of-magnitude OpenAI chat-completion envelope.
        assertEquals(0L, EcoCalculator.cloudBytesAvoided(0L))
        assertEquals(6_000L, EcoCalculator.cloudBytesAvoided(100L))
        assertEquals(60_000L, EcoCalculator.cloudBytesAvoided(1_000L))
        assertEquals(3_000L, EcoCalculator.cloudBytesAvoided(50L))
    }

    @Test
    fun `cloudBytesAvoided rejects negative tokens`() {
        assertFails { EcoCalculator.cloudBytesAvoided(-1L) }
    }

    @Test
    fun `calculateSavings for 0 tokens returns all zeros`() {
        // Arrange
        val tokens = 0

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(0, savings.tokensProcessed)
        assertEquals(0, savings.waterSavedMl)
        assertEquals(0, savings.energySavedWh)
        assertEquals(0, savings.co2PreventedG)
        assertEquals(0, savings.bytesSent)
    }

    // ==================== Formatting Tests ====================

    @Test
    fun `formatWater displays milliliters for small amounts`() {
        // Arrange & Act
        val result100 = EcoCalculator.formatWater(100)
        val result500 = EcoCalculator.formatWater(500)

        // Assert
        assertEquals("100 ml", result100, "Small amounts should show milliliters")
        assertEquals("500 ml", result500)
    }

    @Test
    fun `formatWater displays liters for large amounts`() {
        // Arrange & Act
        val result1000 = EcoCalculator.formatWater(1000)
        val result2500 = EcoCalculator.formatWater(2500)

        // Assert
        assertEquals("1.00 L", result1000, "1000ml should show as 1.00 L")
        assertEquals("2.50 L", result2500, "2500ml should show as 2.50 L")
    }

    @Test
    fun `formatEnergy displays watt-hours correctly`() {
        // Arrange & Act
        val result1500 = EcoCalculator.formatEnergy(1500) // 1.5 Wh
        val result3000 = EcoCalculator.formatEnergy(3000) // 3 Wh

        // Assert
        assertEquals("1.50 Wh", result1500, "1500 mWh should display as 1.50 Wh")
        assertEquals("3.00 Wh", result3000, "3000 mWh should display as 3.00 Wh")
    }

    @Test
    fun `formatEnergy displays kilowatt-hours for large amounts`() {
        // Arrange & Act
        val result1000000 = EcoCalculator.formatEnergy(1000000) // 1 kWh

        // Assert
        assertEquals("1.00 kWh", result1000000, "1,000,000 mWh should display as 1.00 kWh")
    }

    @Test
    fun `formatCO2 displays grams for small amounts`() {
        // Arrange & Act
        val result100 = EcoCalculator.formatCO2(100)
        val result500 = EcoCalculator.formatCO2(500)

        // Assert
        assertEquals("100 g", result100, "Small amounts should show grams")
        assertEquals("500 g", result500)
    }

    @Test
    fun `formatCO2 displays kilograms for large amounts`() {
        // Arrange & Act
        val result1000 = EcoCalculator.formatCO2(1000)
        val result2500 = EcoCalculator.formatCO2(2500)

        // Assert
        assertEquals("1.00 kg", result1000, "1000g should display as 1.00 kg")
        assertEquals("2.50 kg", result2500, "2500g should display as 2.50 kg")
    }

    // ==================== Achievement Tests ====================

    @Test
    fun `getAchievement returns Water Bottle for 500ml`() {
        // Arrange
        val waterSaved = 500

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNotNull(achievement, "Should unlock achievement at 500ml")
        assertEquals(Achievement.WATER_BOTTLE, achievement)
        assertEquals("Saved a water bottle", achievement.title)
        assertEquals("💧", achievement.emoji)
    }

    @Test
    fun `getAchievement returns Bucket for 5000ml`() {
        // Arrange
        val waterSaved = 5000 // 5L

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNotNull(achievement)
        assertEquals(Achievement.BUCKET, achievement)
        assertEquals("Saved a bucket", achievement.title)
        assertEquals("🪣", achievement.emoji)
    }

    @Test
    fun `getAchievement returns Bathtub for 100000ml`() {
        // Arrange
        val waterSaved = 100000 // 100L

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNotNull(achievement)
        assertEquals(Achievement.BATHTUB, achievement)
        assertEquals("Saved a bathtub", achievement.title)
        assertEquals("🛁", achievement.emoji)
    }

    @Test
    fun `getAchievement returns Pool for 1000000ml`() {
        // Arrange
        val waterSaved = 1000000 // 1000L

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNotNull(achievement)
        assertEquals(Achievement.POOL, achievement)
        assertEquals("Saved a swimming pool", achievement.title)
        assertEquals("🏊", achievement.emoji)
    }

    @Test
    fun `getAchievement returns Olympic Pool for 2500000ml`() {
        // Arrange
        val waterSaved = 2500000 // 2500L

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNotNull(achievement)
        assertEquals(Achievement.OLYMPIC_POOL, achievement)
        assertEquals("Saved an Olympic pool", achievement.title)
        assertEquals("🏅", achievement.emoji)
    }

    @Test
    fun `getAchievement returns null for amounts below threshold`() {
        // Arrange
        val waterSaved = 100 // Below 500ml threshold

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNull(achievement, "Should return null when below minimum threshold")
    }

    @Test
    fun `getAchievement returns highest unlocked achievement`() {
        // Arrange - User has saved 10000ml, which unlocks Bucket but not Bathtub
        val waterSaved = 10000

        // Act
        val achievement = EcoCalculator.getAchievement(waterSaved)

        // Assert
        assertNotNull(achievement)
        assertEquals(Achievement.BUCKET, achievement, "Should return Bucket achievement for 10L")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `calculateSavings handles large token counts`() {
        // Arrange
        val tokens = 1000000 // 1 million tokens

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(1000000, savings.tokensProcessed)
        assertEquals(1200000, savings.waterSavedMl, "Should handle large values without overflow")
        assertTrue(savings.energySavedWh > 0, "Energy should be positive")
        assertTrue(savings.co2PreventedG > 0, "CO2 should be positive")
    }

    @Test
    fun `formatWater handles 0ml`() {
        // Arrange & Act
        val result = EcoCalculator.formatWater(0)

        // Assert
        assertEquals("0 ml", result)
    }

    @Test
    fun `formatEnergy handles 0 mWh`() {
        // Arrange & Act
        val result = EcoCalculator.formatEnergy(0)

        // Assert
        assertEquals("0.00 Wh", result)
    }

    @Test
    fun `formatCO2 handles 0g`() {
        // Arrange & Act
        val result = EcoCalculator.formatCO2(0)

        // Assert
        assertEquals("0 g", result)
    }

    // ==================== Realistic Usage Scenarios ====================

    @Test
    fun `typical short query scenario - 50 tokens`() {
        // Scenario: User asks "What is the capital of France?" (short query)
        // Arrange
        val tokens = 50 // ~25 input + ~25 output

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(60, savings.waterSavedMl, "Short query should save ~60ml water")
        assertEquals(1500, savings.energySavedWh, "Short query should save ~1.5 Wh")
        assertEquals(1, savings.co2PreventedG, "Short query should prevent ~1g CO2")

        // Verify formatting
        assertEquals("60 ml", EcoCalculator.formatWater(savings.waterSavedMl))
        assertEquals("1.50 Wh", EcoCalculator.formatEnergy(savings.energySavedWh))
    }

    @Test
    fun `typical medium query scenario - 200 tokens`() {
        // Scenario: User asks for code explanation (medium query)
        // Arrange
        val tokens = 200 // ~50 input + ~150 output

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(240, savings.waterSavedMl, "Medium query should save ~240ml water")
        assertEquals(6000, savings.energySavedWh, "Medium query should save ~6 Wh")
        assertEquals(4, savings.co2PreventedG, "Medium query should prevent ~4g CO2")
    }

    @Test
    fun `typical long query scenario - 500 tokens`() {
        // Scenario: User requests detailed explanation or code generation
        // Arrange
        val tokens = 500 // ~100 input + ~400 output

        // Act
        val savings = EcoCalculator.calculateSavings(tokens)

        // Assert
        assertEquals(600, savings.waterSavedMl, "Long query should save ~600ml water")
        assertEquals(15000, savings.energySavedWh, "Long query should save ~15 Wh")
        assertEquals(10, savings.co2PreventedG, "Long query should prevent ~10g CO2")

        // User should unlock Water Bottle achievement after this query
        val achievement = EcoCalculator.getAchievement(savings.waterSavedMl)
        assertNotNull(achievement, "Should unlock achievement after 600ml saved")
    }

    @Test
    fun `daily usage scenario - 20 queries averaging 150 tokens each`() {
        // Scenario: Typical user has 20 interactions per day
        // Arrange
        val queriesPerDay = 20
        val avgTokensPerQuery = 150
        val totalTokens = queriesPerDay * avgTokensPerQuery // 3000 tokens

        // Act
        val dailySavings = EcoCalculator.calculateSavings(totalTokens)

        // Assert
        assertEquals(3600, dailySavings.waterSavedMl, "Daily usage should save ~3.6L")
        assertEquals(90000, dailySavings.energySavedWh, "Daily usage should save ~90 Wh")
        assertEquals(60, dailySavings.co2PreventedG, "Daily usage should prevent ~60g CO2")

        // Format for user display
        assertEquals("3.60 L", EcoCalculator.formatWater(dailySavings.waterSavedMl))
        assertEquals("90.00 Wh", EcoCalculator.formatEnergy(dailySavings.energySavedWh))
    }
}
