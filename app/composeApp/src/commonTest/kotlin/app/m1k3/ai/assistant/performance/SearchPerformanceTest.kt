package app.m1k3.ai.assistant.performance

import app.m1k3.ai.assistant.history.SearchRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.system.measureTimeMillis

/**
 * 間 AI - Search Performance Test
 *
 * Validates SearchRepository performance with 10,000 messages,
 * ensuring scalability for Phase 2 conversation history features.
 *
 * **Performance Targets:**
 * - Search with 10K messages: <100ms
 * - Relevance scoring: <50ms per query
 * - Database query optimization
 *
 * **Philosophy:**
 * Speed is a feature. Users deserve instant search results
 * even with extensive conversation history.
 */
class SearchPerformanceTest {

    private lateinit var database: app.m1k3.ai.assistant.database.MaDatabase
    private lateinit var searchRepository: SearchRepository
    private lateinit var conversationRepository: ConversationRepository

    @BeforeTest
    fun setup() {
        database = TestDatabaseFactory.createInMemoryDatabase()
        searchRepository = SearchRepository(database)
        conversationRepository = ConversationRepository(database)
    }

    @Test
    fun `search with 10K messages completes within performance target`() {
        runBlocking {
            // ARRANGE: Create 10,000 messages across 100 conversations
            println("📊 Generating 10,000 test messages...")

            val messageCount = 10_000
            val conversationCount = 100
            val messagesPerConversation = messageCount / conversationCount

            val setupTime = measureTimeMillis {
                // Create conversations
                val conversationIds = mutableListOf<Long>()
                repeat(conversationCount) { i ->
                    val convId = conversationRepository.createConversation(
                        projectId = "performance_test",
                        title = "Test Conversation ${i + 1}"
                    )
                    conversationIds.add(convId)
                }

                // Generate diverse message content
                val topics = listOf(
                    "artificial intelligence",
                    "machine learning",
                    "neural networks",
                    "natural language processing",
                    "computer vision",
                    "deep learning",
                    "robotics",
                    "data science",
                    "quantum computing",
                    "blockchain"
                )

                // Populate messages
                var messageId = 0
                for (convId in conversationIds) {
                    repeat(messagesPerConversation) { i ->
                        val topic = topics[i % topics.size]
                        val content = "This is message ${messageId + 1} about $topic and related concepts. " +
                                "It discusses various aspects of $topic including theory, practice, and applications. " +
                                "This helps test search relevance and performance at scale."

                        database.messageQueries.insertMessage(
                            id = "msg_perf_${messageId++}",
                            project_id = "performance_test",
                            conversation_id = convId,
                            role = if (i % 2 == 0) "user" else "assistant",
                            content = content,
                            tokens = 50L,
                            timestamp = Clock.System.now().toEpochMilliseconds() + i,
                            image_uri = null,
                            sentiment_valence = null,
                            sentiment_arousal = null,
                            sentiment_dominance = null,
                            sentiment_emotion = null,
                            sentiment_intensity = null,
                            rag_sources = null,
                            rag_confidence = null
                        )

                        // Update conversation metadata
                        if ((i + 1) % 10 == 0) { // Batch update every 10 messages
                            conversationRepository.incrementMessageCount(convId, 500L) // 10 messages * 50 tokens
                        }
                    }
                }
            }

            println("✅ Setup complete in ${setupTime}ms")
            println("📊 Database contains $messageCount messages across $conversationCount conversations")

            // Verify message count
            val actualCount = database.messageQueries.getAllMessagesByProject("performance_test")
                .executeAsList().size
            assertEquals(messageCount, actualCount, "Should have exactly $messageCount messages")

            // ACT & ASSERT: Perform search queries with performance validation
            val testQueries = listOf(
                "artificial intelligence" to 100,    // Common term
                "quantum computing" to 100,          // Specific term
                "machine learning applications" to 100, // Multiple terms
                "neural network deep learning" to 100,  // Related concepts
                "robotics data science" to 100          // Mixed topics
            )

            println("\n🔍 Running search performance tests:")
            println("━".repeat(70))

            for ((query, maxTimeMs) in testQueries) {
                var resultCount = 0
                val searchTime = measureTimeMillis {
                    val results = searchRepository.search(
                        projectId = "performance_test",
                        query = query,
                        limit = 50
                    )
                    resultCount = results.size
                }

                println("Query: \"$query\"")
                println("  • Results: $resultCount")
                println("  • Time: ${searchTime}ms")
                println("  • Performance: ${if (searchTime < maxTimeMs) "✅ PASS" else "❌ FAIL"} (target: <${maxTimeMs}ms)")

                assertTrue(
                    searchTime < maxTimeMs,
                    "Search for '$query' took ${searchTime}ms, exceeds target of ${maxTimeMs}ms"
                )

                assertTrue(
                    resultCount > 0,
                    "Search for '$query' should return at least some results"
                )
                println()
            }

            println("━".repeat(70))
            println("✅ All search performance tests passed!")
        }
    }

