package app.m1k3.ai.domain.ai

import app.m1k3.ai.domain.platform.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD Tests for M1K3Tier.
 *
 * Verifies tier properties and device-tier mapping.
 */
class M1K3TierTest {

    // ===== Mini M1K3 =====

    @Test
    fun `Mini has correct display name`() {
        assertEquals("Mini M1K3", M1K3Tier.Mini.displayName)
    }

    @Test
    fun `Mini uses Gemma3_270M model`() {
        assertIs<LlmModel.Gemma3_270M>(M1K3Tier.Mini.model)
    }

    @Test
    fun `Mini download size is reasonable`() {
        assertTrue(M1K3Tier.Mini.downloadSizeMb in 100..500,
            "Mini download should be 100–500MB, was ${M1K3Tier.Mini.downloadSizeMb}MB")
    }

    // ===== Lil M1K3 =====

    @Test
    fun `Lil has correct display name`() {
        assertEquals("Lil M1K3", M1K3Tier.Lil.displayName)
    }

    @Test
    fun `Lil uses Gemma3_1B model`() {
        assertIs<LlmModel.Gemma3_1B>(M1K3Tier.Lil.model)
    }

    @Test
    fun `Lil download size is reasonable`() {
        assertTrue(M1K3Tier.Lil.downloadSizeMb in 400..900,
            "Lil download should be 400–900MB, was ${M1K3Tier.Lil.downloadSizeMb}MB")
    }

    // ===== Big M1K3 =====

    @Test
    fun `Big has correct display name`() {
        assertEquals("Big M1K3", M1K3Tier.Big.displayName)
    }

    @Test
    fun `Big uses Gemma4_E2B model`() {
        assertIs<LlmModel.Gemma4_E2B>(M1K3Tier.Big.model)
    }

    @Test
    fun `Big download size is the largest`() {
        assertTrue(M1K3Tier.Big.downloadSizeMb > M1K3Tier.Lil.downloadSizeMb,
            "Big should require a larger download than Lil")
    }

    // ===== Device mapping =====

    @Test
    fun `FLAGSHIP maps to Big M1K3`() {
        assertEquals(M1K3Tier.Big, M1K3Tier.forDevice(DeviceTier.FLAGSHIP))
    }

    @Test
    fun `HIGH_END maps to Big M1K3`() {
        assertEquals(M1K3Tier.Big, M1K3Tier.forDevice(DeviceTier.HIGH_END))
    }

    @Test
    fun `MID_RANGE maps to Lil M1K3`() {
        assertEquals(M1K3Tier.Lil, M1K3Tier.forDevice(DeviceTier.MID_RANGE))
    }

    @Test
    fun `BUDGET maps to Lil M1K3`() {
        assertEquals(M1K3Tier.Lil, M1K3Tier.forDevice(DeviceTier.BUDGET))
    }

    @Test
    fun `LOW_END maps to Mini M1K3`() {
        assertEquals(M1K3Tier.Mini, M1K3Tier.forDevice(DeviceTier.LOW_END))
    }

    @Test
    fun `all returns all three tiers`() {
        val tiers = M1K3Tier.all()
        assertEquals(3, tiers.size)
        assertTrue(tiers.contains(M1K3Tier.Mini))
        assertTrue(tiers.contains(M1K3Tier.Lil))
        assertTrue(tiers.contains(M1K3Tier.Big))
    }

    @Test
    fun `each tier has non-empty display name, tagline, and description`() {
        M1K3Tier.all().forEach { tier ->
            assertTrue(tier.displayName.isNotEmpty(), "displayName empty for $tier")
            assertTrue(tier.tagline.isNotEmpty(), "tagline empty for $tier")
            assertTrue(tier.description.isNotEmpty(), "description empty for $tier")
        }
    }
}
