package app.m1k3.ai.domain.passages.usecases

import app.m1k3.ai.domain.memory.TokenCounter
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.FakePassageRepository
import app.m1k3.ai.domain.passages.services.PassageChunker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for ImportTextUseCase.
 *
 * TDD: Phase 1 — domain orchestration of chunking + persistence.
 */
class ImportTextUseCaseTest {
    private class WordCounter : TokenCounter {
        override fun countTokens(text: String): Int = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
    }

    private fun useCase(
        repository: FakePassageRepository = FakePassageRepository(),
        fixedId: String = "src-fixed",
        fixedClock: Long = 1_700_000_000_000L,
    ): Pair<ImportTextUseCase, FakePassageRepository> {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 100,
                minChunkTokens = 1,
            )
        val uc =
            ImportTextUseCase(
                chunker = chunker,
                repository = repository,
                idProvider = { fixedId },
                clock = { fixedClock },
            )
        return uc to repository
    }

    @Test
    fun `blank content is rejected with a failure result`() =
        runTest {
            val (uc, repo) = useCase()

            val result =
                uc.execute(
                    title = "Empty",
                    content = "   \n  ",
                    kind = SourceKind.TEXT,
                    uri = "note:empty",
                )

            assertTrue(result.isFailure, "Blank content should not persist")
            assertIs<IllegalArgumentException>(result.exceptionOrNull())
            assertEquals(0, repo.getAllSources().size, "Nothing should be persisted on rejection")
        }

    @Test
    fun `successful import persists source and chunks`() =
        runTest {
            val (uc, repo) = useCase(fixedId = "src-99", fixedClock = 42L)

            val content = "paragraph one\n\nparagraph two"
            val result =
                uc.execute(
                    title = "My notes",
                    content = content,
                    kind = SourceKind.MARKDOWN,
                    uri = "note:mynotes",
                )

            assertTrue(result.isSuccess, "Happy path should succeed")
            val source = result.getOrNull()
            assertNotNull(source)
            assertEquals("src-99", source.id)
            assertEquals("My notes", source.title)
            assertEquals(SourceKind.MARKDOWN, source.kind)
            assertEquals("note:mynotes", source.uri)
            assertEquals(42L, source.importedAt)
            assertEquals(content.encodeToByteArray().size, source.byteSize)

            val persisted = repo.getSource("src-99")
            assertNotNull(persisted)
            assertEquals(source.chunkCount, persisted.chunkCount)

            val passages = repo.getPassages("src-99")
            assertEquals(source.chunkCount, passages.size)
            assertTrue(passages.isNotEmpty(), "At least one passage expected")
            passages.forEach { p ->
                assertEquals("src-99", p.sourceId)
                assertEquals(passages.size, p.totalPassagesInSource)
            }
        }

    @Test
    fun `repository failure is propagated and no source leaks`() =
        runTest {
            val repo =
                FakePassageRepository().apply {
                    failNextSave = IllegalStateException("disk full")
                }
            val (uc, _) = useCase(repository = repo)

            val result =
                uc.execute(
                    title = "Notes",
                    content = "anything goes here",
                    kind = SourceKind.TEXT,
                    uri = "note:a",
                )

            assertTrue(result.isFailure)
            assertEquals("disk full", result.exceptionOrNull()?.message)
            assertEquals(0, repo.getAllSources().size, "Failed save must not leave orphans")
        }
}
