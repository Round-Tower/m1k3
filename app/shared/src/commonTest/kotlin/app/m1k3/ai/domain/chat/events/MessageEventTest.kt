package app.m1k3.ai.domain.chat.events

import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for MessageEvent sealed class hierarchy.
 */
class MessageEventTest {

    @Test
    fun `Started event is correct type`() {
        val event = MessageEvent.Started

        assertIs<MessageEvent.Started>(event)
    }

    @Test
    fun `RetrievingContext event is correct type`() {
        val event = MessageEvent.RetrievingContext

        assertIs<MessageEvent.RetrievingContext>(event)
    }

    @Test
    fun `ContextRetrieved event contains context`() {
        val context = EnrichedContext.empty()
        val event = MessageEvent.ContextRetrieved(context)

        assertIs<MessageEvent.ContextRetrieved>(event)
        assertEquals(context, event.context)
    }

    @Test
    fun `Streaming event contains partial text and token count`() {
        val event = MessageEvent.Streaming(
            partialText = "Hello, how",
            tokenCount = 3
        )

        assertIs<MessageEvent.Streaming>(event)
        assertEquals("Hello, how", event.partialText)
        assertEquals(3, event.tokenCount)
    }

    @Test
    fun `Complete event contains generation response`() {
        val stats = GenerationStats(
            tokenCount = 10,
            durationMs = 500,
            tokensPerSecond = 20f
        )
        val response = GenerationResponse(
            text = "Hello world",
            stats = stats,
            context = null
        )
        val event = MessageEvent.Complete(response)

        assertIs<MessageEvent.Complete>(event)
        assertEquals("Hello world", event.response.text)
        assertEquals(10, event.response.stats.tokenCount)
    }

    @Test
    fun `Failed event contains error`() {
        val error = ChatError.Timeout("Generation timed out")
        val event = MessageEvent.Failed(error)

        assertIs<MessageEvent.Failed>(event)
        assertIs<ChatError.Timeout>(event.error)
        assertEquals("Generation timed out", event.error.message)
    }

    @Test
    fun `all event types are exhaustive in when`() {
        val events = listOf<MessageEvent>(
            MessageEvent.Started,
            MessageEvent.RetrievingContext,
            MessageEvent.ContextRetrieved(EnrichedContext.empty()),
            MessageEvent.Streaming("test", 1),
            MessageEvent.Complete(
                GenerationResponse(
                    "test",
                    GenerationStats(1, 100, 10f),
                    null
                )
            ),
            MessageEvent.Failed(ChatError.Unknown("test"))
        )

        events.forEach { event ->
            // This when should be exhaustive at compile time
            val handled = when (event) {
                is MessageEvent.Started -> true
                is MessageEvent.RetrievingContext -> true
                is MessageEvent.ContextRetrieved -> true
                is MessageEvent.Streaming -> true
                is MessageEvent.Complete -> true
                is MessageEvent.Failed -> true
            }
            assertTrue(handled)
        }
    }
}

/**
 * Tests for GenerationResponse data class.
 */
class GenerationResponseTest {

    @Test
    fun `GenerationResponse stores all properties`() {
        val stats = GenerationStats(
            tokenCount = 25,
            durationMs = 1000,
            tokensPerSecond = 25f,
            ragInfo = "SCIENCE (85%)",
            ragSources = "Wikipedia",
            ragConfidence = 0.85
        )
        val context = EnrichedContext(
            context = "Relevant context",
            intentCategory = "SCIENCE",
            hasRagContext = true,
            hasMemoryContext = false
        )
        val response = GenerationResponse(
            text = "Photosynthesis is...",
            stats = stats,
            context = context
        )

        assertEquals("Photosynthesis is...", response.text)
        assertEquals(25, response.stats.tokenCount)
        assertEquals(context, response.context)
    }

    @Test
    fun `usedRag returns true when context has RAG`() {
        val context = EnrichedContext(
            context = "RAG context",
            intentCategory = "GENERAL",
            hasRagContext = true,
            hasMemoryContext = false
        )
        val response = GenerationResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = context
        )

        assertTrue(response.usedRag)
    }

    @Test
    fun `usedRag returns false when context has no RAG`() {
        val context = EnrichedContext(
            context = "",
            intentCategory = "GENERAL",
            hasRagContext = false,
            hasMemoryContext = false
        )
        val response = GenerationResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = context
        )

        assertFalse(response.usedRag)
    }

    @Test
    fun `usedRag returns false when context is null`() {
        val response = GenerationResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = null
        )

        assertFalse(response.usedRag)
    }

    @Test
    fun `usedMemory returns true when context has memory`() {
        val context = EnrichedContext(
            context = "Memory context",
            intentCategory = "GENERAL",
            hasRagContext = false,
            hasMemoryContext = true
        )
        val response = GenerationResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = context
        )

        assertTrue(response.usedMemory)
    }

    @Test
    fun `usedMemory returns false when context is null`() {
        val response = GenerationResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = null
        )

        assertFalse(response.usedMemory)
    }

    @Test
    fun `GenerationResponse with null context works`() {
        val response = GenerationResponse(
            text = "Simple response",
            stats = GenerationStats(5, 200, 25f),
            context = null
        )

        assertEquals("Simple response", response.text)
        assertNull(response.context)
        assertFalse(response.usedRag)
        assertFalse(response.usedMemory)
    }
}
