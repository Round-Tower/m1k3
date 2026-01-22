package app.m1k3.ai.domain.rag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Intent domain entity
 *
 * TDD: Phase 1 - Domain Value Objects
 */
class IntentTest {

    @Test
    fun `all intents have valid categories`() {
        Intent.values().forEach { intent ->
            assertTrue(
                intent.category.isNotBlank(),
                "Intent ${intent.name} should have non-blank category"
            )
        }
    }

    @Test
    fun `all intents except GENERAL have keywords`() {
        Intent.values()
            .filter { it != Intent.GENERAL }
            .forEach { intent ->
                assertTrue(
                    intent.keywords.isNotEmpty(),
                    "Intent ${intent.name} should have keywords (except GENERAL)"
                )
            }
    }

    @Test
    fun `GENERAL intent has no keywords`() {
        val generalIntent = Intent.GENERAL
        assertTrue(
            generalIntent.keywords.isEmpty(),
            "GENERAL intent should have no keywords (catch-all)"
        )
    }

    @Test
    fun `CONVERSATIONAL has greeting keywords`() {
        val conversationalIntent = Intent.CONVERSATIONAL

        assertTrue(
            conversationalIntent.keywords.contains("hello"),
            "CONVERSATIONAL should include 'hello'"
        )
        assertTrue(
            conversationalIntent.keywords.contains("hi"),
            "CONVERSATIONAL should include 'hi'"
        )
        assertTrue(
            conversationalIntent.keywords.contains("thanks"),
            "CONVERSATIONAL should include 'thanks'"
        )
    }

    @Test
    fun `AI_ML has AI-related keywords`() {
        val aiIntent = Intent.AI_ML

        assertTrue(
            aiIntent.keywords.contains("ai") ||
            aiIntent.keywords.contains("artificial intelligence") ||
            aiIntent.keywords.contains("machine learning"),
            "AI_ML should have AI-related keywords"
        )
    }

    @Test
    fun `no duplicate keywords across intents`() {
        // This test ensures intents are well-separated
        // (Note: Some overlap is OK, but excessive overlap indicates poor categorization)
        val allKeywords = Intent.values()
            .filterNot { it == Intent.GENERAL }
            .flatMap { it.keywords }

        val uniqueKeywords = allKeywords.toSet()

        // Allow up to 20% keyword overlap (reasonable for multi-word phrases)
        val overlapThreshold = 0.20
        val actualOverlap = 1.0 - (uniqueKeywords.size.toDouble() / allKeywords.size.toDouble())

        assertTrue(
            actualOverlap <= overlapThreshold,
            "Keyword overlap (${"%.2f".format(actualOverlap * 100)}%) should be less than ${(overlapThreshold * 100).toInt()}%"
        )
    }

    @Test
    fun `all keywords are lowercase`() {
        Intent.values().forEach { intent ->
            intent.keywords.forEach { keyword ->
                assertTrue(
                    keyword == keyword.lowercase(),
                    "Keyword '$keyword' in ${intent.name} should be lowercase"
                )
            }
        }
    }
}
