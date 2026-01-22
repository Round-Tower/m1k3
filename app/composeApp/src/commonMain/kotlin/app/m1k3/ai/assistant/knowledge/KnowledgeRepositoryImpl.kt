package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.domain.rag.RetrievedFact
import app.m1k3.ai.assistant.domain.repositories.KnowledgeRepository

/**
 * KnowledgeRepositoryImpl - Implementation of domain KnowledgeRepository
 *
 * Wrapper around SemanticRetrievalService to conform to the domain interface.
 *
 * **Architecture:**
 * - Domain layer defines "what" (KnowledgeRepository interface)
 * - Application layer implements "how" (SemanticRetrievalService)
 * - This adapter bridges the two layers
 *
 * **Mapping:**
 * - SemanticRetrievedFact → domain.rag.RetrievedFact
 * - TriviaFact fields → simplified domain entity
 * - similarityScore → similarity (Float)
 */
class KnowledgeRepositoryImpl(
    private val semanticRetrievalService: SemanticRetrievalService
) : KnowledgeRepository {

    override suspend fun retrieve(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): Result<List<RetrievedFact>> {
        return try {
            // Call semantic retrieval service
            val semanticFacts = semanticRetrievalService.retrieve(
                query = query,
                limit = limit,
                minSimilarity = minSimilarity
            )

            // Map to domain entities
            val domainFacts = semanticFacts.map { semanticFact ->
                RetrievedFact(
                    content = semanticFact.fact.answer, // TriviaFact has 'answer', not 'content'
                    category = semanticFact.fact.category,
                    similarity = semanticFact.similarityScore
                )
            }

            Result.success(domainFacts)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
