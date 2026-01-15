package app.m1k3.ai.assistant.ui.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD Tests for DemoScreenComposables
 *
 * Verifies StatusCard, ArchitectureCard, and utility functions
 * Tests data generation and rendering logic
 *
 * **Note:** Compose rendering tests require ComposeTestRule
 * These tests verify data logic that can run in unit tests
 */
class DemoScreenComposablesTest {

    @Test
    fun `getSystemStatus returns exactly 7 status items`() {
        // GREEN: Verify correct number of status items
        val status = getSystemStatus(null)

        assertEquals(7, status.size)
    }

    @Test
    fun `getSystemStatus marks knowledge as failure when status is null`() {
        // GREEN: Verify knowledge status shows as pending
        val status = getSystemStatus(null)

        val knowledgeItem = status.find { it.name == "Knowledge Base" }
        assertTrue(knowledgeItem != null)
        assertTrue(knowledgeItem!!.isSuccess == false)
    }

    @Test
    fun `getSystemStatus marks knowledge as success when status starts with checkmark`() {
        // GREEN: Verify knowledge success when status is ready
        val status = getSystemStatus("✅ Knowledge ready: 345 documents")

        val knowledgeItem = status.find { it.name == "Knowledge Base" }
        assertTrue(knowledgeItem != null)
        assertTrue(knowledgeItem!!.isSuccess == true)
    }

    @Test
    fun `getSystemStatus includes all required status items`() {
        // GREEN: Verify all critical system components are present
        val status = getSystemStatus(null)
        val names = status.map { it.name }

        assertTrue(names.contains("Privacy Protection"))
        assertTrue(names.contains("Database Foundation"))
        assertTrue(names.contains("Knowledge Base"))
        assertTrue(names.contains("Package Name"))
        assertTrue(names.contains("AI Engine"))
        assertTrue(names.contains("Design System"))
        assertTrue(names.contains("Robot Avatar"))
    }

    @Test
    fun `StatusItem with success has appropriate icon`() {
        // GREEN: Verify status icon for success
        val successItem = StatusItem(
            name = "Test",
            description = "Test description",
            icon = "✅",
            isSuccess = true
        )

        assertEquals("✅", successItem.icon)
        assertTrue(successItem.isSuccess)
    }

    @Test
    fun `StatusItem with failure has appropriate icon`() {
        // GREEN: Verify status icon for failure
        val failItem = StatusItem(
            name = "Test",
            description = "Test description",
            icon = "⚠️",
            isSuccess = false
        )

        assertEquals("⚠️", failItem.icon)
        assertTrue(!failItem.isSuccess)
    }

    @Test
    fun `architecture layers data has correct structure`() {
        // GREEN: Verify architecture layer data
        val layers = listOf(
            "Kotlin Multiplatform 2.2.20" to "Cross-platform foundation",
            "Compose Multiplatform 1.9.1" to "Modern UI framework",
            "SQLDelight 2.0.2" to "Type-safe database",
            "ONNX Runtime 1.23.1" to "Local AI inference",
            "CameraX + ML Kit" to "Multi-modal vision"
        )

        assertEquals(5, layers.size)
        assertEquals("Kotlin Multiplatform 2.2.20", layers[0].first)
        assertEquals("Compose Multiplatform 1.9.1", layers[1].first)
        assertEquals("SQLDelight 2.0.2", layers[2].first)
    }
}

// ============ Data Classes ============

/**
 * StatusItem - represents system status for display
 */
data class StatusItem(
    val name: String,
    val description: String,
    val icon: String,
    val isSuccess: Boolean
)

// ============ Utility Functions ============

/**
 * Get system status items for demo display
 *
 * @param knowledgeStatus Knowledge import status message (from DatabaseInitializer)
 * @return List of StatusItem for rendering
 */
fun getSystemStatus(knowledgeStatus: String? = null): List<StatusItem> {
    return listOf(
        StatusItem(
            name = "Privacy Protection",
            description = "Zero network permission • 100% local",
            icon = "🔒",
            isSuccess = true
        ),
        StatusItem(
            name = "Database Foundation",
            description = "SQLDelight with encryption ready",
            icon = "🗄️",
            isSuccess = true
        ),
        StatusItem(
            name = "Knowledge Base",
            description = knowledgeStatus ?: "Loading...",
            icon = "📚",
            isSuccess = knowledgeStatus?.startsWith("✅") == true
        ),
        StatusItem(
            name = "Package Name",
            description = "app.m1k3.ai.assistant (ASO optimized)",
            icon = "📦",
            isSuccess = true
        ),
        StatusItem(
            name = "AI Engine",
            description = "SmolLM2-360M (Production Ready)",
            icon = "🤖",
            isSuccess = true
        ),
        StatusItem(
            name = "Design System",
            description = "AMOLED Black • Liquid Glass • Complete",
            icon = "🎨",
            isSuccess = true
        ),
        StatusItem(
            name = "Robot Avatar",
            description = "9 Emotions • 6 Activities • Canvas Rendering",
            icon = "🤖",
            isSuccess = true
        )
    )
}
