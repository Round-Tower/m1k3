package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.RetrievedFact
import app.m1k3.ai.domain.usecases.rag.RAGResult

/**
 * RAG Result Utilities
 *
 * Extension functions for RAGResult and List<RetrievedFact> to format
 * sources and calculate confidence. Extracted from RAGManager for domain layer.
 *
 * @see RAGResult
 * @see RetrievedFact
 */

private const val PREVIEW_LENGTH = 60

/**
 * Format retrieved facts as a human-readable source list.
 *
 * Shows preview of fact content (first 60 chars) with similarity score.
 *
 * @return Formatted source list, or null if no facts
 */
fun RAGResult.formatSources(): String? {
    return retrievedFacts.formatRAGSources()
}

/**
 * Calculate average confidence from retrieved facts.
 *
 * @return Average similarity (0.0-1.0), or null if no facts
 */
fun RAGResult.calculateConfidence(): Double? {
    return retrievedFacts.calculateRAGConfidence()
}

/**
 * Format list of retrieved facts as source citations.
 *
 * Shows preview of fact content (first 60 chars) with similarity score.
 *
 * @return Formatted source list, or null if empty
 */
fun List<RetrievedFact>.formatRAGSources(): String? {
    if (isEmpty()) return null

    return mapIndexed { index, fact ->
        val preview = if (fact.content.length > PREVIEW_LENGTH) {
            fact.content.take(PREVIEW_LENGTH) + "..."
        } else {
            fact.content
        }
        "${index + 1}. $preview (${(fact.similarity * 100).toInt()}%)"
    }.joinToString(separator = "\n")
}

/**
 * Calculate average confidence from list of facts.
 *
 * @return Average similarity (0.0-1.0), or null if empty
 */
fun List<RetrievedFact>.calculateRAGConfidence(): Double? {
    if (isEmpty()) return null
    return map { it.similarity.toDouble() }.average()
}
