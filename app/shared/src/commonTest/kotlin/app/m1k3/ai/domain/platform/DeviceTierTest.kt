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
        val expectedTiers =
            setOf(
                "FLAGSHIP",
                "HIGH_END",
                "MID_RANGE",
                "BUDGET",
                "LOW_END",
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

    @Test
    fun `fromRamGB returns FLAGSHIP at 12GB and above`() {
        assertEquals(DeviceTier.FLAGSHIP, DeviceTier.fromRamGB(12))
        assertEquals(DeviceTier.FLAGSHIP, DeviceTier.fromRamGB(16))
        assertEquals(DeviceTier.FLAGSHIP, DeviceTier.fromRamGB(24))
    }

    @Test
    fun `fromRamGB returns HIGH_END at 8GB to 11GB`() {
        assertEquals(DeviceTier.HIGH_END, DeviceTier.fromRamGB(8))
        assertEquals(DeviceTier.HIGH_END, DeviceTier.fromRamGB(10))
        assertEquals(DeviceTier.HIGH_END, DeviceTier.fromRamGB(11))
    }

    @Test
    fun `fromRamGB returns MID_RANGE at 6GB to 7GB`() {
        assertEquals(DeviceTier.MID_RANGE, DeviceTier.fromRamGB(6))
        assertEquals(DeviceTier.MID_RANGE, DeviceTier.fromRamGB(7))
    }

    @Test
    fun `fromRamGB returns BUDGET at 4GB to 5GB`() {
        assertEquals(DeviceTier.BUDGET, DeviceTier.fromRamGB(4))
        assertEquals(DeviceTier.BUDGET, DeviceTier.fromRamGB(5))
    }

    @Test
    fun `fromRamGB returns LOW_END below 4GB`() {
        assertEquals(DeviceTier.LOW_END, DeviceTier.fromRamGB(3))
        assertEquals(DeviceTier.LOW_END, DeviceTier.fromRamGB(2))
        assertEquals(DeviceTier.LOW_END, DeviceTier.fromRamGB(1))
        assertEquals(DeviceTier.LOW_END, DeviceTier.fromRamGB(0))
    }

    @Test
    fun `fromRamGB clamps negative RAM to LOW_END`() {
        assertEquals(DeviceTier.LOW_END, DeviceTier.fromRamGB(-1))
    }
}
