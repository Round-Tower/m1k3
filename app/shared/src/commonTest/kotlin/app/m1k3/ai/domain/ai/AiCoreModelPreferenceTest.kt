package app.m1k3.ai.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TDD Tests for AiCoreModelPreference
 *
 * Represents AICore model selection for ML Kit GenAI:
 * - STABLE: Production Gemini Nano
 * - PREVIEW_SPEED: Gemma 4 E2B (fast, 3x speed)
 * - PREVIEW_FULL: Gemma 4 E4B (highest quality)
 */
class AiCoreModelPreferenceTest {

    @Test
    fun `STABLE has correct display name`() {
        assertEquals("Gemini Nano", AiCoreModelPreference.STABLE.displayName)
    }

    @Test
    fun `PREVIEW_SPEED has correct display name`() {
        assertEquals("Gemma 4 E2B", AiCoreModelPreference.PREVIEW_SPEED.displayName)
    }

    @Test
    fun `PREVIEW_FULL has correct display name`() {
        assertEquals("Gemma 4 E4B", AiCoreModelPreference.PREVIEW_FULL.displayName)
    }

    @Test
    fun `STABLE is not preview`() {
        assertFalse(AiCoreModelPreference.STABLE.isPreview)
    }

    @Test
    fun `PREVIEW_SPEED is preview`() {
        assertTrue(AiCoreModelPreference.PREVIEW_SPEED.isPreview)
    }

    @Test
    fun `PREVIEW_FULL is preview`() {
        assertTrue(AiCoreModelPreference.PREVIEW_FULL.isPreview)
    }

    @Test
    fun `all entries returns three preferences`() {
        assertEquals(3, AiCoreModelPreference.entries.size)
    }
}
