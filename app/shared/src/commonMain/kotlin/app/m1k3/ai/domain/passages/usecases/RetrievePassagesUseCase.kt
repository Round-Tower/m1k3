package app.m1k3.ai.domain.passages.usecases

import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.repositories.PassageRepository

/**
 * RetrievePassagesUseCase — ask the repository for passages relevant to a query.
 *
 * Domain use case. Pure Kotlin, no platform dependencies.
 *
 * Guardrails applied here (so platform impls don't duplicate them):
 * - Blank queries short-circuit to empty.
 * - Non-positive limits short-circuit to empty.
 *
 * Relevance semantics (keyword vs. semantic) live in the repository impl.
 *
 * @param repository Storage contract for passage retrieval.
 */
class RetrievePassagesUseCase(
    private val repository: PassageRepository,
) {
    /**
     * Retrieve passages relevant to [query].
     *
     * @param query User query text.
     * @param limit Max passages to return (default 3).
     * @return Relevance-ordered passages, or empty list if guardrails trip.
     */
    suspend fun execute(
        query: String,
        limit: Int = 3,
    ): List<Passage> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return repository.searchPassages(query, limit)
    }
}
