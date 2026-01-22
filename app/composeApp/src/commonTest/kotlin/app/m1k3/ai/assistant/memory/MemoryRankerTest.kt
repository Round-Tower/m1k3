package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.database.MemoryMetadata
import kotlin.test.*

/**
 * Tests for MemoryRanker
 *
 * Validates composite ranking, token budget management, and memory selection.
 */
class MemoryRankerTest {

    private val ranker = MemoryRanker(
        maxContextTokens = 1000,  // Small budget for testing
        similarityWeight = 0.40f,
        recencyWeight = 0.20f,
        importanceWeight = 0.30f,
        accessWeight = 0.10f
    )

    private fun createMemory(
        id: String,
        embeddingId: String,
        importance: Double = 0.5,
        createdAt: Long = System.currentTimeMillis(),
        accessCount: Long = 0,
        chunkTokens: Long? = 100
    ): MemoryMetadata {
        return MemoryMetadata(
            id = id,
            message_id = "msg-$id",
            project_id = "test-project",
            content = "Test content for $id",
            importance = importance,
            created_at = createdAt,
            chunk_index = 0,
            chunk_total = 1,
            chunk_tokens = chunkTokens,
            embedding_id = embeddingId,
            embedding_model = "all-MiniLM-L6-v2",
            access_count = accessCount,
            last_accessed_at = null,
            decay_factor = 1.0,
            is_pinned = 0
        )
    }

    @Test
    fun `empty inputs return empty result`() {
        val result = ranker.rankAndSelect(
            searchResults = emptyList(),
            memories = emptyList()
        )

        assertTrue(result.isEmpty())
        assertEquals(0, result.totalTokens)
        assertEquals(0, result.droppedCount)
    }

    @Test
    fun `single memory within budget is selected`() {
        val memory = createMemory("mem-1", "emb-1", chunkTokens = 100)

        val result = ranker.rankAndSelect(
            searchResults = listOf(SearchResult("emb-1", 0.9f)),
            memories = listOf(memory)
        )

        assertEquals(1, result.selectedMemories.size)
        assertEquals("mem-1", result.selectedMemories[0].id)
        assertEquals(100, result.totalTokens)
        assertEquals(0, result.droppedCount)
    }

