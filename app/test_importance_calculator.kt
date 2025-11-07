#!/usr/bin/env kotlin

// Standalone test runner for ImportanceCalculator
// Run with: kotlinc -script test_importance_calculator.kt

package test

// Copy of ImportanceCalculator for testing
class ImportanceCalculator {
    companion object {
        private const val BASELINE_SCORE = 0.5f
        private const val QUESTION_BONUS = 0.15f
        private const val COMPLEX_QUESTION_BONUS = 0.1f
        private const val COMPLEX_QUESTION_THRESHOLD = 15
        private const val CODE_BLOCK_BONUS = 0.2f
        private const val KNOWLEDGE_MARKER_BONUS = 0.15f
        private const val PERSONAL_INFO_BONUS = 0.2f
        private const val TECHNICAL_CONTENT_BONUS = 0.1f
        private const val CURRENT_CONVERSATION_BONUS = 0.05f
        private const val TRIVIA_SHARED_BONUS = 0.1f
        private const val LENGTH_VERY_SHORT = 50
        private const val LENGTH_NORMAL = 200
        private const val LENGTH_DETAILED = 500
        private const val VERY_SHORT_PENALTY = -0.1f
        private const val DETAILED_BONUS = 0.1f
        private const val VERY_DETAILED_BONUS = 0.15f

        private val QUESTION_STARTERS = listOf(
            "how", "what", "why", "when", "where", "who", "which",
            "can", "could", "would", "should", "is", "are", "do", "does"
        )

        private val KNOWLEDGE_INDICATORS = listOf(
            "learn", "understand", "realize", "discover",
            "important", "remember", "don't forget", "do not forget",
            "key", "critical", "crucial", "essential",
            "always", "never",
            "tip", "trick", "secret", "fact", "note"
        )

        private val PERSONAL_INDICATORS = listOf(
            "my name", "i am", "i'm", "i work", "i live",
            "i like", "i hate", "i love", "i enjoy",
            "my favorite", "my favourite",
            "years old", "year old", "born in", "from"
        )

        private val TECHNICAL_TERMS = listOf(
            "cpu", "gpu", "ram", "memory", "battery",
            "android", "ios", "linux", "windows",
            "snapdragon", "exynos", "tensor", "bionic",
            "api", "sdk", "debug", "performance",
            "storage", "algorithm", "function", "class",
            "method", "variable", "database", "cache",
            "thread", "process", "compiler", "runtime"
        )
    }

    fun calculateImportance(
        content: String,
        context: ConversationContext = ConversationContext()
    ): Float {
        if (content.isBlank()) return 0.1f

        var score = BASELINE_SCORE
        val lowerContent = content.lowercase()

        if (isQuestion(content, lowerContent)) {
            score += QUESTION_BONUS
            if (isComplexQuestion(content)) score += COMPLEX_QUESTION_BONUS
        }

        if (hasCodeBlock(content)) score += CODE_BLOCK_BONUS
        if (hasKnowledgeMarkers(lowerContent)) score += KNOWLEDGE_MARKER_BONUS
        if (hasPersonalInfo(lowerContent)) score += PERSONAL_INFO_BONUS
        if (hasTechnicalContent(lowerContent)) score += TECHNICAL_CONTENT_BONUS

        score += calculateLengthBonus(content.length)

        if (context.isCurrentConversation) score += CURRENT_CONVERSATION_BONUS
        if (context.triviaWasShared) score += TRIVIA_SHARED_BONUS

        return score.coerceIn(0f, 1f)
    }

    private fun isQuestion(content: String, lowerContent: String): Boolean {
        if (content.contains("?")) return true
        val firstWord = lowerContent.trim().split(Regex("\\s+")).firstOrNull() ?: ""
        return QUESTION_STARTERS.any { it == firstWord }
    }

    private fun isComplexQuestion(content: String): Boolean =
        content.split(Regex("\\s+")).size > COMPLEX_QUESTION_THRESHOLD

