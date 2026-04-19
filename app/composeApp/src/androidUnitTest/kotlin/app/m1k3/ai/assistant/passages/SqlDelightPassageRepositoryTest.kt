package app.m1k3.ai.assistant.passages

import app.m1k3.ai.assistant.test.TestDatabaseFactory
import app.m1k3.ai.domain.passages.Passage
import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.services.PassageEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for SqlDelightPassageRepository against an in-memory
 * JDBC SQLite database via TestDatabaseFactory.
 *
 * TDD: Phase 2 — storage impl for personal-knowledge passages.
 */
class SqlDelightPassageRepositoryTest {
    private lateinit var repository: SqlDelightPassageRepository

    @BeforeTest
    fun setUp() {
        val db = TestDatabaseFactory.createInMemoryDatabase()
        repository = SqlDelightPassageRepository(db, ioDispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() { /* in-memory DB gc'd with driver */ }

    private fun source(
        id: String = "src-1",
        kind: SourceKind = SourceKind.TEXT,
        chunkCount: Int = 0,
        importedAt: Long = 1_700_000_000_000L,
    ) = Source(
        id = id,
        uri = "note:$id",
        kind = kind,
        title = "Source $id",
        byteSize = 128,
        chunkCount = chunkCount,
        importedAt = importedAt,
    )

    private fun passage(
        id: String,
        sourceId: String,
        sequence: Int,
        content: String,
        total: Int,
    ) = Passage(
        id = id,
        sourceId = sourceId,
        sequence = sequence,
        content = content,
        tokenCount = content.length / 4,
        totalPassagesInSource = total,
    )

    @Test
    fun `saveSource persists source and its passages atomically`() =
        runTest {
            val src = source(id = "src-a", chunkCount = 2)
            val passages =
                listOf(
                    passage("src-a_p0", "src-a", 0, "alpha beta gamma", 2),
                    passage("src-a_p1", "src-a", 1, "delta epsilon", 2),
                )

            val result = repository.saveSource(src, passages)

            assertTrue(result.isSuccess)
            assertEquals(src, repository.getSource("src-a"))
            assertEquals(passages, repository.getPassages("src-a"))
        }

    @Test
    fun `getSource returns null for missing id`() =
        runTest {
            assertNull(repository.getSource("nope"))
        }

    @Test
    fun `getAllSources orders by importedAt descending`() =
        runTest {
            repository.saveSource(source(id = "old", importedAt = 1_000L), emptyList())
            repository.saveSource(source(id = "new", importedAt = 3_000L), emptyList())
            repository.saveSource(source(id = "mid", importedAt = 2_000L), emptyList())

            val ids = repository.getAllSources().map(Source::id)

            assertEquals(listOf("new", "mid", "old"), ids)
        }

    @Test
    fun `deleteSource cascades to passages`() =
        runTest {
            val src = source(id = "src-d", chunkCount = 1)
            val p = passage("src-d_p0", "src-d", 0, "body", 1)
            repository.saveSource(src, listOf(p))

            val delete = repository.deleteSource("src-d")

            assertTrue(delete.isSuccess)
            assertNull(repository.getSource("src-d"))
            assertTrue(repository.getPassages("src-d").isEmpty())
        }

    @Test
    fun `deleteSource is idempotent for unknown id`() =
        runTest {
            val result = repository.deleteSource("never-existed")
            assertTrue(result.isSuccess)
        }

    @Test
    fun `getPassages for unknown source returns empty list`() =
        runTest {
            assertTrue(repository.getPassages("ghost").isEmpty())
        }

    @Test
    fun `getPassages orders by sequence ascending`() =
        runTest {
            val src = source(id = "src-ord", chunkCount = 3)
            val out =
                listOf(
                    passage("p2", "src-ord", 2, "third", 3),
                    passage("p0", "src-ord", 0, "first", 3),
                    passage("p1", "src-ord", 1, "second", 3),
                )
            repository.saveSource(src, out)

            val fetched = repository.getPassages("src-ord")

            assertEquals(listOf(0, 1, 2), fetched.map(Passage::sequence))
        }

    @Test
    fun `searchPassages returns keyword matches up to limit`() =
        runTest {
            repository.saveSource(
                source(id = "src-s", chunkCount = 3),
                listOf(
                    passage("s0", "src-s", 0, "Kotlin is a language", 3),
                    passage("s1", "src-s", 1, "Java is also a language", 3),
                    passage("s2", "src-s", 2, "cats are cool", 3),
                ),
            )

            val kotlin = repository.searchPassages("Kotlin", 5)
            val language = repository.searchPassages("language", 1)
            val nothing = repository.searchPassages("nonexistent", 5)

            assertEquals(1, kotlin.size)
            assertEquals("s0", kotlin.first().id)
            assertEquals(1, language.size, "Limit must cap results")
            assertTrue(nothing.isEmpty())
        }

    @Test
    fun `searchPassages short-circuits on blank query and non-positive limit`() =
        runTest {
            repository.saveSource(
                source(id = "src-sc", chunkCount = 1),
                listOf(passage("sc0", "src-sc", 0, "anything", 1)),
            )

            assertTrue(repository.searchPassages("  ", 3).isEmpty())
            assertTrue(repository.searchPassages("anything", 0).isEmpty())
            assertTrue(repository.searchPassages("anything", -1).isEmpty())
        }

    @Test
    fun `saveSource with no passages persists source-only row`() =
        runTest {
            val src = source(id = "src-empty", chunkCount = 0)

            val result = repository.saveSource(src, emptyList())

            assertTrue(result.isSuccess)
            assertNotNull(repository.getSource("src-empty"))
            assertTrue(repository.getPassages("src-empty").isEmpty())
        }

    // ===== Embedding path =====

    /**
     * Deterministic stub. Maps a fixed set of texts to hand-picked 3-d vectors
     * so cosine ranking is predictable.
     */
    private class StubEmbedder(
        override val modelId: String = "stub-v1",
        private val vectors: Map<String, FloatArray> = emptyMap(),
        private val failOnTexts: Set<String> = emptySet(),
    ) : PassageEmbedder {
        override val dimension: Int = 3

        override suspend fun embed(text: String): FloatArray? {
            if (text in failOnTexts) return null
            return vectors[text] ?: floatArrayOf(0f, 0f, 0f)
        }
    }

    @Test
    fun `save with embedder ranks semantic search by cosine similarity`() =
        runTest {
            val vectors =
                mapOf(
                    "apple banana" to floatArrayOf(1f, 0f, 0f),
                    "grass leaves" to floatArrayOf(0f, 1f, 0f),
                    "fruit bowl" to floatArrayOf(0.9f, 0.1f, 0f),
                )
            val db = TestDatabaseFactory.createInMemoryDatabase()
            val repo =
                SqlDelightPassageRepository(
                    database = db,
                    embedder = StubEmbedder(vectors = vectors),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            repo.saveSource(
                source(id = "src-emb", chunkCount = 3),
                listOf(
                    passage("p0", "src-emb", 0, "apple banana", 3),
                    passage("p1", "src-emb", 1, "grass leaves", 3),
                    passage("p2", "src-emb", 2, "fruit bowl", 3),
                ),
            )

            val results = repo.searchPassages("fruit bowl", 3)

            assertEquals(3, results.size, "All passages scored; all returned")
            // "fruit bowl" queries with (0.9, 0.1, 0):
            //   "fruit bowl" itself → cos 1.0
            //   "apple banana"      → cos ≈ 0.994
            //   "grass leaves"      → cos ≈ 0.110
            assertEquals("p2", results[0].id, "Exact match ranks first")
            assertEquals("p0", results[1].id, "Closer neighbour second")
            assertEquals("p1", results[2].id, "Distant vector last")
        }

    @Test
    fun `search falls back to keyword when embedder returns null for query`() =
        runTest {
            val db = TestDatabaseFactory.createInMemoryDatabase()
            val repo =
                SqlDelightPassageRepository(
                    database = db,
                    embedder = StubEmbedder(failOnTexts = setOf("unembed me")),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            repo.saveSource(
                source(id = "src-fb", chunkCount = 1),
                listOf(passage("p0", "src-fb", 0, "contains unembed me somewhere", 1)),
            )

            val results = repo.searchPassages("unembed me", 5)

            assertEquals(1, results.size, "Keyword LIKE fallback still matches")
            assertEquals("p0", results[0].id)
        }

    @Test
    fun `search falls back to keyword when no rows have embeddings for the model`() =
        runTest {
            val db = TestDatabaseFactory.createInMemoryDatabase()

            // Save with NO embedder — rows have null embedding.
            val save = SqlDelightPassageRepository(db, ioDispatcher = Dispatchers.Unconfined)
            save.saveSource(
                source(id = "src-keyword", chunkCount = 1),
                listOf(passage("p0", "src-keyword", 0, "hello world", 1)),
            )

            // Now search with an embedder injected — there are zero embedded rows,
            // so semantic path short-circuits and keyword LIKE runs.
            val search =
                SqlDelightPassageRepository(
                    database = db,
                    embedder = StubEmbedder(vectors = mapOf("hello" to floatArrayOf(1f, 0f, 0f))),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val results = search.searchPassages("hello", 5)

            assertEquals(1, results.size)
            assertEquals("p0", results[0].id)
        }

    @Test
    fun `search skips rows saved with a different embedding model`() =
        runTest {
            val db = TestDatabaseFactory.createInMemoryDatabase()
            val oldModel =
                SqlDelightPassageRepository(
                    database = db,
                    embedder =
                        StubEmbedder(
                            modelId = "old-model",
                            vectors = mapOf("legacy" to floatArrayOf(1f, 0f, 0f)),
                        ),
                    ioDispatcher = Dispatchers.Unconfined,
                )
            oldModel.saveSource(
                source(id = "src-old", chunkCount = 1),
                listOf(passage("p0", "src-old", 0, "legacy", 1)),
            )

            // Re-open repo with a DIFFERENT model id. Semantic path must ignore
            // the mismatched embedding and degrade to keyword.
            val newModel =
                SqlDelightPassageRepository(
                    database = db,
                    embedder =
                        StubEmbedder(
                            modelId = "new-model",
                            vectors = mapOf("legacy" to floatArrayOf(1f, 0f, 0f)),
                        ),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val results = newModel.searchPassages("legacy", 5)

            // Keyword LIKE still finds the string, so we get one hit — just not ranked by cosine.
            assertEquals(1, results.size)
            assertEquals("p0", results[0].id)
        }
}