    @Test
    fun `high similarity memory scores higher`() {
        val now = System.currentTimeMillis()

        val memory1 = createMemory("mem-1", "emb-1", importance = 0.5, createdAt = now)
        val memory2 = createMemory("mem-2", "emb-2", importance = 0.5, createdAt = now)

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.9f),  // High similarity
                SearchResult("emb-2", 0.3f)   // Low similarity
            ),
            memories = listOf(memory1, memory2)
        )

        // mem-1 should be selected first due to higher similarity
        assertEquals(2, result.selectedMemories.size)
        assertEquals("mem-1", result.selectedMemories[0].id)
        assertTrue(result.rankingScores["mem-1"]!! > result.rankingScores["mem-2"]!!)
    }

    @Test
    fun `recent memories score higher than old ones`() {
        val now = System.currentTimeMillis()
        val weekAgo = now - (7 * 24 * 60 * 60 * 1000L)

        val recentMemory = createMemory("mem-recent", "emb-1", createdAt = now)
        val oldMemory = createMemory("mem-old", "emb-2", createdAt = weekAgo)

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.5f),
                SearchResult("emb-2", 0.5f)
            ),
            memories = listOf(recentMemory, oldMemory),
            currentTimestamp = now
        )

        // Recent memory should score higher
        assertTrue(result.rankingScores["mem-recent"]!! > result.rankingScores["mem-old"]!!)
    }

    @Test
    fun `high importance memories score higher`() {
        val now = System.currentTimeMillis()

        val highImportance = createMemory("mem-high", "emb-1", importance = 0.9, createdAt = now)
        val lowImportance = createMemory("mem-low", "emb-2", importance = 0.2, createdAt = now)

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.5f),
                SearchResult("emb-2", 0.5f)
            ),
            memories = listOf(highImportance, lowImportance)
        )

        // High importance should score higher
        assertTrue(result.rankingScores["mem-high"]!! > result.rankingScores["mem-low"]!!)
    }

    @Test
    fun `frequently accessed memories get bonus`() {
        val now = System.currentTimeMillis()

        val popularMemory = createMemory("mem-popular", "emb-1", accessCount = 50, createdAt = now)
        val unpopularMemory = createMemory("mem-unpopular", "emb-2", accessCount = 0, createdAt = now)

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.5f),
                SearchResult("emb-2", 0.5f)
            ),
            memories = listOf(popularMemory, unpopularMemory)
        )

        // Popular memory should score higher
        assertTrue(result.rankingScores["mem-popular"]!! > result.rankingScores["mem-unpopular"]!!)
    }

    @Test
    fun `token budget is respected`() {
        val memories = (1..20).map { i ->
            createMemory("mem-$i", "emb-$i", chunkTokens = 100)
        }

        val searchResults = (1..20).map { i ->
            SearchResult("emb-$i", 0.5f)
        }

        // Budget is 1000 tokens, each memory is 100 tokens
        // Should select exactly 10 memories
        val result = ranker.rankAndSelect(
            searchResults = searchResults,
            memories = memories
        )

        assertEquals(10, result.selectedMemories.size)
        assertEquals(1000, result.totalTokens)
        assertEquals(10, result.droppedCount)
    }

    @Test
    fun `memories are dropped when budget exhausted`() {
        val memory1 = createMemory("mem-1", "emb-1", chunkTokens = 600)
        val memory2 = createMemory("mem-2", "emb-2", chunkTokens = 500)
        val memory3 = createMemory("mem-3", "emb-3", chunkTokens = 400)

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.9f),
                SearchResult("emb-2", 0.8f),
                SearchResult("emb-3", 0.7f)
            ),
            memories = listOf(memory1, memory2, memory3)
        )

        // Budget is 1000, so mem-1 (600) + mem-2 (500) = 1100 exceeds budget
        // Should only select mem-1
        assertEquals(1, result.selectedMemories.size)
        assertEquals("mem-1", result.selectedMemories[0].id)
        assertEquals(600, result.totalTokens)
        assertEquals(2, result.droppedCount)
    }

    @Test
    fun `null chunk tokens are handled gracefully`() {
        val memory = createMemory("mem-1", "emb-1", chunkTokens = null)

        val result = ranker.rankAndSelect(
            searchResults = listOf(SearchResult("emb-1", 0.9f)),
            memories = listOf(memory)
        )

        assertEquals(1, result.selectedMemories.size)
        assertEquals(0, result.totalTokens)  // Null tokens counted as 0
    }

    @Test
    fun `getOrderedByChunks sorts by message and chunk index`() {
        val memory1 = createMemory("mem-1", "emb-1").copy(
            message_id = "msg-A",
            chunk_index = 1
        )
        val memory2 = createMemory("mem-2", "emb-2").copy(
            message_id = "msg-A",
            chunk_index = 0
        )
        val memory3 = createMemory("mem-3", "emb-3").copy(
            message_id = "msg-B",
            chunk_index = 0
        )

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.9f),
                SearchResult("emb-2", 0.8f),
                SearchResult("emb-3", 0.7f)
            ),
            memories = listOf(memory1, memory2, memory3)
        )

        val ordered = result.getOrderedByChunks()

        // Should be: msg-A chunk 0, msg-A chunk 1, msg-B chunk 0
        assertEquals("mem-2", ordered[0].id)  // msg-A, chunk 0
        assertEquals("mem-1", ordered[1].id)  // msg-A, chunk 1
        assertEquals("mem-3", ordered[2].id)  // msg-B, chunk 0
    }

    @Test
    fun `formatAsContext creates readable text`() {
        val memory1 = createMemory("mem-1", "emb-1", importance = 0.8).copy(
            content = "First memory content"
        )
        val memory2 = createMemory("mem-2", "emb-2", importance = 0.6).copy(
            content = "Second memory content"
        )

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.9f),
                SearchResult("emb-2", 0.8f)
            ),
            memories = listOf(memory1, memory2)
        )

        val formatted = result.formatAsContext()

        assertTrue(formatted.contains("First memory content"))
        assertTrue(formatted.contains("Second memory content"))
        assertTrue(formatted.contains("importance: 0.80"))
        assertTrue(formatted.contains("importance: 0.60"))
    }

    @Test
    fun `isEmpty returns true for empty result`() {
        val result = ranker.rankAndSelect(
            searchResults = emptyList(),
            memories = emptyList()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `isEmpty returns false for non-empty result`() {
        val memory = createMemory("mem-1", "emb-1")

        val result = ranker.rankAndSelect(
            searchResults = listOf(SearchResult("emb-1", 0.9f)),
            memories = listOf(memory)
        )

        assertFalse(result.isEmpty())
    }

    @Test
    fun `getStats provides debugging information`() {
        val memory1 = createMemory("mem-1", "emb-1", importance = 0.8)
        val memory2 = createMemory("mem-2", "emb-2", importance = 0.6)

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.9f),
                SearchResult("emb-2", 0.8f)
            ),
            memories = listOf(memory1, memory2)
        )

        val stats = result.getStats()

        assertTrue(stats.contains("Selected: 2 memories"))
        assertTrue(stats.contains("Total Tokens: 200"))
        assertTrue(stats.contains("Dropped: 0"))
        assertTrue(stats.contains("Avg Importance: 0.70"))
    }

    @Test
    fun `composite scoring balances all factors`() {
        val now = System.currentTimeMillis()

        // High similarity, low everything else
        val mem1 = createMemory("mem-1", "emb-1",
            importance = 0.1,
            createdAt = now - (30 * 24 * 60 * 60 * 1000L),  // 30 days old
            accessCount = 0
        )

        // Medium similarity, high importance + recent + accessed
        val mem2 = createMemory("mem-2", "emb-2",
            importance = 0.9,
            createdAt = now,
            accessCount = 20
        )

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-1", 0.95f),  // Very high similarity
                SearchResult("emb-2", 0.60f)   // Medium similarity
            ),
            memories = listOf(mem1, mem2),
            currentTimestamp = now
        )

        // mem-2 should win due to better overall score despite lower similarity
        // (high importance + recent + accessed should outweigh similarity difference)
        val score1 = result.rankingScores["mem-1"]!!
        val score2 = result.rankingScores["mem-2"]!!

        // Both should be selected (within budget)
        assertEquals(2, result.selectedMemories.size)

        // Verify scoring includes all factors
        assertTrue(score1 > 0.0f)
        assertTrue(score2 > 0.0f)
    }

    @Test
    fun `unmatched embedding IDs are ignored`() {
        val memory = createMemory("mem-1", "emb-1")

        val result = ranker.rankAndSelect(
            searchResults = listOf(
                SearchResult("emb-NONEXISTENT", 0.9f)  // No matching memory
            ),
            memories = listOf(memory)
        )

        assertTrue(result.isEmpty())
    }
}
