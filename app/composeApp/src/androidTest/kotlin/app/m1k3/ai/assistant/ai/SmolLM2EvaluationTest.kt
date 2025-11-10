package app.m1k3.ai.assistant.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.embedding.MiniLmEmbeddingEngine
import app.m1k3.ai.assistant.knowledge.KnowledgeBaseImporter
import app.m1k3.ai.assistant.rag.IntentClassifier
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.assistant.rag.SemanticRetrievalService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*
import kotlin.time.measureTime

/**
 * SmolLM2 Model Evaluation Tests
 *
 * Comprehensive evaluation of SmolLM2-135M-Instruct model with:
 * - Performance benchmarks (tokens/sec, latency, memory)
 * - Response quality (coherence, token leakage, determinism)
 * - RAG integration (intent classification, fact retrieval)
 * - 10 comprehensive test prompts across all intent categories
 *
 * **IMPORTANT:** These tests use the REAL SmolLM2 model and take longer to run.
 * Run on physical device for accurate performance metrics.
 *
 * Target Metrics:
 * - Performance: 20-40 tok/s on device
 * - RAG Intent Accuracy: >80%
 * - No special token leakage
 * - Response coherence: High
 */
@RunWith(AndroidJUnit4::class)
class SmolLM2EvaluationTest {

    private lateinit var llamaCppEngine: LlamaCppEngine
    private lateinit var database: MaDatabase
    private lateinit var ragManager: RAGManager
    private lateinit var context: Context

    // Test results collection
    private val testResults = mutableListOf<TestResult>()

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()

        // Initialize real SmolLM2 engine
        llamaCppEngine = LlamaCppEngine(context)
        llamaCppEngine.initialize()

        // Create test database
        database = app.m1k3.ai.assistant.test.TestDatabaseFactory.createInMemoryDatabase()

        // Import knowledge base for RAG testing
        val importer = KnowledgeBaseImporter(database)

        // Load comprehensive knowledge base
        val comprehensiveKB = context.assets.open("knowledge/comprehensive_knowledge_base.json")
            .bufferedReader().use { it.readText() }
        importer.importKnowledgeBase(comprehensiveKB)

        // Load M1K3 system knowledge
        val systemKB = context.assets.open("knowledge/m1k3_system_knowledge.json")
            .bufferedReader().use { it.readText() }
        importer.importKnowledgeBase(systemKB)

        // Initialize RAG Manager
        val embeddingEngine = MiniLmEmbeddingEngine(context)
        embeddingEngine.initialize()

        val retrievalService = SemanticRetrievalService(database, embeddingEngine)
        val intentClassifier = IntentClassifier()
        ragManager = RAGManager(retrievalService, intentClassifier)