    private fun hasCodeBlock(content: String): Boolean = content.contains("```")

    private fun hasKnowledgeMarkers(lowerContent: String): Boolean =
        KNOWLEDGE_INDICATORS.any { lowerContent.contains(it) }

    private fun hasPersonalInfo(lowerContent: String): Boolean =
        PERSONAL_INDICATORS.any { lowerContent.contains(it) }

    private fun hasTechnicalContent(lowerContent: String): Boolean =
        TECHNICAL_TERMS.any { lowerContent.contains(it) }

    private fun calculateLengthBonus(length: Int): Float = when {
        length < LENGTH_VERY_SHORT -> VERY_SHORT_PENALTY
        length < LENGTH_NORMAL -> 0f
        length < LENGTH_DETAILED -> DETAILED_BONUS
        else -> VERY_DETAILED_BONUS
    }
}

data class ConversationContext(
    val triviaWasShared: Boolean = false,
    val isCurrentConversation: Boolean = false
)

// Test cases
fun main() {
    val calculator = ImportanceCalculator()
    var passed = 0
    var failed = 0

    fun test(name: String, condition: Boolean) {
        if (condition) {
            println("✅ PASS: $name")
            passed++
        } else {
            println("❌ FAIL: $name")
            failed++
        }
    }

    println("🧪 Running ImportanceCalculator Tests\n")

    // Test 1: Baseline
    val baseline = calculator.calculateImportance("The weather is nice today.")
    test("Baseline importance ~0.5", baseline in 0.4f..0.6f)

    // Test 2: Questions
    val question = calculator.calculateImportance("What is the capital of France?")
    val statement = calculator.calculateImportance("The capital of France is Paris.")
    test("Questions > Statements", question > statement)

    // Test 3: Complex questions
    val simpleQ = calculator.calculateImportance("What is AI?")
    val complexQ = calculator.calculateImportance("What are the key differences between supervised and unsupervised machine learning algorithms?")
    test("Complex questions > Simple questions", complexQ > simpleQ)

    // Test 4: Code blocks
    val withCode = calculator.calculateImportance("```kotlin\nfun example() {}\n```")
    test("Code blocks high importance", withCode > 0.6f)

    // Test 5: Knowledge markers
    val withMarker = calculator.calculateImportance("It's important to remember Paris is beautiful.")
    val withoutMarker = calculator.calculateImportance("Paris is beautiful.")
    test("Knowledge markers increase score", withMarker > withoutMarker)

    // Test 6: Personal info
    val personal = calculator.calculateImportance("My name is Kevin and I work as an engineer.")
    test("Personal info high importance", personal > 0.6f)

    // Test 7: Technical content
    val technical = calculator.calculateImportance("The CPU uses RAM to process data.")
    val nonTech = calculator.calculateImportance("The computer is slow.")
    test("Technical content > Non-technical", technical > nonTech)

    // Test 8: Length bonus
    val short = calculator.calculateImportance("OK")
    val normal = calculator.calculateImportance("That sounds good, let's proceed.")
    test("Normal length > Very short", normal > short)

    // Test 9: Bounds
    test("Score in [0, 1]", calculator.calculateImportance("test") in 0f..1f)

    // Test 10: Context - current conversation
    val normalContext = calculator.calculateImportance("Hello", ConversationContext(false, false))
    val currentContext = calculator.calculateImportance("Hello", ConversationContext(false, true))
    test("Current conversation bonus", currentContext > normalContext)

    // Test 11: Context - trivia
    val noTrivia = calculator.calculateImportance("Paris is nice", ConversationContext(false, false))
    val withTrivia = calculator.calculateImportance("Paris is nice", ConversationContext(true, false))
    test("Trivia context bonus", withTrivia > noTrivia)

    // Test 12: Empty string
    test("Empty string low importance", calculator.calculateImportance("") < 0.5f)

    println("\n📊 Results: $passed passed, $failed failed")
    if (failed == 0) {
        println("🎉 All tests passed!")
    }
}

// Run tests
main()