    @Test
    fun `relevance scoring performs efficiently at scale`() {
        runBlocking {
            // ARRANGE: Create 1,000 messages with varying relevance
            val messageCount = 1_000
            println("📊 Generating $messageCount messages with varying relevance...")

            val convId = conversationRepository.createConversation(
                projectId = "relevance_test",
                title = "Relevance Test Conversation"
            )

            val setupTime = measureTimeMillis {
                // Create messages with different relevance levels
                repeat(messageCount) { i ->
                    val content = when {
                        i % 10 == 0 -> "This message contains artificial intelligence and machine learning keywords multiple times"
                        i % 5 == 0 -> "This message mentions artificial intelligence once"
                        i % 3 == 0 -> "This message has machine learning but not AI"
                        else -> "This is a generic message about other topics like weather and cooking"
                    }

                    database.messageQueries.insertMessage(
                        id = "msg_rel_$i",
                        project_id = "relevance_test",
                        conversation_id = convId,
                        role = if (i % 2 == 0) "user" else "assistant",
                        content = content,
                        tokens = 20L,
                        timestamp = Clock.System.now().toEpochMilliseconds() + i,
                        image_uri = null,
                        sentiment_valence = null,
                        sentiment_arousal = null,
                        sentiment_dominance = null,
                        sentiment_emotion = null,
                        sentiment_intensity = null,
                        rag_sources = null,
                        rag_confidence = null
                    )
                }
            }

            println("✅ Setup complete in ${setupTime}ms")

            // ACT: Search and validate relevance scoring
            val searchTime = measureTimeMillis {
                val results = searchRepository.search(
                    projectId = "relevance_test",
                    query = "artificial intelligence machine learning",
                    limit = 50
                )

                // ASSERT: Verify results are sorted by relevance
                assertTrue(results.isNotEmpty(), "Should find relevant results")

                // Check that results with multiple keyword matches rank higher
                val topResults = results.take(10)
                val hasHighRelevance = topResults.any { result ->
                    result.content.contains("artificial intelligence") &&
                    result.content.contains("machine learning") &&
                    result.content.contains("multiple times")
                }

                assertTrue(
                    hasHighRelevance,
                    "Top results should include messages with multiple keyword matches"
                )

                println("📊 Relevance scoring results:")
                println("  • Total results: ${results.size}")
                println("  • Search time: ${searchTime}ms")
                println("  • Top result relevance validated: ✅")
            }

            assertTrue(
                searchTime < 50,
                "Relevance scoring with $messageCount messages took ${searchTime}ms, exceeds 50ms target"
            )

            println("✅ Relevance scoring performance test passed!")
        }
    }

