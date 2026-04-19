package app.m1k3.ai.domain.passages.repositories

import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source

/**
 * In-memory fake [PassageRepository] for use-case unit tests.
 *
 * Lives in commonTest so every domain test has a consistent, deterministic
 * storage layer without touching SQLDelight.
 */
class FakePassageRepository : PassageRepository {
    private val sources = mutableMapOf<String, Source>()
    private val passagesBySource = mutableMapOf<String, List<Passage>>()

    var failNextSave: Throwable? = null

    override suspend fun saveSource(
        source: Source,
        passages: List<Passage>,
    ): Result<Unit> {
        failNextSave?.let {
            failNextSave = null
            return Result.failure(it)
        }
        sources[source.id] = source
        passagesBySource[source.id] = passages
        return Result.success(Unit)
    }

    override suspend fun getSource(id: String): Source? = sources[id]

    override suspend fun getAllSources(): List<Source> = sources.values.sortedByDescending(Source::importedAt)

    override suspend fun deleteSource(id: String): Result<Unit> {
        sources.remove(id)
        passagesBySource.remove(id)
        return Result.success(Unit)
    }

    override suspend fun getPassages(sourceId: String): List<Passage> = passagesBySource[sourceId].orEmpty().sortedBy(Passage::sequence)

    override suspend fun searchPassages(
        query: String,
        limit: Int,
    ): List<Passage> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return passagesBySource.values
            .flatten()
            .filter { it.content.lowercase().contains(needle) }
            .take(limit)
    }
}
