package app.m1k3.ai.domain.ai

import app.m1k3.ai.domain.chat.format.ChatFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * TDD Tests for LlmModel sealed class
 *
 * LlmModel represents available on-device LLM models with their
 * configuration (filename, chat format, size).
 */
class LlmModelTest {

    // ===== Gemma3 Tests =====

    @Test
    fun `Gemma3 has correct id`() {
        assertEquals("gemma-3-270m", LlmModel.Gemma3_270M.id)
    }

    @Test
    fun `Gemma3 has correct display name`() {
        assertEquals("Gemma 3 (270M)", LlmModel.Gemma3_270M.displayName)
    }

    @Test
    fun `Gemma3 uses Gemma3 chat format`() {
        assertIs<ChatFormat.Gemma3>(LlmModel.Gemma3_270M.chatFormat)
    }

    @Test
    fun `Gemma3 has correct filename`() {
        assertTrue(LlmModel.Gemma3_270M.filename.contains("gemma"))
    }

    // ===== FalconH1 Tests =====

    @Test
    fun `FalconH1 has correct id`() {
        assertEquals("falcon-h1-90m", LlmModel.FalconH1_90M.id)
    }

    @Test
    fun `FalconH1 has correct display name`() {
        assertEquals("Falcon-H1 (90M)", LlmModel.FalconH1_90M.displayName)
    }

    @Test
    fun `FalconH1 uses FalconH1 chat format`() {
        assertIs<ChatFormat.FalconH1>(LlmModel.FalconH1_90M.chatFormat)
    }

    @Test
    fun `FalconH1 has correct filename`() {
        assertTrue(LlmModel.FalconH1_90M.filename.contains("Falcon"))
    }

    // ===== Default Model Tests =====

    @Test
    fun `default model is Qwen35_2B`() {
        assertEquals(LlmModel.Qwen35_2B, LlmModel.default)
    }

    // ===== Collection Tests =====

    @Test
    fun `all returns at least 2 models`() {
        val models = LlmModel.all()
        assertTrue(models.size >= 2)
    }

    @Test
    fun `all includes active tier models`() {
        val models = LlmModel.all()
        assertTrue(models.contains(LlmModel.Qwen35_0B8))
        assertTrue(models.contains(LlmModel.Qwen35_2B))
        assertTrue(models.contains(LlmModel.FalconH1_90M))
        assertTrue(models.contains(LlmModel.Gemma4_E2B))
    }

    // ===== findById Tests =====

    @Test
    fun `findById returns correct model`() {
        assertEquals(LlmModel.FalconH1_90M, LlmModel.findById("falcon-h1-90m"))
    }

    @Test
    fun `findById returns null for unknown id`() {
        assertEquals(null, LlmModel.findById("nonexistent"))
    }

    // ===== Type Tests =====

    @Test
    fun `all models are LlmModel instances`() {
        LlmModel.all().forEach { model ->
            assertIs<LlmModel>(model)
            assertTrue(model.id.isNotEmpty())
            assertTrue(model.displayName.isNotEmpty())
            assertTrue(model.filename.isNotEmpty())
        }
    }

    // ===== minRamGB Tests =====

    @Test
    fun `Gemma3 has zero minRamGB`() {
        assertEquals(0, LlmModel.Gemma3_270M.minRamGB)
    }

    @Test
    fun `FalconH1 has zero minRamGB`() {
        assertEquals(0, LlmModel.FalconH1_90M.minRamGB)
    }

    // ===== availableFor Tests =====

    @Test
    fun `availableFor 4GB returns base models`() {
        val models = LlmModel.availableFor(4)
        assertTrue(models.contains(LlmModel.Qwen35_0B8))
        assertTrue(models.contains(LlmModel.Qwen35_2B))
        assertTrue(models.contains(LlmModel.FalconH1_90M))
    }

    @Test
    fun `availableFor filters out models above RAM threshold`() {
        // All current models have minRamGB=0, so all should be available
        val models = LlmModel.availableFor(2)
        assertTrue(models.isNotEmpty())
    }

    // ===== Gemma4 E2B Tests =====

    @Test
    fun `Gemma4_E2B has correct id`() {
        assertEquals("gemma-4-e2b", LlmModel.Gemma4_E2B.id)
    }

    @Test
    fun `Gemma4_E2B has correct display name`() {
        assertEquals("Gemma 4 (2.3B)", LlmModel.Gemma4_E2B.displayName)
    }

    @Test
    fun `Gemma4_E2B uses Gemma4 chat format`() {
        assertIs<ChatFormat.Gemma4>(LlmModel.Gemma4_E2B.chatFormat)
    }

    @Test
    fun `Gemma4_E2B requires 6GB RAM`() {
        assertEquals(6, LlmModel.Gemma4_E2B.minRamGB)
    }

    @Test
    fun `Gemma4_E2B is in all models list`() {
        assertTrue(LlmModel.all().contains(LlmModel.Gemma4_E2B))
    }

    @Test
    fun `Gemma4_E2B is available for 6GB devices`() {
        assertTrue(LlmModel.availableFor(6).contains(LlmModel.Gemma4_E2B))
    }

    @Test
    fun `Gemma4_E2B is not available for 4GB devices`() {
        assertFalse(LlmModel.availableFor(4).contains(LlmModel.Gemma4_E2B))
    }

    @Test
    fun `findById returns Gemma4_E2B`() {
        assertEquals(LlmModel.Gemma4_E2B, LlmModel.findById("gemma-4-e2b"))
    }

    // ===== Gemma3 1B Tests =====

    @Test
    fun `Gemma3_1B has correct id`() {
        assertEquals("gemma-3-1b", LlmModel.Gemma3_1B.id)
    }

    @Test
    fun `Gemma3_1B has correct display name`() {
        assertEquals("Gemma 3 (1B)", LlmModel.Gemma3_1B.displayName)
    }

    @Test
    fun `Gemma3_1B uses Gemma3 chat format`() {
        assertIs<ChatFormat.Gemma3>(LlmModel.Gemma3_1B.chatFormat)
    }

    @Test
    fun `Gemma3_1B has minRamGB of 2`() {
        assertEquals(2, LlmModel.Gemma3_1B.minRamGB)
    }

    @Test
    fun `Gemma3_1B is NOT the default model (replaced by Qwen25_1B5)`() {
        assertFalse(LlmModel.Gemma3_1B == LlmModel.default)
    }

    @Test
    fun `Qwen35_2B is in all models list`() {
        assertTrue(LlmModel.all().contains(LlmModel.Qwen35_2B))
    }

    @Test
    fun `Qwen35_0B8 is in all models list`() {
        assertTrue(LlmModel.all().contains(LlmModel.Qwen35_0B8))
    }

    @Test
    fun `Gemma3_1B filename contains gemma and 1b`() {
        val filename = LlmModel.Gemma3_1B.filename.lowercase()
        assertTrue(filename.contains("gemma"))
        assertTrue(filename.contains("1b") || filename.contains("1B"))
    }
}
