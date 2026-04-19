package app.m1k3.ai.domain.tools.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Gemma4ToolCallExtractorTest {
    @Test
    fun `extracts single tool call with bare boolean arg`() {
        val raw =
            """
            Thinking…

            <|tool_call>call:toggle_flashlight{enable:true}<tool_call|>
            """.trimIndent()

        val calls = Gemma4ToolCallExtractor.extract(raw)

        assertEquals(1, calls.size)
        assertEquals("toggle_flashlight", calls[0].name)
        assertEquals("true", calls[0].arguments["enable"])
    }

    @Test
    fun `extracts string args wrapped in Gemma quote tokens`() {
        val raw = "<|tool_call>call:web_search{query:<|\"|>Kevin Murphy round tower<|\"|>}<tool_call|>"

        val call = Gemma4ToolCallExtractor.extract(raw).single()

        assertEquals("web_search", call.name)
        assertEquals("Kevin Murphy round tower", call.arguments["query"])
    }

    @Test
    fun `extracts multiple args of mixed types`() {
        val raw = "<|tool_call>call:set_timer{duration:30,label:<|\"|>eggs<|\"|>}<tool_call|>"

        val call = Gemma4ToolCallExtractor.extract(raw).single()

        assertEquals("set_timer", call.name)
        assertEquals("30", call.arguments["duration"])
        assertEquals("eggs", call.arguments["label"])
    }

    @Test
    fun `extracts multiple tool calls emitted in sequence`() {
        val raw =
            """
            <|tool_call>call:get_battery{}<tool_call|>
            <|tool_call>call:get_time{}<tool_call|>
            """.trimIndent()

        val calls = Gemma4ToolCallExtractor.extract(raw)

        assertEquals(2, calls.size)
        assertEquals("get_battery", calls[0].name)
        assertEquals("get_time", calls[1].name)
    }

    @Test
    fun `returns empty when no tool call marker present`() {
        assertTrue(Gemma4ToolCallExtractor.extract("just chatter, no tools").isEmpty())
    }

    @Test
    fun `returns empty when function name is missing`() {
        val raw = "<|tool_call>call:{}<tool_call|>"
        assertTrue(Gemma4ToolCallExtractor.extract(raw).isEmpty())
    }

    @Test
    fun `handles surrounding Gemma channel thought block`() {
        val raw =
            """
            <|channel>thought
            I should turn on the flashlight as asked.
            <channel|>
            <|tool_call>call:toggle_flashlight{enable:true}<tool_call|>
            """.trimIndent()

        val call = Gemma4ToolCallExtractor.extract(raw).single()

        assertEquals("toggle_flashlight", call.name)
        assertEquals("true", call.arguments["enable"])
    }
}
