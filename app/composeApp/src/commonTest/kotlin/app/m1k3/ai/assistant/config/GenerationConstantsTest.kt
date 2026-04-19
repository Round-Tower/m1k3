package app.m1k3.ai.assistant.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for GenerationConstants.
 *
 * Validates that all constants maintain expected relationships:
 * - Token limits decrease as device tier decreases
 * - Memory topK scales appropriately with device capability
 * - Similarity thresholds maintain proper ordering
 */
class GenerationConstantsTest {
    // ===== Device RAM Thresholds =====

    @Test
    fun `device RAM thresholds decrease in order`() {
        assertTrue(
            GenerationConstants.DeviceRam.FLAGSHIP > GenerationConstants.DeviceRam.HIGH_END,
            "Flagship should have more RAM than high-end",
        )
        assertTrue(
            GenerationConstants.DeviceRam.HIGH_END > GenerationConstants.DeviceRam.MID_RANGE,
            "High-end should have more RAM than mid-range",
        )
        assertTrue(
            GenerationConstants.DeviceRam.MID_RANGE > GenerationConstants.DeviceRam.BUDGET,
            "Mid-range should have more RAM than budget",
        )
    }

    @Test
    fun `device RAM thresholds have expected values`() {
        assertEquals(12, GenerationConstants.DeviceRam.FLAGSHIP)
        assertEquals(8, GenerationConstants.DeviceRam.HIGH_END)
        assertEquals(6, GenerationConstants.DeviceRam.MID_RANGE)
        assertEquals(4, GenerationConstants.DeviceRam.BUDGET)
    }

    // ===== Memory TopK =====

    @Test
    fun `memory topK scales with device capability`() {
        with(GenerationConstants.MemoryTopK) {
            assertTrue(FLAGSHIP > HIGH_END, "Flagship should retrieve more memories")
            assertTrue(HIGH_END > MID_RANGE, "High-end should retrieve more than mid-range")
            assertTrue(MID_RANGE > BUDGET, "Mid-range should retrieve more than budget")
        }
    }

    @Test
    fun `memory topK has expected values`() {
        assertEquals(20, GenerationConstants.MemoryTopK.FLAGSHIP)
        assertEquals(15, GenerationConstants.MemoryTopK.HIGH_END)
        assertEquals(10, GenerationConstants.MemoryTopK.MID_RANGE)
        assertEquals(5, GenerationConstants.MemoryTopK.BUDGET)
    }

    // ===== Similarity Thresholds =====

    @Test
    fun `similarity thresholds are properly ordered`() {
        with(GenerationConstants.Similarity) {
            assertTrue(HIGH_QUALITY > MEDIUM_QUALITY, "High quality threshold should be higher")
            assertTrue(MEDIUM_QUALITY > MINIMUM, "Medium quality should be above minimum")
        }
    }

    @Test
    fun `similarity thresholds have expected values`() {
        assertEquals(0.7f, GenerationConstants.Similarity.HIGH_QUALITY)
        assertEquals(0.6f, GenerationConstants.Similarity.MEDIUM_QUALITY)
        assertEquals(0.5f, GenerationConstants.Similarity.MINIMUM)
    }

    @Test
    fun `similarity thresholds are valid percentages`() {
        with(GenerationConstants.Similarity) {
            assertTrue(HIGH_QUALITY in 0f..1f, "High quality should be between 0 and 1")
            assertTrue(MEDIUM_QUALITY in 0f..1f, "Medium quality should be between 0 and 1")
            assertTrue(MINIMUM in 0f..1f, "Minimum should be between 0 and 1")
        }
    }

    // ===== System Prompt Hints =====

    @Test
    fun `system prompt hints are not empty`() {
        with(GenerationConstants.SystemPromptHints) {
            assertTrue(EDUCATIONAL.isNotBlank(), "Educational hint should not be empty")
            assertTrue(TECHNICAL.isNotBlank(), "Technical hint should not be empty")
            assertTrue(FACTUAL.isNotBlank(), "Factual hint should not be empty")
            assertTrue(CONVERSATIONAL.isNotBlank(), "Conversational hint should not be empty")
        }
    }

    @Test
    fun `educational hint discourages asking questions back`() {
        assertTrue(
            GenerationConstants.SystemPromptHints.EDUCATIONAL.contains("NOT ask questions"),
            "Educational hint should discourage model from asking questions",
        )
    }

    // ===== Temperature =====

    @Test
    fun `temperature values are valid`() {
        with(GenerationConstants.Temperature) {
            assertTrue(DEFAULT in 0f..1f, "Default temperature should be between 0 and 1")
            assertTrue(CREATIVE in 0f..1f, "Creative temperature should be between 0 and 1")
            assertTrue(FOCUSED in 0f..1f, "Focused temperature should be between 0 and 1")
        }
    }

    @Test
    fun `creative temperature is higher than focused`() {
        assertTrue(
            GenerationConstants.Temperature.CREATIVE > GenerationConstants.Temperature.FOCUSED,
            "Creative should have higher temperature than focused",
        )
    }

    @Test
    fun `default temperature is between focused and creative`() {
        with(GenerationConstants.Temperature) {
            assertTrue(DEFAULT > FOCUSED, "Default should be higher than focused")
            assertTrue(DEFAULT < CREATIVE, "Default should be lower than creative")
        }
    }

    // ===== Memory Preview =====

    @Test
    fun `memory preview max length is positive`() {
        assertTrue(
            GenerationConstants.MemoryPreview.MAX_CONTENT_LENGTH > 0,
            "Max content length should be positive",
        )
    }

    @Test
    fun `memory preview max length has expected value`() {
        assertEquals(200, GenerationConstants.MemoryPreview.MAX_CONTENT_LENGTH)
    }
}
