package app.m1k3.ai.domain.passages.usecases

import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.PassageRepository
import app.m1k3.ai.domain.passages.services.PassageChunker

/**
 * ImportTextUseCase — take raw text, chunk it into passages, persist as a [Source].
 *
 * Domain use case. Pure Kotlin, no platform dependencies.
 *
 * **Pipeline:**
 * ```
 * 1. Validate inputs (content must not be blank).
 * 2. Mint a source id via [idProvider].
 * 3. Chunk content via [chunker].
 * 4. Build Source metadata with chunkCount = passages.size.
 * 5. Persist atomically via [repository.saveSource].
 * ```
 *
 * Embedding/indexing is the repository implementation's responsibility —
 * the use case stays transport-agnostic.
 *
 * @param chunker Paragraph-aware chunker.
 * @param repository Storage contract.
 * @param idProvider Strategy for minting source ids (inject UUID source for tests).
 * @param clock Strategy for epoch-millis timestamps (inject for tests).
 */
class ImportTextUseCase(
    private val chunker: PassageChunker,
    private val repository: PassageRepository,
    private val idProvider: () -> String,
    private val clock: () -> Long,
) {
    /**
     * Import [content] as a new [Source] of the given [kind].
     *
     * @param title Human-readable title (filename for files, user label for pastes).
     * @param content The raw text.
     * @param kind Source kind (TEXT or MARKDOWN for day one).
     * @param uri Addressable origin (file://…, note:…, etc.).
     * @return Result wrapping the persisted Source, or failure on empty
     *         content / storage error.
     */
    suspend fun execute(
        title: String,
        content: String,
        kind: SourceKind,
        uri: String,
    ): Result<Source> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Content is blank"))
        }

        val sourceId = idProvider()
        val passages = chunker.chunk(sourceId, content)

        val source =
            Source(
                id = sourceId,
                uri = uri,
                kind = kind,
                title = title,
                byteSize = content.encodeToByteArray().size,
                chunkCount = passages.size,
                importedAt = clock(),
            )

        return repository.saveSource(source, passages).map { source }
    }
}
