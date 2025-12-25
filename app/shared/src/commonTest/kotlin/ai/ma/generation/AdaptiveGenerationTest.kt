package ai.ma.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AdaptiveGeneration - PHASE1.5-007
 *
 * Validates query type detection and parameter adaptation.
 */
class AdaptiveGenerationTest {

    // ============================================================
    // Query Type Detection Tests
    // ============================================================

    @Test
    fun `detectQueryType - educational queries`() {
        val educationalQueries = listOf(
            "teach me about AI",
            "Can you explain quantum mechanics?",
            "How does photosynthesis work?",
            "Help me understand neural networks",
            "Walk me through the process",
            "Tell me about blockchain",
            "Describe how GPT works",
            "Elaborate on machine learning"
        )

        educationalQueries.forEach { query ->
            val type = AdaptiveGeneration.detectQueryType(query)
            assertEquals(
                QueryType.EDUCATIONAL,
                type,
                "Expected EDUCATIONAL for: '$query', got $type"
            )
        }
    }

    @Test
    fun `detectQueryType - technical queries`() {
        val technicalQueries = listOf(
            "write code to reverse a string",
            "debug this function",
            "implement a binary search algorithm",
            "fix this error in my program",
            "optimize this code",
            "refactor this function",
            "code review please",
            "syntax error help"
        )

        technicalQueries.forEach { query ->
            val type = AdaptiveGeneration.detectQueryType(query)
            assertEquals(
                QueryType.TECHNICAL,
                type,
                "Expected TECHNICAL for: '$query', got $type"
            )
        }
    }

    @Test
    fun `detectQueryType - factual queries`() {
        val factualQueries = listOf(
            "What is AI?",
            "Who invented the telephone?",
            "When did World War II end?",
            "Where is Paris?",
            "Define machine learning",
            "Which planet is largest?",
            "How many continents are there?",
            "List the primary colors"
        )

        factualQueries.forEach { query ->
            val type = AdaptiveGeneration.detectQueryType(query)
            assertEquals(
                QueryType.FACTUAL,
                type,
                "Expected FACTUAL for: '$query', got $type"
            )
        }
    }

    @Test
    fun `detectQueryType - conversational queries`() {
        val conversationalQueries = listOf(
            "Hello, how are you?",
            "Tell me a joke",
            "Good morning!",
            "Thanks for your help",
            "I'm feeling stressed",
            "What's your favorite color?"
        )

        conversationalQueries.forEach { query ->
            val type = AdaptiveGeneration.detectQueryType(query)
            assertEquals(
                QueryType.CONVERSATIONAL,
                type,
                "Expected CONVERSATIONAL for: '$query', got $type"
            )
        }
    }

    // ============================================================
    // Max Tokens Adaptation Tests
    // ============================================================

    @Test
    fun `educational query gets 1024 tokens on 8GB device`() {
        val config = AdaptiveGeneration.getConfig(
            query = "Can you teach me about quantum entanglement?",
            deviceRamGB = 8L
        )

        assertEquals(QueryType.EDUCATIONAL, config.queryType)
        assertEquals(1024, config.maxTokens, "Educational query on 8GB device should get 1024 tokens")
    }

    @Test
    fun `technical query gets 768 tokens on 8GB device`() {
        val config = AdaptiveGeneration.getConfig(
            query = "Write a function to reverse a string",
            deviceRamGB = 8L
        )

        assertEquals(QueryType.TECHNICAL, config.queryType)
        assertEquals(768, config.maxTokens, "Technical query on 8GB device should get 768 tokens")
    }

    @Test
    fun `factual query gets 512 tokens on 8GB device`() {
        val config = AdaptiveGeneration.getConfig(
            query = "What is the speed of light?",
            deviceRamGB = 8L
        )

        assertEquals(QueryType.FACTUAL, config.queryType)
        assertEquals(512, config.maxTokens, "Factual query on 8GB device should get 512 tokens")
    }

    @Test
    fun `conversational query gets 256 tokens on 4GB device`() {
        val config = AdaptiveGeneration.getConfig(
            query = "Hello, how are you?",
            deviceRamGB = 4L
        )

        assertEquals(QueryType.CONVERSATIONAL, config.queryType)
        assertEquals(256, config.maxTokens, "Conversational query on 4GB device should get 256 tokens")
    }

    // ============================================================
    // Device RAM Adaptation Tests
    // ============================================================

