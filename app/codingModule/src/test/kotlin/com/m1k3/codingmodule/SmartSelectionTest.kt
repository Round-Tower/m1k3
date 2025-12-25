package com.m1k3.codingmodule

import domain.coding.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Smart Model Auto-Selection Tests
 *
 * Validates the intelligent model routing system that automatically
 * selects the best AI model for each template type.
 *
 * Test Coverage:
 * - Model selection logic for all 4 template types
 * - Selection reason generation
 * - Model comparison and metadata
 * - Engine swapping behavior (simulated)
 *
 * Selection Rules (verified):
 * - QUIZ → SmolLM2-360M (creative, varied questions)
 * - PRESENTATION → SmolLM2-360M (storytelling, visual flow)
 * - SVG_CHART → Qwen2.5-Coder (data handling, calculations)
 * - GAME → Qwen2.5-Coder (game logic, algorithms)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SmartSelectionTest {

    // ===== Model Selection Tests =====

    @Test
    fun `test auto-selects SmolLM2 for quiz template`() {
        val selectedModel = ModelRegistry.getBestModelForTemplate(TemplateType.QUIZ)

        assertEquals(CodingModel.SMOL_LM2_360M, selectedModel)
    }

    @Test
    fun `test auto-selects SmolLM2 for presentation template`() {
        val selectedModel = ModelRegistry.getBestModelForTemplate(TemplateType.PRESENTATION)

        assertEquals(CodingModel.SMOL_LM2_360M, selectedModel)
    }

    @Test
    fun `test auto-selects Qwen for SVG chart template`() {
        val selectedModel = ModelRegistry.getBestModelForTemplate(TemplateType.SVG_CHART)

        assertEquals(CodingModel.QWEN_CODER_0_5B, selectedModel)
    }

    @Test
    fun `test auto-selects Qwen for game template`() {
        val selectedModel = ModelRegistry.getBestModelForTemplate(TemplateType.GAME)

        assertEquals(CodingModel.QWEN_CODER_0_5B, selectedModel)
    }

    // ===== Selection Reasoning Tests =====

    @Test
    fun `test selection reason for SmolLM2 with quiz`() {
        val reason = ModelRegistry.getSelectionReason(
            CodingModel.SMOL_LM2_360M,
            TemplateType.QUIZ
        )

        assertEquals("Best for creative, varied questions", reason)
    }

    @Test
    fun `test selection reason for SmolLM2 with presentation`() {
        val reason = ModelRegistry.getSelectionReason(
            CodingModel.SMOL_LM2_360M,
            TemplateType.PRESENTATION
        )

        assertEquals("Best for storytelling and visual flow", reason)
    }

    @Test
    fun `test selection reason for Qwen with SVG chart`() {
        val reason = ModelRegistry.getSelectionReason(
            CodingModel.QWEN_CODER_0_5B,
            TemplateType.SVG_CHART
        )

        assertEquals("Best for data handling and calculations", reason)
    }

    @Test
    fun `test selection reason for Qwen with game`() {
        val reason = ModelRegistry.getSelectionReason(
            CodingModel.QWEN_CODER_0_5B,
            TemplateType.GAME
        )

        assertEquals("Best for game logic and algorithms", reason)
    }

    // ===== Model Metadata Tests =====

    @Test
    fun `test SmolLM2 model info`() {
        val modelInfo = ModelRegistry.getModelInfo(CodingModel.SMOL_LM2_360M)

        assertEquals("SmolLM2-360M-Instruct", modelInfo.name)
        assertEquals("360M", modelInfo.version)
        assertEquals(180 * 1024 * 1024L, modelInfo.sizeBytes) // 180MB
        assertEquals(8192, modelInfo.contextWindow) // 8K tokens
        assertTrue(modelInfo.capabilities.contains("Creative content generation"))
    }

    @Test
    fun `test Qwen model info`() {
        val modelInfo = ModelRegistry.getModelInfo(CodingModel.QWEN_CODER_0_5B)

        assertEquals("Qwen2.5-Coder-0.5B-Instruct", modelInfo.name)
        assertEquals("0.5B", modelInfo.version)
        assertEquals(120 * 1024 * 1024L, modelInfo.sizeBytes) // 120MB
        assertEquals(32768, modelInfo.contextWindow) // 32K tokens
        assertTrue(modelInfo.capabilities.contains("Template-driven web development"))
    }

    // ===== Model Comparison Tests =====

    @Test
    fun `test model comparison SmolLM2 vs Qwen`() {
        val comparison = ModelRegistry.compareModels(
            CodingModel.SMOL_LM2_360M,
            CodingModel.QWEN_CODER_0_5B
        )

        // SmolLM2 is 60MB larger than Qwen (180MB - 120MB)
        assertEquals(60 * 1024 * 1024L, comparison.sizeDifference)

        // Qwen has 24K more context (32K - 8K)
        assertEquals(-24576, comparison.contextDifference)

        assertTrue(comparison.recommendation.contains("creative content"))
        assertTrue(comparison.recommendation.contains("technical tasks"))
    }

    @Test
    fun `test all available models`() {
        val models = ModelRegistry.getAvailableModels()

        assertEquals(2, models.size)
        assertTrue(models.contains(CodingModel.SMOL_LM2_360M))
        assertTrue(models.contains(CodingModel.QWEN_CODER_0_5B))
    }

    // ===== Enum Properties Tests =====

    @Test
    fun `test CodingModel display names`() {
        assertEquals("SmolLM2-360M", CodingModel.SMOL_LM2_360M.displayName)
        assertEquals("Qwen2.5-Coder", CodingModel.QWEN_CODER_0_5B.displayName)
    }

    @Test
    fun `test CodingModel short descriptions`() {
        assertEquals("General-purpose", CodingModel.SMOL_LM2_360M.shortDescription)
        assertEquals("Coding specialist", CodingModel.QWEN_CODER_0_5B.shortDescription)
    }

    // ===== Template Distribution Tests =====

    @Test
    fun `test creative templates use SmolLM2`() {
        val creativeTemplates = listOf(TemplateType.QUIZ, TemplateType.PRESENTATION)

        creativeTemplates.forEach { template ->
            val model = ModelRegistry.getBestModelForTemplate(template)
            assertEquals(
                CodingModel.SMOL_LM2_360M,
                model,
                "Template $template should use SmolLM2"
            )
        }
    }

    @Test
    fun `test technical templates use Qwen`() {
        val technicalTemplates = listOf(TemplateType.SVG_CHART, TemplateType.GAME)

        technicalTemplates.forEach { template ->
            val model = ModelRegistry.getBestModelForTemplate(template)
            assertEquals(
                CodingModel.QWEN_CODER_0_5B,
                model,
                "Template $template should use Qwen"
            )
        }
    }

    // ===== Model Swapping Simulation Tests =====

    @Test
    fun `test swapping from quiz to game changes model`() = runTest {
        // Simulate user starting with quiz
        val quizModel = ModelRegistry.getBestModelForTemplate(TemplateType.QUIZ)
        assertEquals(CodingModel.SMOL_LM2_360M, quizModel)

        // User switches to game
        val gameModel = ModelRegistry.getBestModelForTemplate(TemplateType.GAME)
        assertEquals(CodingModel.QWEN_CODER_0_5B, gameModel)

        // Verify models are different (swap occurred)
        assertNotEquals(quizModel, gameModel)
    }

    @Test
    fun `test swapping from quiz to presentation keeps same model`() = runTest {
        // Simulate user starting with quiz
        val quizModel = ModelRegistry.getBestModelForTemplate(TemplateType.QUIZ)
        assertEquals(CodingModel.SMOL_LM2_360M, quizModel)

        // User switches to presentation
        val presentationModel = ModelRegistry.getBestModelForTemplate(TemplateType.PRESENTATION)
        assertEquals(CodingModel.SMOL_LM2_360M, presentationModel)

        // Verify models are the same (no swap needed)
        assertEquals(quizModel, presentationModel)
    }

    @Test
    fun `test swapping between all 4 templates`() = runTest {
        val templates = TemplateType.entries
        val selectedModels = templates.map { template ->
            ModelRegistry.getBestModelForTemplate(template)
        }

        // Verify we selected both models at least once
        assertTrue(selectedModels.contains(CodingModel.SMOL_LM2_360M))
        assertTrue(selectedModels.contains(CodingModel.QWEN_CODER_0_5B))

        // Verify distribution: 2 SmolLM2, 2 Qwen
        val smolCount = selectedModels.count { it == CodingModel.SMOL_LM2_360M }
        val qwenCount = selectedModels.count { it == CodingModel.QWEN_CODER_0_5B }

        assertEquals(2, smolCount, "Should have 2 templates using SmolLM2")
        assertEquals(2, qwenCount, "Should have 2 templates using Qwen")
    }

    // ===== Context Window Tests =====

    @Test
    fun `test SmolLM2 context window is 8K`() {
        val info = ModelRegistry.getModelInfo(CodingModel.SMOL_LM2_360M)
        assertEquals(8192, info.contextWindow)
    }

    @Test
    fun `test Qwen context window is 32K`() {
        val info = ModelRegistry.getModelInfo(CodingModel.QWEN_CODER_0_5B)
        assertEquals(32768, info.contextWindow)
    }

    @Test
    fun `test context window difference favors Qwen for long content`() {
        val smolInfo = ModelRegistry.getModelInfo(CodingModel.SMOL_LM2_360M)
        val qwenInfo = ModelRegistry.getModelInfo(CodingModel.QWEN_CODER_0_5B)

        assertTrue(
            qwenInfo.contextWindow > smolInfo.contextWindow,
            "Qwen should have larger context window"
        )

        // Qwen has 4x the context of SmolLM2 (32K vs 8K)
        assertEquals(4, qwenInfo.contextWindow / smolInfo.contextWindow)
    }

    // ===== Integration Test =====

    @Test
    fun `test complete selection workflow for quiz generation`() = runTest {
        // Step 1: User selects quiz template
        val template = TemplateType.QUIZ

        // Step 2: System auto-selects best model
        val selectedModel = ModelRegistry.getBestModelForTemplate(template)
        assertEquals(CodingModel.SMOL_LM2_360M, selectedModel)

        // Step 3: System generates selection reason
        val reason = ModelRegistry.getSelectionReason(selectedModel, template)
        assertEquals("Best for creative, varied questions", reason)

        // Step 4: System retrieves model info
        val modelInfo = ModelRegistry.getModelInfo(selectedModel)
        assertEquals("SmolLM2-360M-Instruct", modelInfo.name)
        assertEquals(8192, modelInfo.contextWindow)

        // Verify complete workflow succeeded
        assertTrue(reason.isNotBlank())
        assertTrue(modelInfo.capabilities.isNotEmpty())
    }
}
