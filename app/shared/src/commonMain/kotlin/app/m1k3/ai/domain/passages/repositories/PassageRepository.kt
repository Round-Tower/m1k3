package app.m1k3.ai.domain.passages.repositories

import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source

/**
 * PassageRepository — storage contract for [Source] + [Passage] pairs.
 *
 * Domain interface. Pure Kotlin, no platform dependencies.
 * Platform implementations wrap SQLDelight (or equivalent) and an
 * embedding/search backend.
 *
 * **Consistency guarantee:** [saveSource] is transactional — a Source and
 * its Passages must commit together or not at all. Orphan passages (without
 * their parent source) are forbidden.
 *
 * **Retrieval:** [searchPassages] is deliberately agnostic about whether
 * the implementation is keyword-based, semantic (embedding-backed), or
 * hybrid. Domain callers only promise to pass a query and expect relevance-
 * ordered results.
 */
interface PassageRepository {
    /**
     * Persist a source and its passages atomically.
     *
     * @param source The source metadata.
     * @param passages Passages owned by [source]. May be empty for a
     *                 placeholder source (chunkCount must then be 0).
     * @return Result of the write. Failure reasons are implementation-
     *         specific (I/O, constraint violation, encryption).
     */
    suspend fun saveSource(
        source: Source,
        passages: List<Passage>,
    ): Result<Unit>

    /**
     * Fetch a single source by id, or null if not found.
     */
    suspend fun getSource(id: String): Source?

    /**
     * Fetch all sources, most recently imported first.
     */
    suspend fun getAllSources(): List<Source>

    /**
     * Delete a source and cascade to its passages.
     *
     * @return Result of the delete. Success for a missing id is allowed;
     *         implementations should make delete idempotent.
     */
    suspend fun deleteSource(id: String): Result<Unit>

    /**
     * Fetch passages owned by [sourceId], ordered by [Passage.sequence].
     */
    suspend fun getPassages(sourceId: String): List<Passage>

    /**
     * Retrieve passages relevant to [query], relevance-ordered, capped at [limit].
     *
     * @param query The user's query text.
     * @param limit Maximum passages to return (caller respects budget).
     */
    suspend fun searchPassages(
        query: String,
        limit: Int = 3,
    ): List<Passage>
}
