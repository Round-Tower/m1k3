package domain.memory

import app.m1k3.ai.assistant.domain.memory.ImportanceCalculator
import app.m1k3.ai.assistant.domain.memory.ConversationContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Test-Driven Development for ImportanceCalculator
 *
 * Phase 2 Ticket: PHASE2-007
 * Following Red-Green-Refactor cycle
 */
class ImportanceCalculatorTest {

    private val calculator = ImportanceCalculator()

    // Test 1: Baseline score
    @Test
    fun `baseline importance is 0_5 for neutral statements`() {
        val content = "The weather is nice today."
        val importance = calculator.calculateImportance(content)

        // Should be close to baseline (0.5) with small adjustments
        assertTrue(importance in 0.4f..0.6f, "Expected baseline ~0.5, got $importance")
    }

    // Test 2: Question detection
    @Test
    fun `questions have higher importance than statements`() {
        val question = "What is the capital of France?"
        val statement = "The capital of France is Paris."

        val questionScore = calculator.calculateImportance(question)
        val statementScore = calculator.calculateImportance(statement)

        assertTrue(questionScore > statementScore,
            "Questions should score higher: question=$questionScore, statement=$statementScore")
    }

    // Test 3: Complex questions weighted even higher
    @Test
    fun `complex questions have highest importance`() {
        val simpleQuestion = "What is AI?"
        val complexQuestion = "What are the key differences between supervised and unsupervised machine learning algorithms?"

        val simpleScore = calculator.calculateImportance(simpleQuestion)
        val complexScore = calculator.calculateImportance(complexQuestion)

        assertTrue(complexScore > simpleScore,
            "Complex questions should score higher: complex=$complexScore, simple=$simpleScore")
    }

    // Test 4: Code blocks high importance
    @Test
    fun `code blocks have high importance`() {
        val withCode = """
            Here's how to do it:
            ```kotlin
            fun example() {
                println("Hello")
            }
            ```
        """.trimIndent()

        val withoutCode = "Here's how to do it: just print hello."

        val codeScore = calculator.calculateImportance(withCode)
        val textScore = calculator.calculateImportance(withoutCode)

        assertTrue(codeScore > 0.6f, "Code blocks should be high importance: $codeScore")
        assertTrue(codeScore > textScore, "Code should score higher than plain text")
    }

    // Test 5: Knowledge markers detection
    @Test
    fun `knowledge markers increase importance`() {
        val withMarker = "It's important to remember that Paris is the capital of France."
        val withoutMarker = "Paris is the capital of France."

        val markerScore = calculator.calculateImportance(withMarker)
        val plainScore = calculator.calculateImportance(withoutMarker)

        assertTrue(markerScore > plainScore,
            "Knowledge markers should increase importance")
    }

    // Test 6: Personal information detection
    @Test
    fun `personal information has high importance`() {
        val personal = "My name is Kevin and I work as a software engineer."
        val impersonal = "Software engineering is a growing field."

        val personalScore = calculator.calculateImportance(personal)
        val impersonalScore = calculator.calculateImportance(impersonal)

        assertTrue(personalScore > impersonalScore,
            "Personal info should score higher")
        assertTrue(personalScore > 0.6f, "Personal info should be high importance")
    }

    // Test 7: Technical content detection
    @Test
    fun `technical content gets bonus`() {
        val technical = "The CPU processes data using the ALU and GPU handles graphics rendering."
        val nonTechnical = "The computer is running slowly today."

        val techScore = calculator.calculateImportance(technical)
        val nonTechScore = calculator.calculateImportance(nonTechnical)

        assertTrue(techScore > nonTechScore,
            "Technical content should score higher")
    }

    // Test 8: Length-based adjustment
    @Test
    fun `very short content has lower importance`() {
        val veryShort = "OK"
        val normal = "That sounds good, let's proceed with the plan."

        val shortScore = calculator.calculateImportance(veryShort)
        val normalScore = calculator.calculateImportance(normal)

        assertTrue(shortScore < normalScore,
            "Very short content should score lower")
    }

    @Test
    fun `detailed content gets length bonus`() {
        val short = "Paris is nice."
        val detailed = "Paris is the capital of France and one of the most beautiful cities in the world, known for its art, culture, cuisine, and the iconic Eiffel Tower that attracts millions of visitors each year."

        val shortScore = calculator.calculateImportance(short)
        val detailedScore = calculator.calculateImportance(detailed)

        assertTrue(detailedScore > shortScore,
            "Detailed content should get length bonus")
    }

    // Test 9: Importance clamped to [0, 1]
    @Test
    fun `importance is always between 0 and 1`() {
        val testCases = listOf(
            "?", // Very short question
            "What is the meaning of life, the universe, and everything? This is an important question to remember.", // Everything
            "", // Empty
            "A".repeat(1000) // Very long
        )

        testCases.forEach { content ->
            val score = calculator.calculateImportance(content)
            assertTrue(score in 0f..1f,
                "Importance must be in [0, 1], got $score for: ${content.take(50)}")
        }
    }

    // Test 10: Conversation context affects scoring
    @Test
    fun `current conversation gets recency bonus`() {
        val content = "Let's talk about Paris."

        val normalContext = ConversationContext(isCurrentConversation = false)
        val currentContext = ConversationContext(isCurrentConversation = true)

        val normalScore = calculator.calculateImportance(content, normalContext)
        val currentScore = calculator.calculateImportance(content, currentContext)

        assertTrue(currentScore > normalScore,
            "Current conversation should get recency bonus")
    }

    // Test 11: Trivia shared context
    @Test
    fun `trivia shared increases importance`() {
        val content = "The Eiffel Tower was built in 1889."

        val noTriviaContext = ConversationContext(triviaWasShared = false)
        val triviaContext = ConversationContext(triviaWasShared = true)

        val normalScore = calculator.calculateImportance(content, noTriviaContext)
        val triviaScore = calculator.calculateImportance(content, triviaContext)

        assertTrue(triviaScore > normalScore,
            "Trivia context should increase importance")
    }

    // Test 12: Multiple question markers
    @Test
    fun `different question markers detected`() {
        val questionTypes = listOf(
            "How does AI work?",
            "What is machine learning?",
            "Why is the sky blue?",
            "When was Paris founded?",
            "Where is the Eiffel Tower?",
            "Who invented the computer?"
        )

        val statement = "Paris is a city."
        val statementScore = calculator.calculateImportance(statement)

        questionTypes.forEach { question ->
            val questionScore = calculator.calculateImportance(question)
            assertTrue(questionScore > statementScore,
                "Question '$question' should score higher than statement")
        }
    }

    // Test 13: Knowledge markers variety
    @Test
    fun `various knowledge markers increase score`() {
        val markers = listOf(
            "learn", "understand", "realize", "discover",
            "important", "remember", "don't forget",
            "key", "critical", "always", "never",
            "tip", "trick", "secret", "fact"
        )

        val baseline = "Paris is a city."
        val baselineScore = calculator.calculateImportance(baseline)

        markers.forEach { marker ->
            val content = "It's $marker that Paris is a city."
            val score = calculator.calculateImportance(content)
            assertTrue(score > baselineScore,
                "Marker '$marker' should increase score")
        }
    }

    // Test 14: Edge cases
    @Test
    fun `empty string returns low importance`() {
        val score = calculator.calculateImportance("")
        assertTrue(score < 0.5f, "Empty string should have low importance")
    }

    @Test
    fun `whitespace only returns low importance`() {
        val score = calculator.calculateImportance("   \n\t  ")
        assertTrue(score < 0.5f, "Whitespace should have low importance")
    }
}
