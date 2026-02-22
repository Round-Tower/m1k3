package app.m1k3.ai.domain.chat.events

import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ChatEvent sealed class hierarchy.
 */
class ChatEventTest {

    @Test
    fun `Started event is correct type`() {
        val event = ChatEvent.Started

        assertIs<ChatEvent.Started>(event)
    }

    @Test
    fun `RetrievingContext event is correct type`() {
        val event = ChatEvent.RetrievingContext

        assertIs<ChatEvent.RetrievingContext>(event)
    }

    @Test
    fun `ContextRetrieved event contains context`() {
        val context = EnrichedContext.empty()
        val event = ChatEvent.ContextRetrieved(context)

        assertIs<ChatEvent.ContextRetrieved>(event)
        assertEquals(context, event.context)
    }

    @Test
    fun `Generating event is correct type`() {
        val event = ChatEvent.Generating

        assertIs<ChatEvent.Generating>(event)
    }

    @Test
    fun `Streaming event contains partial text and token count`() {
        val event = ChatEvent.Streaming(
            partialText = "Processing your",
            tokenCount = 4
        )

        assertIs<ChatEvent.Streaming>(event)
        assertEquals("Processing your", event.partialText)
        assertEquals(4, event.tokenCount)
    }

    @Test
    fun `ToolsExecuted event contains results and confirmation status`() {
        val results = listOf(
            ToolResult.Success(
                toolId = "battery",
                output = "85%",
                executionTimeMs = 50
            )
        )
        val event = ChatEvent.ToolsExecuted(
            results = results,
            hasPendingConfirmations = false
        )

        assertIs<ChatEvent.ToolsExecuted>(event)
        assertEquals(1, event.results.size)
        assertFalse(event.hasPendingConfirmations)
        assertTrue(event.allSucceeded)
    }

    @Test
    fun `ToolsExecuted allSucceeded returns false when has failures`() {
        val results = listOf(
            ToolResult.Success(toolId = "battery", output = "85%", executionTimeMs = 50),
            ToolResult.Failure(
                toolId = "camera",
                error = ToolError.PermissionDenied("CAMERA"),
                executionTimeMs = 10
            )
        )
        val event = ChatEvent.ToolsExecuted(
            results = results,
            hasPendingConfirmations = false
        )

        assertFalse(event.allSucceeded)
        assertEquals(1, event.successfulResults.size)
        assertEquals(1, event.failedResults.size)
    }

    @Test
    fun `ToolsExecuted pendingConfirmations filters correctly`() {
        val pendingCall = ToolCall(
            toolId = "delete",
            arguments = mapOf("file" to "test.txt"),
            rawText = """{"tool": "delete", "args": {"file": "test.txt"}}"""
        )
        val results = listOf(
            ToolResult.Success(toolId = "battery", output = "85%", executionTimeMs = 50),
            ToolResult.RequiresConfirmation(
                toolId = "delete",
                confirmationPrompt = "Delete file?",
                pendingCall = pendingCall
            )
        )
        val event = ChatEvent.ToolsExecuted(
            results = results,
            hasPendingConfirmations = true
        )

        assertEquals(1, event.pendingConfirmations.size)
        assertEquals("delete", event.pendingConfirmations[0].toolId)
    }

    @Test
    fun `Complete event contains chat response`() {
        val stats = GenerationStats(
            tokenCount = 15,
            durationMs = 750,
            tokensPerSecond = 20f
        )
        val response = ChatResponse(
            text = "Your battery is at 85%",
            stats = stats,
            context = null,
            toolResults = null
        )
        val event = ChatEvent.Complete(response)

        assertIs<ChatEvent.Complete>(event)
        assertEquals("Your battery is at 85%", event.response.text)
    }

    @Test
    fun `Failed event contains error`() {
        val error = ChatError.ModelError("Model crashed")
        val event = ChatEvent.Failed(error)

        assertIs<ChatEvent.Failed>(event)
        assertIs<ChatError.ModelError>(event.error)
    }

    @Test
    fun `all event types are exhaustive in when`() {
        val events = listOf<ChatEvent>(
            ChatEvent.Started,
            ChatEvent.RetrievingContext,
            ChatEvent.ContextRetrieved(EnrichedContext.empty()),
            ChatEvent.Generating,
            ChatEvent.Streaming("test", 1),
            ChatEvent.ToolsExecuted(emptyList(), false),
            ChatEvent.Complete(
                ChatResponse(
                    "test",
                    GenerationStats(1, 100, 10f),
                    null,
                    null
                )
            ),
            ChatEvent.Failed(ChatError.Unknown("test"))
        )

        events.forEach { event ->
            val handled = when (event) {
                is ChatEvent.Started -> true
                is ChatEvent.RetrievingContext -> true
                is ChatEvent.ContextRetrieved -> true
                is ChatEvent.Generating -> true
                is ChatEvent.Streaming -> true
                is ChatEvent.ToolsExecuted -> true
                is ChatEvent.Complete -> true
                is ChatEvent.Failed -> true
            }
            assertTrue(handled)
        }
    }
}