    @Test
    fun `educational query scales with device RAM`() {
        val query = "teach me about AI"

        // High-end device (12GB+)
        val highEnd = AdaptiveGeneration.getConfig(query, deviceRamGB = 12L)
        assertEquals(1536, highEnd.maxTokens)

        // Mid-high device (8GB)
        val midHigh = AdaptiveGeneration.getConfig(query, deviceRamGB = 8L)
        assertEquals(1024, midHigh.maxTokens)

        // Mid-range device (6GB)
        val midRange = AdaptiveGeneration.getConfig(query, deviceRamGB = 6L)
        assertEquals(768, midRange.maxTokens)

        // Low-mid device (4GB)
        val lowMid = AdaptiveGeneration.getConfig(query, deviceRamGB = 4L)
        assertEquals(512, lowMid.maxTokens)

        // Low-end device (2GB)
        val lowEnd = AdaptiveGeneration.getConfig(query, deviceRamGB = 2L)
        assertEquals(384, lowEnd.maxTokens)

        // Verify scaling: higher RAM = more tokens
        assertTrue(highEnd.maxTokens > midHigh.maxTokens)
        assertTrue(midHigh.maxTokens > midRange.maxTokens)
        assertTrue(midRange.maxTokens > lowMid.maxTokens)
        assertTrue(lowMid.maxTokens > lowEnd.maxTokens)
    }

    // ============================================================
    // Temperature Adaptation Tests
    // ============================================================

    @Test
    fun `temperature varies by query type`() {
        val deviceRamGB = 8L

        // Educational: 0.6 (engaging)
        val educational = AdaptiveGeneration.getConfig("teach me about AI", deviceRamGB)
        assertEquals(0.6f, educational.temperature, 0.01f)

        // Technical: 0.3 (deterministic)
        val technical = AdaptiveGeneration.getConfig("write code", deviceRamGB)
        assertEquals(0.3f, technical.temperature, 0.01f)

        // Factual: 0.5 (consistent)
        val factual = AdaptiveGeneration.getConfig("what is AI?", deviceRamGB)
        assertEquals(0.5f, factual.temperature, 0.01f)

        // Conversational: 0.7 (natural)
        val conversational = AdaptiveGeneration.getConfig("hello!", deviceRamGB)
        assertEquals(0.7f, conversational.temperature, 0.01f)
    }

    // ============================================================
    // User Preferences Tests
    // ============================================================

    @Test
    fun `verbosity multiplier adjusts max tokens`() {
        val query = "teach me about AI"
        val deviceRamGB = 8L

        // Default (1.0x) = 1024 tokens
        val default = AdaptiveGeneration.getConfig(query, deviceRamGB, UserPreferences.BALANCED)
        assertEquals(1024, default.maxTokens)

        // Concise (0.5x) = 512 tokens
        val concise = AdaptiveGeneration.getConfig(query, deviceRamGB, UserPreferences.CONCISE)
        assertEquals(512, concise.maxTokens)

        // Verbose (1.5x) = 1536 tokens (capped at device max)
        val verbose = AdaptiveGeneration.getConfig(query, deviceRamGB, UserPreferences.VERBOSE)
        assertEquals(1536, verbose.maxTokens)
    }

    @Test
    fun `verbosity multiplier respects minimum tokens`() {
        val query = "teach me about AI"
        val deviceRamGB = 8L

        // Even with 0.1x multiplier, educational queries need minimum 384 tokens
        val ultraConcise = AdaptiveGeneration.getConfig(
            query,
            deviceRamGB,
            UserPreferences(verbosityMultiplier = 0.1f)
        )

        assertTrue(ultraConcise.maxTokens >= 384, "Educational queries need minimum 384 tokens")
    }

    @Test
    fun `verbosity multiplier respects maximum tokens`() {
        val query = "teach me about AI"
        val deviceRamGB = 8L

        // Even with 10x multiplier, 8GB devices cap at 1536 tokens
        val ultraVerbose = AdaptiveGeneration.getConfig(
            query,
            deviceRamGB,
            UserPreferences(verbosityMultiplier = 10.0f)
        )

        assertTrue(ultraVerbose.maxTokens <= 1536, "8GB devices cap at 1536 tokens")
    }

    // ============================================================
    // Edge Cases Tests
    // ============================================================

    @Test
    fun `empty query defaults to conversational`() {
        val config = AdaptiveGeneration.getConfig("", deviceRamGB = 8L)
        assertEquals(QueryType.CONVERSATIONAL, config.queryType)
    }

