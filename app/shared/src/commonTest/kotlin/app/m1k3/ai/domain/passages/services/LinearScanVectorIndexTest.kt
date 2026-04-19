package app.m1k3.ai.domain.passages.services

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearScanVectorIndexTest {
    private val modelA = "minilm-l6-v2"
    private val modelB = "bge-m3-v1"

    @Test
    fun `empty index returns no hits`() =
        runTest {
            val index = LinearScanVectorIndex()
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            assertTrue(hits.isEmpty())
        }

    @Test
    fun `add then search returns the vector`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            assertEquals(1, hits.size)
            assertEquals("p1", hits[0].id)
            // Unit vector against itself ⇒ similarity 1
            assertTrue(hits[0].similarity > 0.99f)
        }

    @Test
    fun `search ranks by similarity descending`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("parallel", floatArrayOf(1f, 0f, 0f), modelA)
            index.add("close", floatArrayOf(0.9f, 0.4f, 0f), modelA)
            index.add("far", floatArrayOf(0.2f, 0.9f, 0f), modelA)
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 3)
            assertEquals(listOf("parallel", "close", "far"), hits.map { it.id })
            assertTrue(hits[0].similarity > hits[1].similarity)
            assertTrue(hits[1].similarity > hits[2].similarity)
        }

    @Test
    fun `search respects k`() =
        runTest {
            val index = LinearScanVectorIndex()
            repeat(10) { i ->
                index.add("p$i", floatArrayOf(1f, 0f, 0f), modelA)
            }
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 3)
            assertEquals(3, hits.size)
        }

    @Test
    fun `search filters by modelId`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("modelA-hit", floatArrayOf(1f, 0f, 0f), modelA)
            index.add("modelB-hit", floatArrayOf(1f, 0f, 0f), modelB)

            val hitsA = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            val hitsB = index.search(floatArrayOf(1f, 0f, 0f), modelB, 10)

            assertEquals(listOf("modelA-hit"), hitsA.map { it.id })
            assertEquals(listOf("modelB-hit"), hitsB.map { it.id })
        }

    @Test
    fun `search drops zero-similarity entries`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("orthogonal", floatArrayOf(0f, 1f, 0f), modelA)
            index.add("matched", floatArrayOf(1f, 0f, 0f), modelA)
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            // Orthogonal vector has cosine 0 → dropped.
            assertEquals(listOf("matched"), hits.map { it.id })
        }

    @Test
    fun `add with same id replaces the vector`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(0f, 1f, 0f), modelA) // orthogonal to query
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA) // parallel to query
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            assertEquals(1, hits.size)
            assertTrue(hits[0].similarity > 0.99f)
            assertEquals(1, index.size())
        }

    @Test
    fun `remove drops the entry`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            index.add("p2", floatArrayOf(1f, 0f, 0f), modelA)
            index.remove("p1")
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            assertEquals(listOf("p2"), hits.map { it.id })
        }

    @Test
    fun `remove missing id is a no-op`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            index.remove("ghost")
            assertEquals(1, index.size())
        }

    @Test
    fun `rebuild replaces index contents`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("old1", floatArrayOf(1f, 0f, 0f), modelA)
            index.add("old2", floatArrayOf(1f, 0f, 0f), modelA)

            index.rebuild(
                listOf(
                    VectorIndex.Entry("new1", floatArrayOf(1f, 0f, 0f), modelA),
                    VectorIndex.Entry("new2", floatArrayOf(1f, 0f, 0f), modelA),
                    VectorIndex.Entry("new3", floatArrayOf(1f, 0f, 0f), modelA),
                ),
            )

            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            assertEquals(setOf("new1", "new2", "new3"), hits.map { it.id }.toSet())
            assertEquals(3, index.size())
        }

    @Test
    fun `rebuild with empty list clears the index`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            index.rebuild(emptyList())
            assertEquals(0, index.size())
            assertTrue(index.search(floatArrayOf(1f, 0f, 0f), modelA, 10).isEmpty())
        }

    @Test
    fun `clear empties the index`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            index.add("p2", floatArrayOf(1f, 0f, 0f), modelA)
            index.clear()
            assertEquals(0, index.size())
        }

    @Test
    fun `search with k less than one returns empty`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            assertTrue(index.search(floatArrayOf(1f, 0f, 0f), modelA, 0).isEmpty())
            assertTrue(index.search(floatArrayOf(1f, 0f, 0f), modelA, -1).isEmpty())
        }

    @Test
    fun `search with empty query returns empty`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("p1", floatArrayOf(1f, 0f, 0f), modelA)
            assertTrue(index.search(floatArrayOf(), modelA, 10).isEmpty())
        }

    @Test
    fun `mismatched vector length does not crash search`() =
        runTest {
            val index = LinearScanVectorIndex()
            index.add("short", floatArrayOf(1f, 0f), modelA)
            index.add("ok", floatArrayOf(1f, 0f, 0f), modelA)
            // cosineSimilarity returns 0 when lengths differ, so "short" is dropped.
            val hits = index.search(floatArrayOf(1f, 0f, 0f), modelA, 10)
            assertEquals(listOf("ok"), hits.map { it.id })
        }
}