/**
 * Tests for ChatResponse data class.
 */
class ChatResponseTest {

    @Test
    fun `ChatResponse stores all properties`() {
        val stats = GenerationStats(
            tokenCount = 30,
            durationMs = 1500,
            tokensPerSecond = 20f
        )
        val context = EnrichedContext(
            context = "Context",
            intentCategory = "DEVICE",
            hasRagContext = false,
            hasMemoryContext = true
        )
        val toolResults = listOf(
            ToolResult.Success(toolId = "battery", output = "85%", executionTimeMs = 50)
        )
        val response = ChatResponse(
            text = "Battery is 85%",
            stats = stats,
            context = context,
            toolResults = toolResults,
            toolResultsFormatted = "Battery: 85%"
        )

        assertEquals("Battery is 85%", response.text)
        assertEquals(30, response.stats.tokenCount)
        assertEquals(context, response.context)
        assertEquals(1, response.toolResults?.size)
        assertEquals("Battery: 85%", response.toolResultsFormatted)
    }

    @Test
    fun `hasToolResults returns true when tools executed`() {
        val response = ChatResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = null,
            toolResults = listOf(
                ToolResult.Success(toolId = "test", output = "ok", executionTimeMs = 50)
            )
        )

        assertTrue(response.hasToolResults)
    }

    @Test
    fun `hasToolResults returns false when no tools`() {
        val response = ChatResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = null,
            toolResults = null
        )

        assertFalse(response.hasToolResults)
    }

    @Test
    fun `hasToolResults returns false when empty list`() {
        val response = ChatResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = null,
            toolResults = emptyList()
        )

        assertFalse(response.hasToolResults)
    }

    @Test
    fun `usedRag returns true when context has RAG`() {
        val context = EnrichedContext(
            context = "RAG facts",
            intentCategory = "SCIENCE",
            hasRagContext = true,
            hasMemoryContext = false
        )
        val response = ChatResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = context,
            toolResults = null
        )

        assertTrue(response.usedRag)
    }

    @Test
    fun `usedMemory returns true when context has memory`() {
        val context = EnrichedContext(
            context = "Memory",
            intentCategory = "GENERAL",
            hasRagContext = false,
            hasMemoryContext = true
        )
        val response = ChatResponse(
            text = "Response",
            stats = GenerationStats(1, 100, 10f),
            context = context,
            toolResults = null
        )

        assertTrue(response.usedMemory)
    }

    @Test
    fun `getDisplayText returns text only when no tool results`() {
        val response = ChatResponse(
            text = "Simple response",
            stats = GenerationStats(1, 100, 10f),
            context = null,
            toolResults = null
        )

        assertEquals("Simple response", response.getDisplayText())
    }

    @Test
    fun `getDisplayText combines text and tool results`() {
        val response = ChatResponse(
            text = "Here's your info",
            stats = GenerationStats(1, 100, 10f),
            context = null,
            toolResults = listOf(
                ToolResult.Success(toolId = "battery", output = "85%", executionTimeMs = 50)
            ),
            toolResultsFormatted = "Battery: 85%"
        )

        val displayText = response.getDisplayText()
        assertTrue(displayText.contains("Here's your info"))
        assertTrue(displayText.contains("Battery: 85%"))
    }

    @Test
    fun `getDisplayText handles empty text with tool results`() {
        val response = ChatResponse(
            text = "",
            stats = GenerationStats(1, 100, 10f),
            context = null,
            toolResults = listOf(
                ToolResult.Success(toolId = "battery", output = "85%", executionTimeMs = 50)
            ),
            toolResultsFormatted = "Battery: 85%"
        )

        assertEquals("Battery: 85%", response.getDisplayText())
    }

    @Test
    fun `ChatResponse with null context works`() {
        val response = ChatResponse(
            text = "Response",
            stats = GenerationStats(5, 200, 25f),
            context = null,
            toolResults = null
        )

        assertNull(response.context)
        assertFalse(response.usedRag)
        assertFalse(response.usedMemory)
    }
}