    @Test
    fun `case insensitive query detection`() {
        val queries = listOf(
            "TEACH ME ABOUT AI",
            "Teach Me About AI",
            "teach me about ai",
            "TeAcH mE aBoUt Ai"
        )

        queries.forEach { query ->
            val config = AdaptiveGeneration.getConfig(query, deviceRamGB = 8L)
            assertEquals(QueryType.EDUCATIONAL, config.queryType)
        }
    }

    @Test
    fun `reason string includes query type and device RAM`() {
        val config = AdaptiveGeneration.getConfig(
            query = "teach me about AI",
            deviceRamGB = 8L
        )

        assertTrue(config.reason.contains("educational"), "Reason should mention query type")
        assertTrue(config.reason.contains("8GB"), "Reason should mention device RAM")
    }

    @Test
    fun `reason string includes verbosity when non-default`() {
        val config = AdaptiveGeneration.getConfig(
            query = "teach me about AI",
            deviceRamGB = 8L,
            userPreferences = UserPreferences(verbosityMultiplier = 1.5f)
        )

        assertTrue(config.reason.contains("Verbosity"), "Reason should mention custom verbosity")
        assertTrue(config.reason.contains("1.5"), "Reason should show multiplier value")
    }

    // ============================================================
    // GenerationConfig Tests
    // ============================================================

    @Test
    fun `GenerationConfig validation - positive maxTokens`() {
        try {
            GenerationConfig(
                maxTokens = -1,
                temperature = 0.5f,
                queryType = QueryType.CONVERSATIONAL,
                reason = "test"
            )
            throw AssertionError("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `GenerationConfig validation - temperature in range`() {
        try {
            GenerationConfig(
                maxTokens = 256,
                temperature = 1.5f,  // Invalid: > 1.0
                queryType = QueryType.CONVERSATIONAL,
                reason = "test"
            )
            throw AssertionError("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `GenerationConfig withMaxTokens creates copy`() {
        val original = GenerationConfig(
            maxTokens = 256,
            temperature = 0.5f,
            queryType = QueryType.CONVERSATIONAL,
            reason = "test"
        )

        val modified = original.withMaxTokens(512)

        assertEquals(256, original.maxTokens, "Original should be unchanged")
        assertEquals(512, modified.maxTokens, "Modified should have new value")
        assertEquals(original.temperature, modified.temperature, "Other fields should match")
    }

    @Test
    fun `GenerationConfig withTemperature creates copy`() {
        val original = GenerationConfig(
            maxTokens = 256,
            temperature = 0.5f,
            queryType = QueryType.CONVERSATIONAL,
            reason = "test"
        )

        val modified = original.withTemperature(0.8f)

        assertEquals(0.5f, original.temperature, "Original should be unchanged")
        assertEquals(0.8f, modified.temperature, "Modified should have new value")
        assertEquals(original.maxTokens, modified.maxTokens, "Other fields should match")
    }

    // ============================================================
    // Real-World Scenario Tests
    // ============================================================

    @Test
    fun `real scenario - teaching AI on mid-range device`() {
        // Pixel 6 with 6GB RAM asking educational question
        val config = AdaptiveGeneration.getConfig(
            query = "Can you teach me about artificial intelligence and machine learning?",
            deviceRamGB = 6L
        )

        assertEquals(QueryType.EDUCATIONAL, config.queryType)
        assertEquals(768, config.maxTokens, "6GB device gets 768 tokens for educational")
        assertEquals(0.6f, config.temperature, "Educational gets engaging temperature")
    }

    @Test
    fun `real scenario - debugging on high-end device`() {
        // Samsung Galaxy S23 with 12GB RAM asking technical question
        val config = AdaptiveGeneration.getConfig(
            query = "Debug this code: function reverseString(str) { return str.split().reverse().join() }",
            deviceRamGB = 12L
        )

        assertEquals(QueryType.TECHNICAL, config.queryType)
        assertEquals(1024, config.maxTokens, "12GB device gets 1024 tokens for technical")
        assertEquals(0.3f, config.temperature, "Technical gets deterministic temperature")
    }

    @Test
    fun `real scenario - casual chat on low-end device`() {
        // Budget phone with 3GB RAM, casual greeting
        val config = AdaptiveGeneration.getConfig(
            query = "Hey, how's it going?",
            deviceRamGB = 3L
        )

        assertEquals(QueryType.CONVERSATIONAL, config.queryType)
        assertEquals(192, config.maxTokens, "Low-end device gets minimal tokens for chat")
        assertEquals(0.7f, config.temperature, "Conversational gets natural temperature")
    }
}
