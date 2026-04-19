package app.m1k3.ai.assistant.passages

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.PassageRepository
import app.m1k3.ai.domain.passages.services.PassageEmbedder
import app.m1k3.ai.domain.passages.services.cosineSimilarity
import app.m1k3.ai.domain.rag.services.EmbeddingSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * 2. Otherwise fall back to `LIKE '%query%'` — works for any row, including
 *    those saved before embeddings were wired.
 *
 * **Save strategy:** if [embedder] is present, each passage's content is
 * embedded inline and stored alongside the row. Embedding failure is
 * tolerated — the passage is still persisted with a null embedding.
 */
class SqlDelightPassageRepository(
    private val database: MaDatabase,
    private val embedder: PassageEmbedder? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PassageRepository {
    override suspend fun saveSource(
        source: Source,
        passages: List<Passage>,
    ): Result<Unit> =
        runCatching {
            withContext(ioDispatcher) {
                // Embed outside the transaction — embedding can be slow and
                // we don't want to hold a DB write lock while we wait.
                val embeddings: Map<String, ByteArray?> =
                    if (embedder == null) {
                        emptyMap()
                    } else {
                        passages.associate { p ->
                            p.id to embedder.embed(p.content)?.let(EmbeddingSerializer::serialize)
                        }
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
                        val bytes = embeddings[passage.id]
                        database.passageQueries.insertPassage(
                            id = passage.id,
                            source_id = passage.sourceId,
                            sequence = passage.sequence.toLong(),
                            content = passage.content,
                            token_count = passage.tokenCount.toLong(),
                            total_passages_in_source = passage.totalPassagesInSource.toLong(),
                            embedding = bytes,
                            embedding_model = if (bytes != null) modelId else null,
                        )
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
                // Explicit two-step delete, wrapped in a transaction. Mirrors
                // the FK cascade defined in Passage.sq but stays robust even
                // when `PRAGMA foreign_keys = ON` isn't asserted on the driver
                // connection (notably the JDBC in-memory driver used in tests).
                database.transaction {
                    database.passageQueries.deletePassagesBySource(id)
                    database.sourceQueries.deleteSourceById(id)
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

        // Try the semantic path first. We need the RAW FloatArray for cosine
        // math — no serialize/deserialize round-trip for the query vector.
        val queryEmbedding: FloatArray? = embedder?.embed(query)
        val modelId = embedder?.modelId
        if (queryEmbedding != null && modelId != null) {
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
            // Fall through to keyword if no embedded matches.
        }

        return withContext(ioDispatcher) {
            database.passageQueries
                .searchPassagesByKeyword(query = query, max = limit.toLong())
                .executeAsList()
                .map { it.toDomain() }
        }
    }

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
