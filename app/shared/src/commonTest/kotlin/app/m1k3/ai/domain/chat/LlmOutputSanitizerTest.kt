package app.m1k3.ai.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class LlmOutputSanitizerTest {
    @Test
    fun `passes plain text through unchanged`() {
        assertEquals(
            "Hello world",
            LlmOutputSanitizer.strip("Hello world"),
        )
    }

    @Test
    fun `drops complete think block with content`() {
        val raw = "Before <think>reasoning here</think> After"
        assertEquals("Before  After", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `drops orphan closing think tag left by the model`() {
        // Seen in Qwen3.5 native-chat path: model emits a closer without an opener.
        val raw = "The user is asking for X. </think> Here is the answer."
        assertEquals("The user is asking for X.  Here is the answer.", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `drops orphan opening think tag without a closer`() {
        val raw = "Answer text <think>and some trailing reasoning"
        assertEquals("Answer text", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `drops complete tool_call xml block`() {
        val raw = "I should call the tool. <tool_call>{\"name\":\"web_search\"}</tool_call> Done."
        assertEquals("I should call the tool.  Done.", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `drops stray tool_call and closing tag`() {
        val raw = "<tool_call>partial"
        assertEquals("partial", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `handles qwen3_5 interleaved think and tool_call tags seen in native chat path`() {
        // Literal capture from logcat 2026-04-19 — Qwen3.5 0.8B emitted this:
        val raw =
            "</think>\n<tool_call>\n</think>\n<tool_call>\n</tool_call>\n<think>\n<think>\n</think>\n<tool_call>\n</tool_call>\nCork is the second-largest city."
        assertEquals("Cork is the second-largest city.", LlmOutputSanitizer.strip(raw).trim())
    }

    @Test
    fun `handles tokenizer space variants like slash space think`() {
        val raw = "Answer < / think > here"
        assertEquals("Answer  here", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `collapses three or more consecutive blank lines left by stripped tags`() {
        val raw = "One\n\n\n\n\nTwo"
        assertEquals("One\n\nTwo", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `handles empty input`() {
        assertEquals("", LlmOutputSanitizer.strip(""))
    }

    @Test
    fun `drops qwen bare function call xml without tool_call wrapper`() {
        // Captured on Pixel 9a 2026-04-19: Qwen3.5 sometimes emits the function
        // call XML directly without the <tool_call> envelope.
        val raw = "<function=web_search><parameter=query>how to cook a 5lb chicken</parameter></function>"
        assertEquals("", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `drops qwen function call xml with surrounding prose`() {
        val raw =
            "Let me check. <function=web_search><parameter=query>cork city</parameter></function> Done."
        assertEquals("Let me check.  Done.", LlmOutputSanitizer.strip(raw))
    }

    @Test
    fun `drops stray parameter tags outside a function block`() {
        val raw = "<parameter=query>orphan</parameter> text"
        assertEquals("text", LlmOutputSanitizer.strip(raw).trim())
    }
}
