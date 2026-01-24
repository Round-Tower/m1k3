package app.m1k3.ai.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for DeviceTier enum.
 */
class DeviceTierTest {

    @Test
    fun `contains all expected tiers`() {
        val expectedTiers = setOf(
            "FLAGSHIP",
            "HIGH_END",
            "MID_RANGE",
            "BUDGET",
            "LOW_END"
        )

        val actualTiers = DeviceTier.entries.map { it.name }.toSet()

        assertEquals(expectedTiers, actualTiers)
    }

    @Test
    fun `has exactly 5 tiers`() {
        assertEquals(5, DeviceTier.entries.size)
    }

    @Test
    fun `ordinal order matches capability hierarchy`() {
        // FLAGSHIP should be first (highest capability)
        assertEquals(0, DeviceTier.FLAGSHIP.ordinal)
        assertEquals(1, DeviceTier.HIGH_END.ordinal)
        assertEquals(2, DeviceTier.MID_RANGE.ordinal)
        assertEquals(3, DeviceTier.BUDGET.ordinal)
        assertEquals(4, DeviceTier.LOW_END.ordinal)
    }

    @Test
    fun `valueOf returns correct enum for valid names`() {
        assertEquals(DeviceTier.FLAGSHIP, DeviceTier.valueOf("FLAGSHIP"))
        assertEquals(DeviceTier.BUDGET, DeviceTier.valueOf("BUDGET"))
    }
}
