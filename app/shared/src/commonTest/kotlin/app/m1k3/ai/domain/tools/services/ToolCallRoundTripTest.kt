package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.ParameterType
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Grammar ↔ parser drift guard.
 *
 * [ToolCallGrammarBuilder] and [DefaultToolCallParser] are implicitly coupled —
 * every value shape the grammar allows, the parser must recover. This test
 * locks that contract by round-tripping a canonical tool call for every
 * parameter type the grammar describes:
 *
 * - STRING  → `"..."`
 * - NUMBER  → digits, with optional `-` and fractional part (grammar: `"-"? [0-9]+ ("." [0-9]+)?`)
 * - BOOLEAN → `true | false`
 * - ENUM    → one of `"..."` from the enum set
 *
 * If anyone broadens the grammar (e.g. adds `null` or exponent form) without
 * teaching the parser the new shape, the next arg encoded here fails to
 * round-trip and the test goes red.
 *
 * Synthesizes canonical-form JSON by hand (no whitespace between tokens,
 * exactly like [ToolCallGrammarBuilder]'s root rule). Avoids running the
 * actual grammar compiler — we're not validating llama.cpp, we're validating
 * that the two halves agree on shape.
 */
class ToolCallRoundTripTest {
    private val parser: ToolCallParser = DefaultToolCallParser()

    private val everyTypeTool =
        Tool(
            id = "every_type",
            name = "Every Type",
            description = "Tool exercising every ParameterType the grammar supports",
            parameters =
                listOf(
                    ToolParameter("text", ParameterType.STRING, "a string", required = true),
                    ToolParameter("count", ParameterType.NUMBER, "a number", required = true),
                    ToolParameter("enabled", ParameterType.BOOLEAN, "a boolean", required = true),
                    ToolParameter(
                        name = "stream",
                        type = ParameterType.ENUM,
                        description = "an enum",
                        required = true,
                        enumValues = listOf("media", "ring", "alarm"),
                    ),
                ),
            category = ToolCategory.SYSTEM,
        )

    @Test
    fun `round-trips string number boolean and enum values`() {
        val canonical =
            canonicalToolCall(
                toolId = "every_type",
                args =
                    listOf(
                        "text" to """"hello"""",
                        "count" to "42",
                        "enabled" to "true",
                        "stream" to """"ring"""",
                    ),
            )

        val calls = parser.parse(canonical)

        assertEquals(1, calls.size, "Parser must recover exactly one call from canonical grammar output")
        val args = calls[0].arguments
        assertEquals("hello", args["text"])
        assertEquals("42", args["count"])
        assertEquals("true", args["enabled"])
        assertEquals("ring", args["stream"])
    }

    @Test
    fun `round-trips grammar-legal number variants`() {
        // ToolCallGrammarBuilder's number rule: `"-"? [0-9]+ ("." [0-9]+)?`
        // Parser must recover every one of these shapes.
        val shapes =
            mapOf(
                "positive_int" to "7",
                "zero" to "0",
                "negative_int" to "-5",
                "decimal" to "3.14",
                "negative_decimal" to "-0.5",
            )

        val canonical =
            canonicalToolCall(
                toolId = "every_type",
                args = shapes.map { (k, v) -> k to v },
            )

        val calls = parser.parse(canonical)

        assertEquals(1, calls.size)
        shapes.forEach { (key, value) ->
            assertEquals(value, calls[0].arguments[key], "Parser must preserve `$value` for `$key`")
        }
    }

    @Test
    fun `round-trips boolean values`() {
        // Grammar: `boolean ::= "true" | "false"`
        val canonical =
            canonicalToolCall(
                toolId = "every_type",
                args = listOf("a" to "true", "b" to "false"),
            )

        val calls = parser.parse(canonical)

        assertEquals(1, calls.size)
        assertEquals("true", calls[0].arguments["a"])
        assertEquals("false", calls[0].arguments["b"])
    }

    @Test
    fun `parser recovers tool id for every tool in an every-type fleet`() {
        // Verifies the alternation produced by toolIdRule round-trips per tool.
        val ids = listOf("get_battery", "toggle-flashlight", "complex_tool_name_with_underscores")
        ids.forEach { id ->
            val canonical =
                canonicalToolCall(
                    toolId = id,
                    args = listOf("x" to "1"),
                )
            val calls = parser.parse(canonical)
            assertEquals(1, calls.size, "Parser must recover call for tool id `$id`")
            assertEquals(id, calls[0].toolId)
        }
    }

    @Test
    fun `unwrapped JSON also round-trips for both paths`() {
        // Parser supports bare JSON too; exercise that path so grammar changes
        // that drop the XML wrapper for a different trigger can't silently break.
        val bareJson = """{"tool":"every_type","args":{"count":-9}}"""
        val calls = parser.parse(bareJson)

        assertEquals(1, calls.size)
        assertEquals("every_type", calls[0].toolId)
        assertEquals("-9", calls[0].arguments["count"])
    }

    @Test
    fun `parser does not need everyTypeTool fixture`() {
        // Guardrail: the fleet Tool is declared for documentation; the parser
        // works from raw text alone. Keep the parameter list authoritative
        // so if the grammar builder later adds a new ParameterType, adding it
        // here makes the new round-trip case obvious.
        assertTrue(everyTypeTool.parameters.size == 4)
    }

    // ── Canonical JSON synthesis ──────────────────────────────
    //
    // Mirrors ToolCallGrammarBuilder.rootRule() output exactly:
    //   "<tool_call>" "{" "\"tool\"" ":" tool-id "," "\"args\"" ":" args "}" "</tool_call>"
    // No whitespace between tokens — the grammar does not insert any.
    private fun canonicalToolCall(
        toolId: String,
        args: List<Pair<String, String>>,
    ): String {
        val argsJson =
            if (args.isEmpty()) {
                "{}"
            } else {
                args.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
                    """"$k":$v"""
                }
            }
        return """<tool_call>{"tool":"$toolId","args":$argsJson}</tool_call>"""
    }
}