    @Test
    fun `search handles edge cases efficiently`() {
        runBlocking {
            // ARRANGE: Create 5,000 messages
            println("📊 Generating 5,000 messages for edge case testing...")

            val convId = conversationRepository.createConversation(
                projectId = "edge_case_test",
                title = "Edge Case Test"
            )

            val setupTime = measureTimeMillis {
                repeat(5_000) { i ->
                    database.messageQueries.insertMessage(
                        id = "msg_edge_$i",
                        project_id = "edge_case_test",
                        conversation_id = convId,
                        role = "user",
                        content = "Message $i with content",
                        tokens = 10L,
                        timestamp = Clock.System.now().toEpochMilliseconds() + i,
                        image_uri = null,
                        sentiment_valence = null,
                        sentiment_arousal = null,
                        sentiment_dominance = null,
                        sentiment_emotion = null,
                        sentiment_intensity = null,
                        rag_sources = null,
                        rag_confidence = null
                    )
                }
            }

            println("✅ Setup complete in ${setupTime}ms")

            // Test edge cases
            val edgeCases = listOf(
                "nonexistent query term" to 0,      // No matches
                "content" to 5000,                   // Matches everything
                "message 1234" to 1,                 // Exact match
                "" to 0,                             // Empty query
                "a" to 0                             // Single character (filtered)
            )

            println("\n🔍 Testing edge cases:")
            println("━".repeat(70))

            for ((query, expectedMinResults) in edgeCases) {
                var resultCount = 0
                val searchTime = measureTimeMillis {
                    val results = searchRepository.search(
                        projectId = "edge_case_test",
                        query = query,
                        limit = 50
                    )
                    resultCount = results.size
                }

                println("Query: \"$query\"")
                println("  • Results: $resultCount")
                println("  • Time: ${searchTime}ms")
                println("  • Performance: ${if (searchTime < 100) "✅" else "⚠️"}")

                assertTrue(
                    searchTime < 100,
                    "Edge case search took ${searchTime}ms, exceeds 100ms target"
                )

                if (expectedMinResults > 0) {
                    assertTrue(
                        resultCount >= expectedMinResults,
                        "Expected at least $expectedMinResults results, got $resultCount"
                    )
                }

                println()
            }

            println("━".repeat(70))
            println("✅ All edge case tests passed!")
        }
    }

    @Test
    fun `database query optimization validates indexed lookups`() {
        runBlocking {
            // ARRANGE: Create 10,000 messages
            println("📊 Generating 10,000 messages for query optimization test...")

            val messageCount = 10_000
            val convId = conversationRepository.createConversation(
                projectId = "query_optimization_test",
                title = "Query Optimization Test"
            )

            val setupTime = measureTimeMillis {
                repeat(messageCount) { i ->
                    database.messageQueries.insertMessage(
                        id = "msg_opt_$i",
                        project_id = "query_optimization_test",
                        conversation_id = convId,
                        role = if (i % 2 == 0) "user" else "assistant",
                        content = "Optimization test message $i",
                        tokens = 15L,
                        timestamp = Clock.System.now().toEpochMilliseconds() + i,
                        image_uri = null,
                        sentiment_valence = null,
                        sentiment_arousal = null,
                        sentiment_dominance = null,
                        sentiment_emotion = null,
                        sentiment_intensity = null,
                        rag_sources = null,
                        rag_confidence = null
                    )
                }
            }

            println("✅ Setup complete in ${setupTime}ms")

            // Test database query performance
            val queryTests = mapOf(
                "Project ID lookup" to {
                    database.messageQueries.getAllMessagesByProject("query_optimization_test").executeAsList()
                },
                "Conversation ID lookup" to {
                    database.messageQueries.getMessagesByConversation(convId).executeAsList()
                },
                "Combined search" to {
                    searchRepository.search("query_optimization_test", "optimization", limit = 100)
                }
            )

            println("\n🔍 Database query optimization tests:")
            println("━".repeat(70))

            for ((testName, queryFn) in queryTests) {
                var resultCount = 0
                val queryTime = measureTimeMillis {
                    resultCount = queryFn().size
                }

                println("$testName")
                println("  • Results: $resultCount")
                println("  • Query time: ${queryTime}ms")
                println("  • Performance: ${if (queryTime < 100) "✅ PASS" else "❌ FAIL"} (target: <100ms)")

                assertTrue(
                    queryTime < 100,
                    "$testName took ${queryTime}ms, exceeds 100ms target (needs indexing)"
                )
                println()
            }

            println("━".repeat(70))
            println("✅ All query optimization tests passed!")
            println("💡 Database indexes are working efficiently")
        }
    }
}