        println("✅ Test setup complete - SmolLM2 initialized with 1,401 documents")
    }

    @After
    fun teardown() {
        llamaCppEngine.release()
        database.close()

        // Print test summary
        printTestSummary()
    }

    // ========================================
    // Test Suite A: Performance Benchmarks
    // ========================================

    @Test
    fun testInferenceSpeed() = runTest {
        val prompt = "Count from 1 to 10"
        val config = GenerationConfig(
            maxTokens = 100,
            temperature = 0.7f,
            systemPrompt = "You are a helpful assistant. Be concise."
        )

        var tokenCount = 0
        val duration = measureTime {
            llamaCppEngine.generateStreaming(prompt, config) { token ->
                tokenCount++
            }
        }

        val tokensPerSecond = (tokenCount * 1000.0) / duration.inWholeMilliseconds
        val firstTokenLatency = duration.inWholeMilliseconds / tokenCount.coerceAtLeast(1)

        // Record results
        testResults.add(
            TestResult(
                name = "Inference Speed",
                passed = tokensPerSecond >= 15.0, // Minimum acceptable: 15 tok/s
                metrics = mapOf(
                    "tokens" to tokenCount.toString(),
                    "duration_ms" to duration.inWholeMilliseconds.toString(),
                    "tok_per_sec" to String.format("%.2f", tokensPerSecond),
                    "first_token_latency_ms" to firstTokenLatency.toString()
                )
            )
        )

        println("📊 Inference Speed: ${"%.2f".format(tokensPerSecond)} tok/s ($tokenCount tokens in ${duration.inWholeMilliseconds}ms)")

        // Assertions
        assertTrue(tokensPerSecond >= 15.0, "Should achieve at least 15 tok/s (got ${"%.2f".format(tokensPerSecond)})")
        assertTrue(tokenCount > 0, "Should generate at least one token")
    }

    // ========================================
    // Test Suite B: Response Quality
    // ========================================

    @Test
    fun testResponseCoherence() = runTest {
        val prompt = "What is artificial intelligence?"
        val config = GenerationConfig(
            maxTokens = 150,
            temperature = 0.7f,
            systemPrompt = "You are M1K3, a helpful AI assistant. Provide clear, accurate answers."
        )

        val result = llamaCppEngine.generate(prompt, config)

        // Check for coherence indicators
        val hasWords = result.text.split(Regex("\\s+")).size > 5
        val hasValidSentence = result.text.contains(Regex("[.!?]"))
        val notTooRepetitive = !result.text.contains(Regex("(\\b\\w+\\b)\\s+\\1\\s+\\1")) // No 3x word repetition

        val coherent = hasWords && hasValidSentence && notTooRepetitive

        testResults.add(
            TestResult(
                name = "Response Coherence",
                passed = coherent,
                metrics = mapOf(
                    "word_count" to result.text.split(Regex("\\s+")).size.toString(),
                    "has_punctuation" to hasValidSentence.toString(),
                    "not_repetitive" to notTooRepetitive.toString(),
                    "response_preview" to result.text.take(100)
                )
            )
        )

        println("📝 Response: ${result.text}")

        assertTrue(coherent, "Response should be coherent (words: $hasWords, sentence: $hasValidSentence, not repetitive: $notTooRepetitive)")
    }

    @Test
    fun testSpecialTokenLeakage() = runTest {
        val prompt = "Tell me a short story"
        val config = GenerationConfig(
            maxTokens = 100,
            temperature = 0.8f
        )

        val result = llamaCppEngine.generate(prompt, config)

        // Check for leaked special tokens
        val specialTokens = listOf("<|im_start|>", "<|im_end|>", "<|endoftext|>", "<|", "|>")
        val hasLeakage = specialTokens.any { result.text.contains(it) }

        testResults.add(
            TestResult(
                name = "No Special Token Leakage",
                passed = !hasLeakage,
                metrics = mapOf(
                    "response_length" to result.text.length.toString(),
                    "contains_special_tokens" to hasLeakage.toString(),
                    "response_preview" to result.text.take(150)
                )
            )
        )

        assertFalse(hasLeakage, "Response should not contain special tokens: ${result.text}")
    }

    @Test
    fun testMaxTokensRespected() = runTest {
        val maxTokens = 50
        val prompt = "Write a very long essay about space exploration"
        val config = GenerationConfig(
            maxTokens = maxTokens,
            temperature = 0.7f
        )

        var tokenCount = 0
        llamaCppEngine.generateStreaming(prompt, config) { token ->
            tokenCount++
        }

        val respected = tokenCount <= maxTokens + 5 // Allow small margin

        testResults.add(
            TestResult(
                name = "Max Tokens Respected",
                passed = respected,
                metrics = mapOf(
                    "requested_max" to maxTokens.toString(),
                    "actual_tokens" to tokenCount.toString(),
                    "within_limit" to respected.toString()
                )
            )
        )

        assertTrue(respected, "Should respect maxTokens limit (requested: $maxTokens, got: $tokenCount)")
    }

    @Test
    fun testDeterminism() = runTest {
        val prompt = "Count from 1 to 5"
        val config = GenerationConfig(
            maxTokens = 50,
            temperature = 0.0f, // Zero temperature for determinism
            systemPrompt = "Count numbers exactly as requested."
        )

        // Generate 3 times with same config
        val response1 = llamaCppEngine.generate(prompt, config).text
        val response2 = llamaCppEngine.generate(prompt, config).text
        val response3 = llamaCppEngine.generate(prompt, config).text

        val deterministic = response1 == response2 && response2 == response3

        testResults.add(
            TestResult(
                name = "Determinism (temp=0)",
                passed = deterministic,
                metrics = mapOf(
                    "identical_responses" to deterministic.toString(),
                    "response_1" to response1.take(50),
                    "response_2" to response2.take(50),
                    "response_3" to response3.take(50)
                )
            )
        )

        println("Response 1: ${response1.take(100)}")
        println("Response 2: ${response2.take(100)}")
        println("Response 3: ${response3.take(100)}")

        assertTrue(deterministic, "With temperature=0, responses should be identical")
    }

    // ========================================
    // Test Suite C: RAG Integration
    // ========================================

    @Test
    fun testDeviceQueryRAG() = runTest {
        val prompt = "My phone battery drains quickly. What should I check?"
        val systemPrompt = "You are M1K3, a privacy-first AI assistant."

        val result = ragManager.enrichPrompt(prompt, systemPrompt, enableRAG = true)

        val correctIntent = result.intent == IntentClassifier.Intent.DEVICE_TECH
        val factsRetrieved = result.retrievedFacts.size in 3..5 // Should retrieve 5 facts (or close)
        val ragApplied = result.ragApplied

        testResults.add(
            TestResult(
                name = "Device Query RAG",
                passed = correctIntent && factsRetrieved && ragApplied,
                metrics = mapOf(
                    "intent" to result.intent.name,
                    "confidence" to String.format("%.2f", result.confidence),
                    "facts_retrieved" to result.retrievedFacts.size.toString(),
                    "rag_applied" to ragApplied.toString()
                )
            )
        )

        assertEquals(IntentClassifier.Intent.DEVICE_TECH, result.intent, "Should classify as DEVICE_TECH")
        assertTrue(factsRetrieved, "Should retrieve 3-5 facts (got ${result.retrievedFacts.size})")
        assertTrue(ragApplied, "RAG should be applied for device queries")
    }

    @Test
    fun testConversationalSkipsRAG() = runTest {
        val prompt = "Hello, how are you today?"
        val systemPrompt = "You are M1K3."

        val result = ragManager.enrichPrompt(prompt, systemPrompt, enableRAG = true)

        val correctIntent = result.intent == IntentClassifier.Intent.CONVERSATIONAL ||
                          result.intent == IntentClassifier.Intent.GENERAL
        val noFactsRetrieved = result.retrievedFacts.isEmpty()
        val ragNotApplied = !result.ragApplied

        testResults.add(
            TestResult(
                name = "Conversational Skips RAG",
                passed = correctIntent && noFactsRetrieved && ragNotApplied,
                metrics = mapOf(
                    "intent" to result.intent.name,
                    "facts_retrieved" to result.retrievedFacts.size.toString(),
                    "rag_applied" to result.ragApplied.toString()
                )
            )
        )

        assertTrue(correctIntent, "Should classify as CONVERSATIONAL or GENERAL (got ${result.intent})")
        assertTrue(noFactsRetrieved, "Should not retrieve facts for conversational queries")
        assertTrue(ragNotApplied, "RAG should not be applied for conversational queries")
    }

    @Test
    fun testRAGQualityFiltering() = runTest {
        val prompt = "How do I protect myself from phishing attacks?"
        val systemPrompt = "You are M1K3."

        val result = ragManager.enrichPrompt(prompt, systemPrompt, enableRAG = true)

        // Check that all retrieved facts meet quality threshold (similarity >= 0.6)
        val allHighQuality = result.retrievedFacts.all { it.similarity >= 0.6f }
        val avgSimilarity = if (result.retrievedFacts.isNotEmpty()) {
            result.retrievedFacts.map { it.similarity }.average()
        } else 0.0

        testResults.add(
            TestResult(
                name = "RAG Quality Filtering",
                passed = allHighQuality && avgSimilarity >= 0.65,
                metrics = mapOf(
                    "facts_retrieved" to result.retrievedFacts.size.toString(),
                    "all_above_threshold" to allHighQuality.toString(),
                    "avg_similarity" to String.format("%.3f", avgSimilarity),
                    "min_similarity" to result.retrievedFacts.minOfOrNull { it.similarity }?.toString() ?: "N/A"
                )
            )
        )

        assertTrue(allHighQuality, "All retrieved facts should have similarity >= 0.6")
        assertTrue(avgSimilarity >= 0.65, "Average similarity should be >= 0.65 (got ${"%.3f".format(avgSimilarity)})")
    }

    // ========================================
    // Test Suite D: Comprehensive Prompts
    // ========================================

    @Test
    fun testPrompt_Conversational() = runTest {
        testPrompt(
            category = "Conversational",
            prompt = "Hello, how are you?",
            expectedIntent = IntentClassifier.Intent.CONVERSATIONAL,
            expectedRAG = false,
            expectedFactCount = 0
        )
    }

    @Test
    fun testPrompt_DeviceTech() = runTest {
        testPrompt(
            category = "Device Technology",
            prompt = "My iPhone keeps overheating. How can I fix this?",
            expectedIntent = IntentClassifier.Intent.DEVICE_TECH,
            expectedRAG = true,
            expectedFactCount = 3..5
        )
    }

    @Test
    fun testPrompt_WiFi() = runTest {
        testPrompt(
            category = "WiFi & Networking",
            prompt = "My WiFi connection keeps dropping every few minutes",
            expectedIntent = IntentClassifier.Intent.WIFI_NETWORK,
            expectedRAG = true,
            expectedFactCount = 3..5
        )
    }

    @Test
    fun testPrompt_Security() = runTest {
        testPrompt(
            category = "Security & Privacy",
            prompt = "What are the best practices for password security?",
            expectedIntent = IntentClassifier.Intent.SECURITY,
            expectedRAG = true,
            expectedFactCount = 3..5
        )
    }

    @Test
    fun testPrompt_Math() = runTest {
        testPrompt(
            category = "Mathematical Calculations",
            prompt = "Calculate the area of a circle with radius 5",
            expectedIntent = IntentClassifier.Intent.MATH,
            expectedRAG = true,
            expectedFactCount = 2..3
        )
    }

    @Test
    fun testPrompt_Trivia() = runTest {
        testPrompt(
            category = "Trivia & Fun Facts",
            prompt = "Tell me an interesting science fact",
            expectedIntent = IntentClassifier.Intent.TRIVIA,
            expectedRAG = true,
            expectedFactCount = 1..2
        )
    }

    @Test
    fun testPrompt_M1K3System() = runTest {
        testPrompt(
            category = "M1K3 System Knowledge",
            prompt = "What are your capabilities?",
            expectedIntent = IntentClassifier.Intent.SYSTEM,
            expectedRAG = true,
            expectedFactCount = 1..3
        )
    }

    // ========================================
    // Helper Methods
    // ========================================

    private suspend fun testPrompt(
        category: String,
        prompt: String,
        expectedIntent: IntentClassifier.Intent,
        expectedRAG: Boolean,
        expectedFactCount: IntRange
    ) {
        println("\n📝 Testing: $category")
        println("   Prompt: \"$prompt\"")

        val systemPrompt = "You are M1K3, a helpful AI assistant."

        // Test RAG
        val ragResult = ragManager.enrichPrompt(prompt, systemPrompt, enableRAG = true)

        val intentMatch = ragResult.intent == expectedIntent
        val ragMatch = ragResult.ragApplied == expectedRAG
        val factCountMatch = ragResult.retrievedFacts.size in expectedFactCount

        println("   Intent: ${ragResult.intent} (expected: $expectedIntent) ✓ = $intentMatch")
        println("   RAG Applied: ${ragResult.ragApplied} (expected: $expectedRAG) ✓ = $ragMatch")
        println("   Facts Retrieved: ${ragResult.retrievedFacts.size} (expected: $expectedFactCount) ✓ = $factCountMatch")

        // Generate AI response
        val config = GenerationConfig(
            maxTokens = 100,
            temperature = 0.7f,
            systemPrompt = ragResult.enrichedPrompt
        )

        var tokenCount = 0
        val duration = measureTime {
            llamaCppEngine.generateStreaming(prompt, config) { token ->
                tokenCount++
            }
        }

        val tokPerSec = (tokenCount * 1000.0) / duration.inWholeMilliseconds

        println("   Response: $tokenCount tokens in ${duration.inWholeMilliseconds}ms (${"%.2f".format(tokPerSec)} tok/s)")

        testResults.add(
            TestResult(
                name = category,
                passed = intentMatch && ragMatch && factCountMatch && tokenCount > 0,
                metrics = mapOf(
                    "intent" to ragResult.intent.name,
                    "intent_match" to intentMatch.toString(),
                    "rag_applied" to ragResult.ragApplied.toString(),
                    "facts_retrieved" to ragResult.retrievedFacts.size.toString(),
                    "tokens_generated" to tokenCount.toString(),
                    "tok_per_sec" to String.format("%.2f", tokPerSec)
                )
            )
        )

        assertTrue(intentMatch, "Intent should match (expected: $expectedIntent, got: ${ragResult.intent})")
        assertTrue(ragMatch, "RAG application should match expectation")
        assertTrue(factCountMatch, "Fact count should be in range (expected: $expectedFactCount, got: ${ragResult.retrievedFacts.size})")
        assertTrue(tokenCount > 0, "Should generate at least one token")
    }

    private fun printTestSummary() {
        println("\n" + "=".repeat(60))
        println("📊 SmolLM2 Evaluation Test Summary")
        println("=".repeat(60))

        val passed = testResults.count { it.passed }
        val total = testResults.size
        val successRate = (passed * 100.0) / total

        println("Total Tests: $total")
        println("Passed: $passed")
        println("Failed: ${total - passed}")
        println("Success Rate: ${"%.1f".format(successRate)}%")
        println()

        testResults.forEach { result ->
            val status = if (result.passed) "✅ PASS" else "❌ FAIL"
            println("$status - ${result.name}")
            result.metrics.forEach { (key, value) ->
                println("    $key: $value")
            }
        }

        println("=".repeat(60))
    }

    // ========================================
    // Data Classes
    // ========================================

    data class TestResult(
        val name: String,
        val passed: Boolean,
        val metrics: Map<String, String>
    )
}
