package app.m1k3.ai.domain.passages.usecases

import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.FakePassageRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for RetrievePassagesUseCase.
 *
 * TDD: Phase 1 — thin domain wrapper with guardrails.
 */
class RetrievePassagesUseCaseTest {
    private suspend fun seed(
        repo: FakePassageRepository,
        vararg contents: String,
    ) {
        val passages =
            contents.mapIndexed { i, text ->
                Passage(
                    id = "p$i",
                    sourceId = "src-seed",
                    sequence = i,
                    content = text,
                    tokenCount = text.length / 4,
                    totalPassagesInSource = contents.size,
                )
            }
        val source =
            Source(
                id = "src-seed",
                uri = "note:seed",
                kind = SourceKind.TEXT,
                title = "Seed",
                byteSize = 0,
                chunkCount = contents.size,
                importedAt = 0L,
            )
        repo.saveSource(source, passages)
    }

    @Test
    fun `blank query short circuits to empty`() =
        runTest {
            val repo = FakePassageRepository()
            seed(repo, "photosynthesis is cool")
            val uc = RetrievePassagesUseCase(repo)

            assertTrue(uc.execute("   ", 3).isEmpty())
            assertTrue(uc.execute("", 3).isEmpty())
        }

    @Test
    fun `non positive limit short circuits to empty`() =
        runTest {
            val repo = FakePassageRepository()
            seed(repo, "photosynthesis is cool")
            val uc = RetrievePassagesUseCase(repo)

            assertTrue(uc.execute("cool", 0).isEmpty())
            assertTrue(uc.execute("cool", -5).isEmpty())
        }

    @Test
    fun `delegates to repository and returns matched passages`() =
        runTest {
            val repo = FakePassageRepository()
            seed(repo, "photosynthesis is cool", "kotlin is also cool", "unrelated passage")
            val uc = RetrievePassagesUseCase(repo)

            val results = uc.execute("cool", 5)

            assertEquals(2, results.size, "Two passages match 'cool'")
            assertTrue(results.all { it.content.contains("cool") })
        }

    @Test
    fun `limit is honored`() =
        runTest {
            val repo = FakePassageRepository()
            seed(repo, "cool one", "cool two", "cool three")
            val uc = RetrievePassagesUseCase(repo)

            val results = uc.execute("cool", 2)

            assertEquals(2, results.size)
        }
}
