package app.m1k3.ai.assistant.passages

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.PassageRepository
import app.m1k3.ai.domain.passages.services.PassageEmbedder
import app.m1k3.ai.domain.passages.services.VectorIndex
import app.m1k3.ai.domain.passages.services.cosineSimilarity
import app.m1k3.ai.domain.rag.services.EmbeddingSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.m1k3.ai.assistant.database.Passage as PassageRow
import app.m1k3.ai.assistant.database.Source as SourceRow

/**
 * SqlDelightPassageRepository — SQLDelight-backed [PassageRepository].
 *
 * Storage impl for the personal-knowledge passage layer. Lives in `commonMain`
 * so it's shareable across platforms; the platform-specific driver (SQLCipher
 * on Android, JDBC for tests) is injected via [MaDatabase].
 *
 * **Transactionality:** [saveSource] wraps the Source insert + per-Passage
 * inserts in a single SQLDelight transaction — orphan passages are prevented.
 *
 * **Search strategy:**
 * 1. If [embedder] is present AND the query embeds successfully AND there are
 *    rows with embeddings for the same model, rank by cosine similarity.
 *    - When [vectorIndex] is supplied, the cosine math runs over the index's
 *      cached vectors and only the top-K passage rows are pulled from the DB.
 *    - When [vectorIndex] is null, the previous linear-scan-over-all-rows
 *      path runs (unchanged). Revert-safe.
 * 2. Otherwise fall back to `LIKE '%query%'` — works for any row, including
 *    those saved before embeddings were wired.
 *
 * **Save strategy:** if [embedder] is present, each passage's content is
 * embedded inline and stored alongside the row. Embedding failure is
 * tolerated — the passage is still persisted with a null embedding. When a
 * [vectorIndex] is wired, successful embeddings are mirrored into it so
 * subsequent searches hit the cache directly.
 *
 * **Index warmup:** the index is hydrated lazily on the first search that
 * needs it (read from all embedded rows, add into the index). Keeps cold-
 * start fast and avoids doing work the user may not need.
 */
