package app.m1k3.ai.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD Tests for ModelDownloadManager interface
 *
 * Verifies the contract for downloading and managing GGUF model files.
 */
class ModelDownloadManagerTest {

    private val fakeManager = FakeModelDownloadManager()

    @Test
    fun `isModelAvailable returns false for undownloaded model`() {
        assertFalse(fakeManager.isModelAvailable("gemma-4-e2b"))
    }

    @Test
    fun `isModelAvailable returns true after marking as available`() {
        fakeManager.markAvailable("gemma-4-e2b", "/path/to/model.gguf")
        assertTrue(fakeManager.isModelAvailable("gemma-4-e2b"))
    }

    @Test
    fun `getModelPath returns null for undownloaded model`() {
        assertEquals(null, fakeManager.getModelPath("gemma-4-e2b"))
    }

    @Test
    fun `getModelPath returns path for downloaded model`() {
        fakeManager.markAvailable("gemma-4-e2b", "/data/models/gemma4.gguf")
        assertEquals("/data/models/gemma4.gguf", fakeManager.getModelPath("gemma-4-e2b"))
    }

    @Test
    fun `deleteModel removes availability`() {
        fakeManager.markAvailable("gemma-4-e2b", "/path/model.gguf")
        assertTrue(fakeManager.deleteModel("gemma-4-e2b"))
        assertFalse(fakeManager.isModelAvailable("gemma-4-e2b"))
    }

    @Test
    fun `deleteModel returns false for unknown model`() {
        assertFalse(fakeManager.deleteModel("nonexistent"))
    }
}

/**
 * Fake implementation for testing the contract.
 */
private class FakeModelDownloadManager : ModelDownloadManager {
    private val models = mutableMapOf<String, String>()

    fun markAvailable(modelId: String, path: String) {
        models[modelId] = path
    }

    override fun isModelAvailable(modelId: String): Boolean = models.containsKey(modelId)

    override fun getModelPath(modelId: String): String? = models[modelId]

    override fun deleteModel(modelId: String): Boolean {
        return models.remove(modelId) != null
    }
}
