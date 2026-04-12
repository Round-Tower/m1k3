package app.m1k3.ai.assistant.design.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolCallPillTest {

    @Test
    fun `formatToolName converts underscored ID to title case`() {
        assertEquals("Get Battery", formatToolName("get_battery"))
    }

    @Test
    fun `formatToolName handles single word`() {
        assertEquals("Camera", formatToolName("camera"))
    }

    @Test
    fun `formatToolName handles multiple underscores`() {
        assertEquals("Get Screen Time", formatToolName("get_screen_time"))
    }

    @Test
    fun `formatToolName handles web search`() {
        assertEquals("Web Search", formatToolName("web_search"))
    }
}