class SqlDelightPassageRepository(
    private val database: MaDatabase,
    private val embedder: PassageEmbedder? = null,
    private val vectorIndex: VectorIndex? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PassageRepository {
    private val warmupMutex = Mutex()

    @Volatile private var indexWarmed = false

    override suspend fun saveSource(
        source: Source,
        passages: List<Passage>,
    ): Result<Unit> =
        runCatching {
            withContext(ioDispatcher) {
                // Embed outside the transaction — embedding can be slow and
                // we don't want to hold a DB write lock while we wait.
                val vectors: Map<String, FloatArray?> =
                    if (embedder == null) {
                        emptyMap()
                    } else {
                        passages.associate { p -> p.id to embedder.embed(p.content) }
                    }
                val modelId = embedder?.modelId

                database.transaction {
                    database.sourceQueries.insertSource(
                        id = source.id,
                        uri = source.uri,
                        kind = source.kind.name,
                        title = source.title,
                        byte_size = source.byteSize.toLong(),
                        chunk_count = source.chunkCount.toLong(),
                        imported_at = source.importedAt,
                    )
                    passages.forEach { passage ->
                        val vec = vectors[passage.id]
                        val bytes = vec?.let(EmbeddingSerializer::serialize)
                        database.passageQueries.insertPassage(
                            id = passage.id,
                            source_id = passage.sourceId,
                            sequence = passage.sequence.toLong(),
                            content = passage.content,
                            token_count = passage.token_count_or_dim(),
                            total_passages_in_source = passage.totalPassagesInSource.toLong(),
                            embedding = bytes,
                            embedding_model = if (bytes != null) modelId else null,
                        )
                    }
                }

                // Mirror successful embeddings into the index. Done outside the
                // transaction because VectorIndex ops are suspend and may lock.
                if (vectorIndex != null && modelId != null) {
                    vectors.forEach { (id, vec) ->
                        if (vec != null) vectorIndex.add(id, vec, modelId)
                    }
                }
            }
        }

    override suspend fun getSource(id: String): Source? =
        withContext(ioDispatcher) {
            database.sourceQueries
                .getSourceById(id)
                .executeAsOneOrNull()
                ?.toDomain()
        }

    override suspend fun getAllSources(): List<Source> =
        withContext(ioDispatcher) {
            database.sourceQueries
                .getAllSources()
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun deleteSource(id: String): Result<Unit> =
        runCatching {
            withContext(ioDispatcher) {
                // Pull passage IDs before the DB cascade wipes them so we can
                // also evict them from the vector index.
                val ids: List<String> =
                    if (vectorIndex != null) {
                        database.passageQueries
                            .getPassagesBySource(id)
                            .executeAsList()
                            .map { it.id }
                    } else {
                        emptyList()
                    }

                // Explicit two-step delete, wrapped in a transaction. Mirrors
                // the FK cascade defined in Passage.sq but stays robust even
                // when `PRAGMA foreign_keys = ON` isn't asserted on the driver
                // connection (notably the JDBC in-memory driver used in tests).
                database.transaction {
                    database.passageQueries.deletePassagesBySource(id)
                    database.sourceQueries.deleteSourceById(id)
                }

                if (vectorIndex != null) {
                    ids.forEach { vectorIndex.remove(it) }
                }
            }
        }

    override suspend fun getPassages(sourceId: String): List<Passage> =
        withContext(ioDispatcher) {
            database.passageQueries
                .getPassagesBySource(sourceId)
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun searchPassages(
        query: String,
        limit: Int,
    ): List<Passage> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val queryEmbedding: FloatArray? = embedder?.embed(query)
        val modelId = embedder?.modelId

        // === Index-backed semantic path ===
        if (queryEmbedding != null && modelId != null && vectorIndex != null) {
            warmIndexIfNeeded(modelId)
            val hits = vectorIndex.search(queryEmbedding, modelId, limit)
            if (hits.isNotEmpty()) {
                return fetchPassagesInRankOrder(hits.map { it.id })
            }
            // Fall through to keyword.
        }

        // === Legacy full-scan semantic path (no index wired) ===
        if (queryEmbedding != null && modelId != null && vectorIndex == null) {
            val ranked: List<Passage> =
                withContext(ioDispatcher) {
                    val rows = database.passageQueries.getAllEmbeddedPassages().executeAsList()
                    rows
                        .mapNotNull { row ->
                            val bytes = row.embedding ?: return@mapNotNull null
                            if (row.embedding_model != modelId) return@mapNotNull null
                            val vec = EmbeddingSerializer.deserialize(bytes)
                            val score = cosineSimilarity(queryEmbedding, vec)
                            if (score <= 0f) {
                                null
                            } else {
                                Passage(
                                    id = row.id,
                                    sourceId = row.source_id,
                                    sequence = row.sequence.toInt(),
                                    content = row.content,
                                    tokenCount = row.token_count.toInt(),
                                    totalPassagesInSource = row.total_passages_in_source.toInt(),
                                ) to score
                            }
                        }.sortedByDescending { it.second }
                        .take(limit)
                        .map { it.first }
                }
            if (ranked.isNotEmpty()) return ranked
            // Fall through to keyword.
        }

        // === Keyword fallback ===
        return withContext(ioDispatcher) {
            database.passageQueries
                .searchPassagesByKeyword(query = query, max = limit.toLong())
                .executeAsList()
                .map { it.toDomain() }
        }
    }

    /**
     * Fetch rows for the given passage IDs and return them in the same order
     * as [idsInRankOrder]. SQLite's `IN (...)` clause doesn't preserve input
     * order, so the reorder happens in Kotlin.
     */
    private suspend fun fetchPassagesInRankOrder(idsInRankOrder: List<String>): List<Passage> {
        if (idsInRankOrder.isEmpty()) return emptyList()
        val rows =
            withContext(ioDispatcher) {
                database.passageQueries
                    .getPassagesByIds(idsInRankOrder)
                    .executeAsList()
            }
        val byId = rows.associateBy { it.id }
        return idsInRankOrder.mapNotNull { id -> byId[id]?.toDomain() }
    }

    /**
     * Hydrate the index on first search. Reads every row that has an embedding
     * for the current model, deserializes vectors, and bulk-loads them into
     * the index. Idempotent; subsequent calls short-circuit.
     */
    private suspend fun warmIndexIfNeeded(modelId: String) {
        if (indexWarmed || vectorIndex == null) return
        warmupMutex.withLock {
            if (indexWarmed) return@withLock
            val entries: List<VectorIndex.Entry> =
                withContext(ioDispatcher) {
                    database.passageQueries
                        .getAllEmbeddedPassages()
                        .executeAsList()
                        .mapNotNull { row ->
                            val bytes = row.embedding ?: return@mapNotNull null
                            val rowModel = row.embedding_model ?: return@mapNotNull null
                            if (rowModel != modelId) return@mapNotNull null
                            VectorIndex.Entry(
                                id = row.id,
                                vector = EmbeddingSerializer.deserialize(bytes),
                                modelId = rowModel,
                            )
                        }
                }
            vectorIndex.rebuild(entries)
            indexWarmed = true
        }
    }

    /** Domain Passage doesn't expose a way to get the original tokenCount as a
     *  Long, but the property on the data class is an Int — promote it here to
     *  the DB column type. Kept as a helper so the save path stays readable. */
    private fun Passage.token_count_or_dim(): Long = tokenCount.toLong()

    private fun SourceRow.toDomain(): Source =
        Source(
            id = id,
            uri = uri,
            kind = runCatching { SourceKind.valueOf(kind) }.getOrDefault(SourceKind.TEXT),
            title = title,
            byteSize = byte_size.toInt(),
            chunkCount = chunk_count.toInt(),
            importedAt = imported_at,
        )

    private fun PassageRow.toDomain(): Passage =
        Passage(
            id = id,
            sourceId = source_id,
            sequence = sequence.toInt(),
            content = content,
            tokenCount = token_count.toInt(),
            totalPassagesInSource = total_passages_in_source.toInt(),
        )
}
