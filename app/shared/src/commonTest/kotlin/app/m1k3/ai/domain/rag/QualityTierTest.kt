package app.m1k3.ai.domain.rag

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * QualityTier Tests
 *
 * Tests for similarity-based quality classification.
 */
class QualityTierTest {

    @Test
    fun `HIGH tier for similarity 0_8 and above`() {
        assertEquals(QualityTier.HIGH, QualityTier.fromSimilarity(0.8f))
        assertEquals(QualityTier.HIGH, QualityTier.fromSimilarity(0.9f))
        assertEquals(QualityTier.HIGH, QualityTier.fromSimilarity(1.0f))
    }

    @Test
    fun `MEDIUM tier for similarity 0_6 to 0_8`() {
        assertEquals(QualityTier.MEDIUM, QualityTier.fromSimilarity(0.6f))
        assertEquals(QualityTier.MEDIUM, QualityTier.fromSimilarity(0.7f))
        assertEquals(QualityTier.MEDIUM, QualityTier.fromSimilarity(0.79f))
    }

    @Test
    fun `LOW tier for similarity below 0_6`() {
        assertEquals(QualityTier.LOW, QualityTier.fromSimilarity(0.59f))
        assertEquals(QualityTier.LOW, QualityTier.fromSimilarity(0.5f))
        assertEquals(QualityTier.LOW, QualityTier.fromSimilarity(0.0f))
    }

    @Test
    fun `isHighQuality helper returns correct values`() {
        assertEquals(true, QualityTier.isHighQuality(0.8f))
        assertEquals(true, QualityTier.isHighQuality(0.9f))
        assertEquals(false, QualityTier.isHighQuality(0.79f))
        assertEquals(false, QualityTier.isHighQuality(0.5f))
    }

    @Test
    fun `isMediumQuality helper returns correct values`() {
        assertEquals(true, QualityTier.isMediumQuality(0.6f))
        assertEquals(true, QualityTier.isMediumQuality(0.7f))
        assertEquals(true, QualityTier.isMediumQuality(0.8f)) // HIGH is also MEDIUM+
        assertEquals(false, QualityTier.isMediumQuality(0.59f))
    }
}
