package app.m1k3.ai.assistant.avatar

import app.m1k3.ai.domain.platform.PreferenceKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Avatar Selection feature.
 *
 * TDD: Verify avatar selection model, preference storage, and gallery state.
 */
class AvatarSelectionTest {

    // ===== Preference Key =====

    @Test
    fun `SELECTED_AVATAR preference key exists`() {
        assertEquals("selected_avatar", PreferenceKeys.SELECTED_AVATAR)
    }

    // ===== ModelRegistry =====

    @Test
    fun `ModelRegistry has at least 9 models`() {
        assertTrue(ModelRegistry.allModels.size >= 9)
    }

    @Test
    fun `ModelRegistry default is Colobus`() {
        assertEquals("colobus", ModelRegistry.getDefault().id)
    }

    @Test
    fun `All models have non-blank names`() {
        ModelRegistry.allModels.forEach { model ->
            assertTrue(model.name.isNotBlank(), "Model ${model.id} has blank name")
        }
    }

    @Test
    fun `All models have valid paths`() {
        ModelRegistry.allModels.forEach { model ->
            assertTrue(model.path.endsWith(".glb"), "Model ${model.id} path should end in .glb")
        }
    }

    @Test
    fun `Each model has a unique ID`() {
        val ids = ModelRegistry.allModels.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "Duplicate model IDs found")
    }

    @Test
    fun `Categories are available`() {
        val categories = ModelRegistry.getCategories()
        assertTrue(categories.isNotEmpty())
    }

    @Test
    fun `Can find model by ID`() {
        val sparrow = ModelRegistry.getById("sparrow")
        assertNotNull(sparrow)
        assertEquals("Sparrow", sparrow.name)
    }

    @Test
    fun `Can find models by category`() {
        val mammals = ModelRegistry.getByCategory("mammal")
        assertTrue(mammals.isNotEmpty())
        mammals.forEach { assertEquals("mammal", it.category) }
    }

    // ===== AvatarGalleryState =====

    @Test
    fun `AvatarGalleryState defaults correctly`() {
        val state = AvatarGalleryState()
        assertEquals(ModelRegistry.DEFAULT_MODEL_ID, state.selectedModelId)
        assertTrue(state.models.isEmpty())
        assertNotNull(state.selectedModelId)
    }

    @Test
    fun `AvatarGalleryState can select model`() {
        val state = AvatarGalleryState(
            models = ModelRegistry.allModels,
            selectedModelId = "sparrow"
        )
        assertEquals("sparrow", state.selectedModelId)
    }

    @Test
    fun `AvatarGalleryState selectedModel returns correct config`() {
        val state = AvatarGalleryState(
            models = ModelRegistry.allModels,
            selectedModelId = "gecko"
        )
        assertEquals("Gecko", state.selectedModel?.name)
    }

    @Test
    fun `AvatarGalleryState selectedModel returns null for unknown ID`() {
        val state = AvatarGalleryState(
            models = ModelRegistry.allModels,
            selectedModelId = "nonexistent"
        )
        assertEquals(null, state.selectedModel)
    }
}
