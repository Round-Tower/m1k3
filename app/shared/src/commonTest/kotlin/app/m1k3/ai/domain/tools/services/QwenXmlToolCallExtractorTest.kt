package app.m1k3.ai.domain.tools.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QwenXmlToolCallExtractorTest {
    @Test
    fun `extracts single tool call with one string parameter`() {
        val raw =
            """
            Let me check your topic.

            <tool_call>
            <function=web_search>
            <parameter=query>
            recent news 2026
            </parameter>
            </function>
            </tool_call>
            """.trimIndent()

        val calls = QwenXmlToolCallExtractor.extract(raw)

        assertEquals(1, calls.size)
        val call = calls[0]
        assertEquals("web_search", call.name)
        assertEquals("recent news 2026", call.arguments["query"])
    }

    @Test
    fun `extracts multiple parameters`() {
        val raw =
            """
            <tool_call>
            <function=set_timer>
            <parameter=duration>
            30
            </parameter>
            <parameter=label>
            eggs
            </parameter>
            </function>
            </tool_call>
            """.trimIndent()

        val call = QwenXmlToolCallExtractor.extract(raw).single()
        assertEquals("set_timer", call.name)
        assertEquals("30", call.arguments["duration"])
        assertEquals("eggs", call.arguments["label"])
    }

    @Test
    fun `extracts multiple tool calls from one response`() {
        val raw =
            """
            <tool_call>
            <function=get_battery>
            </function>
            </tool_call>

            <tool_call>
            <function=get_time>
            </function>
            </tool_call>
            """.trimIndent()

        val calls = QwenXmlToolCallExtractor.extract(raw)
        assertEquals(2, calls.size)
        assertEquals("get_battery", calls[0].name)
        assertEquals("get_time", calls[1].name)
    }

    @Test
    fun `returns empty list when no tool call block present`() {
        val raw = "Just a chatty response with no tools at all."
        assertTrue(QwenXmlToolCallExtractor.extract(raw).isEmpty())
    }

    @Test
    fun `tolerates tokenization quirks collapsing whitespace inside tags`() {
        // Some Qwen tokenizations render split tokens with extra spaces
        val raw =
            """
            <tool_call>
            < function =toggle_flashlight>
            < parameter =enable>
            true
            </parameter>
            </function>
            </tool_call>
            """.trimIndent()

        val call = QwenXmlToolCallExtractor.extract(raw).single()
        assertEquals("toggle_flashlight", call.name)
        assertEquals("true", call.arguments["enable"])
    }

    @Test
    fun `ignores surrounding think block`() {
        val raw =
            """
            <think>
            I should call the web_search tool to answer this.
            </think>

            <tool_call>
            <function=web_search>
            <parameter=query>
            who is John Mullane
            </parameter>
            </function>
            </tool_call>
            """.trimIndent()

        val call = QwenXmlToolCallExtractor.extract(raw).single()
        assertEquals("web_search", call.name)
        assertEquals("who is John Mullane", call.arguments["query"])
    }

    @Test
    fun `returns empty when function tag has no name`() {
        val raw =
            """
            <tool_call>
            <function=>
            </function>
            </tool_call>
            """.trimIndent()

        assertTrue(QwenXmlToolCallExtractor.extract(raw).isEmpty())
    }
}
