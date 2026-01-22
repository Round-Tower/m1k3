package app.m1k3.ai.assistant.mocks

import app.m1k3.ai.assistant.domain.rag.Intent
import app.m1k3.ai.assistant.rag.RAGManager

/**
 * Mock implementation of RAGManager for testing.
 *
 * Provides predictable RAG responses without requiring
 * actual database or embedding engine.
 *
 * **Usage:**
 * ```kotlin
 * val mockRag = MockRAGManager()
 *
 * // Configure response
 * mockRag.setRagResult(
 *     enrichedPrompt = "Knowledge about X...",
 *     intent = Intent.SCIENCE,
 *     confidence = 0.85f,
 *     facts = listOf(...)
 * )
 *
 * val result = mockRag.enrichPrompt("query", "system prompt")
 * // Returns configured result
 * ```
 */
class MockRAGManager {
    // Configurable responses
    private var enrichedPrompt: String = ""
    private var intent: Intent = Intent.GENERAL
    private var confidence: Float = 0f
    private var retrievedFacts: List<RAGManager.RetrievedFact> = emptyList()
    private var ragApplied: Boolean = false

    // Tracking
    var enrichPromptCallCount = 0
        private set
    var lastUserQuery: String? = null
        private set
    var lastSystemPrompt: String? = null
        private set
    var lastEnableRAG: Boolean? = null
        private set

    // Error simulation
    private var shouldThrowError: Boolean = false
    private var errorToThrow: Exception? = null

    /**
     * Configure the RAG result to return.
     */
    fun setRagResult(
        enrichedPrompt: String,
        intent: Intent = Intent.GENERAL,
        confidence: Float = 0.8f,
        facts: List<RAGManager.RetrievedFact> = emptyList(),
        ragApplied: Boolean = true
    ) {
        this.enrichedPrompt = enrichedPrompt
        this.intent = intent
        this.confidence = confidence
        this.retrievedFacts = facts
        this.ragApplied = ragApplied
    }

    /**
     * Configure to return no RAG results.
     */
    fun setNoRagResults() {
        this.enrichedPrompt = ""
        this.intent = Intent.GENERAL
        this.confidence = 0f
        this.retrievedFacts = emptyList()
        this.ragApplied = false
    }

    /**
     * Configure to throw an error.
     */
    fun setError(error: Exception) {
        this.shouldThrowError = true
        this.errorToThrow = error
    }

    /**
     * Simulate enrichPrompt method from RAGManager.
     */
    suspend fun enrichPrompt(
        userQuery: String,
        systemPrompt: String,
        enableRAG: Boolean = true
    ): RAGManager.RAGResult {
        enrichPromptCallCount++
        lastUserQuery = userQuery
        lastSystemPrompt = systemPrompt
        lastEnableRAG = enableRAG

        if (shouldThrowError) {
            throw errorToThrow ?: RuntimeException("Mock error")
        }

        if (!enableRAG) {
            return RAGManager.RAGResult(
                enrichedPrompt = systemPrompt,
                intent = Intent.GENERAL,
                confidence = 0f,
                retrievedFacts = emptyList(),
                ragApplied = false
            )
        }

        return RAGManager.RAGResult(
            enrichedPrompt = enrichedPrompt,
            intent = intent,
            confidence = confidence,
            retrievedFacts = retrievedFacts,
            ragApplied = ragApplied
        )
    }

    /**
     * Format RAG sources for display.
     */
    fun formatRAGSources(facts: List<RAGManager.RetrievedFact>): String? {
        if (facts.isEmpty()) return null
        return facts.joinToString("; ") { "${it.category}: ${it.content.take(50)}..." }
    }

    /**
     * Calculate RAG confidence.
     */
    fun calculateRAGConfidence(facts: List<RAGManager.RetrievedFact>): Double? {
        if (facts.isEmpty()) return null
        return facts.map { it.similarity.toDouble() }.average()
    }

    /**
     * Reset all state.
     */
    fun reset() {
        enrichedPrompt = ""
        intent = Intent.GENERAL
        confidence = 0f
        retrievedFacts = emptyList()
        ragApplied = false
        enrichPromptCallCount = 0
        lastUserQuery = null
        lastSystemPrompt = null
        lastEnableRAG = null
        shouldThrowError = false
        errorToThrow = null
    }

    companion object {
        /**
         * Create mock with science facts.
         */
        fun withScienceFacts() = MockRAGManager().apply {
            setRagResult(
                enrichedPrompt = "Knowledge: Photosynthesis converts sunlight to energy.",
                intent = Intent.SCIENCE,
                confidence = 0.85f,
                facts = listOf(
                    RAGManager.RetrievedFact(
                        content = "Photosynthesis converts sunlight to energy",
                        category = "science_facts",
                        similarity = 0.9f
                    )
                )
            )
        }

        /**
         * Create mock with code debug facts.
         */
        fun withCodeDebugFacts() = MockRAGManager().apply {
            setRagResult(
                enrichedPrompt = "Knowledge: NullPointerException occurs when...",
                intent = Intent.CODE_DEBUG,
                confidence = 0.75f,
                facts = listOf(
                    RAGManager.RetrievedFact(
                        content = "NullPointerException occurs when accessing null reference",
                        category = "code_debug",
                        similarity = 0.8f
                    )
                )
            )
        }
    }
}
