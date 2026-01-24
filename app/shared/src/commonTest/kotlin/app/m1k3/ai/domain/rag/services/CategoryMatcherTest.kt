package app.m1k3.ai.domain.rag.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CategoryMatcher Tests
 *
 * Tests for keyword-to-category mapping used in RAG fallback retrieval.
 * CategoryMatcher provides fast, rule-based category detection without embeddings.
 */
class CategoryMatcherTest {

    // ========================================
    // matchCategories Tests
    // ========================================

    @Test
    fun `matches device technology keywords`() {
        val queries = listOf(
            "How do I extend my phone battery life?",
            "iPhone storage is full",
            "Android device keeps crashing"
        )

        queries.forEach { query ->
            val categories = CategoryMatcher.matchCategories(query)
            assertTrue(
                categories.contains("device_technology"),
                "Expected 'device_technology' for query: $query"
            )
        }
    }

    @Test
    fun `matches wifi networking keywords`() {
        val categories = CategoryMatcher.matchCategories("My wifi connection keeps dropping")
        assertTrue(categories.contains("wifi_networking"))
    }

    @Test
    fun `matches security privacy keywords`() {
        val categories = CategoryMatcher.matchCategories("How do I create a secure password?")
        assertTrue(categories.contains("security_privacy"))
    }

    @Test
    fun `matches AI and technology trends keywords`() {
        val categories = CategoryMatcher.matchCategories("What is artificial intelligence?")
        assertTrue(categories.contains("technology_trends"))
    }

    @Test
    fun `matches science facts keywords`() {
        val categories = CategoryMatcher.matchCategories("How does quantum physics work?")
        assertTrue(categories.contains("science_facts"))
    }

    @Test
    fun `matches historical facts keywords`() {
        val categories = CategoryMatcher.matchCategories("Tell me about ancient Rome")
        assertTrue(categories.contains("historical_facts"))
    }

    @Test
    fun `matches food culture keywords`() {
        val categories = CategoryMatcher.matchCategories("What's a good pasta recipe?")
        assertTrue(categories.contains("food_culture"))
    }

    @Test
    fun `matches multiple categories for complex queries`() {
        // Query about AI history should match both
        val categories = CategoryMatcher.matchCategories(
            "What is the history of artificial intelligence?"
        )
        assertTrue(categories.contains("technology_trends"))
        assertTrue(categories.contains("historical_facts"))
    }

    @Test
    fun `returns empty list for unmatched queries`() {
        val categories = CategoryMatcher.matchCategories("xyzzy foobar gibberish")
        assertTrue(categories.isEmpty())
    }

    @Test
    fun `handles empty query`() {
        val categories = CategoryMatcher.matchCategories("")
        assertTrue(categories.isEmpty())
    }

    @Test
    fun `matching is case insensitive`() {
        val lower = CategoryMatcher.matchCategories("wifi")
        val upper = CategoryMatcher.matchCategories("WIFI")
        val mixed = CategoryMatcher.matchCategories("WiFi")

        assertEquals(lower, upper)
        assertEquals(lower, mixed)
    }

    // ========================================
    // extractKeywords Tests
    // ========================================

    @Test
    fun `extracts meaningful keywords from query`() {
        val keywords = CategoryMatcher.extractKeywords("How do I fix my phone battery?")

        assertTrue(keywords.contains("fix"))
        assertTrue(keywords.contains("phone"))
        assertTrue(keywords.contains("battery"))
    }

    @Test
    fun `removes stop words from query`() {
        val keywords = CategoryMatcher.extractKeywords("What is the best way to learn?")

        // Stop words should be removed
        assertTrue("what" !in keywords)
        assertTrue("is" !in keywords)
        assertTrue("the" !in keywords)
        assertTrue("to" !in keywords)

        // Meaningful words remain
        assertTrue(keywords.contains("best"))
        assertTrue(keywords.contains("way"))
        assertTrue(keywords.contains("learn"))
    }

    @Test
    fun `filters short words under 3 characters`() {
        val keywords = CategoryMatcher.extractKeywords("I am a AI fan")

        // Short words filtered
        assertTrue("am" !in keywords)
        assertTrue("ai" !in keywords) // 2 chars

        // "fan" is 3 chars, should be included
        assertTrue(keywords.contains("fan"))
    }

    @Test
    fun `returns empty list for query with only stop words`() {
        val keywords = CategoryMatcher.extractKeywords("what is the a an")
        assertTrue(keywords.isEmpty())
    }

    @Test
    fun `handles special characters in query`() {
        val keywords = CategoryMatcher.extractKeywords("What's the best phone?")

        assertTrue(keywords.contains("best"))
        assertTrue(keywords.contains("phone"))
    }

    // ========================================
    // Category Coverage Tests
    // ========================================

    @Test
    fun `all major categories are matchable`() {
        val categoryKeywords = mapOf(
            "device_technology" to "phone",
            "wifi_networking" to "wifi",
            "security_privacy" to "password",
            "mathematical_calculation" to "calculate",
            "code_debugging" to "debug",
            "technical_explanation" to "how does",
            "historical_facts" to "history",
            "science_facts" to "physics",
            "geography_facts" to "country",
            "movies_tv" to "movie",
            "music_culture" to "music",
            "sports_recreation" to "football",
            "food_culture" to "recipe",
            "technology_trends" to "blockchain",
            "lifestyle_wellness" to "meditation",
            "diagnostic_troubleshooting" to "troubleshoot",
            "educational_tutoring" to "learn",
            "trivia_facts" to "fun fact"
        )

        categoryKeywords.forEach { (expectedCategory, keyword) ->
            val categories = CategoryMatcher.matchCategories(keyword)
            assertTrue(
                categories.contains(expectedCategory),
                "Expected '$expectedCategory' for keyword '$keyword', got: $categories"
            )
        }
    }
}
