package app.m1k3.ai.assistant.passages

import app.m1k3.ai.domain.memory.TokenCounter
import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.PassageRepository
import app.m1k3.ai.domain.passages.services.PassageChunker
import app.m1k3.ai.domain.passages.usecases.ImportTextUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DocumentsViewModel.
 *
 * Uses an inline in-memory [PassageRepository] fake. Shares no code with
 * shared/commonTest's FakePassageRepository because source sets are isolated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelTest {
    private class WordCounter : TokenCounter {
        override fun countTokens(text: String): Int = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
    }

    private class InMemoryPassageRepo : PassageRepository {
        val sources = mutableMapOf<String, Source>()
        val passagesBySource = mutableMapOf<String, List<Passage>>()
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

        override suspend fun getPassages(sourceId: String): List<Passage> = passagesBySource[sourceId].orEmpty()

        override suspend fun searchPassages(
            query: String,
            limit: Int,
        ): List<Passage> = emptyList()
    }

    private fun build(): Triple<DocumentsViewModel, InMemoryPassageRepo, TestScope> {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val repo = InMemoryPassageRepo()
        val importUc =
            ImportTextUseCase(
                chunker =
                    PassageChunker(
                        tokenCounter = WordCounter(),
                        maxChunkTokens = 100,
                        minChunkTokens = 1,
                    ),
                repository = repo,
                idProvider = { "src-${repo.sources.size + 1}" },
                clock = { (1_000_000 + repo.sources.size).toLong() },
            )
        val vm = DocumentsViewModel(importUc, repo, scope)
        return Triple(vm, repo, scope)
    }

    @Test
    fun `initial state is empty and not loading`() {
        val (vm, _, _) = build()
        val s = vm.state.value
        assertTrue(s.sources.isEmpty())
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `load populates sources from repository ordered by recency`() =
        runTest {
            val (vm, repo, scope) = build()
            repo.sources["old"] = Source("old", "note:old", SourceKind.TEXT, "Old", 0, 0, 1_000L)
            repo.sources["new"] = Source("new", "note:new", SourceKind.TEXT, "New", 0, 0, 3_000L)
            repo.sources["mid"] = Source("mid", "note:mid", SourceKind.TEXT, "Mid", 0, 0, 2_000L)

            vm.load()
            scope.advanceUntilIdle()

            assertEquals(
                listOf("new", "mid", "old"),
                vm.state.value.sources
                    .map(Source::id),
            )
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `import persists a source and refreshes state`() =
        runTest {
            val (vm, repo, scope) = build()

            vm.import("Notes", "paragraph one\n\nparagraph two", SourceKind.MARKDOWN)
            scope.advanceUntilIdle()

            assertEquals(1, repo.sources.size)
            assertEquals(1, vm.state.value.sources.size)
            assertEquals(
                "Notes",
                vm.state.value.sources
                    .first()
                    .title,
            )
            assertEquals(
                SourceKind.MARKDOWN,
                vm.state.value.sources
                    .first()
                    .kind,
            )
        }

    @Test
    fun `import derives title from first non blank line when blank`() =
        runTest {
            val (vm, repo, scope) = build()

            vm.import("   ", "\n  \nReal first line\ntrailing", SourceKind.TEXT)
            scope.advanceUntilIdle()

            assertEquals(
                "Real first line",
                repo.sources.values
                    .first()
                    .title,
            )
        }

    @Test
    fun `import surfaces errorMessage on failure`() =
        runTest {
            val (vm, repo, scope) = build()
            repo.failNextSave = IllegalStateException("disk full")

            vm.import("Title", "content", SourceKind.TEXT)
            scope.advanceUntilIdle()

            assertEquals("disk full", vm.state.value.errorMessage)
            assertEquals(0, repo.sources.size)
        }

    @Test
    fun `clearError resets errorMessage`() =
        runTest {
            val (vm, repo, scope) = build()
            repo.failNextSave = IllegalStateException("boom")
            vm.import("t", "c", SourceKind.TEXT)
            scope.advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            vm.clearError()

            assertNull(vm.state.value.errorMessage)
        }

    @Test
    fun `delete removes a source and refreshes state`() =
        runTest {
            val (vm, repo, scope) = build()
            repo.sources["a"] = Source("a", "note:a", SourceKind.TEXT, "A", 0, 0, 1_000L)
            repo.sources["b"] = Source("b", "note:b", SourceKind.TEXT, "B", 0, 0, 2_000L)
            vm.load()
            scope.advanceUntilIdle()

            vm.delete("a")
            scope.advanceUntilIdle()

            assertEquals(
                listOf("b"),
                vm.state.value.sources
                    .map(Source::id),
            )
            assertFalse(repo.sources.containsKey("a"))
        }

    @Test
    fun `empty state flag reflects no sources after load`() =
        runTest {
            val (vm, _, scope) = build()

            vm.load()
            scope.advanceUntilIdle()

            assertTrue(vm.state.value.isEmpty)
        }
}
